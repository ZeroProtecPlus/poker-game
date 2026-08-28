package model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

public class Deck {

    private final ArrayDeque<Card> cards = new ArrayDeque<>();

    private final String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    private final String[] suits = {"♠", "♥", "♦", "♣"};

    public Deck() {
        initialize();
    }

    private void initialize() {
        cards.clear();

        // Construimos el mazo en una lista temporal para poder barajarlo
        ArrayList<Card> temp = new ArrayList<>();
        for (String suit : suits) {
            for (String rank : ranks) {
                temp.add(new Card(rank, suit));
            }
        }

        // Se baraja al crearse, como en la vida real
        Collections.shuffle(temp);

        // Cargamos al deque (tope = primera carta a repartir)
        cards.addAll(temp);
    }

    /**
     * Baraja de nuevo si se quiere reiniciar el mazo manualmente.
     * Reconstruye e inicializa desde cero.
     */
    public void shuffle() {
        initialize();
    }

    /**
     * Saca la carta del tope del mazo (como un dealer real).
     */
    public Card dealCard() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("No hay más cartas en el mazo");
        }
        return cards.removeFirst();
    }

    /**
     * Devuelve cuántas cartas quedan en el mazo.
     * Útil para la UI (ej: reducir visualmente la pila).
     */
    public int getRemainingCards() {
        return cards.size();
    }

    /**
     * Devuelve una copia de las cartas restantes en el mazo.
     * Útil para serializar el estado del juego.
     */
    public ArrayList<Card> getRemainingCardsList() {
        return new ArrayList<>(cards);
    }
}
