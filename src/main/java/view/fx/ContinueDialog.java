package view.fx;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * ContinueDialog — Image-based casino modal for "Play again?" prompt.
 * Shows the pre-rendered continuar_partida.png image with YES/NO overlays.
 * Invisible click zones over the buttons respond to mouse input with hover feedback.
 */
public class ContinueDialog {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String IMAGE_PATH = "/sprites/continuar_partida.png";
    private static final String YES_OVERLAY_PATH = "/sprites/continuar_partida_yes.png";
    private static final String NO_OVERLAY_PATH = "/sprites/continuar_partida_no.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String MODAL_CSS_PATH = "/modal.css";

    private static final String QUESTION_TEXT = "Le damos otra partida?";
    private static final String POKEFICHAS_LABEL_PREFIX = "Total PokeFichas: ";

    /** Target display width; height scales proportionally. */
    private static final double TARGET_WIDTH = 420;

    /** Design canvas — must match modal.css (.continue-dialog-canvas). */
    private static final double DESIGN_WIDTH = 1502;
    private static final double DESIGN_HEIGHT = 1428;

    /** Yes overlay — must match modal.css (.modal-yes-overlay comments). */
    private static final double YES_OVERLAY_X = 0;
    private static final double YES_OVERLAY_Y = 0;
    private static final double YES_OVERLAY_WIDTH = 1502;
    private static final double YES_OVERLAY_HEIGHT = 1428;

    /** No overlay — must match modal.css (.modal-no-overlay comments). */
    private static final double NO_OVERLAY_X = 0;
    private static final double NO_OVERLAY_Y = 0;
    private static final double NO_OVERLAY_WIDTH = 1502;
    private static final double NO_OVERLAY_HEIGHT = 1428;

    /**
     * Text layout — must match modal.css (debajo del título y centro del panel).
     * Font sizes in CSS are design pixels: visiblePx × (DESIGN_WIDTH / TARGET_WIDTH).
     */
    private static final double POKEFICHAS_LABEL_TOP = 355;
    private static final double CONTINUE_QUESTION_TOP = 640;
    private static final double TEXT_HORIZONTAL_INSET = 120;

    private static final double BUTTON_ZONE_HEIGHT_RATIO = 0.22;
    private static final double BUTTON_ZONE_BOTTOM_RATIO = 0.02;
    private static final double HOVER_SCALE = 1.06;

    /**
     * Shows the continue dialog and returns a CompletableFuture&lt;Boolean&gt;.
     * The future completes with true if the user clicks the YES zone or presses Enter,
     * false if the user clicks the NO zone or presses Escape.
     *
     * @param chips current human chip count ({@code User.getNumbChips()} via {@code GameView.askPlayAgain})
     * @param owner the owner stage (game table) so this dialog only blocks that window
     * @return CompletableFuture&lt;Boolean&gt; that completes with the user's choice
     */
    public static CompletableFuture<Boolean> showAndWait(int chips, Stage owner) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                Stage stage = createStage(future, chips, owner);
                stage.show();
            } catch (Exception ex) {
                future.complete(false);
            }
        });

        return future;
    }

    private static Stage createStage(CompletableFuture<Boolean> future, int chips, Stage owner) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(owner);
        stage.setTitle("¿Seguir jugando?");

        StackPane root = new StackPane();
        root.setBackground(Background.EMPTY);
        root.setStyle("-fx-background-color: transparent;");
        root.setAlignment(Pos.CENTER);

        Image modalImage;
        ImageView modalImageView;
        try {
            modalImage = loadImage(IMAGE_PATH);
            modalImageView = createUnscaledImageView(modalImage, DESIGN_WIDTH, DESIGN_HEIGHT);
            modalImageView.getStyleClass().add("modal-base-image");
        } catch (Exception ex) {
            System.err.println("ContinueDialog: Could not load " + IMAGE_PATH + " — " + ex.getMessage());
            future.complete(false);
            stage.close();
            return stage;
        }

        AnchorPane canvas = new AnchorPane();
        canvas.setBackground(Background.EMPTY);
        canvas.setStyle("-fx-background-color: transparent;");
        canvas.getStyleClass().add("continue-dialog-canvas");
        canvas.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMinSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMaxSize(DESIGN_WIDTH, DESIGN_HEIGHT);

        AnchorPane.setTopAnchor(modalImageView, 0.0);
        AnchorPane.setLeftAnchor(modalImageView, 0.0);
        canvas.getChildren().add(modalImageView);

        ImageView yesOverlayView = loadOverlayView(
            YES_OVERLAY_PATH, YES_OVERLAY_X, YES_OVERLAY_Y,
            YES_OVERLAY_WIDTH, YES_OVERLAY_HEIGHT, "modal-yes-overlay"
        );
        ImageView noOverlayView = loadOverlayView(
            NO_OVERLAY_PATH, NO_OVERLAY_X, NO_OVERLAY_Y,
            NO_OVERLAY_WIDTH, NO_OVERLAY_HEIGHT, "modal-no-overlay"
        );
        addOverlayIfPresent(canvas, yesOverlayView);
        addOverlayIfPresent(canvas, noOverlayView);

        addTextLabels(canvas, chips);

        double displayScale = TARGET_WIDTH / DESIGN_WIDTH;
        double displayH = DESIGN_HEIGHT * displayScale;

        AnchorPane clickZones = createClickZones(stage, future, yesOverlayView, noOverlayView);
        canvas.getChildren().add(clickZones);

        canvas.setScaleX(displayScale);
        canvas.setScaleY(displayScale);

        StackPane content = new StackPane();
        content.setBackground(Background.EMPTY);
        content.setStyle("-fx-background-color: transparent;");
        content.getChildren().add(canvas);
        content.setMaxWidth(TARGET_WIDTH);
        content.setMaxHeight(displayH);
        content.setPrefWidth(TARGET_WIDTH);
        content.setPrefHeight(displayH);

        root.getChildren().add(content);
        root.setPrefSize(TARGET_WIDTH, displayH);

        Scene scene = new Scene(root, TARGET_WIDTH, displayH, Color.TRANSPARENT);
        var fontsCss = ContinueDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var css = ContinueDialog.class.getResource(MODAL_CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                future.complete(true);
                stage.close();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                future.complete(false);
                stage.close();
            }
        });

        stage.setScene(scene);

        root.setOpacity(0.0);
        root.setScaleX(0.90);
        root.setScaleY(0.90);

        Timeline entrance = new Timeline(
            new KeyFrame(
                Duration.ZERO,
                new KeyValue(root.opacityProperty(), 0.0),
                new KeyValue(root.scaleXProperty(), 0.90),
                new KeyValue(root.scaleYProperty(), 0.90)
            ),
            new KeyFrame(
                Duration.millis(250),
                new KeyValue(root.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(root.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                new KeyValue(root.scaleYProperty(), 1.0, Interpolator.EASE_OUT)
            )
        );
        stage.setOnShown(event -> entrance.play());

        scheduleTimeout(stage, future);

        return stage;
    }

    private static void addTextLabels(AnchorPane canvas, int chips) {
        String pokefichasText = POKEFICHAS_LABEL_PREFIX + formatChips(chips);

        Label pokefichasLabel = new Label(pokefichasText);
        pokefichasLabel.getStyleClass().add("modal-pokefichas-label");
        pokefichasLabel.setWrapText(true);
        pokefichasLabel.setAlignment(Pos.CENTER);
        pokefichasLabel.setMaxWidth(DESIGN_WIDTH - TEXT_HORIZONTAL_INSET * 2);
        AnchorPane.setTopAnchor(pokefichasLabel, POKEFICHAS_LABEL_TOP);
        AnchorPane.setLeftAnchor(pokefichasLabel, TEXT_HORIZONTAL_INSET);
        AnchorPane.setRightAnchor(pokefichasLabel, TEXT_HORIZONTAL_INSET);

        Label questionLabel = new Label(QUESTION_TEXT);
        questionLabel.getStyleClass().add("modal-continue-question");
        questionLabel.setWrapText(true);
        questionLabel.setAlignment(Pos.CENTER);
        questionLabel.setMaxWidth(DESIGN_WIDTH - 200);
        AnchorPane.setTopAnchor(questionLabel, CONTINUE_QUESTION_TOP);
        AnchorPane.setLeftAnchor(questionLabel, 100.0);
        AnchorPane.setRightAnchor(questionLabel, 100.0);

        canvas.getChildren().addAll(pokefichasLabel, questionLabel);
    }

    /** Same formatting as {@link view.GameView#askPlayAgain(int)} fallback dialog. */
    private static String formatChips(int chips) {
        return String.format(Locale.US, "%,d", Math.max(0, chips));
    }

    private static AnchorPane createClickZones(
        Stage stage,
        CompletableFuture<Boolean> future,
        ImageView yesOverlayView,
        ImageView noOverlayView
    ) {
        AnchorPane pane = new AnchorPane();
        pane.setBackground(Background.EMPTY);
        pane.setStyle("-fx-background-color: transparent;");
        pane.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        pane.setMaxSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        pane.setPickOnBounds(false);

        double zoneHeight = DESIGN_HEIGHT * BUTTON_ZONE_HEIGHT_RATIO;
        double zoneBottom = DESIGN_HEIGHT * BUTTON_ZONE_BOTTOM_RATIO;
        double pivotY = DESIGN_HEIGHT - zoneBottom - zoneHeight / 2 + 12;
        double yesPivotX = DESIGN_WIDTH / 4;
        double noPivotX = DESIGN_WIDTH * 3 / 4;

        Region yesZone = new Region();
        yesZone.getStyleClass().add("modal-btn-hit-yes");
        yesZone.setPrefWidth(DESIGN_WIDTH / 2);
        yesZone.setPrefHeight(zoneHeight);
        yesZone.setOnMouseClicked(event -> {
            future.complete(true);
            stage.close();
        });
        AnchorPane.setBottomAnchor(yesZone, zoneBottom);
        AnchorPane.setLeftAnchor(yesZone, 0.0);
        bindButtonHover(yesZone, yesOverlayView, yesPivotX, pivotY, "modal-yes-overlay-hover");

        Region noZone = new Region();
        noZone.getStyleClass().add("modal-btn-hit-no");
        noZone.setPrefWidth(DESIGN_WIDTH / 2);
        noZone.setPrefHeight(zoneHeight);
        noZone.setOnMouseClicked(event -> {
            future.complete(false);
            stage.close();
        });
        AnchorPane.setBottomAnchor(noZone, zoneBottom);
        AnchorPane.setRightAnchor(noZone, 0.0);
        bindButtonHover(noZone, noOverlayView, noPivotX, pivotY, "modal-no-overlay-hover");

        pane.getChildren().addAll(yesZone, noZone);
        return pane;
    }

    private static void bindButtonHover(
        Region hitZone,
        ImageView overlayView,
        double pivotX,
        double pivotY,
        String hoverStyleClass
    ) {
        if (overlayView == null) {
            return;
        }

        Scale hoverScale = new Scale(1.0, 1.0, pivotX, pivotY);
        overlayView.getTransforms().add(hoverScale);

        hitZone.setOnMouseEntered(event -> {
            hoverScale.setX(HOVER_SCALE);
            hoverScale.setY(HOVER_SCALE);
            if (!overlayView.getStyleClass().contains(hoverStyleClass)) {
                overlayView.getStyleClass().add(hoverStyleClass);
            }
            ColorAdjust brighten = new ColorAdjust(0, 0, 0.14, 0);
            overlayView.setEffect(brighten);
        });

        hitZone.setOnMouseExited(event -> {
            hoverScale.setX(1.0);
            hoverScale.setY(1.0);
            overlayView.getStyleClass().remove(hoverStyleClass);
            overlayView.setEffect(null);
        });
    }

    private static Image loadImage(String classpathResource) {
        InputStream is = ContinueDialog.class.getResourceAsStream(classpathResource);
        if (is == null) {
            throw new RuntimeException("Resource not found on classpath: " + classpathResource);
        }
        return new Image(is);
    }

    private static ImageView createUnscaledImageView(Image image, double width, double height) {
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        return imageView;
    }

    private static void addOverlayIfPresent(AnchorPane canvas, ImageView overlayView) {
        if (overlayView != null) {
            canvas.getChildren().add(overlayView);
        }
    }

    private static ImageView loadOverlayView(
        String classpathResource,
        double x,
        double y,
        double width,
        double height,
        String styleClass
    ) {
        try {
            Image overlayImage = loadImage(classpathResource);
            ImageView overlayView = createUnscaledImageView(overlayImage, width, height);
            overlayView.getStyleClass().add(styleClass);
            AnchorPane.setTopAnchor(overlayView, y);
            AnchorPane.setLeftAnchor(overlayView, x);
            return overlayView;
        } catch (Exception ex) {
            System.err.println(
                "ContinueDialog: Could not load " + classpathResource + " — " + ex.getMessage()
            );
            return null;
        }
    }

    private static void scheduleTimeout(Stage stage, CompletableFuture<Boolean> future) {
        Thread timeoutThread = new Thread(() -> {
            try {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                Platform.runLater(() -> {
                    future.complete(false);
                    stage.close();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    future.complete(false);
                    stage.close();
                });
            }
        }, "continue-dialog-timeout");
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }
}
