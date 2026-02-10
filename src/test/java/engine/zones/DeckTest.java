package engine.zones;

import engine.cards.Card;
import engine.cards.CardData;
import engine.player.Player;    

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DeckTest {
    @Test
    /**
     * Tests the drawTop method of the Deck class by adding multiple cards to a deck,
     * drawing the top card, and verifying that the drawn card is correct and that the size
     * of the deck is updated correctly after drawing. If all assertions pass, it prints a success message.
     */
    public void drawMultipleCards() {
        Player player = new Player();
        Deck deck = new Deck(player);
        CardData cardData1 = new CardData(
            "TEST-001", "TEST-SET", "Test Card 1", "Test description 1", "Test Set",
            null, null, null, null, 1, 1000, null, null, 0.0
        );
        CardData cardData2 = new CardData(
            "TEST-002", "TEST-SET", "Test Card 2", "Test description 2", "Test Set",
            null, null, null, null, 2, 2000, null, null, 0.0
        );
        CardData cardData3 = new CardData(
            "TEST-003", "TEST-SET", "Test Card 3", "Test description 3", "Test Set",
            null, null, null, null, 3, 3000, null, null, 0.0
        );
        Card card1 = new Card("card1", cardData1, player);
        Card card2 = new Card("card2", cardData2, player);
        Card card3 = new Card("card3", cardData3, player);
        deck.add(card1);
        deck.add(card2);
        deck.add(card3);
        assertEquals(3, deck.size());
        Card drawnCard = deck.drawTop();
        assertEquals(card3, drawnCard);
        assertEquals(2, deck.size());
        System.out.println("drawMultipleCards passed");
    }
    @Test
    /**
     * Tests the search method of the Deck class by adding multiple cards to a deck,
     * searching for cards that match a specific condition, and verifying that the correct cards are returned
     *  and that the size of the deck remains unchanged after searching. If all assertions pass, it prints a success message.
     */
    public void searchCards() {
        Player player = new Player();
        Deck deck = new Deck(player);
        CardData cardData1 = new CardData(
            "TEST-001", "TEST-SET", "Test Card 1", "Test description 1", "Test Set",
            null, null, null, null, 1, 1000, null, null, 0.0
        );
        CardData cardData2 = new CardData(
            "TEST-002", "TEST-SET", "Test Card 2", "Test description 2", "Test Set",
            null, null, null, null, 2, 2000, null, null, 0.0
        );
        CardData cardData3 = new CardData(
            "TEST-003", "TEST-SET", "Test Card 3", "Test description 3", "Test Set",
            null, null, null, null, 3, 3000, null, null, 0.0
        );
        Card card1 = new Card("card1", cardData1, player);
        Card card2 = new Card("card2", cardData2, player);
        Card card3 = new Card("card3", cardData3, player);
        deck.add(card1);
        deck.add(card2);
        deck.add(card3);
        List<Card> searchResults = deck.search(card -> card.getData().cost() >= 2);
        assertTrue(searchResults.contains(card2));
        assertTrue(searchResults.contains(card3));
        assertFalse(searchResults.contains(card1));
        assertEquals(3, deck.size());
        System.out.println("searchCards passed");
    }
}
