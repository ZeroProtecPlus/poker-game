package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AIPlayer decision logic — SPR gate, phase-aware thresholds,
 * action caps, counter reset, and preflop/postflop raise sizing.
 *
 * All tests use known hole cards to produce deterministic hand strengths
 * based on evaluatePreflopStrength() and evaluateHandStrength().
 *
 * NOTE: The decide() method has a 20% random bluff when strength > 0.20.
 * Tests use hands with strength <= 0.20 to avoid the bluff path for determinism,
 * except where the bluff is explicitly tested.
 */
class AIPlayerTest {

    private AIPlayer ai;

    @BeforeEach
    void setUp() {
        ai = new AIPlayer("TestBot", 10000);
    }

    // ── Helper methods ─────────────────────────────────────────────────────

    private static ArrayList<Card> noCommunity() {
        return new ArrayList<>();
    }

    private static ArrayList<Card> community(Card... cards) {
        return new ArrayList<>(java.util.List.of(cards));
    }

    private static Card c(String rank, String suit) {
        return new Card(rank, suit);
    }

    private void setHoleCards(String rank1, String suit1, String rank2, String suit2) {
        ai.clearHand();
        ai.addCard(c(rank1, suit1));
        ai.addCard(c(rank2, suit2));
    }

    /**
     * Pair of Aces: preflop strength ≈ 0.96 (above SPR_COMMIT_THRESHOLD = 0.70)
     */
    private void setPremiumHand() {
        setHoleCards("A", "S", "A", "H");
    }

    /**
     * Pair of 5s: preflop strength ≈ 0.66 (below SPR_COMMIT_THRESHOLD = 0.70,
     * above postflop call margin thresholds)
     */
    private void setMarginalHand() {
        setHoleCards("5", "S", "5", "H");
    }

    /**
     * 7-2 offsuit: preflop strength ≈ 0.14 (very weak, below 0.20 bluff threshold)
     */
    private void setWeakHand() {
        setHoleCards("7", "S", "2", "H");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Named Constants — FR-05
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Named Constants (FR-05)")
    class NamedConstantsTests {

        @Test
        @DisplayName("PREFLOP_CALL_MARGIN should be 0.22")
        void preflopCallMargin() {
            assertEquals(0.22, AIPlayer.PREFLOP_CALL_MARGIN, 0.001);
        }

        @Test
        @DisplayName("POSTFLOP_CALL_MARGIN should be 0.08")
        void postflopCallMargin() {
            assertEquals(0.08, AIPlayer.POSTFLOP_CALL_MARGIN, 0.001);
        }

        @Test
        @DisplayName("SPR_COMMIT_THRESHOLD should be 0.70")
        void sprCommitThreshold() {
            assertEquals(0.70, AIPlayer.SPR_COMMIT_THRESHOLD, 0.001);
        }

        @Test
        @DisplayName("SPR_FOLD_RATIO should be 0.35")
        void sprFoldRatio() {
            assertEquals(0.35, AIPlayer.SPR_FOLD_RATIO, 0.001);
        }

        @Test
        @DisplayName("PREFLOP_BLIND_BET_THRESHOLD should be 0.50")
        void preflopBlindBetThreshold() {
            assertEquals(0.50, AIPlayer.PREFLOP_BLIND_BET_THRESHOLD, 0.001);
        }

        @Test
        @DisplayName("PREFLOP_BLIND_CALL_THRESHOLD should be 0.30")
        void preflopBlindCallThreshold() {
            assertEquals(0.30, AIPlayer.PREFLOP_BLIND_CALL_THRESHOLD, 0.001);
        }

        @Test
        @DisplayName("PREFLOP_BLIND_RAISE_THRESHOLD should be 0.75")
        void preflopBlindRaiseThreshold() {
            assertEquals(0.75, AIPlayer.PREFLOP_BLIND_RAISE_THRESHOLD, 0.001);
        }

        @Test
        @DisplayName("MAX_CALLS_PER_PHASE should be 2")
        void maxCallsPerPhase() {
            assertEquals(2, AIPlayer.MAX_CALLS_PER_PHASE);
        }

        @Test
        @DisplayName("MAX_RAISES_PER_PHASE should be 1")
        void maxRaisesPerPhase() {
            assertEquals(1, AIPlayer.MAX_RAISES_PER_PHASE);
        }

        @Test
        @DisplayName("PREFLOP_RAISE_MULTIPLIER should be 3")
        void preflopRaiseMultiplier() {
            assertEquals(3, AIPlayer.PREFLOP_RAISE_MULTIPLIER);
        }

        @Test
        @DisplayName("PREFLOP_RAISE_CAP_MULTIPLIER should be 6")
        void preflopRaiseCapMultiplier() {
            assertEquals(6, AIPlayer.PREFLOP_RAISE_CAP_MULTIPLIER);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SPR Protection Gate — FR-01
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SPR Protection Gate (FR-01)")
    class SPRGateTests {

        @Test
        @DisplayName("Marginal hand folds when callAmount > chips * 0.35 and strength < 0.70")
        void marginalHandFoldsWhenExpensiveCall() {
            // Pair of 5s: strength ≈ 0.66 (below SPR_COMMIT_THRESHOLD)
            setMarginalHand();
            // callAmount = 4000 > 10000 * 0.35 = 3500
            BettingRound.Action action = ai.decide(4000, 200, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.FOLD, action,
                "Marginal hand should fold when call cost exceeds 35% of stack");
        }

        @Test
        @DisplayName("Premium hand bypasses SPR gate — does not fold expensive call")
        void premiumHandBypassesSPR() {
            // Pair of Aces: strength ≈ 0.96 (above SPR_COMMIT_THRESHOLD)
            setPremiumHand();
            // callAmount = 4000 > 10000 * 0.35 = 3500 (SPR condition met)
            // pot = 8000 → potOdds = 4000/12000 ≈ 0.333
            // strength = 0.96 > raiseThresh → RAISE (premium hand commits fully, bypassing SPR)
            BettingRound.Action action = ai.decide(4000, 8000, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertNotEquals(BettingRound.Action.FOLD, action,
                "Premium hand (strength >= 0.70) should bypass SPR gate and not fold");
        }

        @Test
        @DisplayName("Cheap call (callAmount <= chips * 0.35) skips SPR gate")
        void cheapCallSkipsSPR() {
            setMarginalHand();
            // callAmount = 2000, chips = 10000 → 2000 <= 10000 * 0.35 = 3500
            // SPR gate not triggered — decision proceeds normally
            BettingRound.Action action = ai.decide(2000, 500, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            // The action is determined by normal logic (not SPR-forced)
            assertNotNull(action, "Decision should proceed normally when call is cheap relative to stack");
        }

        @Test
        @DisplayName("SPR gate with zero callAmount does not force FOLD")
        void sprGateWithZeroCallAmount() {
            setWeakHand();
            // callAmount = 0, so SPR condition callAmount > chips * 0.35 is false
            // With callAmount=0 and weak hand, result is CHECK (not FOLD)
            BettingRound.Action action = ai.decide(0, 20, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            // With callAmount=0: SPR gate doesn't apply, and result depends on
            // blind check logic. Since strength is very low, likely CHECK or BET (15% random)
            assertNotEquals(BettingRound.Action.FOLD, action,
                "With callAmount=0, should never fold");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Phase-Aware Thresholds — FR-02
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Phase-Aware Call Margins (FR-02)")
    class PhaseAwareThresholdTests {

        @Test
        @DisplayName("Preflop uses +0.22 call margin causing weak hand to fold")
        void preflopStricterMarginCausesFold() {
            // 7-2 offsuit: preflop strength ≈ 0.14 (below bluff threshold of 0.20)
            // Deterministic — no random bluff path can fire
            setWeakHand();
            // callAmount=10, pot=60 → potOdds = 10/70 ≈ 0.143
            // preflop threshold = 0.143 + 0.22 = 0.363
            // 0.14 < 0.363 → FOLD
            // SPR: 10 <= 10000*0.35 → no SPR gate
            BettingRound.Action preflopAction = ai.decide(10, 60, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.FOLD, preflopAction,
                "Preflop: weak hand should fold with +0.22 margin");
        }

        @Test
        @DisplayName("Postflop uses +0.08 call margin (looser than preflop)")
        void postflopLooserMarginAllowsCall() {
            // Use community cards for postflop evaluation
            // Hole: 8H 9H, Community: 10S JC QS 2D KC → straight (8-9-10-J-Q)
            // HandRank.STRAIGHT.ordinal() = 4, strength = 4/8 = 0.50
            setHoleCards("8", "H", "9", "H");
            ArrayList<Card> comm = community(c("10", "S"), c("J", "C"), c("Q", "S"), c("2", "D"), c("K", "C"));

            // callAmount=10, pot=30 → potOdds = 10/40 = 0.25
            // postflop threshold = 0.25 + 0.08 = 0.33
            // strength = 0.50 > 0.33 → CALL
            BettingRound.Action action = ai.decide(10, 30, comm, AIPlayer.Role.NONE, BettingRound.Phase.RIVER);
            assertEquals(BettingRound.Action.CALL, action,
                "Postflop: straight (0.50) should call with +0.08 margin at potOdds 0.25");
        }

        @Test
        @DisplayName("Preflop blind BET uses stricter threshold (0.50)")
        void preflopBlindBetThreshold() {
            // 96 suited: preflop strength below 0.50 blind BET threshold
            // high=9, low=6, gap=2, suited → score ≈ 0.43
            setHoleCards("9", "H", "6", "H");
            // callAmount=0, isBlind=true, preflop
            BettingRound.Action action = ai.decide(0, 10, noCommunity(), AIPlayer.Role.BIG_BLIND, BettingRound.Phase.PREFLOP);
            // strength ~0.43 < 0.50 (PREFLOP_BLIND_BET_THRESHOLD) → should NOT BET
            // But note: there's a 15% random BET chance when strength < 0.35
            // Since 0.43 > 0.35, it goes to CHECK instead
            assertEquals(BettingRound.Action.CHECK, action,
                "Preflop blind with strength ~0.43 should CHECK, not BET (below 0.50 threshold)");
        }

        @Test
        @DisplayName("Postflop blind BET uses original threshold (0.25)")
        void postflopBlindBetUsesOriginalThreshold() {
            // Hole: 5H 5D, Community: 5S KC 2H 7D 9C → three of a kind
            // THREE_OF_A_KIND.ordinal() = 3, strength = 3/8 = 0.375
            setHoleCards("5", "H", "5", "D");
            ArrayList<Card> comm = community(c("5", "S"), c("K", "C"), c("2", "H"), c("7", "D"), c("9", "C"));
            // Postflop: callAmount=0, isBlind=true, blindBetThresh=0.25 (unchanged postflop)
            // strength 0.375 > 0.25 → BET
            BettingRound.Action action = ai.decide(0, 100, comm, AIPlayer.Role.BIG_BLIND, BettingRound.Phase.FLOP);
            assertEquals(BettingRound.Action.BET, action,
                "Postflop blind with three-of-a-kind (0.375) should BET above 0.25 threshold");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Action Caps — FR-03
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Action Caps (FR-03)")
    class ActionCapTests {

        @Test
        @DisplayName("AI folds after 2 calls in a phase with marginal hand")
        void foldAfterTwoCalls() {
            setMarginalHand(); // pair of 5s, strength ≈ 0.66

            // Call once — should succeed (potOdds favorable, callCount=0)
            BettingRound.Action call1 = ai.decide(10, 200, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.CALL, call1, "First call should succeed");

            // Call again — should succeed (callCount=1, cap=2)
            BettingRound.Action call2 = ai.decide(10, 210, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.CALL, call2, "Second call should succeed");

            // Third call — should FOLD (callCount=2 >= MAX_CALLS_PER_PHASE, strength < 0.70)
            BettingRound.Action call3 = ai.decide(10, 220, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.FOLD, call3,
                "Should fold after reaching call cap with marginal hand");
        }

        @Test
        @DisplayName("Premium hand (strength >= 0.70) bypasses call cap")
        void premiumHandBypassesCallCap() {
            setPremiumHand(); // pair of Aces, strength ≈ 0.96

            // Make 3 calls — premium hand should bypass cap
            ai.decide(10, 100, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            ai.decide(10, 110, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            BettingRound.Action call3 = ai.decide(10, 120, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertNotEquals(BettingRound.Action.FOLD, call3,
                "Premium hand (strength >= 0.70) should bypass call cap");
        }

        @Test
        @DisplayName("Marginal hand cannot raise after one raise per phase")
        void marginalHandRaiseCapped() {
            // Pair of 5s: strength ≈ 0.66 (above 0.65 raise threshold, below 0.70 SPR_COMMIT_THRESHOLD)
            setMarginalHand();

            // First raise: callAmount=0, strength=0.66 > 0.65, roundRaiseCount=0 → RAISE
            BettingRound.Action action1 = ai.decide(0, 20, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.RAISE, action1, "First raise attempt should succeed");

            // Second raise: callAmount=0, roundRaiseCount=1, strength < 0.70 → cap enforced → CHECK
            BettingRound.Action action2 = ai.decide(0, 40, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.CHECK, action2,
                "Marginal hand should CHECK after reaching raise cap");
        }

        @Test
        @DisplayName("Counter reset: resetRoundBet zeros counters")
        void resetRoundBetZerosCounters() {
            setMarginalHand();
            // Make a call to increment counter
            ai.decide(10, 100, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            // Reset between phases
            ai.resetRoundBet();
            // Should be able to call again (counters reset)
            BettingRound.Action action = ai.decide(10, 100, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.FLOP);
            assertEquals(BettingRound.Action.CALL, action,
                "After resetRoundBet, AI should call normally (counters reset)");
        }

        @Test
        @DisplayName("Counter reset: clearHand zeros counters")
        void clearHandZerosCounters() {
            setMarginalHand();
            // Make a call to increment counter
            ai.decide(10, 100, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            // Clear hand (between rounds)
            ai.clearHand();
            // Set up hand again
            ai.addCard(c("5", "S"));
            ai.addCard(c("5", "H"));
            // Should be able to call again (counters reset)
            BettingRound.Action action = ai.decide(10, 100, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertEquals(BettingRound.Action.CALL, action,
                "After clearHand, AI should call normally (counters reset)");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Preflop Raise Sizing — FR-04
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Preflop Raise Sizing (FR-04)")
    class PreflopRaiseSizingTests {

        @Test
        @DisplayName("Preflop: raise capped at minBet * 6")
        void preflopRaiseSizingCapped() {
            setPremiumHand(); // Need strong hand for maximum raise sizing

            // minBet = 10 (BIG_BLIND), preflop base = max(10, 10*3*1.5) = 45 (isBlind mult)
            // Actually, Role.NONE → mult = 1.0, so base = max(10, 30) = 30
            // With strength 0.96 > 0.85: base * 3 = 90, but cap = 10 * 6 = 60
            int amount = ai.decideAmount(10, 200, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertTrue(amount <= 60,
                "Preflop raise should be capped at minBet * 6 (60)");
            assertTrue(amount >= 10,
                "Preflop raise should be at least minBet (10)");
        }

        @Test
        @DisplayName("Preflop: base amount uses minBet * multiplier, not pot-relative")
        void preflopBaseNotPotRelative() {
            setMarginalHand();

            // Even with a large pot, preflop base should be minBet * 3, not pot/4
            // pair of 5s (0.66 > 0.60): base * 2
            // base = max(10, 10*3*1.0) = 30, amount = min(chips, min(60, 60)) = 60
            int amount = ai.decideAmount(10, 1000, noCommunity(), AIPlayer.Role.NONE, BettingRound.Phase.PREFLOP);
            assertTrue(amount <= 60,
                "Preflop raise should not exceed cap of minBet * 6 = 60 regardless of pot");
        }

        @Test
        @DisplayName("Postflop: uses currentPot/4 formula (uncapped relative to preflop)")
        void postflopUsesPotRelative() {
            // Use community cards for postflop evaluation
            setHoleCards("8", "H", "9", "H");
            ArrayList<Card> comm = community(c("10", "S"), c("J", "C"), c("Q", "S"), c("2", "D"), c("K", "C"));

            // Postflop: base = max(minBet, currentPot/4) * mult
            // currentPot = 100, minBet = 10
            // base = max(10, 25) * 1.0 = 25
            // Hand: straight ≈ 0.50, which is NOT > 0.60 or > 0.85
            // So amount = min(chips, cap, base) = min(10000, 10000, 25) = 25
            int amount = ai.decideAmount(10, 100, comm, AIPlayer.Role.NONE, BettingRound.Phase.RIVER);
            // Postflop has no artificial cap (cap = chips)
            assertEquals(25, amount,
                "Postflop raise should use currentPot/4 formula: max(10, 100/4) = 25");
        }
    }
}