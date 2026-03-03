package engine.core;

import engine.cards.Card;
import engine.cards.abilities.Ability;
import engine.cards.abilities.Trigger;
import engine.cards.effects.Effect;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectFactory;
import engine.player.Player;

import java.util.List;

/**
 * Dispatches triggered abilities from cards to {@link EffectFactory} for resolution.
 *
 * <p>AbilityManager is the single choke-point between "an event happened" and
 * "effects fire." It is stored on {@link GameState} and set once by the game setup.
 *
 * <p>Dispatch points:
 * <ul>
 *   <li>{@link Trigger#ON_PLAY}               — {@code GameState.playCard()}, after card enters zone</li>
 *   <li>{@link Trigger#WHEN_ATTACKING}         — {@code BattleSystem.resolve()}, before attacker rests</li>
 *   <li>{@link Trigger#ON_KO}                  — {@code GameState.trash()}, when card was in CHARACTER zone</li>
 *   <li>{@link Trigger#END_OF_YOUR_TURN}       — {@code TurnManager.advancePhase(END)}, active player's cards</li>
 *   <li>{@link Trigger#END_OF_OPPONENTS_TURN}  — {@code TurnManager.advancePhase(END)}, opponent's cards</li>
 *   <li>{@link Trigger#TRIGGER}               — TODO (Tier 2): BattleSystem life-damage reveal</li>
 * </ul>
 *
 * <p>{@link Trigger#KEYWORD} abilities are never dispatched here — they are
 * enforced directly by {@code BattleSystem} and {@code CliController}.
 */
public class AbilityManager {

    private final GameState gameState;

    public AbilityManager(GameState gameState) {
        this.gameState = gameState;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Fires all abilities on {@code card} whose trigger matches {@code trigger}.
     *
     * <p>Skips KEYWORD abilities, abilities blocked by the once-per-turn gate,
     * and abilities whose [DON!! xX] passive requirement is not met.
     *
     * @param trigger      the game event that occurred
     * @param card         the card whose abilities are checked
     * @param actingPlayer the player who controls the card (effects target relative to them)
     */
    public void dispatch(Trigger trigger, Card card, Player actingPlayer) {
        if (card == null || trigger == Trigger.KEYWORD) return;
        for (Ability ability : card.getAbilities()) {
            if (ability.getTriggerType() != trigger) continue;
            if (!ability.canActivate()) continue;                    // once-per-turn gate
            if (!meetsDonRequirement(ability, card)) continue;       // [DON!! xX] passive check
            fireAbility(ability, card, actingPlayer);
        }
    }

    /**
     * Fires {@code trigger} for every card on {@code player}'s field and Leader.
     * Used for turn-boundary triggers ({@link Trigger#END_OF_YOUR_TURN},
     * {@link Trigger#END_OF_OPPONENTS_TURN}).
     *
     * <p>Iterates over a snapshot of the field so that abilities that move cards
     * (e.g. TRASH) do not cause {@code ConcurrentModificationException}.
     *
     * @param trigger      the turn-boundary trigger to fire
     * @param player       the player whose cards are checked
     * @param actingPlayer the player whose turn is ending
     */
    public void dispatchAll(Trigger trigger, Player player, Player actingPlayer) {
        for (Card card : List.copyOf(player.getField().getCards())) {
            dispatch(trigger, card, actingPlayer);
        }
        if (player.getLeader() != null) {
            dispatch(trigger, player.getLeader(), actingPlayer);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void fireAbility(Ability ability, Card card, Player actingPlayer) {
        for (EffectData effectData : ability.getEffects()) {
            Effect effect = EffectFactory.build(effectData, card);
            effect.apply(gameState, actingPlayer);
        }
        if (ability.isOncePerTurn()) {
            ability.markUsed();
        }
    }

    /**
     * Returns {@code true} if the card satisfies the [DON!! xX] passive requirement.
     * A {@code donRequired} of 0 always passes.
     */
    private boolean meetsDonRequirement(Ability ability, Card card) {
        return ability.getDonRequired() <= 0 || card.countDon() >= ability.getDonRequired();
    }
}
