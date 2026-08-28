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
        shouldFallbackToCheckWhenInputTimesOutAndCanCheck();
        shouldFallbackToFoldWhenInputTimesOutAndCannotCheck();
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

    private static void shouldFallbackToCheckWhenInputTimesOutAndCanCheck() {
        TimeoutActionGameView view = new TimeoutActionGameView(true);
        GameController controller = new GameController(view);

        controller.createNewPlayer();
        invokePlayOneHand(controller);

        require(view.timeoutFallbackAction == BettingRound.Action.CHECK,
            "timeout should fallback to CHECK when round allows check");
    }

    private static void shouldFallbackToFoldWhenInputTimesOutAndCannotCheck() {
        TimeoutActionGameView view = new TimeoutActionGameView(false);
        GameController controller = new GameController(view);

        controller.createNewPlayer();
        invokePlayOneHand(controller);

        require(view.timeoutFallbackAction == BettingRound.Action.FOLD,
            "timeout should fallback to FOLD when check is not allowed");
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
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, int newCardsCount) {
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

    private static final class TimeoutActionGameView extends GameView {
        private final boolean canCheck;
        private BettingRound.Action timeoutFallbackAction;

        private TimeoutActionGameView(boolean canCheck) {
            super(false);
            this.canCheck = canCheck;
        }

        @Override
        public String getUserName() {
            return "TimeoutTester";
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs) {
            return null;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action) {
            timeoutFallbackAction = action;
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand) {
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
        public long getUiSyncTimeoutMs() {
            return 1;
        }

        @Override
        protected JoinDecision requestJoinAdmission(String proposedName) {
            return new JoinDecision(true, "PlayerId", proposedName, null);
        }

        @Override
        protected void applyAcceptedJoinDecision(JoinDecision decision) {
            super.applyAcceptedJoinDecision(decision);
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet) {
            return null;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs) {
            return true;
        }

        @Override
        public boolean awaitUiReady(long timeoutMs) {
            return true;
        }

        @Override
        public void showJoinRejectionMessage(String message) {
        }

        @Override
        public boolean askRetryJoin() {
            return false;
        }

        @Override
        public void clearTableForNewHand() {
        }

        @Override
        public void showUserChips(String userNamePlayer, int numbChips) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, int ignored) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, int ignored) {
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs, boolean ignored) {
            return null;
        }

        @Override
        public boolean askRetryJoin(boolean ignored) {
            return false;
        }

        @Override
        public void showJoinRejectionMessage(String message, boolean ignored) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored) {
        }

        @Override
        public void showPot(int pot, boolean ignored) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored) {
        }

        @Override
        public void showPlayerFolded(boolean ignored) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored) {
            return false;
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2) {
        }

        @Override
        public boolean awaitUiReady(long timeoutMs, boolean ignored) {
            return true;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs, boolean ignored) {
            return true;
        }

        @Override
        public void runOnEdtAndWait(Runnable task, boolean ignored) {
        }

        @Override
        public <T> T callOnEdtAndWait(java.util.concurrent.Callable<T> task, boolean ignored) {
            return null;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action, boolean ignored) {
            timeoutFallbackAction = action;
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs, boolean ignored, boolean ignored2) {
            return null;
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3) {
            return null;
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public boolean awaitUiReady(long timeoutMs, boolean ignored, boolean ignored2) {
            return true;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs, boolean ignored, boolean ignored2) {
            return true;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action, boolean ignored, boolean ignored2) {
            timeoutFallbackAction = action;
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return null;
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return false;
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public boolean askRetryJoin(boolean ignored, boolean ignored2) {
            return false;
        }

        @Override
        public void showJoinRejectionMessage(String message, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored, boolean ignored2) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5, boolean ignored6) {
            return false;
        }

        @Override
        public boolean awaitUiReady(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3) {
            return true;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3) {
            return true;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action, boolean ignored, boolean ignored2, boolean ignored3) {
            timeoutFallbackAction = action;
        }

        @Override
        public void runOnEdtAndWait(Runnable task, boolean ignored, boolean ignored2) {
        }

        @Override
        public <T> T callOnEdtAndWait(java.util.concurrent.Callable<T> task, boolean ignored, boolean ignored2) {
            return null;
        }

        @Override
        public void showJoinRejectionMessage(String message, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public boolean askRetryJoin(boolean ignored, boolean ignored2, boolean ignored3) {
            return false;
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored, boolean ignored2, boolean ignored3) {
            return false;
        }

        @Override
        public void showJoinRejectionMessage(String message, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public boolean askRetryJoin(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return false;
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return false;
        }

        @Override
        public boolean awaitUiReady(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return true;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            return true;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4) {
            timeoutFallbackAction = action;
        }

        @Override
        public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            return null;
        }

        @Override
        public void runOnEdtAndWait(Runnable task, boolean ignored, boolean ignored2, boolean ignored3) {
        }

        @Override
        public <T> T callOnEdtAndWait(java.util.concurrent.Callable<T> task, boolean ignored, boolean ignored2, boolean ignored3) {
            return null;
        }

        @Override
        public void showJoinRejectionMessage(String message, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public boolean askRetryJoin(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            return false;
        }

        @Override
        public void showUserChips(String userName, int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showPot(int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showAIActions(List<String> log, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showPlayerFolded(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               ShowdownResult showdownResult, int pot, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot,
                               boolean humanWon, String aiWinnerName, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void showGameOver(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public void requestGracefulShutdown(boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
        }

        @Override
        public boolean askPlayAgain(int chips, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            return false;
        }

        @Override
        public boolean awaitUiReady(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            return true;
        }

        @Override
        public boolean awaitLastAnimation(long timeoutMs, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            return true;
        }

        @Override
        public void resolvePendingPlayerAction(BettingRound.Action action, boolean ignored, boolean ignored2, boolean ignored3, boolean ignored4, boolean ignored5) {
            timeoutFallbackAction = action;
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
