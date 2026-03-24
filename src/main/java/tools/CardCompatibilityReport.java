package tools;

import engine.cards.CardData;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Condition;
import engine.cards.abilities.Cost;
import engine.cards.abilities.Trigger;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectType;
import engine.data.CardDatabase;
import engine.cards.effects.Duration;
import java.io.*;
import java.util.*;
import static java.util.stream.Collectors.*;

public class CardCompatibilityReport {

    // Sets of values currently handled by the engine
    private static final Set<EffectType> IMPLEMENTED_EFFECTS = Set.of(
            EffectType.DRAW, EffectType.TRASH, EffectType.REST, EffectType.KO,
            EffectType.RETURN_TO_HAND, EffectType.POWER_BOOST, EffectType.COST_MODIFY,
            EffectType.GRANT_KEYWORD, EffectType.PLAY_CARD, EffectType.ADD_DON,
            EffectType.SET_DON_ACTIVE, EffectType.ATTACH_DON, EffectType.SEARCH_DECK,
            EffectType.SHUFFLE_DECK, EffectType.MOVE_TO_TOP_DECK, EffectType.MOVE_TO_BOTTOM_DECK,
            EffectType.PREVENT_ATTACK, EffectType.PREVENT_KO, EffectType.PREVENT_BLOCK,
            EffectType.ADD_LIFE, EffectType.FLIP_LIFE, EffectType.NEGATE,
            EffectType.UNREST, EffectType.MODAL, EffectType.POWER_REDUCE);

    private static final Set<Condition> IMPLEMENTED_CONDITIONS = EnumSet.allOf(Condition.class);

    private static final Set<Cost.CostType> IMPLEMENTED_COSTS = Set.of(
            Cost.CostType.DON, Cost.CostType.DON_MINUS, Cost.CostType.DON_REST,
            Cost.CostType.TRASH, Cost.CostType.TRASH_SELF, Cost.CostType.REST_SELF,
            Cost.CostType.REST_NAMED, Cost.CostType.REST_CHARACTER, Cost.CostType.REST_ANY,
            Cost.CostType.RETURN_SELF, Cost.CostType.PLACE_BOTTOM_SELF,
            Cost.CostType.LIFE_TO_HAND, Cost.CostType.LIFE_TO_TRASH);

    private static final Set<Trigger> UNIMPLEMENTED_TRIGGERS = Set.of(
            Trigger.MAIN,
            Trigger.ON_BLOCK,
            Trigger.OPPONENTS_ATTACK);

    public enum Flag {
        UNIMPLEMENTED_TRIGGER,
        UNIMPLEMENTED_EFFECT,
        UNIMPLEMENTED_COST,
        UNIMPLEMENTED_CONDITION,
        COMPOUND_COST, // costs.size() > 1
        WHILE_IN_PLAY_DURATION, // Future Rescoping — source-dependent continuous
        NO_ABILITIES // non-blank card_text but abilities list is empty
    }

    record Row(
            String cardId, String cardName, String cardType,
            Flag flag, String detail) {
    }

    public static void main(String[] args) throws IOException {
        CardDatabase db = new CardDatabase("src/main/resources/compiled/data/");
        generate(db);
    }

    /**
     * Generates a report on card compatibility with the current engine
     * implementation, based on the cards in {@code db}.
     * 
     * @param db the CardDatabase to analyze
     * @throws IOException
     */
    public static void generate(CardDatabase db) throws IOException {
        List<Row> rows = collectRows(db);
        int totalCards = db.getAllCards().size();
        printSummary(rows, totalCards);
        writeCsv(rows, totalCards);

    }

    /**
     * Collects rows of card data for cards that are not fully supported by the
     * engine, along with the reason(s) why.
     * Criteria for inclusion:
     * - Card has non-blank description but no abilities (likely missing parsing of
     * abilities)
     * - Card has at least one ability with an unimplemented trigger, effect, cost,
     * or condition
     * - Card has at least one ability with a compound cost (costs.size() > 1)
     * - Card has at least one ability with a "while in play" continuous effect
     * (duration
     * of CONTINUOUS and a trigger other than KEYWORD or PASSIVE)
     * 
     * @param db the CardDatabase to analyze
     * @return a list of rows representing cards with compatibility issues, along
     *         with the reason(s
     */
    private static List<Row> collectRows(CardDatabase db) {
        // For each card, collect flag rows
        List<Row> rows = new ArrayList<>();
        for (CardData card : db.getAllCards()) {
            if (card.abilities().isEmpty() && !card.description().isBlank()) {
                rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.NO_ABILITIES,
                        truncate(card.description(), 60)));
            }

            for (AbilityData ability : card.abilities()) {
                if (UNIMPLEMENTED_TRIGGERS.contains(ability.triggerType())) {
                    rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.UNIMPLEMENTED_TRIGGER,
                            "Trigger: " + ability.triggerType().name()));
                }

                if (ability.costs() != null && ability.costs().size() > 1) {
                    rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.COMPOUND_COST,
                            "Costs: " + ability.costs().size()));
                }
                if (ability.costs() != null) {
                    for (Cost cost : ability.costs()) {
                        if (!IMPLEMENTED_COSTS.contains(cost.type)) {
                            rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.UNIMPLEMENTED_COST,
                                    "Cost: " + cost.type.name()));
                        }
                    }
                    checkCondition(card, ability.condition(), rows);
                    checkEffects(card, ability.effects(), rows);
                }
            }
        }
        return rows;
    }

    /**
     * Checks if the given card has any abilities with unimplemented triggers,
     * effects, costs, or conditions, and adds corresponding rows to the report.
     * Also checks for compound costs and "while in play" continuous effects.
     * 
     * @param card    the CardData to check
     * @param effects the list of EffectData to check for unimplemented effects and
     *                "while in play" continuous effects
     * @param rows    the list of rows to add to if any issues are found
     */
    private static void checkEffects(CardData card, List<EffectData> effects, List<Row> rows) {
        for (EffectData effect : effects) {
            if (!IMPLEMENTED_EFFECTS.contains(effect.type())) {
                rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.UNIMPLEMENTED_EFFECT,
                        "Effect: " + effect.type().name()));
            }
            if (effect.duration() == Duration.WHILE_IN_PLAY) {
                rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.WHILE_IN_PLAY_DURATION,
                        "Effect: " + effect.type().name()));
            }
            checkCondition(card, effect.condition(), rows);
            if (effect.choices() != null) {
                for (List<EffectData> branch : effect.choices()) {
                    checkEffects(card, branch, rows);
                }
            }
        }
    }

    /**
     * Checks if the given card has any abilities with unimplemented triggers,
     * effects, costs, or conditions, and adds corresponding rows to the report.
     * Also checks for compound costs and "while in play" continuous effects.
     * 
     * @param card the CardData to check
     * @param c    the Condition to check for unimplemented conditions
     * @param rows the list of rows to add to if any issues are found
     */
    private static void checkCondition(CardData card, Condition c, List<Row> rows) {
        if (c != null && !IMPLEMENTED_CONDITIONS.contains(c)) {
            rows.add(new Row(card.id(), card.name(), card.cardType().name(), Flag.UNIMPLEMENTED_CONDITION,
                    "Condition: " + c.name()));
        }
    }

    /**
     * Prints a console summary of the compatibility report, including the total
     * number of cards analyzed and the number/percentage of cards with each flag,
     * grouped by flag type.
     * 
     * @param rows       the rows representing cards with compatibility issues
     * @param totalCards the total number of cards analyzed (for summary statistics)
     */
    private static void printSummary(List<Row> rows, int totalCards) {
        // Print console summary grouped by flag type

        System.out.println("Card Compatibility Report Summary:");
        System.out.println("Total cards analyzed: " + totalCards);
        int distinctCardIds = rows.stream().map(Row::cardId).collect(toSet()).size();
        System.out.println("Distinct cards with issues: " + distinctCardIds);
        int fullyPlayable = totalCards - distinctCardIds;
        System.out.println("Fully playable cards: " + fullyPlayable);
        Map<Flag, Long> counts = rows.stream()
                .collect(groupingBy(Row::flag, () -> new EnumMap<>(Flag.class), counting()));
        counts.forEach((flag, count) -> System.out.printf("  %-28s %d cards%n", flag, count));
        System.out.println("Report written to csv/card_compatibility_report.csv");
    }

    /**
     * Writes the collected rows to a CSV file, along with a summary of the total
     * number of cards analyzed and the number/percentage of cards with each flag.
     * 
     * @param rows       the rows to write to the CSV
     * @param totalCards the total number of cards analyzed (for summary statistics)
     */
    private static void writeCsv(List<Row> rows, int totalCards) {
        // Write csv/card_compatibility_report.csv
        try (PrintWriter writer = new PrintWriter(new File("csv/card_compatibility_report.csv"))) {
            StringBuilder sb = new StringBuilder();
            sb.append("card_id,card_name,card_type,flag,detail\n");
            for (Row row : rows) {
                sb.append(csvQuote(row.cardId)).append(",")
                        .append(csvQuote(row.cardName)).append(",")
                        .append(csvQuote(row.cardType)).append(",")
                        .append(csvQuote(row.flag.name())).append(",")
                        .append(csvQuote(row.detail)).append("\n");
            }
            writer.write(sb.toString());
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Truncates a string to a maximum length, adding "..." if it was truncated.
     * 
     * @param s         the string to truncate
     * @param maxLength the maximum length of the returned string, including "..."
     *                  if truncation occurs
     * @return the truncated string
     */
    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }

    /**
     * Quotes a string for CSV output, escaping quotes as needed.
     * 
     * @param s the string to quote
     * @return the quoted string
     */
    private static String csvQuote(String s) {
        if (s.contains("\"") || s.contains(",") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

}
