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
import engine.cards.abilities.Trigger;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectType;
import engine.cards.effects.ModalSelector;
import engine.cards.effects.TargetSelector;
import engine.cards.keywords.Keyword;
import engine.cards.types.CardType;
import engine.cards.types.Color;
import engine.battle.BattleSystem;

import java.util.stream.Collectors;

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
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";

    public CliController(GameState gameState, TurnManager turnManager) {
        this.gameState = gameState;
        this.inputHandler = new InputHandler();
        this.turnManager = turnManager;
        this.battleSystem = new BattleSystem(gameState, turnManager);
        gameState.setTargetSelector(this::selectTargets);
        gameState.setModalSelector(this::selectModal);
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
     * a second Color field. When that is added, this method should accept a
     * List<Color>
     * and alternate the ANSI codes character-by-character across the name string.
     */
    private String colorize(String text, Color color) {
        if (color == null)
            return text;
        switch (color) {
            case Red:
                return RED + text + RESET;
            case Green:
                return GREEN + text + RESET;
            case Blue:
                return BLUE + text + RESET;
            case Yellow:
                return YELLOW + text + RESET;
            case Purple:
                return PURPLE + text + RESET;
            case Black:
                return BLACK + text + RESET;
            default:
                return text;
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
                case Red:
                    code += RED;
                    break;
                case Green:
                    code += GREEN;
                    break;
                case Blue:
                    code += BLUE;
                    break;
                case Yellow:
                    code += YELLOW;
                    break;
                case Purple:
                    code += PURPLE;
                    break;
                case Black:
                    code += BLACK;
                    break;
            }
        }
        return code + text + RESET;
    }

    /**
     * Returns the raw ANSI escape code string for the given color, or an empty
     * string if color is null.
     */
    private String ansiCode(Color color) {
        if (color == null)
            return "";
        return switch (color) {
            case Red -> RED;
            case Green -> GREEN;
            case Blue -> BLUE;
            case Yellow -> YELLOW;
            case Purple -> PURPLE;
            case Black -> BLACK;
        };
    }

    /**
     * Multi-color variant of {@link #colorize(String, Color)}.
     * Single-color lists delegate to the standard method.
     * Multi-color lists alternate ANSI color codes per character so that both
     * colors are visible in the name (e.g. "Yamato" in Green/Yellow alternates
     * each letter between the two codes).
     */
    private String colorize(String text, List<Color> colors) {
        if (colors.isEmpty())
            return text;
        if (colors.size() == 1)
            return colorize(text, colors.get(0));
        String[] codes = colors.stream().map(this::ansiCode).toArray(String[]::new);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(codes[i % codes.length]).append(text.charAt(i));
        }
        return sb.append(RESET).toString();
    }

    /**
     * Multi-color variant of {@link #boldColorize(String, Color)}.
     * Same alternating-character logic as {@link #colorize(String, List)} but
     * each character is also rendered in bold.
     */
    private String boldColorize(String text, List<Color> colors) {
        if (colors.isEmpty())
            return boldColorize(text, (Color) null);
        if (colors.size() == 1)
            return boldColorize(text, colors.get(0));
        String[] codes = colors.stream().map(c -> BOLD + ansiCode(c)).toArray(String[]::new);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(codes[i % codes.length]).append(text.charAt(i));
        }
        return sb.append(RESET).toString();
    }

    /**
     * Returns a single-line ability/effect description for {@code card}.
     *
     * <p>
     * Priority order:
     * <ol>
     * <li>The card's {@code description} field (set from JSON or the GameFactory
     * ability text) — used for all real cards and named test cards.</li>
     * <li>A bracketed list of keyword abilities derived from the runtime ability
     * list — used for generic test cards whose description is empty.</li>
     * <li>Empty string — no abilities, nothing printed.</li>
     * </ol>
     */
    private String cardAbilityLine(Card card) {
        if (card.getData() == null)
            return "";
        String desc = card.getData().description();
        if (desc != null && !desc.isBlank())
            return desc;
        // Fall back: format keyword abilities (e.g. "[Rush]", "[Blocker]")
        return card.getAbilities().stream()
                .filter(a -> a.getTriggerType() == Trigger.KEYWORD && a.getKeyword() != null)
                .map(a -> formatKeyword(a.getKeyword()))
                .collect(Collectors.joining(" "));
    }

    /**
     * Returns a single-line trigger description for {@code card}, showing all
     * TRIGGER abilities.
     * 
     * @param card The card whose trigger abilities to format. If the card has no
     *             TRIGGER abilities, returns an empty string.
     * @return A string describing the card's TRIGGER abilities, concatenated with "
     *         | " if there are multiple. Returns an empty string if the card has no
     *         TRIGGER abilities or if card data is null.
     */
    private String cardTriggerLine(Card card) {
        if (card.getData() == null) return "";
        boolean hasTrigger = card.getAbilities().stream()
                .anyMatch(a -> a.getTriggerType() == Trigger.TRIGGER);
        return hasTrigger ? card.getData().description() : "";
    }

    /**
     * Formats a Keyword enum value as a bracket tag, e.g. DOUBLE_ATTACK → "[Double
     * Attack]".
     */
    private String formatKeyword(Keyword k) {
        String raw = k.name().replace('_', ' ').toLowerCase();
        return "[" + Character.toUpperCase(raw.charAt(0)) + raw.substring(1) + "]";
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
                name = colorize(card.getData().name(), card.getData().colors());
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
        List<Color> activeColor = (active.getLeader() != null) ? active.getLeader().getData().colors() : null;
        List<Color> opponentColor = (opponent.getLeader() != null) ? opponent.getLeader().getData().colors() : null;
        System.out.println("=== " + boldColorize(active.getName(), activeColor)
                + " vs " + boldColorize(opponent.getName(), opponentColor) + " ===");

        int yourActiveDon   = active.getCost().getActive();
        int yourAttachedDon = active.getCost().getAttached();
        int yourRestedDon   = active.getCost().getRested();
        int oppActiveDon    = opponent.getCost().getActive();
        int oppAttachedDon  = opponent.getCost().getAttached();
        int oppRestedDon    = opponent.getCost().getRested();

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
            String name = boldColorize(card.getData().name(), card.getData().colors());
            String type = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
            System.out.printf("%d. %s%s  |  Cost: %d, Power: %d%n",
                    i + 1, name, type, card.getData().cost(), card.getData().power());
            String abilityLine = cardAbilityLine(card);
            if (!abilityLine.isEmpty()) {
                System.out.println("   " + WHITE + abilityLine + RESET);
            }
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
            String name = boldColorize(leader.getData().name(), leader.getData().colors());
            String rested = leader.isRested() ? "Rested" : "Active";
            String dons = leader.countDon() > 0 ? " | Dons: " + leader.countDon() : "";
            int power = (turnManager.getActivePlayer() == player) ? leader.getTotalPower() : leader.getBasePower();
            System.out.printf("  %s [Leader]  |  Power: %d | Life: %d | %s%s%n",
                    name, power, leader.getLifePoints(), rested, dons);
            String leaderAbility = cardAbilityLine(leader);
            if (!leaderAbility.isEmpty()) {
                System.out.println("     " + WHITE + leaderAbility + RESET);
            }
        }

        // Characters
        List<Card> fieldCards = player.getField().getCards();
        System.out.println("  Characters (" + fieldCards.size() + "/5):");
        if (fieldCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (Card card : fieldCards) {
                if (card.getData() == null) {
                    System.out.println("    - Unknown Card");
                    continue;
                }
                String name = boldColorize(card.getData().name(), card.getData().colors());
                String type = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? card.getTotalPower() : card.getBasePower();
                String rested = card.isRested() ? "Rested" : "Active";
                String dons = card.countDon() > 0 ? " | Dons: " + card.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
                String charAbility = cardAbilityLine(card);
                if (!charAbility.isEmpty()) {
                    System.out.println("       " + WHITE + charAbility + RESET);
                }
            }
        }
        // Stage
        List<Card> stageCards = player.getStage().getCards();
        System.out.println("  Stage (" + stageCards.size() + "/1):");
        if (stageCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (Card card : stageCards) {
                if (card.getData() == null) {
                    System.out.println("    - Unknown Card");
                    continue;
                }
                String name = boldColorize(card.getData().name(), card.getData().colors());
                String type = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? card.getTotalPower() : card.getBasePower();
                String rested = card.isRested() ? "Rested" : "Active";
                String dons = card.countDon() > 0 ? " | Dons: " + card.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
                String stageAbility = cardAbilityLine(card);
                if (!stageAbility.isEmpty()) {
                    System.out.println("       " + WHITE + stageAbility + RESET);
                }
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
                String name = boldColorize(topCard.getData().name(), topCard.getData().colors());
                String type = (topCard.getData().cardType() != null) ? " [" + topCard.getData().cardType() + "]" : "";
                int power = (turnManager.getActivePlayer() == player) ? topCard.getTotalPower()
                        : topCard.getBasePower();
                String rested = topCard.isRested() ? "Rested" : "Active";
                String dons = topCard.countDon() > 0 ? " | Dons: " + topCard.countDon() : "";
                System.out.printf("    - %s%s  |  Power: %d | %s%s%n",
                        name, type, power, rested, dons);
            }
        }
        // Life
        List<Card> lifeCards = player.getLife().getCards();
        System.out.println("  Life (" + lifeCards.size() + " cards):");
        if (lifeCards.isEmpty()) {
            System.out.println("    (none)");
        } else {
            for (Card card : lifeCards) {
                if (card.isFaceUp() && card.getData() != null) {
                    String name = boldColorize(card.getData().name(), card.getData().colors());
                    String type = (card.getData().cardType() != null) ? " [" + card.getData().cardType() + "]" : "";
                    int power = card.getBasePower();
                    System.out.printf("    - %s%s  |  Power: %d%n", name, type, power);
                } else {
                    System.out.println("    - [???]");
                }
            }
        }
        // Deck (size only)
        System.out.println("  Deck: " + player.getDeck().size() + " cards remaining");
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
            // TODO: Use Leader's Ability (ACTIVATED_MAIN trigger — not yet implemented)
            // System.out.printf("%d. Use Leader's Ability\n", mod + 6);
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
                // case 7: Use Leader's Ability — stub, uncomment when ACTIVATED_MAIN is wired
                // System.out.println("Leader ability not yet implemented.");
                // break;
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

        announceAttack(attacker, target);
        defend(opponent, attacker, target);
    }

    /**
     * Handles the defend phase logic: allows the defending player to either block
     * with a valid character, use counters,
     * or take the attack. This method is called from the battle system to give the
     * defender the opportunity to respond to an attack before it is resolved.
     * 
     * @param otherPlayer The defending player.
     * @param attacker    The attacking card.
     * @param target      The defending target card.
     */
    public void defend(Player otherPlayer, Card attacker, Card target) {
        int counterBoost = 0;

        // Step 1: blocker window (skipped if attacker is UNBLOCKABLE)
        if (!attacker.hasKeyword(Keyword.UNBLOCKABLE)) {
            List<Card> blockers = battleSystem.getValidBlockers(otherPlayer);
            if (!blockers.isEmpty() && inputHandler.confirm("Use a blocker?")) {
                Card blocker = selectCard(blockers, "Select blocker: ");
                if (blocker != null) {
                    blocker.rest();
                    target = blocker;
                    announceBlocker(blocker, attacker);
                }
            }
        }

        // Step 2: counter window against the final target (loop allows multiple
        // counters)
        List<Card> counters = battleSystem.getValidCounters(otherPlayer);
        while (!counters.isEmpty() && inputHandler.confirm("Play a counter card?")) {
            Card counterCard = selectCard(counters, "Counter card: ");
            if (counterCard == null)
                break;
            int boost = battleSystem.applyCounter(otherPlayer, counterCard, target);
            counterBoost += boost;
            announceCounter(counterCard, boost);
            counters = battleSystem.getValidCounters(otherPlayer);
        }

        // Step 3: capture pre-resolve state, resolve, then announce outcome
        boolean targetIsLeader = target instanceof Leader;
        int lifesBefore = targetIsLeader ? target.getOwner().getLife().size() : 0;

        List<Card> triggers = battleSystem.resolve(attacker, target, counterBoost);

        int lifesAfter = targetIsLeader ? target.getOwner().getLife().size() : 0;
        boolean attackerWon = targetIsLeader
                ? lifesAfter < lifesBefore
                : !target.getOwner().getField().getCards().contains(target);
        announceBattleResult(attacker, target, attackerWon, lifesAfter, lifesBefore - lifesAfter);

        // Step 4: trigger window for any triggers generated by the battle resolution
        trigger(target.getOwner(), triggers);
    }

    /**
     * Handles the trigger resolution phase after a battle, allowing the attacking
     * player to choose whether to activate each trigger in order. This method is
     * called from the battle system after resolving an attack, passing in any
     * triggers that were generated by the battle resolution (e.g. "When this card
     * destroys a character, draw a card"). The player can choose to activate each
     * trigger immediately or skip it. This allows the player to respond to triggers
     * in a timely manner while also giving them the option to delay or forego
     * certain triggers based on the evolving game state.
     * 
     * @param player   The player who controls the triggers (usually the attacker,
     *                 but could be the defender if they had a trigger that
     *                 activated during defense).
     * @param triggers The list of trigger cards generated by the battle resolution
     *                 that the player has the option to activate. This list may be
     *                 empty if no triggers were generated.
     */
    public void trigger(Player player, List<Card> triggers) {
        if (triggers.isEmpty()) {
            return;
        }
        for (Card triggerCard : triggers) {
            String desc = cardTriggerLine(triggerCard);
            if (!desc.isEmpty()) {
                System.out.println("  [Trigger] " + WHITE + desc + RESET);
            }
            boolean proceed = inputHandler.confirm("Activate trigger?");
            if (proceed) {
                announceTrigger(player, triggerCard);
                battleSystem.resolveTrigger(player, triggerCard);
            } else {
                System.out.println("  Trigger skipped.");
            }
        }
    }

    /**
     * Printed when a player chooses to activate a [Trigger] ability revealed
     * from their life area.
     *
     * @param player      the player activating the trigger
     * @param triggerCard the life card whose [Trigger] ability is firing
     */
    public void announceTrigger(Player player, Card triggerCard) {
        String name = boldColorize(triggerCard.getData().name(), triggerCard.getData().colors());
        String desc = cardTriggerLine(triggerCard);
        System.out.println("  → " + player.getName() + " activates " + name
                + "'s [Trigger]" + (desc.isEmpty() ? "!" : ": " + WHITE + desc + RESET));
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

        int availableDon = currentPlayer.getCost().getActive();

        System.out.println("Select a card to play:");
        for (int i = 0; i < playable.size(); i++) {
            Card card = playable.get(i);
            String name = colorize(card.getData().name(), card.getData().colors());
            int cost = card.getData().cost();
            int power = card.getData().power();
            String stats = (availableDon >= cost)
                    ? GREEN + "(Type: " + card.getData().cardType() + ", Cost: " + cost + ", Power: " + power + ")"
                            + RESET
                    : RED + "(Type: " + card.getData().cardType() + ", Cost: " + cost + ", Power: " + power + ")"
                            + RESET;
            System.out.printf("%d. %s %s%n", i + 1, name, stats);
        }
        System.out.println("0. Cancel");
        int choice = inputHandler.readInt("Card: ", 0, playable.size());
        if (choice == 0)
            return;

        Card selected = playable.get(choice - 1);
        CardType selectedType = selected.getData().cardType();

        // Handle zone capacity: prompt for replacement before playing
        if (selectedType == CardType.CHARACTER && currentPlayer.getField().getCards().size() >= 5) {
            System.out.println("Your field is full (5/5). Choose a character to send to trash, or 0 to cancel:");
            List<Card> fieldCards = new ArrayList<>(currentPlayer.getField().getCards());
            Card toTrash = selectCard(fieldCards, "Replace: ");
            if (toTrash == null) {
                System.out.println("Play cancelled.");
                return;
            }
            gameState.trash(currentPlayer, toTrash);
            System.out.println(colorize(toTrash.getData().name(), toTrash.getData().colors()) + " sent to trash.");
        } else if (selectedType == CardType.STAGE && !currentPlayer.getStage().isEmpty()) {
            Card existing = currentPlayer.getStage().getCards().get(0);
            boolean confirm = inputHandler.confirm(
                    colorize(existing.getData().name(), existing.getData().colors())
                            + " will be sent to trash. Replace?");
            if (!confirm) {
                System.out.println("Play cancelled.");
                return;
            }
            gameState.trash(currentPlayer, existing);
            System.out.println(colorize(existing.getData().name(), existing.getData().colors()) + " sent to trash.");
        }

        gameState.playCard(currentPlayer, selected);
        announceCardPlayed(currentPlayer, selected);
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
            if (card instanceof DonCard don && !don.isRested() && !don.isAttached()) {
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

    /**
     * Interactive {@link TargetSelector} implementation. Called by
     * {@code EffectFactory} when an ability effect needs the player to pick
     * targets from a filtered pool (e.g. "K.O. 1 of your opponent's Characters").
     *
     * <p>
     * Loops up to {@code count} times, each time calling {@link #selectCard}
     * to present the remaining candidates. The player may cancel (enter 0) at any
     * point, which stops selection early (useful for optional "up to N" effects).
     */
    private List<Card> selectTargets(List<Card> candidates, int count, String prompt) {
        List<Card> chosen = new ArrayList<>();
        List<Card> remaining = new ArrayList<>(candidates);
        for (int i = 0; i < count && !remaining.isEmpty(); i++) {
            String p = count > 1 ? prompt + " (" + (i + 1) + "/" + count + ")" : prompt;
            Card card = selectCardWithVisibility(remaining, p);
            if (card == null)
                break; // player cancelled (entered 0)
            chosen.add(card);
            remaining.remove(card);
        }
        return chosen;
    }

    /**
     * Like {@link #selectCard}, but respects face-down state: face-down cards are
     * displayed as {@code [???]} so hidden-information rules are preserved when a
     * player chooses among their life cards to flip.
     */
    private Card selectCardWithVisibility(List<Card> cards, String prompt) {
        if (cards.isEmpty()) return null;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            String label;
            if (!card.isFaceUp() && card.getZone() != null
                    && card.getZone().getType() == engine.zones.ZoneType.LIFE) {
                label = "[???]";
            } else if (card instanceof DonCard) {
                String raw = (card.getData() != null) ? card.getData().name() : "DON!!";
                label = WHITE + raw + RESET;
            } else if (card.getData() != null) {
                label = colorize(card.getData().name(), card.getData().colors());
            } else {
                label = "Unknown Card";
            }
            System.out.printf("%d. %s%n", i + 1, label);
        }
        System.out.println("0. Cancel");
        int choice = inputHandler.readInt(prompt, 0, cards.size());
        return (choice == 0) ? null : cards.get(choice - 1);
    }

    /**
     * Interactive {@link ModalSelector} implementation. Presents each branch as
     * a numbered option and returns the 0-based index of the player's choice.
     */
    private int selectModal(List<List<EffectData>> choices, String prompt) {
        System.out.println(prompt);
        for (int i = 0; i < choices.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, describeBranch(choices.get(i)));
        }
        int choice = inputHandler.readInt("Choose: ", 1, choices.size());
        return choice - 1;
    }

    /** Joins each effect in a branch with ", then ". */
    private String describeBranch(List<EffectData> branch) {
        if (branch == null || branch.isEmpty()) return "(no effect)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branch.size(); i++) {
            if (i > 0) sb.append(", then ");
            sb.append(describeEffect(branch.get(i)));
        }
        return sb.toString();
    }

    /** Produces a short human-readable description of a single EffectData. */
    private String describeEffect(EffectData e) {
        if (e == null) return "?";
        EffectType type = e.type();
        int val = e.value();
        engine.cards.effects.Target tgt = e.target();
        String player = (tgt != null && tgt.player() != null) ? tgt.player().name() : null;
        String cardType = (tgt != null && tgt.cardType() != null) ? tgt.cardType().name().toLowerCase() : "card";
        int max = (tgt != null) ? tgt.max() : 0;
        int maxCost = (tgt != null) ? tgt.maxCost() : 0;

        return switch (type) {
            case DRAW            -> "Draw " + val + " card" + (val == 1 ? "" : "s");
            case TRASH           -> "Trash " + val + " card" + (val == 1 ? "" : "s");
            case REST            -> "Rest " + (max > 0 ? "up to " + max : "1") + " "
                                    + (player != null ? player.toLowerCase() + "'s " : "") + cardType;
            case KO              -> "K.O. " + (max > 0 ? max : 1)
                                    + (maxCost > 0 ? " " + cardType + " with cost " + maxCost + " or less" : " " + cardType);
            case RETURN_TO_HAND  -> "Return " + (max > 0 ? max : 1)
                                    + (maxCost > 0 ? " " + cardType + " (cost ≤" + maxCost + ")" : " " + cardType)
                                    + " to hand";
            case POWER_BOOST     -> (val >= 0 ? "+" : "") + val + " power this turn";
            case COST_MODIFY     -> (val >= 0 ? "+" : "") + val + " cost";
            case GRANT_KEYWORD   -> "Gain " + (e.keyword() != null ? formatKeyword(e.keyword()) : "keyword");
            case ADD_DON         -> "Add " + val + " DON!!";
            case SET_DON_ACTIVE  -> "Set " + val + " DON!! active";
            case SHUFFLE_DECK    -> "Shuffle your deck";
            case SEARCH_DECK     -> "Look at top " + val + ", add " + (max > 0 ? max : 1) + " to hand";
            case PLAY_CARD       -> "Play 1 card for free";
            case MODAL           -> "Choose one";
            default              -> type.name().toLowerCase().replace('_', ' ');
        };
    }

    // -------------------------------------------------------------------------
    // Game-event announcements (called by GameEngine — keeps System.out out of
    // the engine layer)
    // -------------------------------------------------------------------------

    /**
     * Prints the turn/phase header shown at the start of every phase.
     *
     * @param turn       current turn number
     * @param playerName name of the active player
     * @param phaseName  display name of the phase (e.g. "Draw")
     */
    public void announcePhase(int turn, String playerName, String phaseName) {
        System.out.println("\nTurn " + turn + " — " + boldColorize(playerName, (Color) null)
                + "'s " + phaseName + " Phase");
    }

    /**
     * Printed after the automatic Draw phase resolves.
     *
     * @param active the player who drew
     */
    public void announceDrawPhaseResult(Player active) {
        System.out.println("  → " + active.getName() + " drew 1 card.");
    }

    /**
     * Printed after the automatic DON!! phase resolves.
     *
     * @param active   the player who received DON!! cards
     * @param donCount 1 on the very first turn, 2 thereafter
     */
    public void announceDonPhaseResult(Player active, int donCount) {
        System.out.println("  → " + active.getName() + " received "
                + donCount + " DON!! card" + (donCount == 1 ? "" : "s") + ".");
    }

    /**
     * Printed when a card declares an attack.
     *
     * @param attacker the attacking card
     * @param target   the card being attacked
     */
    public void announceAttack(Card attacker, Card target) {
        String atkName = boldColorize(attacker.getData().name(), attacker.getData().colors());
        String tgtName = colorize(target.getData().name(), target.getData().colors());
        System.out.println("  \n" + atkName + " attacks " + tgtName + "!");
    }

    /**
     * Printed when a defending player activates a blocker, redirecting the attack.
     *
     * @param blocker  the card stepping in to block
     * @param attacker the attacking card (shown for context)
     */
    public void announceBlocker(Card blocker, Card attacker) {
        String blkName = boldColorize(blocker.getData().name(), blocker.getData().colors());
        String atkName = colorize(attacker.getData().name(), attacker.getData().colors());
        System.out.println("  " + blkName + " blocks " + atkName + "! (attack redirected)");
    }

    /**
     * Printed each time a counter card is played during the defend window.
     *
     * @param counterCard the card played as a counter (already sent to trash)
     * @param boost       the power boost it granted to the defender
     */
    public void announceCounter(Card counterCard, int boost) {
        String name = colorize(counterCard.getData().name(), counterCard.getData().colors());
        System.out.println("  Counter: " + name + " (+" + boost + " power to defender)");
    }

    /**
     * Printed after {@code BattleSystem.resolve()} finishes, summarising the
     * outcome.
     *
     * <p>
     * State is observed by the caller ({@link #defend}) before and after resolve
     * so that the method needs no access to BattleSystem internals.
     *
     * @param attacker          the attacking card
     * @param target            the card that was ultimately attacked (may be a
     *                          blocker)
     * @param attackerWon       true if the attacker's power met or exceeded the
     *                          defender's
     * @param defenderLifeAfter life count of the target's owner after resolve (0
     *                          for Characters)
     * @param lifesTaken        how many life cards were taken this attack (0, 1, or
     *                          2 for DOUBLE_ATTACK)
     */
    public void announceBattleResult(Card attacker, Card target,
            boolean attackerWon, int defenderLifeAfter, int lifesTaken) {
        String tgtName = colorize(target.getData().name(), target.getData().colors());
        String atkName = boldColorize(attacker.getData().name(), attacker.getData().colors());
        if (!attackerWon) {
            System.out.println("  → " + tgtName + " withstands the attack!");
        } else if (target instanceof Leader) {
            String plural = lifesTaken == 1 ? "card" : "cards";
            System.out.println("  → " + atkName + "'s attack hits! "
                    + lifesTaken + " life " + plural + " taken. "
                    + "(Life remaining: " + defenderLifeAfter + ")");
        } else {
            System.out.println("  → " + tgtName + " is K.O.'d!");
        }
    }

    /**
     * Printed when a player successfully plays a card from their hand.
     *
     * @param player the player who played the card
     * @param card   the card that was played
     */
    public void announceCardPlayed(Player player, Card card) {
        String name = boldColorize(card.getData().name(), card.getData().colors());
        String type = card.getData().cardType() != null ? " [" + card.getData().cardType() + "]" : "";
        System.out.println("  " + player.getName() + " played " + name + type + ".");
        String abilityLine = cardAbilityLine(card);
        if (!abilityLine.isEmpty()) {
            System.out.println("  " + WHITE + abilityLine + RESET);
        }
    }

    /**
     * Printed when the game ends.
     *
     * @param winnerName name of the winning player
     */
    public void announceGameOver(String winnerName) {
        System.out.println("\n" + BOLD + "═══ Game Over! Winner: " + winnerName + " ═══" + RESET);
    }
}
