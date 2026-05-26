package view;

import javafx.application.Platform;
import model.BettingRound;
import model.PokerGame;
import view.fx.JavaFxBootstrap;

import javax.swing.SwingUtilities;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GameViewSyncTest {

    public static void main(String[] args) {
        shouldReturnFalseWhenUiReadyTimesOut();
        shouldReturnTrueWhenUiReadySignaled();
        shouldExecuteRunOnEdtAndWait();
        shouldExecutePlatformRunLaterWithCompletableFuture();
        shouldTimeoutWaitForPlayerActionAndReturnNull();
        shouldReturnTrueWhenNoAnimationPending();
        System.out.println("GameViewSyncTest: all tests passed");
    }

    private static void shouldReturnFalseWhenUiReadyTimesOut() {
        GameView view = new GameView(false);
        boolean ready = view.awaitUiReady(1);
        require(!ready, "awaitUiReady should return false when timeout expires");
    }

    private static void shouldReturnTrueWhenUiReadySignaled() {
        GameView view = new GameView(false);
        view.signalUiReady();
        boolean ready = view.awaitUiReady(1);
        require(ready, "awaitUiReady should return true once signaled");
    }

    private static void shouldExecuteRunOnEdtAndWait() {
        GameView view = new GameView(false);
        boolean[] ranOnEdt = {false};
        view.runOnEdtAndWait(() -> ranOnEdt[0] = SwingUtilities.isEventDispatchThread());
        require(ranOnEdt[0], "runOnEdtAndWait should execute on EDT");
    }

    private static void shouldExecutePlatformRunLaterWithCompletableFuture() {
        JavaFxBootstrap.ensureStarted();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Platform.runLater(() -> future.complete(Platform.isFxApplicationThread()));
        try {
            Boolean ranOnFx = future.get(5, TimeUnit.SECONDS);
            require(ranOnFx, "Platform.runLater should execute on FX application thread");
        } catch (Exception e) {
            throw new AssertionError("Platform.runLater test failed", e);
        }
    }

    private static void shouldTimeoutWaitForPlayerActionAndReturnNull() {
        GameView view = new GameView(false);
        attachTablePanel(view);

        BettingRound round = new BettingRound(
            BettingRound.Phase.PREFLOP,
            PokerGame.BIG_BLIND,
            PokerGame.BIG_BLIND,
            PokerGame.BIG_BLIND
        );

        BettingRound.Action action = view.waitForPlayerAction(round, PokerGame.BIG_BLIND, 5);
        require(action == null, "waitForPlayerAction should return null on timeout");
    }

    private static void shouldReturnTrueWhenNoAnimationPending() {
        GameView view = new GameView(false);
        boolean completed = view.awaitLastAnimation(1);
        require(completed, "awaitLastAnimation should return true when no animation is pending");
    }

    private static void attachTablePanel(GameView view) {
        try {
            Constructor<GameView.TablePanel> constructor = GameView.TablePanel.class.getDeclaredConstructor(GameView.class);
            constructor.setAccessible(true);
            GameView.TablePanel panel = constructor.newInstance(view);

            Field tablePanelField = GameView.class.getDeclaredField("tablePanel");
            tablePanelField.setAccessible(true);
            tablePanelField.set(view, panel);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to create and attach table panel", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
