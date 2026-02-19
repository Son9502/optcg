package engine.cards;
import engine.player.Player;
import engine.zones.ZoneType;
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
    public void takeLife(int amount){
        lifePoints = Math.max(0, lifePoints - amount);
    }
    public void gainLife(){
        lifePoints++;
    }
    public void gainLife(int amount){
        lifePoints += amount;
    }
    @Override
    public String toString() {
        String card = "Leader: [Name=" + data.name() + "\n" + "Life=" + lifePoints + "\n" + "Power=" + getTotalPower() + "\n" + "Cost=" + data.cost() + "\n" + "Description=" + data.description();
        if (zone != null && zone.getType() == ZoneType.CHARACTER) {
            card += "\n" + "Rested=" + rested;
            if (!attachedDons.isEmpty()) {
                card += "\n" + "Attached Dons: " + attachedDons.size();
            }
        }
        card += "]";
        return card;
    }

    
}
