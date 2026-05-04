package network.session;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionNameRegistryConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        shouldAllowOnlySingleWinnerForSimultaneousSameNameReservation();
        System.out.println("SessionNameRegistryConcurrencyTest: all tests passed");
    }

    private static void shouldAllowOnlySingleWinnerForSimultaneousSameNameReservation() throws InterruptedException {
        SessionNameRegistry registry = new SessionNameRegistry();
        String normalizedName = "alice";

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        AtomicBoolean firstWon = new AtomicBoolean(false);
        AtomicBoolean secondWon = new AtomicBoolean(false);

        Thread first = new Thread(() -> {
            awaitAndReserve(registry, normalizedName, ready, start, firstWon);
            done.countDown();
        }, "registry-claimant-1");

        Thread second = new Thread(() -> {
            awaitAndReserve(registry, normalizedName, ready, start, secondWon);
            done.countDown();
        }, "registry-claimant-2");

        first.start();
        second.start();

        require(ready.await(2, TimeUnit.SECONDS), "threads did not get ready in time");
        start.countDown();
        require(done.await(2, TimeUnit.SECONDS), "threads did not complete in time");

        int winners = (firstWon.get() ? 1 : 0) + (secondWon.get() ? 1 : 0);
        require(winners == 1, "exactly one reservation winner is expected");
    }

    private static void awaitAndReserve(
        SessionNameRegistry registry,
        String normalizedName,
        CountDownLatch ready,
        CountDownLatch start,
        AtomicBoolean won
    ) {
        try {
            ready.countDown();
            require(start.await(2, TimeUnit.SECONDS), "start signal timeout");
            won.set(registry.reserve(normalizedName));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("thread interrupted", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
