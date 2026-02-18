package engine.core;

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
            System.out.println("Turn " + turnManager.getTurnCount() + " - "
                    + turnManager.getActivePlayer().getName() + "'s " + currentPhase.getName() + " Phase");

            if (currentPhase.isInteractive()) {
                cliController.runMainPhases(turnManager.getActivePlayer());
            } else {
                turnManager.advancePhase();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Record state after every phase advance
            recordState();
        }

        System.out.println("Game Over! Winner: " + gameState.getWinner().getName());
    }
}
