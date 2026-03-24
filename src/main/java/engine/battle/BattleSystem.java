package engine.battle;

import engine.cards.Card;
import engine.cards.Leader;
import engine.cards.abilities.Trigger;
import engine.cards.keywords.Keyword;
import engine.cards.types.CardType;
import engine.core.AbilityManager;
import engine.core.GameState;
import engine.core.TurnManager;
import engine.player.Player;

import java.util.ArrayList;
import java.util.List;

public class BattleSystem {
    private final GameState gameState;
    private final TurnManager turnManager;

    public BattleSystem(GameState gameState, TurnManager turnManager) {
        this.gameState = gameState;
        this.turnManager = turnManager;
    }

    /**
     * Returns whether a card is eligible to declare an attack.
     * A card can attack if it is not rested.
     *
     * @param attacker The card attempting to attack.
     * @return true if the card can attack, false otherwise.
     */
    public boolean canAttack(Card attacker) {
        return !attacker.isRested() && !attacker.isSummonSick()
                && !attacker.hasKeyword(Keyword.CANNOT_ATTACK);
    }

    /**
     * Calculates the effective power of a card for battle purposes.
     * For the active player's cards, this includes any Don boosts. For the
     * opponent's cards, this is just the base power.
     *
     * @param card The card whose effective power is being calculated.
     * @return The effective power of the card.
     */
    private int getEffectivePower(Card card) {
        // DON!! boost applies only to the active player's cards.
        // Effect-based boosts (bonusPower, bonusBattlePower) apply to both sides.
        if (card.getOwner() == turnManager.getActivePlayer()) {
            return card.getTotalPower(); // base + DON!! + effect boosts
        }
        return card.getPowerWithEffects(); // base + effect boosts, no DON!!
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
     * Returns all valid blockers for the given player: any non-rested characters on
     * their field with the BLOCKER keyword.
     *
     * @param player The defending player.
     * @return List of cards that can legally block an attack. If the list is empty,
     *         the defender has no valid blockers and the attacker can target the
     *         leader directly.
     */
    public List<Card> getValidBlockers(Player player) {
        List<Card> blockers = new ArrayList<>();
        for (Card card : player.getField().getCards()) {
            if (card.hasKeyword(Keyword.BLOCKER) && !card.isRested()) {
                blockers.add(card);
            }
        }
        return blockers;
    }

    /**
     * Returns all valid counter cards in the defender's hand that can be played in
     * response to an attack. For now, this is any card with a KEYWORD ability or a
     * non-zero counter value, but this will be refined once we implement specific
     * counter mechanics based on card effects.
     * 
     * @param player The defending player.
     * @return List of cards that can legally be played as counters.
     */
    public List<Card> getValidCounters(Player player) {
        List<Card> counters = new ArrayList<>();
        for (Card card : player.getHand().getCards()) {
            // This check whether the card can be used as a counter for no specific reason
            // other than to have some valid counter options for testing purposes. It can be
            // removed once we have actual counter mechanics implemented.
            if (card.getData().counter() > 0) {
                counters.add(card);
            }
            // Event cards with a [Counter] ability (Trigger.COUNTER) can be played
            // during the Counter Step.
            else if (card.getAbilities().stream()
                    .anyMatch(a -> a.getTriggerType() == engine.cards.abilities.Trigger.COUNTER)) {
                counters.add(card);
            }
        }
        return counters;
    }

    /**
     * Returns all valid attack targets for the given opponent per OP TCG rules:
     * the opponent's leader is always a valid target; rested characters are also
     * valid targets. If the attacker has {@link Keyword#RUSH_CHARACTER}, the leader
     * is excluded — only rested characters may be targeted.
     *
     * @param opponent The defending player.
     * @param attacker The attacking card; may be null (falls back to no restriction).
     * @return List of cards that can legally be targeted.
     */
    public List<Card> getValidTargets(Player opponent, Card attacker) {
        List<Card> targets = new ArrayList<>();
        boolean rushCharOnly = attacker != null && attacker.hasKeyword(Keyword.RUSH_CHARACTER);
        if (!rushCharOnly) {
            targets.add(opponent.getLeader());
        }
        for (Card card : opponent.getField().getCards()) {
            if (card.isRested()) {
                targets.add(card);
            }
        }
        return targets;
    }

    /**
     * Convenience overload — no attacker restriction applied.
     *
     * @param opponent The defending player.
     * @return List of cards that can legally be targeted.
     */
    public List<Card> getValidTargets(Player opponent) {
        return getValidTargets(opponent, null);
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
        // For now, assume this is a Character counter card. Event counters and other
        // types will be handled once we implement specific counter mechanics based on
        // card effects.
        int counterValue = 0;
        CardType type = counterCard.getData().cardType();
        if (type == null || type == CardType.CHARACTER) {
            counterValue = counterCard.getData().counter();
        } else if (type == CardType.EVENT) {
            AbilityManager am = gameState.getAbilityManager();
            am.dispatch(Trigger.COUNTER, counterCard, defender);
            counterValue = gameState.playCounter(defender, counterCard, target);
        }
        gameState.trash(defender, counterCard);
        return counterValue;
    }

    /**
     * Simulates activating a trigger ability by moving the trigger card to the
     * player's hand and then resolving the trigger.
     * Used by BattleSystem trigger tests to test the effect of a trigger ability
     * when it is
     * 
     * @param owner       The player who owns the trigger card.
     * @param triggerCard The card with the trigger ability to activate.
     */
    public void resolveTrigger(Player owner, Card triggerCard) {
        AbilityManager abilityManager = gameState.getAbilityManager();
        abilityManager.dispatch(Trigger.TRIGGER, triggerCard, owner);
        gameState.trash(owner, triggerCard);
    }

    /**
     * Resolves a battle between an attacker and a target. Compares the attacker's
     * effective power against the target's effective power plus any counter boost.
     * If the
     * attacker wins, the appropriate consequence is applied: the target loses a
     * life card if it is the opponent's leader, or is sent to trash if it is a
     * character. Rests the attacker regardless of outcome.
     *
     * @param attacker     The attacking card.
     * @param target       The defending card.
     * @param counterBoost Additional power added to the defender by counters.
     */
    public List<Card> resolve(Card attacker, Card target, int counterBoost) {
        AbilityManager am = gameState.getAbilityManager();
        List<Card> lives = new ArrayList<>();

        // CANNOT_ATTACK: abort before resting or firing any triggers
        if (attacker.hasKeyword(Keyword.CANNOT_ATTACK)) {
            return lives;
        }

        // Fire WHEN_ATTACKING before the attacker rests (effects happen on attack declaration)
        if (am != null) {
            am.dispatch(Trigger.WHEN_ATTACKING, attacker, attacker.getOwner());
        }
        attacker.rest();

        // UNBLOCKABLE: if a Character intercepted, redirect the attack to the leader
        Card effectiveTarget = target;
        if (attacker.hasKeyword(Keyword.UNBLOCKABLE) && !(target instanceof Leader)) {
            effectiveTarget = target.getOwner().getLeader();
        }
        if (effectiveTarget == null) return lives;

        int attackerPower = getEffectivePower(attacker);
        int defenderPower = getEffectivePower(effectiveTarget) + counterBoost;
        if (attackerPower >= defenderPower) {
            Player targetOwner = effectiveTarget.getOwner();
            if (effectiveTarget instanceof Leader) {
                boolean banish = attacker.hasKeyword(Keyword.BANISH);
                if (banish) {
                    gameState.banishLife(targetOwner);
                } else {
                    Card removedCard = gameState.removeLife(targetOwner);
                    lives.add(removedCard);
                }
                if (attacker.hasKeyword(Keyword.DOUBLE_ATTACK)) {
                    if (banish) {
                        gameState.banishLife(targetOwner);
                    } else {
                        Card removedCard = gameState.removeLife(targetOwner);
                        lives.add(removedCard);
                    }
                }
            } else {
                // CANNOT_KO: character survives a battle it would otherwise lose
                if (!effectiveTarget.hasKeyword(Keyword.CANNOT_KO)) {
                    gameState.trash(targetOwner, effectiveTarget);
                    effectiveTarget.detachDonCards();
                }
            }
        }
        // If attacker power < defender power, attack fails — no consequence

        // Clear THIS_BATTLE boosts on the direct battle participants first (covers cards
        // that left the field due to KO and won't be reached by clearAllBattleBoosts).
        attacker.clearBattlePowerBoosts();
        if (effectiveTarget != null) effectiveTarget.clearBattlePowerBoosts();
        // Sweep remaining field cards (handles counter boosts on non-targeted cards).
        clearAllBattleBoosts();
        return lives;
    }

    /** Clears battle-scoped power boosts from both players' leaders and field characters. */
    private void clearAllBattleBoosts() {
        for (Player player : List.of(gameState.getPlayer1(), gameState.getPlayer2())) {
            if (player.getLeader() != null) player.getLeader().clearBattlePowerBoosts();
            for (Card c : player.getField().getCards()) {
                c.clearBattlePowerBoosts();
            }
        }
    }
}
