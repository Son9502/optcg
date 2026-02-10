package engine.cards.effects;

import engine.cards.types.CardType;
import engine.zones.Zone;

public record Target(
    TargetPlayer player,
    Zone zone,
    CardType cardType,
    int max,
    int maxCost,
    int maxPower){
        public enum TargetPlayer {
            SELF,OPPONENT,ALL
        }

}
