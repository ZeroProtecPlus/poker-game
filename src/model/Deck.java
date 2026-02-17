package model;

import java.util.ArrayList;
import java.util.Collections;

public class Deck {
    private final ArrayList<Card> cards = new ArrayList<>();
    private final String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private final String[] suits = {"♠", "♥", "♦", "♣"};

    public Deck() {
        initialize();
    }

    private void initialize() {
        cards.clear();
        for (String suit : suits) {
            for (String rank : ranks) {
                cards.add(new Card(rank, suit));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    public Card dealCard() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("No hay más cartas en el mazo");
        }
    // ArrayList no tiene removeFirst(); usar índice 0
    return cards.remove(0);
    }
}
