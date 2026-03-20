package engine.cards;

import engine.TestUtils;
import engine.cards.keywords.Keyword;
import engine.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player();
    }

    @Test
    void testCardCreation() {
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
        Card card = new Card("card1", TestUtils.makeCardData(500), player);
        DonCard donCard = new DonCard(player);
        card.attachDonCard(donCard);
        assertEquals(1500, card.getTotalPower());
        assertEquals(1, card.getAttachedDons().size());
        card.detachDonCard();
        assertEquals(500, card.getTotalPower());
        assertTrue(card.getAttachedDons().isEmpty());
    }
    @Test
    void testGrantedKeyword() {
        Card card = new Card("card1", TestUtils.makeCardData(1000), player);
        assertFalse(card.hasKeyword(Keyword.RUSH));
        card.grantKeyword(Keyword.RUSH);
        assertTrue(card.hasKeyword(Keyword.RUSH));
    }

    @Test
    void testClearGrantedKeywords() {
        Card card = new Card("card1", TestUtils.makeCardData(1000), player);
        card.grantKeyword(Keyword.RUSH);
        card.grantKeyword(Keyword.BLOCKER);
        assertTrue(card.hasKeyword(Keyword.RUSH));
        assertTrue(card.hasKeyword(Keyword.BLOCKER));
        card.clearGrantedKeywords();
        assertFalse(card.hasKeyword(Keyword.RUSH));
        assertFalse(card.hasKeyword(Keyword.BLOCKER));
    }

    @Test
    void testhasKeyword() {
        Card card = TestUtils.makeCardWithKeyword(player, 1000, Keyword.RUSH);
        assertTrue(card.hasKeyword(Keyword.RUSH));
        card.clearGrantedKeywords();
        assertTrue(card.hasKeyword(Keyword.RUSH));
        
    }
}
