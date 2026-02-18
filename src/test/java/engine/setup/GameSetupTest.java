package engine.setup;
import org.junit.jupiter.api.Test;

import engine.core.GameState;
import engine.player.Player;

import static org.junit.jupiter.api.Assertions.*;
public class GameSetupTest {
    @Test
    void testGameSetup() {
        // Placeholder for game setup test logic
        Player player1 = new Player("player_1");
        Player player2 = new Player("player_2");
        GameState gameState = new GameState(player1, player2);
        GameSetup gameSetup = new GameSetup(gameState);
        // Initialize the game and verify initial conditions
        
        gameSetup.initializeGame();
        assertTrue(player1.getHand().size() == 5);
        assertTrue(player2.getHand().size() == 5);
    }
    @Test
    void textMulligan() {
        // Placeholder for mulligan test logic
        
    }
}
