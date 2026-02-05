package engine.cards.abilities;
import java.util.List;
import engine.cards.effects.EffectData;
public record AbilityData(
    Trigger triggerType,
    int donRequired,
    Cost cost,
    boolean isOncePerTurn,
    Condition condition,
    List<EffectData> effects
) {
    
}
