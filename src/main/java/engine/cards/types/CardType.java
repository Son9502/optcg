package engine.cards.types;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CardType {
    CHARACTER,
    LEADER,
    EVENT,
    STAGE,
    DON,
    /** Compound target: effect can act on either the Leader or a Character. */
    LEADER_OR_CHARACTER,
    /** DON!! source qualifier: only rested (sideways) DON!! cards in cost zone. */
    RESTED_DON,
    /** DON!! source qualifier: only active (upright) DON!! cards in cost zone. */
    ACTIVE_DON;

    @JsonCreator
    public static CardType fromValue(String value) {
        return CardType.valueOf(value.toUpperCase());
    }
}
