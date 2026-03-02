package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Generates {@code csv/card_checklist.csv} as part of the CardCompiler pipeline.
 *
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
 * </ul>
 *
 * <p>Called automatically at the end of {@link CardCompiler#main(String[])} after
 * all JSON output is written. Can also be run standalone on already-compiled data:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="tools.CardChecklistCsvGenerator"
 * </pre>
 */
public class CardChecklistCsvGenerator {

    private static final String COMPILED_DIR = "src/main/resources/compiled/data/";
    private static final String OUTPUT_PATH  = "csv/card_checklist.csv";
    private static final String[] CATEGORIES = {"sets", "decks", "promos"};

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
     * Writes {@code csv/card_checklist.csv} from a collection of compiled card
     * ObjectNodes. The caller is responsible for deduplication by
     * {@code card_set_id}; first-seen entry wins when the same id appears across
     * multiple categories.
     *
     * <p>Rows are sorted alphabetically by {@code card_set_id} so sets, decks,
     * and promos are grouped naturally by their set-code prefix.
     *
     * @param cards compiled card ObjectNodes, one per unique {@code card_set_id}
     * @throws IOException if the output file or its parent directory cannot be created
     */
    static void generate(Collection<ObjectNode> cards) throws IOException {
        new File("csv/").mkdirs();

        List<ObjectNode> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparing(n -> n.path("card_set_id").asText()));

        int overrideCount = 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_PATH))) {
            pw.println("card_name,card_set_id,card_text,generic_card_text,unknown_tokens,needs_override");

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

                pw.printf("%s,%s,%s,%s,%s,%s%n",
                        csvQuote(name),
                        csvQuote(id),
                        csvQuote(cardText),
                        csvQuote(genericText),
                        csvQuote(unknownStr),
                        csvQuote(needsOverride ? "true" : "false"));
            }
        }

        System.out.printf("Checklist: %d cards (%d need override) → %s%n",
                sorted.size(), overrideCount, OUTPUT_PATH);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
