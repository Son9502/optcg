package engine.cards.effects;

import com.fasterxml.jackson.annotation.JsonProperty;
import engine.cards.abilities.Condition;

public record EffectData(
    @JsonProperty("type")      EffectType type,
    @JsonProperty("value")     int        value,
    @JsonProperty("target")    Target     target,
    @JsonProperty("condition") Condition  condition,
    @JsonProperty("duration")  int        duration
) {}
