package engine.cards;

import engine.TestUtils;
import engine.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardTest {

    @Test
    void testCardCreation() {
        Player player = new Player();
        CardData cardData = TestUtils.makeCardData(1000);
        Card card = new Card("card1", cardData, player);
        assertEquals("card1", card.getCardId());
        assertEquals(cardData, card.getData());
        assertEquals(player, card.getOwner());
        assertNull(card.getZone());
    }

    @Test
    void testRestAndActivate() {
        Card card = new Card("card1", TestUtils.makeCardData(1000), null);
        card.rest();
        assertTrue(card.isRested());
        card.activate();
        assertFalse(card.isRested());
    }

    @Test
    void testDonCardWorks() {
        Player player = new Player();
        Card card = new Card("card1", TestUtils.makeCardData(500), player);
        DonCard donCard = new DonCard(player);
        card.attachDonCard(donCard);
        assertEquals(1500, card.getTotalPower()); // 500 base + 1000 Don boost
        assertEquals(0, card.countRestedDon());
        donCard.rest();
        assertEquals(1, card.countRestedDon());
    }

    @Test
    void attachAndDetachDon() {
        Player player = new Player();
        Card card = new Card("card1", TestUtils.makeCardData(500), player);
        DonCard donCard = new DonCard(player);
        card.attachDonCard(donCard);
        assertEquals(1500, card.getTotalPower());
        assertEquals(1, card.getAttachedDons().size());
        card.detachDonCard();
        assertEquals(500, card.getTotalPower());
        assertTrue(card.getAttachedDons().isEmpty());
    }
}
