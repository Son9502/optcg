package engine.cards;

import engine.cards.types.CardType;
import engine.player.Player;

public class DonCard extends Card {
    private CardType type;
    private boolean isAttached;

    public DonCard(String card_id, CardData data, Player owner) {
        super(card_id, data, owner);
        this.type = CardType.Don;
        this.controller = owner; // Don cards are controlled by their owner
    }
    public DonCard(Player owner){
        this(null, null, owner);
    }
    public CardType getCardType() {
        return type;
    }
    @Override
    public void attachDonCard(DonCard don) {
        throw new UnsupportedOperationException("Cannot attach a Don card to another Don card.");
    }
    @Override
    public DonCard detachDonCard() {
        throw new UnsupportedOperationException("Cannot detach a Don card from another Don card.");
    }
    public boolean isDonCard() {
        return this.type == CardType.Don;
    }
    public boolean isAttached() {
        return isAttached;
    }
    public void setAttached(boolean attached) {
        this.isAttached = attached;
    }
    public int getBoost() {
        return 1000; // Placeholder for boost value, can be modified as needed
    }
}