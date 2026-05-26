package view.fx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        // Replaced built-in tick labels with manual Label nodes for proper
        // visibility across the full value range (fix-raise-slider-and-connection).
        slider.setShowTickLabels(false);
        slider
            .valueProperty()
            .addListener((obs, old, val) ->
                valueLabel.setText(String.valueOf(val.intValue()))
            );

        // Manual tick labels — positioned along the slider track via width bindings.
        javafx.scene.layout.Pane tickLabelsBox = buildTickLabelsBox(minBet, maxBet, slider);

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
            .addAll(title, rangeLabel, valueLabel, slider, tickLabelsBox, buttons);

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

    /**
     * Builds a pane of manual tick labels positioned along the slider track.
     * Each label's {@code layoutX} is bound to the slider width at its
     * computed fraction so they reflow on DPI/scale changes.
     */
    private static javafx.scene.layout.Pane buildTickLabelsBox(
        int minBet, int maxBet, Slider slider
    ) {
        List<TickInfo> ticks = computeTicks(minBet, maxBet);
        javafx.scene.layout.Pane pane = new javafx.scene.layout.Pane();
        pane.getStyleClass().add("tick-labels-box");
        pane.setMinHeight(20);
        pane.setPrefHeight(22);

        // Bind the pane width to the slider width so labels stay aligned
        pane.prefWidthProperty().bind(slider.widthProperty());
        pane.minWidthProperty().bind(slider.widthProperty());

        for (TickInfo tick : ticks) {
            Label lbl = new Label(formatTickValue(tick.value));
            lbl.getStyleClass().add("tick-label");
            lbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

            // Position at the fraction along the slider width, centered
            // Offset by half the label width so "center" of label aligns
            // with the tick mark position on the slider.
            lbl.layoutXProperty().bind(
                slider.widthProperty()
                    .multiply(tick.fraction)
                    .subtract(lbl.widthProperty().divide(2.0))
            );

            pane.getChildren().add(lbl);
        }
        return pane;
    }

    /**
     * Formats a tick value for compact display (e.g. "1.5K" instead of "1500").
     */
    private static String formatTickValue(int value) {
        if (value >= 1000) {
            if (value % 1000 == 0) {
                return (value / 1000) + "K";
            }
            return String.format("%.1fK", value / 1000.0);
        }
        return String.valueOf(value);
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
     * Tick label info: value to display and its fraction position along the slider
     * track (0.0 = far left, 1.0 = far right).
     */
    public record TickInfo(int value, double fraction) {}

    /**
     * Computes evenly spaced tick values between min and max, returning
     * their display values and fractional positions along the slider track.
     *
     * For extreme ranges where labels would overlap, reduces the count
     * but always includes the minimum and maximum values.
     */
    public static List<TickInfo> computeTicks(int min, int max) {
        if (max <= min) {
            return Collections.singletonList(new TickInfo(min, 0.5));
        }

        int range = max - min;
        // Determine tick count: start with 5, reduce if range is extreme enough
        // that labels would overlap in ~320px of visual space.
        int tickCount = 5;
        if (range > 20000) {
            tickCount = 4;
        }
        if (range > 50000) {
            tickCount = 3;
        }

        List<TickInfo> ticks = new ArrayList<>(tickCount);
        for (int i = 0; i < tickCount; i++) {
            double fraction = (double) i / (tickCount - 1);
            int value = min + (int) Math.round(fraction * range);
            ticks.add(new TickInfo(value, fraction));
        }
        return ticks;
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
