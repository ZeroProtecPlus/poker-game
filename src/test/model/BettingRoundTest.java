package model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for BettingRound - canBet, playerBets, addToPot.
 */
class BettingRoundTest {

    // ── canBet tests ─────────────────────────────────────────────────────────

    @Test
    void shouldAllowBetWhenNoCurrentBet() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 30, 0, 20);
        assertTrue(round.canBet(), "no current bet should allow bet");
    }

    @Test
    void shouldDisallowBetWhenBetAlreadyExists() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 150, 40, 20);
        assertFalse(round.canBet(), "existing bet should disallow bet");
    }

    // ── playerBets tests ────────────────────────────────────────────────────

    @Test
    void playerBetsUpdatesCurrentBetWhenRaising() {
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 50, 20, 20);
        round.playerBets(60);
        assertEquals(60, round.getCurrentBet(), "current bet should update to raised amount");
    }

    @Test
    void playerBetsDoesNotUpdateCurrentBetWhenCalling() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 200, 40, 20);
        round.playerBets(30);
        assertEquals(40, round.getCurrentBet(), "current bet should not decrease when calling less");
    }

    @Test
    void playerBetsAlwaysAddsToPot() {
        BettingRound round = new BettingRound(BettingRound.Phase.RIVER, 100, 0, 20);
        round.playerBets(50);
        assertEquals(150, round.getPot(), "pot should include bet amount");
    }

    // ── addToPot tests ──────────────────────────────────────────────────────

    @Test
    void addToPotIncreasesPotByAmount() {
        BettingRound round = new BettingRound(BettingRound.Phase.FLOP, 100, 0, 20);
        round.addToPot(25);
        assertEquals(125, round.getPot(), "pot should increase by added amount");
    }

    @Test
    void addToPotAccumulatesAcrossMultipleCalls() {
        BettingRound round = new BettingRound(BettingRound.Phase.TURN, 50, 0, 20);
        round.addToPot(30);
        round.addToPot(45);
        assertEquals(125, round.getPot(), "pot should accumulate all additions");
    }
}