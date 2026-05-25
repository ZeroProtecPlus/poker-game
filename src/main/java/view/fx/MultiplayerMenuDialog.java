package view.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Second step after Multiplayer: host or join (LAN flow wired from {@link Main}).
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
    private static final double DESIGN_WIDTH = MenuLayoutConstants.DESIGN_WIDTH;
    private static final double DESIGN_HEIGHT = MenuLayoutConstants.DESIGN_HEIGHT;

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

        ImageView background = imageViewAtNativeSize(loadImage(BG_PATH));

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

        VBox panel = new VBox(18, title, hostBtn, joinBtn, backBtn);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(40));
        panel.setMaxWidth(520);
        panel.setStyle("-fx-background-color: rgba(6, 14, 10, 0.55); -fx-background-radius: 16;");

        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMinSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMaxSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        AnchorPane.setTopAnchor(background, 0.0);
        AnchorPane.setLeftAnchor(background, 0.0);
        canvas.getChildren().add(background);

        StackPane panelStack = new StackPane(panel);
        panelStack.setPickOnBounds(false);
        AnchorPane.setTopAnchor(panelStack, 0.0);
        AnchorPane.setBottomAnchor(panelStack, 0.0);
        AnchorPane.setLeftAnchor(panelStack, 0.0);
        AnchorPane.setRightAnchor(panelStack, 0.0);
        canvas.getChildren().add(panelStack);

        FxMenuChrome.apply(stage, canvas, "Multijugador");

        Scene scene = new Scene(canvas, DESIGN_WIDTH, DESIGN_HEIGHT, Color.BLACK);
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

    private static ImageView imageViewAtNativeSize(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setFitWidth(image.getWidth());
        view.setFitHeight(image.getHeight());
        return view;
    }

    private static Image loadImage(String classpathResource) {
        InputStream stream = MultiplayerMenuDialog.class.getResourceAsStream(classpathResource);
        if (stream == null) {
            throw new IllegalStateException("Resource not found: " + classpathResource);
        }
        return new Image(stream);
    }

    private static void complete(Stage stage, CompletableFuture<Choice> future, Choice choice) {
        if (!future.isDone()) {
            future.complete(choice);
        }
        stage.close();
    }
}
