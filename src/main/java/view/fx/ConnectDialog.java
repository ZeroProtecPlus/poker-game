package view.fx;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
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
 *   Full-canvas Star_Game.png background (same source sprite as StartMenu)
 *   FxMenuChrome title bar "Unirse a partida"
 *   Transparent overlay with minimal card containing:
 *     - .multiplayer-title "CONECTAR A PARTIDA LAN"
 *     - 2 TextFields: IP (default 127.0.0.1), Puerto (default 9876)
 *     - .lan-error-label for inline validation errors
 *     - "CONECTAR" (.multiplayer-btn) / "VOLVER" (.multiplayer-btn .multiplayer-btn-back) buttons
 *   CSS: fonts.css + multiplayer-menu.css + menu-chrome.css
 */
public final class ConnectDialog {

    public record ConnectInfo(String host, int port) {}

    private static final String BG_PATH = "/sprites/Star_Game.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/multiplayer-menu.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double DISPLAY_W = StartMenuLayoutConstants.CANVAS_WIDTH;
    private static final double DISPLAY_H = StartMenuLayoutConstants.CANVAS_HEIGHT;

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

        // ── Root canvas with Star_Game.png background ────────────────────────
        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(DISPLAY_W, DISPLAY_H);
        canvas.setMinSize(DISPLAY_W, DISPLAY_H);
        canvas.setMaxSize(DISPLAY_W, DISPLAY_H);

        Image backgroundImage = MenuImages.load(BG_PATH);
        ImageView backgroundView = MenuImages.nativeSizedView(backgroundImage, "start-menu-base-image");
        backgroundView.setMouseTransparent(true);
        AnchorPane.setTopAnchor(backgroundView, 0.0);
        AnchorPane.setLeftAnchor(backgroundView, 0.0);
        canvas.getChildren().add(backgroundView);

        // ── Chrome title bar ─────────────────────────────────────────────────
        FxMenuChrome.apply(stage, canvas, "Unirse a partida");

        // ── Centered content via StackPane ───────────────────────────────────
        StackPane centerWrapper = new StackPane();
        centerWrapper.setPickOnBounds(false);
        AnchorPane.setTopAnchor(centerWrapper, 0.0);
        AnchorPane.setLeftAnchor(centerWrapper, 0.0);
        AnchorPane.setBottomAnchor(centerWrapper, 0.0);
        AnchorPane.setRightAnchor(centerWrapper, 0.0);
        canvas.getChildren().add(centerWrapper);

        // ── Transparent card — no background panel, floats on sprite ─────────
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(500);

        // ── Title ────────────────────────────────────────────────────────────
        Label title = new Label("CONECTAR A PARTIDA LAN");
        title.getStyleClass().add("multiplayer-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        // ── IP field ─────────────────────────────────────────────────────────
        TextField ipField = new TextField("127.0.0.1");
        ipField.setPromptText("Dirección IP del host");
        ipField.setMaxWidth(450);
        ipField.setMinWidth(400);
        ipField.getStyleClass().add("lan-input-field");

        // ── Port field ───────────────────────────────────────────────────────
        TextField portField = new TextField(String.valueOf(LanConstants.DEFAULT_PORT));
        portField.setPromptText("Puerto (1–65535)");
        portField.setMaxWidth(450);
        portField.setMinWidth(400);
        portField.getStyleClass().add("lan-input-field");

        // ── Error label (hidden by default) ──────────────────────────────────
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("lan-error-label");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(450);
        errorLabel.setWrapText(true);

        // ── Buttons stacked vertically (VBox, same width as inputs) ──────────
        VBox buttonBar = new VBox(14);
        buttonBar.setAlignment(Pos.CENTER);

        Button connectBtn = new Button("CONECTAR");
        connectBtn.getStyleClass().add("multiplayer-btn");
        connectBtn.setMaxWidth(450);
        connectBtn.setMinWidth(400);

        Button volverBtn = new Button("VOLVER");
        volverBtn.getStyleClass().addAll("multiplayer-btn", "multiplayer-btn-back");
        volverBtn.setMaxWidth(450);
        volverBtn.setMinWidth(400);

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
        StackPane root = new StackPane(canvas);
        root.setAlignment(Pos.CENTER);
        root.setBackground(Background.EMPTY);

        Scene scene = new Scene(root, DISPLAY_W, DISPLAY_H, Color.BLACK);
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
        var css = ConnectDialog.class.getResource(CSS_PATH);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
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
