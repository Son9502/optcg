package engine.ui.cli;
import java.util.Scanner;
public class InputHandler {
    private static final Scanner scanner = new Scanner(System.in);
    /**
     * Reads an integer input from the user, prompting them with the provided message.
     * @param prompt The message to display to the user when asking for input.
     * @param min The minimum acceptable integer value (inclusive).
     * @param max The maximum acceptable integer value (inclusive).
     * @return The integer value entered by the user. If the input is not a valid integer, 
     * it will prompt the user again until a valid integer is entered.
     */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
               int input = Integer.parseInt(scanner.nextLine());
               if (input >= min && input <= max) {
                   return input;
               } else {
                   System.out.println("Input must be between " + min + " and " + max + ".");
               }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid integer.");
            }
        }
    }
    /**
     * Reads a string input from the user, prompting them with the provided message.
     * @param prompt The message to display to the user when asking for input.
     * @return The string entered by the user.
     */
    public String readString(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }
    /**
     * Prompts the user for a yes/no confirmation.
     * @param prompt The message to display to the user when asking for confirmation.
     * @return true if the user confirms with 'y' or 'yes', false if the user responds with 'n' or 'no'.
     */
    public boolean confirm(String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            } else {
                System.out.println("Invalid input. Please enter 'y' or 'n'.");
            }
        }
    }
}
