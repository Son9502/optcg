package engine.setup;

import engine.TestUtils;
import engine.cards.CardData;
import engine.cards.Leader;
import engine.core.GameState;
import engine.player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameSetupTest {

    private Player player1;
    private Player player2;
    private GameState gameState;
    private GameSetup gameSetup;

    @BeforeEach
    void setUp() {
        player1 = new Player("player_1");
        player2 = new Player("player_2");
        gameState = new GameState(player1, player2);
        gameSetup = new GameSetup(gameState);
    }

    private Leader makeLeader(Player owner) {
        CardData leaderData = new CardData("L-001", "SET", "Leader", "", "Set",
                null, null, null, null, 0, 5000, 4, null, 0.0);
        return new Leader("l1", leaderData, owner);
    }

    private void populateDeck(Player player, int count) {
        for (int i = 0; i < count; i++) {
            player.getDeck().add(TestUtils.makeCard(player, i * 100));
        }
    }

    @Test
    void testGameSetup() {
        populateDeck(player1, 50);
        populateDeck(player2, 50);

        gameSetup.initializeGame();

        assertEquals(5, player1.getHand().size());
        assertEquals(5, player2.getHand().size());
        assertEquals(45, player1.getDeck().size());
        assertEquals(45, player2.getDeck().size());
    }

    @Test
    void textMulligan() {
        populateDeck(player1, 50);

        // Simulate dealing a starting hand
        player1.getHand().add(player1.getDeck().draw(5));

        assertEquals(5, player1.getHand().size());
        assertEquals(45, player1.getDeck().size());

        gameSetup.mulligan(player1);

        // After mulligan: hand has 5 new cards, deck has 45 remaining
        // (45 + 5 returned − 5 redrawn = 45)
        assertEquals(5, player1.getHand().size());
        assertEquals(45, player1.getDeck().size());
    }

    @Test
    void testSetUpLife() {
        player1.setLeader(makeLeader(player1));
        populateDeck(player1, 10);

        assertTrue(player1.getLife().isEmpty());

        gameSetup.setUpLife(player1);

        // Leader has 4 life points → 4 cards drawn from deck into life zone
        assertEquals(4, player1.getLife().size());
        assertEquals(6, player1.getDeck().size()); // 10 − 4
    }

    @Test
    void testSetUpLife_partialDeck() {
        player1.setLeader(makeLeader(player1));
        populateDeck(player1, 2); // only 2 cards, leader wants 4

        // Should draw however many are available without throwing
        gameSetup.setUpLife(player1);

        assertEquals(2, player1.getLife().size());
        assertTrue(player1.getDeck().isEmpty());

    }
}
