package engine.cards.abilities;

public enum Condition {
    YOUR_TURN,          // [Your Turn]
    OPPONENTS_TURN,     // [Opponent's Turn]
    ONCE_PER_TURN,      // [Once Per Turn]
    IF_YOU_DO,          // if you do (preceding effect resolved)
    // ---- Quantity-based conditions ----
    // conditionValue sign encodes direction: -N = "N or less" (<=), +N = "N or more" (>=), 0 = always true
    IF_LIFE_COUNT,             // if you have [±N] life cards
    IF_HAND_COUNT,             // if you have [±N] cards in hand
    IF_DON_COUNT,              // if you have [±N] active DON!! cards
    IF_CHARACTER_COUNT,        // if you have [±N] Characters on field
    IF_DECK_COUNT,             // if you have [±N] cards in deck
    IF_TRASH_COUNT,            // if you have [±N] cards in trash
    IF_FIELD_CHARACTER_COST,   // if any Character on your field has cost [±N]
    IF_OPPONENT_LIFE_COUNT,    // if your opponent has [±N] life cards
    IF_OPPONENT_HAND_COUNT,    // if your opponent has [±N] cards in hand
    // ---- State-based conditions ----
    IF_RESTED,               // if this Character is rested
    IF_LEADER_NAME,          // if your Leader is [Name]
    IF_LEADER_TYPE,          // if your Leader has the {X} type
    IF_LEADER_ATTRIBUTE,     // if your Leader has the (X) attribute
    IF_FIELD_CHARACTER_TYPE, // if you have a Character with {X} type on the field
    IF_LEADER_COLOR,         // if your Leader is multicolored
}
