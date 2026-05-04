package model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for BettingRound.canCheck() - 6 test cases.
 */
class BettingRoundCanCheckTest {

    @Test
    void shouldAllowCheckWhenPlayerMatchesNonZeroBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 200, 40, 20);
        assertTrue(round.canCheck(40), "matched non-zero round bet should allow check");
    }

    @Test
    void shouldDisallowCheckWhenPlayerHasNotMatchedBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 150, 50, 20);
        assertFalse(round.canCheck(30), "unmatched round bet should disallow check");
    }

    @Test
    void shouldAllowCheckForBigBlindEquivalentPreflopState() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 30, 20, 20);
        assertTrue(round.canCheck(20), "preflop big-blind-equivalent state should allow check");
    }

    @Test
    void shouldDisallowCheckForRaisedPreflopWhenPlayerIsUnmatched() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 220, 60, 20);
        assertFalse(round.canCheck(40), "raised preflop with unmatched bet should disallow check");
        assertTrue(round.callAmount(40) > 0, "raised preflop unmatched player should still owe chips to call");
    }

    @Test
    void shouldAllowCheckPostflopWhenNoActiveBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 180, 0, 20);
        assertTrue(round.canCheck(0), "postflop with no active bet should allow check");
    }

    @Test
    void shouldEvaluateCanCheckPerPlayerCurrentBetInSameRoundState() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 260, 50, 20);
        assertTrue(round.canCheck(50), "matched evaluation should allow check");
        assertFalse(round.canCheck(30), "unmatched evaluation should disallow check in same round state");
    }
}