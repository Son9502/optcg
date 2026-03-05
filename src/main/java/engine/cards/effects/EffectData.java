package engine.cards.effects;
import engine.cards.keywords.Keyword;

import com.fasterxml.jackson.annotation.JsonProperty;
import engine.cards.abilities.Condition;

public record EffectData(
    @JsonProperty("type")      EffectType type,
    @JsonProperty("value")     int        value,
    @JsonProperty("target")    Target     target,
    @JsonProperty("condition") Condition  condition,
    @JsonProperty("duration")  Duration   duration,
    @JsonProperty("keyword")   Keyword    keyword
) {}
