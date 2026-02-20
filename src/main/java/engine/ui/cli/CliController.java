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
import engine.cards.Leader;
import engine.cards.types.Color;
import engine.battle.BattleSystem;

public class CliController {
    private final GameState gameState;
    private final InputHandler inputHandler;
    private final BattleSystem battleSystem;
    private final TurnManager turnManager;

    // ANSI escape codes for colored text output
    // Will be used to show cost and color of cards in the CLI for better
    // readability
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String WHITE  = "\u001B[37m";
    public static final String BOLD   = "\u001B[1m";

    public CliController(GameState gameState, TurnManager turnManager) {
        this.gameState = gameState;
        this.inputHandler = new InputHandler();
        this.turnManager = turnManager;
        this.battleSystem = new BattleSystem(gameState, turnManager);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Player getOpponent(Player player) {
        return (player == gameState.getPlayer1()) ? gameState.getPlayer2() : gameState.getPlayer1();
    }

    /**
     * Wraps text in the ANSI color matching the given card color.
     * Returns plain text if color is null.
     *
     * NOTE: dual-color cards (e.g. Red/Yellow leaders) require CardData to expose
     * a second Color field. When that is added, this method should accept a List<Color>
     * and alternate the ANSI codes character-by-character across the name string.
     */
    private String colorize(String text, Color color) {
        if (color == null) return text;
        switch (color) {
            case Red:    return RED    + text + RESET;
            case Green:  return GREEN  + text + RESET;
            case Blue:   return BLUE   + text + RESET;
            case Yellow: return YELLOW + text + RESET;
            case Purple: return PURPLE + text + RESET;
            case Black:  return BLACK  + text + RESET;
            default:     return text;
        }
    }

    /**
     * Wraps text in bold + the ANSI color matching the given card color.
     * Always appends RESET so surrounding text is unaffected.
     * Safe when color is null — produces bold plain text.
     */
    private String boldColorize(String text, Color color) {
        String code = BOLD;
        if (color != null) {
            switch (color) {
                case Red:    code += RED;    break;
                case Green:  code += GREEN;  break;
                case Blue:   code += BLUE;   break;
                case Yellow: code += YELLOW; break;
                case Purple: code += PURPLE; break;
                case Black:  code += BLACK;  break;
            }
        }
        return code + text + RESET;
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
            String name;
            if (card instanceof DonCard) {
                String raw = (card.getData() != null) ? card.getData().name() : "DON!!";
                name = WHITE + raw + RESET;
            } else if (card.getData() != null) {
                name = colorize(card.getData().name(), card.getData().color());
            } else {
                name = "Unknown Card";
            }
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
        Color activeColor   = (active.getLeader()   != null) ? active.getLeader().getData().color()   : null;
        Color opponentColor = (opponent.getLeader()  != null) ? opponent.getLeader().getData().color() : null;
        System.out.println("=== " + boldColorize(active.getName(), activeColor)
                + " vs " + boldColorize(opponent.getName(), opponentColor) + " ===");

        // Don counts derived from cost zone (activeDon/restedDon fields are not yet
        // updated)
        long yourActiveDon = active.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && !d.isRested() && !d.isAttached()).count();
        long yourAttachedDon = active.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && d.isAttached()).count();
        long yourRestedDon = active.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && d.isRested()).count();
        long oppActiveDon = opponent.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && !d.isRested() && !d.isAttached()).count();
        long oppAttachedDon = opponent.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && d.isAttached()).count();
        long oppRestedDon = opponent.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard d && d.isRested()).count();

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

        System.out.printf("Your  | Life: %d | Don: %d active, %d attached, %d rested | Stage: %s | Trash top: %s%n",
                active.getLife().size(), yourActiveDon, yourAttachedDon, yourRestedDon, yourStage, yourTrashTop);
        System.out.printf("Opp   | Life: %d | Don: %d active, %d attached, %d rested | Stage: %s | Trash top: %s%n",
                opponent.getLife().size(), oppActiveDon, oppAttachedDon, oppRestedDon, oppStage, oppTrashTop);
    }

    /**
     * Prints the hand of the specified player.
     *
     * @param owner The player whose hand is being printed.
     */
    public void printHand(Player owner) {
        List<Card> cards = owner.getHand().getCards();
        System.out.println("--- " + owner.getName() + "'s Hand (" + cards.size() + " cards) ---");
        if (cards.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (card.getData() == null) {
                System.out.printf("%d. Unknown Card%n", i + 1);
                continue;
            }
            String name = boldColorize(card.getData().name(), card.getData().color());
            String type = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
            System.out.printf("%d. %s%s  |  Cost: %d, Power: %d%n",
                    i + 1, name, type, card.getData().cost(), card.getData().power());
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
        System.out.println("--- " + player.getName() + "'s Field ---");

        // Leader
        Leader leader = player.getLeader();
        if (leader != null && leader.getData() != null) {
            String name   = boldColorize(leader.getData().name(), leader.getData().color());
            String rested = leader.isRested() ? "Rested" : "Active";
            String dons   = leader.countDon() > 0 ? " | Dons: " + leader.countDon() : "";
            int power = (turnManager.getActivePlayer() == player) ? leader.getTotalPower() : leader.getBasePower();
            System.out.printf("  %s [Leader]  |  Power: %d | Life: %d | %s%s%n",
                    name, power, leader.getLifePoints(), rested, dons);
        }

        // Characters
        List<Card> fieldCards = player.getField().getCards();
        System.out.println("  Characters (" + fieldCards.size() + "/5):");
        if (fieldCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (Card card : fieldCards) {
                if (card.getData() == null) { System.out.println("    - Unknown Card"); continue; }
                String name   = boldColorize(card.getData().name(), card.getData().color());
                String type   = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? card.getTotalPower() : card.getBasePower();
                String rested = card.isRested() ? "Rested" : "Active";
                String dons   = card.countDon() > 0 ? " | Dons: " + card.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
            }
        }
        // Stage 
        List<Card> stageCards = player.getStage().getCards();
        System.out.println("  Stage (" + stageCards.size() + "/1):");
        if (stageCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (Card card : stageCards) {
                if (card.getData() == null) { System.out.println("    - Unknown Card"); continue; }
                String name   = boldColorize(card.getData().name(), card.getData().color());
                String type   = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? card.getTotalPower() : card.getBasePower();
                String rested = card.isRested() ? "Rested" : "Active";
                String dons   = card.countDon() > 0 ? " | Dons: " + card.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
            }
        }
        // Trash (top card only)
        List<Card> trashCards = player.getTrash().getCards();
        System.out.println("  Trash (" + trashCards.size() + " cards, top):");
        if (trashCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            Card topCard = trashCards.get(0); // Zone.add() uses addFirst → index 0 is top
            if (topCard.getData() == null) {
                System.out.println("    - Unknown Card");
            } else {
                String name   = boldColorize(topCard.getData().name(), topCard.getData().color());
                String type   = (topCard.getData().cardType() != null) ? " [" + topCard.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? topCard.getTotalPower() : topCard.getBasePower();
                String rested = topCard.isRested() ? "Rested" : "Active";
                String dons   = topCard.countDon() > 0 ? " | Dons: " + topCard.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
            }
        }
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
            int mod = (turnManager.getTurnCount() < 3) ? 0 : 1; // Restrict certain actions on the first turn
            if (turnManager.getTurnCount() >= 3) {
                System.out.printf("%d. Attack\n", mod);
            }
            System.out.printf("%d. Reveal Hand\n", mod + 1);
            System.out.printf("%d. Reveal Your Field\n", mod + 2);
            System.out.printf("%d. Reveal Opponent's Field\n", mod + 3);
            System.out.printf("%d. Play Card\n", mod + 4);
            System.out.printf("%d. Attach Don\n", mod + 5);
            System.out.printf("%d. End Phase\n", mod + 6);
            int choice = inputHandler.readInt("Choose an action: ", 1, 7 - (mod == 0 ? 1 : 0));
            switch (choice + (mod == 0 ? 1 : 0)) {
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
        if (turnManager.getTurnCount() < 3) {
            System.out.println("Attacking is not allowed on the first turn.");
            return;
        }
        Player opponent = getOpponent(currentPlayer);

        List<Card> attackers = battleSystem.getValidAttackers(currentPlayer);
        if (attackers.isEmpty()) {
            System.out.println("No available attackers.");
            return;
        }

        System.out.println("Select an attacker:");
        Card attacker = selectCard(attackers, "Attacker: ");
        if (attacker == null)
            return;

        List<Card> targets = battleSystem.getValidTargets(opponent);
        if (targets.isEmpty()) {
            System.out.println("No valid targets.");
            return;
        }

        System.out.println("Select a target:");
        Card target = selectCard(targets, "Target: ");
        if (target == null)
            return;

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

        long availableDon = currentPlayer.getCost().getCards().stream()
                .filter(c -> c instanceof DonCard && !c.isRested()).count();

        System.out.println("Select a card to play:");
        for (int i = 0; i < playable.size(); i++) {
            Card card = playable.get(i);
            String name = colorize(card.getData().name(), card.getData().color());
            int cost  = card.getData().cost();
            int power = card.getData().power();
            String stats = (availableDon >= cost)
                    ? GREEN + "(Type: " + card.getData().cardType() + ", Cost: " + cost + ", Power: " + power + ")" + RESET
                    : RED   + "(Type: " + card.getData().cardType() + ", Cost: " + cost + ", Power: " + power + ")" + RESET;
            System.out.printf("%d. %s %s%n", i + 1, name, stats);
        }
        System.out.println("0. Cancel");
        int choice = inputHandler.readInt("Card: ", 0, playable.size());
        if (choice == 0) return;

        Card selected = playable.get(choice - 1);
        gameState.playCard(currentPlayer, selected);
        System.out.println("Played: " + colorize(selected.getData().name(), selected.getData().color()));
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
            if (card instanceof DonCard && !card.isRested() && !((DonCard) card).isAttached()) {
                availableDons.add(card);
            }
        }

        if (availableDons.isEmpty()) {
            System.out.println("No available Don cards to attach.");
            return;
        }
        // For simplicity, auto-select the first available Don
        DonCard selectedDon = (DonCard) availableDons.get(0); 
        
        if (selectedDon == null)
            return;

        // Get characters on field (and Leader if not rested) to attach Don to
        List<Card> fieldCards = new ArrayList<>(currentPlayer.getField().getCards());
        if (currentPlayer.getLeader() != null && !currentPlayer.getLeader().isRested()) {
            fieldCards.add(currentPlayer.getLeader());
        }
        if (fieldCards.isEmpty()) {
            System.out.println("No characters to attach to.");
            return;
        }

        System.out.println("Select a character to attach the Don to:");
        Card target = selectCard(fieldCards, "Character: ");
        if (target == null)
            return;

        gameState.attachDon(target, selectedDon);
        System.out.println("Don attached to " + target.getData().name()
                + " (total power: " + target.getTotalPower() + ")");
    }
}
