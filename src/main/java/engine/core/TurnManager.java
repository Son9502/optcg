package engine.core;
import engine.cards.abilities.Trigger;
import engine.player.Player;
public class TurnManager {
    private final GameState state;
    private Player activePlayer;
    private Phase currentPhase;

    private int turnCount = 1;
    private boolean firstTurn = true;
    public TurnManager(GameState state) {
        this.state = state;
        this.activePlayer = state.getPlayer1(); // Player 1 starts first
        this.currentPhase = Phase.DON; // Start with the DON phase for the first player on the first turn
    }
    public Player getActivePlayer() {
        return activePlayer;
    }
    public Phase getCurrentPhase() {
        return currentPhase;
    }
    public int getTurnCount() {
        return turnCount;
    }
    /**
     * Advances the game to the next phase. This method will handle the logic for transitioning between phases,
     * including any actions that need to be taken at the start of each phase (e.g., drawing cards, refreshing characters, etc.).
     */
    public void advancePhase(){
        switch (currentPhase) {
            case REFRESH:
                // Implement REFRESH phase logic (e.g., refresh characters, reset abilities, etc.)
                state.refreshLeader(activePlayer);
                state.refreshField(activePlayer);
                state.refreshDon(activePlayer);
                currentPhase = Phase.DRAW;
                break;
            case DRAW:
                state.draw(activePlayer, 1); 
                currentPhase = Phase.DON;
                break;
            case DON:
                int donCount = firstTurn ? 1 : 2; // First turn, draw 1 DON card; subsequent turns, draw 2 DON cards
                state.drawDon(activePlayer, donCount);
                currentPhase = Phase.MAIN;
                break;
            case MAIN:
                // Implement MAIN phase logic (e.g., allow player to play character cards, use abilities, etc.)
                currentPhase = Phase.END;
                break;
            case END:
                AbilityManager am = state.getAbilityManager();
                if (am != null) {
                    Player opponent = (activePlayer == state.getPlayer1())
                            ? state.getPlayer2() : state.getPlayer1();
                    am.dispatchAll(Trigger.END_OF_YOUR_TURN,      activePlayer, activePlayer);
                    am.dispatchAll(Trigger.END_OF_OPPONENTS_TURN, opponent,     activePlayer);
                }
                endTurn();
                break;
        }
    }
    private void endTurn() {
        firstTurn = false;
        // Clear OPPONENTS_NEXT_TURN buckets on the non-active player's cards.
        // "Opponent's next turn" for the non-active player = the turn just ending.
        Player other = (activePlayer == state.getPlayer1()) ? state.getPlayer2() : state.getPlayer1();
        other.getField().getCards().forEach(c -> {
            c.clearBonusPowerOpponentTurn();
            c.clearBonusCostOpponentTurn();
        });
        if (other.getLeader() != null) {
            other.getLeader().clearBonusPowerOpponentTurn();
            other.getLeader().clearBonusCostOpponentTurn();
        }
        activePlayer = other;
        turnCount++;
        currentPhase = Phase.REFRESH;
    }

}
