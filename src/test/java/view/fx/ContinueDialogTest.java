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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Automated checks for {@link ContinueDialog} (Task 2.1).
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.ContinueDialogTest}
 *
 * Manual visual preview: {@code view.fx.ContinueDialogTest} in {@code src/main/java} (JavaFX Application).
 */
public class ContinueDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();
        shouldHaveStaticShowAndWaitMethod();
        shouldReturnCompletableFutureOfBoolean();
        shouldExpose60SecondTimeoutConstant();
        shouldLoadContinueImageFromClasspath();
        shouldCompleteFutureWithTrueOnYesZoneClick();
        shouldCompleteFutureWithFalseOnNoZoneClick();
        shouldCompleteFutureWithFalseOnEscape();
        shouldCompleteFutureWithTrueOnEnter();
        System.out.println("ContinueDialogTest: all tests passed");
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — ContinueDialog.showAndWait must exist as static method
    // -------------------------------------------------------------------------
    private static void shouldHaveStaticShowAndWaitMethod() throws Exception {
        Method method = ContinueDialog.class.getDeclaredMethod("showAndWait", int.class, Stage.class);
        require(Modifier.isStatic(method.getModifiers()),
            "showAndWait should be a static method");
        require(CompletableFuture.class.isAssignableFrom(method.getReturnType()),
            "showAndWait should return CompletableFuture<Boolean>");
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — Return type must be CompletableFuture<Boolean>
    // -------------------------------------------------------------------------
    private static void shouldReturnCompletableFutureOfBoolean() throws Exception {
        Method method = ContinueDialog.class.getDeclaredMethod("showAndWait", int.class, Stage.class);
        Class<?> returnType = method.getReturnType();
        require(returnType.equals(CompletableFuture.class),
            "return type should be CompletableFuture, got: " + returnType.getName());
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — Timeout should default to false after 60s
    // -------------------------------------------------------------------------
    private static void shouldExpose60SecondTimeoutConstant() throws Exception {
        Field timeoutField = ContinueDialog.class.getDeclaredField("TIMEOUT_SECONDS");
        timeoutField.setAccessible(true);
        require(timeoutField.getType() == long.class || timeoutField.getType() == Long.TYPE,
            "TIMEOUT_SECONDS should be a long");
        require(timeoutField.getLong(null) == 60L,
            "TIMEOUT_SECONDS should be 60 seconds");
    }

    private static void shouldLoadContinueImageFromClasspath() {
        require(
            ContinueDialog.class.getResource("/sprites/continuar_partida.png") != null,
            "continuar_partida.png should be available on the classpath"
        );
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — Enter key should complete with true
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // Task 2.1 — Escape key should complete with false
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithFalseOnEscape() throws Exception {
        // Mirrors the Enter/Escape mapping used in ContinueDialog.createStage (lines 113-120).
        AtomicReference<Boolean> resultRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = new Stage();
            Scene scene = new Scene(new javafx.scene.layout.StackPane());
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    future.complete(true);
                    stage.close();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    future.complete(false);
                    stage.close();
                }
            });
            scene.getOnKeyPressed().handle(keyEvent(KeyCode.ESCAPE));
            require(future.isDone(), "Escape should complete the future immediately");
            resultRef.set(future.getNow(null));
        });

        require(Boolean.FALSE.equals(resultRef.get()), "Escape should complete with false");
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — Enter key should complete with true (run last: only one full dialog open)
    // -------------------------------------------------------------------------
    private static void shouldCompleteFutureWithTrueOnEnter() throws Exception {
        DialogHarness harness = openDialogHarness();
        try {
            Boolean result = pressKeyAndAwaitResult(harness, KeyCode.ENTER);
            require(Boolean.TRUE.equals(result), "Enter should complete with true");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    private static void shouldCompleteFutureWithTrueOnYesZoneClick() throws Exception {
        Boolean result = clickZoneHarnessAndAwaitResult(0);
        require(Boolean.TRUE.equals(result), "YES zone click should complete with true");
    }

    private static void shouldCompleteFutureWithFalseOnNoZoneClick() throws Exception {
        Boolean result = clickZoneHarnessAndAwaitResult(1);
        require(Boolean.FALSE.equals(result), "NO zone click should complete with false");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) {
            return;
        }
        synchronized (ContinueDialogTest.class) {
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

    private static DialogHarness openDialogHarness() {
        AtomicReference<DialogHarness> harnessRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = invokeCreateStage(future);
            stage.show();
            harnessRef.set(new DialogHarness(future, stage));
        });

        DialogHarness harness = harnessRef.get();
        require(harness != null, "dialog harness should be created");
        return harness;
    }

    private static Boolean pressKeyAndAwaitResult(DialogHarness harness, KeyCode keyCode)
            throws Exception {
        runOnFxThreadAndWait(() -> {
            Scene scene = harness.stage.getScene();
            require(scene.getOnKeyPressed() != null, "scene should handle key presses");
            scene.getOnKeyPressed().handle(keyEvent(keyCode));
            require(harness.future.isDone(), keyCode + " should complete the future immediately");
        });
        return harness.future.get(5, TimeUnit.SECONDS);
    }

    private static Boolean clickZoneHarnessAndAwaitResult(int zoneIndex) throws Exception {
        AtomicReference<Boolean> resultRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Stage stage = new Stage();
            AnchorPane clickZones = invokeCreateClickZones(320.0, stage, future);
            require(clickZones.getChildren().size() >= 2, "click zones should contain YES and NO regions");

            Region zone = (Region) clickZones.getChildren().get(zoneIndex);
            require(zone.getOnMouseClicked() != null, "click zone should handle mouse clicks");
            zone.getOnMouseClicked().handle(mouseClickEvent());
            require(future.isDone(), "click zone should complete the future immediately");
            resultRef.set(future.getNow(null));
        });

        return resultRef.get();
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

    private static Stage invokeCreateStage(CompletableFuture<Boolean> future) {
        try {
            Method createStage = ContinueDialog.class.getDeclaredMethod(
                "createStage", CompletableFuture.class, int.class, Stage.class
            );
            createStage.setAccessible(true);
            return (Stage) createStage.invoke(null, future, 5000, new Stage());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke createStage", ex);
        }
    }

    private static AnchorPane invokeCreateClickZones(
            double displayHeight,
            Stage stage,
            CompletableFuture<Boolean> future
    ) {
        try {
            Method createClickZones = ContinueDialog.class.getDeclaredMethod(
                "createClickZones", double.class, Stage.class, CompletableFuture.class
            );
            createClickZones.setAccessible(true);
            return (AnchorPane) createClickZones.invoke(null, displayHeight, stage, future);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke createClickZones", ex);
        }
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

    private static MouseEvent mouseClickEvent() {
        return new MouseEvent(
            MouseEvent.MOUSE_CLICKED,
            0,
            0,
            0,
            0,
            MouseButton.PRIMARY,
            1,
            false,
            false,
            false,
            false,
            true,
            false,
            false,
            true,
            false,
            false,
            null
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DialogHarness {
        private final CompletableFuture<Boolean> future;
        private final Stage stage;

        private DialogHarness(CompletableFuture<Boolean> future, Stage stage) {
            this.future = future;
            this.stage = stage;
        }
    }
}
