package engine.cards.effects;

import java.lang.annotation.Target;
import java.util.concurrent.locks.Condition;

public record EffectData(
    EffectType type,
    int value,
    Target target,
    Condition condition,
    int duration
) {
    
}
