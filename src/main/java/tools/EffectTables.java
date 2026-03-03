package tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared data tables for the EffectLexer and EffectParser.
 *
 * PHRASES     — maps every known literal phrase to its TokenType.
 * SORTED_PHRASES / LIST_PHRASES — derived sorted lists used by EffectLexer for
 *               longest-first phrase matching (do not modify at runtime).
 * FILTER_PATTERNS — compiled regex Patterns for numeric/variable FILTER clauses
 *               that cannot be expressed as literal phrase keys (e.g. "with a
 *               cost of N or less" where N is variable).
 *
 * Adding new entries: add them to the appropriate private static helper method
 * below. The duplicate-key wrapper will throw IllegalStateException immediately
 * at class-load time if the same phrase appears twice, so collisions are caught
 * during development rather than silently overwriting the first entry.
 */
public final class EffectTables {

    private EffectTables() {}

    // ── Primary phrase → TokenType table ─────────────────────────────────────
    public static final Map<String, TokenType> PHRASES;

    // ── Derived lists built from PHRASES (for EffectLexer's scan loop) ───────
    public static final List<String>   SORTED_PHRASES; // sorted by length desc
    public static final List<String[]> LIST_PHRASES;   // parallel pre-split arrays

    // ── Regex patterns for numeric/variable FILTER clauses ───────────────────
    public static final List<Pattern> FILTER_PATTERNS;

    static {
        Map<String, TokenType> p = new HashMap<>();

        addVerbs(p);
        addQuantities(p);
        addPlayers(p);
        addCardTypes(p);
        addFilters(p);
        addZones(p);
        addDestinations(p);
        addStats(p);
        addDurations(p);
        addConditions(p);
        addConnectors(p);
        addModifiers(p);
        addSkips(p);

        PHRASES = Collections.unmodifiableMap(p);

        // ── Derived lists (sorted longest-first for EffectLexer) ─────────────
        List<String> sorted = new ArrayList<>(PHRASES.keySet());
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));
        SORTED_PHRASES = Collections.unmodifiableList(sorted);

        List<String[]> splitList = new ArrayList<>(sorted.size());
        for (String phrase : sorted) {
            splitList.add(phrase.split(" +"));
        }
        LIST_PHRASES = Collections.unmodifiableList(splitList);

        FILTER_PATTERNS = buildFilterPatterns();
    }

    // ── Duplicate-key guard ───────────────────────────────────────────────────
    /**
     * Inserts a phrase into the map and throws immediately if the phrase was
     * already registered. This catches copy-paste duplicates at class-load time
     * rather than silently overwriting the first mapping.
     */
    private static void put(Map<String, TokenType> map, String phrase, TokenType type) {
        TokenType existing = map.put(phrase, type);
        if (existing != null) {
            throw new IllegalStateException(
                "Duplicate phrase in EffectTables: \"" + phrase
                + "\" (was " + existing + ", attempted " + type + ")");
        }
    }

    // ── VERB ──────────────────────────────────────────────────────────────────
    private static void addVerbs(Map<String, TokenType> p) {
        // "choose one" must precede the single-word "choose" entry.
        put(p, "choose one",  TokenType.VERB); // MODAL trigger
        put(p, "draw",        TokenType.VERB);
        put(p, "draws",       TokenType.VERB); // 3rd-person
        put(p, "rest",        TokenType.VERB);
        put(p, "ko",          TokenType.VERB); // input pre-normalised: "k.o." → "ko"
        put(p, "ko'd",        TokenType.VERB); // "K.O.'d" → normalised → "ko'd"
        put(p, "trash",       TokenType.VERB);
        put(p, "trashes",     TokenType.VERB); // 3rd-person
        put(p, "trashed",     TokenType.VERB); // past-tense: "when this card is trashed"
        put(p, "return",      TokenType.VERB);
        put(p, "returns",     TokenType.VERB); // 3rd-person
        put(p, "play",        TokenType.VERB);
        put(p, "plays",       TokenType.VERB); // 3rd-person
        put(p, "played",      TokenType.VERB); // past-tense: "if played from hand"
        put(p, "add",         TokenType.VERB);
        put(p, "adds",        TokenType.VERB); // 3rd-person
        put(p, "give",        TokenType.VERB);
        put(p, "set",         TokenType.VERB);
        put(p, "place",       TokenType.VERB);
        put(p, "places",      TokenType.VERB); // 3rd-person
        put(p, "placed",      TokenType.VERB); // past-tense
        put(p, "look",        TokenType.VERB);
        put(p, "reveal",      TokenType.VERB);
        put(p, "reveals",     TokenType.VERB); // 3rd-person
        put(p, "negate",      TokenType.VERB);
        put(p, "negated",     TokenType.VERB); // past-tense: "this effect is negated"
        put(p, "gain",        TokenType.VERB);
        put(p, "gains",       TokenType.VERB);
        put(p, "activate",    TokenType.VERB);
        put(p, "activates",   TokenType.VERB); // 3rd-person
        put(p, "activated",   TokenType.VERB); // past-tense: "when this effect is activated"
        put(p, "cannot",      TokenType.VERB); // prevention clause opener
        put(p, "become",      TokenType.VERB); // "become active"
        put(p, "becomes",     TokenType.VERB); // "becomes the same as"
        put(p, "choose",      TokenType.VERB); // standalone; "choose one" (2-word) caught above
        put(p, "chooses",     TokenType.VERB); // 3rd-person
        put(p, "select",      TokenType.VERB); // "select up to N cards from your deck"
        put(p, "shuffle",     TokenType.VERB); // "shuffle your deck"
        put(p, "shuffles",    TokenType.VERB); // 3rd-person
        put(p, "attacks",     TokenType.VERB); // 3rd-person: trigger "when this character attacks"
        put(p, "battles",     TokenType.VERB); // 3rd-person: "when this character battles"
        put(p, "reduced",     TokenType.VERB); // past-tense: "cost is reduced by N"
        put(p, "change",      TokenType.VERB); // "change N cards from your hand"
        put(p, "take",        TokenType.VERB); // "take N cards from your deck"
        put(p, "deal",        TokenType.VERB); // "deal N damage"
        put(p, "swap",        TokenType.VERB); // "swap this card with a card in your hand"
        put(p, "win",         TokenType.VERB); // "win the game"
    }

    // ── QUANTITY ──────────────────────────────────────────────────────────────
    private static void addQuantities(Map<String, TokenType> p) {
        // Bare digits are caught by the digit-check in the scan loop, not here.
        put(p, "up to",     TokenType.QUANTITY); // 2 words; must precede any lone "up"
        put(p, "all",       TokenType.QUANTITY);
        put(p, "each",      TokenType.QUANTITY); // "draw 1 card for each Character"
        put(p, "every",     TokenType.QUANTITY); // "for every N characters"
        put(p, "more",      TokenType.QUANTITY); // comparative upper bound; filter form caught by FILTER_PATTERNS
        put(p, "less",      TokenType.QUANTITY); // comparative lower bound; filter form caught by FILTER_PATTERNS
        put(p, "number",    TokenType.QUANTITY); // "the number of DON!! cards on your field"
        put(p, "total",     TokenType.QUANTITY); // "N or more in total", "a total of N"
        put(p, "no",        TokenType.QUANTITY); // "if you have no characters" → quantity of 0
        put(p, "none",      TokenType.QUANTITY); // "none of your characters"
        put(p, "one",       TokenType.QUANTITY); // "one of your characters"; "choose one" (2-word) caught above
        put(p, "additional",TokenType.QUANTITY); // "an additional +N power"
        put(p, "extra",     TokenType.QUANTITY); // "an extra card", "N extra power"
    }

    // ── PLAYER ────────────────────────────────────────────────────────────────
    private static void addPlayers(Map<String, TokenType> p) {
        put(p, "your opponent's", TokenType.PLAYER); // 2 words; must precede "your"
        put(p, "your opponent",   TokenType.PLAYER); // no apostrophe-s variant
        put(p, "your",            TokenType.PLAYER);
        put(p, "you",             TokenType.PLAYER); // nominative; "your" handles possessive
        put(p, "owner's",         TokenType.PLAYER); // "to the owner's hand"
    }

    // ── CARD_TYPE ─────────────────────────────────────────────────────────────
    private static void addCardTypes(Map<String, TokenType> p) {
        // Multi-word entries must precede any single-word alternatives they share a prefix with.
        // State-qualified DON!! entries (5 words) precede bare "don!! card/cards" (2 words)
        // so "rested don!! card" is matched as a single CARD_TYPE token rather than
        // three separate tokens (FILTER "rested" + CARD_TYPE "don!!" + CARD_TYPE "card").
        put(p, "rested don!! cards",  TokenType.CARD_TYPE); // 3 words; → RESTED_DON
        put(p, "rested don!! card",   TokenType.CARD_TYPE); // 3 words; → RESTED_DON
        put(p, "active don!! cards",  TokenType.CARD_TYPE); // 3 words; → ACTIVE_DON
        put(p, "active don!! card",   TokenType.CARD_TYPE); // 3 words; → ACTIVE_DON
        put(p, "leader or characters", TokenType.CARD_TYPE); // 3 words; compound target (plural)
        put(p, "leader or character",  TokenType.CARD_TYPE); // 3 words; compound target (singular)
        put(p, "don!! cards",         TokenType.CARD_TYPE); // 2 words; must precede "don!!"
        put(p, "don!! card",          TokenType.CARD_TYPE);
        put(p, "event cards",         TokenType.CARD_TYPE);
        put(p, "event card",          TokenType.CARD_TYPE);
        put(p, "stage cards",         TokenType.CARD_TYPE);
        put(p, "stage card",          TokenType.CARD_TYPE);
        put(p, "don!!",               TokenType.CARD_TYPE);
        put(p, "leader",              TokenType.CARD_TYPE);
        put(p, "leaders",             TokenType.CARD_TYPE); // plural edge-case
        put(p, "characters",          TokenType.CARD_TYPE);
        put(p, "character",           TokenType.CARD_TYPE);
        put(p, "cards",               TokenType.CARD_TYPE);
        put(p, "card",                TokenType.CARD_TYPE);
        put(p, "event",               TokenType.CARD_TYPE); // standalone "Event"
        put(p, "events",              TokenType.CARD_TYPE); // plural not covered by "event cards"
        put(p, "stages",              TokenType.CARD_TYPE); // plural not covered by "stage cards"
        put(p, "leader's",            TokenType.CARD_TYPE); // possessive: "your Leader's power"
        put(p, "card's",              TokenType.CARD_TYPE); // possessive: "this card's power"
        put(p, "character's",         TokenType.CARD_TYPE); // possessive: "this character's power"
    }

    // ── FILTER ────────────────────────────────────────────────────────────────
    private static void addFilters(Map<String, TokenType> p) {
        // Keyword, subtype, and attribute filters are handled by bracket detection
        // in EffectLexer ([Keyword], {Type}, (Attribute), "TypeName").
        // Numeric filters (cost, power) are handled by FILTER_PATTERNS.
        // Multi-word entries first.
        put(p, "base power", TokenType.FILTER); // "base power" (2-word) must precede lone "base"
        // State / orientation
        put(p, "rested",     TokenType.FILTER);
        put(p, "active",     TokenType.FILTER);
        put(p, "face-up",    TokenType.FILTER); // "place it face-up"
        put(p, "face-down",  TokenType.FILTER); // "place it face-down"
        put(p, "selected",   TokenType.FILTER); // "the selected card"
        put(p, "revealed",   TokenType.FILTER); // "the revealed card"
        put(p, "chosen",     TokenType.FILTER); // "the chosen card"
        // Colors
        put(p, "red",        TokenType.FILTER);
        put(p, "green",      TokenType.FILTER);
        put(p, "blue",       TokenType.FILTER);
        put(p, "purple",     TokenType.FILTER);
        put(p, "yellow",     TokenType.FILTER);
        put(p, "black",      TokenType.FILTER);
        put(p, "multicolored",TokenType.FILTER);
        // Positional
        put(p, "top",        TokenType.FILTER); // "top of a zone"
        put(p, "bottom",     TokenType.FILTER); // "bottom of a zone"
        // Type qualifier
        put(p, "type",       TokenType.FILTER); // type-qualifier noun after a {bracket} filter
        put(p, "base",       TokenType.FILTER); // "base cost", standalone; "base power" tried first
        // Lowercase keyword names ("gains the Blocker keyword" constructions)
        put(p, "blocker",    TokenType.FILTER);
        put(p, "double",     TokenType.FILTER); // Double Attack
        put(p, "banish",     TokenType.FILTER);
        put(p, "rush",       TokenType.FILTER);
    }

    // ── ZONE ──────────────────────────────────────────────────────────────────
    private static void addZones(Map<String, TokenType> p) {
        // 2-word entries must precede any 1-word alternatives they share a prefix with.
        put(p, "don!! deck",  TokenType.ZONE); // must precede "don!!" (CARD_TYPE)
        put(p, "life area",   TokenType.ZONE);
        put(p, "life cards",  TokenType.ZONE);
        put(p, "cost area",   TokenType.ZONE); // where attached DON!! cards live
        put(p, "hand",        TokenType.ZONE);
        put(p, "deck",        TokenType.ZONE);
        // "trash" intentionally omitted — it already lives as VERB.
        // The parser resolves DESTINATION + PLAYER + VERB("trash") as a zone reference.
        put(p, "field",       TokenType.ZONE);
        put(p, "life",        TokenType.ZONE); // standalone; residual after "life area"/"life cards" match first
        put(p, "counter",     TokenType.ZONE); // standalone Counter zone/keyword area
    }

    // ── DESTINATION (directional / positional prepositions) ───────────────────
    private static void addDestinations(Map<String, TokenType> p) {
        put(p, "from", TokenType.DESTINATION);
        put(p, "to",   TokenType.DESTINATION);
        put(p, "into", TokenType.DESTINATION);
        put(p, "on",   TokenType.DESTINATION);
        put(p, "in",   TokenType.DESTINATION); // "in your hand", "in your life area"
        put(p, "at",   TokenType.DESTINATION); // "at the top of your deck"
    }

    // ── STAT ──────────────────────────────────────────────────────────────────
    private static void addStats(Map<String, TokenType> p) {
        put(p, "power", TokenType.STAT); // "+2000 power"; filter form caught by FILTER_PATTERNS
        put(p, "cost",  TokenType.STAT); // "-3 cost";    filter form caught by FILTER_PATTERNS
    }

    // ── DURATION ──────────────────────────────────────────────────────────────
    private static void addDurations(Map<String, TokenType> p) {
        // Longest phrases first (all start with "until the" or "during this").
        put(p, "until the end of your opponent's next end phase", TokenType.DURATION); // → OPPONENTS_NEXT_TURN
        put(p, "until the end of your opponent's next turn",      TokenType.DURATION); // → OPPONENTS_NEXT_TURN
        put(p, "until the start of your next turn",               TokenType.DURATION); // → OPPONENTS_NEXT_TURN (most common)
        put(p, "until the end of your next turn",                 TokenType.DURATION); // → OPPONENTS_NEXT_TURN
        put(p, "until the end of this turn",                      TokenType.DURATION); // → THIS_TURN
        put(p, "until end of this turn",                          TokenType.DURATION); // → THIS_TURN
        put(p, "during this battle",                              TokenType.DURATION); // → THIS_BATTLE
        put(p, "during this turn",                                TokenType.DURATION); // → THIS_TURN
    }

    // ── CONDITION ─────────────────────────────────────────────────────────────
    private static void addConditions(Map<String, TokenType> p) {
        // Numeric conditions (IF_LIFE_COUNT, IF_DON_COUNT, etc.) require regex
        // and will be added to a CONDITION_PATTERNS list when EffectParser is built.
        put(p, "if you do",    TokenType.CONDITION); // IF_YOU_DO; 3 words, precedes "if"
        put(p, "once per turn",TokenType.CONDITION); // frequency condition; must precede "once"
        put(p, "if",           TokenType.CONDITION); // general condition opener
        put(p, "when",         TokenType.CONDITION); // "when X, do Y" — same conditional role as "if"
    }

    // ── CONNECTOR ─────────────────────────────────────────────────────────────
    private static void addConnectors(Map<String, TokenType> p) {
        put(p, "and",  TokenType.CONNECTOR);
        put(p, "then", TokenType.CONNECTOR);
        put(p, "also", TokenType.CONNECTOR);
        put(p, "or",   TokenType.CONNECTOR); // standalone "A or B"; "N or less/more" is FILTER_PATTERN
        put(p, "/",    TokenType.CONNECTOR); // trigger-notation separator; kept inside clauses by splitClauses
        put(p, "•",    TokenType.CONNECTOR); // modal choice bullet; splits "Choose one: • A • B" into clauses
    }

    // ── MODIFIER ──────────────────────────────────────────────────────────────
    private static void addModifiers(Map<String, TokenType> p) {
        put(p, "may",  TokenType.MODIFIER); // "you may draw 1 card" → optional effect
        put(p, "must", TokenType.MODIFIER); // "must attack" → forced action
    }

    // ── SKIP ──────────────────────────────────────────────────────────────────
    private static void addSkips(Map<String, TokenType> p) {
        // Articles
        put(p, "the",   TokenType.SKIP);
        put(p, "a",     TokenType.SKIP);
        put(p, "an",    TokenType.SKIP);
        // Prepositions (directional ones are DESTINATION; these are non-directional glue)
        put(p, "of",    TokenType.SKIP);
        put(p, "as",    TokenType.SKIP); // "set it as active" → VERB SKIP FILTER
        put(p, "with",  TokenType.SKIP); // standalone; "with N power or less" caught by FILTER_PATTERNS
        put(p, "for",   TokenType.SKIP); // "draw 1 card for each character"
        put(p, "than",  TokenType.SKIP); // "more than", "less than" comparisons
        put(p, "any",   TokenType.SKIP); // "in any order"
        // Linking verbs
        put(p, "is",    TokenType.SKIP);
        put(p, "are",   TokenType.SKIP);
        put(p, "be",    TokenType.SKIP);
        put(p, "been",  TokenType.SKIP);
        put(p, "was",   TokenType.SKIP);
        put(p, "were",  TokenType.SKIP);
        // Auxiliary have
        put(p, "has",   TokenType.SKIP);
        put(p, "have",  TokenType.SKIP);
        put(p, "had",   TokenType.SKIP);
        // (may / must moved to addModifiers — they carry semantic weight)
        // Auxiliary do
        put(p, "do",    TokenType.SKIP);
        put(p, "does",  TokenType.SKIP);
        put(p, "did",   TokenType.SKIP);
        put(p, "don't", TokenType.SKIP); // contraction of "do not"
        // Pronouns (referent clear from context)
        put(p, "it",    TokenType.SKIP);
        put(p, "this",  TokenType.SKIP);
        put(p, "they",  TokenType.SKIP);
        put(p, "them",  TokenType.SKIP);
        put(p, "their", TokenType.SKIP);
        // Relative pronouns
        put(p, "that",  TokenType.SKIP);
        put(p, "which", TokenType.SKIP);
        // Common glue words between structured tokens
        put(p, "other",     TokenType.SKIP); // "other than", "other Characters"
        put(p, "order",     TokenType.SKIP); // "in any order"
        put(p, "by",        TokenType.SKIP); // "increase by N", "K.O.'d by effects"
        put(p, "equal",     TokenType.SKIP); // "equal to"
        put(p, "including", TokenType.SKIP); // "including yourself"
        put(p, "next",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        put(p, "turn",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        put(p, "attack",    TokenType.SKIP); // "cannot attack"; "during an attack"
        put(p, "effect",    TokenType.SKIP); // "by effects", "this card's effect"
        put(p, "effects",   TokenType.SKIP);
        put(p, "includes",  TokenType.SKIP); // "Leader's type includes X"
        // Auxiliary / modal verbs used in prevention and conditional clauses
        put(p, "can",     TokenType.SKIP); // "can attack", "can only be K.O.'d"
        put(p, "will",    TokenType.SKIP); // "will be K.O.'d"
        put(p, "would",   TokenType.SKIP); // "would be K.O.'d"
        put(p, "not",     TokenType.SKIP); // "cannot" is VERB; standalone "not" is glue
        put(p, "instead", TokenType.SKIP); // "instead of attacking", replacement clauses
        // Past-participle / residual words
        put(p, "removed",  TokenType.SKIP); // "removed from the game"
        put(p, "returned", TokenType.SKIP); // "returned to your hand"
        put(p, "given",    TokenType.SKIP); // "given –N cost" — past-participle object
        put(p, "there",    TokenType.SKIP); // "if there are N or more"
        put(p, "rules",    TokenType.SKIP); // "according to the rules"
        put(p, "end",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        put(p, "battle",   TokenType.SKIP); // residual after "during this battle" matches first
        put(p, "attribute",TokenType.SKIP); // "Leader's attribute"
        put(p, "according",TokenType.SKIP); // "according to the rules"
        put(p, "using",    TokenType.SKIP); // "using this card's effect"
        put(p, "back",     TokenType.SKIP); // "put back", "goes back to"
        put(p, "only",     TokenType.SKIP); // "can only be K.O.'d", "only once per turn"
        put(p, "own",      TokenType.SKIP); // "your own effects"
        put(p, "game",     TokenType.SKIP); // "removed from the game"
        put(p, "same",     TokenType.SKIP); // "the same as your opponent's Leader"
        put(p, "without",  TokenType.SKIP); // "without a Counter", "without paying its cost"
        put(p, "damage",   TokenType.SKIP); // game-concept noun; not a zone or verb in OP TCG
        // Game-phase words
        put(p, "refresh",  TokenType.SKIP); // "Refresh Phase"
        put(p, "phase",    TokenType.SKIP);
        // Final batch
        put(p, "being",    TokenType.SKIP); // "being K.O.'d", "being attacked"
        put(p, "until",    TokenType.SKIP); // residual after full DURATION phrases match first
        put(p, "currently",TokenType.SKIP); // "currently on the field"
        put(p, "except",   TokenType.SKIP); // "except for your Leader"
        put(p, "after",    TokenType.SKIP); // "after this battle", "after your opponent attacks"
        put(p, "target",   TokenType.SKIP); // "the target of the attack"
        put(p, "different",TokenType.SKIP); // "a different color than"
        put(p, "include",  TokenType.SKIP); // "if your Leader's type does not include"
        put(p, "least",    TokenType.SKIP); // "at least N"
        put(p, "start",    TokenType.SKIP); // residual after "until the start of your next turn"
        put(p, "under",    TokenType.SKIP); // "under this card"
        put(p, "between",  TokenType.SKIP); // "choose between A and B"
    }

    // ── FILTER_PATTERNS ───────────────────────────────────────────────────────
    private static List<Pattern> buildFilterPatterns() {
        List<Pattern> fp = new ArrayList<>();
        // Longer / more specific patterns first to prevent partial matches.
        // Cost filters
        fp.add(Pattern.compile("with a cost of (\\d+) or less"));
        fp.add(Pattern.compile("with a cost of (\\d+) or more"));
        fp.add(Pattern.compile("with a cost of up to (\\d+)"));     // synonym for "or less"
        fp.add(Pattern.compile("with a base cost of (\\d+) or less"));
        // Power filters — syntax: "with N power or less"
        fp.add(Pattern.compile("with (\\d+) power or less"));
        fp.add(Pattern.compile("with (\\d+) power or more"));
        fp.add(Pattern.compile("with power of up to (\\d+)"));      // synonym for "or less"
        return Collections.unmodifiableList(fp);
    }
}
