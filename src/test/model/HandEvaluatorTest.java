package model;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * JUnit 5 tests for HandEvaluator - 16 test cases covering all HandRanks.
 */
class HandEvaluatorTest {

    // ========== HIGH_CARD Tests ==========

    @Test
    void shouldEvaluateHighCard() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("9", "H"), card("7", "D"), card("5", "C"), card("3", "S"), card("2", "H"), card("2", "D")
        ));
        TestUtils.assertRank(HandRank.HIGH_CARD, result);
    }

    @Test
    void shouldEvaluateHighCardWithAce() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("K", "H"), card("Q", "D"), card("J", "C"), card("9", "S"), card("7", "H"), card("3", "D")
        ));
        TestUtils.assertRank(HandRank.HIGH_CARD, result);
    }

    // ========== ONE_PAIR Tests ==========

    @Test
    void shouldEvaluateOnePair() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("K", "H"), card("Q", "D"), card("9", "C"), card("7", "D"), card("4", "S"), card("2", "H")
        ));
        TestUtils.assertRank(HandRank.ONE_PAIR, result);
    }

    @Test
    void shouldEvaluateOnePairWithAcePair() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("K", "D"), card("Q", "C"), card("J", "D"), card("9", "S"), card("7", "H")
        ));
        TestUtils.assertRank(HandRank.ONE_PAIR, result);
    }

    // ========== TWO_PAIR Tests ==========

    @Test
    void shouldEvaluateTwoPair() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("K", "H"), card("Q", "D"), card("Q", "C"), card("9", "D"), card("4", "S"), card("2", "H")
        ));
        TestUtils.assertRank(HandRank.TWO_PAIR, result);
    }

    @Test
    void shouldEvaluateTwoPairWithAcesAndKings() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("K", "D"), card("K", "C"), card("Q", "D"), card("9", "S"), card("7", "H")
        ));
        TestUtils.assertRank(HandRank.TWO_PAIR, result);
    }

    // ========== THREE_OF_A_KIND Tests ==========

    @Test
    void shouldEvaluateThreeOfAKind() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("K", "H"), card("K", "D"), card("Q", "C"), card("9", "D"), card("4", "S"), card("2", "H")
        ));
        TestUtils.assertRank(HandRank.THREE_OF_A_KIND, result);
    }

    // ========== STRAIGHT Tests ==========

    @Test
    void shouldEvaluateStraight() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("Q", "H"), card("J", "D"), card("10", "C"), card("9", "S"), card("7", "H"), card("2", "D")
        ));
        TestUtils.assertRank(HandRank.STRAIGHT, result);
    }

    @Test
    void shouldEvaluateStraightWheel() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("2", "H"), card("3", "D"), card("4", "C"), card("5", "S"), card("K", "H"), card("Q", "D")
        ));
        TestUtils.assertRank(HandRank.STRAIGHT, result);
    }

    // ========== FLUSH Tests ==========

    @Test
    void shouldEvaluateFlush() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("9", "S"), card("7", "S"), card("5", "S"), card("3", "S"), card("Q", "H"), card("2", "D")
        ));
        TestUtils.assertRank(HandRank.FLUSH, result);
    }

    @Test
    void shouldEvaluateFlushAceHigh() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "H"), card("K", "H"), card("Q", "H"), card("J", "H"), card("9", "H"), card("7", "S"), card("2", "D")
        ));
        TestUtils.assertRank(HandRank.FLUSH, result);
    }

    // ========== FULL_HOUSE Tests ==========

    @Test
    void shouldEvaluateFullHouse() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("K", "H"), card("K", "D"), card("Q", "C"), card("Q", "D"), card("9", "S"), card("7", "H")
        ));
        TestUtils.assertRank(HandRank.FULL_HOUSE, result);
    }

    @Test
    void shouldEvaluateFullHouseWithAces() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("A", "D"), card("K", "C"), card("K", "D"), card("Q", "S"), card("J", "H")
        ));
        TestUtils.assertRank(HandRank.FULL_HOUSE, result);
    }

    // ========== FOUR_OF_A_KIND Tests ==========

    @Test
    void shouldEvaluateFourOfAKind() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("K", "S"), card("K", "H"), card("K", "D"), card("K", "C"), card("Q", "D"), card("9", "S"), card("7", "H")
        ));
        TestUtils.assertRank(HandRank.FOUR_OF_A_KIND, result);
    }

    @Test
    void shouldEvaluateFourOfAKindWithAces() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("A", "H"), card("A", "D"), card("A", "C"), card("K", "D"), card("Q", "S"), card("J", "H")
        ));
        TestUtils.assertRank(HandRank.FOUR_OF_A_KIND, result);
    }

    // ========== STRAIGHT_FLUSH Tests ==========

    @Test
    void shouldEvaluateStraightFlush() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("9", "S"), card("8", "S"), card("7", "S"), card("6", "S"), card("5", "S"), card("K", "H"), card("Q", "D")
        ));
        TestUtils.assertRank(HandRank.STRAIGHT_FLUSH, result);
    }

    // ========== ROYAL_FLUSH Test ==========

    @Test
    void shouldEvaluateRoyalFlush() {
        HandEvaluator evaluator = new HandEvaluator();
        HandStrength result = evaluator.evaluateBestHandStrength(cards(
            card("A", "S"), card("K", "S"), card("Q", "S"), card("J", "S"), card("10", "S"), card("7", "H"), card("2", "D")
        ));
        TestUtils.assertRank(HandRank.ROYAL_FLUSH, result);
    }

    // ========== Test Utils ==========

    private static ArrayList<Card> cards(Card... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static Card card(String rank, String suit) {
        return new Card(rank, suit);
    }
}