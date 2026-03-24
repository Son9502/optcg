package engine.cards.abilities;

public class Cost {
    public enum CostType {
        DON,              // Rest N DON!! from cost area (§8-3-1-5: symbol ①②③...)
        DON_MINUS,        // Return N DON!! to DON!! deck from field/cost areas (§8-3-1-6: DON!! −X)
        DON_REST,         // Rest N DON!! cards from the field (not the cost zone)
        TRASH,            // Trash N cards from hand
        TRASH_SELF,       // Trash this card (the card bearing the ability) — 64 cards
        REST_SELF,        // Rest this card (the card bearing the ability) — 128 cards
        REST_NAMED,       // Rest a field card with a specific name — e.g. "rest 1 of your [Uta] cards" — ~5 cards
        REST_CHARACTER,   // Rest N unnamed Characters on your field — e.g. "rest 2 of your Characters"
        REST_ANY,         // Rest N of any of your field cards (unnamed) — e.g. "rest 2 of your cards"
        RETURN_SELF,      // Return this card to its owner's hand — 8 cards
        PLACE_BOTTOM_SELF,// Place this card at the bottom of the owner's deck — 8 cards
        LIFE_TO_HAND,     // Place N cards from the top of Life area into hand (Big Mom archetype)
        LIFE_TO_TRASH     // Trash N cards from the top of Life area
    }
    public CostType type;
    public int amount;
}
