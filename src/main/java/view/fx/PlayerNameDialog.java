package view.fx;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.CompletableFuture;

/**
 * Single-player name screen with sprite overlays for input field and button.
 * Components are positioned independently over the background using absolute
 * coordinates in an AnchorPane. The button sprite has "ENTRAR" baked in.
 */
public final class PlayerNameDialog {

    private static final String BG_PATH = "/menu-bg.png";
    private static final String INPUT_OVERLAY_PATH = "/menu_input.png";
    private static final String BTN_OVERLAY_PATH = "/menu_btn_entrar.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/player-name.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double DISPLAY_W = MenuLayoutConstants.DESIGN_WIDTH;
    private static final double DISPLAY_H = MenuLayoutConstants.DESIGN_HEIGHT;

    private PlayerNameDialog() {}

    public static String showAndWaitBlocking() {
        return JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            Stage stage = buildStage(future);
            stage.showAndWait();
            return future.getNow(null);
        });
    }

    private static Stage buildStage(CompletableFuture<String> future) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Royal Poker — Jugador");

        AnchorPane canvas = new AnchorPane();
        canvas.getStyleClass().add("player-name-canvas");
        canvas.setPrefSize(DISPLAY_W, DISPLAY_H);
        canvas.setMinSize(DISPLAY_W, DISPLAY_H);
        canvas.setMaxSize(DISPLAY_W, DISPLAY_H);

        // ── Layer 1: Background image (menu-bg.png scaled to display size) ──
        ImageView bg = MenuImages.fullCanvasLayer(BG_PATH);
        bg.setFitWidth(DISPLAY_W);
        bg.setFitHeight(DISPLAY_H);
        AnchorPane.setTopAnchor(bg, 0.0);
        AnchorPane.setLeftAnchor(bg, 0.0);
        canvas.getChildren().add(bg);

        // ── Layer 2: Input field sprite overlay (menu_input.png) ──
        ImageView inputOverlay = MenuImages.fullCanvasLayer(INPUT_OVERLAY_PATH);
        inputOverlay.setFitWidth(DISPLAY_W);
        inputOverlay.setFitHeight(DISPLAY_H);
        AnchorPane.setTopAnchor(inputOverlay, 0.0);
        AnchorPane.setLeftAnchor(inputOverlay, 0.0);
        canvas.getChildren().add(inputOverlay);

        // ── Layer 3: Button sprite overlay (menu_btn_entrar.png) ──
        ImageView btnOverlay = MenuImages.fullCanvasLayer(BTN_OVERLAY_PATH);
        btnOverlay.setFitWidth(DISPLAY_W);
        btnOverlay.setFitHeight(DISPLAY_H);
        AnchorPane.setTopAnchor(btnOverlay, 0.0);
        AnchorPane.setLeftAnchor(btnOverlay, 0.0);
        canvas.getChildren().add(btnOverlay);

        FxMenuChrome.apply(stage, canvas, "Single Player");

        // ── Layer 4: Transparent TextField over the input sprite ──
        // Scaled from native (1092, 771, 569×71) at 2752×1536 → (663, 472, 346×44) at 1672×941
        TextField nameField = new TextField();
        nameField.setPromptText("Tu nombre");
        nameField.setPrefWidth(346);
        nameField.setPrefHeight(44);
        nameField.setMaxWidth(346);
        nameField.getStyleClass().add("player-name-field");
        AnchorPane.setLeftAnchor(nameField, 663.0);
        AnchorPane.setTopAnchor(nameField, 472.0);
        canvas.getChildren().add(nameField);

        // ── Layer 5: Transparent Button over the button sprite ──
        // Scaled from native (1140, 899, 473×89) at 2752×1536 → (693, 551, 287×54) at 1672×941
        Button enterBtn = new Button();
        enterBtn.setPrefWidth(287);
        enterBtn.setPrefHeight(54);
        enterBtn.setMaxWidth(287);
        enterBtn.getStyleClass().add("player-name-enter-btn");
        AnchorPane.setLeftAnchor(enterBtn, 693.0);
        AnchorPane.setTopAnchor(enterBtn, 551.0);
        canvas.getChildren().add(enterBtn);

        // Shared submit: validate letters-only, then close stage with name
        Runnable submit = () -> {
            String name = nameField.getText().trim();
            if (name.isEmpty() || name.matches(".*[\\d\\W].*")) {
                nameField.setText("");
                nameField.setPromptText("Solo letras, sin números ni símbolos");
                nameField.getStyleClass().add("player-name-field-error");
                return;
            }
            nameField.getStyleClass().remove("player-name-field-error");
            if (!future.isDone()) {
                future.complete(name);
            }
            stage.close();
        };

        enterBtn.setOnAction(event -> submit.run());
        nameField.setOnAction(event -> submit.run()); // Enter key on TextField

        // Clear error style and restore default prompt as soon as the user types again
        nameField.textProperty().addListener((obs, oldVal, newVal) -> {
            nameField.getStyleClass().remove("player-name-field-error");
            if ("Solo letras, sin números ni símbolos".equals(nameField.getPromptText())) {
                nameField.setPromptText("Tu nombre");
            }
        });

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

    private static void loadStyles(Scene scene) {
        var fontsCss = PlayerNameDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var css = PlayerNameDialog.class.getResource(CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        var chromeCss = PlayerNameDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) {
            scene.getStylesheets().add(chromeCss.toExternalForm());
        }
    }

    private static void cancel(Stage stage, CompletableFuture<String> future) {
        if (!future.isDone()) {
            future.complete(null);
        }
        stage.close();
    }
}
