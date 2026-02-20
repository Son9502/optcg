package engine.cards;

import engine.cards.types.CardType;
import engine.player.Player;

/**
 * Creates Card instances (or appropriate subclasses) from CardData records.
 */
public class CardFactory {

    private CardFactory() {
    }

    /**
     * Creates a Card or Leader from the given CardData, owned by the given player.
     * Returns a Leader when cardType is Leader, a plain Card otherwise.
     */
    public static Card createCard(CardData data, Player owner) {
        if (data.cardType() == CardType.Leader) {
            return new Leader(data.id(), data, owner);
        }
        return new Card(data.id(), data, owner);
    }
}
