package engine.cards.effects;

public enum EffectType {

    // ── Card draw / discard ───────────────────────────────────────────────────
    DRAW,               // Draw N cards from your deck
    TRASH,              // Trash N card(s) from hand, field, or opponent's field

    // ── Field manipulation ────────────────────────────────────────────────────
    REST,               // Rest N of a player's Characters (or Leader)
    UNREST,             // Activate (unrest) N of a player's Characters (or Leader)
    KO,                 // K.O. N of a player's Characters meeting a condition

    // ── Power and cost modification ───────────────────────────────────────────
    POWER_BOOST,        // Give +N power to a Leader or Character (until end of turn or permanent)
    POWER_REDUCE,       // Give -N power to an opponent's Character
    COST_REDUCE,        // Give -N cost to an opponent's Character during this turn

    // ── Card movement ─────────────────────────────────────────────────────────
    RETURN_TO_HAND,     // Return N card(s) to a player's hand
    PLAY_CARD,          // Play a card (from hand, deck, or trash; may be free or at cost)
    SEARCH_DECK,        // Look at the top N cards of deck; add some to hand, rest to bottom
    MOVE_TO_BOTTOM_DECK,// Place N card(s) at the bottom of a player's deck
    MOVE_TO_TOP_DECK,   // Place N card(s) at the top of a player's deck

    // ── DON!! management ──────────────────────────────────────────────────────
    ATTACH_DON,         // Give N rested DON!! from your field to your Leader or a Character
    ADD_DON,            // Add N DON!! card(s) from your DON!! deck (rested or active)
    SET_DON_ACTIVE,     // Set N of your DON!! cards as active (refresh them)

    // ── Life zone ─────────────────────────────────────────────────────────────
    ADD_LIFE,           // Place N card(s) into a player's Life area

    // ── Keyword granting ──────────────────────────────────────────────────────
    GRANT_KEYWORD,      // Give a Character a keyword (Rush, Blocker, Double Attack, etc.)

    // ── Prevention / negation ─────────────────────────────────────────────────
    PREVENT_ATTACK,     // Target cannot attack (this turn or during this battle)
    PREVENT_BLOCK,      // Opponent cannot activate a Blocker meeting a condition
    PREVENT_KO,         // Target cannot be K.O.'d (in battle, or by a specific attribute)
    NEGATE,             // Negate the effects of an opponent's card

    // ── Modal ─────────────────────────────────────────────────────────────────
    MODAL,              // "Choose one:" — player selects one of two or more effect lists
}
