package engine.cards.effects;

import engine.cards.types.CardType;
import engine.zones.ZoneType;

public record Target(
    TargetPlayer player,
    ZoneType zone,
    CardType cardType,
    int max,
    int maxCost,
    int minCost,
    int maxPower,
    int minPower){
        public enum TargetPlayer {
            SELF,OPPONENT,ALL
        }

}
