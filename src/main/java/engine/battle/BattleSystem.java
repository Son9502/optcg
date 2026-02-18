package engine.battle;

import engine.cards.Card;
import engine.cards.Leader;
import engine.core.GameState;
import engine.player.Player;

import java.util.ArrayList;
import java.util.List;

public class BattleSystem {
    private final GameState gameState;

    public BattleSystem(GameState gameState) {
        this.gameState = gameState;
    }

    /**
     * Returns whether a card is eligible to declare an attack.
     * A card can attack if it is not rested.
     *
     * @param attacker The card attempting to attack.
     * @return true if the card can attack, false otherwise.
     */
    public boolean canAttack(Card attacker) {
        return !attacker.isRested();
    }

    /**
     * Returns all valid attackers for the given player: their leader (if not
     * rested) and any non-rested characters on their field.
     *
     * @param player The attacking player.
     * @return List of cards that can legally declare an attack.
     */
    public List<Card> getValidAttackers(Player player) {
        List<Card> attackers = new ArrayList<>();
        if (canAttack(player.getLeader())) {
            attackers.add(player.getLeader());
        }
        for (Card card : player.getField().getCards()) {
            if (canAttack(card)) {
                attackers.add(card);
            }
        }
        return attackers;
    }

    /**
     * Returns all valid attack targets for the given opponent per OP TCG rules:
     * the opponent's leader is always a valid target; rested characters are also
     * valid targets.
     *
     * @param opponent The defending player.
     * @return List of cards that can legally be targeted.
     */
    public List<Card> getValidTargets(Player opponent) {
        List<Card> targets = new ArrayList<>();
        targets.add(opponent.getLeader());
        for (Card card : opponent.getField().getCards()) {
            if (card.isRested()) {
                targets.add(card);
            }
        }
        return targets;
    }

    /**
     * Applies a counter card played by the defending player. Returns the power
     * boost the counter provides. The actual counter value resolution will be
     * implemented once card effects are wired up; for now this delegates to
     * GameState and returns 0.
     *
     * @param defender    The defending player playing the counter.
     * @param counterCard The counter card being played.
     * @param target      The card being defended.
     * @return The power boost granted to the defending card.
     */
    public int applyCounter(Player defender, Card counterCard, Card target) {
        gameState.playCounter(defender, counterCard, target);
        return 0; // Placeholder — counter value resolution pending card effects implementation
    }

    /**
     * Resolves a battle between an attacker and a target. Compares the attacker's
     * total power against the target's total power plus any counter boost. If the
     * attacker wins, the appropriate consequence is applied: the target loses a
     * life card if it is the opponent's leader, or is sent to trash if it is a
     * character. Rests the attacker regardless of outcome.
     *
     * @param attacker     The attacking card.
     * @param target       The defending card.
     * @param counterBoost Additional power added to the defender by counters.
     */
    public void resolve(Card attacker, Card target, int counterBoost) {
        attacker.rest();

        int attackerPower = attacker.getTotalPower();
        int defenderPower = target.getTotalPower() + counterBoost;

        if (attackerPower > defenderPower) {
            Player targetOwner = target.getOwner();
            if (target instanceof Leader) {
                gameState.removeLife(targetOwner);
            } else {
                gameState.trash(targetOwner, target);
            }
        }
        // If attacker power <= defender power, attack fails — no consequence
    }
}
