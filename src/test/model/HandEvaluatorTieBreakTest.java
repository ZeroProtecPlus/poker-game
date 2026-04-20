package model;

import java.util.ArrayList;
import java.util.List;

public class HandEvaluatorTieBreakTest {
    public static void main(String[] args) {
        shouldCompareHigherKickerWithinSameRankAsStronger();
        shouldTreatEquivalentBestFiveAsExactTie();
        shouldKeepEvaluateBestRankAsCompatibilityWrapper();
        System.out.println("HandEvaluatorTieBreakTest: all tests passed");
    }

    private static void shouldCompareHigherKickerWithinSameRankAsStronger() {
        HandEvaluator evaluator = new HandEvaluator();

        HandEvaluator.HandStrength stronger = evaluator.evaluateBestHandStrength(cards(
            c("A", "S"), c("A", "H"), c("K", "D"), c("9", "C"), c("7", "D"), c("4", "S"), c("2", "H")
        ));

        HandEvaluator.HandStrength weaker = evaluator.evaluateBestHandStrength(cards(
            c("A", "S"), c("A", "H"), c("Q", "D"), c("9", "C"), c("7", "D"), c("4", "S"), c("2", "H")
        ));

        require(stronger.getRank() == HandEvaluator.HandRank.ONE_PAIR, "expected one pair rank for stronger hand");
        require(weaker.getRank() == HandEvaluator.HandRank.ONE_PAIR, "expected one pair rank for weaker hand");
        require(stronger.compareTo(weaker) > 0, "higher kicker should compare as stronger within same rank");
    }

    private static void shouldTreatEquivalentBestFiveAsExactTie() {
        HandEvaluator evaluator = new HandEvaluator();

        HandEvaluator.HandStrength first = evaluator.evaluateBestHandStrength(cards(
            c("A", "H"), c("K", "D"), c("Q", "C"), c("J", "S"), c("10", "H"), c("3", "D"), c("2", "C")
        ));

        HandEvaluator.HandStrength second = evaluator.evaluateBestHandStrength(cards(
            c("A", "S"), c("K", "H"), c("Q", "D"), c("J", "C"), c("10", "S"), c("4", "H"), c("2", "D")
        ));

        require(first.getRank() == HandEvaluator.HandRank.STRAIGHT, "expected straight rank for first hand");
        require(second.getRank() == HandEvaluator.HandRank.STRAIGHT, "expected straight rank for second hand");
        require(first.compareTo(second) == 0, "equivalent best five should compare equal");
        require(first.equals(second), "equivalent rank and tie-break vector should be equal");
    }

    private static void shouldKeepEvaluateBestRankAsCompatibilityWrapper() {
        HandEvaluator evaluator = new HandEvaluator();
        ArrayList<Card> holeCards = cards(c("A", "H"), c("K", "D"));
        ArrayList<Card> communityCards = cards(c("Q", "C"), c("J", "S"), c("10", "H"), c("2", "D"), c("3", "C"));

        HandEvaluator.HandRank rank = evaluator.evaluateBestRank(holeCards, communityCards);
        HandEvaluator.HandStrength strength = evaluator.evaluateBestHandStrength(cards(
            c("A", "H"), c("K", "D"), c("Q", "C"), c("J", "S"), c("10", "H"), c("2", "D"), c("3", "C")
        ));

        require(rank == HandEvaluator.HandRank.STRAIGHT, "compatibility wrapper should return the same rank category");
        require(rank == strength.getRank(), "rank wrapper should match strength rank");
    }

    @SafeVarargs
    private static ArrayList<Card> cards(Card... values) {
        return new ArrayList<>(List.of(values));
    }

    private static Card c(String rank, String suit) {
        return new Card(rank, suit);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
