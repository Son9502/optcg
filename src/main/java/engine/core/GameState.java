package engine.core;

import engine.cards.Card;
import engine.cards.abilities.Ability;
import engine.cards.abilities.Cost;
import engine.cards.abilities.Trigger;
import engine.cards.effects.ModalSelector;
import engine.cards.effects.TargetSelector;
import engine.cards.keywords.Keyword;
import engine.player.Player;
import engine.cards.DonCard;
import engine.cards.Leader;
import engine.zones.Zone;
import engine.zones.ZoneType;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private Player player1;
    private Player player2;

    private boolean gameOver;
    private Player winner;

    private AbilityManager abilityManager;

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public void setAbilityManager(AbilityManager am) {
        this.abilityManager = am;
    }

    /** Default: auto-select first N candidates (deterministic, no player input). */
    private TargetSelector targetSelector = (candidates, count, prompt) -> candidates.subList(0,
            Math.min(count, candidates.size()));

    public TargetSelector getTargetSelector() {
        return targetSelector;
    }

    public void setTargetSelector(TargetSelector ts) {
        this.targetSelector = ts;
    }

    /** Default: always pick branch 0 (deterministic, no player input). */
    private ModalSelector modalSelector = (choices, prompt) -> 0;

    public ModalSelector getModalSelector() {
        return modalSelector;
    }

    public void setModalSelector(ModalSelector ms) {
        this.modalSelector = ms;
    }

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
     * Moving the card from one Zone to another, but placing it on the bottom of the
     * target zone instead of the top.
     * 
     * @param card   The card to be moved. This is the card that will be removed
     *               from the origin zone and added to the target zone.
     * @param target The zone to which the card will be moved. This is the new
     *               location of the card after it is moved.
     */
    public void moveCardtoBottom(Card card, Zone target) {
        Zone origin = card.getZone();
        if (origin != null) {
            origin.remove(card);
        }
        target.addBottom(card);
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
        boolean wasOnField = card.getZone() != null
                && card.getZone().getType() == ZoneType.CHARACTER;
        moveCard(card, player.getTrash());
        if (wasOnField && abilityManager != null) {
            abilityManager.dispatch(Trigger.ON_KO, card, player);
            card.detachDonCards(); // Detach any Dons from the card as it leaves the field
        }
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
        // Pay the Don cost before moving the card (use effective cost to respect
        // COST_MODIFY)
        int cardCost = card.getEffectiveCost();
        if (cardCost > 0) {
            Cost cost = new Cost();
            cost.type = Cost.CostType.DON;
            cost.amount = cardCost;
            if (!payCost(player, cost)) {
                return;
            }
        }
        // Determine the target zone based on the card type
        Zone targetZone;
        switch (card.getData().cardType()) {
            case CHARACTER:
                if (player.getField().getCards().size() >= 5) {
                    System.out.println("Field is full (5/5). " + card.getData().name() + " cannot be played.");
                    return;
                }
                targetZone = player.getField();
                if (!card.hasKeyword(Keyword.RUSH) && !card.hasKeyword(Keyword.RUSH_CHARACTER)) {
                    card.setSummonSick(true);
                }
                break;
            case EVENT:
                targetZone = player.getTrash();
                break;
            case STAGE:
                if (!player.getStage().isEmpty()) {
                    System.out.println("Stage zone is occupied. " + card.getData().name() + " cannot be played.");
                    return;
                }
                targetZone = player.getStage();
                break;
            default:
                System.out.println("Unknown card type for " + card.getData().name());
                return;
        }
        moveCard(card, targetZone);
        if (abilityManager != null) {
            abilityManager.dispatch(Trigger.ON_PLAY, card, player);
        }
    }

    /**
     * Plays a card for free (no cost payment). Used by effect-driven plays such as
     * PLAY_CARD effects. Follows the same zone-routing and ON_PLAY dispatch as
     * {@link #playCard}, but skips cost payment and the hand-membership check
     * (the caller's {@code resolveTargets} already located the card).
     *
     * @param player The player playing the card.
     * @param card   The card to play.
     */
    public void playCardFree(Player player, Card card) {
        if (card.getData() == null || card.getData().cardType() == null) return;
        Zone targetZone;
        switch (card.getData().cardType()) {
            case CHARACTER:
                if (player.getField().getCards().size() >= 5) return;
                targetZone = player.getField();
                if (!card.hasKeyword(Keyword.RUSH) && !card.hasKeyword(Keyword.RUSH_CHARACTER)) {
                    card.setSummonSick(true);
                }
                break;
            case EVENT:
                targetZone = player.getTrash();
                break;
            case STAGE:
                if (!player.getStage().isEmpty()) return;
                targetZone = player.getStage();
                break;
            default:
                return;
        }
        moveCard(card, targetZone);
        if (abilityManager != null) {
            abilityManager.dispatch(Trigger.ON_PLAY, card, player);
        }
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
    public boolean payCost(Player player, Cost cost) {
        if (cost.type != Cost.CostType.DON) {
            System.out.println("Unsupported cost type: " + cost.type);
            return false;
        }
        List<DonCard> available = new ArrayList<>();
        for (Card c : player.getCost().getCards()) {
            if (c instanceof DonCard don && !don.isRested() && !don.isAttached()) {
                available.add(don);
            }
        }
        if (available.size() < cost.amount) {
            System.out.println(player.getName() + " cannot afford this card (needs " + cost.amount
                    + " Don, has " + available.size() + " available).");
            return false;
        }
        for (int i = 0; i < cost.amount; i++) {
            available.get(i).rest();
        }
        return true;
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
     * @return The card that was moved from the life zone to the hand, or null if
     *         the player has no more life points.
     */
    public Card removeLife(Player player) {
        if (player.getLife().isEmpty()) {
            System.out.println(player.getName() + " has no more life points and has been defeated.");
            System.out.println((player == player1 ? player2.getName() : player1.getName()) + " wins the game!");
            gameOver = true;
            winner = (player == player1) ? player2 : player1;
            return null;
        }
        Card cardToRemove = player.getLife().draw();
        moveCard(cardToRemove, player.getHand());
        Leader leader = player.getLeader();
        if (leader != null)
            leader.takeLife();
        return cardToRemove;
    }

    /**
     * Lose a life point with BANISH — the life card goes to trash instead of hand.
     * Game-over logic is identical to removeLife().
     */
    public void banishLife(Player player) {
        if (player.getLife().isEmpty()) {
            gameOver = true;
            winner = (player == player1) ? player2 : player1;
            return;
        }
        Card cardToRemove = player.getLife().draw();
        moveCard(cardToRemove, player.getTrash());
        Leader leader = player.getLeader();
        if (leader != null)
            leader.takeLife();
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
            card.clearPowerBoosts(); // Clear end-of-turn power modifications
            card.clearCostModifiers(); // Clear end-of-turn cost modifications
            card.clearGrantedKeywords(); // Clear temporarily granted keywords
            for (Ability ability : card.getAbilities())
                ability.resetTurn(); // Reset once-per-turn gate
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
        if (leader == null)
            return;
        leader.clearPowerBoosts(); // Clear end-of-turn power modifications
        leader.clearCostModifiers(); // Clear end-of-turn cost modifications
        for (Ability ability : leader.getAbilities())
            ability.resetTurn(); // Reset once-per-turn gate
        if (leader.isRested()) {
            leader.activate();
        }
        detachDonCards(leader);
    }

    public void resolveBattle(Card attacker, Card defender) {
        // Implement battle resolution logic (e.g., compare power, apply effects)
    }

    public int playCounter(Player player, Card counterCard, Card targetCard) {
        // Implement counter play logic (e.g., check if counter can be played, apply
        // effects)
        // For now, this is a placeholder that simply returns 0 as the counter boost
        // value.
        // The actual implementation will depend on the specific mechanics of counter
        // cards and their effects, which will be developed once we have the card
        // effects system in place.
        return 0; // Placeholder return value
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
