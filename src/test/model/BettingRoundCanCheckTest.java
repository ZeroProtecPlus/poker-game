package model;

public class BettingRoundCanCheckTest {
    public static void main(String[] args) {
        shouldAllowCheckWhenPlayerMatchesNonZeroBet();
        shouldDisallowCheckWhenPlayerHasNotMatchedBet();
        shouldAllowCheckForBigBlindEquivalentPreflopState();
        shouldDisallowCheckForRaisedPreflopWhenPlayerIsUnmatched();
        shouldAllowCheckPostflopWhenNoActiveBet();
        shouldEvaluateCanCheckPerPlayerCurrentBetInSameRoundState();
        System.out.println("BettingRoundCanCheckTest: all tests passed");
    }

    private static void shouldAllowCheckWhenPlayerMatchesNonZeroBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 200, 40, 20);

        require(round.canCheck(40), "matched non-zero round bet should allow check");
    }

    private static void shouldDisallowCheckWhenPlayerHasNotMatchedBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 150, 50, 20);

        require(!round.canCheck(30), "unmatched round bet should disallow check");
    }

    private static void shouldAllowCheckForBigBlindEquivalentPreflopState() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 30, 20, 20);

        require(round.canCheck(20), "preflop big-blind-equivalent state should allow check");
    }

    private static void shouldDisallowCheckForRaisedPreflopWhenPlayerIsUnmatched() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 220, 60, 20);

        require(!round.canCheck(40), "raised preflop with unmatched bet should disallow check");
        require(round.callAmount(40) > 0, "raised preflop unmatched player should still owe chips to call");
    }

    private static void shouldAllowCheckPostflopWhenNoActiveBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 180, 0, 20);

        require(round.canCheck(0), "postflop with no active bet should allow check");
    }

    private static void shouldEvaluateCanCheckPerPlayerCurrentBetInSameRoundState() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 260, 50, 20);

        require(round.canCheck(50), "matched evaluation should allow check");
        require(!round.canCheck(30), "unmatched evaluation should disallow check in same round state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
