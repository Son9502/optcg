package engine.zones;
import engine.player.Player;
import engine.cards.Card;
import java.util.List;
import java.util.function.Predicate;
import java.util.ArrayList;
public class Deck extends Zone {
    public Deck(Player owner) {
        super(ZoneType.DECK, owner);
    }
    public Deck(ZoneType type, Player owner) {
        super(type, owner);
    }
    /**
     * Draws a card from the top of the deck and adds it to the player's hand.
     * @param count The number of cards to draw.
     * @return The card drawn, or null if the deck is empty.
     */
    public List<Card> draw(int count){
        List<Card> drawnCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (cards.isEmpty()) {
                System.out.println("No more cards to draw from " + type);
                break;
            }
            Card drawnCard = this.drawTop();
            if (drawnCard == null) {
                break;
            }
            drawnCards.add(drawnCard);
        }
        return drawnCards;
    }
    /**
     * Searches the deck for cards that match the given condition and returns a list of matching cards.
     * @param condition A predicate that defines the search condition for the cards.
     * @return A list of cards that match the search condition.
     */
    public List<Card> search(Predicate<Card> condition) {
        return getCards().stream()
                .filter(condition)
                .toList();
    }
    /**
     * Peeks at the top 'count' cards of the deck without removing them.
     * @param count The number of top cards to peek at.
     * @return A list of the top 'count' cards from the deck, or fewer if the deck has less than 'count' cards.
     */
    public List<Card> peekTop(int count) {
        List<Card> topCards = getCards();
        int size = topCards.size();
        int start = Math.max(0, size - count);
        return topCards.subList(start, size);
    }

}
