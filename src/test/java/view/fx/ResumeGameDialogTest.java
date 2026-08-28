package view.fx;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Automated checks for {@link ResumeGameDialog}.
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.ResumeGameDialogTest}
 */
public class ResumeGameDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) {
        try {
            ensureJavaFxInitialized();
            shouldHaveStaticShowAndWaitBlockingMethod();
            shouldReturnCompletableFutureOfBoolean();
            shouldAcceptIntParameter();
            shouldShowChipCountInLabel(1500);
            shouldShowChipCountInLabel(0);
            shouldCompleteFutureWithTrueOnSiButton();
            shouldCompleteFutureWithFalseOnNoButton();
            shouldCompleteFutureWithTrueOnEnter();
            shouldCompleteFutureWithFalseOnEscape();
            System.out.println("ResumeGameDialogTest: all tests passed");
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        } finally {
            Platform.exit();
        }
    }

    // -------------------------------------------------------------------------
    // showAndWaitBlocking must exist as public static method returning
    // CompletableFuture<Boolean>
    // -------------------------------------------------------------------------
    private static void shouldHaveStaticShowAndWaitBlockingMethod() throws Exception {
        Method method = ResumeGameDialog.class.getDeclaredMethod(
            "showAndWaitBlocking", int.class
        );
        require(Modifier.isStatic(method.getModifiers()),
            "showAndWaitBlocking should be a static method");
        require(Modifier.isPublic(method.getModifiers()),
            "showAndWaitBlocking should be public");
    }

    private static void shouldReturnCompletableFutureOfBoolean() throws Exception {
        Method method = ResumeGameDialog.class.getDeclaredMethod(
            "showAndWaitBlocking", int.class
        );
        require(method.getReturnType().equals(CompletableFuture.class),
            "showAndWaitBlocking should return CompletableFuture, got: "
                + method.getReturnType().getName());
    }

    private static void shouldAcceptIntParameter() throws Exception {
        Method method = ResumeGameDialog.class.getDeclaredMethod(
            "showAndWaitBlocking", int.class
        );
        Class<?>[] params = method.getParameterTypes();
        require(params.length == 1, "showAndWaitBlocking should take exactly 1 parameter");
        require(params[0] == int.class,
            "showAndWaitBlocking parameter should be int, got: " + params[0].getName());
    }

    // -------------------------------------------------------------------------
    // Message label must contain the chip count
    // -------------------------------------------------------------------------
    private static void shouldShowChipCountInLabel(int chips) throws Exception {
        DialogHarness harness = openDialogHarness(chips);
        try {
            String labelText = findMessageLabelText(harness);
            require(labelText != null, "dialog should contain a message label");
            String formattedChips = String.format(Locale.US, "%,d", chips);
            require(labelText.contains(formattedChips),
                "label should contain formatted chip count \""
                    + formattedChips + "\", got: " + labelText);
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // -------------------------------------------------------------------------
    // SÍ button click completes future with true
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithTrueOnSiButton() throws Exception {
        DialogHarness harness = openDialogHarness(5000);
        try {
            Button siBtn = findButtonByText(harness, "SÍ");
            clickButtonAndAwait(siBtn);
            require(harness.future.isDone(), "future should be done after SÍ click");
            require(Boolean.TRUE.equals(harness.future.getNow(null)),
                "SÍ button should complete with true");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // -------------------------------------------------------------------------
    // NO button click completes future with false
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithFalseOnNoButton() throws Exception {
        DialogHarness harness = openDialogHarness(3000);
        try {
            Button noBtn = findButtonByText(harness, "NO");
            clickButtonAndAwait(noBtn);
            require(harness.future.isDone(), "future should be done after NO click");
            require(Boolean.FALSE.equals(harness.future.getNow(null)),
                "NO button should complete with false");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // -------------------------------------------------------------------------
    // Enter key should complete with true
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithTrueOnEnter() throws Exception {
        DialogHarness harness = openDialogHarness(1000);
        try {
            runOnFxThreadAndWait(() -> {
                Scene scene = harness.stage.getScene();
                require(scene.getOnKeyPressed() != null,
                    "scene should handle key presses");
                scene.getOnKeyPressed().handle(keyEvent(KeyCode.ENTER));
            });
            require(harness.future.isDone(), "future should be done after Enter");
            require(Boolean.TRUE.equals(harness.future.getNow(null)),
                "Enter should complete with true");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // -------------------------------------------------------------------------
    // Escape key should complete with false
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithFalseOnEscape() throws Exception {
        DialogHarness harness = openDialogHarness(2000);
        try {
            runOnFxThreadAndWait(() -> {
                Scene scene = harness.stage.getScene();
                require(scene.getOnKeyPressed() != null,
                    "scene should handle key presses");
                scene.getOnKeyPressed().handle(keyEvent(KeyCode.ESCAPE));
            });
            require(harness.future.isDone(), "future should be done after Escape");
            require(Boolean.FALSE.equals(harness.future.getNow(null)),
                "Escape should complete with false");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) {
            return;
        }
        synchronized (ResumeGameDialogTest.class) {
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

    private static DialogHarness openDialogHarness(int chips) {
        AtomicReference<DialogHarness> harnessRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = invokeBuildStage(future, chips);
            require(stage.getScene() != null,
                "buildStage should set a Scene on the Stage");
            harnessRef.set(new DialogHarness(future, stage));
        });

        DialogHarness harness = harnessRef.get();
        require(harness != null, "dialog harness should be created");
        return harness;
    }

    /** Finds the label whose text contains chip-related information. */
    private static String findMessageLabelText(DialogHarness harness) {
        AtomicReference<String> textRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            Pane root = (Pane) harness.stage.getScene().getRoot();
            textRef.set(findLabelContaining(root, "fichas"));
        });

        return textRef.get();
    }

    /** Recursively searches for a Labeled node whose text contains the keyword. */
    private static String findLabelContaining(javafx.scene.Parent parent, String keyword) {
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Labeled labeled) {
                String text = labeled.getText();
                if (text != null && text.toLowerCase().contains(keyword)) {
                    return text;
                }
            }
            if (child instanceof javafx.scene.Parent p) {
                String found = findLabelContaining(p, keyword);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Finds a Button with the given text in the stage's scene graph. */
    private static Button findButtonByText(DialogHarness harness, String text) {
        AtomicReference<Button> btnRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            Pane root = (Pane) harness.stage.getScene().getRoot();
            btnRef.set(findButtonRecursive(root, text));
        });

        Button btn = btnRef.get();
        require(btn != null, "button with text '" + text + "' should exist in dialog");
        return btn;
    }

    private static Button findButtonRecursive(javafx.scene.Parent parent, String text) {
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Button button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof javafx.scene.Parent p) {
                Button found = findButtonRecursive(p, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Fires the button's onAction handler. */
    private static void clickButtonAndAwait(Button button) {
        runOnFxThreadAndWait(() -> {
            var handler = button.getOnAction();
            require(handler != null, "button should have an action handler");
            handler.handle(null);
        });
    }

    private static Stage invokeBuildStage(
        CompletableFuture<Boolean> future, int chips
    ) {
        try {
            Method buildStage = ResumeGameDialog.class.getDeclaredMethod(
                "buildStage", CompletableFuture.class, int.class
            );
            buildStage.setAccessible(true);
            return (Stage) buildStage.invoke(null, future, chips);
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

    private static void waitForLatch(
        CountDownLatch latch, long timeout, TimeUnit unit, String message
    ) {
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
        final CompletableFuture<Boolean> future;
        final Stage stage;

        DialogHarness(CompletableFuture<Boolean> future, Stage stage) {
            this.future = future;
            this.stage = stage;
        }
    }
}
