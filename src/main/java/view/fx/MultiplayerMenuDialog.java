package view.fx;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

import java.util.concurrent.CompletableFuture;

/**
 * Second step after Multiplayer: host or join (LAN flow wired from {@link Main}).
 * Full-canvas Star_Game.png background (source sprite), clean overlay for buttons.
 */
public final class MultiplayerMenuDialog {

    public enum Choice {
        HOST,
        JOIN,
        BACK,
        CLOSED
    }

    private static final String BG_PATH = "/sprites/Star_Game.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/multiplayer-menu.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";
    private static final double W = StartMenuLayoutConstants.CANVAS_WIDTH;
    private static final double H = StartMenuLayoutConstants.CANVAS_HEIGHT;

    private MultiplayerMenuDialog() {}

    public static Choice showAndWaitBlocking() {
        return JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<Choice> future = new CompletableFuture<>();
            Stage stage = buildStage(future);
            stage.showAndWait();
            return future.getNow(Choice.CLOSED);
        });
    }

    private static Stage buildStage(CompletableFuture<Choice> future) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Multijugador");

        // ── Full-canvas Star_Game.png background (same source as StartMenu) ──
        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(W, H);
        canvas.setMinSize(W, H);
        canvas.setMaxSize(W, H);

        Image backgroundImage = MenuImages.load(BG_PATH);
        ImageView backgroundView = MenuImages.nativeSizedView(backgroundImage, "start-menu-base-image");
        backgroundView.setMouseTransparent(true);
        AnchorPane.setTopAnchor(backgroundView, 0.0);
        AnchorPane.setLeftAnchor(backgroundView, 0.0);
        canvas.getChildren().add(backgroundView);

        // ── Overlay content — floating on the sprite background ──────────────
        Label title = new Label("Multijugador LAN");
        title.getStyleClass().add("multiplayer-title");

        Button hostBtn = new Button("Crear partida (Host)");
        hostBtn.getStyleClass().add("multiplayer-btn");
        hostBtn.setOnAction(event -> complete(stage, future, Choice.HOST));

        Button joinBtn = new Button("Unirse a partida");
        joinBtn.getStyleClass().add("multiplayer-btn");
        joinBtn.setOnAction(event -> complete(stage, future, Choice.JOIN));

        Button backBtn = new Button("Volver al menú");
        backBtn.getStyleClass().addAll("multiplayer-btn", "multiplayer-btn-back");
        backBtn.setOnAction(event -> complete(stage, future, Choice.BACK));

        // Transparent card — no background panel, buttons float directly on sprite
        VBox panel = new VBox(18, title, hostBtn, joinBtn, backBtn);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(560);

        StackPane panelStack = new StackPane(panel);
        panelStack.setPickOnBounds(false);
        AnchorPane.setTopAnchor(panelStack, 0.0);
        AnchorPane.setBottomAnchor(panelStack, 0.0);
        AnchorPane.setLeftAnchor(panelStack, 0.0);
        AnchorPane.setRightAnchor(panelStack, 0.0);
        canvas.getChildren().add(panelStack);

        FxMenuChrome.apply(stage, canvas, "Multijugador");

        StackPane root = new StackPane(canvas);
        root.setAlignment(Pos.CENTER);
        root.setBackground(Background.EMPTY);

        Scene scene = new Scene(root, W, H, Color.BLACK);
        var fontsCss = MultiplayerMenuDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var css = MultiplayerMenuDialog.class.getResource(CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        var chromeCss = MultiplayerMenuDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) {
            scene.getStylesheets().add(chromeCss.toExternalForm());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                complete(stage, future, Choice.BACK);
            }
        });

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> complete(stage, future, Choice.CLOSED));
        return stage;
    }

    private static void complete(Stage stage, CompletableFuture<Choice> future, Choice choice) {
        if (!future.isDone()) {
            future.complete(choice);
        }
        stage.close();
    }
}
