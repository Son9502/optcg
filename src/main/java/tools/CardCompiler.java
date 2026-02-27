package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Keeps only engine-relevant fields and appends a parsed abilities array
     * in AbilityData-format JSON (typed enum names, not raw strings).
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
        List<ParsedAbility> parsed = Parser.parse(cardText);
        ArrayNode abilitiesNode = mapper.createArrayNode();
        for (ParsedAbility pa : parsed) {
            ObjectNode node = toAbilityJson(mapper, pa);
            if (node != null) abilitiesNode.add(node);
        }
        out.set("abilities", abilitiesNode);

        return out;
    }

    // ── ParsedAbility → AbilityData-format JSON ───────────────────────────────

    /**
     * Converts a ParsedAbility to an ObjectNode whose field names match the
     * @JsonProperty annotations on AbilityData. Returns null for unknown triggers
     * so the caller can skip them.
     */
    private static ObjectNode toAbilityJson(ObjectMapper mapper, ParsedAbility pa) {
        String triggerName = mapTrigger(pa.trigger());
        if (triggerName == null) return null;

        ObjectNode node = mapper.createObjectNode();
        node.put("triggerType",   triggerName);
        node.put("donRequired",   parseDonRequired(pa.condition()));
        node.put("isOncePerTurn", isOncePerTurn(pa.condition()));

        ObjectNode costNode = parseCostNode(mapper, pa.cost());
        if (costNode != null) node.set("cost", costNode);
        else                  node.putNull("cost");

        String conditionName = mapCondition(pa.condition());
        if (conditionName != null) node.put("condition", conditionName);
        else                       node.putNull("condition");

        if ("KEYWORD".equals(triggerName)) {
            String kwName = mapKeyword(pa.effect());
            if (kwName != null) node.put("keyword", kwName);
            else                node.putNull("keyword");
        } else {
            node.putNull("keyword");
        }

        node.set("effects", mapper.createArrayNode()); // filled in Step 4
        return node;
    }

    private static String mapTrigger(String trigger) {
        if (trigger == null) return null;
        return switch (trigger.trim()) {
            case "On Play"                           -> "ON_PLAY";
            case "When Attacking"                    -> "WHEN_ATTACKING";
            case "Activate:Main", "Activate: Main"   -> "ACTIVATED_MAIN";
            case "Main"                              -> "MAIN";
            case "Counter"                           -> "COUNTER";
            case "Trigger"                           -> "TRIGGER";
            case "Keyword"                           -> "KEYWORD";
            case "End of Turn", "End of Your Turn"   -> "END_OF_YOUR_TURN";
            case "End of Opponent's Turn",
                 "End of Your Opponent's Turn"       -> "END_OF_OPPONENTS_TURN";
            case "On Your Opponent's Attack"         -> "OPPONENTS_ATTACK";
            case "Passive"                           -> "PASSIVE";
            case "On K.O."                           -> "ON_KO";
            case "On Block"                          -> "ON_BLOCK";
            default                                  -> null;
        };
    }

    private static String mapKeyword(String effect) {
        if (effect == null) return null;
        return switch (effect.trim()) {
            case "Rush"                              -> "RUSH";
            case "Blocker"                           -> "BLOCKER";
            case "Double Attack"                     -> "DOUBLE_ATTACK";
            case "Banish"                            -> "BANISH";
            case "Trigger"                           -> "TRIGGER";
            case "Unblockable"                       -> "UNBLOCKABLE";
            case "Rush: Character", "Rush:Character" -> "RUSH_CHARACTER";
            default                                  -> null;
        };
    }

    private static String mapCondition(String condition) {
        if (condition == null || condition.isBlank()) return null;
        String c = condition.trim();
        if (c.contains("Your Turn") && !c.contains("Opponent")) return "YOUR_TURN";
        if (c.contains("Opponent's Turn"))                       return "OPPONENTS_TURN";
        if (c.contains("Life"))                                  return "IF_LIFE_COUNT";
        if (c.contains("hand") || c.contains("Hand"))           return "IF_HAND_COUNT";
        if (c.contains("Character") && !c.contains("Leader"))   return "IF_CHARACTER_COUNT";
        if (c.contains("rested") || c.contains("Rested"))       return "IF_RESTED";
        if (c.contains("Leader") && c.contains("type"))         return "IF_LEADER_TYPE";
        return null;
    }

    private static boolean isOncePerTurn(String condition) {
        return condition != null && condition.contains("Once Per Turn");
    }

    private static final Pattern DON_REQUIRED_PATTERN =
            Pattern.compile("DON!!\\s*x\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static int parseDonRequired(String condition) {
        if (condition == null) return 0;
        Matcher m = DON_REQUIRED_PATTERN.matcher(condition);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static final Pattern DON_COST_PATTERN =
            Pattern.compile("\\((\\d+)\\)\\s*DON", Pattern.CASE_INSENSITIVE);
    private static final Pattern DON_MINUS_PATTERN =
            Pattern.compile("DON!!\\s*[-\u2212](\\d+)", Pattern.CASE_INSENSITIVE);

    private static ObjectNode parseCostNode(ObjectMapper mapper, String cost) {
        if (cost == null || cost.isBlank()) return null;

        Matcher m = DON_COST_PATTERN.matcher(cost);
        if (m.find()) {
            ObjectNode n = mapper.createObjectNode();
            n.put("type", "DON");
            n.put("amount", Integer.parseInt(m.group(1)));
            return n;
        }

        m = DON_MINUS_PATTERN.matcher(cost);
        if (m.find()) {
            ObjectNode n = mapper.createObjectNode();
            n.put("type", "DON_MINUS");
            n.put("amount", Integer.parseInt(m.group(1)));
            return n;
        }

        if (cost.toLowerCase().contains("rest this")) {
            ObjectNode n = mapper.createObjectNode();
            n.put("type", "REST");
            n.put("amount", 0);
            return n;
        }

        return null;
    }
}
