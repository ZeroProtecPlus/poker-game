package view.fx;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Quick visual demo for BetAmountDialog.
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.BetAmountDialogTest}
 */
public class BetAmountDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();

        System.out.println("\n=== VISUAL DEMO: BetAmountDialog ===");
        System.out.println("Close the dialog to exit.");

        var future = BetAmountDialog.showAndWait(100, 5000);

        Integer result = future.get(60, TimeUnit.SECONDS);
        System.out.println("Chosen amount: " + result);
    }

    private static void ensureJavaFxInitialized() throws Exception {
        if (!javafxInitialized) {
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                Platform.startup(latch::countDown);
            }).start();
            latch.await(10, TimeUnit.SECONDS);
            javafxInitialized = true;
        }
    }
}
