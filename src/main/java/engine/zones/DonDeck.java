package engine.zones;
import engine.player.Player;

import java.util.List;
import java.util.ArrayList;

import engine.cards.DonCard;
public class DonDeck extends Zone {
    public DonDeck(Player owner) {
        super(ZoneType.DON_DECK, owner);
        this.maxSize = 10; // Set the maximum size for the Don Deck
    }
    public DonCard drawDon() {
        if (cards.isEmpty()) {
            return null;
        }
        DonCard drawnCard = (DonCard) this.remove();
        drawnCard.setZone(null); // Clear the card's zone reference
        return drawnCard;
    }
    public List<DonCard> drawDon(int count) {
        List<DonCard> drawnCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DonCard drawnCard = drawDon();
            if (drawnCard != null) {
                drawnCards.add(drawnCard);
            } else {
                break;
            }
        }
        return drawnCards;
    }
}
