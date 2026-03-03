package engine.core;

import engine.core.Phase;
import engine.history.GameHistoryManager;
import engine.setup.GameSetup;
import engine.ui.cli.CliController;

public class GameEngine {
    private final GameState gameState;
    private final TurnManager turnManager;
    private final GameSetup gameSetup;
    private final CliController cliController;
    private final GameHistoryManager historyManager;

    public GameEngine(GameState gameState) {
        this.gameState = gameState;
        this.gameSetup = new GameSetup(gameState);
        this.turnManager = new TurnManager(gameState);
        this.cliController = new CliController(gameState, turnManager);
        this.historyManager = new GameHistoryManager();
    }

    /**
     * Returns the history manager, allowing external callers (e.g. a debug menu)
     * to traverse or print recorded game states.
     */
    public GameHistoryManager getHistoryManager() {
        return historyManager;
    }

    /**
     * Prints a short feedback line after a non-interactive phase auto-resolves.
     * Only DRAW and DON phases produce visible card movements worth announcing;
     * REFRESH and END are silent here (END-of-turn abilities fire their own
     * messages via AbilityManager when that layer gains a logger).
     *
     * @param phase       the phase that just resolved
     * @param turnNumber  turn number at the time of resolution (used for DON count)
     */
    private void announcePhaseResult(Phase phase, int turnNumber) {
        switch (phase) {
            case DRAW -> cliController.announceDrawPhaseResult(turnManager.getActivePlayer());
            case DON  -> {
                // TurnManager gives 1 DON on the very first turn (turnNumber == 1),
                // and 2 DON on every subsequent turn — mirror that logic here.
                int donCount = (turnNumber == 1) ? 1 : 2;
                cliController.announceDonPhaseResult(turnManager.getActivePlayer(), donCount);
            }
            default   -> {} // REFRESH, END — no card-draw announcement needed
        }
    }

    /**
     * Records the current game state into history.
     */
    private void recordState() {
        historyManager.record(
                gameState.getPlayer1(),
                gameState.getPlayer2(),
                turnManager.getActivePlayer(),
                turnManager.getCurrentPhase(),
                turnManager.getTurnCount()
        );
    }

    /**
     * Initializes the game by setting up the game state, shuffling decks, drawing
     * starting hands, and allowing players to take mulligans if they choose.
     */
    public void start() {
        gameSetup.initializeGame();

        cliController.runStartPhase(gameState.getPlayer1(), gameSetup);
        cliController.runStartPhase(gameState.getPlayer2(), gameSetup);

        gameSetup.setUpLife(gameState.getPlayer1());
        gameSetup.setUpLife(gameState.getPlayer2());

        // Record the initial state after setup is complete
        recordState();
    }

    /**
     * Runs the main game loop, advancing through turns and phases until a win
     * condition is met.
     */
    public void run() {
        this.start();

        while (!gameState.isGameOver()) {
            Phase currentPhase = turnManager.getCurrentPhase();
            int currentTurn   = turnManager.getTurnCount();
            String playerName = turnManager.getActivePlayer().getName();

            cliController.announcePhase(currentTurn, playerName, currentPhase.getName());

            if (currentPhase.isInteractive()) {
                cliController.runMainPhases(turnManager.getActivePlayer());
            } else {
                turnManager.advancePhase();
                announcePhaseResult(currentPhase, currentTurn);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Record state after every phase advance
            recordState();
        }

        cliController.announceGameOver(gameState.getWinner().getName());
    }
}
