package engine.cards.abilities;

public enum Condition {

    // ── Turn / Phase ─────────────────────────────────────────────────────────
    YOUR_TURN,           // [Your Turn]
    OPPONENTS_TURN,      // [Opponent's Turn]
    ONCE_PER_TURN,       // [Once Per Turn]

    // ── Trigger gates ────────────────────────────────────────────────────────
    IF_YOU_DO,           // "if you do" — preceding effect must have resolved
    IF_RESTED,           // this Character is rested

    // ── Self quantity thresholds ──────────────────────────────────────────────
    // conditionValue sign encodes direction: -N = "N or less" (≤N), +N = "N or more" (≥N)
    IF_LIFE_COUNT,                 // your life cards
    IF_HAND_COUNT,                 // cards in your hand
    IF_DON_COUNT,                  // your active DON!! cards
    IF_CHARACTER_COUNT,            // Characters on your field
    IF_RESTED_CHARACTER_COUNT,     // rested Characters on your field
    IF_DECK_COUNT,                 // cards in your deck
    IF_TRASH_COUNT,                // cards in your trash

    // ── Self field state ─────────────────────────────────────────────────────
    IF_LEADER_NAME,                // your Leader is [Name]; subtype = name
    IF_LEADER_TYPE,                // your Leader has the {X} type; subtype = type
    IF_LEADER_ATTRIBUTE,           // your Leader has the (X) attribute; subtype = attribute name
    IF_LEADER_MULTICOLORED,        // your Leader has 2+ colors
    IF_FIELD_CHARACTER_TYPE,       // any field Character has the {X} type; subtype = type
    IF_FIELD_CHARACTER_POWER,      // any field Character has ≥N power; conditionValue = N
    IF_FIELD_CHARACTER_NAME,       // field has a [Name] Character;
                                   //   subtype = pipe-joined names ("San-Gorou|Sanji");
                                   //   conditionValue = min base power (0 = no power check)
    IF_NOT_FIELD_CHARACTER_NAME,   // field does not have a [Name] Character; subtype and conditionValue as above
    IF_FIELD_CHARACTER_NAME_RESTED,// field has a rested [Name] Character; subtype = name
    IF_FIELD_CHARACTER_COST_COUNT, // ≥N field Characters with cost ≥C;
                                   //   conditionValue = min count; subtype = min cost as string
    IF_FIELD_CHARACTER_COST,       // any field Character has cost ≥N; conditionValue = N

    // ── Opponent quantity thresholds ─────────────────────────────────────────
    IF_OPPONENT_LIFE_COUNT,              // opponent's life cards
    IF_OPPONENT_HAND_COUNT,              // opponent's hand size
    IF_OPPONENT_DON_COUNT,               // opponent's active DON!! cards
    IF_OPPONENT_CHARACTER_COUNT,         // Characters on opponent's field
    IF_OPPONENT_RESTED_CHARACTER_COUNT,  // rested Characters on opponent's field

    // ── Opponent field state ─────────────────────────────────────────────────
    IF_OPPONENT_FIELD_CHARACTER_POWER,   // any opponent field Character has ≥N power; conditionValue = N
    IF_OPPONENT_FIELD_CHARACTER_COST,    // any opponent field Character has cost ≥N; conditionValue = N
    IF_OPPONENT_ATTRIBUTE,               // opponent's Leader has the (X) attribute; subtype = attribute name
}
