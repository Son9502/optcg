package engine.cards.effects;

/**
 * How long a granted effect or stat modification persists.
 *
 * Card text phrases → Duration value:
 *   "during this battle"                     → THIS_BATTLE
 *   "during this turn"                       → THIS_TURN
 *   "until the end of this turn"             → THIS_TURN
 *   "during your opponent's next turn"        → OPPONENTS_NEXT_TURN
 *   "until the end of your opponent's turn"   → OPPONENTS_NEXT_TURN
 *   no qualifier (K.O., DRAW, permanent buff) → PERMANENT
 */
public enum Duration {

    /** Effect lasts only for the current battle (attack resolution). */
    THIS_BATTLE,

    /** Effect lasts until the end of the current turn. */
    THIS_TURN,

    /** Effect lasts until the end of the opponent's next turn. */
    OPPONENTS_NEXT_TURN,

    /** Effect lasts while the card is on the field. */
    WHILE_IN_PLAY, // for effects that only apply while the card is on the field (e.g. "this Character gets +1000 power")
}
