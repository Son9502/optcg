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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CardDatabaseTest {

    private static CardDatabase db;
    private static boolean hasData;

    @BeforeAll
    static void setUp() {
        db = new CardDatabase("src/main/resources/raw/data/");
        hasData = !db.getAllCards().isEmpty();
    }

    /** Skips the calling test if no card data has been loaded. */
    private static void requireData() {
        assumeTrue(hasData, "No card data in raw/data/ — run CardScraper first to populate.");
    }

    @Test
    void testDatabaseLoadsWithoutThrowing() {
        assertNotNull(db);
    }

    @Test
    void testKnownCardIsRetrievable() {
        requireData();
        CardData data = db.getCardData("OP01-001");
        assertNotNull(data);
        assertEquals("OP01-001", data.id());
    }

    @Test
    void testLeaderCardParsedCorrectly() {
        requireData();
        CardData data = db.getCardData("OP01-001");
        assertEquals(CardType.Leader, data.cardType());
        assertEquals(Color.Red, data.color());
        assertEquals(5000, data.power());
        assertEquals(5, data.life());
    }

    @Test
    void testCharacterCardParsedCorrectly() {
        requireData();
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
        requireData();
        CardData data = db.getCardData("OP01-001");
        Player p = new Player();
        Card card = CardFactory.createCard(data, p);
        assertInstanceOf(Leader.class, card);
        assertEquals(5, ((Leader) card).getLifePoints());
    }

    @Test
    void testCardFactoryCreatesPlainCard() {
        requireData();
        CardData data = db.getCardData("OP01-004");
        Player p = new Player();
        Card card = CardFactory.createCard(data, p);
        assertFalse(card instanceof Leader);
        assertEquals(3000, card.getBasePower());
    }

    // -------------------------------------------------------------------------
    // subTypes field
    // -------------------------------------------------------------------------

    @Test
    void testSubTypesFieldParsed() {
        requireData();
        // OP01-001 is Monkey.D.Luffy — sub_types should be "Straw Hat Crew"
        CardData data = db.getCardData("OP01-001");
        assertNotNull(data.subTypes());
        assertFalse(data.subTypes().isBlank());
    }

    // -------------------------------------------------------------------------
    // getByName
    // -------------------------------------------------------------------------

    @Test
    void testGetByName_returnsResults() {
        requireData();
        // "Monkey.D.Luffy" appears in many sets; should return at least one entry
        List<CardData> results = db.getByName("Monkey.D.Luffy");
        assertFalse(results.isEmpty());
        results.forEach(c -> assertEquals("Monkey.D.Luffy", c.name()));
    }

    @Test
    void testGetByName_caseInsensitive() {
        requireData();
        List<CardData> lower = db.getByName("monkey.d.luffy");
        List<CardData> exact = db.getByName("Monkey.D.Luffy");
        assertEquals(exact.size(), lower.size());
    }

    @Test
    void testGetByName_unknownNameReturnsEmpty() {
        assertTrue(db.getByName("FAKE_CARD_NAME_XYZ").isEmpty());
    }

    @Test
    void testGetByName_nullReturnsEmpty() {
        assertTrue(db.getByName(null).isEmpty());
    }

    // -------------------------------------------------------------------------
    // getBySubtype
    // -------------------------------------------------------------------------

    @Test
    void testGetBySubtype_returnsResults() {
        requireData();
        // "Straw Hat Crew" is one of the most common sub_types in the game
        List<CardData> results = db.getBySubtype("Straw Hat Crew");
        assertFalse(results.isEmpty());
        results.forEach(c -> assertTrue(c.subTypes().contains("Straw Hat Crew")));
    }

    @Test
    void testGetBySubtype_unknownSubtypeReturnsEmpty() {
        assertTrue(db.getBySubtype("FAKE_SUBTYPE_XYZ").isEmpty());
    }

    @Test
    void testGetBySubtype_nullReturnsEmpty() {
        assertTrue(db.getBySubtype(null).isEmpty());
    }

    // -------------------------------------------------------------------------
    // getAllCards
    // -------------------------------------------------------------------------

    @Test
    void testGetAllCards_nonEmpty() {
        requireData();
        assertFalse(db.getAllCards().isEmpty());
    }
}
