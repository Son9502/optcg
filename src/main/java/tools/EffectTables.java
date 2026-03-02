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
 * Ordering note: all puts into PHRASES must appear BEFORE the derived-list
 * block. The sort is by character length descending, which correlates with word
 * count and guarantees multi-word phrases are tried before shorter alternatives
 * that share the same opening word(s).
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

        // ── VERB ─────────────────────────────────────────────────────────────
        // "choose one" must precede any single-word entry that shares a prefix.
        p.put("choose one",  TokenType.VERB); // MODAL trigger
        p.put("draw",        TokenType.VERB);
        p.put("rest",        TokenType.VERB);
        p.put("ko",          TokenType.VERB); // input pre-normalised: "k.o." → "ko"
        p.put("trash",       TokenType.VERB);
        p.put("return",      TokenType.VERB);
        p.put("play",        TokenType.VERB);
        p.put("add",         TokenType.VERB);
        p.put("give",        TokenType.VERB);
        p.put("set",         TokenType.VERB);
        p.put("place",       TokenType.VERB);
        p.put("look",        TokenType.VERB);
        p.put("reveal",      TokenType.VERB);
        p.put("negate",      TokenType.VERB);
        p.put("gain",        TokenType.VERB);
        p.put("gains",       TokenType.VERB);
        p.put("activate",    TokenType.VERB);
        p.put("cannot",      TokenType.VERB); // prevention clause opener

        // ── QUANTITY ─────────────────────────────────────────────────────────
        // Bare digits are caught by the digit-check in the scan loop, not here.
        p.put("up to",   TokenType.QUANTITY); // 2 words; must precede any lone "up"
        p.put("all",     TokenType.QUANTITY);
        p.put("each",    TokenType.QUANTITY); // "draw 1 card for each Character"
        p.put("more",    TokenType.QUANTITY); // comparative upper bound; filter form caught by FILTER_PATTERNS
        p.put("less",    TokenType.QUANTITY); // comparative lower bound; filter form caught by FILTER_PATTERNS
        p.put("number",  TokenType.QUANTITY); // "the number of DON!! cards on your field"

        // ── PLAYER ───────────────────────────────────────────────────────────
        p.put("your opponent's", TokenType.PLAYER); // 2 words; must precede "your"
        p.put("your",            TokenType.PLAYER);
        p.put("your opponent",   TokenType.PLAYER); // no apostrophe-s variant
        p.put("you",             TokenType.PLAYER); // nominative; "your" handles possessive
        p.put("owner's",         TokenType.PLAYER); // "to the owner's hand"

        // ── CARD_TYPE ────────────────────────────────────────────────────────
        p.put("leader or character", TokenType.CARD_TYPE); // 3 words; compound target
        p.put("don!! cards",         TokenType.CARD_TYPE); // 2 words; must precede "don!!"
        p.put("don!! card",          TokenType.CARD_TYPE);
        p.put("event cards",         TokenType.CARD_TYPE);
        p.put("event card",          TokenType.CARD_TYPE);
        p.put("stage cards",         TokenType.CARD_TYPE);
        p.put("stage card",          TokenType.CARD_TYPE);
        p.put("don!!",               TokenType.CARD_TYPE);
        p.put("leader",              TokenType.CARD_TYPE);
        p.put("characters",          TokenType.CARD_TYPE);
        p.put("character",           TokenType.CARD_TYPE);
        p.put("cards",               TokenType.CARD_TYPE);
        p.put("card",                TokenType.CARD_TYPE);

        // ── FILTER (literal: state and color) ────────────────────────────────
        // Keyword, subtype, and attribute filters are handled by bracket
        // detection in EffectLexer ([Keyword], {Type}, (Attribute)).
        // Numeric filters (cost, power) are handled by FILTER_PATTERNS below.
        p.put("rested",  TokenType.FILTER);
        p.put("active",  TokenType.FILTER);
        p.put("red",     TokenType.FILTER);
        p.put("green",   TokenType.FILTER);
        p.put("blue",    TokenType.FILTER);
        p.put("purple",  TokenType.FILTER);
        p.put("yellow",  TokenType.FILTER);
        p.put("black",   TokenType.FILTER);
        p.put("multicolored",           TokenType.FILTER);
        p.put("base power",             TokenType.FILTER);
        p.put("top",    TokenType.FILTER); // positional: top of a zone
        p.put("bottom", TokenType.FILTER); // positional: bottom of a zone
        p.put("type",   TokenType.FILTER); // type-qualifier noun after a [bracket] filter

        // ── ZONE (atomic zone names only — DESTINATION carries the direction) ───
        // 2-word entries must precede any 1-word alternatives they share a prefix with.
        p.put("don!! deck",  TokenType.ZONE); // must precede "don!!" (CARD_TYPE)
        p.put("life area",   TokenType.ZONE);
        p.put("life cards",  TokenType.ZONE);
        p.put("cost area",   TokenType.ZONE); // where attached DON!! cards live
        p.put("hand",        TokenType.ZONE);
        p.put("deck",        TokenType.ZONE);
        // "trash" intentionally omitted — it already lives as VERB.
        // The parser resolves DESTINATION + PLAYER + VERB("trash") as a zone reference.
        p.put("field",       TokenType.ZONE);

        // ── DESTINATION (directional / positional prepositions) ──────────────
        p.put("from",  TokenType.DESTINATION);
        p.put("to",    TokenType.DESTINATION);
        p.put("into",  TokenType.DESTINATION);
        p.put("on",    TokenType.DESTINATION);
        p.put("in",    TokenType.DESTINATION); // "in your hand", "in your life area"
        p.put("at",    TokenType.DESTINATION); // "at the top of your deck"

        // ── STAT (game-stat nouns when used standalone after a QUANTITY) ──────
        p.put("power", TokenType.STAT); // "+2000 power"; filter form caught by FILTER_PATTERNS
        p.put("cost",  TokenType.STAT); // "-3 cost";    filter form caught by FILTER_PATTERNS

        // ── DURATION ─────────────────────────────────────────────────────────
        // Longest phrases first (all start with "until the" or "during this").
        p.put("until the end of your opponent's next end phase", TokenType.DURATION);
        p.put("until the end of your opponent's next turn",      TokenType.DURATION);
        p.put("until the start of your next turn",               TokenType.DURATION); // most common "next turn" form
        p.put("until the end of your next turn",                 TokenType.DURATION);
        p.put("until the end of this turn",                      TokenType.DURATION);
        p.put("until end of this turn",                          TokenType.DURATION);
        p.put("during this battle",                              TokenType.DURATION);
        p.put("during this turn",                                TokenType.DURATION);

        // ── CONDITION ────────────────────────────────────────────────────────
        // Numeric conditions (IF_LIFE_COUNT, IF_DON_COUNT, etc.) require regex
        // and will be added to a CONDITION_PATTERNS list when EffectParser is built.
        p.put("if you do", TokenType.CONDITION); // IF_YOU_DO; 3 words, precedes "if"
        p.put("if",        TokenType.CONDITION); // general condition opener
        p.put("when",      TokenType.CONDITION); // "when X, do Y" — same conditional role as "if"
        p.put("once per turn", TokenType.CONDITION); // TRIGGER condition; must follow "if"/"when"

        // ── CONNECTOR ────────────────────────────────────────────────────────
        p.put("and",  TokenType.CONNECTOR);
        p.put("then", TokenType.CONNECTOR);
        p.put("also", TokenType.CONNECTOR);
        p.put("or",   TokenType.CONNECTOR); // standalone "A or B"; "N or less/more" is FILTER_PATTERN
        p.put("/",    TokenType.CONNECTOR); // modal option separator

        // ── SKIP (grammatical glue — matched and emitted but discarded by parser) ─
        // Articles
        p.put("the",   TokenType.SKIP);
        p.put("a",     TokenType.SKIP);
        p.put("an",    TokenType.SKIP);
        // Prepositions (directional ones are DESTINATION; these are non-directional glue)
        p.put("of",    TokenType.SKIP);
        p.put("as",    TokenType.SKIP); // "set it as active" → VERB SKIP FILTER
        p.put("with",  TokenType.SKIP); // standalone; "with N power or less" caught by FILTER_PATTERNS
        p.put("for",   TokenType.SKIP); // "draw 1 card for each character"
        p.put("than",  TokenType.SKIP); // "more than", "less than" comparisons
        p.put("any",   TokenType.SKIP); // "in any order"
        // Linking verbs
        p.put("is",    TokenType.SKIP);
        p.put("are",   TokenType.SKIP);
        p.put("be",    TokenType.SKIP);
        p.put("been",  TokenType.SKIP);
        p.put("was",   TokenType.SKIP);
        p.put("were",  TokenType.SKIP);
        // Auxiliary have
        p.put("has",   TokenType.SKIP);
        p.put("have",  TokenType.SKIP);
        p.put("had",   TokenType.SKIP);
        // Modal
        p.put("may",   TokenType.SKIP);
        p.put("must",  TokenType.SKIP);
        // Auxiliary do
        p.put("do",    TokenType.SKIP);
        p.put("does",  TokenType.SKIP);
        p.put("did",   TokenType.SKIP);
        // Pronouns (referent clear from context)
        p.put("it",    TokenType.SKIP);
        p.put("this",  TokenType.SKIP);
        p.put("they",  TokenType.SKIP);
        p.put("them",  TokenType.SKIP);
        p.put("their", TokenType.SKIP);
        // Relative pronouns
        p.put("that",  TokenType.SKIP);
        p.put("which", TokenType.SKIP);
        // Common glue words that appear between structured tokens
        p.put("other",     TokenType.SKIP); // "other than", "other Characters"
        p.put("order",     TokenType.SKIP); // "in any order"
        p.put("by",        TokenType.SKIP); // "increase by N", "K.O.'d by effects"
        p.put("equal",     TokenType.SKIP); // "equal to"
        p.put("including", TokenType.SKIP); // "including yourself"
        p.put("next",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        p.put("turn",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        p.put("attack",    TokenType.SKIP); // "cannot attack"; "during an attack"
        p.put("effect",    TokenType.SKIP); // "by effects", "this card's effect"
        p.put("effects",   TokenType.SKIP);
        p.put("includes",  TokenType.SKIP); // "Leader's type includes X"
        // Auxiliary / modal verbs used in prevention and conditional clauses
        p.put("can",     TokenType.SKIP); // "can attack", "can only be K.O.'d"
        p.put("will",    TokenType.SKIP); // "will be K.O.'d"
        p.put("would",   TokenType.SKIP); // "would be K.O.'d"
        p.put("not",     TokenType.SKIP); // "cannot" is VERB; standalone "not" is glue
        p.put("instead", TokenType.SKIP); // "instead of attacking", replacement clauses
        // Past-participle / residual words
        p.put("removed",  TokenType.SKIP); // "removed from the game"
        p.put("returned", TokenType.SKIP); // "returned to your hand"
        p.put("given",    TokenType.SKIP); // "given –N cost" — past-participle object
        p.put("there",    TokenType.SKIP); // "if there are N or more"
        p.put("rules",    TokenType.SKIP); // "according to the rules"
        p.put("end",      TokenType.SKIP); // residual after DURATION multi-word phrases match first
        p.put("battle",   TokenType.SKIP); // residual after "during this battle" phrase matches first
        p.put("attribute",TokenType.SKIP); // "Leader's attribute"
        p.put("according",TokenType.SKIP); // "according to the rules"
        p.put("using",    TokenType.SKIP); // "using this card's effect"
        p.put("back",     TokenType.SKIP); // "put back", "goes back to"
        p.put("only",     TokenType.SKIP); // "can only be K.O.'d", "only once per turn"
        p.put("own",      TokenType.SKIP); // "your own effects"
        p.put("game",     TokenType.SKIP); // "removed from the game"
        p.put("same",     TokenType.SKIP); // "the same as your opponent's Leader"
        p.put("without",  TokenType.SKIP); // "without a Counter", "without paying its cost"
        p.put("damage",   TokenType.SKIP); // game-concept noun; not a zone or verb in OP TCG
        // Game-phase words
        p.put("refresh",  TokenType.SKIP); // "Refresh Phase"
        p.put("phase",    TokenType.SKIP);

        // ── ZONE (additional forms) ────────────────────────────────────────────
        p.put("life", TokenType.ZONE); // standalone "life" — residual after "life area"/"life cards" match first

        // ── CARD_TYPE (additional forms) ──────────────────────────────────────
        p.put("event",       TokenType.CARD_TYPE); // standalone "Event" (vs "Event card/cards")
        p.put("events",      TokenType.CARD_TYPE); // plural not covered by "event cards" phrase
        p.put("stages",      TokenType.CARD_TYPE); // plural not covered by "stage cards" phrase
        p.put("leader's",    TokenType.CARD_TYPE); // possessive: "your Leader's power", "Leader's type"
        p.put("card's",      TokenType.CARD_TYPE); // possessive: "this card's power"
        p.put("character's", TokenType.CARD_TYPE); // possessive: "this character's power"

        // ── FILTER (additional state / placement qualifiers) ──────────────────
        p.put("face-up",   TokenType.FILTER); // "place it face-up"
        p.put("face-down", TokenType.FILTER); // "place it face-down"
        p.put("blocker",   TokenType.FILTER); // lowercase keyword name: "gains the Blocker keyword"
        p.put("double",    TokenType.FILTER); // lowercase keyword name: "gains Double Attack"
        p.put("banish",    TokenType.FILTER); // lowercase keyword name: "gains Banish"
        p.put("rush",      TokenType.FILTER); // lowercase keyword name: "gains Rush"
        p.put("base",      TokenType.FILTER); // "base cost", standalone; "base power" (2-word) tried first
        p.put("selected",  TokenType.FILTER); // "the selected card"
        p.put("revealed",  TokenType.FILTER); // "the revealed card"
        p.put("chosen",    TokenType.FILTER); // "the chosen card"

        // ── QUANTITY (additional forms) ───────────────────────────────────────
        p.put("total",      TokenType.QUANTITY); // "N or more in total", "a total of N"
        p.put("every",      TokenType.QUANTITY); // "for every N characters"
        p.put("no",         TokenType.QUANTITY); // "if you have no characters" → quantity of 0
        p.put("none",       TokenType.QUANTITY); // "none of your characters"
        p.put("one",        TokenType.QUANTITY); // "one of your characters"; "choose one" (2-word) caught above
        p.put("additional", TokenType.QUANTITY); // "an additional +N power"

        // ── VERB (additional forms) ───────────────────────────────────────────
        p.put("become",    TokenType.VERB); // "becomes the same as", "become active"
        p.put("becomes",   TokenType.VERB);
        p.put("choose",    TokenType.VERB); // standalone "choose N"; "choose one" (2-word) caught above
        p.put("select",    TokenType.VERB); // "select up to N cards from your deck"
        p.put("shuffle",   TokenType.VERB); // "shuffle your deck"
        p.put("played",    TokenType.VERB); // past-tense: "if played from hand"
        p.put("activated", TokenType.VERB); // past-tense: "when this effect is activated"
        p.put("trashed",   TokenType.VERB); // past-tense: "when this card is trashed"
        p.put("negated",   TokenType.VERB); // past-tense: "this effect is negated"
        p.put("returns",   TokenType.VERB); // 3rd-person of "return"
        p.put("attacks",   TokenType.VERB); // 3rd-person of "attack" in trigger conditions
        p.put("trashes",   TokenType.VERB); // 3rd-person of "trash"
        p.put("activates", TokenType.VERB); // 3rd-person of "activate"
        p.put("battles",   TokenType.VERB); // 3rd-person of "battle": "when this character battles"
        p.put("places",    TokenType.VERB); // 3rd-person of "place"
        p.put("draws",     TokenType.VERB); // 3rd-person of "draw"
        p.put("reveals",   TokenType.VERB); // 3rd-person of "reveal"
        p.put("adds",      TokenType.VERB); // 3rd-person of "add"
        p.put("plays",     TokenType.VERB); // 3rd-person of "play"
        p.put("chooses",   TokenType.VERB); // 3rd-person of "choose"
        p.put("shuffles",  TokenType.VERB); // 3rd-person of "shuffle"
        p.put("placed",    TokenType.VERB); // past-tense of "place"
        p.put("reduced",   TokenType.VERB); // past-tense: "cost is reduced by N"
        p.put("change",    TokenType.VERB); // "change N cards from your hand"
        p.put("take",      TokenType.VERB); // "take N cards from your deck"
        p.put("deal",      TokenType.VERB); // "deal N damage"
        p.put("swap",      TokenType.VERB); // "swap this card with a card in your hand"
        p.put("win",       TokenType.VERB); // "win the game"
        // "K.O.'d" → lowercased → "k.o.'d" → normalised (k.o.→ko) → "ko'd"
        p.put("ko'd",      TokenType.VERB); // passive past-tense KO: "cannot be K.O.'d"

        // ── SKIP (final batch) ────────────────────────────────────────────────
        p.put("being",     TokenType.SKIP); // "being K.O.'d", "being attacked"
        p.put("until",     TokenType.SKIP); // residual after full DURATION phrases match first
        p.put("don't",     TokenType.SKIP); // contraction of "do not"
        p.put("currently", TokenType.SKIP); // "currently on the field"
        p.put("except",    TokenType.SKIP); // "except for your Leader"
        p.put("after",     TokenType.SKIP); // "after this battle", "after your opponent attacks"
        p.put("target",    TokenType.SKIP); // "the target of the attack"
        p.put("different", TokenType.SKIP); // "a different color than"
        p.put("include",   TokenType.SKIP); // "if your Leader's type does not include"
        p.put("least",     TokenType.SKIP); // "at least N"
        p.put("start",     TokenType.SKIP); // residual after "until the start of your next turn"
        p.put("under",     TokenType.SKIP); // "under this card"
        p.put("leaders",   TokenType.SKIP); // plural possessive edge-case
        p.put("between",   TokenType.SKIP); // "choose between A and B"

        // ── QUANTITY (final batch) ────────────────────────────────────────────
        p.put("extra",    TokenType.QUANTITY); // "an extra card", "N extra power"

        // ── ZONE (final batch) ────────────────────────────────────────────────
        p.put("counter",  TokenType.ZONE); // standalone "Counter" zone/keyword area

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

        // ── FILTER_PATTERNS ──────────────────────────────────────────────────
        // Longer / more specific patterns first to prevent partial matches.
        List<Pattern> fp = new ArrayList<>();
        // Cost filters
        fp.add(Pattern.compile("with a cost of (\\d+) or less"));
        fp.add(Pattern.compile("with a cost of (\\d+) or more"));
        fp.add(Pattern.compile("with a cost of up to (\\d+)"));     // synonym for "or less"
        fp.add(Pattern.compile("with a base cost of (\\d+) or less"));
        // Power filters — syntax: "with N power or less"
        fp.add(Pattern.compile("with (\\d+) power or less"));
        fp.add(Pattern.compile("with (\\d+) power or more"));
        fp.add(Pattern.compile("with power of up to (\\d+)"));      // synonym for "or less"
        FILTER_PATTERNS = Collections.unmodifiableList(fp);
    }
}
