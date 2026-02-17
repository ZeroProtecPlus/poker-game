package model;

import java.util.*;

public class PokerGame {
    private final Deck deck;
    private final ArrayList<Card> playerHand = new ArrayList<>();
    private final ArrayList<Card> communityCards = new ArrayList<>();

    public PokerGame(User player) {
        this.deck = new Deck();
    }

    public void startNewRound() {
        playerHand.clear();
        communityCards.clear();
        deck.shuffle();
        dealPlayerCards();
    }

    private void dealPlayerCards() {
        playerHand.add(deck.dealCard());
        playerHand.add(deck.dealCard());
    }

    public void dealFlop() {
        for (int i = 0; i < 3; i++) {
            communityCards.add(deck.dealCard());
        }
    }

    public void dealTurnOrRiver() {
        communityCards.add(deck.dealCard());
    }

    public ArrayList<Card> getPlayerHand() {
        return new ArrayList<>(playerHand);
    }

    public ArrayList<Card> getCommunityCards() {
        return new ArrayList<>(communityCards);
    }

    public <T> boolean tieneRepetidos(List<T> lista, int cantidadObjetivo) {
        Map<T, Integer> conteos = new HashMap<>();

        for (T elemento : lista) {
            conteos.put(elemento, conteos.getOrDefault(elemento, 0) + 1);
        }

        for (int cantidad : conteos.values()) {
            if (cantidad == cantidadObjetivo) {
                return true;
            }
        }
        return false;
    }

    // Evalúa si hay n del mismo rango (por ejemplo trío=3) en una lista de cartas
    public boolean hasNKindByRank(List<Card> cards, int n) {
        Map<String, Integer> counts = new HashMap<>();
        for (Card c : cards) {
            counts.put(c.getRank(), counts.getOrDefault(c.getRank(), 0) + 1);
        }
        for (int cnt : counts.values()) {
            if (cnt >= n) return true;
        }
        return false;
    }

    // Evalúa si en las cartas comunitarias hay un trío (por rango)
    public boolean communityHasTrips() {
        return hasNKindByRank(communityCards, 3);
    }
}
