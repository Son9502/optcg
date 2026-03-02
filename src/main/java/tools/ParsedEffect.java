package tools;

/**
 * Structured representation of a single game effect extracted from card effect text
 * by EffectParser. This is the intermediate format produced in the tools pipeline;
 * CardCompiler serializes it to JSON and CardDatabase deserializes it back into
 * runtime EffectData objects.
 *
 * All engine-typed fields (EffectType, TargetPlayer, CardType, Condition) are stored
 * as their String names here to avoid cross-package dependencies between tools and engine.
 *
 * Target fields are flattened from EffectData.target (a nested Target record) into
 * top-level fields for simpler serialization.
 *
 * Zero (0) indicates "not specified" for all int fields.
 * Null indicates "not applicable" for all String fields.
 *
 * Example — "Rest up to 1 of your opponent's Characters with a cost of 4 or less.":
 *   effectType    = "REST_CHARACTER"
 *   value         = 0
 *   targetPlayer  = "OPPONENT"
 *   targetZone    = null
 *   targetCardType = "CHARACTER"
 *   max           = 1
 *   maxCost       = 4
 *   minCost       = 0
 *   maxPower      = 0
 *   minPower      = 0
 *   condition     = null
 *   duration      = null
 */
public record ParsedEffect(
        String effectType,       // EffectType enum name: "DRAW", "REST", "ADD_DON", etc.
        int    value,            // signed int: +2000 power boost, -3 cost, 2 cards drawn; 0 = unspecified
        String targetPlayer,     // TargetPlayer name: "SELF" | "OPPONENT" | "ALL" | null
        String targetZone,       // Zone lexer token raw value: "hand" | "deck" | "don!! deck" | etc. | null
        String targetCardType,   // CardType name: "CHARACTER" | "DON_CARD" | "CARD" | null
        int    max,              // "up to N" max count; 0 = unspecified
        int    maxCost,          // upper cost filter ("with a cost of N or less"); 0 = none
        int    minCost,          // lower cost filter ("with a cost of N or more"); 0 = none
        int    maxPower,         // upper power filter ("with N power or less"); 0 = none
        int    minPower,         // lower power filter ("with N power or more"); 0 = none
        String condition,        // Condition enum name: "IF_LIFE_COUNT", "IF_CHARACTER_COUNT", etc. | null
        String duration          // Duration enum name: "THIS_TURN" | "THIS_BATTLE" | "OPPONENTS_NEXT_TURN" | "PERMANENT"
) {}
