package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HandEvaluator {

    public static final class HandStrength implements Comparable<HandStrength> {
        private final HandRank rank;
        private final List<Integer> tieBreakValues;

        public HandStrength(HandRank rank, List<Integer> tieBreakValues) {
            this.rank = Objects.requireNonNull(rank, "rank cannot be null");
            this.tieBreakValues = List.copyOf(Objects.requireNonNull(tieBreakValues, "tieBreakValues cannot be null"));
        }

        public HandRank getRank() {
            return rank;
        }

        public List<Integer> getTieBreakValues() {
            return tieBreakValues;
        }

        @Override
        public int compareTo(HandStrength other) {
            if (other == null) {
                return 1;
            }

            int byRank = Integer.compare(rank.ordinal(), other.rank.ordinal());
            if (byRank != 0) {
                return byRank;
            }

            int max = Math.max(tieBreakValues.size(), other.tieBreakValues.size());
            for (int i = 0; i < max; i++) {
                int left = i < tieBreakValues.size() ? tieBreakValues.get(i) : 0;
                int right = i < other.tieBreakValues.size() ? other.tieBreakValues.get(i) : 0;
                if (left != right) {
                    return Integer.compare(left, right);
                }
            }
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof HandStrength other)) {
                return false;
            }
            return rank == other.rank && tieBreakValues.equals(other.tieBreakValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rank, tieBreakValues);
        }
    }

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
        return evaluateBestHandStrength(allCards).getRank();
    }

    public HandStrength evaluateBestHandStrength(List<Card> cards) {
        validateAllCardsInput(cards);

        List<List<Card>> combos = combinations(new ArrayList<>(cards), 5);
        HandStrength best = null;
        for (List<Card> combo : combos) {
            HandStrength current = evaluateFiveStrength(combo);
            if (best == null || current.compareTo(best) > 0) {
                best = current;
            }
        }
        return best;
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

    private void validateAllCardsInput(List<Card> cards) {
        if (cards == null) {
            throw new IllegalArgumentException("Hand strength evaluation requires non-null cards");
        }

        if (cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("Hand strength evaluation requires 5 to 7 cards");
        }
    }

    private HandStrength evaluateFiveStrength(List<Card> five) {
        List<Integer> valuesAscending = sortedValues(five);
        List<Integer> valuesDescending = new ArrayList<>(valuesAscending);
        Collections.reverse(valuesDescending);
        Map<Integer, Integer> counts = valueCounts(valuesAscending);

        boolean flush = isFlush(five);
        int straightHigh = straightHigh(valuesAscending);

        if (flush && straightHigh != -1) {
            if (straightHigh == 14 && valuesAscending.get(0) == 10) {
                return new HandStrength(HandRank.ROYAL_FLUSH, List.of(14));
            }
            return new HandStrength(HandRank.STRAIGHT_FLUSH, List.of(straightHigh));
        }

        Integer fourValue = findValueWithCount(counts, 4);
        if (fourValue != null) {
            return new HandStrength(HandRank.FOUR_OF_A_KIND, List.of(fourValue, highestExcluding(valuesDescending, fourValue)));
        }

        Integer threeValue = findValueWithCount(counts, 3);
        Integer pairForHouse = findValueWithCount(counts, 2);
        if (threeValue != null && pairForHouse != null) {
            return new HandStrength(HandRank.FULL_HOUSE, List.of(threeValue, pairForHouse));
        }

        if (flush) {
            return new HandStrength(HandRank.FLUSH, valuesDescending);
        }

        if (straightHigh != -1) {
            return new HandStrength(HandRank.STRAIGHT, List.of(straightHigh));
        }

        if (threeValue != null) {
            List<Integer> kickers = descendingValuesExcluding(valuesDescending, List.of(threeValue));
            return new HandStrength(HandRank.THREE_OF_A_KIND, prepend(threeValue, kickers));
        }

        List<Integer> pairs = valuesWithCountDescending(counts, 2);
        if (pairs.size() >= 2) {
            int highPair = pairs.get(0);
            int lowPair = pairs.get(1);
            int kicker = highestExcluding(valuesDescending, highPair, lowPair);
            return new HandStrength(HandRank.TWO_PAIR, List.of(highPair, lowPair, kicker));
        }

        if (pairs.size() == 1) {
            int pairValue = pairs.get(0);
            List<Integer> kickers = descendingValuesExcluding(valuesDescending, List.of(pairValue));
            return new HandStrength(HandRank.ONE_PAIR, prepend(pairValue, kickers));
        }

        return new HandStrength(HandRank.HIGH_CARD, valuesDescending);
    }

    private boolean isFlush(List<Card> cards) {
        String suit = cards.get(0).getSuit();
        return cards.stream().allMatch(card -> card.getSuit().equals(suit));
    }

    private int straightHigh(List<Integer> values) {
        if (values.equals(Arrays.asList(2, 3, 4, 5, 14))) {
            return 5;
        }
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) != values.get(i - 1) + 1) {
                return -1;
            }
        }
        return values.get(values.size() - 1);
    }

    private Map<Integer, Integer> valueCounts(List<Integer> values) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer value : values) {
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
        return counts;
    }

    private Integer findValueWithCount(Map<Integer, Integer> counts, int expectedCount) {
        Integer best = null;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == expectedCount) {
                if (best == null || entry.getKey() > best) {
                    best = entry.getKey();
                }
            }
        }
        return best;
    }

    private List<Integer> valuesWithCountDescending(Map<Integer, Integer> counts, int expectedCount) {
        List<Integer> values = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == expectedCount) {
                values.add(entry.getKey());
            }
        }
        values.sort(Collections.reverseOrder());
        return values;
    }

    private int highestExcluding(List<Integer> valuesDescending, Integer... excludedValues) {
        return highestExcluding(valuesDescending, Arrays.asList(excludedValues));
    }

    private int highestExcluding(List<Integer> valuesDescending, List<Integer> excludedValues) {
        for (Integer value : valuesDescending) {
            if (!excludedValues.contains(value)) {
                return value;
            }
        }
        throw new IllegalStateException("Expected at least one kicker value");
    }

    private List<Integer> descendingValuesExcluding(List<Integer> valuesDescending, List<Integer> excludedValues) {
        List<Integer> values = new ArrayList<>();
        for (Integer value : valuesDescending) {
            if (!excludedValues.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<Integer> prepend(int first, List<Integer> tail) {
        List<Integer> result = new ArrayList<>();
        result.add(first);
        result.addAll(tail);
        return result;
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
