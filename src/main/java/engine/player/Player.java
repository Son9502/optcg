package engine.player;

import engine.cards.Card;
import engine.zones.Zone;
import engine.zones.ZoneType;
import engine.zones.Deck;
import java.util.List;
import engine.cards.types.CardType;
import engine.cards.Leader;

public class Player {
    String player_id;
    String name;

    Deck deck;
    Deck donDeck;
    Zone hand;
    Zone field;
    Zone trash;
    Zone life_zone;
    Zone cost_zone;
    Zone stage;

    int activeDon;
    int restedDon;

    int lifePoints;
    Leader leader;

    public Player(String player_id, String name) {
        this.player_id = player_id;
        this.name = name;
        this.deck = new Deck(this);
        this.donDeck = new Deck(ZoneType.DON_DECK, this);
        this.hand = new Zone(ZoneType.HAND, this);
        this.field = new Zone(ZoneType.CHARACTER, this);
        this.trash = new Zone(ZoneType.TRASH, this);
        this.life_zone = new Zone(ZoneType.LIFE, this);
        this.cost_zone = new Zone(ZoneType.COST, this);
        this.stage = new Zone(ZoneType.STAGE, this);

        this.leader = null;
        this.activeDon = 0;
        this.restedDon = 0;
    }

    public Player() {
        this("default_id", "Default Player");
    }

    // Accessor methods
    public Deck getDeck() {
        return deck;
    }

    public Deck getDonDeck() {
        return donDeck;
    }

    public Zone getHand() {
        return hand;
    }

    public Zone getField() {
        return field;
    }

    public Zone getTrash() {
        return trash;
    }

    public Zone getLife() {
        return life_zone;
    }

    public Zone getCost() {
        return cost_zone;
    }

    public Card getLeader() {
        return leader;
    }

    public String getPlayerId() {
        return player_id;
    }

    public String getName() {
        return name;
    }

    public int getActiveDon() {
        return activeDon;
    }

    public int getRestedDon() {
        return restedDon;
    }

    public int getLifePoints() {
        return leader != null ? leader.getLifePoints() : 0;
    }

    public Zone getStage() {
        return stage;
    }

    // Mutator methods
    public void setLeader(Leader leader) {
        this.leader = leader;
    }

    /**
     * Draws a card from the top of the deck and adds it to the player's hand.
     * 
     * @param n The number of cards to draw.
     */
    public void draw(int n) {
        List<Card> drawnCards = deck.draw(n);
        drawnCards.forEach(card -> {
            hand.add(card);
            card.setZone(hand);
        });
    }

    /**
     * Draws a card from the top of the don deck and adds it to the player's hand.
     * 
     * @param count The number of cards to draw.
     */
    public void drawDon(int count) {
        List<Card> drawnCards = donDeck.draw(count);
        drawnCards.forEach(card -> {
            hand.add(card);
            card.setZone(hand);
        });
    }

    /**
     * Trashes a card. This method will move the specified card to the trash zone.
     * 
     * @param card The card to be trashed. The card will be removed from its current
     *             zone
     *             and added to the trash zone.
     */
    public void trashCard(Card card) {
        if (card.getZone() != null) {
            card.getZone().remove(card);
        }
        trash.add(card);
        card.setZone(trash);
    }

    /**
     * Lose a life point. This method will decrease the player's life points by one
     * and move a card from the life zone to the hand.
     * If the player has no more life points,
     * it will print a message indicating that the player has been defeated.
     */
    public void loseLife() {
        if (leader != null && leader.getLifePoints() > 0) {
            leader.takeLife();
            // Move a card from life to hand
            Card life_card = life_zone.peekTop();
            if (life_card != null) {
                trash.add(life_card);
                life_card.setZone(trash);
            }
            life_card.moveToZone(hand);
        } else {
            System.out.println(name + " has no more life points to lose!");
            System.out.println(name + " has been defeated!");
        }

    }

    /**
     * Gain a life point. This method will increase the player's life points by one
     * and move a card from the hand to the life zone.
     * 
     * @param life The card to be moved from the hand to the life zone when gaining
     *             a life point.
     */
    public void gainLife(Card life_card) {
        if (!hand.getCards().contains(life_card)) {
            System.out.println("Cannot gain life with a card that is not in hand.");
            return;
        }
        hand.moveTo(life_zone, life_card);
        life_card.moveToZone(life_zone);
        lifePoints++;
    }

    /**
     * Play a card from the hand to the appropriate zone based on its type.
     * This method will move the specified card from the hand to the field, stage,
     * or trash zone depending on its type.
     * 
     * @param card The card to be played. The card must be in the player's hand and
     *             will be moved to the appropriate zone based on its type.
     */
    public void playCard(Card card) {
        if (!hand.getCards().contains(card)) {
            System.out.println("Cannot play a card that is not in hand.");
            return;
        }
        switch (card.getData().cardType()) {
            case Character:
                hand.moveTo(field, card);
                card.setZone(field);
                break;
            case Event:
                hand.moveTo(trash, card);
                card.setZone(trash);
                break;
            case Stage:
                hand.moveTo(stage, card);
                card.setZone(stage);
                break;
            default:
                System.out.println("Unknown card type: " + card.getData().cardType());
        }
        card.onEnterZone();
    }
    /**
     * Pay the cost to play a card. This method will check if the player has enough
     * resources to pay the cost of the card and, if so, will move the appropriate
     * number of cards from the hand to the cost zone and decrease the player's active
     * Don count accordingly. If the player does not have enough resources to pay the cost, 
     * it will print a message indicating that the cost cannot be paid.
     * 
     * @param cost The cost to be paid, typically represented as an integer value. 
     * The method will check if the player has enough resources to pay this cost 
     * and will handle the payment process accordingly.
     * 
     * @return true if the cost was successfully paid, false otherwise.
     */
    public boolean payCost(int cost) {
        long available = cost_zone.getCards().stream().filter(c -> !c.isRested()).count();
        if (available < cost) {
            System.out.println("Not enough resources to pay the cost of " + cost);
            return false;
        }
        int rested = 0;
        for (Card c : cost_zone.getCards()) {
            if (!c.isRested() && rested < cost) {
                c.rest();
                rested++;
            }
        }
        return true;
    }
    /**
     * Attach a Don card to a target card. This method will check if the specified Don card 
     * is in the cost zone and is not already rested, and if the target card is valid for attachment 
     * (e.g., a Character card). If the conditions are met, it will attach the Don card to the 
     * target card and remove it from the cost zone. 
     * If the conditions are not met, it will print a message 
     * indicating that the Don card cannot be attached.
     * @param target The target card to which the Don card will be attached. 
     * This is typically a Character card that can have Don cards attached to it.
     * @param donCard The Don card to be attached. This card must be in the cost zone and not rested.
     * @return true if the Don card was successfully attached, false otherwise.
     */
    public boolean attachDon(Card target, Card donCard) {
        if (cost_zone.contains(donCard) && !donCard.isRested() && target != null 
            && target.getData().cardType() == CardType.Character) {
            target.attachDonCard(donCard);
            cost_zone.remove(donCard);
            return true;
        } else {
            System.out.println("Cannot attach Don card that is not in cost zone or is already rested.");
            return false;
        }
    }
    public void refreshCost(){
        for (Card donCard : cost_zone.getCards()) {
            donCard.activate();
        }
    }
    public void refreshField(){
        for (Card card : field.getCards()) {
            Card donCard = card.detachDonCard();
            if (donCard != null) {
                cost_zone.add(donCard);
                donCard.setZone(cost_zone);
            }
        }
    }
}
