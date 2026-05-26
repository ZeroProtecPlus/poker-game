package view.fx;

import java.util.concurrent.CompletableFuture;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

/**
 * ExitConfirmDialog — casino-styled modal to confirm leaving a game.
 *
 * <p>SALIR / ENTER = confirmed (returns {@code true} to caller).
 * <p>CANCELAR / ESC / X = cancelled (returns {@code false}).
 * <p>The caller is responsible for deciding what to do with the result
 * (e.g. kill the game thread, return to menu, etc.).
 *
 * <p>CSS: fonts.css + modal.css + menu-chrome.css
 */
public final class ExitConfirmDialog {

    private static final String BG_PATH = "/menu-bg.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String MODAL_CSS_PATH = "/modal.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double WIDTH = 520;
    private static final double HEIGHT = 340;

    private ExitConfirmDialog() {}

    /**
     * Shows the exit-confirmation dialog and blocks until the user chooses.
     *
     * @return {@code true} if the user confirmed exit, {@code false} otherwise
     */
    public static boolean showAndWaitBlocking() {
        return Boolean.TRUE.equals(JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = buildStage(future);
            stage.showAndWait();
            return future.getNow(false);
        }));
    }

    private static Stage buildStage(CompletableFuture<Boolean> future) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Royal Poker — Salir");

        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(WIDTH, HEIGHT);
        canvas.setMinSize(WIDTH, HEIGHT);
        canvas.setMaxSize(WIDTH, HEIGHT);

        ImageView bg = MenuImages.fullCanvasLayer(BG_PATH);
        bg.setFitWidth(WIDTH);
        bg.setFitHeight(HEIGHT);
        AnchorPane.setTopAnchor(bg, 0.0);
        AnchorPane.setLeftAnchor(bg, 0.0);
        canvas.getChildren().add(bg);

        FxMenuChrome.apply(stage, canvas, "Royal Poker", WIDTH, HEIGHT);

        StackPane centerWrapper = new StackPane();
        centerWrapper.setPrefSize(WIDTH, HEIGHT);
        centerWrapper.setMinSize(WIDTH, HEIGHT);
        centerWrapper.setMaxSize(WIDTH, HEIGHT);
        centerWrapper.setAlignment(Pos.CENTER);
        AnchorPane.setTopAnchor(centerWrapper, 0.0);
        AnchorPane.setLeftAnchor(centerWrapper, 0.0);
        canvas.getChildren().add(centerWrapper);

        VBox card = new VBox(16);
        card.getStyleClass().add("exit-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(430);
        card.setPadding(new Insets(28, 36, 24, 36));

        Label iconLabel = new Label("\u26A0");
        iconLabel.getStyleClass().add("modal-title");
        iconLabel.getStyleClass().add("exit-warning-icon");

        Label message = new Label("¿Salir de la partida?\nSe perderá el progreso.");
        message.getStyleClass().add("modal-question");
        message.setWrapText(true);
        message.setAlignment(Pos.CENTER);
        message.setMaxWidth(380);

        Button salirBtn = new Button("SALIR");
        salirBtn.getStyleClass().add("btn-no");
        salirBtn.setOnAction(e -> {
            future.complete(true);
            stage.close();
        });

        Button cancelarBtn = new Button("CANCELAR");
        cancelarBtn.getStyleClass().add("btn-yes");
        cancelarBtn.setOnAction(e -> {
            future.complete(false);
            stage.close();
        });

        HBox buttonBox = new HBox(24);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(4, 0, 0, 0));
        buttonBox.getChildren().addAll(salirBtn, cancelarBtn);
        card.getChildren().addAll(iconLabel, message, buttonBox);
        centerWrapper.getChildren().add(card);

        Scene scene = new Scene(canvas, WIDTH, HEIGHT, Color.BLACK);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                future.complete(false);
                stage.close();
            } else if (event.getCode() == KeyCode.ENTER) {
                future.complete(true);
                stage.close();
            }
        });

        var fontsCss = ExitConfirmDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var modalCss = ExitConfirmDialog.class.getResource(MODAL_CSS_PATH);
        if (modalCss != null) scene.getStylesheets().add(modalCss.toExternalForm());
        var chromeCss = ExitConfirmDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) scene.getStylesheets().add(chromeCss.toExternalForm());

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!future.isDone()) {
                future.complete(false);
            }
        });

        return stage;
    }
}
