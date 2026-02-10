package engine.cards.abilities;

public class Cost {
    public enum CostType {
        DON,
        TRASH,
        REST,
        LIFE
    }
    public CostType type;
    public int amount;
}
