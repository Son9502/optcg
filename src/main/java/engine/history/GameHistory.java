package engine.history;

import engine.core.Phase;
import engine.player.Player;

/**
 * Represents a single node in the game history linked list. Each node stores a
 * shallow snapshot of the game state at a particular point — the two players
 * (and by extension their zones and cards via live references), whose turn it
 * is, the current phase, and the turn count.
 *
 * NOTE: Player references are live objects, not deep copies. This is sufficient
 * for CMU-phase replay and debugging. When AI simulation is added, this class
 * will need to store deep-copied snapshots produced by GameState.snapshot().
 */
public class GameHistory {
    private final Player player1;
    private final Player player2;
    private final Player activePlayer;
    private final Phase phase;
    private final int turnCount;

    private GameHistory nextNode;
    private GameHistory prevNode;

    public GameHistory(Player player1, Player player2, Player activePlayer, Phase phase, int turnCount) {
        this.player1 = player1;
        this.player2 = player2;
        this.activePlayer = activePlayer;
        this.phase = phase;
        this.turnCount = turnCount;
        this.nextNode = null;
        this.prevNode = null;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public Player getActivePlayer() {
        return activePlayer;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public GameHistory getNextNode() {
        return nextNode;
    }

    public GameHistory getPrevNode() {
        return prevNode;
    }

    // -------------------------------------------------------------------------
    // Package-private mutators (used only by GameHistoryManager)
    // -------------------------------------------------------------------------

    void setNextNode(GameHistory next) {
        this.nextNode = next;
    }

    void setPrevNode(GameHistory prev) {
        this.prevNode = prev;
    }

    @Override
    public String toString() {
        return String.format("Turn %d | %s's %s phase", turnCount, activePlayer.getName(), phase.getName());
    }
}
