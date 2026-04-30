package controller;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.Player;
import model.PokerGame;
import model.ShowdownResult;
import model.User;
import view.GameView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class GameControllerTest {

    public static void main(String[] args) {
        shouldSequenceTableClearBeforeRoundStartAndInitialRender();
        shouldSendShowdownResultToViewForTieOutcome();
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

    private static void invokeEndRound(GameController controller) {
        try {
            Method endRound = GameController.class.getDeclaredMethod("endRound");
            endRound.setAccessible(true);
            endRound.invoke(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke endRound reflectively", ex);
        }
    }

    private static void shouldSendShowdownResultToViewForTieOutcome() {
        TieCaptureGameView view = new TieCaptureGameView();
        GameController controller = new GameController(view);

        User human = new User("Tester");
        human.setNumbChips(500);
        AIPlayer aiA = new AIPlayer("Carlos", 1000);
        AIPlayer aiB = new AIPlayer("Sofia", 1000);
        ShowdownResult tieResult = new ShowdownResult(List.of(human, aiA, aiB), PokerGame.HandRank.ONE_PAIR, true);

        StubPokerGame stubGame = new StubPokerGame(human, tieResult, 101,
            new ArrayList<>(List.of(new Card("2", "H"), new Card("9", "C"), new Card("K", "D"))),
            new ArrayList<>(List.of(new Card("A", "S"), new Card("A", "D"))));

        setPrivateField(controller, "newPlayer", human);
        setPrivateField(controller, "userNamePlayer", "Tester");
        setPrivateField(controller, "pokerGame", stubGame);

        invokeEndRound(controller);

        require(stubGame.awardCalled, "controller should call awardPot(showdownResult)");
        require(stubGame.awardArgument == tieResult, "controller should pass exact showdown result to payout");
        require(view.showResultNewSignatureCalls == 1, "controller should call showdown-aware view signature");
        require(view.showResultLegacyCalls == 0, "controller should not use legacy result signature in active flow");
        require(view.capturedResult == tieResult, "view should receive exact showdown result payload");
        require(view.capturedPot == 101, "view should receive pre-award pot amount");
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
            return "ControllerSeq";
        }

        @Override
        public void showJoinRejectionMessage(String message) {
            throw new AssertionError("join rejection should not be shown in sequencing test");
        }

        @Override
        public boolean askRetryJoin() {
            throw new AssertionError("join retry should not be requested in sequencing test");
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
                               ShowdownResult showdownResult, int pot) {
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

    private static final class TieCaptureGameView extends GameView {
        private int showResultNewSignatureCalls = 0;
        private int showResultLegacyCalls = 0;
        private ShowdownResult capturedResult;
        private int capturedPot;

        private TieCaptureGameView() {
            super(false);
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot) {
            showResultNewSignatureCalls++;
            capturedResult = showdownResult;
            capturedPot = pot;
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName) {
            showResultLegacyCalls++;
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }
    }

    private static final class StubPokerGame extends PokerGame {
        private final ShowdownResult showdownResult;
        private final int potAmount;
        private final ArrayList<Card> communityCards;
        private final ArrayList<Card> playerHand;

        private boolean awardCalled;
        private ShowdownResult awardArgument;

        private StubPokerGame(User player, ShowdownResult showdownResult, int potAmount,
                              ArrayList<Card> communityCards, ArrayList<Card> playerHand) {
            super(player);
            this.showdownResult = showdownResult;
            this.potAmount = potAmount;
            this.communityCards = communityCards;
            this.playerHand = playerHand;
        }

        @Override
        public ShowdownResult determineShowdownResult() {
            return showdownResult;
        }

        @Override
        public void awardPot(ShowdownResult showdownResult) {
            awardCalled = true;
            awardArgument = showdownResult;
        }

        @Override
        public int getPot() {
            return potAmount;
        }

        @Override
        public ArrayList<Card> getCommunityCards() {
            return new ArrayList<>(communityCards);
        }

        @Override
        public ArrayList<Card> getPlayerHand() {
            return new ArrayList<>(playerHand);
        }
    }
}
