package view.fx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * BetAmountDialog — Casino-style modal for choosing bet/raise amount.
 * Uses button-bg.png as background, styled Slider, and ACCEPT/CANCEL buttons.
 * Replaces Swing JOptionPane with JSlider in GameView.
 */
public class BetAmountDialog {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String BG_PATH = "/sprites/button-bg.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/modal.css";

    /**
     * Shows the bet amount dialog and returns a CompletableFuture&lt;Integer&gt;.
     * The future completes with the chosen amount, or minBet if cancelled/timed out.
     *
     * @param minBet minimum bet amount
     * @param maxBet maximum bet amount
     * @return CompletableFuture&lt;Integer&gt; that completes with the chosen amount
     */
    public static CompletableFuture<Integer> showAndWait(
        int minBet,
        int maxBet
    ) {
        CompletableFuture<Integer> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                Stage stage = createStage(future, minBet, maxBet);
                stage.show();
            } catch (Exception ex) {
                future.complete(minBet);
            }
        });

        return future;
    }

    private static Stage createStage(
        CompletableFuture<Integer> future,
        int minBet,
        int maxBet
    ) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("¿Cuánto apuestas?");

        // Background with button-bg.png
        javafx.scene.image.ImageView bgView = new javafx.scene.image.ImageView(
            BetAmountDialog.class.getResource(BG_PATH).toExternalForm()
        );
        bgView.setPreserveRatio(false);

        // Content container
        VBox content = new VBox();
        content.setAlignment(Pos.CENTER);
        content.setSpacing(16);
        content.setStyle("-fx-padding: 48 24 24 24;");

        // Title
        Label title = new Label("¿Cuánto apuestas?");
        title.getStyleClass().add("bet-title");

        // Min/Max info
        Label rangeLabel = new Label("Mín: " + minBet + "  |  Máx: " + maxBet);
        rangeLabel.getStyleClass().add("bet-range");

        // Value display
        Label valueLabel = new Label(String.valueOf(minBet));
        valueLabel.getStyleClass().add("bet-value");

        // Slider
        Slider slider = new Slider(minBet, maxBet, minBet);
        slider.getStyleClass().add("bet-slider");
        slider.setBlockIncrement(100);
        slider.setMajorTickUnit(Math.max((maxBet - minBet) / 4, 100));
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider
            .valueProperty()
            .addListener((obs, old, val) ->
                valueLabel.setText(String.valueOf(val.intValue()))
            );

        // Buttons with custom backgrounds
        Button acceptBtn = new Button("ACEPTAR");
        acceptBtn.getStyleClass().addAll("bet-btn", "bet-btn-accept");
        acceptBtn.setPrefWidth(180);
        clipRounded(acceptBtn, 26);
        acceptBtn.setOnAction(e -> {
            future.complete((int) slider.getValue());
            stage.close();
        });

        Button cancelBtn = new Button("CANCELAR");
        cancelBtn.getStyleClass().addAll("bet-btn", "bet-btn-cancel");
        cancelBtn.setPrefWidth(180);
        clipRounded(cancelBtn, 26);
        cancelBtn.setOnAction(e -> {
            future.complete(minBet);
            stage.close();
        });

        HBox buttons = new HBox(acceptBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);
        buttons.setSpacing(16);

        content
            .getChildren()
            .addAll(title, rangeLabel, valueLabel, slider, buttons);

        // Scene setup
        StackPane root = new StackPane(bgView, content);
        Scene scene = new Scene(root, 480, 300, Color.TRANSPARENT);
        var fontsCss = BetAmountDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        scene
            .getStylesheets()
            .add(BetAmountDialog.class.getResource(CSS_PATH).toExternalForm());

        // Key handlers
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ESCAPE -> {
                    future.complete(minBet);
                    stage.close();
                }
                case ENTER -> {
                    future.complete((int) slider.getValue());
                    stage.close();
                }
                default -> {
                }
            }
        });

        // Size background to scene
        bgView.fitWidthProperty().bind(scene.widthProperty());
        bgView.fitHeightProperty().bind(scene.heightProperty());

        stage.setScene(scene);

        // Timeout
        startTimeout(stage, future, minBet);

        return stage;
    }

    private static void startTimeout(
        Stage stage,
        CompletableFuture<Integer> future,
        int minBet
    ) {
        new Thread(() -> {
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                Platform.runLater(() -> {
                    if (stage.isShowing()) {
                        future.complete(minBet);
                        stage.close();
                    }
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * Clips a button's content (including background image) to rounded corners.
     * JavaFX CSS background-image ignores background-radius, so we use
     * a Rectangle clip — same pattern used for betting buttons in GameTable.
     */
    private static void clipRounded(Button btn, int arcSize) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(arcSize);
        clip.setArcHeight(arcSize);
        clip.widthProperty().bind(btn.widthProperty());
        clip.heightProperty().bind(btn.heightProperty());
        btn.setClip(clip);
    }
}
