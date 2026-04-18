package controller;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.PokerGame;
import view.GameView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class GameControllerTest {

    public static void main(String[] args) {
        shouldSequenceTableClearBeforeRoundStartAndInitialRender();
        System.out.println("GameControllerTest: all tests passed");
    }

    private static void shouldSequenceTableClearBeforeRoundStartAndInitialRender() {
        SequencingGameView view = new SequencingGameView();
        GameController controller = new GameController(view);
        view.attachController(controller);

        controller.createNewPlayer();
        invokePlayOneHand(controller);
        invokePlayOneHand(controller);

        require(view.clearCalls == 2, "table should be cleared exactly once per hand start");
        require(view.showPlayerHandCalls == 2, "player hand should render exactly once per hand start");
        require(view.orderViolations == 0, "hand-start render should never happen before clear");
        require(view.gameReplacementViolations == 0, "clear should run before creating next hand model");
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

    private static final class SequencingGameView extends GameView {
        private GameController controller;
        private final ArrayList<PokerGame> gameSeenAtClear = new ArrayList<>();

        private int clearCalls = 0;
        private int showPlayerHandCalls = 0;
        private int orderViolations = 0;
        private int gameReplacementViolations = 0;

        private SequencingGameView() {
            super(false);
        }

        private void attachController(GameController controller) {
            this.controller = controller;
        }

        @Override
        public String getUserName() {
            return "ControllerSequence";
        }

        @Override
        public void clearTableForNewHand() {
            clearCalls++;
            gameSeenAtClear.add(readPokerGame(controller));
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand) {
            if (clearCalls != showPlayerHandCalls + 1) {
                orderViolations++;
            }

            PokerGame gameAtRender = readPokerGame(controller);
            PokerGame gameAtClear = gameSeenAtClear.get(showPlayerHandCalls);
            if (gameAtRender == null || gameAtRender == gameAtClear) {
                gameReplacementViolations++;
            }

            require(hand.size() == 2, "new hand should render two hole cards");
            require(gameAtRender.getPot() == PokerGame.SMALL_BLIND + PokerGame.BIG_BLIND,
                "startNewRound should post blinds before first render");

            showPlayerHandCalls++;
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
        public int getPlayerBetAmount(int minBet, int maxBet) {
            return minBet;
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName) {
        }

        @Override
        public boolean askPlayAgain(int chips) {
            return false;
        }

        @Override
        public void showGameOver(int chips) {
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand) {
        }
    }
}
