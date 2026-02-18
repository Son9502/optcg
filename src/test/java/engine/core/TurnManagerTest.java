package engine.core;
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
}
