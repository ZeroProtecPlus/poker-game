package view.fx;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests and visual demo for BetAmountDialog.
 *
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.BetAmountDialogTest}
 */
public class BetAmountDialogTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        shouldComputeFiveEvenlySpacedTicksForNormalRange();
        shouldComputeFiveEvenlySpacedTicksForHighStakes();
        shouldAdaptTickCountForExtremeRange();
        shouldAlwaysIncludeMinAndMaxLabels();

        System.out.println("BetAmountDialogTest: all tick-computation tests passed");

        // Visual demo (optional — comment out for headless CI)
        ensureJavaFxInitialized();
        System.out.println("\n=== VISUAL DEMO: BetAmountDialog ===");
        System.out.println("Close the dialog to exit.");

        var future = BetAmountDialog.showAndWait(100, 5000);
        Integer result = future.get(60, TimeUnit.SECONDS);
        System.out.println("Chosen amount: " + result);
    }

    // ── RED tests: verify tick label computation before UI implementation ───

    private static void shouldComputeFiveEvenlySpacedTicksForNormalRange() {
        // minBet=10, maxBet=500 → normal range, 5 labels
        List<BetAmountDialog.TickInfo> ticks = BetAmountDialog.computeTicks(10, 500);
        require(ticks.size() == 5,
            "normal range should produce 5 ticks; got=" + ticks.size());
        require(ticks.get(0).value() == 10,
            "first tick should be minBet; got=" + ticks.get(0).value());
        require(ticks.get(4).value() == 500,
            "last tick should be maxBet; got=" + ticks.get(4).value());
        require(ticks.get(2).value() == 255,
            "middle tick should be midpoint (~255); got=" + ticks.get(2).value());
    }

    private static void shouldComputeFiveEvenlySpacedTicksForHighStakes() {
        // minBet=100, maxBet=10000 → high stakes, should still have 5 labels
        List<BetAmountDialog.TickInfo> ticks = BetAmountDialog.computeTicks(100, 10000);
        require(ticks.size() == 5,
            "high-stakes range should produce 5 ticks; got=" + ticks.size());
        require(ticks.get(0).value() == 100,
            "first tick should be minBet; got=" + ticks.get(0).value());
        require(ticks.get(4).value() == 10000,
            "last tick should be maxBet; got=" + ticks.get(4).value());

        // Verify no overlap in fraction space: each tick's fraction should be
        // monotonic and spread across [0.0, 1.0]
        for (int i = 1; i < ticks.size(); i++) {
            require(ticks.get(i).fraction() > ticks.get(i - 1).fraction(),
                "tick fractions must be strictly increasing; tick " + i
                    + " fraction=" + ticks.get(i).fraction()
                    + " prev=" + ticks.get(i - 1).fraction());
        }
        require(ticks.get(0).fraction() == 0.0,
            "first tick fraction must be 0.0; got=" + ticks.get(0).fraction());
        require(ticks.get(ticks.size() - 1).fraction() == 1.0,
            "last tick fraction must be 1.0; got=" + ticks.get(ticks.size() - 1).fraction());
    }

    private static void shouldAdaptTickCountForExtremeRange() {
        // minBet=0, maxBet=50000 → range=50000, should reduce from 5 to 4 ticks.
        // minBet=0, maxBet=100000 → range=100000, should reduce to 3 ticks.
        // Min must be 0, max must be the given max.
        List<BetAmountDialog.TickInfo> ticks = BetAmountDialog.computeTicks(0, 50000);
        require(ticks.size() == 4,
            "50000 range should produce 4 ticks; got=" + ticks.size());
        require(ticks.get(0).value() == 0,
            "first tick must be min=0; got=" + ticks.get(0).value());
        require(ticks.get(ticks.size() - 1).value() == 50000,
            "last tick must be max=50000; got=" + ticks.get(ticks.size() - 1).value());
        require(ticks.get(0).fraction() == 0.0,
            "first tick fraction must be 0.0");
        require(ticks.get(ticks.size() - 1).fraction() == 1.0,
            "last tick fraction must be 1.0");

        // For extreme range 100000, should reduce to 3 ticks
        List<BetAmountDialog.TickInfo> extreme = BetAmountDialog.computeTicks(0, 100000);
        require(extreme.size() == 3,
            "100000 range should produce 3 ticks; got=" + extreme.size());
        require(extreme.get(0).value() == 0,
            "first extreme tick must be min=0");
        require(extreme.get(extreme.size() - 1).value() == 100000,
            "last extreme tick must be max=100000");
    }

    private static void shouldAlwaysIncludeMinAndMaxLabels() {
        // minBet=0, maxBet=50000 → min and max always visible
        List<BetAmountDialog.TickInfo> ticks = BetAmountDialog.computeTicks(0, 50000);
        require(ticks.get(0).value() == 0,
            "min label must be visible for all ranges");
        require(ticks.get(ticks.size() - 1).value() == 50000,
            "max label must be visible for all ranges");

        // Test with very large range too
        List<BetAmountDialog.TickInfo> huge = BetAmountDialog.computeTicks(0, 100000);
        require(huge.get(0).value() == 0,
            "min must always be visible");
        require(huge.get(huge.size() - 1).value() == 100000,
            "max must always be visible");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
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
