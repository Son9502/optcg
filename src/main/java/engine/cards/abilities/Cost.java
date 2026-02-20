package engine.cards.abilities;

public class Cost {
    public enum CostType {
        DON,       // Rest N DON!! from cost area  (§8-3-1-5: symbol ①②③...)
        DON_MINUS, // Return N DON!! to DON!! deck from Leader/Character/cost areas (§8-3-1-6: DON!! −X)
        TRASH,     // Trash a card from hand
        REST,      // Rest this card (e.g. "You may rest this Character:")
        LIFE       // Add/remove cards from Life area
    }
    public CostType type;
    public int amount;
}
