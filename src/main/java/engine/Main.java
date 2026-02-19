package engine;

import engine.core.GameEngine;
import engine.core.GameState;
import engine.setup.GameFactory;

public class Main {
    public static void main(String[] args) {
        GameState gameState = GameFactory.createTestGame();
        GameEngine engine = new GameEngine(gameState);
        engine.run();
    }
}
