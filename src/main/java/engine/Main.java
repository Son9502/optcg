package engine;

import engine.core.GameEngine;
import engine.core.GameState;
import engine.setup.GameFactory;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== One Piece TCG ===");
        System.out.println("Select a deck:");
        for (int i = 0; i < GameFactory.DECK_NAMES.length; i++) {
            System.out.println("  " + GameFactory.DECK_NAMES[i]); // names already carry their number
        }

        int p1 = readChoice(sc, "Player 1 deck (1-12): ", 12);
        int p2 = readChoice(sc, "Player 2 deck (1-12): ", 12);

        GameState gameState = GameFactory.createGame(p1 - 1, p2 - 1);
        GameEngine engine = new GameEngine(gameState);
        engine.run();
    }

    private static int readChoice(Scanner sc, String prompt, int max) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            try {
                int n = Integer.parseInt(line);
                if (n >= 1 && n <= max) return n;
            } catch (NumberFormatException ignored) {}
            System.out.println("Please enter a number between 1 and " + max + ".");
        }
    }
}
