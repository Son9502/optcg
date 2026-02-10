package engine.cards;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import engine.player.Player;
import engine.zones.Zone;
import engine.zones.ZoneType;
public class CardTest {
    @Test
    /**
     * Tests the creation of a Card object by verifying that its properties are set correctly.
     * It checks the card's ID, data, owner, and initial zone (which should
     * be null). If all assertions pass, it prints a success message.
     */
    void testCardCreation() {
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
        assertEquals("card1", card.getCardId());
        assertEquals(cardData, card.getData());
        assertEquals(player, card.getOwner());
        assertNull(card.getZone());
    }
    @Test
    /**
     * Tests the rest and activate methods of the Card class by creating a card, activating it to rest it,
     * and then resting it again to verify that the card remains rested. It checks the card's rested state
     * after each operation.
     */
    void testRestAndActivate(){
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
        Card card = new Card("card1", cardData, null);
        card.activate();
        assertTrue(card.isRested());
        card.rest();    
        assertTrue(card.isRested());
    }
    @Test
    /**
     * Tests the functionality of Don cards by creating a Card and a DonCard, attaching the DonCard to the Card,
     * and verifying that the total power of the Card is increased by the DonCard's boost
     * value. It also checks the count of rested Don cards before and after resting the DonCard 
     * to ensure that the count is updated correctly.
     */
    void testDonCardWorks(){
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
            500, // power
            null, // life
            null, // counter
            0.0 // marketPrice
        );
        Card card = new Card("card1", cardData, player);
        DonCard donCard = new DonCard(player);
        card.attachDonCard(donCard);
        assertEquals(1500, card.getTotalPower());
        assertEquals(0, card.countRestedDon());
        donCard.rest();
        assertEquals(1, card.countRestedDon());
    }
    @Test
    /**
     * 
     *  */ 
    void attachAndDetachDon(){}
    


}
