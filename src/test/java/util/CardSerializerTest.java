package util;

import model.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CardSerializer.
 */
class CardSerializerTest {

    @Test
    void shouldEncodeAll52Cards() {
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        String[] suits = {"♠", "♥", "♦", "♣"};

        for (String rank : ranks) {
            for (String suit : suits) {
                Card card = new Card(rank, suit);
                String code = CardSerializer.toCode(card);
                assertEquals(rank + suit, code, "encoding should match rank + suit");
            }
        }
    }

    @Test
    void shouldDecodeAll52Cards() {
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        String[] suits = {"♠", "♥", "♦", "♣"};

        for (String rank : ranks) {
            for (String suit : suits) {
                String code = rank + suit;
                Card card = CardSerializer.fromCode(code);
                assertEquals(rank, card.getRank(), "decoded rank should match");
                assertEquals(suit, card.getSuit(), "decoded suit should match");
            }
        }
    }

    @Test
    void shouldRoundTripThroughJson() {
        List<Card> original = List.of(
            new Card("A", "♠"),
            new Card("10", "♥"),
            new Card("2", "♦"),
            new Card("K", "♣")
        );

        String json = CardSerializer.toJson(original);
        List<Card> restored = CardSerializer.fromJson(json);

        assertEquals(original.size(), restored.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i), restored.get(i), "card at index " + i + " should match");
        }
    }

    @Test
    void shouldHandleEmptyListToJson() {
        String json = CardSerializer.toJson(new ArrayList<>());
        assertEquals("[]", json);

        List<Card> restored = CardSerializer.fromJson(json);
        assertTrue(restored.isEmpty(), "restored list should be empty");
    }

    @Test
    void shouldRejectInvalidCode() {
        assertThrows(IllegalArgumentException.class, () -> CardSerializer.fromCode("X♠"));
        assertThrows(IllegalArgumentException.class, () -> CardSerializer.fromCode("A?"));
        assertThrows(IllegalArgumentException.class, () -> CardSerializer.fromCode(""));
        assertThrows(IllegalArgumentException.class, () -> CardSerializer.fromCode(null));
    }
}
