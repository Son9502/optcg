package engine.cards;
import engine.player.Player;
public class Leader extends Card {
    private int lifePoints;
    public Leader(String card_id, CardData data, Player owner) {
        super(card_id, data, owner);
        this.lifePoints = data.life(); // Assuming CardData has a method to get life points
    }
    public int getLifePoints() {
        return lifePoints;
    }
    public void takeLife(){
        lifePoints = Math.max(0, lifePoints - 1);
    }
    public void gainLife(){
        lifePoints++;
    }

    
}
