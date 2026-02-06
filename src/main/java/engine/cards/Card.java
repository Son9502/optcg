package engine.cards;
import engine.zones.Zone;
import engine.player.Player;
import java.util.List;
import java.util.ArrayList;
import engine.cards.abilities.Ability;
public class Card {
    String card_id;
    CardData data;
    Player owner;
    Player controller;
    Zone zone;
    Boolean rested;
    List<Card> attachedDonCards;
    List<Ability> abilities;
    public Card(String card_id, CardData data, Player owner) {
        this.card_id = card_id;
        this.data = data;
        this.owner = owner;
        this.controller = owner;
        this.zone = null;
        this.rested = false;
        this.attachedDonCards = new ArrayList<Card>();
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
    public Boolean isRested() {
        return rested;
    }
    public List<Card> getAttachedDonCards() {
        return attachedDonCards;
    }
    public List<Ability> getAbilities() {
        return abilities;
    }
    // Mutator methods
    public void setZone(Zone zone) {
        this.zone = zone;
    }
    public Boolean canPlay(){
        // Implement logic to determine if the card can be played
        return true;
    }
    public void onEnterZone(){
        // Implement logic for when the card enters a zone
    } 
    public void onLeaveZone(){
        // Implement logic for when the card leaves a zone
    }   
    public void onResolve(){
        // Implement logic for when the card's effect resolves
    }
    public void applyEffects(){
        // Implement logic to apply the card's effects
    }
    /**
     * Rest this card. This is used for cards that can be rested, such as monsters. Resting a card typically means it cannot be used until it is activated again.
     */
    public void rest(){
        this.rested = true;
    }
    /**
     * Activate this card. This is used for cards that can be activated, such as monsters. Activating a card typically means it can be used until it is rested again.
     */
    public void activate(){
        this.rested = false;
    }
    /**
     * Attach a Don card to this card. This is used for cards that can have Don cards attached to them, such as monsters.
     * @param donCard The Don card to attach to this card.
     */
    public void attachDonCard(Card donCard){
        attachedDonCards.add(donCard);
    }
    /**
     * Detach a Don card from this card.
     */
    public Card detachDonCard(){
        if (attachedDonCards.isEmpty()) {
            System.out.println("No Don cards to detach from " + card_id);
            return null;
        }
        return attachedDonCards.remove(attachedDonCards.size() - 1);
    }
    /**
     * Calculate the total power of this card, including any attached Don cards. This is used for cards that can have Don cards attached to them, such as monsters.
     * @return The total power of this card, including any attached Don cards.
     */
    public int getTotalPower(){
        int totalPower = data.power();
        for (Card donCard : attachedDonCards) {
            totalPower += donCard.getData().power();
        }
        return totalPower;
    }
    /**
     * Move this card to a new zone. This method will handle removing the card from its current zone and adding it to the new zone, as well as updating the card's zone reference.
     * @param newZone The new zone to move this card to.
     */
    public void moveToZone(Zone newZone){
        newZone.add(this);
        this.zone = newZone;
    }
    public int countRestedDon(){
        int count = 0;
        for (Card donCard : attachedDonCards) {
            if (donCard.isRested()) {
                count++;
            }
        }
        return count;
    }
    public int countDon(){
        return attachedDonCards.size();
    }
}