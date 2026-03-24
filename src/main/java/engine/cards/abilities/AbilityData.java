package engine.cards.abilities;

import com.fasterxml.jackson.annotation.JsonProperty;
import engine.cards.effects.EffectData;
import engine.cards.keywords.Keyword;
import java.util.List;

public record AbilityData(
    @JsonProperty("triggerType")   Trigger          triggerType,
    @JsonProperty("donRequired")   int              donRequired,
    @JsonProperty("costs")         List<Cost>       costs,
    @JsonProperty("isOncePerTurn") boolean          isOncePerTurn,
    @JsonProperty("condition")     Condition        condition,
    /** Non-null only when triggerType == KEYWORD. */
    @JsonProperty("keyword")       Keyword          keyword,
    @JsonProperty("effects")       List<EffectData> effects
) {}
