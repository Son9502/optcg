package engine.cards.effects;

import java.util.List;

/**
 * Callback used by {@link EffectFactory} when a {@code MODAL} effect requires
 * the player to choose one branch from two or more option lists.
 *
 * <p>The default implementation (stored on {@link engine.core.GameState}) always
 * picks index 0 — this keeps tests deterministic without requiring UI.
 * {@code CliController} replaces it with an interactive menu at game start.
 */
@FunctionalInterface
public interface ModalSelector {

    /**
     * Ask the player to choose one branch.
     *
     * @param choices the list of option branches (each branch is a list of effects)
     * @param prompt  human-readable description of the choice
     * @return the 0-based index of the chosen branch
     */
    int select(List<List<EffectData>> choices, String prompt);
}
