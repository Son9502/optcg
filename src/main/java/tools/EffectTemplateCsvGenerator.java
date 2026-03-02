package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Regenerates effect_templates_generic.csv and effect_templates_unique.csv
 * from compiled card JSON, using the fixed Parser to correctly split multi-ability text.
 *
 * Each row corresponds to one atomic sub-effect clause, not a full ability body.
 * Compound effects are split on:
 *   • bullet points (modal "Choose one: • A • B" choices)
 *   • ". Then, " sequential chains
 *   • "If X, do Y." conditional clauses (simple if-clauses only)
 *
 * Trigger labels embedded after "/" or "•" (e.g. "/ [On K.O.] K.O. up to...")
 * are stripped from the simplified description; the sub-effect retains the "/" or "•"
 * prefix and is classified by its actual effect type.
 *
 *   Generic : (simplified_description, trigger) pair appears on 2+ distinct cards.
 *   Unique  : (simplified_description, trigger) pair appears on exactly 1 card.
 *
 * Usage (run after CardCompiler):
 *   mvn exec:java -Dexec.mainClass="tools.EffectTemplateCsvGenerator"
 */
public class EffectTemplateCsvGenerator {

    private static final String COMPILED_DIR = "src/main/resources/compiled/data/";
    private static final String CSV_DIR      = "csv/";
    private static final String[] CATEGORIES = {"sets", "decks", "promos"};

    // ── Generalisation patterns (applied in order) ────────────────────────────

    private static final Pattern P_TYPE_BRACE = Pattern.compile("\\{[^}]+\\}");
    private static final Pattern P_TYPE_QUOTE = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern P_POWER_NUM  = Pattern.compile("[+\\-]?\\d{4,}");
    private static final Pattern P_DON_X      = Pattern.compile("DON!! x\\d+",   Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DON_MINUS  = Pattern.compile("DON!! -\\d+",   Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NUMBER     = Pattern.compile("\\b\\d+\\b");
    private static final Pattern P_YOUR_OPP   = Pattern.compile("\\byour opponent's\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_YOUR       = Pattern.compile("\\byour\\b",            Pattern.CASE_INSENSITIVE);

    /** Trigger/keyword labels preserved in bracket notation inside effect text. */
    private static final Set<String> TRIGGER_LABELS = Set.of(
        "Main", "Trigger", "On Play", "When Attacking",
        "Activate:Main", "Activate: Main",
        "Once Per Turn", "On K.O.", "On Block",
        "Counter", "End of Your Turn", "End of Your Opponent's Turn",
        "On Your Opponent's Attack",
        "Blocker", "Rush", "Rush: Character",
        "Double Attack", "Banish", "Unblockable"
    );

    // ── Effect-type explanations (keyed by engine EffectType name) ───────────

    private static final Map<String, String> EXPLANATIONS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Engine EffectType names
        m.put("MODAL",              "Player selects one of two or more effects ('Choose one:')");
        m.put("DRAW",               "Draw cards from the top of the deck into hand");
        m.put("TRASH",              "Send a card from hand, field, or deck to the trash");
        m.put("REST",               "Change one or more cards' state from active to rested");
        m.put("UNREST",             "Change one or more cards' state from rested to active");
        m.put("KO",                 "Knock out one or more Characters, sending them to trash");
        m.put("POWER_BOOST",        "Increase a card's power by +N (positive value)");
        m.put("POWER_REDUCE",       "Decrease a card's power by N");
        m.put("COST_MODIFY",        "Change a Character's cost by ±N");
        m.put("RETURN_TO_HAND",     "Return a card from the field to the owner's hand");
        m.put("PLAY_CARD",          "Play a card from hand, deck, or trash directly to the field");
        m.put("SEARCH_DECK",        "Look at the top N cards of the deck; add matching cards to hand");
        m.put("SHUFFLE_DECK",       "Shuffle a player's deck");
        m.put("MOVE_TO_BOTTOM_DECK","Place N card(s) at the bottom of a player's deck");
        m.put("MOVE_TO_TOP_DECK",   "Place N card(s) at the top of a player's deck");
        m.put("ATTACH_DON",         "Give N rested DON!! from your field to a Leader or Character");
        m.put("ADD_DON",            "Add N DON!! card(s) from your DON!! deck (rested or active)");
        m.put("SET_DON_ACTIVE",     "Set N of your DON!! cards as active (refresh them)");
        m.put("ADD_LIFE",           "Place N card(s) into a player's Life area");
        m.put("GRANT_KEYWORD",      "Give a Character a keyword ability (Rush, Blocker, Double Attack, etc.)");
        m.put("PREVENT_ATTACK",     "Target cannot attack this turn or during this battle");
        m.put("PREVENT_BLOCK",      "Opponent cannot activate a Blocker meeting a condition");
        m.put("PREVENT_KO",         "Target cannot be K.O.'d (in battle or by a specific attribute)");
        m.put("NEGATE",             "Negate the effects of an opponent's card");
        // Trigger-based categories (not engine EffectTypes)
        m.put("COUNTER",            "Power boost granted to your cards during a battle");
        m.put("KEYWORD",            "Standalone keyword ability (Rush, Blocker, Double Attack, Banish, Unblockable)");
        m.put("PASSIVE",            "Persistent passive effect while conditions are met");
        m.put("OTHER",              "Complex or rare effect not yet covered by a parser rule");
        EXPLANATIONS = Collections.unmodifiableMap(m);
    }

    // ── Entry record ──────────────────────────────────────────────────────────

    private record EffectEntry(
        String effectType,
        String trigger,
        String example,
        String simplified,
        int    count
    ) {}

    // ── Main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // key = simplified::trigger  (case-insensitive dedup key)
        Map<String, EffectEntry> entries = new LinkedHashMap<>();

        for (String category : CATEGORIES) {
            File dir = Path.of(COMPILED_DIR, category).toFile();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) continue;

            for (File file : files) {
                JsonNode root = mapper.readTree(file);
                if (!root.isArray()) continue;

                for (JsonNode card : root) {
                    String cardText = card.path("card_text").textValue();
                    if (cardText == null || cardText.isBlank()) continue;
                    // Strip disclaimer/errata boilerplate appended to some reprinted cards.
                    // Three observed formats:
                    //   "Disclaimer: ..."  (sentence-cased)
                    //   "DISCLAIMER: ..."  (upper-cased)
                    //   "This card has been officially errata'd."
                    String lower = cardText.toLowerCase();
                    int disclaimerIdx = lower.indexOf("disclaimer:");
                    if (disclaimerIdx < 0) disclaimerIdx = lower.indexOf("this card has been officially errata");
                    if (disclaimerIdx >= 0) cardText = cardText.substring(0, disclaimerIdx).trim();
                    if (cardText.isBlank()) continue;

                    List<ParsedAbility> abilities = Parser.parse(cardText);
                    for (ParsedAbility pa : abilities) {
                        String effectText = pa.effect();
                        if (effectText == null || effectText.isBlank()) continue;

                        String trigger = pa.trigger() != null ? pa.trigger() : "Passive";

                        // Split compound effects into atomic sub-clauses before generalising.
                        for (String sub : splitSubEffects(effectText)) {
                            String effectType = classifyEffectWithParser(sub, trigger);
                            String simplified = generalizeEffect(sub);
                            String key        = simplified.toLowerCase() + "::" + trigger.toLowerCase();

                            EffectEntry prev = entries.get(key);
                            if (prev == null) {
                                entries.put(key, new EffectEntry(effectType, trigger, sub, simplified, 1));
                            } else {
                                entries.put(key, new EffectEntry(
                                    prev.effectType, prev.trigger,
                                    prev.example,   prev.simplified,
                                    prev.count + 1));
                            }
                        }
                    }
                }
            }
        }

        List<EffectEntry> generic = new ArrayList<>();
        List<EffectEntry> unique  = new ArrayList<>();
        for (EffectEntry e : entries.values()) {
            (e.count >= 2 ? generic : unique).add(e);
        }

        Comparator<EffectEntry> comp = Comparator
            .comparing((EffectEntry e) -> e.effectType)
            .thenComparing(e -> e.trigger);
        generic.sort(comp);
        unique.sort(comp);

        writeCsv(CSV_DIR + "effect_templates_generic.csv", generic);
        writeCsv(CSV_DIR + "effect_templates_unique.csv",  unique);

        System.out.printf("Generic : %d entries%n", generic.size());
        System.out.printf("Unique  : %d entries%n", unique.size());
        System.out.printf("Total   : %d distinct (simplified, trigger) pairs%n", entries.size());
    }

    // ── Compound-effect splitting ─────────────────────────────────────────────

    /**
     * Splits a compound effect body into atomic sub-clauses:
     *
     *   1. Bullet-point modal choices  "Choose one: • A • B"  → ["Choose one:", "A", "B"]
     *   2. Sequential ". Then, " chains → each part is a separate clause
     *   3. Simple "If X, do Y." splits → ["If X", "do Y."]
     *      (only when the if-clause itself contains no nested commas)
     *
     * Each returned string is trimmed but otherwise unchanged (raw text);
     * generaliseEffect() is called on each part individually.
     */
    static List<String> splitSubEffects(String text) {
        // 1. Bullet-point modal: split on "•"
        if (text.contains("•")) {
            List<String> parts = new ArrayList<>();
            for (String part : text.split("\\s*•\\s*")) {
                part = part.trim();
                if (!part.isEmpty()) parts.add(part);
            }
            return parts;
        }

        // 2. Sequential ". Then, " chain
        // Regex: lookbehind for sentence-end [.!?], optional whitespace, "Then, "
        List<String> thenParts = splitOnThen(text);
        if (thenParts.size() > 1) return thenParts;

        // 3. "If X, do Y." — split simple conditional clauses
        if (text.length() > 3 && text.substring(0, 3).equalsIgnoreCase("if ")) {
            List<String> ifParts = splitIfClause(text);
            if (ifParts.size() > 1) return ifParts;
        }

        return List.of(text);
    }

    /**
     * Splits text on ". Then, " or "; then, " sequential markers.
     * "Then, " is stripped from the start of each follow-up clause.
     */
    private static List<String> splitOnThen(String text) {
        // Split on sentence-terminal + whitespace + "Then, "
        String[] raw = text.split("(?<=[.!?;])\\s+[Tt]hen,\\s+");
        if (raw.length == 1) return List.of(text);
        List<String> result = new ArrayList<>();
        for (String part : raw) {
            String t = part.trim();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }

    /**
     * Splits "If X, do Y." into ["If X", "do Y."].
     * Only splits when the if-clause (X) contains no nested commas, to avoid
     * incorrectly splitting complex conditions like
     * "If your Leader has the [X] type, and you have no other [Y]...".
     */
    private static List<String> splitIfClause(String text) {
        int start     = 3;  // skip "If " (case already checked by caller)
        int commaIdx  = text.indexOf(", ", start);
        if (commaIdx < 0) return List.of(text);

        String ifClause     = text.substring(start, commaIdx);
        String consequence  = text.substring(commaIdx + 2).trim();

        // Don't split if the if-clause itself contains commas (nested condition)
        if (ifClause.contains(",")) return List.of(text);
        // Don't split if the consequence is empty or trivially short
        if (consequence.length() < 4) return List.of(text);

        return List.of(
            text.substring(0, commaIdx).trim(),  // "If X"
            consequence                           // "do Y."
        );
    }

    // ── Effect classification ─────────────────────────────────────────────────

    /**
     * Returns the engine EffectType name for a given sub-effect clause by
     * delegating to EffectParser. Trigger-based special categories (Counter,
     * Keyword, Passive) are handled before the parser is invoked.
     * Falls back to "OTHER" for effects the parser does not yet recognise.
     */
    private static String classifyEffectWithParser(String text, String trigger) {
        if ("Counter".equals(trigger)) return "COUNTER";
        if ("Keyword".equals(trigger)) return "KEYWORD";
        if ("Passive".equals(trigger)) return "PASSIVE";

        // Strip leading modal prefix ("• " or "/ ") so the parser sees the action text.
        String core = text.replaceFirst("^[/•]\\s*", "").trim();

        List<ParsedEffect> effects = EffectParser.parse(core);
        if (!effects.isEmpty() && effects.get(0).effectType() != null) {
            return effects.get(0).effectType();
        }
        return "OTHER";
    }

    // ── Effect generalisation ─────────────────────────────────────────────────

    static String generalizeEffect(String text) {
        // 1. {TypeName} → {TYPE_REF}
        text = P_TYPE_BRACE.matcher(text).replaceAll("{TYPE_REF}");

        // 2. "TypeName" → "[TYPE_REF]"
        text = P_TYPE_QUOTE.matcher(text).replaceAll("\"[TYPE_REF]\"");

        // 3. [BracketToken] → [CARD_REF], but preserve trigger/keyword labels
        {
            Matcher m = Pattern.compile("\\[([^\\]]+)\\]").matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String inner = m.group(1).trim();
                if (TRIGGER_LABELS.contains(inner)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                } else {
                    m.appendReplacement(sb, "[CARD_REF]");
                }
            }
            m.appendTail(sb);
            text = sb.toString();
        }

        // 4. Strip embedded trigger labels that follow "/" or "•" modal separators.
        //    E.g. "/ [On K.O.] K.O. up to..." → "/ K.O. up to..."
        //    The "/" stays; the trigger bracket is informational metadata already
        //    captured in the outer ability's trigger field.
        text = stripModalTriggerLabels(text);

        // 5. DON!! -N → DON!! -[N]
        text = P_DON_MINUS.matcher(text).replaceAll("DON!! -[N]");

        // 6. Power numbers ≥ 4 digits (±) → [N]
        text = P_POWER_NUM.matcher(text).replaceAll("[N]");

        // 7. DON!! xN → DON!! x[N]
        text = P_DON_X.matcher(text).replaceAll("DON!! x[N]");

        // 8. Remaining standalone integers → [N]
        text = P_NUMBER.matcher(text).replaceAll("[N]");

        // 9. "your opponent's" → [CARD_REF]'s
        text = P_YOUR_OPP.matcher(text).replaceAll("[CARD_REF]'s");

        // 10. "your" (possessive) → [CARD_REF]'s
        text = P_YOUR.matcher(text).replaceAll("[CARD_REF]'s");

        return text;
    }

    /**
     * Strips trigger/keyword bracket labels that immediately follow a "/" or "•"
     * modal separator.  The separator itself is kept.
     *
     * Examples:
     *   "/ [On K.O.] K.O. up to..."  →  "/ K.O. up to..."
     *   "• [Trigger] Play this card." →  "• Play this card."
     */
    private static String stripModalTriggerLabels(String text) {
        for (String label : TRIGGER_LABELS) {
            // After "/"
            text = text.replace("/ [" + label + "] ", "/ ");
            text = text.replace("/ [" + label + "]",  "/ ");
            // After "•"
            text = text.replace("• [" + label + "] ", "• ");
            text = text.replace("• [" + label + "]",  "• ");
        }
        // Collapse any double spaces introduced by the stripping
        return text.replaceAll("  +", " ").trim();
    }

    // ── CSV writing ───────────────────────────────────────────────────────────

    private static void writeCsv(String path, List<EffectEntry> entries) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("simplified_description,explanation,example,effect_type,trigger");
            for (EffectEntry e : entries) {
                String explanation = EXPLANATIONS.getOrDefault(e.effectType, "");
                pw.printf("%s,%s,%s,%s,%s%n",
                    csvQuote(e.simplified),
                    csvQuote(explanation),
                    csvQuote(e.example),
                    csvQuote(e.effectType),
                    csvQuote(e.trigger));
            }
        }
    }

    private static String csvQuote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
