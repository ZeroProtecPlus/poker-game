package view.fx;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class JavaFxBootstrap {

    private static volatile boolean started;

    private JavaFxBootstrap() {}

    public static void ensureStarted() {
        if (started) {
            return;
        }
        synchronized (JavaFxBootstrap.class) {
            if (started) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            Platform.setImplicitExit(false);  // Keep toolkit alive even when all stages close
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX toolkit did not start in time");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("JavaFX startup interrupted", ex);
            }
            started = true;
        }
    }

    public static <T> T runOnFxAndWait(Supplier<T> supplier) {
        ensureStarted();
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        });
        try {
            return future.get(5, TimeUnit.MINUTES);
        } catch (Exception ex) {
            throw new IllegalStateException("JavaFX task failed", ex);
        }
    }

    public static void runOnFxAndWait(Runnable runnable) {
        runOnFxAndWait(() -> {
            runnable.run();
            return null;
        });
    }
}
