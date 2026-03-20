package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates {@code csv/card_checklist.csv} and {@code csv/token_checklist.csv}
 * as part of the CardCompiler pipeline.
 *
 * <h3>card_checklist.csv</h3>
 * <p>One row per unique card, deduplicated by {@code card_set_id} across all
 * categories (sets, decks, promos). Cards that share the same {@code card_set_id}
 * and {@code card_text} are treated as one entry; first-seen wins.
 *
 * <p>Columns:
 * <ul>
 *   <li>{@code card_name}         — display name</li>
 *   <li>{@code card_set_id}       — primary key (e.g. {@code "OP01-001"})</li>
 *   <li>{@code card_text}         — raw card text (disclaimer/errata boilerplate stripped)</li>
 *   <li>{@code generic_card_text} — generalised form using the same rules as
 *       {@code effect_templates_generic.csv}: numbers → {@code [N]},
 *       type/card refs → {@code {TYPE_REF}}/{@code [CARD_REF]}, etc.</li>
 *   <li>{@code unknown_tokens}    — pipe-separated words the EffectLexer could not
 *       classify; empty if the card was fully tokenised</li>
 *   <li>{@code needs_override}    — {@code "true"} if any unknown tokens remain;
 *       {@code "false"} otherwise</li>
 *   <li>{@code tokens}            — pipe-separated TokenType names found in this
 *       card's effect text, sorted by {@link TokenType} declaration order</li>
 * </ul>
 *
 * <h3>token_checklist.csv</h3>
 * <p>One row per {@link TokenType} value, sorted by declaration order.
 *
 * <p>Columns:
 * <ul>
 *   <li>{@code token_type}   — enum name (e.g. {@code VERB})</li>
 *   <li>{@code description}  — plain-English description of what the token represents</li>
 *   <li>{@code example}      — a real effect-body snippet from card data in which
 *       the first occurrence of the matched phrase is wrapped in {@code **...**}</li>
 * </ul>
 *
 * <p>Called automatically at the end of {@link CardCompiler#main(String[])} after
 * all JSON output is written. Can also be run standalone on already-compiled data:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="tools.CardChecklistCsvGenerator"
 * </pre>
 */
public class CardChecklistCsvGenerator {

    private static final String COMPILED_DIR      = "src/main/resources/compiled/data/";
    private static final String OUTPUT_PATH       = "csv/card_checklist.csv";
    private static final String TOKEN_OUTPUT_DIR  = "csv/tokens/";
    private static final String[] CATEGORIES = {"sets", "decks", "promos"};

    /** Plain-English descriptions for each TokenType, keyed by declaration order. */
    private static final Map<TokenType, String> TOKEN_DESCRIPTIONS;
    static {
        Map<TokenType, String> m = new EnumMap<>(TokenType.class);
        m.put(TokenType.VERB,        "Action word that drives the effect (e.g. draw, rest, K.O.)");
        m.put(TokenType.QUANTITY,    "Numeric or symbolic count (e.g. 1, 2, X, up to N)");
        m.put(TokenType.PLAYER,      "Reference to a player or possessive (e.g. you, your opponent's)");
        m.put(TokenType.CARD_TYPE,   "Card category (e.g. Character, Event, DON!!)");
        m.put(TokenType.FILTER,      "Constraint on target selection (e.g. with a cost of 4 or less)");
        m.put(TokenType.ZONE,        "Named game zone (e.g. hand, deck, life area)");
        m.put(TokenType.DURATION,    "Temporal scope of an effect (e.g. during this turn)");
        m.put(TokenType.CONDITION,   "Prerequisite gating the effect (e.g. if you do, once per turn)");
        m.put(TokenType.CONNECTOR,   "Clause separator linking multiple effects (e.g. and, then)");
        m.put(TokenType.DESTINATION, "Directional preposition indicating card movement (e.g. from, to)");
        m.put(TokenType.STAT,        "Standalone game stat noun (e.g. power, cost)");
        m.put(TokenType.SKIP,        "Grammatical glue with no game-logic value (e.g. the, a, of)");
        m.put(TokenType.MODIFIER,    "Semantic qualifier for effect optionality (e.g. may, must)");
        m.put(TokenType.UNKNOWN,     "Unrecognized phrase — not in the EffectTables phrase map");
        TOKEN_DESCRIPTIONS = Collections.unmodifiableMap(m);
    }

    // ── Standalone entry point ────────────────────────────────────────────────

    /**
     * Standalone runner: reads compiled JSON across all three categories,
     * deduplicates by {@code card_set_id} (first-seen wins), and calls
     * {@link #generate(Collection)}.
     *
     * @param args unused
     * @throws Exception if any JSON file cannot be read or the output cannot be written
     */
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, ObjectNode> allCards = new LinkedHashMap<>();

        for (String category : CATEGORIES) {
            File dir = Path.of(COMPILED_DIR, category).toFile();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) continue;

            for (File file : files) {
                JsonNode root = mapper.readTree(file);
                if (!root.isArray()) continue;
                for (JsonNode node : root) {
                    String id = node.path("card_set_id").textValue();
                    if (id == null || id.isBlank()) continue;
                    allCards.putIfAbsent(id, (ObjectNode) node);
                }
            }
        }

        generate(allCards.values());
    }

    // ── Core generation ───────────────────────────────────────────────────────

    /**
     * Writes {@code csv/card_checklist.csv} and {@code csv/token_checklist.csv}
     * from a collection of compiled card ObjectNodes. The caller is responsible
     * for deduplication by {@code card_set_id}; first-seen entry wins when the
     * same id appears across multiple categories.
     *
     * <p>Rows in card_checklist are sorted alphabetically by {@code card_set_id}.
     * Rows in token_checklist follow {@link TokenType} declaration order.
     *
     * @param cards compiled card ObjectNodes, one per unique {@code card_set_id}
     * @throws IOException if either output file or its parent directory cannot be created
     */
    static void generate(Collection<ObjectNode> cards) throws IOException {
        new File("csv/").mkdirs();

        List<ObjectNode> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparing(n -> n.path("card_set_id").asText()));

        // Collect one example effect snippet per TokenType (first-seen wins)
        Map<TokenType, String> tokenExamples = new EnumMap<>(TokenType.class);

        int overrideCount = 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_PATH))) {
            pw.println("card_name,card_set_id,card_text,generic_card_text,unknown_tokens,needs_override,tokens");

            for (ObjectNode card : sorted) {
                String name    = card.path("card_name").asText();
                String id      = card.path("card_set_id").asText();
                String rawText = card.path("card_text").asText();

                String cardText    = stripDisclaimer(rawText);
                String genericText = EffectTemplateCsvGenerator.generalizeEffect(cardText);

                List<String> unknowns = collectUnknownTokens(cardText);
                String unknownStr     = String.join(" | ", unknowns);
                boolean needsOverride = !unknowns.isEmpty();
                if (needsOverride) overrideCount++;

                String tokensStr = collectTokenTypes(cardText, tokenExamples);

                pw.printf("%s,%s,%s,%s,%s,%s,%s%n",
                        csvQuote(name),
                        csvQuote(id),
                        csvQuote(cardText),
                        csvQuote(genericText),
                        csvQuote(unknownStr),
                        csvQuote(needsOverride ? "true" : "false"),
                        csvQuote(tokensStr));
            }
        }

        System.out.printf("Checklist: %d cards (%d need override) → %s%n",
                sorted.size(), overrideCount, OUTPUT_PATH);

        // ── csv/tokens/{TokenType}.csv ────────────────────────────────────────
        // Build phrases-by-type map in matching-priority order (longest first)
        Map<TokenType, List<String>> phrasesByType = new EnumMap<>(TokenType.class);
        for (TokenType tt : TokenType.values()) phrasesByType.put(tt, new ArrayList<>());
        for (String phrase : EffectTables.SORTED_PHRASES) {
            TokenType tt = EffectTables.PHRASES.get(phrase);
            if (tt != null) phrasesByType.get(tt).add(phrase);
        }
        // Append regex patterns for FILTER (numeric cost/power filters)
        for (Pattern pat : EffectTables.FILTER_PATTERNS) {
            phrasesByType.get(TokenType.FILTER).add("[pattern] " + pat.pattern());
        }

        new File(TOKEN_OUTPUT_DIR).mkdirs();
        for (TokenType tt : TokenType.values()) {
            String path = TOKEN_OUTPUT_DIR + tt.name() + ".csv";
            try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
                pw.println("phrase");
                for (String phrase : phrasesByType.getOrDefault(tt, List.of())) {
                    pw.println(csvQuote(phrase));
                }
            }
        }

        System.out.printf("Token checklist: %d CSVs → %s%n",
                TokenType.values().length, TOKEN_OUTPUT_DIR);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Lexes all ability effect bodies from {@code cardText}, returns a
     * pipe-separated string of distinct {@link TokenType} names found (sorted by
     * enum declaration order), and populates {@code exampleCollector} with the
     * first real-card example seen for each new token type.
     *
     * <p>The example stored in {@code exampleCollector} is the normalized
     * (lowercase) effect snippet with the first occurrence of the matched raw
     * phrase wrapped in {@code **...**}.
     *
     * @param cardText        raw card text before trigger-header stripping
     * @param exampleCollector mutable map to populate; existing entries are not overwritten
     * @return pipe-separated token type names, or {@code ""} if the card has no abilities
     */
    private static String collectTokenTypes(String cardText,
                                            Map<TokenType, String> exampleCollector) {
        if (cardText == null || cardText.isBlank()) return "";

        Set<TokenType> found = new LinkedHashSet<>();
        for (ParsedAbility pa : Parser.parse(cardText)) {
            String effectText = pa.effect();
            if (effectText == null || effectText.isBlank()) continue;
            String normalized = effectText.toLowerCase().replace("k.o.", "ko");
            for (Token t : EffectLexer.parseEffect(effectText)) {
                found.add(t.type());
                if (!exampleCollector.containsKey(t.type()) && !t.raw().isBlank()) {
                    String bolded = normalized.replaceFirst(
                            Pattern.quote(t.raw()),
                            "**" + t.raw() + "**");
                    exampleCollector.put(t.type(), bolded);
                }
            }
        }

        return found.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(" | "));
    }

    /**
     * Strips disclaimer / errata boilerplate sometimes appended to reprinted cards.
     * Handles two observed patterns:
     * <ul>
     *   <li>{@code "Disclaimer: ..."} / {@code "DISCLAIMER: ..."}</li>
     *   <li>{@code "This card has been officially errata'd."}</li>
     * </ul>
     *
     * @param text raw card text from the JSON field
     * @return trimmed text with the disclaimer suffix removed; original text if
     *         no disclaimer is present; empty string if {@code text} is null or blank
     */
    private static String stripDisclaimer(String text) {
        if (text == null || text.isBlank()) return "";
        String lower = text.toLowerCase();
        int idx = lower.indexOf("disclaimer:");
        if (idx < 0) idx = lower.indexOf("this card has been officially errata");
        return (idx >= 0) ? text.substring(0, idx).trim() : text;
    }

    /**
     * Lexes all ability effect bodies extracted from {@code cardText} and returns
     * the distinct UNKNOWN token raw values in encounter order.
     *
     * <p>Trigger headers and activation costs are stripped by {@link Parser} before
     * lexing, preventing false positives from bracket labels such as
     * {@code "Activate"}, {@code "Once"}, or {@code "Main"}.
     *
     * @param cardText raw card text before trigger-header stripping
     * @return distinct unrecognised words in encounter order; empty list if all
     *         tokens in every ability body were recognised by the EffectLexer
     */
    private static List<String> collectUnknownTokens(String cardText) {
        if (cardText == null || cardText.isBlank()) return Collections.emptyList();

        Set<String> seen = new LinkedHashSet<>();
        for (ParsedAbility pa : Parser.parse(cardText)) {
            String effectText = pa.effect();
            if (effectText == null || effectText.isBlank()) continue;
            for (Token t : EffectLexer.parseEffect(effectText)) {
                if (t.type() == TokenType.UNKNOWN) seen.add(t.raw());
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Wraps {@code s} in double-quotes and escapes any internal double-quotes as
     * {@code ""} per RFC 4180.
     *
     * @param s field value; {@code null} is treated as an empty string
     * @return properly quoted CSV field
     */
    private static String csvQuote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
