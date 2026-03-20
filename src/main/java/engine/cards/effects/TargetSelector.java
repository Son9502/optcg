package engine.cards.effects;

import engine.cards.Card;
import java.util.List;

/**
 * Callback used by {@link EffectFactory} when player input is required to
 * select targets from a filtered candidate pool.
 *
 * <p>The default implementation (stored on {@link engine.core.GameState}) is a
 * no-input auto-selector that picks the first N candidates — this preserves
 * test determinism without requiring a UI. {@code CliController} replaces it
 * with a real interactive implementation at game start.
 *
 * <p>The selector is only invoked when {@code candidates.size() > count}
 * (i.e., there is an actual choice to make). When all candidates fit within
 * the limit, {@code EffectFactory} bypasses the selector entirely.
 */
@FunctionalInterface
public interface TargetSelector {

    /**
     * Ask the player to choose up to {@code count} cards from {@code candidates}.
     *
     * @param candidates pre-filtered list of valid target cards (size &gt; count)
     * @param count      how many cards to select
     * @param prompt     human-readable description of the choice
     * @return the chosen cards; may be shorter than {@code count} if the player
     *         cancels an optional selection
     */
    List<Card> select(List<Card> candidates, int count, String prompt);
}
