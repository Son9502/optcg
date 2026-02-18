package engine.ui.cli;

import engine.core.GameState;
import engine.core.TurnManager;
import engine.player.Player;
import engine.setup.GameSetup;
import engine.zones.Deck;
import engine.zones.Zone;

import java.util.ArrayList;
import java.util.List;

import engine.cards.Card;
import engine.cards.DonCard;
import engine.battle.BattleSystem;

public class CliController {
    private final GameState gameState;
    private final InputHandler inputHandler;
    private final BattleSystem battleSystem;
    private final TurnManager turnManager;

    public CliController(GameState gameState, TurnManager turnManager) {
        this.gameState = gameState;
        this.inputHandler = new InputHandler();
        this.battleSystem = new BattleSystem(gameState);
        this.turnManager = turnManager;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Player getOpponent(Player player) {
        return (player == gameState.getPlayer1()) ? gameState.getPlayer2() : gameState.getPlayer1();
    }

    /**
     * Displays a numbered list of cards and prompts the player to select one.
     * Entering 0 cancels the selection.
     *
     * @param cards  The list of cards to choose from.
     * @param prompt The prompt to display to the player.
     * @return The selected card, or null if the player cancelled.
     */
    private Card selectCard(List<Card> cards, String prompt) {
        if (cards.isEmpty()) {
            return null;
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            String name = (card.getData() != null) ? card.getData().name() : "Don Card";
            System.out.printf("%d. %s%n", i + 1, name);
        }
        System.out.println("0. Cancel");
        int choice = inputHandler.readInt(prompt, 0, cards.size());
        return (choice == 0) ? null : cards.get(choice - 1);
    }

    // -------------------------------------------------------------------------
    // Display methods
    // -------------------------------------------------------------------------

    /**
     * Prints a compact status header for both players showing life and Don counts.
     *
     * @param active   The active player.
     * @param opponent The opponent player.
     */
    public void printStatus(Player active, Player opponent) {
        System.out.println("=== " + active.getName() + " vs " + opponent.getName() + " ===");

        // Don counts derived from cost zone (activeDon/restedDon fields are not yet updated)
        long yourActiveDon = active.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard && !c.isRested()).count();
        long yourRestedDon = active.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard && c.isRested()).count();
        long oppActiveDon = opponent.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard && !c.isRested()).count();
        long oppRestedDon = opponent.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard && c.isRested()).count();

        // Stage cards
        String yourStage = active.getStage().isEmpty() ? "None"
                : active.getStage().getCards().get(0).getData().name();
        String oppStage = opponent.getStage().isEmpty() ? "None"
                : opponent.getStage().getCards().get(0).getData().name();

        // Top of trash (most recently added card — index 0 due to addFirst)
        String yourTrashTop = active.getTrash().isEmpty() ? "Empty"
                : active.getTrash().getCards().get(0).getData().name();
        String oppTrashTop = opponent.getTrash().isEmpty() ? "Empty"
                : opponent.getTrash().getCards().get(0).getData().name();

        System.out.printf("Your  | Life: %d | Don: %d active, %d rested | Stage: %s | Trash top: %s%n",
                active.getLife().size(), yourActiveDon, yourRestedDon, yourStage, yourTrashTop);
        System.out.printf("Opp   | Life: %d | Don: %d active, %d rested | Stage: %s | Trash top: %s%n",
                opponent.getLife().size(), oppActiveDon, oppRestedDon, oppStage, oppTrashTop);
    }

    /**
     * Prints the hand of the specified player.
     *
     * @param owner The player whose hand is being printed.
     */
    public void printHand(Player owner) {
        List<Card> cards = owner.getHand().getCards();
        System.out.println("Your Hand: ");
        for (int i = 0; i < cards.size(); i++) {
            System.out.printf("- %s%n", cards.get(i));
        }
    }

    /**
     * Prints the specified zone for the given player, showing all cards currently
     * in that zone.
     *
     * @param owner The player whose zone is being printed.
     * @param zone  The zone to be printed (e.g., hand, field, life, etc.).
     */
    public void printZone(Player owner, Zone zone) {
        List<Card> cards = zone.getCards();
        System.out.println(owner.getName() + "'s Zone: " + zone.getType());
        for (int i = 0; i < cards.size(); i++) {
            System.out.printf("- %s%n", cards.get(i));
        }
    }

    /**
     * Prints the deck size. Contents are not shown to preserve game integrity.
     *
     * @param title The title to display before the deck info.
     * @param deck  The deck to describe.
     */
    public void printDeck(String title, Deck deck) {
        System.out.println(title + ": " + deck.size() + " cards remaining");
    }

    /**
     * Prints the current state of the field for the specified player, showing all
     * characters and their statuses.
     *
     * @param player The player whose field is being printed.
     */
    public void printField(Player player) {
        StringBuilder fieldBuilder = new StringBuilder();
        fieldBuilder.append(player.getName()).append("'s Field:\n");
        fieldBuilder.append(String.format("%s\n", player.getLeader()));
        fieldBuilder.append("Characters:\n");
        for (Card card : player.getField().getCards()) {
            fieldBuilder.append(String.format("- %s\n", card));
        }
        System.out.println(fieldBuilder.toString());
    }

    // -------------------------------------------------------------------------
    // Phase logic
    // -------------------------------------------------------------------------

    /**
     * Runs the start phase for a player, allowing them to view their starting hand
     * and choose whether to take a mulligan.
     *
     * @param currentPlayer The player whose start phase is being executed.
     * @param gameSetup     The GameSetup instance to handle mulligan logic.
     */
    public void runStartPhase(Player currentPlayer, GameSetup gameSetup) {
        System.out.println("Here is your starting hand, " + currentPlayer.getName() + ":");
        printHand(currentPlayer);

        boolean mulligan = inputHandler.confirm("Do you want to take a mulligan?");
        if (mulligan) {
            gameSetup.mulligan(currentPlayer);
            System.out.println("Here is your new hand after mulligan, " + currentPlayer.getName() + ":");
            printHand(currentPlayer);
        }
    }

    /**
     * Runs the main phases for the active player, looping until the player chooses
     * to end their turn.
     *
     * @param currentPlayer The player whose main phases are being executed.
     */
    public void runMainPhases(Player currentPlayer) {
        Player opponent = getOpponent(currentPlayer);
        boolean finished = false;
        while (!finished) {
            printStatus(currentPlayer, opponent);
            System.out.println("\nCurrent Phase: " + turnManager.getCurrentPhase().getName());
            System.out.println("1. Attack");
            System.out.println("2. Reveal Hand");
            System.out.println("3. Reveal Your Field");
            System.out.println("4. Reveal Opponent's Field");
            System.out.println("5. Play Card");
            System.out.println("6. Attach Don");
            System.out.println("7. End Phase");
            int choice = inputHandler.readInt("Choose an action: ", 1, 7);
            switch (choice) {
                case 1:
                    battle(currentPlayer);
                    break;
                case 2:
                    printHand(currentPlayer);
                    break;
                case 3:
                    printField(currentPlayer);
                    break;
                case 4:
                    printField(opponent);
                    break;
                case 5:
                    addCardToField(currentPlayer);
                    break;
                case 6:
                    attachDon(currentPlayer);
                    break;
                case 7:
                    finished = true;
                    turnManager.advancePhase();
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    /**
     * Handles the battle phase logic: selects an attacker, selects a valid target
     * (opponent's leader or rested characters), optionally allows a counter, then
     * resolves the battle.
     *
     * @param currentPlayer The attacking player.
     */
    public void battle(Player currentPlayer) {
        Player opponent = getOpponent(currentPlayer);

        List<Card> attackers = battleSystem.getValidAttackers(currentPlayer);
        if (attackers.isEmpty()) {
            System.out.println("No available attackers.");
            return;
        }

        System.out.println("Select an attacker:");
        Card attacker = selectCard(attackers, "Attacker: ");
        if (attacker == null) return;

        List<Card> targets = battleSystem.getValidTargets(opponent);
        if (targets.isEmpty()) {
            System.out.println("No valid targets.");
            return;
        }

        System.out.println("Select a target:");
        Card target = selectCard(targets, "Target: ");
        if (target == null) return;

        System.out.println(attacker.getData().name() + " attacks " + target.getData().name() + "!");

        // Counter window for the defending player
        int counterBoost = 0;
        boolean playCounter = inputHandler.confirm(opponent.getName() + ", play a counter card?");
        if (playCounter) {
            List<Card> handCards = new ArrayList<>(opponent.getHand().getCards());
            if (handCards.isEmpty()) {
                System.out.println("No cards in hand to counter with.");
            } else {
                System.out.println("Select a counter card:");
                Card counterCard = selectCard(handCards, "Counter card: ");
                if (counterCard != null) {
                    counterBoost = battleSystem.applyCounter(opponent, counterCard, target);
                }
            }
        }

        battleSystem.resolve(attacker, target, counterBoost);
        System.out.println("Battle resolved.");
    }

    /**
     * Lets the current player select a card from their hand and play it to the
     * appropriate zone. Cost validation is skipped until payCost() is implemented.
     *
     * @param currentPlayer The player who is playing a card.
     */
    public void addCardToField(Player currentPlayer) {
        // Filter out Don cards — only playable card types
        List<Card> playable = new ArrayList<>();
        for (Card card : currentPlayer.getHand().getCards()) {
            if (!(card instanceof DonCard)) {
                playable.add(card);
            }
        }

        if (playable.isEmpty()) {
            System.out.println("No playable cards in hand.");
            return;
        }

        System.out.println("Select a card to play:");
        Card selected = selectCard(playable, "Card: ");
        if (selected == null) return;

        gameState.playCard(currentPlayer, selected);
        System.out.println("Played: " + selected.getData().name());
    }

    /**
     * Lets the current player attach a Don card from their cost zone to a character
     * on their field.
     *
     * @param currentPlayer The player attaching the Don.
     */
    public void attachDon(Player currentPlayer) {
        // Get non-rested Dons from cost zone
        List<Card> availableDons = new ArrayList<>();
        for (Card card : currentPlayer.getCost().getCards()) {
            if (card instanceof DonCard && !card.isRested()) {
                availableDons.add(card);
            }
        }

        if (availableDons.isEmpty()) {
            System.out.println("No available Don cards to attach.");
            return;
        }

        System.out.println("Select a Don to attach:");
        Card selectedDon = selectCard(availableDons, "Don: ");
        if (selectedDon == null) return;

        // Get characters on field
        List<Card> fieldCards = new ArrayList<>(currentPlayer.getField().getCards());
        if (fieldCards.isEmpty()) {
            System.out.println("No characters on the field to attach to.");
            return;
        }

        System.out.println("Select a character to attach the Don to:");
        Card target = selectCard(fieldCards, "Character: ");
        if (target == null) return;

        gameState.attachDon(target, (DonCard) selectedDon);
        System.out.println("Don attached to " + target.getData().name()
                + " (total power: " + target.getTotalPower() + ")");
    }
}
