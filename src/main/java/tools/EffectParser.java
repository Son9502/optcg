package tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the token stream produced by EffectLexer into a list of ParsedEffect
 * objects, one per game-effect clause in the ability body.
 *
 * Entry points:
 *   EffectParser.parse(String effectText)   — tokenizes then parses
 *   EffectParser.parse(List<Token> tokens)  — parses a pre-tokenized stream
 *
 * The parser expects the ability body only (trigger headers, optional-cost
 * preambles, and timing brackets already stripped by Parser.parseBlock).
 *
 * Phase 1 coverage — unambiguous and context-dependent verbs:
 *   DRAW, TRASH, REST, KO, RETURN_TO_HAND, PLAY_CARD, NEGATE, SEARCH_DECK,
 *   ADD_DON, ADD_LIFE, ATTACH_DON, POWER_BOOST, COST_MODIFY, GRANT_KEYWORD,
 *   SET_DON_ACTIVE, UNREST, MOVE_TO_TOP_DECK, MOVE_TO_BOTTOM_DECK,
 *   PREVENT_ATTACK, PREVENT_BLOCK, PREVENT_KO, MODAL (stub).
 *
 * Deferred (Phase 2):
 *   Full numeric condition values, SEARCH_DECK secondary effects,
 *   subject-first clauses, PLAY_CARD cost/filter details.
 */
public final class EffectParser {

    private EffectParser() {}

    // ── Entry points ──────────────────────────────────────────────────────────

    /** Convenience: tokenize {@code effectText} then parse the token stream. */
    public static List<ParsedEffect> parse(String effectText) {
        return parse(EffectLexer.parseEffect(effectText));
    }

    /**
     * Core entry point. Filters SKIP tokens, extracts a leading condition if
     * present, splits into clauses on CONNECTOR boundaries, and parses each
     * clause into a ParsedEffect.
     */
    public static List<ParsedEffect> parse(List<Token> tokens) {
        // 1. Remove SKIP tokens; keep everything else (MODIFIER included).
        List<Token> filtered = new ArrayList<>();
        for (Token t : tokens) {
            if (t.type() != TokenType.SKIP) filtered.add(t);
        }

        // 2. Extract a leading condition clause (e.g. "If you do," / "If your Leader…").
        String[] conditionHolder = {null}; // mutable box so helper can write into it
        filtered = extractLeadingCondition(filtered, conditionHolder);
        String leadingCondition = conditionHolder[0];

        // 3. Split into clauses on CONNECTOR tokens (and / then / also).
        List<List<Token>> clauses = splitClauses(filtered);

        // 4. Parse each clause.
        List<ParsedEffect> effects = new ArrayList<>();
        for (int i = 0; i < clauses.size(); i++) {
            List<Token> clause = clauses.get(i);
            // First clause gets the leading condition.
            // Clauses after "then" imply IF_YOU_DO (execute only if prior did).
            String clauseCondition = (i == 0) ? leadingCondition
                                   : (i > 0 && connectsBefore(filtered, clauses, i, "then"))
                                     ? "IF_YOU_DO"
                                   : null;
            ParsedEffect effect = parseClause(clause, clauseCondition);
            if (effect != null) effects.add(effect);
        }
        return effects;
    }

    // ── Condition extraction ──────────────────────────────────────────────────

    /**
     * If the token stream starts with a CONDITION token ("if", "when",
     * "if you do", "once per turn"), consume all tokens up to and including
     * the next CONNECTOR (the comma boundary), classify the condition, and
     * return the remaining token list. Writes the condition name into
     * {@code out[0]}.
     */
    private static List<Token> extractLeadingCondition(List<Token> tokens, String[] out) {
        if (tokens.isEmpty()) return tokens;
        Token first = tokens.get(0);
        if (first.type() != TokenType.CONDITION) return tokens;

        String raw = first.raw();

        // "if you do" and "once per turn" are self-contained single tokens — consume
        // only that one token; the effect tokens follow immediately (comma already
        // stripped as punctuation by the lexer, so no CONNECTOR separates them).
        if (raw.equals("if you do")) {
            out[0] = "IF_YOU_DO";
            return tokens.subList(1, tokens.size());
        }
        if (raw.equals("once per turn")) {
            out[0] = "ONCE_PER_TURN";
            return tokens.subList(1, tokens.size());
        }

        // "if" / "when" — classify by peeking at the condition tokens, then advance
        // to the first effect VERB (comma is stripped; no CONNECTOR marks the boundary).
        if (raw.equals("if") || raw.equals("when")) {
            out[0] = classifyConditionClause(tokens);
            int effectStart = findFirstEffectVerb(tokens, 1);
            return (effectStart >= 0) ? tokens.subList(effectStart, tokens.size()) : List.of();
        }

        return tokens;
    }

    /**
     * Finds the index of the first VERB token (at or after {@code from}) that
     * resolves to a non-null EffectType — i.e., the start of an effect clause.
     * Returns -1 if none found.
     */
    private static int findFirstEffectVerb(List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.VERB
                    && resolveVerb(t.raw(), tokens.subList(i, tokens.size())) != null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Classifies an "if …" / "when …" condition by scanning tokens up to the
     * first effect VERB. Uses two passes so that high-specificity FILTER markers
     * (rested, multicolored, type) are checked before lower-specificity CARD_TYPE
     * markers — otherwise "if this Character is rested" would return
     * IF_CHARACTER_COUNT instead of IF_RESTED.
     */
    private static String classifyConditionClause(List<Token> tokens) {
        // Find the boundary: index of first VERB that maps to a real EffectType.
        int limit = tokens.size();
        for (int i = 1; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.VERB
                    && resolveVerb(t.raw(), tokens.subList(i, tokens.size())) != null) {
                limit = i;
                break;
            }
        }

        // Pass 1 — high-specificity single-token markers.
        for (int i = 1; i < limit; i++) {
            Token t = tokens.get(i);
            String r = t.raw();
            if (t.type() == TokenType.FILTER && r.equals("rested"))
                return "IF_RESTED";
            if (t.type() == TokenType.FILTER && r.equals("multicolored"))
                return "IF_LEADER_COLOR";
            if (t.type() == TokenType.FILTER && (r.startsWith("{") || r.equals("type")))
                return "IF_LEADER_TYPE";
            if (t.type() == TokenType.ZONE
                    && (r.equals("life") || r.equals("life area") || r.equals("life cards")))
                return "IF_LIFE_COUNT";
        }

        // Pass 2 — contextual type / zone markers.
        for (int i = 1; i < limit; i++) {
            Token t = tokens.get(i);
            String r = t.raw();
            if (t.type() == TokenType.CARD_TYPE && r.contains("don!!"))
                return "IF_DON_COUNT";
            if (t.type() == TokenType.CARD_TYPE && r.contains("character"))
                return "IF_CHARACTER_COUNT";
            if (t.type() == TokenType.ZONE && r.equals("hand"))
                return "IF_HAND_COUNT";
        }

        return null; // unknown condition — defer to manual override
    }

    // ── Clause splitting ──────────────────────────────────────────────────────

    /**
     * Splits the token stream into clauses on CONNECTOR tokens
     * ("and", "then", "also"). Each resulting sub-list is one game-effect
     * clause. The "/" connector (MODAL option separator) is intentionally
     * kept inside clauses for the MODAL stub handler.
     */
    private static List<List<Token>> splitClauses(List<Token> tokens) {
        List<List<Token>> clauses = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        for (Token t : tokens) {
            if (t.type() == TokenType.CONNECTOR && !t.raw().equals("/")) {
                if (!current.isEmpty()) {
                    clauses.add(current);
                    current = new ArrayList<>();
                }
            } else {
                current.add(t);
            }
        }
        if (!current.isEmpty()) clauses.add(current);
        return clauses;
    }

    /**
     * Returns true if the connector token immediately before clause {@code i}
     * (in the original filtered stream) matches {@code connectorRaw}.
     * Used to detect "then" boundaries so subsequent clauses get IF_YOU_DO.
     */
    private static boolean connectsBefore(List<Token> filtered,
                                          List<List<Token>> clauses,
                                          int clauseIndex,
                                          String connectorRaw) {
        // Walk the original filtered stream, counting non-"/" CONNECTORs.
        // The Nth connector (1-based) is the one that precedes clause N.
        int clauseIdx = 0;
        for (int i = 0; i < filtered.size(); i++) {
            Token t = filtered.get(i);
            if (t.type() == TokenType.CONNECTOR && !t.raw().equals("/")) {
                clauseIdx++;
                if (clauseIdx == clauseIndex) {
                    return t.raw().equals(connectorRaw);
                }
            }
        }
        return false;
    }

    // ── Clause parser ─────────────────────────────────────────────────────────

    /**
     * Parses a single clause (list of non-SKIP tokens) into a ParsedEffect.
     * Returns null if no recognisable VERB is found (residual fragment).
     */
    private static ParsedEffect parseClause(List<Token> clause, String condition) {
        // 4a. Find the VERB.
        Token verbToken = firstOfType(clause, TokenType.VERB);
        if (verbToken == null) return null;

        // 4b. Resolve EffectType.
        String effectType = resolveVerb(verbToken.raw(), clause);
        if (effectType == null) return null;

        // 4c. Optional flag — "may" before the VERB?
        boolean optional = false;
        for (Token t : clause) {
            if (t == verbToken) break;
            if (t.type() == TokenType.MODIFIER && t.raw().equals("may")) {
                optional = true;
                break;
            }
        }

        // MODAL — return stub immediately.
        if (effectType.equals("MODAL")) {
            return new ParsedEffect("MODAL", 0, null, null, null,
                    0, 0, 0, 0, 0, condition, null, optional);
        }

        // 4d. Quantities.
        int[] qv = extractQuantities(clause, effectType);
        int value = qv[0];
        int max   = qv[1];

        // 4e. Player.
        String player = extractPlayer(clause);
        // Effects that are always self-targeted default to SELF when no PLAYER token present.
        if (player == null && isSelfTargetingEffect(effectType)) player = "SELF";

        // 4f. Card type.
        String cardType = extractCardType(clause);

        // 4g. Zone.
        String zone = extractZone(clause);

        // 4h. Filter bounds.
        int[] filters = extractFilters(clause);
        int maxCost  = filters[0];
        int minCost  = filters[1];
        int maxPower = filters[2];
        int minPower = filters[3];

        // 4i. Duration.
        String duration = extractDuration(clause);

        // 4j. Assemble.
        return new ParsedEffect(effectType, value, player, zone, cardType,
                max, maxCost, minCost, maxPower, minPower,
                condition, duration, optional);
    }

    // ── Verb → EffectType resolution ─────────────────────────────────────────

    private static String resolveVerb(String verb, List<Token> clause) {
        return switch (verb) {
            // Unambiguous
            case "draw", "draws"           -> "DRAW";
            case "trash", "trashes",
                 "trashed"                 -> "TRASH";
            case "rest"                    -> "REST";
            case "ko"                      -> "KO";
            case "return", "returns"       -> "RETURN_TO_HAND";
            case "play", "plays", "played" -> "PLAY_CARD";
            case "look", "reveal",
                 "reveals"                 -> "SEARCH_DECK";
            case "negate", "negated"       -> "NEGATE";
            case "choose one"              -> "MODAL";
            case "shuffle", "shuffles"     -> null; // no SHUFFLE_DECK EffectType yet — deferred Phase 2
            // Context-dependent
            case "add", "adds"             -> resolveAdd(clause);
            case "give", "gain", "gains"   -> resolveGiveGain(clause);
            case "set", "activate",
                 "activates", "activated"  -> resolveSetActivate(clause);
            case "place", "places",
                 "placed"                  -> resolvePlace(clause);
            case "cannot"                  -> resolveCannot(clause);
            default                        -> null;
        };
    }

    /** "add / adds" — disambiguate by first CARD_TYPE or ZONE. */
    private static String resolveAdd(List<Token> clause) {
        for (Token t : clause) {
            if (t.type() == TokenType.CARD_TYPE && t.raw().contains("don!!")) return "ADD_DON";
            if (t.type() == TokenType.ZONE) {
                String z = t.raw();
                if (z.equals("life") || z.equals("life area") || z.equals("life cards")) return "ADD_LIFE";
            }
        }
        return "ADD_DON"; // default: most common "add" usage
    }

    /**
     * "give / gain / gains" — look at the first STAT or FILTER token after the VERB.
     * Signed QUANTITY before STAT determines power-boost vs cost-modify.
     */
    private static String resolveGiveGain(List<Token> clause) {
        boolean pastVerb = false;
        for (Token t : clause) {
            if (t.type() == TokenType.VERB) { pastVerb = true; continue; }
            if (!pastVerb) continue;
            if (t.type() == TokenType.STAT) {
                return t.raw().equals("power") ? "POWER_BOOST" : "COST_MODIFY";
            }
            if (t.type() == TokenType.FILTER) {
                String r = t.raw();
                // [Keyword] filter → GRANT_KEYWORD; keyword word-form (blocker etc.) → GRANT_KEYWORD
                if (r.startsWith("[") || r.equals("blocker") || r.equals("double")
                        || r.equals("banish") || r.equals("rush")) {
                    return "GRANT_KEYWORD";
                }
            }
            if (t.type() == TokenType.CARD_TYPE && t.raw().contains("don!!")) return "ATTACH_DON";
        }
        // Fallback: check if there's a STAT anywhere in clause
        for (Token t : clause) {
            if (t.type() == TokenType.STAT)
                return t.raw().equals("power") ? "POWER_BOOST" : "COST_MODIFY";
        }
        return null;
    }

    /** "set / activate …" — DON!! → SET_DON_ACTIVE; character/leader → UNREST. */
    private static String resolveSetActivate(List<Token> clause) {
        for (Token t : clause) {
            if (t.type() == TokenType.CARD_TYPE) {
                String r = t.raw();
                if (r.contains("don!!")) return "SET_DON_ACTIVE";
                if (r.contains("character") || r.equals("leader") || r.equals("leader's"))
                    return "UNREST";
            }
        }
        return "SET_DON_ACTIVE"; // default
    }

    /** "place / places / placed" — use FILTER(top/bottom) or ZONE(life) to disambiguate. */
    private static String resolvePlace(List<Token> clause) {
        for (Token t : clause) {
            if (t.type() == TokenType.FILTER) {
                if (t.raw().equals("top"))    return "MOVE_TO_TOP_DECK";
                if (t.raw().equals("bottom")) return "MOVE_TO_BOTTOM_DECK";
            }
            if (t.type() == TokenType.ZONE) {
                String z = t.raw();
                if (z.equals("life") || z.equals("life area") || z.equals("life cards"))
                    return "ADD_LIFE";
            }
        }
        return null;
    }

    /**
     * "cannot" — look at the next VERB after "cannot" for "attack" (→ PREVENT_ATTACK),
     * "ko'd" (→ PREVENT_KO); or a [Blocker] FILTER (→ PREVENT_BLOCK).
     */
    private static String resolveCannot(List<Token> clause) {
        boolean pastCannot = false;
        for (Token t : clause) {
            if (t.type() == TokenType.VERB && t.raw().equals("cannot")) { pastCannot = true; continue; }
            if (!pastCannot) continue;
            if (t.type() == TokenType.VERB) {
                return switch (t.raw()) {
                    case "attack", "attacks" -> "PREVENT_ATTACK";
                    case "ko'd"              -> "PREVENT_KO";
                    case "activate",
                         "activates"         -> "PREVENT_BLOCK"; // "cannot activate [Blocker]"
                    default                  -> null;
                };
            }
            if (t.type() == TokenType.FILTER && t.raw().startsWith("[")) return "PREVENT_BLOCK";
        }
        return null;
    }

    // ── Field extractors ──────────────────────────────────────────────────────

    /**
     * Extracts value and max from QUANTITY tokens in the clause.
     * Returns int[]{value, max}.
     *
     * Rule:
     * - "up to N" anywhere → max = N (always a targeting count).
     * - Bare digit BEFORE the VERB → max = N (subject-first clause targeting count,
     *   e.g. "1 of your Characters gains +1000 power" or
     *        "Up to 1 of your Leader or Characters gains +1000 power").
     * - Bare digit AFTER the VERB → routed by effectType:
     *     → value for count-effects (DRAW, POWER_BOOST, COST_MODIFY, ADD_DON, TRASH, ADD_LIFE, ATTACH_DON)
     *     → max  for targeting effects (REST, KO, RETURN_TO_HAND, PLAY_CARD, SEARCH_DECK)
     */
    private static int[] extractQuantities(List<Token> clause, String effectType) {
        int value = 0;
        int max   = 0;
        boolean upToSeen = false;
        boolean pastVerb = false;

        for (Token t : clause) {
            if (t.type() == TokenType.VERB) { pastVerb = true; continue; }
            if (t.type() == TokenType.QUANTITY) {
                String raw = t.raw();
                if (raw.equals("up to")) {
                    upToSeen = true;
                    continue;
                }
                if (t.value() instanceof Integer n) {
                    if (upToSeen) {
                        // "up to N" — always a targeting count
                        max = n;
                        upToSeen = false;
                    } else if (!pastVerb) {
                        // Digit before the VERB — subject-first targeting count
                        max = n;
                    } else {
                        // Digit after the VERB — route by EffectType
                        if (isTargetingEffect(effectType)) {
                            max = n;
                        } else {
                            value = n;
                        }
                    }
                }
            }
        }
        return new int[]{value, max};
    }

    /** Returns true for effects that are always performed by/on the active player. */
    private static boolean isSelfTargetingEffect(String effectType) {
        return switch (effectType) {
            case "DRAW", "ADD_DON", "SET_DON_ACTIVE", "ADD_LIFE", "SEARCH_DECK" -> true;
            default -> false;
        };
    }

    /** Returns true for effects where the numeric argument is a targeting count. */
    private static boolean isTargetingEffect(String effectType) {
        return switch (effectType) {
            case "REST", "KO", "RETURN_TO_HAND", "PLAY_CARD",
                 "SEARCH_DECK", "UNREST", "NEGATE",
                 "PREVENT_ATTACK", "PREVENT_BLOCK", "PREVENT_KO" -> true;
            default -> false;
        };
    }

    /** Maps a PLAYER token raw value to a TargetPlayer name string. */
    private static String extractPlayer(List<Token> clause) {
        Token t = firstOfType(clause, TokenType.PLAYER);
        if (t == null) return null;
        return switch (t.raw()) {
            case "your opponent's",
                 "your opponent"   -> "OPPONENT";
            case "owner's"        -> "OPPONENT";
            case "your", "you"    -> "SELF";
            default               -> null;
        };
    }

    /**
     * Maps the first CARD_TYPE token to a normalized CardType name string.
     * Possessives and multi-word forms are collapsed to base type names.
     */
    private static String extractCardType(List<Token> clause) {
        Token t = firstOfType(clause, TokenType.CARD_TYPE);
        if (t == null) return null;
        String r = t.raw();
        if (r.contains("leader or character")) return "LEADER_OR_CHARACTER";
        if (r.equals("leader") || r.equals("leader's"))  return "LEADER";
        if (r.equals("leaders"))                          return "LEADER";
        if (r.contains("character"))                      return "CHARACTER";
        if (r.contains("don!!"))                          return "DON_CARD";
        if (r.contains("event"))                          return "EVENT_CARD";
        if (r.contains("stage"))                          return "STAGE_CARD";
        return "CARD"; // "card" / "cards" — untyped
    }

    /**
     * Returns the raw value of the first ZONE token that appears after a
     * DESTINATION token, or the first ZONE token if no DESTINATION precedes it.
     */
    private static String extractZone(List<Token> clause) {
        boolean seenDestination = false;
        String fallback = null;
        for (Token t : clause) {
            if (t.type() == TokenType.DESTINATION) { seenDestination = true; continue; }
            if (t.type() == TokenType.ZONE) {
                if (seenDestination) return t.raw();
                if (fallback == null) fallback = t.raw();
            }
        }
        return fallback;
    }

    /**
     * Extracts numeric cost/power filter bounds from FILTER tokens.
     * FILTER_PATTERNS in the lexer store the numeric value in token.value().
     * Returns int[]{maxCost, minCost, maxPower, minPower}.
     */
    private static int[] extractFilters(List<Token> clause) {
        int maxCost = 0, minCost = 0, maxPower = 0, minPower = 0;
        for (Token t : clause) {
            if (t.type() != TokenType.FILTER) continue;
            if (!(t.value() instanceof Integer n)) continue;
            String r = t.raw();
            if (r.contains("cost")) {
                if (r.contains("less") || r.contains("up to")) maxCost = n;
                else if (r.contains("more"))                    minCost = n;
            } else if (r.contains("power")) {
                if (r.contains("less") || r.contains("up to")) maxPower = n;
                else if (r.contains("more"))                    minPower = n;
            }
        }
        return new int[]{maxCost, minCost, maxPower, minPower};
    }

    /** Maps a DURATION token to its Duration enum name string. */
    private static String extractDuration(List<Token> clause) {
        Token t = firstOfType(clause, TokenType.DURATION);
        if (t == null) return null;
        String r = t.raw();
        if (r.contains("battle"))                                        return "THIS_BATTLE";
        if (r.contains("opponent") || r.contains("next"))               return "OPPONENTS_NEXT_TURN";
        // "during this turn" / "until end of this turn" / etc.
        return "THIS_TURN";
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Returns the first token in {@code clause} with the given type, or null. */
    private static Token firstOfType(List<Token> clause, TokenType type) {
        for (Token t : clause) {
            if (t.type() == type) return t;
        }
        return null;
    }
}
