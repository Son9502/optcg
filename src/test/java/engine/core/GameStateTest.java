package engine.core;

import engine.cards.CardData;
import engine.cards.DonCard;
import engine.cards.types.CardType;
import engine.player.Player;
import engine.cards.Card;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GameStateTest {
    /**
     * Test that drawing a card moves it from the deck to the hand
     */
    void testdrawCard() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        // Setup initial conditions (e.g., add cards to the deck)
        // Call the method to draw a card
        // Assert that the card is now in the hand and removed from the deck
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
        Card card = new engine.cards.Card("card1", cardData, p1);
        p1.getDeck().add(card);
        assertEquals(0, p1.getHand().size());
        gameState.draw(p1);
        assertTrue(p1.getHand().contains(card));
        assertFalse(p1.getDeck().contains(card));
    }
    
    @Test
    /**
     * Test that drawing a Don card moves it from the Don deck to the hand
     */
    void testdrawDon() {
        DonCard donCard = new DonCard("don1", null, null);
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        p1.getDonDeck().add(donCard);
        assertEquals(0, p1.getHand().size());
        gameState.drawDon(p1, 1);
        assertTrue(p1.getHand().contains(donCard));
        assertFalse(p1.getDonDeck().contains(donCard));
    }

    @Test
    /**
     * Test that attempting to draw a card from an empty deck results in the correct
     * game state change
     * (e.g., player loses the game, takes damage, etc.)
     */
    void testdrawEmpty() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        assertEquals(0, p1.getDeck().size());
        gameState.draw(p1);
        // Assert the expected outcome (e.g., player loses, takes damage)
        assertTrue(gameState.isGameOver());
        assertEquals(p2, gameState.getWinner());
        gameState.drawDon(p1, 0);
    }

    @Test
    /**
     * Test that playing a card moves it from the hand to the field and updates the
     * game state accordingly
     * (e.g., reduces resources, triggers effects)
     */
    void testPlayCard() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        CardData cardDataOne = new CardData(
                "TEST-001", // id
                "TEST-SET", // setId
                "Test Card", // name
                "Test description", // description
                "Test Set", // setName
                null, // rarity
                CardType.Character, // cardType
                null, // attribute
                null, // color
                1, // cost
                1000, // power
                null, // life
                null, // counter
                0.0 // marketPrice
        );
        CardData cardDataTwo = new CardData(
                "TEST-002", // id
                "TEST-SET", // setId
                "Test Card", // name
                "Test description", // description
                "Test Set", // setName
                null, // rarity
                CardType.Event, // cardType
                null, // attribute
                null, // color
                1, // cost
                1000, // power
                null, // life
                null, // counter
                0.0 // marketPrice
        );
        CardData cardDataThree = new CardData(
                "TEST-003", // id
                "TEST-SET", // setId
                "Test Card", // name
                "Test description", // description
                "Test Set", // setName
                null, // rarity
                CardType.Stage, // cardType
                null, // attribute
                null, // color
                1, // cost
                1000, // power
                null, // life
                null, // counter
                0.0 // marketPrice
        );
        Card card = new engine.cards.Card("card1", cardDataOne, p1);
        Card card2 = new engine.cards.Card("card2", cardDataTwo, p1);
        Card card3 = new engine.cards.Card("card3", cardDataThree, p1);
        p1.getHand().add(card);
        p1.getHand().add(card2);
        p1.getHand().add(card3);
        assertTrue(p1.getHand().contains(card));
        assertTrue(p1.getHand().contains(card2));
        assertTrue(p1.getHand().contains(card3));
        gameState.playCard(p1, card);
        assertTrue(p1.getField().contains(card));
        assertFalse(p1.getHand().contains(card));
        gameState.playCard(p1, card2);
        assertTrue(p1.getTrash().contains(card2));
        assertFalse(p1.getHand().contains(card2));
        gameState.playCard(p1, card3);
        assertTrue(p1.getStage().contains(card3));
        assertFalse(p1.getHand().contains(card3));
    }

    @Test
    /**
     * Test adding a life to the player increases their life total correctly and
     * handles edge cases (e.g., exceeding maximum life)
     */
    void testaddLife() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        int initialLife = p1.getLife().size();
        CardData cardData = new CardData(
                "TEST-001", // id
                "TEST-SET", // setId
                "Test", // name
                "Test description", // description
                "Test Set", // setName
                null, // rarity
                null, // cardType
                null, // attribute
                null, // color
                0, // cost
                0, // power
                5, // life
                null, // counter
                0.0 // marketPrice
        );
        Card lifeCard = new Card("lifeCard", cardData, p1, p1.getHand());
        gameState.addLife(p1, lifeCard);
        assertEquals(initialLife + 1, p1.getLife().size());
        assertTrue(lifeCard.getZone() == p1.getLife());
        assertTrue(p1.getLife().contains(lifeCard));
        assertFalse(p1.getHand().contains(lifeCard));
    }

    @Test
    /**
     * Test that removing life points decreases the player's life total correctly
     * and handles edge cases (e.g., going to zero or negative)
     */
    void testremoveLife() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
        CardData cardData = new CardData(
                "TEST-001", // id
                "TEST-SET", // setId
                "Test", // name
                "Test description", // description
                "Test Set", // setName
                null, // rarity
                null, // cardType
                null, // attribute
                null, // color
                0, // cost
                0, // power
                5, // life
                null, // counter
                0.0 // marketPrice
        );
        for (int i = 0; i < 5; i++) {
            Card lifeCard = new Card("lifeCard" + i, cardData, p1, p1.getHand());
            gameState.addLife(p1, lifeCard);
        }
        assertEquals(5, p1.getLife().size());
        for (int i = 0; i < 5; i++) {
            Card lifeCard = p1.getLife().getCards().getLast();
            gameState.removeLife(p1);
            assertEquals(5 - (i + 1), p1.getLife().size());
            assertTrue(p1.getHand().contains(lifeCard));
            assertFalse(p1.getLife().contains(lifeCard));
        }
        assertEquals(0, p1.getLife().size());
        gameState.removeLife(p1);
        assertTrue(gameState.isGameOver());
        assertEquals(p2, gameState.getWinner());
    }

    @Test
    /**
     * Test that attaching a Don card to a card updates the game state correctly and
     * that detaching it reverses those changes
     */
    void testAttachandDetachDon() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
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
        Card card = new engine.cards.Card("card1", cardData, p1);
        DonCard donCard = new DonCard("don1", null, p1);
        p1.getField().add(card);
        p1.getHand().add(donCard);
        gameState.attachDon(card, donCard);
        assertTrue(card.getAttachedDons().contains(donCard));
        gameState.detachDon(card);
        assertFalse(card.getAttachedDons().contains(donCard));
    }

    @Test
    /**
     * Test that refreshing the game state at the end of a turn correctly resets the
     * state of cards (e.g., untapping, refreshing abilities) and updates any
     * relevant game variables
     */
    void testRefreshBehavior() {
        // Player p1 = new Player();
        // Player p2 = new Player();
        // GameState gameState = new GameState(p1, p2);
        // // Setup initial conditions (e.g., tap some cards, use abilities)
        // // Call the refresh method
        // // Assert that the cards are untapped and abilities are refreshed


    }

    @Test
    /**
     * Test that trashing a card moves it to the correct zone and updates the game
     * state accordingly (e.g., triggers effects, updates resources)
     */
    void testTrashCard() {
        Player p1 = new Player();
        Player p2 = new Player();
        GameState gameState = new GameState(p1, p2);
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
        Card card = new engine.cards.Card("card1", cardData, p1);
        p1.getField().add(card);
        assertTrue(p1.getField().contains(card));
        gameState.trash(p1, card);
        assertFalse(p1.getField().contains(card));
        assertTrue(p1.getTrash().contains(card));
    }

    // Future tests to consider:
    @Test
    void testCostReduction() {

    }
}
