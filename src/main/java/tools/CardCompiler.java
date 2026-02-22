package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Compiles raw scraped JSON into clean, engine-ready compiled JSON.
 *
 * For each card it:
 *   - Keeps:    card_set_id, set_id, card_name, card_text, set_name, rarity,
 *               card_type, attribute, card_color, card_cost, card_power,
 *               life, counter_amount, market_price
 *   - Removes:  date_scraped, card_image, card_image_id, inventory_price, sub_types
 *   - Adds:     abilities[] — structured ability list from Parser
 *   - Deduplicates by card_set_id, keeping the entry with the lowest market_price
 *     (base version, not the SP/Parallel)
 *
 * Usage: run main() once after CardScraper to populate compiled/data/.
 */
public class CardCompiler {

    private static final String RAW_DIR      = "src/main/resources/raw/data/";
    private static final String COMPILED_DIR = "src/main/resources/compiled/data/";
    private static final String[] CATEGORIES = {"sets", "decks", "promos"};

    private static final Set<String> KEEP_FIELDS = Set.of(
            "card_set_id",    // kept as database primary key (not displayed in-game)
            "card_name", "card_text", "card_color", "card_type",
            "life", "card_cost", "card_power",
            "sub_types", "counter_amount", "attribute",
            "card_image_id", "card_image");

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        int totalCards = 0;
        int totalFiles = 0;

        for (String category : CATEGORIES) {
            File rawDir = Path.of(RAW_DIR, category).toFile();
            File compiledDir = Path.of(COMPILED_DIR, category).toFile();
            compiledDir.mkdirs();

            File[] files = rawDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) {
                System.out.println("[" + category + "] No files found, skipping.");
                continue;
            }

            for (File file : files) {
                JsonNode root = mapper.readTree(file);
                if (!root.isArray()) {
                    System.out.println("Skipping non-array file: " + file.getName());
                    continue;
                }

                // Deduplicate by card_set_id (keep lowest market_price = base version)
                Map<String, ObjectNode> deduped = new LinkedHashMap<>();
                for (JsonNode node : root) {
                    String id = node.path("card_set_id").textValue();
                    if (id == null || id.isBlank()) continue;

                    ObjectNode compiled = buildCompiledCard(mapper, node);
                    if (!deduped.containsKey(id)) {
                        deduped.put(id, compiled);
                    } else {
                        // Keep base version (lower market_price)
                        double existing = deduped.get(id).path("market_price").asDouble(Double.MAX_VALUE);
                        double incoming = compiled.path("market_price").asDouble(Double.MAX_VALUE);
                        if (incoming < existing) {
                            deduped.put(id, compiled);
                        }
                    }
                }

                // Sort by card_set_id
                List<ObjectNode> sorted = new ArrayList<>(deduped.values());
                sorted.sort(Comparator.comparing(n -> n.path("card_set_id").asText()));

                ArrayNode output = mapper.createArrayNode();
                sorted.forEach(output::add);

                File outFile = new File(compiledDir, file.getName());
                mapper.writerWithDefaultPrettyPrinter().writeValue(outFile, output);
                System.out.printf("[%s] %s -> %d cards%n", category, file.getName(), sorted.size());
                totalCards += sorted.size();
                totalFiles++;
            }
        }

        System.out.printf("%nDone. %d files, %d total cards written to %s%n",
                totalFiles, totalCards, COMPILED_DIR);
    }

    /**
     * Builds a compiled card ObjectNode from a raw API node.
     * Keeps only engine-relevant fields and appends a parsed abilities array.
     */
    static ObjectNode buildCompiledCard(ObjectMapper mapper, JsonNode raw) {
        ObjectNode out = mapper.createObjectNode();

        for (String field : KEEP_FIELDS) {
            JsonNode value = raw.get(field);
            if (value != null && !value.isNull()) {
                out.set(field, value);
            } else {
                out.putNull(field);
            }
        }

        String cardText = raw.path("card_text").textValue();
        List<ParsedAbility> abilities = Parser.parse(cardText);
        ArrayNode abilitiesNode = mapper.createArrayNode();
        for (ParsedAbility ability : abilities) {
            ObjectNode abilityNode = mapper.createObjectNode();
            putNullable(abilityNode, "trigger",   ability.trigger());
            putNullable(abilityNode, "condition", ability.condition());
            putNullable(abilityNode, "cost",      ability.cost());
            putNullable(abilityNode, "character", ability.character());
            putNullable(abilityNode, "effect",    ability.effect());
            abilitiesNode.add(abilityNode);
        }
        out.set("abilities", abilitiesNode);

        return out;
    }

    private static void putNullable(ObjectNode node, String key, String value) {
        if (value != null) node.put(key, value);
        else node.putNull(key);
    }
}
