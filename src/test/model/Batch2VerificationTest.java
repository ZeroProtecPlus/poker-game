package model;

import controller.AutoFoldContinueRegressionTest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Batch2VerificationTest {
    public static void main(String[] args) {
        shouldUseUnifiedSeatCollectionForRolesAndBlinds();
        shouldUseMixedSeatIteratorForActiveBettingProgression();
        shouldExposeExplicitWinnerPlayerForHumanAndAIOutcomes();
        shouldSupportControllerStyleWinnerConsumption();
        shouldAwardPotByPlayerReference();
        shouldPreservePokerGameRankParityAcrossCategories();
        shouldPreserveAIDecisionThresholdBehaviorForNoCallFlow();
        shouldPreserveAIDecisionThresholdBehaviorForCallFlow();
        shouldPreserveFoldedStateWhenHumanAlreadyFoldedAtPhaseEntry();
        shouldPreserveAutoFoldContinueWithoutPostFoldPrompts();
        shouldRejectInvalidHandEvaluatorInputShapes();
        shouldResolveNonTieWinnerUsingFullHandStrength();
        shouldSplitPotEvenlyAcrossExactTieWinners();
        shouldAssignRemainderDeterministicallyForThreeWayTie();
        System.out.println("Batch2VerificationTest: all tests passed");
    }

    private static void shouldUseUnifiedSeatCollectionForRolesAndBlinds() {
        PokerGame game = new PokerGame(new User("Verifier"));
        game.startNewRound();

        List<Player> players = game.getPlayers();
        require(players.size() == 4, "expected human + three AI seats");

        int dealerCount = 0;
        int smallBlindCount = 0;
        int bigBlindCount = 0;
        int totalBets = 0;

        for (Player player : players) {
            if (player.getPlayerRole() == PlayerRole.DEALER) {
                dealerCount++;
            } else if (player.getPlayerRole() == PlayerRole.SMALL_BLIND) {
                smallBlindCount++;
                require(player.getCurrentBet() == PokerGame.SMALL_BLIND, "small blind amount mismatch");
            } else if (player.getPlayerRole() == PlayerRole.BIG_BLIND) {
                bigBlindCount++;
                require(player.getCurrentBet() == PokerGame.BIG_BLIND, "big blind amount mismatch");
            }
            totalBets += player.getCurrentBet();
        }

        require(dealerCount == 1, "expected exactly one dealer");
        require(smallBlindCount == 1, "expected exactly one small blind");
        require(bigBlindCount == 1, "expected exactly one big blind");
        require(totalBets == game.getPot(), "pot should match sum of player bets");

        BettingRound preflop = game.createBettingRound(BettingRound.Phase.PREFLOP);
        require(preflop.getCurrentBet() == PokerGame.BIG_BLIND, "preflop high bet should match big blind");
    }

    private static void shouldExposeExplicitWinnerPlayerForHumanAndAIOutcomes() {
        PokerGame humanWins = new PokerGame(new User("HumanWinner"));
        humanWins.startNewRound();

        for (AIPlayer ai : humanWins.getAIPlayers()) {
            ai.setFolded(true);
        }

        Player winnerPlayer = humanWins.determineWinnerPlayer();
        require(winnerPlayer instanceof User, "winner player should reference human user");

        PokerGame aiWins = new PokerGame(new User("HumanFolded"));
        aiWins.startNewRound();

        Player firstAI = aiWins.getAIPlayers().get(0);
        aiWins.getPlayers().get(0).setFolded(true);
        aiWins.getAIPlayers().get(1).setFolded(true);
        aiWins.getAIPlayers().get(2).setFolded(true);

        Player aiWinnerPlayer = aiWins.determineWinnerPlayer();
        require(aiWinnerPlayer == firstAI, "winner player should reference active AI");
    }

    private static void shouldUseMixedSeatIteratorForActiveBettingProgression() {
        PokerGame game = new PokerGame(new User("MixedProgress"));
        game.startNewRound();

        Player human = game.getPlayers().get(0);
        int humanBetBefore = human.getCurrentBet();
        int potBefore = game.getPot();

        BettingRound round = game.createBettingRound(BettingRound.Phase.PREFLOP);
        int callAmount = round.callAmount(humanBetBefore);

        PokerGame.AIBettingResult result = game.runUnifiedBettingRound(round.getCurrentBet(), BettingRound.Action.CALL, callAmount);

        require(human.getCurrentBet() == humanBetBefore + callAmount, "human call should be applied in unified iterator");
        require(game.getPot() >= potBefore + callAmount, "pot should include human contribution in unified iterator");
        require(result.highBet >= round.getCurrentBet(), "high bet should stay synchronized after unified progression");
    }

    private static void shouldAwardPotByPlayerReference() {
        PokerGame game = new PokerGame(new User("Payout"));
        game.startNewRound();

        Player winner = game.getPlayers().get(0);
        int chipsBefore = winner.getChips();
        int potBefore = game.getPot();

        game.awardPotTo(winner);

        require(game.getPot() == 0, "pot should reset after award");
        require(winner.getChips() == chipsBefore + potBefore, "winner chips should increase by awarded pot");
    }

    private static void shouldSupportControllerStyleWinnerConsumption() {
        PokerGame humanWins = new PokerGame(new User("ControllerHuman"));
        humanWins.startNewRound();
        for (AIPlayer ai : humanWins.getAIPlayers()) {
            ai.setFolded(true);
        }

        Player humanWinner = humanWins.determineWinnerPlayer();
        require(humanWinner != null, "controller winner path should always return explicit winner");
        require(humanWinner instanceof User, "human winner should be represented as User player");

        PokerGame aiWins = new PokerGame(new User("ControllerAI"));
        aiWins.startNewRound();
        aiWins.getPlayers().get(0).setFolded(true);
        aiWins.getAIPlayers().get(1).setFolded(true);
        aiWins.getAIPlayers().get(2).setFolded(true);

        Player aiWinner = aiWins.determineWinnerPlayer();
        require(aiWinner != null, "controller winner path should return AI winner when human folds");
        require(aiWinner instanceof AIPlayer, "AI winner should be represented as AI player");
    }

    private static void shouldPreservePokerGameRankParityAcrossCategories() {
        PokerGame game = new PokerGame(new User("RankParity"));

        assertRank(game, PokerGame.HandRank.HIGH_CARD,
            c("2", "H"), c("9", "S"), c("A", "D"), c("7", "C"), c("3", "H"));

        assertRank(game, PokerGame.HandRank.ONE_PAIR,
            c("A", "H"), c("A", "S"), c("K", "D"), c("7", "C"), c("3", "H"));

        assertRank(game, PokerGame.HandRank.TWO_PAIR,
            c("K", "H"), c("K", "S"), c("3", "D"), c("3", "C"), c("9", "H"));

        assertRank(game, PokerGame.HandRank.THREE_OF_A_KIND,
            c("Q", "H"), c("Q", "S"), c("Q", "D"), c("8", "C"), c("2", "H"));

        assertRank(game, PokerGame.HandRank.STRAIGHT,
            c("5", "H"), c("6", "S"), c("7", "D"), c("8", "C"), c("9", "H"));

        assertRank(game, PokerGame.HandRank.FLUSH,
            c("2", "H"), c("6", "H"), c("9", "H"), c("J", "H"), c("K", "H"));

        assertRank(game, PokerGame.HandRank.FULL_HOUSE,
            c("8", "H"), c("8", "S"), c("8", "D"), c("K", "C"), c("K", "H"));

        assertRank(game, PokerGame.HandRank.FOUR_OF_A_KIND,
            c("J", "H"), c("J", "S"), c("J", "D"), c("J", "C"), c("4", "H"));

        assertRank(game, PokerGame.HandRank.STRAIGHT_FLUSH,
            c("5", "S"), c("6", "S"), c("7", "S"), c("8", "S"), c("9", "S"));

        assertRank(game, PokerGame.HandRank.ROYAL_FLUSH,
            c("10", "D"), c("J", "D"), c("Q", "D"), c("K", "D"), c("A", "D"));
    }

    private static void shouldPreserveAIDecisionThresholdBehaviorForNoCallFlow() {
        AIPlayer ai = new AIPlayer("ThresholdNoCall", 5000);

        setHoleCards(ai, c("8", "H"), c("8", "D"));
        BettingRound.Action regularAction = ai.decide(
            0,
            100,
            cards(c("8", "S"), c("K", "C"), c("K", "H"), c("2", "D"), c("3", "C")),
            AIPlayer.Role.NONE
        );
        require(regularAction == BettingRound.Action.RAISE, "full house strength should trigger raise when call is zero");

        setHoleCards(ai, c("5", "H"), c("6", "D"));
        BettingRound.Action blindAction = ai.decide(
            0,
            100,
            cards(c("7", "S"), c("8", "C"), c("9", "H"), c("2", "D"), c("K", "C")),
            AIPlayer.Role.BIG_BLIND
        );
        require(blindAction == BettingRound.Action.BET, "blind-protection threshold should trigger bet before generic check");
    }

    private static void shouldPreserveAIDecisionThresholdBehaviorForCallFlow() {
        AIPlayer ai = new AIPlayer("ThresholdCall", 5000);

        setHoleCards(ai, c("J", "H"), c("J", "D"));
        BettingRound.Action raiseAction = ai.decide(
            30,
            200,
            cards(c("J", "S"), c("J", "C"), c("2", "H"), c("3", "D"), c("9", "C")),
            AIPlayer.Role.NONE
        );
        require(raiseAction == BettingRound.Action.RAISE, "very strong hand should raise over call in contested pot");

        setHoleCards(ai, c("5", "H"), c("6", "D"));
        BettingRound.Action valueCall = ai.decide(
            20,
            100,
            cards(c("7", "S"), c("8", "C"), c("9", "H"), c("2", "D"), c("K", "C")),
            AIPlayer.Role.NONE
        );
        require(valueCall == BettingRound.Action.CALL, "strength above pot-odds threshold should call");

        setHoleCards(ai, c("A", "H"), c("A", "D"));
        BettingRound.Action blindDefenseCall = ai.decide(
            80,
            20,
            cards(c("K", "S"), c("K", "C"), c("2", "H"), c("7", "D"), c("9", "C")),
            AIPlayer.Role.SMALL_BLIND
        );
        require(blindDefenseCall == BettingRound.Action.CALL, "blind defense threshold should call even when pot-odds gate is not met");
    }

    private static void shouldPreserveFoldedStateWhenHumanAlreadyFoldedAtPhaseEntry() {
        PokerGame game = new PokerGame(new User("FoldedAtEntry"));
        game.startNewRound();
        game.getPlayers().get(0).setFolded(true);

        BettingRound round = game.createBettingRound(BettingRound.Phase.FLOP);
        PokerGame.AIBettingResult result = game.runUnifiedBettingRound(round.getCurrentBet(), null, 0);

        require(result.humanFolded, "unified betting round should report persisted human folded state at phase entry");
    }

    private static void shouldPreserveAutoFoldContinueWithoutPostFoldPrompts() {
        AutoFoldContinueRegressionTest.main(new String[0]);
    }

    private static void shouldRejectInvalidHandEvaluatorInputShapes() {
        HandEvaluator evaluator = new HandEvaluator();

        requireIllegalArgument(
            "non-null hole and community cards",
            () -> evaluator.evaluateBestRank(null, cards(c("2", "H"), c("3", "D"), c("4", "S")))
        );

        requireIllegalArgument(
            "non-null hole and community cards",
            () -> evaluator.evaluateBestRank(cards(c("A", "H"), c("K", "D")), null)
        );

        requireIllegalArgument(
            "exactly 2 hole cards",
            () -> evaluator.evaluateBestRank(cards(c("A", "H")), cards(c("2", "D"), c("3", "S"), c("4", "C"), c("5", "H")))
        );

        requireIllegalArgument(
            "at most 5 community cards",
            () -> evaluator.evaluateBestRank(
                cards(c("A", "H"), c("K", "D")),
                cards(c("2", "D"), c("3", "S"), c("4", "C"), c("5", "H"), c("6", "D"), c("7", "S"))
            )
        );

        requireIllegalArgument(
            "total of 5 to 7 cards",
            () -> evaluator.evaluateBestRank(cards(c("A", "H"), c("K", "D")), cards(c("2", "D"), c("3", "S")))
        );
    }

    private static void shouldResolveNonTieWinnerUsingFullHandStrength() {
        PokerGame game = new PokerGame(new User("NonTieStrength"));
        game.startNewRound();

        List<Player> players = game.getPlayers();
        Player human = players.get(0);
        Player ai0 = players.get(1);
        Player ai1 = players.get(2);
        Player ai2 = players.get(3);

        setPlayerHand(human, c("K", "S"), c("Q", "D"));
        setPlayerHand(ai0, c("J", "C"), c("10", "H"));
        ai1.setFolded(true);
        ai2.setFolded(true);

        setCommunityCards(game, cards(c("A", "H"), c("A", "D"), c("9", "S"), c("5", "C"), c("2", "H")));

        ShowdownResult result = game.determineShowdownResult();
        require(!result.isTie(), "non-tie showdown should not be marked as tie");
        require(result.getWinners().size() == 1, "non-tie showdown should have exactly one winner");
        require(result.getWinners().get(0) == human, "higher kicker should win within same rank category");
        require(result.getBestRank() == PokerGame.HandRank.ONE_PAIR, "best rank should match evaluated category");
        require(game.determineWinnerPlayer() == human, "winner compatibility API should resolve to same player");
    }

    private static void shouldSplitPotEvenlyAcrossExactTieWinners() {
        PokerGame game = new PokerGame(new User("EvenSplit"));
        game.startNewRound();

        List<Player> players = game.getPlayers();
        Player human = players.get(0);
        Player ai0 = players.get(1);
        Player ai1 = players.get(2);
        Player ai2 = players.get(3);

        setPlayerHand(human, c("2", "C"), c("3", "D"));
        setPlayerHand(ai1, c("4", "C"), c("5", "D"));
        ai0.setFolded(true);
        ai2.setFolded(true);

        setCommunityCards(game, cards(c("10", "H"), c("J", "H"), c("Q", "H"), c("K", "H"), c("A", "H")));
        setPot(game, 100);

        int humanBefore = human.getChips();
        int ai1Before = ai1.getChips();

        ShowdownResult result = game.determineShowdownResult();
        require(result.isTie(), "equal best-five showdown should be marked as tie");
        require(result.getWinners().size() == 2, "two active tied players should be included as winners");

        game.awardPot(result);

        require(human.getChips() == humanBefore + 50, "human should receive even split share");
        require(ai1.getChips() == ai1Before + 50, "ai should receive even split share");
        require(game.getPot() == 0, "pot should be cleared after tie payout");
    }

    private static void shouldAssignRemainderDeterministicallyForThreeWayTie() {
        int expectedRemainderWinnerSeat = 0;

        for (int i = 0; i < 5; i++) {
            PokerGame game = new PokerGame(new User("Remainder" + i));
            game.startNewRound();

            List<Player> players = game.getPlayers();
            Player seat0 = players.get(0);
            Player seat1 = players.get(1);
            Player seat2 = players.get(2);
            Player seat3 = players.get(3);

            setPlayerHand(seat0, c("2", "C"), c("3", "D"));
            setPlayerHand(seat1, c("4", "C"), c("5", "D"));
            setPlayerHand(seat3, c("6", "C"), c("7", "D"));
            seat2.setFolded(true);

            setCommunityCards(game, cards(c("10", "H"), c("J", "H"), c("Q", "H"), c("K", "H"), c("A", "H")));
            setPot(game, 10);

            int[] before = snapshotChips(players);
            ShowdownResult result = game.determineShowdownResult();
            require(result.isTie(), "three-way equal showdown should be tie");
            require(result.getWinners().size() == 3, "three active players should be in winner set");

            game.awardPot(result);

            int remainderSeat = findRemainderWinnerSeat(players, before);
            require(remainderSeat == expectedRemainderWinnerSeat,
                "remainder chip must be assigned by deterministic dealer-relative order");
        }
    }

    private static int[] snapshotChips(List<Player> players) {
        int[] chips = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            chips[i] = players.get(i).getChips();
        }
        return chips;
    }

    private static int findRemainderWinnerSeat(List<Player> players, int[] chipsBefore) {
        int seat = -1;
        for (int i = 0; i < players.size(); i++) {
            int delta = players.get(i).getChips() - chipsBefore[i];
            if (delta == 4) {
                if (seat != -1) {
                    throw new AssertionError("only one seat should receive remainder chip");
                }
                seat = i;
            } else if (delta == 3 || delta == 0) {
            } else {
                throw new AssertionError("unexpected payout delta for seat " + i + ": " + delta);
            }
        }

        if (seat == -1) {
            throw new AssertionError("expected one remainder winner seat");
        }
        return seat;
    }

    private static void setPlayerHand(Player player, Card first, Card second) {
        player.clearHand();
        player.setFolded(false);
        player.addCard(first);
        player.addCard(second);
    }

    private static void setCommunityCards(PokerGame game, ArrayList<Card> cards) {
        try {
            Field field = PokerGame.class.getDeclaredField("communityCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<Card> community = (ArrayList<Card>) field.get(game);
            community.clear();
            community.addAll(cards);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to configure community cards", ex);
        }
    }

    private static void setPot(PokerGame game, int value) {
        try {
            Field field = PokerGame.class.getDeclaredField("pot");
            field.setAccessible(true);
            field.setInt(game, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to configure pot", ex);
        }
    }

    private static void setHoleCards(AIPlayer ai, Card first, Card second) {
        ai.clearHand();
        ai.addCard(first);
        ai.addCard(second);
    }

    @SafeVarargs
    private static ArrayList<Card> cards(Card... values) {
        return new ArrayList<>(List.of(values));
    }

    private static void assertRank(PokerGame game, PokerGame.HandRank expected, Card... cards) {
        PokerGame.HandRank actual = game.evaluateCards(List.of(cards));
        require(actual == expected, "expected " + expected + " but got " + actual);
    }

    private static Card c(String rank, String suit) {
        return new Card(rank, suit);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireIllegalArgument(String expectedMessagePart, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException containing: " + expectedMessagePart);
        } catch (IllegalArgumentException ex) {
            require(
                ex.getMessage() != null && ex.getMessage().contains(expectedMessagePart),
                "unexpected exception message: " + ex.getMessage()
            );
        }
    }
}
