package view.fx;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.concurrent.CompletableFuture;

/**
 * Menú de inicio 1672×941.
 *
 * <p>Capas (z-index de atrás hacia adelante):
 * <ol>
 *   <li>{@code Star_Game.png} — fondo opaco completo</li>
 *   <li>Overlays PNG completos con alpha ({@code Star_Game_*.png}) en (0,0)</li>
 *   <li>Zonas de clic + hover sobre los píxeles opacos de cada botón</li>
 *   <li>Barra minimizar/cerrar</li>
 * </ol>
 * Los PNG de botón NO se recortan en (436,340): el arte está en ~632×345 según el alpha del archivo.
 */
public final class StartMenuDialog {

    public enum Choice {
        SINGLE_PLAYER,
        MULTIPLAYER,
        SETTINGS,
        CLOSED
    }

    private static final String BG_PATH = "/sprites/Star_Game.png";
    private static final String SINGLE_OVERLAY = "/sprites/Star_Game_singleplayer.png";
    private static final String MULTI_OVERLAY = "/sprites/Star_Game_multiplayer.png";
    private static final String SETTINGS_OVERLAY = "/sprites/Star_Game_settings.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/start-menu.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double W = StartMenuLayoutConstants.CANVAS_WIDTH;
    private static final double H = StartMenuLayoutConstants.CANVAS_HEIGHT;

    private StartMenuDialog() {}

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
        stage.setTitle("Royal Poker");

        AnchorPane canvas = new AnchorPane();
        canvas.getStyleClass().add("start-menu-canvas");
        canvas.setPrefSize(W, H);
        canvas.setMinSize(W, H);
        canvas.setMaxSize(W, H);

        Image backgroundImage = MenuImages.load(BG_PATH);
        ImageView backgroundView = MenuImages.nativeSizedView(backgroundImage, "start-menu-base-image");
        backgroundView.setMouseTransparent(true);
        AnchorPane.setTopAnchor(backgroundView, 0.0);
        AnchorPane.setLeftAnchor(backgroundView, 0.0);
        canvas.getChildren().add(backgroundView);

        ImageView singleLayer = MenuButtonSlot.addFullCanvasOverlay(canvas, SINGLE_OVERLAY);
        ImageView multiLayer = MenuButtonSlot.addFullCanvasOverlay(canvas, MULTI_OVERLAY);
        ImageView settingsLayer = MenuButtonSlot.addFullCanvasOverlay(canvas, SETTINGS_OVERLAY);

        var singleHit = MenuButtonSlot.addHitZone(
            canvas,
            StartMenuLayoutConstants.SINGLE_X,
            StartMenuLayoutConstants.SINGLE_Y,
            StartMenuLayoutConstants.SINGLE_W,
            StartMenuLayoutConstants.SINGLE_H,
            () -> complete(stage, future, Choice.SINGLE_PLAYER)
        );
        var multiHit = MenuButtonSlot.addHitZone(
            canvas,
            StartMenuLayoutConstants.MULTI_X,
            StartMenuLayoutConstants.MULTI_Y,
            StartMenuLayoutConstants.MULTI_W,
            StartMenuLayoutConstants.MULTI_H,
            () -> complete(stage, future, Choice.MULTIPLAYER)
        );
        var settingsHit = MenuButtonSlot.addHitZone(
            canvas,
            StartMenuLayoutConstants.SETTINGS_X,
            StartMenuLayoutConstants.SETTINGS_Y,
            StartMenuLayoutConstants.SETTINGS_W,
            StartMenuLayoutConstants.SETTINGS_H,
            () -> complete(stage, future, Choice.SETTINGS)
        );

        orderLayers(canvas, backgroundView, singleLayer, multiLayer, settingsLayer,
            singleHit, multiHit, settingsHit);

        FxMenuChrome.apply(stage, canvas, "Royal Poker");

        StackPane root = new StackPane(canvas);
        root.setAlignment(Pos.CENTER);
        root.setBackground(Background.EMPTY);

        Scene scene = new Scene(root, W, H, Color.BLACK);
        loadStyles(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                complete(stage, future, Choice.CLOSED);
            }
        });

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> complete(stage, future, Choice.CLOSED));
        return stage;
    }

    /**
     * Z-order explícito: fondo → overlays con alpha → hit zones → chrome (ya al frente).
     */
    private static void orderLayers(
        AnchorPane canvas,
        ImageView background,
        ImageView singleLayer,
        ImageView multiLayer,
        ImageView settingsLayer,
        StackPane singleHit,
        StackPane multiHit,
        StackPane settingsHit
    ) {
        background.toBack();
        singleLayer.toFront();
        multiLayer.toFront();
        settingsLayer.toFront();
        singleHit.toFront();
        multiHit.toFront();
        settingsHit.toFront();
        canvas.getChildren().stream()
            .filter(node -> node.getStyleClass().contains("menu-chrome-bar")
                || node instanceof javafx.scene.layout.HBox)
            .forEach(javafx.scene.Node::toFront);
    }

    private static void loadStyles(Scene scene) {
        var fontsCss = StartMenuDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var menuCss = StartMenuDialog.class.getResource(CSS_PATH);
        if (menuCss != null) {
            scene.getStylesheets().add(menuCss.toExternalForm());
        }
        var chromeCss = StartMenuDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) {
            scene.getStylesheets().add(chromeCss.toExternalForm());
        }
    }

    private static void complete(Stage stage, CompletableFuture<Choice> future, Choice choice) {
        if (!future.isDone()) {
            future.complete(choice);
        }
        stage.close();
    }
}
