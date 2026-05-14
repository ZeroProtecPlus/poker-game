package util;

import model.Card;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for serializing and deserializing Card objects to/from compact codes and JSON.
 *
 * Code format: rank + suit (e.g., "AS", "10H", "2D", "KC").
 */
public class CardSerializer {

    private CardSerializer() {
        // utility class
    }

    /**
     * Converts a card to a compact two-character code.
     *
     * @param card the card to encode
     * @return code such as "AS", "10H", "2D"
     */
    public static String toCode(Card card) {
        return card.getRank() + card.getSuit();
    }

    /**
     * Parses a compact code into a Card.
     *
     * @param code the code to parse
     * @return the corresponding Card
     * @throws IllegalArgumentException if the code is malformed or unsupported
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
     * Serializes a list of cards to a JSON array string.
     *
     * @param cards list of cards
     * @return JSON array of card codes
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
     * Deserializes a JSON array string into a list of cards.
     *
     * @param json JSON array of card codes
     * @return list of parsed cards
     * @throws IllegalArgumentException if JSON is malformed or contains invalid codes
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
