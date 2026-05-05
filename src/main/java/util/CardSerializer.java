package util;

import model.Card;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad para serializar/deserializar cartas en formatos compactos y JSON.
 *
 * Formato de código: rank + suit (ej: "AS", "10H", "2D", "KC").
 */
public class CardSerializer {

    private CardSerializer() {
        // Clase utilitaria: no se instancia.
    }

    /**
     * Convierte una carta a un código compacto.
     */
    public static String toCode(Card card) {
        return card.getRank() + card.getSuit();
    }

    /**
     * Parsea un código compacto a Card.
     */
    public static Card fromCode(String code) {
        if (code == null || code.length() < 2) {
            throw new IllegalArgumentException("Invalid card code: " + code);
        }
        // El último carácter representa el palo; el resto, el rango.
        String suit = code.substring(code.length() - 1);
        String rank = code.substring(0, code.length() - 1);
        validateRank(rank);
        validateSuit(suit);
        return new Card(rank, suit);
    }

    /**
     * Serializa una lista de cartas a JSON.
     */
    public static String toJson(List<Card> cards) {
        // Serializa en JSON compacto para persistencia en SQLite.
        JSONArray array = new JSONArray();
        for (Card card : cards) {
            array.put(toCode(card));
        }
        return array.toString();
    }

    /**
     * Deserializa JSON a lista de cartas.
     */
    public static List<Card> fromJson(String json) {
        // Convierte JSON de códigos a objetos Card con validación estricta.
        JSONArray array = new JSONArray(json);
        List<Card> cards = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            cards.add(fromCode(array.getString(i)));
        }
        return cards;
    }

    private static void validateRank(String rank) {
        switch (rank) {
            case "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" -> {}
            default -> throw new IllegalArgumentException("Invalid card rank: " + rank);
        }
    }

    private static void validateSuit(String suit) {
        switch (suit) {
            case "♠", "♥", "♦", "♣" -> {}
            default -> throw new IllegalArgumentException("Invalid card suit: " + suit);
        }
    }
}
