package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test utilities for poker game tests.
 * Provides helpers for card creation and assertions.
 */
public class TestUtils {

    /**
     * Creates a Card from rank and suit strings.
     * rank: "2"-"10", "J", "Q", "K", "A"
     * suit: "S" (Spades), "H" (Hearts), "D" (Diamonds), "C" (Clubs)
     */
    public static Card card(String rank, String suit) {
        return new Card(rank, suit);
    }

    /**
     * Creates an ArrayList of Cards from varargs.
     */
    @SafeVarargs
    public static ArrayList<Card> cards(Card... cards) {
        return new ArrayList<>(Arrays.asList(cards));
    }

    /**
     * Creates an ArrayList of Cards from a List.
     */
    public static ArrayList<Card> cards(List<Card> cardList) {
        return new ArrayList<>(cardList);
    }

    /**
     * Asserts that two HandStrength objects are equal.
     * Throws AssertionError if they differ.
     */
    public static void assertHandEquals(HandStrength expected, HandStrength actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            throw new AssertionError("Expected: " + expected + " but was: " + actual);
        }
        if (expected.getRank() != actual.getRank()) {
            throw new AssertionError("Expected rank: " + expected.getRank() + " but was: " + actual.getRank());
        }
        if (!expected.getTieBreakValues().equals(actual.getTieBreakValues())) {
            throw new AssertionError("Expected tie-break values: " + expected.getTieBreakValues() + 
                " but was: " + actual.getTieBreakValues());
        }
    }

    /**
     * Asserts that the HandStrength has the expected rank.
     * Throws AssertionError if ranks differ.
     */
    public static void assertRank(HandRank expected, HandStrength actual) {
        if (expected == null) {
            throw new IllegalArgumentException("expected rank cannot be null");
        }
        if (actual == null) {
            throw new AssertionError("Expected rank: " + expected + " but actual was null");
        }
        if (expected != actual.getRank()) {
            throw new AssertionError("Expected rank: " + expected + " but was: " + actual.getRank());
        }
    }

    /**
     * Asserts that the HandStrength has the expected rank and tie-break values.
     * Throws AssertionError if they differ.
     */
    public static void assertHandEquals(HandRank expectedRank, List<Integer> expectedTieBreak, HandStrength actual) {
        assertRank(expectedRank, actual);
        if (!expectedTieBreak.equals(actual.getTieBreakValues())) {
            throw new AssertionError("Expected tie-break values: " + expectedTieBreak + 
                " but was: " + actual.getTieBreakValues());
        }
    }

    /**
     * Helper method to combine hole cards and community cards into a single list.
     */
    @SafeVarargs
    public static ArrayList<Card> allCards(List<Card> holeCards, List<Card>... communityCards) {
        ArrayList<Card> result = new ArrayList<>(holeCards);
        for (List<Card> community : communityCards) {
            result.addAll(community);
        }
        return result;
    }
}