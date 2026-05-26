package controller;

import model.BettingRound;
import model.PokerGame;
import view.GameView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AutoFoldContinueRegressionTest {

    public static void main(String[] args) {
        shouldNotPromptHumanAfterPreflopFoldAndShouldSettleHand();
        System.out.println("AutoFoldContinueRegressionTest: all tests passed");
    }

    private static void shouldNotPromptHumanAfterPreflopFoldAndShouldSettleHand() {
        DeterministicGameView testView = new DeterministicGameView();
        GameController controller = new GameController(testView);

        controller.createNewPlayer();
        require(testView.joinRejectionCalls == 0,
            "join should be accepted directly with deterministic valid username");
        require(testView.retryJoinCalls == 0,
            "retry join prompt should never be requested in this regression test");

        invokePlayOneHand(controller);

        require(testView.waitForPlayerActionCalls == 1,
            "human should be prompted exactly once before folding; actual=" + testView.waitForPlayerActionCalls);

        require(testView.firstActionWasFold,
            "first human action should be fold to activate auto-continue flow");

        require(testView.resultShown || testView.potWasZeroShown,
            "hand should resolve via showdown or early-finish settlement without extra prompts");

        PokerGame game = readPokerGame(controller);
        require(game != null, "controller should hold a poker game instance after playing one hand");
        require(game.getPot() == 0, "hand must settle pot to zero at completion");
    }

    private static void invokePlayOneHand(GameController controller) {
        try {
            Method playOneHand = GameController.class.getDeclaredMethod("playOneHand");
            playOneHand.setAccessible(true);
            playOneHand.invoke(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke playOneHand reflectively", ex);
        }
    }

    private static PokerGame readPokerGame(GameController controller) {
        try {
            Field pokerGameField = GameController.class.getDeclaredField("pokerGame");
            pokerGameField.setAccessible(true);
            return (PokerGame) pokerGameField.get(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to read pokerGame field reflectively", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DeterministicGameView extends GameView {
        private int waitForPlayerActionCalls = 0;
        private boolean firstActionWasFold = false;
        private boolean resultShown = false;
        private boolean potWasZeroShown = false;
        private int joinRejectionCalls = 0;
        private int retryJoinCalls = 0;

        private DeterministicGameView() {
            super(false);
        }

        @Override
        public String getUserName() {
            return "AutoFoldUser";
        }

        @Override
        public void showJoinRejectionMessage(String message) {
            joinRejectionCalls++;
        }

        @Override
        public boolean askRetryJoin() {
            retryJoinCalls++;
            return false;
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void showPlayerHand(ArrayList<model.Card> hand) {
        }

        @Override
        public void showCommunityCards(ArrayList<model.Card> community, ArrayList<model.Card> playerHand, int newCardsCount) {
        }

        @Override
        public void showRoles(model.AIPlayer.Role humanRole, List<model.AIPlayer> aiPlayers) {
        }

        @Override
        public void showPot(int pot) {
            if (pot == 0) {
                potWasZeroShown = true;
            }
        }

        @Override
        public void showAIActions(List<String> log) {
        }

        @Override
        public void showPlayerFolded() {
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet) {
            waitForPlayerActionCalls++;
            if (waitForPlayerActionCalls == 1) {
                firstActionWasFold = true;
                return BettingRound.Action.FOLD;
            }
            throw new AssertionError("unexpected post-fold human prompt in phase " + round.getPhase());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> getPlayerBetAmount(int minBet, int maxBet) {
            return java.util.concurrent.CompletableFuture.completedFuture(minBet);
        }

        @Override
        public void showResult(ArrayList<model.Card> community, ArrayList<model.Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName) {
            resultShown = true;
        }

        @Override
        public boolean askPlayAgain(int chips) {
            return false;
        }

        @Override
        public void showGameOver(int chips) {
        }
    }
}
