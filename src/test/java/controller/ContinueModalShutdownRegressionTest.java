package controller;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.PokerGame;
import model.ShowdownResult;
import view.GameView;

import java.util.ArrayList;
import java.util.List;

public class ContinueModalShutdownRegressionTest {

    public static void main(String[] args) {
        shouldTriggerShutdownWhenContinueAnswerIsNo();
        shouldKeepYesPathFlowUnchangedBeforeLaterNo();
        shouldKeepShutdownRequestIdempotent();
        System.out.println("ContinueModalShutdownRegressionTest: all tests passed");
    }

    private static void shouldTriggerShutdownWhenContinueAnswerIsNo() {
        ContinueDecisionGameView view = new ContinueDecisionGameView(List.of(false));
        GameController controller = new GameController(view);

        controller.createNewPlayer();
        controller.createNewGame();

        require(view.askPlayAgainCalls == 1, "NO scenario should ask continue exactly once");
        require(view.gracefulShutdownCompletions == 1, "NO scenario should trigger graceful shutdown path");
        require(view.showGameOverCalls == 0, "NO scenario should not render game-over after shutdown request");
    }

    private static void shouldKeepYesPathFlowUnchangedBeforeLaterNo() {
        ContinueDecisionGameView view = new ContinueDecisionGameView(List.of(true, false));
        GameController controller = new GameController(view);

        controller.createNewPlayer();
        controller.createNewGame();

        require(view.askPlayAgainCalls == 2,
            "YES then NO scenario should ask continue twice to run next-hand flow");
        require(view.playerHandsShown >= 2,
            "YES answer should continue normal flow and render at least one additional hand");
        require(view.gracefulShutdownCompletions == 1,
            "shutdown should occur only after the final NO answer");
    }

    private static void shouldKeepShutdownRequestIdempotent() {
        ContinueDecisionGameView view = new ContinueDecisionGameView(List.of());

        view.requestGracefulShutdown();
        view.requestGracefulShutdown();

        require(view.gracefulShutdownCompletions == 1,
            "graceful shutdown should be idempotent on duplicate triggers");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ContinueDecisionGameView extends GameView {
        private final List<Boolean> continueAnswers;
        private int continueAnswerIndex = 0;

        private int askPlayAgainCalls = 0;
        private int gracefulShutdownCompletions = 0;
        private int showGameOverCalls = 0;
        private int playerHandsShown = 0;

        private ContinueDecisionGameView(List<Boolean> continueAnswers) {
            super(false);
            this.continueAnswers = continueAnswers;
        }

        @Override
        public String getUserName() {
            return "ContinueFlowUser";
        }

        @Override
        public void showJoinRejectionMessage(String message) {
            throw new AssertionError("join rejection should not happen in continue-modal regression tests");
        }

        @Override
        public boolean askRetryJoin() {
            throw new AssertionError("retry join should not happen in continue-modal regression tests");
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand) {
            playerHandsShown++;
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, int newCardsCount) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers) {
        }

        @Override
        public void showPot(int pot) {
        }

        @Override
        public void showAIActions(List<String> log) {
        }

        @Override
        public void showPlayerFolded() {
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet) {
            return BettingRound.Action.FOLD;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> getPlayerBetAmount(int minBet, int maxBet) {
            return java.util.concurrent.CompletableFuture.completedFuture(minBet);
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName) {
        }

        @Override
        public boolean askPlayAgain(int chips) {
            askPlayAgainCalls++;
            if (continueAnswerIndex < continueAnswers.size()) {
                return continueAnswers.get(continueAnswerIndex++);
            }
            return false;
        }

        @Override
        public void showGameOver(int chips) {
            showGameOverCalls++;
        }

        @Override
        protected void onGracefulShutdownComplete() {
            gracefulShutdownCompletions++;
        }
    }
}
