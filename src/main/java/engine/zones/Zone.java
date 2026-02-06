package engine.zones;
import engine.player.Player;
import engine.cards.Card;
import java.util.List;
import java.util.ArrayList;
public class Zone {
    public ZoneType type;
    public Player owner;
    public List<Card> cards;

    public Zone(ZoneType type, Player owner) {
        this.type = type;
        this.owner = owner;
        this.cards = new ArrayList<>();
    }
    // Accessor methods
    public List<Card> getCards() {
        return cards;
    }
    public ZoneType getType() {
        return type;
    }
    public Player getOwner() {
        return owner;
    }
    // Utility methods
    public void add(Card card) {
        cards.add(card);
        card.setZone(this);
    }
    public void remove(Card card) {
        cards.remove(card);
        card.setZone(null);
    }
    public Card drawTop(){
        if (cards.isEmpty()) {
            System.out.println("No cards to draw from " + type);
            return null;
        }
        return cards.remove(cards.size() - 1);
    }
    public Card drawBottom(){
         if (cards.isEmpty()) {
            System.out.println("No cards to draw from " + type);
            return null;
        }
        return cards.remove(0);
    } 
    public Card peekTop() {
        if (cards.isEmpty()) {
            System.out.println("No cards to peek in " + type);
            return null;
        }
        return cards.get(cards.size() - 1);
    }
    public Card peekBottom() {
        if (cards.isEmpty()) {
            System.out.println("No cards to peek in " + type);
            return null;    
        }
        return cards.get(0);
    }
    public void shuffle() {
        java.util.Collections.shuffle(cards);
    }
    public boolean contains(Card card) {
        return cards.contains(card);
    }
    public int size() {
        return cards.size();
    }
    @Override
    public String toString() {
        return "Zone [type=" + type + ", owner=" + owner.getName() + ", cards=" + cards + "]";
    }
    public void moveTo(Zone targetZone, Card card) {
        if (this.contains(card)) {
            this.remove(card);
            targetZone.add(card);
        } else {
            System.out.println("Card not found in " + type);
        }
    }
}
