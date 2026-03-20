package engine.cards.effects;
import engine.cards.keywords.Keyword;

import com.fasterxml.jackson.annotation.JsonProperty;
import engine.cards.abilities.Condition;
import java.util.List;

public record EffectData(
    @JsonProperty("type")      EffectType type,
    @JsonProperty("value")     int        value,
    @JsonProperty("target")    Target     target,
    @JsonProperty("subtype")   String     subtype,
    @JsonProperty("condition") Condition  condition,
    @JsonProperty("duration")  Duration   duration,
    @JsonProperty("keyword")   Keyword    keyword,
    @JsonProperty("choices")   List<List<EffectData>> choices,
    @JsonProperty("conditionValue") int conditionValue
) {}
