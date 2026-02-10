package engine.core;

import engine.cards.Card;
import engine.cards.abilities.Cost;
import engine.player.Player;
import engine.cards.DonCard;
import engine.zones.Zone;

public class GameState {
    private Player player1;
    private Player player2;

    private Player activePlayer;
    private Phase phase;

    public GameState(Player player1, Player player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.activePlayer = player1; // Player 1 starts first
        this.phase = Phase.START;
    }

    /**
     * Switches the active player and refreshes the field at the start of the new
     * player's turn. This method will toggle the active player between player1 and
     * player2, reset the phase to the START phase, and call the refreshField method
     * to refresh any cards on the field for the new active player.
     */
    public void switchTurn() {
        activePlayer = (activePlayer == player1) ? player2 : player1;
    }

    /**
     * Switches the game phase to the next phase in the sequence. This method will
     * update the current phase of the game according to the defined order of phases
     * (e.g., START -> DON -> MAIN -> BATTLE -> END -> REFRESH -> DRAW -> DON ->
     * ...).
     * It will handle any necessary actions or state changes that should occur when
     * transitioning between phases.
     * The specific actions taken during each phase transition can be implemented
     * within the switch statement, allowing for customized behavior based on the
     * current phase of the game.
     */
    public void switchPhase() {
        switch (phase) {
            case START:
                phase = Phase.DON;
                break;
            case REFRESH:
                phase = Phase.DRAW;
                break;
            case DRAW:
                phase = Phase.DON;
                break;
            case DON:
                phase = Phase.MAIN;
                break;
            case MAIN:
                phase = Phase.BATTLE;
                break;
            case BATTLE:
                phase = Phase.END;
                break;
            case END:
                switchTurn();
                phase = Phase.REFRESH;
                break;
        }
    }

    /**
     * Moving the card from one Zone to another
     * 
     * @param card   The card to be moved. This is the card that will be removed
     *               from the origin zone
     *               and added to the target zone.
     * @param origin The zone from which the card will be moved. This is the current
     *               location of the card
     *               before it is moved.
     * @param target The zone to which the card will be moved. This is the new
     *               location of the card after it is moved.
     */
    public void moveCard(Card card, Zone target) {
        Zone origin = card.getZone();
        if (origin != null) {
            origin.remove(card);
        }
        target.add(card);
        card.setZone(target);
    }

    /**
     * Draws a card from the top of the deck and adds it to the player's hand.
     * 
     * be drawn from and whose hand will receive the drawn card.
     * 
     * @param n The number of cards to draw.
     */
    public void draw(int count) {
        for (int i = 0; i < count; i++) {
            if (activePlayer.getDeck().getCards().isEmpty()) {
                System.out.println(activePlayer.getName() + " has no more cards to draw.");
                break;
            }
            Card drawnCard = activePlayer.getDeck().draw();
            if (drawnCard != null) {
                moveCard(drawnCard, activePlayer.getHand());
            }
        }
    }

    /**
     * Draws a card from the top of the don deck and adds it to the player's hand.
     * 
     * @param count The number of cards to draw.
     */
    public void drawDon(int count) {
        for (int i = 0; i < count; i++) {
            if (activePlayer.getDonDeck().getCards().isEmpty()) {
                System.out.println(activePlayer.getName() + " has no more Don cards to draw.");
                break;
            }
            Card drawnCard = activePlayer.getDonDeck().draw();
            if (drawnCard != null) {
                moveCard(drawnCard, activePlayer.getHand());
            }
        }
    }

    /**
     * Trashes a card. This method will move the specified card to the trash zone.
     * 
     * will receive the trashed card.
     * 
     * @param card The card to be trashed. The card will be removed from its
     *             current
     *             zone
     *             and added to the trash zone.
     */
    public void trash(Card card) {
        moveCard(card, activePlayer.getTrash());
    }

    /**
     * Play a card from the hand to the appropriate zone based on its type.
     * This method will move the specified card from the hand to the field, stage,
     * or trash zone depending on its type.
     * 
     * @param card The card to be played. The card must be in the player's hand
     *             and
     *             will be moved to the appropriate zone based on its type.
     */
    public void playCard(Card card) {
        if (!activePlayer.getHand().getCards().contains(card)) {
            System.out.println(activePlayer.getName() + " cannot play " + card.getData().name()
                    + " because it is not in their hand.");
            return;
        }
        // Determine the target zone based on the card type
        Zone targetZone;
        switch (card.getData().cardType()) {
            case Character:
                targetZone = activePlayer.getField();
                break;
            case Event:
                targetZone = activePlayer.getTrash();
                break;
            case Stage:
                targetZone = activePlayer.getStage();
                break;
            default:
                System.out.println("Unknown card type for " + card.getData().name());
                return;
        }
        moveCard(card, targetZone);
    }

    /**
     * Pay the cost to play a card. This method will check if the player has enough
     * resources to pay the cost of the card and, if so, will move the appropriate
     * number of cards from the hand to the cost zone and decrease the player's
     * active
     * Don count accordingly. If the player does not have enough resources to pay
     * the cost,
     * it will print a message indicating that the cost cannot be paid.
     * 
     * @param cost The cost to be paid, typically represented as an integer value.
     *             The method will check if the player has enough resources to pay
     *             this cost
     *             and will handle the payment process accordingly.
     * 
     * @return true if the cost was successfully paid, false otherwise.
     */
    public void payCost(Cost cost) {

    }

    /**
     * Attach a Don card to a target card. This method will check if the specified
     * Don card
     * is in the cost zone and is not already rested, and if the target card is
     * valid for attachment
     * (e.g., a Character card). If the conditions are met, it will attach the Don
     * card to the
     * target card and remove it from the cost zone.
     * If the conditions are not met, it will print a message
     * indicating that the Don card cannot be attached.
     * 
     * @param card    The target card to which the Don card will be attached. This
     *                is typically
     *                a Character card that can have Don cards attached to it.
     * @param donCard The Don card to be attached. This card must be in the cost
     *                zone and not rested.
     * @return true if the Don card was successfully attached, false otherwise.
     */
    public void attachDon(Card card, DonCard don) {
        card.attachDonCard(don);

    }

    /**
     * Detach a Don card from a target card. This method will check if the specified
     * target card has any attached Don cards and if the player has enough resources
     * to pay the cost of detaching the Don card. If the conditions are met,
     * it will detach the Don card from the target card and move it to the cost zone
     * If the conditions are not met, it will print a message indicating that the
     * Don
     * card cannot be detached.
     * 
     * @param card The target card from which the Don card will be detached.
     *             This is typically a Character card that has Don cards attached
     *             to it.
     */
    public void detachDon(Card card) {
        card.detachDonCard();
    }

    /**
     * Gain a life point. This method will increase the player's life points by one
     * and move a card from the hand to the life zone.
     * 
     * and who will move a card from their hand to their life zone.
     * 
     * @param card The card to be moved from the hand to the life zone.
     * 
     */
    public void addLife(Card card) {
        moveCard(card, activePlayer.getLife());
    }

    /**
     * Lose a life point. This method will decrease the player's life points by one
     * and move a card from the life zone to the hand.
     * If the player has no more life points,
     * it will print a message indicating that the player has been defeated.
     * 
     */
    public void removeLife() {
        if (activePlayer.getLife().isEmpty()) {
            System.out.println(activePlayer.getName() + " has no more life points and has been defeated.");
            return;
        }
        Card cardToRemove = activePlayer.getLife().draw();
        moveCard(cardToRemove, activePlayer.getHand());
    }

    /**
     * Refresh the Dons card in the cost area by refreshing them.
     * 
     */
    public void refreshDon() {
        for (Card don : activePlayer.getCost().getCards()) {
            don.activate();
        }
    }

    /**
     * Activating the Character hands at the start of a turn and also returning the
     * Don cards attached back to the cost area(just deattaching them)
     * If the target card has no attached
     * Don cards, it will simply be activated if necessary.
     */
    public void refreshField() {
        for (Card card : activePlayer.getField().getCards()) {
            if (card.isRested()) {
                card.activate();
            } else {
                for (DonCard don : card.getAttachedDons()) {
                    detachDon(don);
                }
            }
        }
    }

}
