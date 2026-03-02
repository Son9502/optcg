package tools;

import java.util.*;
import java.util.regex.*;

/**
 * Rule-based parser for One Piece TCG card text.
 *
 * Card text follows a consistent structure:
 *   [header brackets...] (optional activation cost) body text
 *
 * Each card may have multiple ability blocks separated by newlines or
 * inline double-spaces (e.g. "[Blocker] (explanation)  [On Play] ...").
 *
 * Output: a list of ParsedAbility, one per ability block.
 */
public class Parser {

    // -------------------------------------------------------------------------
    // Lookup tables
    // -------------------------------------------------------------------------

    /** Bracket token text → canonical trigger label */
    private static final Map<String, String> TRIGGER_MAP;

    /** Bracket token text → timing condition label */
    private static final Map<String, String> TIMING_MAP;

    /** Standalone keyword abilities (entire block = just this keyword) */
    private static final Set<String> KEYWORD_SET = Set.of(
            "Rush", "Blocker", "Double Attack", "Banish",
            "Rush: Character",  // §10-1-6: can attack Characters on turn played
            "Unblockable");     // §10-1-7: opponent cannot activate Blocker against this card

    static {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("On Play",                    "On Play");
        t.put("When Attacking",             "When Attacking");
        t.put("On K.O.",                    "On K.O.");
        t.put("On Block",                   "On Block");
        t.put("Activate:Main",              "Activate:Main");
        t.put("Activate: Main",             "Activate:Main");
        t.put("Main",                       "Main");
        t.put("Counter",                    "Counter");
        t.put("Trigger",                    "Trigger");
        t.put("End of Your Turn",                "End of Your Turn");
        t.put("End of Your Opponent's Turn",     "End of Your Opponent's Turn");
        t.put("On Your Opponent's Attack",       "On Your Opponent's Attack");
        TRIGGER_MAP = Collections.unmodifiableMap(t);

        Map<String, String> c = new LinkedHashMap<>();
        c.put("Your Turn",       "Your Turn");
        c.put("Opponent's Turn", "Opponent's Turn");
        c.put("Once Per Turn",   "Once Per Turn");
        TIMING_MAP = Collections.unmodifiableMap(c);
    }

    // Regex: one bracket token at the start of a string
    private static final Pattern BRACKET_TOKEN = Pattern.compile("^\\[([^\\]]+)\\]");
    // Regex: any bracket token anywhere in a string (used for embedded-trigger scanning)
    private static final Pattern BRACKET_ANYWHERE = Pattern.compile("\\[([^\\]]+)\\]");
    // Regex: parenthetical explanation "(text)" — used to skip Blocker/Rush descriptions
    private static final Pattern PAREN_BLOCK    = Pattern.compile("^\\(([^)]+)\\)");
    // Regex: DON!! xN inside a bracket token
    private static final Pattern DON_REQ        = Pattern.compile("^DON!! x(\\d+)$");
    // Regex: activation cost "(N)" optionally followed by a description paren
    private static final Pattern ACTIVATION_COST = Pattern.compile(
            "^\\((\\d+)\\)\\s*(?:\\([^)]*\\))?\\s*:?\\s*");
    // Regex: optional cost "You may X:" — captures X
    private static final Pattern OPTIONAL_COST  = Pattern.compile(
            "You may ([^:]+):\\s*");
    // Regex: "if ..." condition clauses (captures the clause up to comma or sentence end)
    private static final Pattern IF_CONDITION   = Pattern.compile(
            "(?:^|[,;.\\s])(?:if|If) ([^,.\n]+)");
    // Regex: target phrase — "up to N of your/opponent's Leader/Characters..."
    //        or "this Character", "all of your Characters"
    private static final Pattern TARGET_PHRASE  = Pattern.compile(
            "(?:up to \\d+ (?:of )?)?(?:your opponent's|all of your|your|this) " +
            "(?:Leader|Characters?)(?:[^,.]*)?",
            Pattern.CASE_INSENSITIVE);
    // Regex: [CardName] or [Type] references inside effect text
    //        We want to capture refs that look like proper nouns — start with capital
    private static final Pattern CARD_REF       = Pattern.compile("\\[([A-Z][^\\]]+)\\]");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses card text into a list of ParsedAbility objects.
     * Returns an empty list for null, blank, or "NULL" text.
     */
    public static List<ParsedAbility> parse(String cardText) {
        if (cardText == null || cardText.isBlank() || cardText.equalsIgnoreCase("null")) {
            return Collections.emptyList();
        }

        List<String> blocks = splitIntoBlocks(cardText);
        List<ParsedAbility> abilities = new ArrayList<>();
        for (String block : blocks) {
            ParsedAbility ability = parseBlock(block.trim());
            if (ability != null) {
                abilities.add(ability);
            }
        }
        return abilities;
    }

    // -------------------------------------------------------------------------
    // Block splitting
    // -------------------------------------------------------------------------

    /**
     * Splits card text into individual ability blocks.
     *   Pass 1 — newlines.
     *   Pass 2 — two or more spaces before '[' (legacy inline multi-ability).
     *   Pass 3 — any [TriggerToken] that appears after a sentence boundary mid-line
     *             (handles single-space inline multi-ability, e.g.
     *              "[Activate:Main]… Draw 1 card. [When Attacking]… Give -1 cost.").
     */
    private static List<String> splitIntoBlocks(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("null")) continue;
            // Pass 2: double-space split
            String[] inline = line.split("  +(?=\\[)");
            for (String part : inline) {
                part = part.trim();
                if (!part.isEmpty()) splitOnEmbeddedTriggers(part, result);
            }
        }
        return result;
    }

    /**
     * Scans {@code block} for any [TriggerToken], keyword, or [DON!! xN] bracket
     * that appears AFTER a sentence boundary (period, !, ?) inside the body text.
     * Such a bracket signals the start of a second ability on the same line.
     * Adds one or more sub-blocks to {@code result}.
     *
     * Also handles the pattern "[Keyword] (explanation) [Trigger] body" — a keyword
     * followed by its parenthetical rule reminder, then a separate trigger ability.
     * Because the paren ends with ')' (not '.'), the afterSentence check would miss it;
     * instead we track whether we have seen a keyword bracket in the leading cluster
     * and split when the very next leading bracket is a trigger.
     *
     * Safe against card-reference brackets like "[Main]" in
     * "Activate this card's [Main] effect." because those appear mid-sentence,
     * not after a sentence terminator.
     */
    private static void splitOnEmbeddedTriggers(String block, List<String> result) {
        Matcher m = BRACKET_ANYWHERE.matcher(block);
        int leadingEnd = 0;             // end-index of the leading-bracket region (incl. paren)
        boolean sawKeyword = false;     // true after a keyword bracket + its paren were consumed
        List<Integer> splitPoints = new ArrayList<>();

        while (m.find()) {
            int start = m.start();

            // Skip any bracket whose position is already inside a consumed parenthetical.
            if (start < leadingEnd) continue;

            String inner = m.group(1).trim();
            String gap   = block.substring(leadingEnd, start);

            if (gap.isBlank()) {
                // Still inside the leading-bracket cluster.
                if (KEYWORD_SET.contains(inner)) {
                    // Keyword bracket: advance past it, then also skip the parenthetical
                    // explanation that immediately follows (e.g. "[Blocker] (After ...)").
                    leadingEnd = m.end();
                    sawKeyword = true;
                    // Skip optional whitespace + a single "(…)" block
                    int pos = leadingEnd;
                    while (pos < block.length() && Character.isWhitespace(block.charAt(pos))) pos++;
                    if (pos < block.length() && block.charAt(pos) == '(') {
                        int close = block.indexOf(')', pos);
                        if (close >= 0) leadingEnd = close + 1;
                    }
                } else if (sawKeyword && TRIGGER_MAP.containsKey(inner)) {
                    // A trigger bracket appearing right after a keyword+paren block
                    // starts a new, separate ability — record the split point.
                    splitPoints.add(start);
                    sawKeyword = false;
                    leadingEnd = m.end();
                } else {
                    // DON!! requirement, timing condition, or additional trigger header.
                    leadingEnd = m.end();
                }
            } else {
                // Body text has begun. Split here only if:
                //   (a) the text since the last leading bracket ends with a sentence terminator, AND
                //   (b) this bracket is a known ability-starter (trigger, keyword, or DON!! req)
                String gapTrimmed = gap.stripTrailing();
                boolean afterSentence = !gapTrimmed.isEmpty()
                        && (gapTrimmed.endsWith(".")
                        ||  gapTrimmed.endsWith("!")
                        ||  gapTrimmed.endsWith("?")
                        ||  gapTrimmed.endsWith(";"));

                if (afterSentence && (TRIGGER_MAP.containsKey(inner)
                        || KEYWORD_SET.contains(inner)
                        || DON_REQ.matcher(inner).matches())) {
                    splitPoints.add(start);
                }
            }
        }

        if (splitPoints.isEmpty()) {
            result.add(block);
            return;
        }

        int prev = 0;
        for (int sp : splitPoints) {
            String part = block.substring(prev, sp).trim();
            if (!part.isEmpty()) result.add(part);
            prev = sp;
        }
        String last = block.substring(prev).trim();
        if (!last.isEmpty()) result.add(last);
    }

    // -------------------------------------------------------------------------
    // Single block parsing
    // -------------------------------------------------------------------------

    private static ParsedAbility parseBlock(String block) {
        // --- Step 1: Extract all leading [token] brackets ---
        List<String> tokens = new ArrayList<>();
        String rest = block;

        while (rest.startsWith("[")) {
            Matcher m = BRACKET_TOKEN.matcher(rest);
            if (!m.find()) break;
            tokens.add(m.group(1).trim());
            rest = rest.substring(m.end()).trim();
            // Skip parenthetical explanation ONLY for keyword abilities.
            // e.g. [Blocker] (After your opponent declares an attack...)
            // Do NOT strip for [Activate:Main] etc., where (N) is the activation cost.
            String lastToken = tokens.get(tokens.size() - 1);
            if (KEYWORD_SET.contains(lastToken)) {
                Matcher pm = PAREN_BLOCK.matcher(rest);
                if (pm.find()) {
                    rest = rest.substring(pm.end()).trim();
                }
            }
        }

        // --- Step 2: Classify tokens ---
        String trigger = null;
        List<String> conditions = new ArrayList<>();
        int donRequired = 0;
        List<String> keywords = new ArrayList<>();

        for (String token : tokens) {
            if (TRIGGER_MAP.containsKey(token)) {
                trigger = TRIGGER_MAP.get(token);
            } else if (TIMING_MAP.containsKey(token)) {
                conditions.add(TIMING_MAP.get(token));
            } else {
                Matcher donMatcher = DON_REQ.matcher(token);
                if (donMatcher.matches()) {
                    // [DON!! xN] is a condition (§8-3-2-3): the card must have ≥N DON attached.
                    // It is NOT a cost — record as a condition, not in costParts.
                    donRequired = Integer.parseInt(donMatcher.group(1));
                    conditions.add("DON!! x" + donRequired);
                } else if (KEYWORD_SET.contains(token)) {
                    keywords.add(token);
                }
                // Bracket card/type references used as conditions, e.g. [Straw Hat Crew]
                // These appear in the effect text, not here — skip silently.
            }
        }

        // If only keywords found (no trigger, no body) → keyword ability
        if (!keywords.isEmpty() && rest.isEmpty() && trigger == null) {
            String kw = String.join(", ", keywords);
            return new ParsedAbility("Keyword", null, null, null, kw);
        }

        // If no tokens at all and no body → skip
        if (tokens.isEmpty() && rest.isEmpty()) return null;

        // --- Step 3: Extract activation cost "(N)" from body ---
        // Note: [DON!! xN] is already in conditions (added in Step 2 as per §8-3-2-3).
        // Activation cost is the explicit "(N)" parenthetical after [Activate:Main].
        List<String> costParts = new ArrayList<>();

        Matcher activMatcher = ACTIVATION_COST.matcher(rest);
        if (activMatcher.find()) {
            costParts.add("(" + activMatcher.group(1) + ") DON");
            rest = rest.substring(activMatcher.end()).trim();
        }

        // --- Step 4: Extract optional cost "You may X:" ---
        Matcher optMatcher = OPTIONAL_COST.matcher(rest);
        if (optMatcher.find()) {
            costParts.add("Optional: " + optMatcher.group(1).trim());
            rest = rest.substring(optMatcher.end()).trim();
        }

        // --- Step 5: Extract "if ..." condition clauses from body ---
        Matcher ifMatcher = IF_CONDITION.matcher(rest);
        while (ifMatcher.find()) {
            conditions.add("if " + ifMatcher.group(1).trim());
        }

        // --- Step 6: Extract character/target references ---
        String character = extractCharacter(rest);

        // --- Step 7: If trigger still null, infer ---
        if (trigger == null) {
            if (!keywords.isEmpty()) {
                trigger = "Keyword";
            } else if (!rest.isEmpty()) {
                trigger = "Passive";
            }
        }

        // --- Assemble ---
        String conditionStr = conditions.isEmpty() ? null : String.join("; ", conditions);
        String costStr      = costParts.isEmpty()  ? null : String.join(", ", costParts);
        String effectStr    = rest.isEmpty()        ? null : rest;

        // Append keywords into effect if they coexist with a real trigger
        if (!keywords.isEmpty() && effectStr != null) {
            effectStr = String.join(", ", keywords) + ". " + effectStr;
        } else if (!keywords.isEmpty()) {
            effectStr = String.join(", ", keywords);
        }

        return new ParsedAbility(trigger, conditionStr, costStr, character, effectStr);
    }

    // -------------------------------------------------------------------------
    // Character / target extraction
    // -------------------------------------------------------------------------

    private static String extractCharacter(String text) {
        List<String> refs = new ArrayList<>();

        // Primary: target phrase
        Matcher targetMatcher = TARGET_PHRASE.matcher(text);
        if (targetMatcher.find()) {
            refs.add(targetMatcher.group().trim());
        }

        // Secondary: [CardName] / [TypeName] bracket references
        Matcher cardRefMatcher = CARD_REF.matcher(text);
        while (cardRefMatcher.find()) {
            String ref = cardRefMatcher.group(1).trim();
            // Filter out keyword refs like [Rush], [Blocker] that appear in effect text
            if (!KEYWORD_SET.contains(ref) && !TRIGGER_MAP.containsKey(ref)) {
                refs.add(ref);
            }
        }

        return refs.isEmpty() ? null : String.join("; ", refs);
    }
}
