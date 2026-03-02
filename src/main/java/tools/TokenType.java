package tools;
/**
 * TokenType defines the different types of tokens that can be identified in card effect text.
 * This is used by the EffectLexer to parse card effects into structured components that can be
 * processed by the game engine. The specific token types are designed to capture the various
 * elements commonly found in card effects, such as actions, quantities, targets, conditions, and
 * connectors.
 */
public enum TokenType {
    VERB, // e.g. "draw", "deal", "gain"
    QUANTITY, // e.g. "2", "X"
    PLAYER, // e.g. "you", "opponent"
    CARD_TYPE, // e.g. "character", "event", "action"
    FILTER, // e.g. "with power 3000 or less", "with a BLOCKER keyword"
    ZONE, // e.g. "hand", "field", "trash"
    DURATION, // e.g. "until end of turn", "for the rest of the game"
    CONDITION, // e.g. "if you have more cards in hand than your opponent", "if this card is in your trash"
    CONNECTOR,   // e.g. "and", "or", "then"
    DESTINATION, // directional/positional preposition: "from", "to", "into", "on", "in", "at"
    STAT,        // game stat noun standalone: "power", "cost"
    SKIP,        // grammatical glue with no game-logic value (e.g. "the", "a", "may")
    UNKNOWN      // unrecognized word — signals a missing phrase-table entry
}
