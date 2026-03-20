package engine.cards.effects;

import engine.cards.types.CardType;
import engine.zones.ZoneType;
import engine.cards.types.Attribute;
import engine.cards.types.Color;
import java.util.List;

public record Target(
    TargetPlayer player,
    ZoneType zone,
    CardType cardType,
    int max,
    int maxCost,
    int minCost,
    int maxPower,
    int minPower,
    String subtype,
    String nameFilter,
    Attribute attribute,
    List<Color> colors){
        public enum TargetPlayer {
            SELF,OPPONENT,ALL,THIS_CARD
        }

}
