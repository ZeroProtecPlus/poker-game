package view.fx;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * ResumeGameDialog — asks the user whether to resume a saved game.
 * Uses continuar_partida.png casino sprite as background with a centred
 * overlay card containing the text, chips display, and solid-colour buttons.
 * Text is clean (no dropshadow), buttons are solid casino colours.
 */
public final class ResumeGameDialog {

    private static final String BG_PATH = "/sprites/continuar_partida.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String MODAL_CSS_PATH = "/modal.css";

    /** Sprite native size — must match continuar_partida.png dimensions. */
    private static final double SPRITE_W = 1502;
    private static final double SPRITE_H = 1428;

    /** Target display width (same as ContinueDialog for visual consistency). */
    private static final double DISPLAY_W = 420;
    private static final double DISPLAY_H = SPRITE_H * (DISPLAY_W / SPRITE_W);

    private ResumeGameDialog() {}

    /**
     * Shows the resume-game dialog and blocks until the user chooses.
     *
     * @param savedChips chip count from the saved game
     * @return a completed CompletableFuture&lt;Boolean&gt;
     *         (true = resume, false = new game)
     */
    public static CompletableFuture<Boolean> showAndWaitBlocking(int savedChips) {
        return JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = buildStage(future, savedChips);
            stage.showAndWait();
            return future;
        });
    }

    private static Stage buildStage(
        CompletableFuture<Boolean> future, int savedChips
    ) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Royal Poker — Reanudar partida");

        StackPane root = new StackPane();
        root.setPrefSize(DISPLAY_W, DISPLAY_H);
        root.setStyle("-fx-background-color: transparent;");

        // Layer 1: Casino sprite background (continuar_partida.png)
        ImageView bg = MenuImages.fullCanvasLayer(BG_PATH);
        bg.setFitWidth(SPRITE_W);
        bg.setFitHeight(SPRITE_H);
        // Scale the sprite to the target display width
        double scale = DISPLAY_W / SPRITE_W;
        bg.setScaleX(scale);
        bg.setScaleY(scale);
        root.getChildren().add(bg);

        // Layer 2: Card with message and buttons
        VBox card = new VBox(16);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(380);
        card.setAlignment(Pos.CENTER);

        // ── Chips label ────────────────────────────────────────────────────
        Label chipsLabel = new Label(formatChips(savedChips) + " PokeFichas");
        chipsLabel.getStyleClass().add("resume-chips");

        // ── Title ──────────────────────────────────────────────────────────
        Label titleLabel = new Label("Reanudar partida");
        titleLabel.getStyleClass().add("resume-title");

        // ── Question ───────────────────────────────────────────────────────
        String message = "Tenés una partida guardada.\n¿Querés reanudar?";
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("resume-question");
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setMaxWidth(340);

        // ── Buttons — solid colours, rounded squares, casino styling ──────
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button siBtn = new Button("SÍ");
        siBtn.getStyleClass().add("btn-resume-yes");
        siBtn.setOnAction(e -> {
            future.complete(true);
            stage.close();
        });

        Button noBtn = new Button("NO");
        noBtn.getStyleClass().add("btn-resume-no");
        noBtn.setOnAction(e -> {
            future.complete(false);
            stage.close();
        });

        buttonBox.getChildren().addAll(siBtn, noBtn);
        card.getChildren().addAll(chipsLabel, titleLabel, messageLabel, buttonBox);
        root.getChildren().add(card);

        Scene scene = new Scene(root, DISPLAY_W, DISPLAY_H, Color.rgb(6, 14, 10));
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                future.complete(true);
                stage.close();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                future.complete(false);
                stage.close();
            }
        });

        var fontsCss = ResumeGameDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var css = ResumeGameDialog.class.getResource(MODAL_CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!future.isDone()) {
                future.complete(false);
            }
        });

        return stage;
    }

    /** Same formatting as the Swing JOptionPane fallback. */
    private static String formatChips(int chips) {
        return String.format(Locale.US, "%,d", Math.max(0, chips));
    }
}
