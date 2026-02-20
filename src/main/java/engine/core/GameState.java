package engine.core;

import engine.cards.Card;
import engine.cards.abilities.Cost;
import engine.player.Player;
import engine.cards.DonCard;
import engine.cards.Leader;
import engine.zones.Zone;

public class GameState {
    private Player player1;
    private Player player2;

    private boolean gameOver;
    private Player winner;

    public GameState(Player player1, Player player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.gameOver = false;
        this.winner = null;
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
     * @param player The player who is drawing the card. This is the player whose
     *               deck will
     *               be drawn from and whose hand will receive the drawn card.
     */
    public void draw(Player player) {
        if (player.getDeck().getCards().isEmpty()) {
            System.out.println(player.getName() + " has no more cards to draw.");
            System.out.println((player == player1 ? player2.getName() : player1.getName()) + " wins the game!");
            gameOver = true;
            winner = (player == player1) ? player2 : player1;
            return;
        }
        Card drawnCard = player.getDeck().draw();
        if (drawnCard != null) {
            moveCard(drawnCard, player.getHand());
        }

    }

    /**
     * Draws multiple cards from the top of the deck and adds them to the player's
     * hand.
     * 
     * @param player The player who is drawing the cards. This is the player whose
     *               deck will be drawn from and whose hand will receive the drawn
     *               cards.
     * @param count  The number of cards to draw.
     */
    public void draw(Player player, int count) {
        for (int i = 0; i < count; i++) {
            draw(player);
            if (gameOver) {
                break; // Stop drawing if the game is over
            }
        }
    }

    /**
     * Shuffles the player's deck. This method will randomize the order of the cards
     * in the player's deck.
     *
     * @param player The player whose deck will be shuffled. This is the player
     *               whose deck will be randomized.
     */
    public void shuffle(Player player) {
        player.getDeck().shuffle();
    }

    /**
     * Draws a card from the top of the don deck and adds it to the player's hand.
     * 
     * @param player The player who is drawing the Don cards. This is the player
     *               whose Don deck will be drawn from and whose hand will receive
     *               the drawn cards.
     * @param count  The number of Don cards to draw.
     */
    public void drawDon(Player player, int count) {
        for (int i = 0; i < count; i++) {
            if (player.getDonDeck().getCards().isEmpty()) {
                System.out.println(player.getName() + " has no more Don cards to draw.");
                break;
            }
            DonCard drawnCard = player.getDonDeck().drawDon();
            if (drawnCard != null) {
                moveCard(drawnCard, player.getCost());
            }
        }
    }

    /**
     * Trashes a card. This method will move the specified card to the trash zone.
     * 
     * @param player The player who is trashing the card. This is the player whose
     *               trash zone will receive the trashed card.
     * @param card   The card to be trashed. The card will be removed from its
     *               current
     *               zone
     *               and added to the trash zone.
     */
    public void trash(Player player, Card card) {
        moveCard(card, player.getTrash());
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
    public void playCard(Player player, Card card) {
        if (!player.getHand().getCards().contains(card)) {
            System.out.println(player.getName() + " cannot play " + card.getData().name()
                    + " because it is not in their hand.");
            return;
        }
        // Determine the target zone based on the card type
        Zone targetZone;
        switch (card.getData().cardType()) {
            case Character:
                targetZone = player.getField();
                card.setSummonSick(true); // Cannot attack the turn it is played (Rush bypasses this)
                break;
            case Event:
                targetZone = player.getTrash();
                break;
            case Stage:
                targetZone = player.getStage();
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
    public void payCost(Player player, Cost cost) {

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
        don.setAttached(true);
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
        DonCard don = card.detachDonCard();
        if (don != null) {
            don.setAttached(false);
        }
    }

    /**
     * Detach all Don cards from a target card. This method will check if the
     * specified
     * target card has any attached Don cards and if the player has enough resources
     * to pay the cost of detaching the Don cards. If the conditions are met,
     * it will detach all Don cards from the target card and move them to the cost
     * zone
     * If the conditions are not met, it will print a message indicating that the
     * Don
     * cards cannot be detached.
     * 
     * @param card The target card from which all Don cards will be detached. This
     *             is typically a Character card that has multiple Don cards
     *             attached to it.
     */
    public void detachDonCards(Card card) {
        int attachedDons = card.getAttachedDons().size();
        for (int i = 0; i < attachedDons; i++) {
            detachDon(card);
        }
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
    public void addLife(Player player, Card card) {
        moveCard(card, player.getLife());
    }

    /**
     * Lose a life point. This method will decrease the player's life points by one
     * and move a card from the life zone to the hand.
     * If the player has no more life points,
     * it will print a message indicating that the player has been defeated.
     * 
     */
    public void removeLife(Player player) {
        if (player.getLife().isEmpty()) {
            System.out.println(player.getName() + " has no more life points and has been defeated.");
            System.out.println((player == player1 ? player2.getName() : player1.getName()) + " wins the game!");
            gameOver = true;
            winner = (player == player1) ? player2 : player1;
            return;
        }
        Card cardToRemove = player.getLife().draw();
        moveCard(cardToRemove, player.getHand());
        Leader leader = player.getLeader();
        if (leader != null) leader.takeLife();
    }

    /**
     * Remove a specified amount of life points from a player. This method will
     * decrease the player's life points by the specified amount and move the
     * corresponding number of cards from the life zone to the hand. If the player
     * has no more life points after the removal, it will print a message indicating
     * that the player has been defeated.
     * 
     * @param player The player whose life points will be removed. This is the
     *               player who will lose life points and have cards moved from
     *               their life zone to their hand.
     * @param amount The number of life points to be removed. This is the amount by
     *               which the player's life points will be decreased, and it will
     *               determine how many cards are moved from the life zone to the
     *               hand.
     */
    public void removeLife(Player player, int amount) {
        for (int i = 0; i < amount; i++) {
            if (player.getLife().isEmpty()) {
                System.out.println(player.getName() + " has no more life points and has been defeated.");
                System.out.println((player == player1 ? player2.getName() : player1.getName()) + " wins the game!");
                gameOver = true;
                winner = (player == player1) ? player2 : player1;
                return;
            }
            Card cardToRemove = player.getLife().draw();
            moveCard(cardToRemove, player.getHand());
        }
        Leader leader = player.getLeader();
        if (leader != null) leader.takeLife(amount);
    }

    /**
     * Refresh the Dons card in the cost area by refreshing them.
     * 
     */
    public void refreshDon(Player player) {
        for (Card don : player.getCost().getCards()) {
            don.activate();
        }
    }

    /**
     * Activating the Character hands at the start of a turn and also returning the
     * Don cards attached back to the cost area(just deattaching them)
     * If the target card has no attached
     * Don cards, it will simply be activated if necessary.
     */
    public void refreshField(Player player) {
        for (Card card : player.getField().getCards()) {
            card.setSummonSick(false); // Clear summon sickness at the start of each new turn
            if (card.isRested()) {
                card.activate();
            } else {
                detachDonCards(card);
            }
        }
    }

    /**
     * Refresh the leader at the start of a turn by activating it if it is rested,
     * or
     * detaching all attached Don cards if it is already active. The leader is a
     * special card that can have Don cards attached to it, and this method ensures
     * that
     * it is properly refreshed at the start of each turn. If the leader is rested,
     * it will be activated. If the leader is already active, all attached Don cards
     * will be detached and returned to the cost area. If the leader has no attached
     * Don cards, it will simply be activated if necessary.
     * 
     * @param player The player whose leader will be refreshed. This is the player
     *               whose leader card will be checked and refreshed at the start of
     *               their turn.
     */
    public void refreshLeader(Player player) {
        Leader leader = player.getLeader();
        if (leader == null) return;
        if (leader.isRested()) {
            leader.activate();
        }
        detachDonCards(leader);
    }

    public void resolveBattle(Card attacker, Card defender) {
        // Implement battle resolution logic (e.g., compare power, apply effects)
    }

    public void playCounter(Player player, Card counterCard, Card targetCard) {
        // Implement counter play logic (e.g., check if counter can be played, apply
        // effects)
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public Player getWinner() {
        return winner;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }
}
