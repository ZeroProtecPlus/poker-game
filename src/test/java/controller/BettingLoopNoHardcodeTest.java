package controller;

import model.BettingRound;
import model.Player;
import model.PokerGame;
import model.User;
import view.GameView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BettingLoopNoHardcodeTest {

    public static void main(String[] args) {
        shouldAllowLongRaiseChainBeyondLegacyCap();
        shouldPreserveCheckCallFoldFlowCompatibility();
        shouldStopOnRepeatedStateNonProgressGuard();
        System.out.println("BettingLoopNoHardcodeTest: all tests passed");
    }

    private static void shouldAllowLongRaiseChainBeyondLegacyCap() {
        ScriptedView view = new ScriptedView(List.of(
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK
        ));
        GameController controller = new GameController(view);
        User user = new User("loop-user", "LoopUser");
        LongRaiseChainPokerGame game = new LongRaiseChainPokerGame(user);

        setControllerState(controller, user, game);
        invokeRunBettingPhase(controller, BettingRound.Phase.FLOP);

        require(game.runCalls == 9,
            "long raise/re-raise chain should continue beyond old cap; calls=" + game.runCalls);
        require(view.waitForActionCalls == 9,
            "human should be prompted for each open decision in chain; prompts=" + view.waitForActionCalls);
    }

    private static void shouldPreserveCheckCallFoldFlowCompatibility() {
        shouldHandleCheckPath();
        shouldHandleCallPath();
        shouldHandleFoldPath();
    }

    private static void shouldHandleCheckPath() {
        ScriptedView view = new ScriptedView(List.of(BettingRound.Action.CHECK));
        GameController controller = new GameController(view);
        User user = new User("check-user", "CheckUser");
        CheckPathPokerGame game = new CheckPathPokerGame(user);

        setControllerState(controller, user, game);
        invokeRunBettingPhase(controller, BettingRound.Phase.TURN);

        require(game.runCalls == 1, "check path should settle in one loop when already matched");
        require(view.waitForActionCalls == 1, "check path should ask player exactly once");
    }

    private static void shouldHandleCallPath() {
        ScriptedView view = new ScriptedView(List.of(BettingRound.Action.CHECK, BettingRound.Action.CALL));
        GameController controller = new GameController(view);
        User user = new User("call-user", "CallUser");
        CallPathPokerGame game = new CallPathPokerGame(user);

        setControllerState(controller, user, game);
        invokeRunBettingPhase(controller, BettingRound.Phase.TURN);

        require(game.runCalls == 2, "call path should keep phase open until caller matches high bet");
        require(game.observedCallOnSecondTurn,
            "controller should send CALL with computed amount when high bet is outstanding");
    }

    private static void shouldHandleFoldPath() {
        ScriptedView view = new ScriptedView(List.of(BettingRound.Action.FOLD));
        GameController controller = new GameController(view);
        User user = new User("fold-user", "FoldUser");
        FoldPathPokerGame game = new FoldPathPokerGame(user);

        setControllerState(controller, user, game);
        invokeRunBettingPhase(controller, BettingRound.Phase.RIVER);

        require(game.runCalls == 1, "fold path should stop quickly after terminal fold state");
        require(view.playerFoldedShown == 1, "fold indicator should still be emitted once");
    }

    private static void shouldStopOnRepeatedStateNonProgressGuard() {
        ScriptedView view = new ScriptedView(List.of(
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK,
            BettingRound.Action.CHECK
        ));
        GameController controller = new GameController(view);
        User user = new User("guard-user", "GuardUser");
        NonProgressPokerGame game = new NonProgressPokerGame(user);

        setControllerState(controller, user, game);
        invokeRunBettingPhase(controller, BettingRound.Phase.PREFLOP);

        require(game.runCalls == 8,
            "non-progress guard should terminate repeated fingerprint state deterministically; calls=" + game.runCalls);
        require(view.waitForActionCalls == 8,
            "non-progress guard should exit without infinite loop; prompts=" + view.waitForActionCalls);
    }

    private static void setControllerState(GameController controller, User user, PokerGame game) {
        setPrivateField(controller, "newPlayer", user);
        setPrivateField(controller, "userNamePlayer", user.getName());
        setPrivateField(controller, "pokerGame", game);
    }

    private static void invokeRunBettingPhase(GameController controller, BettingRound.Phase phase) {
        try {
            Method run = GameController.class.getDeclaredMethod("runBettingPhase", BettingRound.Phase.class);
            run.setAccessible(true);
            run.invoke(controller, phase);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke runBettingPhase reflectively", ex);
        }
    }

    private static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to set field reflectively: " + fieldName, ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void setPlayerBet(Player player, int targetBet) {
        int delta = targetBet - player.getCurrentBet();
        if (delta > 0) {
            player.placeBet(delta);
        }
    }

    private static final class ScriptedView extends GameView {
        private final ArrayList<BettingRound.Action> actions;
        private int actionIndex = 0;
        private int waitForActionCalls = 0;
        private int playerFoldedShown = 0;

        private ScriptedView(List<BettingRound.Action> actions) {
            super(false);
            this.actions = new ArrayList<>(actions);
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet) {
            waitForActionCalls++;
            if (actionIndex >= actions.size()) {
                return BettingRound.Action.CHECK;
            }
            return actions.get(actionIndex++);
        }

        @Override
        public int getPlayerBetAmount(int minBet, int maxBet) {
            return minBet;
        }

        @Override
        public void showAIActions(List<String> log) {
        }

        @Override
        public void showPot(int pot) {
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void showPlayerFolded() {
            playerFoldedShown++;
        }
    }

    private static final class LongRaiseChainPokerGame extends PokerGame {
        private int runCalls = 0;

        private LongRaiseChainPokerGame(User user) {
            super(user);
        }

        @Override
        public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
            runCalls++;

            int highBet = Math.min(runCalls, 8) * 10;
            List<Player> players = getPlayers();
            for (int i = 0; i < players.size(); i++) {
                int target = highBet;
                if (runCalls < 9 && i == 1) {
                    target = Math.max(0, highBet - 10);
                }
                setPlayerBet(players.get(i), target);
            }

            if (runCalls >= 9) {
                for (Player player : players) {
                    setPlayerBet(player, highBet);
                }
            }

            return new AIBettingResult(List.of("raise-chain-step-" + runCalls), highBet, false);
        }
    }

    private static final class CheckPathPokerGame extends PokerGame {
        private int runCalls = 0;

        private CheckPathPokerGame(User user) {
            super(user);
        }

        @Override
        public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
            runCalls++;
            for (Player player : getPlayers()) {
                setPlayerBet(player, 0);
            }
            return new AIBettingResult(List.of("check-path"), 0, false);
        }
    }

    private static final class CallPathPokerGame extends PokerGame {
        private int runCalls = 0;
        private boolean observedCallOnSecondTurn = false;

        private CallPathPokerGame(User user) {
            super(user);
        }

        @Override
        public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
            runCalls++;
            List<Player> players = getPlayers();

            if (runCalls == 1) {
                setPlayerBet(players.get(1), 40);
                setPlayerBet(players.get(2), 40);
                setPlayerBet(players.get(3), 40);
                return new AIBettingResult(List.of("open-to-40"), 40, false);
            }

            if (humanAction == BettingRound.Action.CALL && humanAmount == 40) {
                observedCallOnSecondTurn = true;
            }

            for (Player player : players) {
                setPlayerBet(player, 40);
            }
            return new AIBettingResult(List.of("call-close"), 40, false);
        }
    }

    private static final class FoldPathPokerGame extends PokerGame {
        private int runCalls = 0;

        private FoldPathPokerGame(User user) {
            super(user);
        }

        @Override
        public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
            runCalls++;
            List<Player> players = getPlayers();
            players.get(0).setFolded(true);
            players.get(2).setFolded(true);
            players.get(3).setFolded(true);
            return new AIBettingResult(List.of("human-folded"), currentHighBet, true);
        }
    }

    private static final class NonProgressPokerGame extends PokerGame {
        private int runCalls = 0;

        private NonProgressPokerGame(User user) {
            super(user);
        }

        @Override
        public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
            runCalls++;
            List<Player> players = getPlayers();
            setPlayerBet(players.get(0), 10);
            setPlayerBet(players.get(1), 20);
            setPlayerBet(players.get(2), 20);
            setPlayerBet(players.get(3), 20);
            return new AIBettingResult(List.of("stalled-state"), 20, false);
        }
    }
}
