package view.fx;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import view.LanDialogs.ConnectParams;

/**
 * Behavioral tests for {@link ConnectDialog} contract.
 *
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.ConnectDialogTest}
 *
 * These tests verify submit/cancel/validation logic without depending
 * on CSS class names — they check semantic outcomes (label visibility, text content).
 */
public class ConnectDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();
        shouldHaveStaticShowAndWaitBlockingMethod();
        shouldReturnConnectParamsFromShowAndWaitBlocking();
        shouldCompleteFutureWithNullOnEscape();
        shouldCompleteFutureWithParamsOnValidSubmit();
        shouldCompleteFutureWithParamsOnEnterKey();
        shouldNotCompleteOnInvalidPort();
        shouldNotCompleteOnInvalidName();
        shouldCompleteWithNullOnVolverButton();
        System.out.println("ConnectDialogTest: all tests passed");
    }

    // ── Reflection API checks ─────────────────────────────────────────────

    private static void shouldHaveStaticShowAndWaitBlockingMethod() throws Exception {
        Method method = ConnectDialog.class.getDeclaredMethod("showAndWaitBlocking");
        require(Modifier.isStatic(method.getModifiers()),
            "showAndWaitBlocking should be a static method");
        require(Modifier.isPublic(method.getModifiers()),
            "showAndWaitBlocking should be public");
    }

    private static void shouldReturnConnectParamsFromShowAndWaitBlocking() throws Exception {
        Method method = ConnectDialog.class.getDeclaredMethod("showAndWaitBlocking");
        require(method.getReturnType().equals(ConnectParams.class),
            "showAndWaitBlocking should return ConnectParams, got: "
                + method.getReturnType().getName());
    }

    // ── Cancel (ESC) ──────────────────────────────────────────────────────

    private static void shouldCompleteFutureWithNullOnEscape() throws Exception {
        ConnectHarness harness = openDialogHarness("127.0.0.1", "9876", "TestPlayer");
        try {
            runOnFxThreadAndWait(() -> {
                Scene scene = harness.stage.getScene();
                require(scene != null, "Stage should have a Scene");
                // Fire ESC on scene — should cancel and complete future with null
                javafx.event.EventHandler<? super javafx.scene.input.KeyEvent> handler =
                    scene.getOnKeyPressed();
                if (handler != null) {
                    handler.handle(keyEvent(KeyCode.ESCAPE));
                }
            });
            Thread.sleep(200); // let FX thread process
            require(harness.future.isDone(), "ESC should complete the future");
            require(harness.future.getNow(null) == null,
                "ESC should complete with null ConnectParams");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ── Valid submit via button click ─────────────────────────────────────

    private static void shouldCompleteFutureWithParamsOnValidSubmit() throws Exception {
        ConnectHarness harness = openDialogHarness("192.168.1.5", "12345", "Alice");
        try {
            runOnFxThreadAndWait(() -> {
                TextField ipField = harness.ipField;
                TextField portField = harness.portField;
                TextField nameField = harness.nameField;
                require(ipField != null, "IP field should be present");
                require(portField != null, "Port field should be present");
                require(nameField != null, "Name field should be present");

                ipField.setText("192.168.1.5");
                portField.setText("12345");
                nameField.setText("Alice");

                // Find and click CONECTAR button
                Button connectBtn = findButtonByText(harness.stage, "CONECTAR");
                require(connectBtn != null, "CONECTAR button should be present");
                var handler = connectBtn.getOnAction();
                require(handler != null, "CONECTAR button should have an action handler");
                handler.handle(null);
            });
            Thread.sleep(200);
            require(harness.future.isDone(), "Valid submit should complete the future");
            ConnectParams result = harness.future.getNow(null);
            require(result != null, "Result should not be null for valid submit");
            require("192.168.1.5".equals(result.host()),
                "Host should be 192.168.1.5, got: " + result.host());
            require(result.port() == 12345,
                "Port should be 12345, got: " + result.port());
            require("Alice".equals(result.playerName()),
                "Name should be Alice, got: " + result.playerName());
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ── Valid submit via Enter on name field ───────────────────────────────

    private static void shouldCompleteFutureWithParamsOnEnterKey() throws Exception {
        ConnectHarness harness = openDialogHarness("127.0.0.1", "9876", "Jugador");
        try {
            runOnFxThreadAndWait(() -> {
                harness.ipField.setText("127.0.0.1");
                harness.portField.setText("9876");
                harness.nameField.setText("Jugador");

                // Simulate Enter on name field (onAction)
                var handler = harness.nameField.getOnAction();
                require(handler != null, "Name field should have an onAction handler");
                handler.handle(null);
            });
            Thread.sleep(200);
            require(harness.future.isDone(), "Enter on name field should complete the future");
            ConnectParams result = harness.future.getNow(null);
            require(result != null, "Result should not be null for Enter submit");
            require("Jugador".equals(result.playerName()),
                "Name should be Jugador, got: " + result.playerName());
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ── Invalid port: shows error, does NOT complete ──────────────────────

    private static void shouldNotCompleteOnInvalidPort() throws Exception {
        ConnectHarness harness = openDialogHarness("127.0.0.1", "99999", "Player");
        try {
            runOnFxThreadAndWait(() -> {
                harness.ipField.setText("127.0.0.1");
                harness.portField.setText("99999"); // out of range
                harness.nameField.setText("Player");

                Button connectBtn = findButtonByText(harness.stage, "CONECTAR");
                require(connectBtn != null, "CONECTAR button should be present");
                connectBtn.getOnAction().handle(null);
            });
            Thread.sleep(200);
            require(!harness.future.isDone(),
                "Future should NOT be completed when port is invalid");

            // Verify error label is visible and shows relevant text
            runOnFxThreadAndWait(() -> {
                Label errorLabel = findErrorLabel(harness.stage);
                require(errorLabel != null, "Error label should be present");
                require(errorLabel.isVisible(),
                    "Error label should be visible for invalid port");
                String text = errorLabel.getText();
                require(text != null && !text.isBlank(),
                    "Error label should show an error message");
                require(text.toLowerCase().contains("puerto")
                        || text.toLowerCase().contains("port"),
                    "Error message should mention port, got: " + text);
            });
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ── Invalid name: shows error, does NOT complete ──────────────────────

    private static void shouldNotCompleteOnInvalidName() throws Exception {
        ConnectHarness harness = openDialogHarness("127.0.0.1", "9876", "123Bad");
        try {
            runOnFxThreadAndWait(() -> {
                harness.ipField.setText("127.0.0.1");
                harness.portField.setText("9876");
                harness.nameField.setText("123Bad"); // has numbers

                Button connectBtn = findButtonByText(harness.stage, "CONECTAR");
                require(connectBtn != null, "CONECTAR button should be present");
                connectBtn.getOnAction().handle(null);
            });
            Thread.sleep(200);
            require(!harness.future.isDone(),
                "Future should NOT be completed when name is invalid");

            // Verify error label is visible for name validation
            runOnFxThreadAndWait(() -> {
                Label errorLabel = findErrorLabel(harness.stage);
                require(errorLabel != null, "Error label should be present");
                require(errorLabel.isVisible(),
                    "Error label should be visible for invalid name");
                String text = errorLabel.getText();
                require(text != null && !text.isBlank(),
                    "Error label should show an error message");
                require(text.toLowerCase().contains("letra")
                        || text.toLowerCase().contains("nombre")
                        || text.toLowerCase().contains("name")
                        || text.toLowerCase().contains("letter"),
                    "Error message should mention name/letters, got: " + text);
            });
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ── VOLVER button cancels ─────────────────────────────────────────────

    private static void shouldCompleteWithNullOnVolverButton() throws Exception {
        ConnectHarness harness = openDialogHarness("127.0.0.1", "9876", "Player");
        try {
            runOnFxThreadAndWait(() -> {
                Button volverBtn = findButtonByText(harness.stage, "VOLVER");
                require(volverBtn != null, "VOLVER button should be present");
                var handler = volverBtn.getOnAction();
                require(handler != null, "VOLVER button should have an action handler");
                handler.handle(null);
            });
            Thread.sleep(200);
            require(harness.future.isDone(), "VOLVER should complete the future");
            require(harness.future.getNow(null) == null,
                "VOLVER should complete with null ConnectParams");
        } finally {
            closeStageIfShowing(harness.stage);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) return;
        synchronized (ConnectDialogTest.class) {
            if (javafxInitialized) return;
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException ignored) {
                // already running
            }
            javafxInitialized = true;
        }
    }

    private static ConnectHarness openDialogHarness(String defaultIp, String defaultPort, String defaultName) {
        AtomicReference<ConnectHarness> harnessRef = new AtomicReference<>();
        runOnFxThreadAndWait(() -> {
            CompletableFuture<ConnectParams> future = new CompletableFuture<>();
            Stage stage = invokeBuildStage(future);
            require(stage.getScene() != null, "buildStage should set a Scene");

            AnchorPane canvas = (AnchorPane) stage.getScene().getRoot();

            // Find the content AnchorPane (inside StackPane inside canvas)
            // Canvas children: [layers(StackPane), chromeBar]
            // Inside layers: [actualCanvas, gameOverOverlay]
            // Inside actualCanvas: [bg, ...content...]
            // The content StackPane is a child of actualCanvas
            TextField ipField = null;
            TextField portField = null;
            TextField nameField = null;

            // Walk tree to find TextFields
            ipField = findTextFieldInTree(canvas, defaultIp);
            portField = findTextFieldByPromptPrefix(canvas, "Puerto");
            nameField = findTextFieldByPromptPrefix(canvas, "Nombre");

            require(ipField != null, "IP TextField should be present");
            require(portField != null, "Port TextField should be present");
            require(nameField != null, "Name TextField should be present");

            harnessRef.set(new ConnectHarness(future, stage, ipField, portField, nameField));
        });

        ConnectHarness harness = harnessRef.get();
        require(harness != null, "Dialog harness should be created");
        return harness;
    }

    private static Stage invokeBuildStage(CompletableFuture<ConnectParams> future) {
        try {
            Method buildStage = ConnectDialog.class.getDeclaredMethod(
                "buildStage", CompletableFuture.class
            );
            buildStage.setAccessible(true);
            return (Stage) buildStage.invoke(null, future);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke buildStage", ex);
        }
    }

    private static TextField findTextFieldInTree(javafx.scene.Node root, String containsText) {
        if (root instanceof TextField tf) {
            String prompt = tf.getPromptText();
            String text = tf.getText();
            if ((prompt != null && prompt.toLowerCase().contains(containsText.toLowerCase()))
                || (text != null && text.toLowerCase().contains(containsText.toLowerCase()))
                || (prompt != null && prompt.toLowerCase().contains("ip"))) {
                return tf;
            }
            // Check all TextFields — return first one with IP-related prompt
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                TextField found = findTextFieldInTree(child, containsText);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextField findTextFieldByPromptPrefix(javafx.scene.Node root, String prefix) {
        if (root instanceof TextField tf) {
            String prompt = tf.getPromptText();
            if (prompt != null && prompt.toLowerCase().contains(prefix.toLowerCase())) {
                return tf;
            }
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                TextField found = findTextFieldByPromptPrefix(child, prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Button findButtonByText(Stage stage, String text) {
        return findButtonByTextInTree(stage.getScene().getRoot(), text);
    }

    private static Button findButtonByTextInTree(javafx.scene.Node root, String text) {
        if (root instanceof Button btn) {
            if (text.equals(btn.getText())) {
                return btn;
            }
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButtonByTextInTree(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Label findErrorLabel(Stage stage) {
        return findLabelWithClass(stage.getScene().getRoot(), "lan-error-label");
    }

    /**
     * Finds a visible Label that has the specified CSS style class.
     * CSS classes are set during construction and are available
     * regardless of whether CSS has been resolved on the Scene.
     */
    private static Label findLabelWithClass(javafx.scene.Node root, String styleClass) {
        if (root instanceof Label lbl) {
            if (lbl.isVisible()
                && lbl.getStyleClass().contains(styleClass)
                && lbl.getText() != null
                && !lbl.getText().isBlank()) {
                return lbl;
            }
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Label found = findLabelWithClass(child, styleClass);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void closeStageIfShowing(Stage stage) {
        runOnFxThreadAndWait(() -> {
            if (stage != null && stage.isShowing()) {
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
        if (failure == null) return;
        if (failure instanceof RuntimeException re) throw re;
        if (failure instanceof Error e) throw e;
        throw new AssertionError("FX thread action failed", failure);
    }

    private static javafx.scene.input.KeyEvent keyEvent(KeyCode code) {
        return new javafx.scene.input.KeyEvent(
            javafx.scene.input.KeyEvent.KEY_PRESSED,
            "", "", code, false, false, false, false
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ── Harness record ────────────────────────────────────────────────────

    private static final class ConnectHarness {
        final CompletableFuture<ConnectParams> future;
        final Stage stage;
        final TextField ipField;
        final TextField portField;
        final TextField nameField;

        ConnectHarness(CompletableFuture<ConnectParams> future, Stage stage,
                       TextField ipField, TextField portField, TextField nameField) {
            this.future = future;
            this.stage = stage;
            this.ipField = ipField;
            this.portField = portField;
            this.nameField = nameField;
        }
    }
}
