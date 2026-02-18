package engine.setup;

import java.util.ArrayList;
import java.util.List;
import engine.cards.Card;
import engine.core.GameState;
import engine.player.Player;

public class GameSetup {
    Player player1;
    Player player2;
    GameState gameState;
    public GameSetup(GameState gameState) {
        this.player1 = gameState.getPlayer1();
        this.player2 = gameState.getPlayer2();
        this.gameState = gameState;
    }

    /**
     * Performs a mulligan for the given player.
     * 
     * @param player The player who is taking a mulligan.
     */
    public void mulligan(Player player) {
        // Implement mulligan logic (e.g., allow player to shuffle hand back into deck
        // and redraw)
        List<Card> handCards = new ArrayList<>(player.getHand().getCards());

        player.getDeck().add(handCards);
        player.getHand().clear();

        // Shuffle the deck after returning cards
        player.getDeck().shuffle();

        // Draw new hand (e.g., 5 cards)
        player.getHand().add(player.getDeck().draw(5));
    }

    /**
     * Sets up the life zone for the given player based on their leader card.
     * 
     * @param player The player whose life zone is being set up.
     */
    public void setUpLife(Player player) {
        // Set up life points based on leader card
        int lifePoints = player.getLeader().getLifePoints();
        List<Card> lifeCards = player.getDeck().draw(lifePoints);
        player.getLife().add(lifeCards);
    }

    /**
     * Initializes the game by setting up decks, hands, and other components for
     * both players.
     */
    public void initializeGame() {
        // Initialize decks, hands, and other game components

        // Shuffle decks
        gameState.shuffle(player1);
        gameState.shuffle(player2);

        // Draw starting hands
        gameState.draw(player1, 5);
        gameState.draw(player2, 5);

    }
}
