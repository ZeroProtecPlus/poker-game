package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HandEvaluator {

    public enum HandRank {
        HIGH_CARD("Carta Alta"),
        ONE_PAIR("Par"),
        TWO_PAIR("Doble Par"),
        THREE_OF_A_KIND("Trío"),
        STRAIGHT("Escalera"),
        FLUSH("Color"),
        FULL_HOUSE("Full House"),
        FOUR_OF_A_KIND("Póker"),
        STRAIGHT_FLUSH("Escalera de Color"),
        ROYAL_FLUSH("Escalera Real");

        public final String spanishName;

        HandRank(String spanishName) {
            this.spanishName = spanishName;
        }
    }

    private static final Map<String, Integer> RANK_VALUE = new LinkedHashMap<>();

    static {
        RANK_VALUE.put("2", 2);
        RANK_VALUE.put("3", 3);
        RANK_VALUE.put("4", 4);
        RANK_VALUE.put("5", 5);
        RANK_VALUE.put("6", 6);
        RANK_VALUE.put("7", 7);
        RANK_VALUE.put("8", 8);
        RANK_VALUE.put("9", 9);
        RANK_VALUE.put("10", 10);
        RANK_VALUE.put("J", 11);
        RANK_VALUE.put("Q", 12);
        RANK_VALUE.put("K", 13);
        RANK_VALUE.put("A", 14);
    }

    public HandRank evaluateBestRank(List<Card> holeCards, List<Card> communityCards) {
        validateInput(holeCards, communityCards);

        ArrayList<Card> allCards = new ArrayList<>(holeCards);
        allCards.addAll(communityCards);
        return evaluateCards(allCards);
    }

    private void validateInput(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || communityCards == null) {
            throw new IllegalArgumentException("Hand evaluation requires non-null hole and community cards");
        }

        if (holeCards.size() != 2) {
            throw new IllegalArgumentException("Hand evaluation requires exactly 2 hole cards");
        }

        if (communityCards.size() > 5) {
            throw new IllegalArgumentException("Hand evaluation supports at most 5 community cards");
        }

        int totalCards = holeCards.size() + communityCards.size();
        if (totalCards < 5 || totalCards > 7) {
            throw new IllegalArgumentException("Hand evaluation requires a total of 5 to 7 cards");
        }
    }

    private HandRank evaluateCards(List<Card> cards) {
        List<List<Card>> combos = combinations(new ArrayList<>(cards), 5);
        HandRank best = HandRank.HIGH_CARD;
        for (List<Card> combo : combos) {
            HandRank rank = evaluateFive(combo);
            if (rank.ordinal() > best.ordinal()) {
                best = rank;
            }
        }
        return best;
    }

    private HandRank evaluateFive(List<Card> five) {
        boolean flush = isFlush(five);
        boolean straight = isStraight(five);
        if (flush && straight) {
            List<Integer> values = sortedValues(five);
            return (values.get(4) == 14 && values.get(0) == 10)
                ? HandRank.ROYAL_FLUSH
                : HandRank.STRAIGHT_FLUSH;
        }
        if (hasNOfAKind(five, 4)) {
            return HandRank.FOUR_OF_A_KIND;
        }
        if (isFullHouse(five)) {
            return HandRank.FULL_HOUSE;
        }
        if (flush) {
            return HandRank.FLUSH;
        }
        if (straight) {
            return HandRank.STRAIGHT;
        }
        if (hasNOfAKind(five, 3)) {
            return HandRank.THREE_OF_A_KIND;
        }
        if (isTwoPair(five)) {
            return HandRank.TWO_PAIR;
        }
        if (hasNOfAKind(five, 2)) {
            return HandRank.ONE_PAIR;
        }
        return HandRank.HIGH_CARD;
    }

    private boolean isFlush(List<Card> cards) {
        String suit = cards.get(0).getSuit();
        return cards.stream().allMatch(card -> card.getSuit().equals(suit));
    }

    private boolean isStraight(List<Card> cards) {
        List<Integer> values = sortedValues(cards);
        if (values.equals(Arrays.asList(2, 3, 4, 5, 14))) {
            return true;
        }
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) != values.get(i - 1) + 1) {
                return false;
            }
        }
        return true;
    }

    private boolean hasNOfAKind(List<Card> cards, int n) {
        return rankCounts(cards).containsValue(n);
    }

    private boolean isFullHouse(List<Card> cards) {
        Map<String, Integer> counts = rankCounts(cards);
        return counts.containsValue(3) && counts.containsValue(2);
    }

    private boolean isTwoPair(List<Card> cards) {
        return rankCounts(cards).values().stream().filter(value -> value == 2).count() == 2;
    }

    private Map<String, Integer> rankCounts(List<Card> cards) {
        Map<String, Integer> counts = new HashMap<>();
        for (Card card : cards) {
            counts.put(card.getRank(), counts.getOrDefault(card.getRank(), 0) + 1);
        }
        return counts;
    }

    private List<Integer> sortedValues(List<Card> cards) {
        List<Integer> values = new ArrayList<>();
        for (Card card : cards) {
            Integer value = RANK_VALUE.get(card.getRank());
            if (value == null) {
                throw new IllegalArgumentException("Unsupported card rank: " + card.getRank());
            }
            values.add(value);
        }
        Collections.sort(values);
        return values;
    }

    private <T> List<List<T>> combinations(List<T> list, int k) {
        List<List<T>> result = new ArrayList<>();
        combinationsHelper(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private <T> void combinationsHelper(List<T> list, int k, int start, List<T> current, List<List<T>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            combinationsHelper(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
