package view.fx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Standalone test launcher for ContinueDialog.
 * Opens ONLY the modal dialog — no poker game, no Swing, no SQLite needed.
 *
 * Usage: Run as JavaFX Application.
 * Click "SÍ" or "NO" zone on the image, or press Enter/Escape.
 * The result prints to stdout.
 */
public class ContinueDialogTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Hide the primary stage immediately
        primaryStage.hide();

        // Launch the dialog with a mock chip count
        System.out.println("Opening ContinueDialog...");
        CompletableFuture<Boolean> future = ContinueDialog.showAndWait(5000, primaryStage);

        future.thenAccept(result -> {
            System.out.println("Result: " + (result ? "SÍ (continue)" : "NO (exit)"));
            Platform.exit();
        });

        // Safety: force exit after 65s if dialog doesn't respond
        new Thread(() -> {
            try {
                Thread.sleep(65_000);
                System.out.println("Timeout — forcing exit.");
                Platform.exit();
            } catch (InterruptedException ignored) {}
        }).start();
    }

    /** Quick-launch helper: blocks until the dialog result is available. */
    public static boolean showAndBlock(int chips, Stage owner) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Boolean> future = ContinueDialog.showAndWait(chips, owner);
        return future.get(70, TimeUnit.SECONDS);
    }

    /**
     * Main entry point.
     * Run with: mvn -Pcontinue-dialog javafx:run
     * (or Run this class from the IDE on src/main/java, not the homonymous test in src/test/java)
     */
    public static void main(String[] args) {
        Application.launch(args);
    }
}
