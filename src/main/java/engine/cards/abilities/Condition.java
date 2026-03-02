package engine.cards.abilities;

public enum Condition {
    YOUR_TURN,          // [Your Turn]
    OPPONENTS_TURN,     // [Opponent's Turn]
    ONCE_PER_TURN,      // [Once Per Turn]
    IF_YOU_DO,          // if you do (preceding effect resolved)
    IF_LIFE_COUNT,      // if you have N or less/more Life cards
    IF_HAND_COUNT,      // if you have N or less/more cards in hand
    IF_DON_COUNT,       // if you have N or more/fewer DON!! cards
    IF_CHARACTER_COUNT, // if you have N or more/fewer Characters
    IF_RESTED,          // if this Character is rested
    IF_LEADER_TYPE,     // if your Leader has the [X] type
    IF_LEADER_COLOR,    // if your Leader is multicolored
}
