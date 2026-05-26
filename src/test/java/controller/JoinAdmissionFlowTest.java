package controller;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.PokerGame;
import model.ShowdownResult;
import network.contracts.JoinDecision;
import network.contracts.RejectReason;
import network.host.HostJoinHandler;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;
import view.GameView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class JoinAdmissionFlowTest {

    public static void main(String[] args) {
        shouldMapRejectionReasonToViewMessageAndRetry();
        shouldAbortWhenUserDeclinesRetry();
        shouldPropagatePlayerIdIntoUserAndKeepSinglePlayerFlowFunctional();
        System.out.println("JoinAdmissionFlowTest: all tests passed");
    }

    private static void shouldMapRejectionReasonToViewMessageAndRetry() {
        JoinFlowView view = new JoinFlowView(List.of("Alice", "AliceRenamed"), List.of(true));
        SequenceJoinHandler joinHandler = new SequenceJoinHandler(List.of(
            JoinDecision.rejected(RejectReason.NAME_TAKEN, "duplicate"),
            JoinDecision.accepted("player-42", "AliceRenamed")
        ));

        GameController controller = new GameController(view, joinHandler);
        controller.createNewPlayer();

        require(view.joinRejectionMessages.size() == 1, "should show exactly one rejection message");
        require(view.joinRejectionMessages.get(0).contains("ya esta en uso"), "NAME_TAKEN should map to duplicate message");
        require("player-42".equals(controller.getPlayerId()), "player identity should come from join decision");
        require(!controller.getPlayerId().equals("AliceRenamed"), "player id must be independent from display name");
    }

    private static void shouldAbortWhenUserDeclinesRetry() {
        JoinFlowView view = new JoinFlowView(List.of("Bad1"), List.of(false));
        SequenceJoinHandler joinHandler = new SequenceJoinHandler(List.of(
            JoinDecision.rejected(RejectReason.INVALID_FORMAT, "format")
        ));

        GameController controller = new GameController(view, joinHandler);

        boolean thrown = false;
        try {
            controller.createNewPlayer();
        } catch (IllegalStateException ex) {
            thrown = true;
        }

        require(thrown, "controller should abort if user declines retry");
        require(view.joinRejectionMessages.size() == 1, "should show rejection before aborting");
        require(view.joinRejectionMessages.get(0).contains("Nombre invalido"), "INVALID_FORMAT should map to format message");
    }

    private static void shouldPropagatePlayerIdIntoUserAndKeepSinglePlayerFlowFunctional() {
        JoinFlowView view = new JoinFlowView(List.of("LocalFlowUser"), List.of());
        SequenceJoinHandler joinHandler = new SequenceJoinHandler(List.of(
            JoinDecision.accepted("stable-player-id", "LocalFlowUser")
        ));

        GameController controller = new GameController(view, joinHandler);
        controller.createNewPlayer();

        model.User createdUser = readCreatedUser(controller);
        require(createdUser != null, "controller should create user on accepted join");
        require("stable-player-id".equals(createdUser.getPlayerId()), "new user should keep admitted player id");
        require("LocalFlowUser".equals(createdUser.getName()), "display name should remain unchanged");

        invokePlayOneHand(controller);
        require(view.waitForActionCalls >= 1, "single-player hand should still execute human action flow");
    }

    private static model.User readCreatedUser(GameController controller) {
        try {
            Field newPlayerField = GameController.class.getDeclaredField("newPlayer");
            newPlayerField.setAccessible(true);
            return (model.User) newPlayerField.get(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to read controller user", ex);
        }
    }

    private static void invokePlayOneHand(GameController controller) {
        try {
            Method playOneHand = GameController.class.getDeclaredMethod("playOneHand");
            playOneHand.setAccessible(true);
            playOneHand.invoke(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke playOneHand", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class SequenceJoinHandler extends HostJoinHandler {
        private final List<JoinDecision> decisions;
        private int index = 0;

        private SequenceJoinHandler(List<JoinDecision> decisions) {
            super(new NameValidator(), new SessionNameRegistry());
            this.decisions = decisions;
        }

        @Override
        public JoinDecision handleJoin(network.contracts.JoinRequest request) {
            if (index >= decisions.size()) {
                throw new AssertionError("no more join decisions configured");
            }
            return decisions.get(index++);
        }
    }

    private static final class JoinFlowView extends GameView {
        private final List<String> requestedNames;
        private final List<Boolean> retryAnswers;
        private int nameIndex = 0;
        private int retryIndex = 0;
        private final List<String> joinRejectionMessages = new ArrayList<>();
        private int waitForActionCalls = 0;

        private JoinFlowView(List<String> requestedNames, List<Boolean> retryAnswers) {
            super(false);
            this.requestedNames = requestedNames;
            this.retryAnswers = retryAnswers;
        }

        @Override
        public String getUserName() {
            return requestedNames.get(nameIndex++);
        }

        @Override
        public void showJoinRejectionMessage(String message) {
            joinRejectionMessages.add(message);
        }

        @Override
        public boolean askRetryJoin() {
            return retryAnswers.get(retryIndex++);
        }

        @Override
        public void showUserChips(String userName, int chips) {
        }

        @Override
        public void clearTableForNewHand() {
        }

        @Override
        public void showPlayerHand(ArrayList<Card> hand) {
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
            waitForActionCalls++;
            return BettingRound.Action.FOLD;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> getPlayerBetAmount(int minBet, int maxBet) {
            return java.util.concurrent.CompletableFuture.completedFuture(minBet);
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand, ShowdownResult showdownResult, int pot) {
        }

        @Override
        public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                               PokerGame.HandRank bestHand, int pot, boolean humanWon, String aiWinnerName) {
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
