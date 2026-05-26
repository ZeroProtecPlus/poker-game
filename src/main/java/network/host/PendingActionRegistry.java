package network.host;

import model.BettingRound;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thread-safe registry for pending remote player actions.
 *
 * <p>Stores either a pre-arrived {@link RemoteAction} (when the network thread
 * calls {@link #complete} before the main thread calls {@link #awaitAction})
 * or a {@link CompletableFuture} that the main thread blocks on. This
 * eliminates the race where an action arriving before {@code awaitAction()}
 * was permanently lost.</p>
 *
 * <p>Contract: at most one action per player per betting round iteration.</p>
 */
public class PendingActionRegistry {

    public record RemoteAction(BettingRound.Action action, int amount) {}

    private final Map<String, Object> pending = new ConcurrentHashMap<>();

    /**
     * Blocks until an action arrives for {@code playerId} or the timeout expires.
     *
     * <p>If {@link #complete} was already called for this player, returns the
     * stored action immediately without blocking.</p>
     *
     * @param playerId  the player to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the remote action
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws TimeoutException     if no action arrives within {@code timeoutMs}
     */
    public RemoteAction awaitAction(String playerId, long timeoutMs)
        throws InterruptedException, TimeoutException {
        // Fast path: action already arrived (complete() won the race)
        Object entry = pending.get(playerId);
        if (entry instanceof RemoteAction ra) {
            pending.remove(playerId);
            return ra;
        }

        CompletableFuture<RemoteAction> future = new CompletableFuture<>();
        Object prev = pending.putIfAbsent(playerId, future);
        if (prev instanceof RemoteAction ra) {
            // Race: complete() stored the action between our get() and putIfAbsent()
            pending.remove(playerId);
            return ra;
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("pending action failed", ex.getCause());
        } finally {
            pending.remove(playerId);
        }
    }

    /**
     * Delivers an action from the network thread.
     *
     * <p>If the main thread is already waiting (via {@link #awaitAction}), the
     * action is delivered to the waiting future. If the main thread has not yet
     * called {@code awaitAction}, the action is stored and will be returned
     * immediately when the main thread does call it.</p>
     *
     * @param playerId the player whose action arrived
     * @param action   the action to deliver
     * @return {@code true} if the action was accepted, {@code false} if no
     *         action was pending for this player (e.g. duplicate delivery)
     */
    public boolean complete(String playerId, RemoteAction action) {
        Object prev = pending.putIfAbsent(playerId, action);
        if (prev == null) {
            // Action stored; awaitAction() will pick it up
            return true;
        }
        if (prev instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<RemoteAction> future = (CompletableFuture<RemoteAction>) prev;
            return future.complete(action);
        }
        // prev is already a RemoteAction — duplicate complete() call
        return false;
    }

    /**
     * Returns {@code true} if a pending entry (future or pre-arrived action)
     * exists for the given player.  Used by the action-validation path to
     * tolerate a brief {@code activePlayerId} mismatch caused by thread scheduling.
     *
     * @param playerId the player to check
     * @return {@code true} if there is a pending entry for this player
     */
    public boolean hasPending(String playerId) {
        return pending.containsKey(playerId);
    }

    /**
     * Cancels any pending wait for the given player (e.g. on disconnect).
     *
     * @param playerId the player to cancel
     */
    public void cancel(String playerId) {
        Object entry = pending.remove(playerId);
        if (entry instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<RemoteAction> future = (CompletableFuture<RemoteAction>) entry;
            future.cancel(true);
        }
    }

    /**
     * Cancels all pending waits. Used during shutdown.
     */
    public void cancelAll() {
        for (Object entry : pending.values()) {
            if (entry instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<RemoteAction> future = (CompletableFuture<RemoteAction>) entry;
                future.cancel(true);
            }
        }
        pending.clear();
    }
}
