package engine.history;

import engine.core.Phase;
import engine.player.Player;

/**
 * Manages the doubly-linked list of GameHistory nodes. Responsible for
 * appending new snapshots after each game action and traversing the list
 * for replay or debugging.
 *
 * CMU usage: call record() after each meaningful action (play card, attack,
 * end phase) to build a traversable history. Use printHistory() to dump the
 * full log, or step prev/next through nodes for replay.
 *
 * AI usage (future): record() will need to store deep-copied GameState
 * snapshots. A branch() method will be added to fork from any node for
 * tree search without affecting the live game state.
 */
public class GameHistoryManager {
    private GameHistory head;
    private GameHistory current;
    private int size;

    public GameHistoryManager() {
        this.head = null;
        this.current = null;
        this.size = 0;
    }

    /**
     * Records the current game state as a new node appended after the current
     * position. If called mid-history (after stepping back), any forward nodes
     * are discarded — the new record becomes the new tail.
     *
     * @param player1     Player 1.
     * @param player2     Player 2.
     * @param activePlayer The player whose turn it is.
     * @param phase       The current game phase.
     * @param turnCount   The current turn number.
     */
    public void record(Player player1, Player player2, Player activePlayer, Phase phase, int turnCount) {
        GameHistory node = new GameHistory(player1, player2, activePlayer, phase, turnCount);

        if (head == null) {
            head = node;
            current = node;
        } else {
            // Discard any forward history from current position
            current.setNextNode(node);
            node.setPrevNode(current);
            current = node;
        }
        size++;
    }

    /**
     * Steps backward one node in the history.
     *
     * @return The previous GameHistory node, or null if already at the start.
     */
    public GameHistory stepBack() {
        if (current == null || current.getPrevNode() == null) {
            return null;
        }
        current = current.getPrevNode();
        return current;
    }

    /**
     * Steps forward one node in the history.
     *
     * @return The next GameHistory node, or null if already at the most recent state.
     */
    public GameHistory stepForward() {
        if (current == null || current.getNextNode() == null) {
            return null;
        }
        current = current.getNextNode();
        return current;
    }

    /**
     * Returns the current node without moving.
     *
     * @return The current GameHistory node.
     */
    public GameHistory getCurrent() {
        return current;
    }

    /**
     * Returns the head (first recorded) node.
     *
     * @return The head GameHistory node.
     */
    public GameHistory getHead() {
        return head;
    }

    /**
     * Returns the total number of recorded states.
     *
     * @return The number of nodes in the history list.
     */
    public int size() {
        return size;
    }

    /**
     * Prints the full history from head to tail, one node per line.
     */
    public void printHistory() {
        GameHistory node = head;
        int index = 1;
        while (node != null) {
            String marker = (node == current) ? " <-- current" : "";
            System.out.printf("%d. %s%s%n", index, node.toString(), marker);
            node = node.getNextNode();
            index++;
        }
    }

    /**
     * Resets the history, clearing all recorded nodes.
     */
    public void clear() {
        head = null;
        current = null;
        size = 0;
    }
}
