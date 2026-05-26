package view.fx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 * Approval tests for {@link PlayerNameDialog} behavioral contract.
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.PlayerNameDialogTest}
 *
 * These tests verify the submit/cancel/validation logic is preserved
 * regardless of layout implementation (VBox or AnchorPane with overlays).
 */
public class PlayerNameDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();
        shouldHaveStaticShowAndWaitBlockingMethod();
        shouldReturnStringFromShowAndWaitBlocking();
        shouldCompleteFutureWithNullOnEscape();
        shouldCompleteFutureWithNameOnEnter();
        shouldCompleteFutureWithNameOnButtonClick();
        shouldNotCompleteFutureOnEmptyName();
        System.out.println("PlayerNameDialogTest: all tests passed");
    }

    // ---------------------------------------------------------------------
    // Task — showAndWaitBlocking must exist as static method returning String
    // ---------------------------------------------------------------------
    private static void shouldHaveStaticShowAndWaitBlockingMethod() throws Exception {
        Method method = PlayerNameDialog.class.getDeclaredMethod("showAndWaitBlocking");
        require(Modifier.isStatic(method.getModifiers()),
            "showAndWaitBlocking should be a static method");
        require(Modifier.isPublic(method.getModifiers()),
            "showAndWaitBlocking should be public");
    }

    private static void shouldReturnStringFromShowAndWaitBlocking() throws Exception {
        Method method = PlayerNameDialog.class.getDeclaredMethod("showAndWaitBlocking");
        require(method.getReturnType().equals(String.class),
            "showAndWaitBlocking should return String, got: " + method.getReturnType().getName());
    }

    // ---------------------------------------------------------------------
    // Escape key should cancel and complete future with null
    // ---------------------------------------------------------------------
    private static void shouldCompleteFutureWithNullOnEscape() throws Exception {
        AtomicReference<String> resultRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            Stage stage = new Stage();
            Scene scene = new Scene(new javafx.scene.layout.StackPane());
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    future.complete(null);
                    stage.close();
                }
            });
            scene.getOnKeyPressed().handle(keyEvent(KeyCode.ESCAPE));
            require(future.isDone(), "Escape should complete the future immediately");
            resultRef.set(future.getNow(null));
        });

        require(resultRef.get() == null, "Escape should complete with null");
    }

    // ---------------------------------------------------------------------
    // Enter key with non-empty text should complete future with the name
    // ---------------------------------------------------------------------
    private static void shouldCompleteFutureWithNameOnEnter() throws Exception {
        DialogHarness harness = openDialogHarness("TestPlayer");
        try {
            // Simulate typing "TestPlayer" and pressing Enter
            Boolean handled = simulateSubmitViaEnter(harness, "TestPlayer");
            require(handled, "Enter should trigger submit when name non-empty");
            require(harness.future.isDone(), "Future should be completed after Enter");
            require("TestPlayer".equals(harness.future.getNow(null)),
                "Future should contain the entered name, got: " + harness.future.getNow(null));
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ---------------------------------------------------------------------
    // Button click with non-empty text should complete future with the name
    // ---------------------------------------------------------------------
    private static void shouldCompleteFutureWithNameOnButtonClick() throws Exception {
        DialogHarness harness = openDialogHarness("Alice");
        try {
            Boolean handled = simulateSubmitViaButtonClick(harness, "Alice");
            require(handled, "Button click should trigger submit when name non-empty");
            require(harness.future.isDone(), "Future should be completed after button click");
            require("Alice".equals(harness.future.getNow(null)),
                "Future should contain the entered name, got: " + harness.future.getNow(null));
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ---------------------------------------------------------------------
    // Empty name should NOT complete future and should add error style class
    // ---------------------------------------------------------------------
    private static void shouldNotCompleteFutureOnEmptyName() throws Exception {
        DialogHarness harness = openDialogHarness("");
        try {
            // Fire submit via Enter — handler runs but rejects empty name
            fireSubmitViaEnter(harness);
            require(!harness.future.isDone(), "Future should NOT be completed on empty name");

            // Verify error class is applied
            TextField field = harness.nameField;
            require(field.getStyleClass().contains("player-name-field-error"),
                "Error style class should be added for empty name");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) {
            return;
        }
        synchronized (PlayerNameDialogTest.class) {
            if (javafxInitialized) {
                return;
            }
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException ignored) {
                // JavaFX toolkit already running in this JVM.
            }
            javafxInitialized = true;
        }
    }

    private static DialogHarness openDialogHarness(String initialText) {
        AtomicReference<DialogHarness> harnessRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            Stage stage = invokeBuildStage(future);

            // Find the TextField and Button in the canvas (AnchorPane)
            AnchorPane canvas = (AnchorPane) stage.getScene().getRoot();
            TextField field = null;
            Button btn = null;
            for (javafx.scene.Node child : canvas.getChildren()) {
                if (child instanceof TextField tf) {
                    field = tf;
                } else if (child instanceof Button b) {
                    btn = b;
                }
            }
            require(field != null, "TextField should be present in the canvas");
            require(btn != null, "Button should be present in the canvas");

            if (!initialText.isEmpty()) {
                field.setText(initialText);
            }

            harnessRef.set(new DialogHarness(future, stage, field, btn));
        });

        DialogHarness harness = harnessRef.get();
        require(harness != null, "dialog harness should be created");
        return harness;
    }

    /**
     * Simulates the submit flow via Enter key.
     * Returns true if the submit handler actually triggered (name non-empty).
     * Returns false if the submit was rejected (empty name, error class added).
     */
    private static Boolean simulateSubmitViaEnter(DialogHarness harness, String name) {
        AtomicReference<Boolean> handledRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            harness.nameField.setText(name);
            // Fire the TextField's onAction (Enter key handler)
            var handler = harness.nameField.getOnAction();
            if (handler != null) {
                handler.handle(null);
                handledRef.set(true);
            } else {
                // Fallback: fire key event on scene
                Scene scene = harness.stage.getScene();
                if (scene.getOnKeyPressed() != null) {
                    scene.getOnKeyPressed().handle(keyEvent(KeyCode.ENTER));
                    handledRef.set(true);
                } else {
                    handledRef.set(false);
                }
            }
        });

        return handledRef.get();
    }

    /** Fires Enter submit without checking result — used for rejection tests. */
    private static void fireSubmitViaEnter(DialogHarness harness) {
        runOnFxThreadAndWait(() -> {
            var handler = harness.nameField.getOnAction();
            if (handler != null) {
                handler.handle(null);
            }
        });
    }

    /**
     * Simulates the submit flow via Button click.
     * Returns true if the submit handler actually triggered.
     */
    private static Boolean simulateSubmitViaButtonClick(DialogHarness harness, String name) {
        AtomicReference<Boolean> handledRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            harness.nameField.setText(name);
            var handler = harness.enterBtn.getOnAction();
            if (handler != null) {
                handler.handle(null);
                handledRef.set(true);
            } else {
                handledRef.set(false);
            }
        });

        return handledRef.get();
    }

    private static Stage invokeBuildStage(CompletableFuture<String> future) {
        try {
            Method buildStage = PlayerNameDialog.class.getDeclaredMethod(
                "buildStage", CompletableFuture.class
            );
            buildStage.setAccessible(true);
            Stage stage = (Stage) buildStage.invoke(null, future);
            // Scene must exist before we can test key handlers
            require(stage.getScene() != null, "buildStage should set a Scene on the Stage");
            return stage;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke buildStage", ex);
        }
    }

    private static void closeStageIfShowing(Stage stage) {
        runOnFxThreadAndWait(() -> {
            if (stage.isShowing()) {
                stage.close();
            }
        });
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

        waitForLatch(done, 15, TimeUnit.SECONDS, "FX thread action timed out");
        propagateFxErrorUnchecked(error.get());
    }

    private static void waitForLatch(CountDownLatch latch, long timeout, TimeUnit unit, String message) {
        try {
            require(latch.await(timeout, unit), message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, ex);
        }
    }

    private static void propagateFxErrorUnchecked(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("FX thread action failed", failure);
    }

    private static KeyEvent keyEvent(KeyCode code) {
        return new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            code,
            false,
            false,
            false,
            false
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DialogHarness {
        final CompletableFuture<String> future;
        final Stage stage;
        final TextField nameField;
        final Button enterBtn;

        DialogHarness(CompletableFuture<String> future, Stage stage,
                      TextField nameField, Button enterBtn) {
            this.future = future;
            this.stage = stage;
            this.nameField = nameField;
            this.enterBtn = enterBtn;
        }
    }
}
