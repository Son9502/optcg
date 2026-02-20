package engine.cards.effects;

import engine.cards.abilities.Condition;

public record EffectData(
    EffectType type,
    int value,
    Target target,
    Condition condition,
    int duration
) {
    
}
