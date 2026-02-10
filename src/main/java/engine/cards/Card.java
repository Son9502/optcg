package engine.cards;

import engine.zones.Zone;
import engine.player.Player;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import engine.cards.abilities.Ability;

public class Card {
    protected String card_id;
    protected CardData data;
    protected Player owner;
    protected Player controller;
    protected Zone zone;
    protected boolean rested;
    private List<DonCard> attachedDons;
    private List<Ability> abilities;

    public Card(String card_id, CardData data, Player owner) {
        this(card_id, data, owner, null);
    }
    public Card(String card_id, CardData data, Player owner, Zone zone) {
        this.card_id = card_id;
        this.data = data;
        this.owner = owner;
        this.controller = owner;
        this.zone = zone;
        this.rested = false;
        this.attachedDons = new ArrayList<DonCard>();
        this.abilities = new ArrayList<Ability>();
    }

    // Accessor methods
    public String getCardId() {
        return card_id;
    }

    public CardData getData() {
        return data;
    }

    public Player getOwner() {
        return owner;
    }

    public Player getController() {
        return controller;
    }

    public Zone getZone() {
        return zone;
    }

    public boolean isRested() {
        return rested;
    }

    public List<DonCard> getAttachedDons() {
        return Collections.unmodifiableList(attachedDons);
    }

    public List<Ability> getAbilities() {
        return Collections.unmodifiableList(abilities);
    }

    // Mutator methods
    public void setZone(Zone zone) {
        this.zone = zone;
    }
    public void setController(Player controller) {
        this.controller = controller;
    }

    public boolean canPlay() {
        // Implement logic to determine if the card can be played
        return true;
    }

    public void onEnterZone() {
        // Implement logic for when the card enters a zone
    }

    public void onLeaveZone() {
        // Implement logic for when the card leaves a zone
    }

    public void onResolve() {
        // Implement logic for when the card's effect resolves
    }

    public void applyEffects() {
        // Implement logic to apply the card's effects
    }

    /**
     * Rest this card. This is used for cards that can be rested, such as monsters.
     * Resting a card typically means it cannot be used until it is activated again.
     */
    public void rest() {
        this.rested = true;
    }

    /**
     * Activate this card. This is used for cards that can be activated, such as
     * monsters. Activating a card typically means it can be used until it is rested
     * again.
     */
    public void activate() {
        this.rested = false;
    }

    /**
     * Attach a Don card to this card. This is used for cards that can have Don
     * cards attached to them, such as monsters.
     * 
     * @param don The Don card to attach to this card.
     */
    public void attachDonCard(DonCard don) {
        attachedDons.add(don);
    }

    /**
     * Detach a Don card from this card.
     */
    public DonCard detachDonCard() {
        if (attachedDons.isEmpty()) {
            System.out.println("No Don cards to detach from " + card_id);
            return null;
        }
        return attachedDons.remove(attachedDons.size() - 1);
    }

    /**
     * Calculate the total power of this card, including any attached Don cards.
     * This is used for cards that can have Don cards attached to them, such as
     * monsters.
     * 
     * @return The total power of this card, including any attached Don cards.
     */
    public int getTotalPower() {
        int totalPower = data.power();
        for (DonCard don : attachedDons) {
            totalPower += don.getBoost();
        }
        return totalPower;
    }
    /**
     * Count the number of rested Don cards attached to this card. This is used for
     * cards that can have Don cards attached to them, such as monsters.
     * @return The number of rested Don cards attached to this card.
     */
    public int countRestedDon() {
        int count = 0;
        for (DonCard don : attachedDons) {
            if (don.isRested()) {
                count++;
            }
        }
        return count;
    }
    /**
     * Count the total number of Don cards attached to this card. This is used for
     * cards that can have Don cards attached to them, such as monsters.
     * @return The total number of Don cards attached to this card.
     */
    public int countDon() {
        return attachedDons.size();
    }
}