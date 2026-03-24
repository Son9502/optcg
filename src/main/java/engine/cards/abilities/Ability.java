package engine.cards.abilities;

import engine.cards.effects.EffectData;
import engine.cards.keywords.Keyword;
import java.util.List;

/**
 * Runtime wrapper around AbilityData.
 *
 * AbilityData is the static card descriptor — trigger type, cost, condition,
 * effects — loaded once from JSON and shared across all copies of a card.
 * Ability is the live per-card instance: it holds AbilityData and tracks
 * whether the ability has already fired this turn.
 *
 * usedThisTurn gates [Once Per Turn] abilities. It is cleared during the
 * REFRESH phase of the owning card's controller's turn via resetTurn().
 */
public class Ability {

    private final AbilityData data;
    private boolean usedThisTurn;
    private boolean isNegated;
    public Ability(AbilityData data) {
        this.data = data;
        this.usedThisTurn = false;
        this.isNegated = false;
    }

    // ── Static descriptor delegates ──────────────────────────────────────────

    /** When this ability fires (e.g. ON_PLAY, ACTIVATED_MAIN, TRIGGER). */
    public Trigger getTriggerType() {
        return data.triggerType();
    }

    /**
     * Minimum attached DON count required for this ability to be active.
     * Corresponds to the [DON!! xX] condition (§8-3-2-3): a passive condition,
     * not a cost — the DON must be attached, not paid.
     */
    public int getDonRequired() {
        return data.donRequired();
    }

    /** The cost to activate this ability (DON, DON−X, TRASH, REST, etc.). */
    public List<Cost> getCosts() {
        return data.costs();
    }

    /** Whether this ability can only be used once per turn. */
    public boolean isOncePerTurn() {
        return data.isOncePerTurn();
    }

    /**
     * Additional condition that must be true for this ability to be active
     * (e.g. YOUR_TURN, IF_LIFE_COUNT). Evaluation against live game state
     * is the responsibility of the trigger/resolution system, not this class.
     */
    public Condition getCondition() {
        return data.condition();
    }

    /** The ordered list of effects this ability produces when it resolves. */
    public List<EffectData> getEffects() {
        return data.effects();
    }

    /**
     * The keyword this ability represents.
     * Non-null only when {@link #getTriggerType()} == {@link Trigger#KEYWORD}.
     */
    public Keyword getKeyword() {
        return data.keyword();
    }

    /** The underlying static descriptor. */
    public AbilityData getData() {
        return data;
    }

    /**
     * Returns true if this ability's effects are currently negated. When true, the card's
     * effects are considered inactive and will not resolve.
     * @param negated true to negate this card's effects, false to restore them.
     */
    public void setNegated(boolean negated) {
        this.isNegated = negated;
    }

    // ── Runtime state ────────────────────────────────────────────────────────

    /**
     * Returns true if this ability can currently be activated.
     * An ability is blocked only when it is marked once-per-turn and has
     * already been used this turn. All other checks (DON count, Condition,
     * cost availability) are evaluated by the trigger/resolution system with
     * access to live GameState.
     */
    public boolean canActivate() {
        return !(data.isOncePerTurn() && usedThisTurn);
    }

    /**
     * Marks this ability as used this turn. Call immediately after the ability
     * resolves — before returning control to the player.
     */
    public void markUsed() {
        this.usedThisTurn = true;
    }

    /**
     * Clears usedThisTurn. Called during the REFRESH phase of the owning
     * card's controller's turn so the ability is available again next turn.
     */
    public void resetTurn() {
        this.usedThisTurn = false;
        this.isNegated = false;
    }

    public boolean isUsedThisTurn() {
        return usedThisTurn;
    }
    public boolean isNegated() {
        return isNegated;
    }

    @Override
    public String toString() {
        return "Ability[trigger=" + data.triggerType()
                + ", once=" + data.isOncePerTurn()
                + ", used=" + usedThisTurn
                + ", negated=" + isNegated
                + ", effects=" + data.effects().size() + "]";
    }
}
