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
        p.put("up to",       TokenType.QUANTITY); // 2 words; must precede any lone "up"
        p.put("all",         TokenType.QUANTITY);

        // ── PLAYER ───────────────────────────────────────────────────────────
        p.put("your opponent's", TokenType.PLAYER); // 2 words; must precede "your"
        p.put("your",            TokenType.PLAYER);

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

        // ── ZONE ─────────────────────────────────────────────────────────────
        // Source zones
        p.put("from the bottom of your deck", TokenType.ZONE);
        p.put("from the top of your deck",    TokenType.ZONE);
        p.put("from your don!! deck",         TokenType.ZONE);
        p.put("from your life area",          TokenType.ZONE);
        p.put("from your hand",               TokenType.ZONE);
        p.put("from your deck",               TokenType.ZONE);
        p.put("from your trash",              TokenType.ZONE);
        p.put("from your field",              TokenType.ZONE);
        // Destination zones
        p.put("at the bottom of your deck",   TokenType.ZONE);
        p.put("at the top of your deck",      TokenType.ZONE);
        p.put("into your life area",          TokenType.ZONE);
        p.put("to your hand",                 TokenType.ZONE);
        p.put("to your trash",                TokenType.ZONE);
        p.put("on your field",                TokenType.ZONE);
        p.put("as active",                    TokenType.ZONE); // DON!! placement state

        // ── DURATION ─────────────────────────────────────────────────────────
        p.put("until the end of your opponent's next end phase", TokenType.DURATION);
        p.put("until the end of your opponent's next turn",      TokenType.DURATION);
        p.put("until the end of this turn",                      TokenType.DURATION);
        p.put("until end of this turn",                          TokenType.DURATION);
        p.put("during this battle",                              TokenType.DURATION);
        p.put("during this turn",                                TokenType.DURATION);

        // ── CONDITION ────────────────────────────────────────────────────────
        // Numeric conditions (IF_LIFE_COUNT, IF_DON_COUNT, etc.) require regex
        // and will be added to a CONDITION_PATTERNS list when EffectParser is built.
        p.put("if you do", TokenType.CONDITION); // IF_YOU_DO; 3 words, precedes "if"
        p.put("if",        TokenType.CONDITION); // general condition opener

        // ── CONNECTOR ────────────────────────────────────────────────────────
        p.put("and",  TokenType.CONNECTOR);
        p.put("then", TokenType.CONNECTOR);
        p.put("also", TokenType.CONNECTOR);
        p.put("/",    TokenType.CONNECTOR); // modal option separator

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
