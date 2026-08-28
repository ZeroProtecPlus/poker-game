package view.fx;

import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import model.Card;

/**
 * GameCard — JavaFX canvas-based card renderer and deal animation.
 *
 * Ported from Swing {@code CardSprite} in GameView.
     * Renders card face (rank, suit, center suit, rotated bottom-right)
     * and card back (cream bg, blue inner, gold border) on two 94×130 Canvases.
 *
 * Deal animation: shrinks card from deck position to midpoint (flip),
 * then grows to destination showing the face.
 */
public final class GameCard {

    public static final double W = 94;   // 72 × 1.3
    public static final double H = 130;  // 100 × 1.3

    static final Color CREAM = Color.web("#F5EDD8");
    static final Color GOLD = Color.web("#C9A84C");
    static final Color BLUE_BACK = Color.web("#1A3A6B");
    static final Color RED_SUIT = Color.web("#C0392B");
    static final Color DARK_SUIT = Color.web("#111111");

    private static final Font FONT_CARD_R = GameFonts.CARD_RANK;
    private static final Font FONT_CARD_S = GameFonts.CARD_SUIT_SMALL;
    private static final Font FONT_CARD_CENTER = GameFonts.CARD_SUIT_CENTER;

    private static final Interpolator EASE_OUT_CUBIC = new Interpolator() {
        @Override
        protected double curve(double t) {
            return 1 - Math.pow(1 - t, 3);
        }
    };

    // Deck position — must match GameTable deckPileCanvas anchors + half card size offset
    private static final double DECK_X = 1308; // 1280 + 75 (deck centre) - 47 (card half-width)
    private static final double DECK_Y = 70;  // 70 + 65 (card half-height from top of deck pile)

    private final StackPane root;
    private final Canvas faceCanvas;
    private final Canvas backCanvas;
    private boolean faceUp;
    private Card card;
    private SequentialTransition currentAnim;

    public GameCard() {
        root = new StackPane();
        root.setPrefSize(W, H);
        root.setMinSize(W, H);
        root.setMaxSize(W, H);

        faceCanvas = new Canvas(W, H);
        backCanvas = new Canvas(W, H);

        // Back drawn in constructor (always ready)
        drawBack();
        // Face drawn when setCard is called

        root.getChildren().addAll(backCanvas, faceCanvas);

        // Cache card as bitmap to avoid re-rendering (performance)
        root.setCache(true);
        root.setCacheHint(javafx.scene.CacheHint.SPEED);

        // Default: show back
        showBack();
    }

    /**
     * Sets the card rank/suit and redraws the face canvas.
     */
    public void setCard(Card card) {
        this.card = card;
        if (card != null) {
            drawFace();
        }
    }

    /** Shows the card back (hides face). */
    public void showBack() {
        faceUp = false;
        backCanvas.setVisible(true);
        faceCanvas.setVisible(false);
    }

    /** Shows the card face (hides back). */
    public void showFace() {
        faceUp = true;
        backCanvas.setVisible(false);
        faceCanvas.setVisible(true);
    }

    /** Returns whether the card is currently face-up. */
    public boolean isFaceUp() {
        return faceUp;
    }

    /** Returns the root StackPane node for adding to the scene graph. */
    public Node getNode() {
        return root;
    }

    /**
     * Animates the card from the deck pile to a destination position.
     *
     * Timeline: wait {@code delayMs} → (200ms: fly halfway + shrink to 0) →
     * flip to face → (200ms: continue to dest + grow to 1).
     *
     * @param destX   target x position
     * @param destY   target y position
     * @param delayMs stagger delay before animation starts (ms)
     * @param onDone  callback when animation completes (can be null)
     */
    public void dealTo(double destX, double destY, long delayMs, Runnable onDone) {
        if (currentAnim != null) {
            currentAnim.stop();
        }

        // Initial state: at deck position, back visible, full size
        root.setTranslateX(DECK_X);
        root.setTranslateY(DECK_Y);
        root.setScaleX(1.0);
        showBack();

        double midX = (DECK_X + destX) / 2;
        double midY = (DECK_Y + destY) / 2;

        // ── Delay ─────────────────────────────────────────────────────────────
        PauseTransition delay = new PauseTransition(Duration.millis(delayMs));

        // ── First half (200ms): fly to midpoint + shrink scaleX 1→0 ───────────
        TranslateTransition move1 = new TranslateTransition(Duration.millis(200), root);
        move1.setFromX(DECK_X);
        move1.setToX(midX);
        move1.setFromY(DECK_Y);
        move1.setToY(midY);
        move1.setInterpolator(EASE_OUT_CUBIC);

        Timeline shrinkX = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(root.scaleXProperty(), 1.0)),
            new KeyFrame(Duration.millis(200),
                new KeyValue(root.scaleXProperty(), 0.0))
        );

        ParallelTransition firstHalf = new ParallelTransition(move1, shrinkX);
        firstHalf.setOnFinished(e -> {
            root.setScaleX(0.0); // ensure exactly 0
            showFace();
        });

        // ── Second half (200ms): fly to dest + grow scaleX 0→1 ────────────────
        TranslateTransition move2 = new TranslateTransition(Duration.millis(200), root);
        move2.setFromX(midX);
        move2.setToX(destX);
        move2.setFromY(midY);
        move2.setToY(destY);
        move2.setInterpolator(EASE_OUT_CUBIC);

        Timeline growX = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(root.scaleXProperty(), 0.0)),
            new KeyFrame(Duration.millis(200),
                new KeyValue(root.scaleXProperty(), 1.0))
        );

        ParallelTransition secondHalf = new ParallelTransition(move2, growX);
        if (onDone != null) {
            secondHalf.setOnFinished(e -> onDone.run());
        }

        currentAnim = new SequentialTransition(delay, firstHalf, secondHalf);
        currentAnim.play();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Card face rendering (Canvas)
    // ═════════════════════════════════════════════════════════════════════════

    private void drawFace() {
        if (card == null) return;

        GraphicsContext gc = faceCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);

        String rank = card.getRank();
        String suit = card.getSuit();
        boolean red = "\u2665".equals(suit) || "\u2666".equals(suit);
        Color textColor = red ? RED_SUIT : DARK_SUIT;

        // Shadow (offset 4,5)
        gc.setFill(Color.rgb(0, 0, 0, 0.24));
        gc.fillRoundRect(4, 5, W, H, 13, 13);

        // White face
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(0, 0, W, H, 13, 13);

        // Gold border 1.5px
        gc.setStroke(GOLD);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(0, 0, W, H, 13, 13);

        // Rank top-left
        gc.setFill(textColor);
        gc.setFont(FONT_CARD_R);
        gc.fillText(rank, 7, 29);

        // Suit top-left (small)
        gc.setFont(FONT_CARD_S);
        gc.fillText(suit, 8, 50);

        // Center suit (large, centered)
        gc.setFont(FONT_CARD_CENTER);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(suit, W / 2, H / 2 + 18);
        gc.setTextAlign(TextAlignment.LEFT);

        // Bottom-right rank+suit (rotated 180° around center)
        gc.save();
        gc.translate(W / 2, H / 2);
        gc.rotate(180);
        gc.translate(-W / 2, -H / 2);
        gc.setFill(textColor);
        gc.setFont(FONT_CARD_R);
        gc.fillText(rank, 7, 29);
        gc.setFont(FONT_CARD_S);
        gc.fillText(suit, 8, 50);
        gc.restore();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Card back rendering (Canvas)
    // ═════════════════════════════════════════════════════════════════════════

    private void drawBack() {
        GraphicsContext gc = backCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, W, H);
        drawBack(gc, 0, 0);
    }

    /**
     * Draws a single card back at the given (x, y) offset on the graphics context.
     * Used by both the card's own back canvas and the deck pile canvas.
     */
    public static void drawBack(GraphicsContext gc, double x, double y) {
        // Shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.24));
        gc.fillRoundRect(x + 4, y + 5, W, H, 13, 13);

        // Cream bg
        gc.setFill(CREAM);
        gc.fillRoundRect(x, y, W, H, 13, 13);

        // Blue inner rect (5px inset)
        gc.setFill(BLUE_BACK);
        gc.fillRoundRect(x + 5, y + 5, W - 10, H - 10, 9, 9);

        // Crosshatch pattern (diagonal lines, white 30% opacity, 8px spacing)
        gc.setStroke(Color.rgb(255, 255, 255, 0.12));
        gc.setLineWidth(0.7);
        for (int i = 0; i < W; i += 8) {
            gc.strokeLine(x + 5 + i, y + 5, x + 5, y + 5 + i);
        }

        // Gold border 1.2px
        gc.setStroke(GOLD);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y, W, H, 13, 13);
    }
}
