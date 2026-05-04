package model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * JUnit 5 style tests for HandEvaluator tie-break logic.
 */
class HandEvaluatorTieBreakTest {

    @Test
    void shouldCompareHigherKickerWithinSameRankAsStronger() {
        HandEvaluator evaluator = new HandEvaluator();

        HandStrength stronger = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("K", "D"), card("9", "C"), card("7", "D"), card("4", "S"), card("2", "H")
        ));

        HandStrength weaker = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("Q", "D"), card("9", "C"), card("7", "D"), card("4", "S"), card("2", "H")
        ));

        TestUtils.assertRank(HandRank.ONE_PAIR, stronger);
        TestUtils.assertRank(HandRank.ONE_PAIR, weaker);
        Assertions.assertTrue(stronger.compareTo(weaker) > 0, "higher kicker should compare as stronger within same rank");
    }

    @Test
    void shouldTreatEquivalentBestFiveAsExactTie() {
        HandEvaluator evaluator = new HandEvaluator();

        HandStrength first = evaluator.evaluateBestHandStrength(cards(
            card("A", "H"), card("K", "D"), card("Q", "C"), card("J", "S"), card("10", "H"), card("3", "D"), card("2", "C")
        ));

        HandStrength second = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("K", "H"), card("Q", "D"), card("J", "C"), card("10", "S"), card("4", "H"), card("2", "D")
        ));

        TestUtils.assertRank(HandRank.STRAIGHT, first);
        TestUtils.assertRank(HandRank.STRAIGHT, second);
        Assertions.assertEquals(0, first.compareTo(second), "equivalent best five should compare equal");
        TestUtils.assertHandEquals(first, second);
    }

    @Test
    void shouldKeepEvaluateBestRankAsCompatibilityWrapper() {
        HandEvaluator evaluator = new HandEvaluator();
        ArrayList<Card> holeCards = cards(card("A", "H"), card("K", "D"));
        ArrayList<Card> communityCards = cards(card("Q", "C"), card("J", "S"), card("10", "H"), card("2", "D"), card("3", "C"));

        HandRank rank = evaluator.evaluateBestRank(holeCards, communityCards);
        HandStrength strength = evaluator.evaluateBestHandStrength(cards(
            card("A", "H"), card("K", "D"), card("Q", "C"), card("J", "S"), card("10", "H"), card("2", "D"), card("3", "C")
        ));

        TestUtils.assertRank(HandRank.STRAIGHT, rank);
        TestUtils.assertRank(HandRank.STRAIGHT, strength);
        Assertions.assertEquals(rank, strength.getRank(), "rank wrapper should match strength rank");
    }

    // ========== Test Utils ==========

    private static ArrayList<Card> cards(Card... values) {
        return new ArrayList<>(List.of(values));
    }
}