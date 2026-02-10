package engine.zones;

import engine.player.Player;
import engine.cards.Card;
import java.util.List;
import java.util.function.Predicate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;

public class Deck extends Zone {
    public Deck(Player owner) {
        super(ZoneType.DECK, owner);
    }

    public Deck(ZoneType type, Player owner) {
        super(type, owner);
    }

    /**
     * Draws a card from the top of the deck and adds it to the player's hand.
     * 
     * @return The card drawn, or null if the deck is empty.
     */
    public Card draw() {
        if (cards.isEmpty()) {
            System.out.println("No more cards to draw from " + type);
            return null;
        }
        Card drawnCard = cards.removeLast();
        if (drawnCard == null) {
            return null;
        }
        drawnCard.setZone(null); // Clear the card's zone reference
        return drawnCard;
    }

    /**
     * Draws a card from the top of the deck and adds it to the player's hand.
     * 
     * @param count The number of cards to draw.
     * @return The cards drawn, or null if the deck is empty.
     */
    public List<Card> draw(int count) {
        List<Card> drawnCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (cards.isEmpty()) {
                System.out.println("No more cards to draw from " + type);
                break;
            }
            Card drawnCard = cards.removeLast();
            if (drawnCard == null) {
                break;
            }
            drawnCard.setZone(null); // Clear the card's zone reference
            drawnCards.add(drawnCard);
        }
        return drawnCards;
    }

    /**
     * Searches the deck for cards that match the given condition and returns a list
     * of matching cards.
     * 
     * @param condition A predicate that defines the search condition for the cards.
     * @return A list of cards that match the search condition.
     */
    public List<Card> search(Predicate<Card> condition) {
        return getCards().stream()
                .filter(condition)
                .toList();
    }


    /**
     * Peeks at the top card of the deck without removing them.
     * 
     * @return The top card of the deck, or null if the deck is empty.
     */
    public Card peek() {
        Deque<Card> deck = getCards();
        if (deck.isEmpty()) {
            System.out.println("No cards to peek in " + type);
            return null;
        }
        return deck.peekLast();
    }
    /**
     * Peeks at the top 'count' cards of the deck without removing them.
     * 
     * @param count The number of top cards to peek at.
     * @return A list of the top 'count' cards from the deck, or fewer if the deck
     *         has less than 'count' cards.
     */
    public List<Card> peek(int count) {
        Deque<Card> deck = getCards();
        int actualCount = Math.min(count, deck.size());
        List<Card> result = new ArrayList<>();

        Iterator<Card> iterator = deck.descendingIterator();
        for (int i = 0; i < actualCount; i++) {
            result.add(iterator.next());
        }
        if (result.size() < count) {
            System.out.println("Only " + result.size() + " cards available to peek in " + type);
        }   
        return result;
    }

}
