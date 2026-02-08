package engine.zones;
import engine.player.Player;
import engine.cards.Card;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
public class Zone {
    public ZoneType type;
    public Player owner;
    public Deque<Card> cards;   
    public Zone(ZoneType type, Player owner) {
        this.type = type;
        this.owner = owner;
        this.cards = new LinkedList<>();
    }
    // Accessor methods
    public Deque<Card> getCards() {
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
        cards.addLast(card);
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
        return cards.removeLast();
    }
    public Card drawBottom(){
         if (cards.isEmpty()) {
            System.out.println("No cards to draw from " + type);
            return null;
        }
        return cards.removeFirst();
    } 
    public Card peekTop() {
        if (cards.isEmpty()) {
            System.out.println("No cards to peek in " + type);
            return null;
        }
        return cards.peekLast();
    }
    public Card peekBottom() {
        if (cards.isEmpty()) {
            System.out.println("No cards to peek in " + type);
            return null;    
        }
        return cards.peekFirst();
    }
    public void shuffle() {
        List<Card> tempList = new ArrayList<>(cards);
        java.util.Collections.shuffle(tempList);
        cards.clear();
        cards.addAll(tempList);
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
