package engine.battle;
import engine.player.Player;
/**
 * GameHistory is a class that represents a node in a linked list of game states. 
 * Each node contains a snapshot of the game state at a particular point in time, 
 * including the state of both players. The linked list allows us to traverse 
 * through the history of the game, enabling features like undoing moves or analyzing past states.
 * @param player1 The first player in the game. This player is typically the one who goes first.
 * @param player2 The second player in the game. This player is typically the one who goes second.
 * @param previousState  A reference to the previous game state in the history. This allows us to traverse back 
 * through the history of the game.
 */
public class GameHistory{
    private Player player1;
    private Player player2;
    private GameHistory nextNode;
    private GameHistory prevNode;
    public GameHistory(Player player1, Player player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.nextNode = null;
        this.prevNode = null;
    }
    public GameHistory(Player player1, Player player2, GameHistory previousState) {
        this.player1 = player1;
        this.player2 = player2;
        this.nextNode = null;
        this.prevNode = previousState;
    }
}
