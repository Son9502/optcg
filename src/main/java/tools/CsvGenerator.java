package tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates card_names_subtypes.csv from card JSON data.
 *
 * Reads compiled data first (CardCompiler output); if compiled is empty,
 * falls back to raw scraped data. Applies two name-cleaning passes before
 * deduplicating:
 *
 *   1. Strip all (...) variant suffixes  — e.g. "(Reprint)", "(Jolly Roger Foil)", "(033)"
 *   2. Strip trailing " - SETID" suffixes — e.g. " - OP14-023", " - P-070"
 *
 * Deduplicates on (cleaned_name, card_type). When the same (name, type) pair
 * has different sub_types across printings, the longest (most complete) string
 * is kept.
 *
 * Usage: run after CardCompiler to keep the CSV in sync with the latest data.
 *   mvn exec:java -Dexec.mainClass="tools.CsvGenerator"
 */
public class CsvGenerator {

    private static final String COMPILED_DIR = "src/main/resources/compiled/data/";
    private static final String RAW_DIR      = "src/main/resources/raw/data/";
    private static final String OUTPUT_PATH  = "card_names_subtypes.csv";
    private static final String[] CATEGORIES = {"sets", "decks", "promos"};

    // Strip all (...) blocks (variant labels, foil types, card numbers, event packs, etc.)
    private static final Pattern PARENS   = Pattern.compile("\\s*\\([^)]*\\)");
    // Strip trailing " - SETID" (e.g. " - OP14-023", " - P-070", " - PRB02-010", " - EB04-015")
    private static final Pattern DASH_ID  = Pattern.compile("\\s+-\\s+[A-Z][A-Z0-9]*-\\d+$");
    private static final Pattern MULTISPC = Pattern.compile("  +");

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Prefer compiled data; fall back to raw if compiled directories are empty
        String sourceDir = chooseSourceDir();
        if (sourceDir == null) {
            System.err.println("No card data found in compiled/ or raw/ directories.");
            System.err.println("Run CardCompiler (or CardScraper) first.");
            System.exit(1);
        }
        System.out.println("Reading from: " + sourceDir);

        // key = "cleaned_name_lower\0card_type_lower"
        // value = [displayName, displayType, bestSubTypes]
        Map<String, String[]> groups = new LinkedHashMap<>();
        int totalEntries = 0;

        for (String category : CATEGORIES) {
            File dir = Path.of(sourceDir, category).toFile();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) continue;

            for (File file : files) {
                JsonNode root = mapper.readTree(file);
                if (!root.isArray()) continue;

                for (JsonNode node : root) {
                    String name     = node.path("card_name").textValue();
                    String cardType = node.path("card_type").textValue();
                    String subTypes = node.path("sub_types").asText();

                    if (name == null || name.isBlank()) continue;
                    if (cardType == null || cardType.isBlank()) continue;
                    if (subTypes.equalsIgnoreCase("null")) subTypes = "";

                    String cleaned = cleanName(name);
                    if (cleaned.isBlank()) continue;

                    String key = cleaned.toLowerCase() + "\0" + cardType.toLowerCase();

                    if (!groups.containsKey(key)) {
                        groups.put(key, new String[]{cleaned, cardType, subTypes});
                    } else {
                        // Keep the longest sub_types string (most complete across printings)
                        if (subTypes.length() > groups.get(key)[2].length()) {
                            groups.get(key)[2] = subTypes;
                        }
                    }
                    totalEntries++;
                }
            }
        }

        if (groups.isEmpty()) {
            System.err.println("No valid card entries found. Check that the data directories contain JSON files.");
            System.exit(1);
        }

        // Sort case-insensitively by card_name
        List<String[]> rows = new ArrayList<>(groups.values());
        rows.sort(Comparator.comparing(r -> r[0].toLowerCase()));

        // Write CSV with proper quoting
        try (PrintWriter pw = new PrintWriter(new FileWriter(OUTPUT_PATH))) {
            pw.println("card_name,card_type,sub_types");
            for (String[] row : rows) {
                pw.printf("%s,%s,%s%n",
                        escapeCsv(row[0]),
                        escapeCsv(row[1]),
                        escapeCsv(row[2]));
            }
        }

        System.out.printf("Read    : %d total card entries%n", totalEntries);
        System.out.printf("Unique  : %d (name, type) pairs%n", rows.size());
        System.out.printf("Written : %s%n", OUTPUT_PATH);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the source directory to read from.
     * Prefers compiled/ if it contains any JSON files; otherwise falls back to raw/.
     */
    private static String chooseSourceDir() {
        if (hasJsonFiles(COMPILED_DIR)) return COMPILED_DIR;
        if (hasJsonFiles(RAW_DIR))      return RAW_DIR;
        return null;
    }

    private static boolean hasJsonFiles(String base) {
        for (String category : CATEGORIES) {
            File dir = Path.of(base, category).toFile();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (files != null && files.length > 0) return true;
        }
        return false;
    }

    private static String cleanName(String name) {
        name = PARENS.matcher(name).replaceAll("");   // strip (...)
        name = DASH_ID.matcher(name).replaceAll("");  // strip " - SETID"
        return MULTISPC.matcher(name).replaceAll(" ").trim();
    }

    /** Wraps a CSV field in quotes if it contains a comma, quote, or newline. */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
