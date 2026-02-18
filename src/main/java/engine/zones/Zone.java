package engine.zones;
import engine.player.Player;
import engine.cards.Card;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Zone {
    protected ZoneType type;
    protected Player owner;
    protected int maxSize;
    protected Deque<Card> cards;   
    public Zone(ZoneType type, Player owner) {
        this.type = type;
        this.owner = owner;
        this.cards = new LinkedList<>();
    }
    public Zone(ZoneType type, Player owner, List<Card> initialCards) {
        this.type = type;
        this.owner = owner;
        this.cards = new LinkedList<>(initialCards);
    }
    // Accessor methods
    public List<Card> getCards() {
        return Collections.unmodifiableList(new ArrayList<>(cards));
    }
    public ZoneType getType() {
        return type;
    }
    public Player getOwner() {
        return owner;
    }
    // Utility methods
    public void add(Card card) {
        cards.addFirst(card);
        card.setZone(this);
    }
    public void addBottom(Card card) {
        cards.addLast(card);
        card.setZone(this);
    }
    public void add(List<Card> newCards) {
        for (Card card : newCards) {
            add(card);
        }
    }
    public void addBottom(List<Card> newCards) {
        for (Card card : newCards) {
            addBottom(card);
        }
    }
    public Card remove(){
        if (!cards.isEmpty()) {
            Card removedCard = cards.removeLast();
            removedCard.setZone(null);
            return removedCard;
        }
        return null;
    }
    public Card remove(Card card) {
        if (!cards.contains(card)) {
            System.out.println("Card not found in " + type);
            return null;
        }
        cards.remove(card);
        card.setZone(null);
        return card;
    }
    public void shuffle() {
        List<Card> tempList = new ArrayList<>(cards);
        java.util.Collections.shuffle(tempList);
        cards.clear();
        cards.addAll(tempList);
    }
    public void clear() {
        for (Card card : cards) {
            card.setZone(null);
        }
        cards.clear();
    }
    public boolean contains(Card card) {
        return cards.contains(card);
    }
    public int size() {
        return cards.size();
    }
    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
