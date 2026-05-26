package view.fx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

import model.Card;

/**
 * TDD tests for {@link GameCard} — card face rendering, card back rendering,
 * face-up toggling, and dimensions.
 *
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.GameCardTest}
 */
public class GameCardTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();

        shouldDrawCardFace();
        shouldDrawCardBack();
        shouldToggleFaceUp();
        shouldHaveCorrectDimensions();

        System.out.println("GameCardTest: all tests passed");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Card face
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldDrawCardFace() {
        runOnFxThreadAndWait(() -> {
            GameCard gc = new GameCard();
            gc.setCard(new Card("A", "\u2660"));
            gc.showFace();

            require(gc.isFaceUp(), "Card should be face-up after showFace()");

            // faceCanvas should be non-null and visible
            Object faceCanvas = getField(gc, "faceCanvas");
            require(faceCanvas != null, "faceCanvas should not be null after setCard");

            javafx.scene.canvas.Canvas fc = (javafx.scene.canvas.Canvas) faceCanvas;
            require(fc.isVisible(), "faceCanvas should be visible when face-up");
            require(fc.getWidth() == 72, "faceCanvas width should be 72, got: " + fc.getWidth());
            require(fc.getHeight() == 100, "faceCanvas height should be 100, got: " + fc.getHeight());
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Card back
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldDrawCardBack() {
        runOnFxThreadAndWait(() -> {
            GameCard gc = new GameCard();
            gc.setCard(new Card("K", "\u2665"));

            // Default state after construction: back visible
            require(!gc.isFaceUp(), "Card should not be face-up by default (back showing)");

            Object backCanvas = getField(gc, "backCanvas");
            require(backCanvas != null, "backCanvas should not be null");

            javafx.scene.canvas.Canvas bc = (javafx.scene.canvas.Canvas) backCanvas;
            require(bc.isVisible(), "backCanvas should be visible when card is face-down");
            require(bc.getWidth() == 72, "backCanvas width should be 72, got: " + bc.getWidth());
            require(bc.getHeight() == 100, "backCanvas height should be 100, got: " + bc.getHeight());

            Object faceCanvas = getField(gc, "faceCanvas");
            javafx.scene.canvas.Canvas fc = (javafx.scene.canvas.Canvas) faceCanvas;
            require(!fc.isVisible(), "faceCanvas should be hidden when back is showing");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Toggle face up / face down
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldToggleFaceUp() {
        runOnFxThreadAndWait(() -> {
            GameCard gc = new GameCard();
            gc.setCard(new Card("Q", "\u2666"));

            javafx.scene.canvas.Canvas faceCanvas = getField(gc, "faceCanvas");
            javafx.scene.canvas.Canvas backCanvas = getField(gc, "backCanvas");

            // Start back
            require(!gc.isFaceUp(), "Precondition: back visible initially");
            require(backCanvas.isVisible(), "Precondition: backCanvas visible");
            require(!faceCanvas.isVisible(), "Precondition: faceCanvas hidden");

            // Show face
            gc.showFace();
            require(gc.isFaceUp(), "Should be face-up after showFace()");
            require(faceCanvas.isVisible(), "faceCanvas should be visible after showFace()");
            require(!backCanvas.isVisible(), "backCanvas should be hidden after showFace()");

            // Show back again
            gc.showBack();
            require(!gc.isFaceUp(), "Should be face-down after showBack()");
            require(backCanvas.isVisible(), "backCanvas should be visible after showBack()");
            require(!faceCanvas.isVisible(), "faceCanvas should be hidden after showBack()");

            // Toggle again — triangulation
            gc.showFace();
            require(gc.isFaceUp(), "Should be face-up after second showFace()");
            require(faceCanvas.isVisible(), "faceCanvas should be visible after second showFace()");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Dimensions
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldHaveCorrectDimensions() {
        runOnFxThreadAndWait(() -> {
            GameCard gc = new GameCard();
            javafx.scene.Node node = gc.getNode();

            require(node != null, "getNode() should return non-null");

            // Check pref size (the container StackPane)
            Object root = getField(gc, "root");
            javafx.scene.layout.StackPane sp = (javafx.scene.layout.StackPane) root;
            require(sp.getPrefWidth() == 72,
                "Root prefWidth should be 72, got: " + sp.getPrefWidth());
            require(sp.getPrefHeight() == 100,
                "Root prefHeight should be 100, got: " + sp.getPrefHeight());
            require(sp.getMinWidth() == 72,
                "Root minWidth should be 72, got: " + sp.getMinWidth());
            require(sp.getMinHeight() == 100,
                "Root minHeight should be 100, got: " + sp.getMinHeight());
            require(sp.getMaxWidth() == 72,
                "Root maxWidth should be 72, got: " + sp.getMaxWidth());
            require(sp.getMaxHeight() == 100,
                "Root maxHeight should be 100, got: " + sp.getMaxHeight());
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) {
            return;
        }
        synchronized (GameCardTest.class) {
            if (javafxInitialized) {
                return;
            }
            JavaFxBootstrap.ensureStarted();
            javafxInitialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f;
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return (T) f.get(obj);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new AssertionError(
                "Field '" + fieldName + "' not found in " + obj.getClass().getName());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(
                "Cannot access field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }

    private static void runOnFxThreadAndWait(Runnable action) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                done.countDown();
            }
        });

        try {
            require(done.await(15, TimeUnit.SECONDS),
                "FX thread action timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("FX thread action interrupted", ex);
        }

        if (error.get() != null) {
            Throwable failure = error.get();
            if (failure instanceof RuntimeException re) throw re;
            if (failure instanceof Error er) throw er;
            throw new AssertionError("FX thread action failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
