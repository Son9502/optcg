package engine.core;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.DonCard;
import engine.player.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class TurnManagerTest {
    @Test 
    public void testInitialPhase() {
        // Create a mock GameState for testing
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState mockGameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(mockGameState);
        // Test initial phase is DON
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
    }
    @Test 
    public void testAdvancePhase() {
        // Create a mock GameState and TurnManager for testing
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState mockGameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(mockGameState);

        // Test initial phase is DON
        assertEquals(Phase.DON, turnManager.getCurrentPhase());

        // Advance to MAIN phase
        turnManager.advancePhase();
        assertEquals(Phase.MAIN, turnManager.getCurrentPhase());

        // Advance to END phase
        turnManager.advancePhase();
        assertEquals(Phase.END, turnManager.getCurrentPhase());

        // Advance to next turn (should go back to REFRESH)
        turnManager.advancePhase();
        assertEquals(Phase.REFRESH, turnManager.getCurrentPhase());

        // Advance to DRAW phase
        turnManager.advancePhase();
        assertEquals(Phase.DRAW, turnManager.getCurrentPhase());

        // Advance to DON phase
        turnManager.advancePhase();
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
    }
    @Test
    public void testEndTurn() {
        // Create a mock GameState and TurnManager for testing
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState mockGameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(mockGameState);
        // Test initial active player is player1
        assertEquals(player1, turnManager.getActivePlayer());
        // Advance to END phase to trigger endTurn
        turnManager.advancePhase(); // DON
        turnManager.advancePhase(); // MAIN
        turnManager.advancePhase(); // END
        turnManager.advancePhase(); // REFRESH
        assertEquals(player2, turnManager.getActivePlayer());
    }

    @Test
    public void testTurnCount_incrementsAfterEndPhase() {
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState gameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(gameState);

        assertEquals(1, turnManager.getTurnCount());

        // Need a Don card in p1's Don deck so the DON phase doesn't fail silently
        player1.getDonDeck().add(new DonCard(player1));

        turnManager.advancePhase(); // DON → MAIN
        turnManager.advancePhase(); // MAIN → END
        turnManager.advancePhase(); // END → endTurn (REFRESH, count becomes 2)

        assertEquals(2, turnManager.getTurnCount());
        assertEquals(Phase.REFRESH, turnManager.getCurrentPhase());
        assertEquals(player2, turnManager.getActivePlayer());
    }

    @Test
    public void testFirstTurnDonDraws1() {
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState gameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(gameState);

        // Load 5 Don cards into p1's Don deck
        for (int i = 0; i < 5; i++) {
            player1.getDonDeck().add(new DonCard(player1));
        }

        // TurnManager starts at DON with firstTurn=true — advancing should draw exactly 1
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
        turnManager.advancePhase(); // DON → MAIN, draws 1 Don for p1

        assertEquals(Phase.MAIN, turnManager.getCurrentPhase());
        assertEquals(1, player1.getCost().size()); // Don cards go to the cost zone, not hand
    }

    @Test
    public void testSubsequentTurnDrawsTwoDon() {
        Player player1 = new Player("player_1", null);
        Player player2 = new Player("player_2", null);
        GameState gameState = new GameState(player1, player2);
        TurnManager turnManager = new TurnManager(gameState);

        // p1 needs 1 Don for the first-turn DON phase
        player1.getDonDeck().add(new DonCard(player1));

        // p2 needs a deck card for the DRAW phase in turn 2
        CardData deckData = new CardData("D1", "SET", "DeckCard", "", "", "Set",
                null, null, null, null, 0, 0, 0, null, 0.0);
        player2.getDeck().add(new Card("dc1", deckData, player2));

        // p2 needs Don cards for the turn-2 DON phase
        for (int i = 0; i < 5; i++) {
            player2.getDonDeck().add(new DonCard(player2));
        }

        // Complete turn 1: DON → MAIN → END → (endTurn: REFRESH, p2 active, firstTurn=false)
        turnManager.advancePhase(); // DON
        turnManager.advancePhase(); // MAIN → END
        turnManager.advancePhase(); // END → endTurn

        // Turn 2 for p2: REFRESH → DRAW → DON
        turnManager.advancePhase(); // REFRESH → DRAW
        turnManager.advancePhase(); // DRAW → DON (draws 1 card to p2.hand)

        assertEquals(Phase.DON, turnManager.getCurrentPhase());
        assertEquals(player2, turnManager.getActivePlayer());

        int donDeckBefore = player2.getDonDeck().size();
        turnManager.advancePhase(); // DON → MAIN (firstTurn=false → draws 2 Don for p2)

        assertEquals(donDeckBefore - 2, player2.getDonDeck().size());
    }
}
