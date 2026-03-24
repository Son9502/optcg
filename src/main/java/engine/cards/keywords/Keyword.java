package engine.cards.keywords;

public enum Keyword {
    // ── Rulebook keyword effects (§10-1) ─────────────────────────────────────
    BLOCKER,
    RUSH,
    RUSH_CHARACTER,
    DOUBLE_ATTACK,
    BANISH,
    UNBLOCKABLE,
    TRIGGER,           // [Trigger] on life cards — card property; dispatch handled by Trigger.TRIGGER

    // ── Engine-internal synthetic keywords (not in rulebook) ─────────────────
    // Used as temporary flags by PREVENT_ATTACK / PREVENT_BLOCK / PREVENT_KO effects.
    // Set each turn by EffectFactory; cleared by Card.refreshField().
    CANNOT_ATTACK,
    CANNOT_BLOCK,
    CANNOT_KO,
}
