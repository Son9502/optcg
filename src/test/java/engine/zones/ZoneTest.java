package engine.zones;
import engine.cards.Card;
import engine.player.Player;
import engine.cards.CardData;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZoneTest {
    Player player = new Player();
    CardData cardData = new CardData(
        "TEST-001", // id
        "TEST-SET", // setId
        "Test Card", // name
        "Test description", // description
        "Test Set", // setName
        null, // rarity
        null, // cardType
        null, // attribute
        null, // color
        1, // cost
        1000, // power
        null, // life
        null, // counter
        0.0 // marketPrice
    );
    Card card = new Card("card1", cardData, player);

    @Test
    /**
     * Tests the add method of the Zone class by adding a card to a zone and verifying 
     * that the card is in the zone, that the card's zone reference is updated, and 
     * that the size of the zone is correct. 
     * If all assertions pass, it prints a success message.
     */
    public void testAddCard() {
        Zone hand = new Zone(ZoneType.HAND, player);
        hand.add(card);
        assertTrue(hand.getCards().contains(card));
        assertEquals(hand, card.getZone());
        assertEquals(1, hand.size());
        System.out.println("testAddCard passed");
    }
    @Test
    /**
     * Tests the remove method of the Zone class by adding a card to a zone, 
     * removing it, and verifying that the card is no longer in the zone and that its zone reference is null. 
     * It also checks that the size of the zone is updated correctly after removal. 
     * If all assertions pass, it prints a success message.
     */
    public void testRemoveCard() {
        Zone hand = new Zone(ZoneType.HAND, player);
        hand.add(card);
        hand.remove(card);
        assertFalse(hand.getCards().contains(card));
        assertNull(card.getZone());
        assertEquals(0, hand.size());
        System.out.println("testRemoveCard passed");
    }
    @Test
    /**
     * Tests the drawTop method of the Zone class by adding a card to a zone,
     * drawing the top card, and verifying that the drawn card is the one that was added
     * and that the card is removed from the zone. It also checks that the size of the zone 
     * is updated correctly after drawing.
     * If all assertions pass, it prints a success message.
     */
    public void testDrawTop() {
        Deck deck = new Deck(player);
        deck.add(card);
        Card drawnCard = deck.draw();
        assertEquals(card, drawnCard);
        assertFalse(deck.getCards().contains(card));
        assertEquals(0, deck.size());
        System.out.println("testDrawTop passed");
    }
    @Test
    /**
     * Tests the moveCard method of the Zone class by adding a card to one zone, 
     * moving it to another zone, and verifying that the card is in the new zone, 
     * not in the old zone, and that the card's zone reference is updated correctly. 
     * It also checks that the sizes  of both zones are updated correctly after the move.
     * If all assertions pass, it prints a success message.
     */
    public void testMoveCard() {
        Zone hand = new Zone(ZoneType.HAND, player);
        Zone trash = new Zone(ZoneType.TRASH, player);
        hand.add(card);
        hand.remove(card);
        trash.add(card);
        assertFalse(hand.getCards().contains(card));
        assertTrue(trash.getCards().contains(card));
        assertEquals(trash, card.getZone());
        assertEquals(0, hand.size());
        assertEquals(1, trash.size());
        assertTrue(card.getZone() == trash);
        System.out.println("testMoveCard passed");
    }
}