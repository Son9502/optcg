package engine.cards.types;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CardType {
    CHARACTER,
    LEADER,
    EVENT,
    STAGE,
    DON;

    @JsonCreator
    public static CardType fromValue(String value) {
        return CardType.valueOf(value.toUpperCase());
    }
}
