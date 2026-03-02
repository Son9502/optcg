package tools;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs every example from effect_templates_generic.csv and
 * effect_templates_unique.csv through EffectLexer.parseEffect() and reports
 * lexer coverage.
 *
 * Output to console:
 *   - total rows, fully-tokenized count, needs-manual count, coverage %
 *   - top 15 most-frequent unknown tokens per file
 *
 * Output files (written to csv/):
 *   - manual_overrides_generic.csv — generic rows that had unknown tokens
 *   - manual_overrides_unique.csv  — unique rows that had unknown tokens
 *
 * Run from the project root, e.g.:
 *   mvn exec:java -Dexec.mainClass="tools.LexerCoverageReport"
 */
public class LexerCoverageReport {

    private static final String CSV_DIR = "csv/";

    public static void main(String[] args) throws IOException {
        System.out.println("=== EffectLexer Coverage Report ===\n");

        List<String[]> genericManual = new ArrayList<>();
        List<String[]> uniqueManual  = new ArrayList<>();

        // unknown-token frequency maps per file
        Map<String, Integer> genericFreq = new LinkedHashMap<>();
        Map<String, Integer> uniqueFreq  = new LinkedHashMap<>();

        scan("GENERIC", Path.of(CSV_DIR + "effect_templates_generic.csv"), genericManual, genericFreq);
        scan("UNIQUE",  Path.of(CSV_DIR + "effect_templates_unique.csv"),  uniqueManual,  uniqueFreq);

        writeManualOverrides(CSV_DIR + "manual_overrides_generic.csv", genericManual);
        writeManualOverrides(CSV_DIR + "manual_overrides_unique.csv",  uniqueManual);

        System.out.println("\nTop unknown tokens [GENERIC]:");
        printTopUnknowns(genericFreq, 15);

        System.out.println("\nTop unknown tokens [UNIQUE]:");
        printTopUnknowns(uniqueFreq, 15);

        System.out.println("\nManual override files written to csv/:");
        System.out.printf("  csv/manual_overrides_generic.csv  (%d rows)%n", genericManual.size());
        System.out.printf("  csv/manual_overrides_unique.csv   (%d rows)%n", uniqueManual.size());
    }

    // ── Core scan ─────────────────────────────────────────────────────────────

    private static void scan(String label,
                             Path csv,
                             List<String[]> needsManual,
                             Map<String, Integer> freq) throws IOException {
        List<String[]> rows = parseCsv(csv);
        if (rows.size() < 2) { System.out.println(label + ": no data"); return; }

        String[] header     = rows.get(0);
        int exampleIdx      = colIndex(header, "example");
        int effectTypeIdx   = colIndex(header, "effect_type");
        int triggerIdx      = colIndex(header, "trigger");

        if (exampleIdx < 0) {
            System.out.println(label + ": 'example' column not found");
            return;
        }

        int total = 0, fullyOk = 0;

        for (int r = 1; r < rows.size(); r++) {
            String[] row    = rows.get(r);
            String example  = safeGet(row, exampleIdx).trim();
            if (example.isEmpty()) continue;
            total++;

            List<Token> tokens = EffectLexer.parseEffect(example);
            List<String> rowUnknowns = tokens.stream()
                    .filter(t -> t.type() == TokenType.UNKNOWN)
                    .map(Token::raw)
                    .collect(Collectors.toList());

            if (rowUnknowns.isEmpty()) {
                fullyOk++;
            } else {
                // Update frequency map
                for (String u : rowUnknowns) {
                    freq.merge(u, 1, Integer::sum);
                }

                needsManual.add(new String[]{
                    safeGet(row, effectTypeIdx),
                    safeGet(row, triggerIdx),
                    example,
                    String.join(" | ", rowUnknowns)
                });
            }
        }

        double pct = total > 0 ? 100.0 * fullyOk / total : 0.0;
        System.out.printf("[%s] %d total | %d fully tokenized | %d need manual | %.1f%% coverage%n",
                label, total, fullyOk, total - fullyOk, pct);
    }

    // ── Output helpers ────────────────────────────────────────────────────────

    private static void writeManualOverrides(String filename, List<String[]> rows)
            throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("effect_type,trigger,example,unknown_tokens");
            for (String[] row : rows) {
                pw.printf("%s,%s,%s,%s%n",
                        csvQuote(row[0]), csvQuote(row[1]),
                        csvQuote(row[2]), csvQuote(row[3]));
            }
        }
    }

    private static void printTopUnknowns(Map<String, Integer> freq, int topN) {
        freq.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(topN)
            .forEach(e -> System.out.printf("  %-20s %d occurrences%n", e.getKey(), e.getValue()));
    }

    // ── CSV parser (RFC-4180, single-line fields) ─────────────────────────────

    private static List<String[]> parseCsv(Path path) throws IOException {
        List<String[]> result = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            result.add(parseRow(line));
        }
        return result;
    }

    private static String[] parseRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur   = new StringBuilder();
        boolean inQuote     = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++; // skip escaped ""
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static int colIndex(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String safeGet(String[] row, int idx) {
        return (idx >= 0 && idx < row.length) ? row[idx] : "";
    }

    /** Wraps a field in double-quotes and escapes any internal double-quotes. */
    private static String csvQuote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
