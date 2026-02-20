package engine.data;

import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.CardFactory;
import engine.cards.Leader;
import engine.cards.types.CardType;
import engine.cards.types.Color;
import engine.player.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardDatabaseTest {

    private static CardDatabase db;

    @BeforeAll
    static void setUp() {
        db = new CardDatabase("src/main/resources/raw/data/");
    }

    @Test
    void testDatabaseLoadsWithoutThrowing() {
        assertNotNull(db);
    }

    @Test
    void testKnownCardIsRetrievable() {
        CardData data = db.getCardData("OP01-001");
        assertNotNull(data);
        assertEquals("OP01-001", data.id());
    }

    @Test
    void testLeaderCardParsedCorrectly() {
        CardData data = db.getCardData("OP01-001");
        assertEquals(CardType.Leader, data.cardType());
        assertEquals(Color.Red, data.color());
        assertEquals(5000, data.power());
        assertEquals(5, data.life());
    }

    @Test
    void testCharacterCardParsedCorrectly() {
        // OP01-004 is a cost-2, 3000-power Red Character from Romance Dawn
        CardData data = db.getCardData("OP01-004");
        assertEquals(CardType.Character, data.cardType());
        assertEquals(Color.Red, data.color());
        assertEquals(3000, data.power());
        assertEquals(2, data.cost());
    }

    @Test
    void testUnknownCardThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> db.getCardData("FAKE-999"));
    }

    @Test
    void testCardFactoryCreatesLeader() {
        CardData data = db.getCardData("OP01-001");
        Player p = new Player();
        Card card = CardFactory.createCard(data, p);
        assertInstanceOf(Leader.class, card);
        assertEquals(5, ((Leader) card).getLifePoints());
    }

    @Test
    void testCardFactoryCreatesPlainCard() {
        CardData data = db.getCardData("OP01-004");
        Player p = new Player();
        Card card = CardFactory.createCard(data, p);
        assertFalse(card instanceof Leader);
        assertEquals(3000, card.getBasePower());
    }
}
