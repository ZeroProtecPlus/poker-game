package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import network.protocol.LanConstants;

import java.util.concurrent.CompletableFuture;

/**
 * LAN connection dialog — IP + port only.
 * The player name is collected separately via {@link PlayerNameDialog}
 * after this dialog returns, keeping concerns separated.
 *
 * Layout:
 *   UNDECORATED Stage + APPLICATION_MODAL
 *   menu-bg.png background (full-canvas layer)
 *   FxMenuChrome title bar "Unirse a partida"
 *   Centered .modal-card panel containing:
 *     - .modal-title "CONECTAR A PARTIDA LAN"
 *     - 2 TextFields: IP (default 127.0.0.1), Puerto (default 9876)
 *     - .lan-error-label for inline validation errors
 *     - "CONECTAR" / "VOLVER" buttons
 *   CSS: fonts.css + modal.css + menu-chrome.css
 */
public final class ConnectDialog {

    public record ConnectInfo(String host, int port) {}

    private static final String BG_PATH = "/menu-bg.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String MODAL_CSS_PATH = "/modal.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double DISPLAY_W = MenuLayoutConstants.DESIGN_WIDTH;
    private static final double DISPLAY_H = MenuLayoutConstants.DESIGN_HEIGHT;

    private ConnectDialog() {}

    /**
     * Shows the connect dialog and blocks until the user submits or cancels.
     *
     * @return ConnectInfo with host and port, or null if cancelled
     */
    public static ConnectInfo showAndWaitBlocking() {
        return JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<ConnectInfo> future = new CompletableFuture<>();
            Stage stage = buildStage(future);
            stage.showAndWait();
            return future.getNow(null);
        });
    }

    // ── Stage construction ─────────────────────────────────────────────────

    static Stage buildStage(CompletableFuture<ConnectInfo> future) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Royal Poker — Conectar");

        // ── Root canvas ──────────────────────────────────────────────────────
        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(DISPLAY_W, DISPLAY_H);
        canvas.setMinSize(DISPLAY_W, DISPLAY_H);
        canvas.setMaxSize(DISPLAY_W, DISPLAY_H);

        // ── Background image (full-canvas layer) ─────────────────────────────
        ImageView bg = MenuImages.fullCanvasLayer(BG_PATH);
        bg.setFitWidth(DISPLAY_W);
        bg.setFitHeight(DISPLAY_H);
        AnchorPane.setTopAnchor(bg, 0.0);
        AnchorPane.setLeftAnchor(bg, 0.0);
        canvas.getChildren().add(bg);

        // ── Chrome title bar ─────────────────────────────────────────────────
        FxMenuChrome.apply(stage, canvas, "Unirse a partida");

        // ── Centered content via StackPane ───────────────────────────────────
        StackPane centerWrapper = new StackPane();
        centerWrapper.setPrefSize(DISPLAY_W, DISPLAY_H);
        centerWrapper.setMinSize(DISPLAY_W, DISPLAY_H);
        centerWrapper.setMaxSize(DISPLAY_W, DISPLAY_H);
        centerWrapper.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(centerWrapper, 0.0);
        AnchorPane.setLeftAnchor(centerWrapper, 0.0);
        canvas.getChildren().add(centerWrapper);

        // ── Modal card panel (.modal-card) ───────────────────────────────────
        VBox card = new VBox(14);
        card.getStyleClass().add("modal-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(480);
        card.setPadding(new Insets(36, 48, 36, 48));

        // ── Title ────────────────────────────────────────────────────────────
        Label title = new Label("CONECTAR A PARTIDA LAN");
        title.getStyleClass().add("modal-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        // ── IP field ─────────────────────────────────────────────────────────
        TextField ipField = new TextField("127.0.0.1");
        ipField.setPromptText("Dirección IP del host");
        ipField.setMaxWidth(380);
        ipField.getStyleClass().add("lan-input-field");

        // ── Port field ───────────────────────────────────────────────────────
        TextField portField = new TextField(String.valueOf(LanConstants.DEFAULT_PORT));
        portField.setPromptText("Puerto (1–65535)");
        portField.setMaxWidth(380);
        portField.getStyleClass().add("lan-input-field");

        // ── Error label (hidden by default) ──────────────────────────────────
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("lan-error-label");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(380);
        errorLabel.setWrapText(true);

        // ── Buttons ──────────────────────────────────────────────────────────
        HBox buttonBar = new HBox(24);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        Button connectBtn = new Button("CONECTAR");
        connectBtn.getStyleClass().add("btn-yes");

        Button volverBtn = new Button("VOLVER");
        volverBtn.getStyleClass().add("btn-no");

        buttonBar.getChildren().addAll(connectBtn, volverBtn);

        // ── Assemble card ────────────────────────────────────────────────────
        card.getChildren().addAll(
            title,
            ipField,
            portField,
            errorLabel,
            buttonBar
        );
        centerWrapper.getChildren().add(card);

        // ── Shared submit logic ──────────────────────────────────────────────
        Runnable submit = () -> {
            // Clear previous error
            hideError(errorLabel);

            String host = ipField.getText().trim();
            if (host.isEmpty()) {
                showError(errorLabel, "La dirección IP no puede estar vacía.");
                return;
            }

            String portText = portField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(portText);
                if (port < 1 || port > 65535) {
                    showError(errorLabel, "El puerto debe estar entre 1 y 65535.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError(errorLabel, "El puerto debe ser un número entero (1–65535).");
                return;
            }

            if (!future.isDone()) {
                future.complete(new ConnectInfo(host, port));
            }
            stage.close();
        };

        connectBtn.setOnAction(event -> submit.run());
        // Enter on port field triggers connect
        portField.setOnAction(event -> submit.run());
        // Enter on IP field moves focus to port field
        ipField.setOnAction(event -> portField.requestFocus());

        // ── "VOLVER" cancels ─────────────────────────────────────────────────
        volverBtn.setOnAction(event -> cancel(stage, future));

        // ── Clear error on text change ───────────────────────────────────────
        portField.textProperty().addListener((obs, old, val) -> hideError(errorLabel));
        ipField.textProperty().addListener((obs, old, val) -> hideError(errorLabel));

        // ── Scene ────────────────────────────────────────────────────────────
        Scene scene = new Scene(canvas, DISPLAY_W, DISPLAY_H, Color.BLACK);
        loadStyles(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancel(stage, future);
            }
        });

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> cancel(stage, future));
        return stage;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void loadStyles(Scene scene) {
        var fontsCss = ConnectDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var modalCss = ConnectDialog.class.getResource(MODAL_CSS_PATH);
        if (modalCss != null) scene.getStylesheets().add(modalCss.toExternalForm());
        var chromeCss = ConnectDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) scene.getStylesheets().add(chromeCss.toExternalForm());
    }

    private static void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private static void hideError(Label errorLabel) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }

    private static void cancel(Stage stage, CompletableFuture<ConnectInfo> future) {
        if (!future.isDone()) {
            future.complete(null);
        }
        stage.close();
    }
}
