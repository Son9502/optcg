package engine.cards;

import engine.zones.Zone;
import engine.zones.ZoneType;
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
    protected boolean summonSick;
    protected List<DonCard> attachedDons;
    protected List<Ability> abilities;

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
        this.summonSick = false;
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

    /**
     * Returns true if this card was played this turn and cannot yet attack.
     * Cleared automatically during the REFRESH phase of the next turn.
     * Note: the Rush keyword bypasses this flag (not yet implemented).
     */
    public boolean isSummonSick() {
        return summonSick;
    }

    public void setSummonSick(boolean summonSick) {
        this.summonSick = summonSick;
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
        DonCard don = attachedDons.remove(attachedDons.size() - 1);
        don.rest(); // Rest the Don card as it is detached
        return don;
    }

    /**
     * Detach all Don cards from this card.
     */
    public void detachDonCards() {
        if (attachedDons.isEmpty()) {
            System.out.println("No Don cards to detach from " + card_id);
            return;
        }
        for (DonCard don : attachedDons) {
            don.rest(); // Rest the Don card as it is detached
        }
        attachedDons.clear();
    }


    /**
     * Get the base power of this card, which is defined in its CardData. This does
     * not include any power boosts from attached Don cards. For the total power
     * including attached Don cards, use getTotalPower() instead.
     * 
     * @return The base power of this card.
     */
    public int getBasePower() {
        return data.power();
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
     * 
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
     * 
     * @return The total number of Don cards attached to this card.
     */
    public int countDon() {
        return attachedDons.size();
    }

    @Override
    public String toString() {
        String card = "Card: [Name=" + data.name() + "\n" + "Power=" + getTotalPower() + "\n" + "Cost=" + data.cost()
                + "\n" + "Description=" + data.description();
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