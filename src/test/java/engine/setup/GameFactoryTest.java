package engine.setup;

import engine.cards.Card;
import engine.cards.types.CardType;
import engine.core.GameState;
import engine.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GameFactoryTest {

    private GameState gameState;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        gameState = GameFactory.createTestGame();
        p1 = gameState.getPlayer1();
        p2 = gameState.getPlayer2();
    }

    @Test
    void testBothDecksHave50Cards() {
        assertEquals(50, p1.getDeck().size(), "Player 1 deck should have 50 cards");
        assertEquals(50, p2.getDeck().size(), "Player 2 deck should have 50 cards");
    }

    @Test
    void testBothDonDecksHave10Cards() {
        assertEquals(10, p1.getDonDeck().size(), "Player 1 Don deck should have 10 cards");
        assertEquals(10, p2.getDonDeck().size(), "Player 2 Don deck should have 10 cards");
    }

    @Test
    void testLeadersAssignedWithCorrectStats() {
        assertNotNull(p1.getLeader(), "Player 1 should have a leader");
        assertNotNull(p2.getLeader(), "Player 2 should have a leader");
        assertEquals(5000, p1.getLeader().getBasePower());
        assertEquals(5000, p2.getLeader().getBasePower());
        assertEquals(5, p1.getLeader().getLifePoints());
        assertEquals(5, p2.getLeader().getLifePoints());
    }

    @Test
    void testDeckContainsCharacterCards() {
        List<Card> deck = p1.getDeck().getCards();
        long count = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Character)
                .count();
        assertTrue(count > 0, "Deck should contain at least one Character card");
    }

    @Test
    void testDeckContainsEventCards() {
        List<Card> deck = p1.getDeck().getCards();
        long count = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Event)
                .count();
        assertTrue(count > 0, "Deck should contain at least one Event card");
    }

    @Test
    void testDeckContainsStageCards() {
        List<Card> deck = p1.getDeck().getCards();
        long count = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Stage)
                .count();
        assertTrue(count > 0, "Deck should contain at least one Stage card");
    }

    @Test
    void testDeckCardTypeBreakdown() {
        List<Card> deck = p1.getDeck().getCards();
        long characters = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Character)
                .count();
        long events = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Event)
                .count();
        long stages = deck.stream()
                .filter(c -> c.getData() != null && c.getData().cardType() == CardType.Stage)
                .count();
        assertEquals(42, characters, "Deck should have 42 Character cards");
        assertEquals(5, events,      "Deck should have 5 Event cards");
        assertEquals(3, stages,      "Deck should have 3 Stage cards");
    }

    @Test
    void testAllDeckCardsOwnedByCorrectPlayer() {
        for (Card card : p1.getDeck().getCards()) {
            assertSame(p1, card.getOwner(), "All p1 deck cards should be owned by p1");
        }
        for (Card card : p2.getDeck().getCards()) {
            assertSame(p2, card.getOwner(), "All p2 deck cards should be owned by p2");
        }
    }

    @Test
    void testPlayersDoNotShareCards() {
        List<Card> p1Cards = p1.getDeck().getCards();
        List<Card> p2Cards = p2.getDeck().getCards();
        for (Card card : p1Cards) {
            assertFalse(p2Cards.contains(card), "p1 and p2 should not share card instances");
        }
    }
}
