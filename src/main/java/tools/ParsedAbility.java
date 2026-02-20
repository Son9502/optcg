package tools;

/**
 * Structured representation of a single card ability extracted from card text.
 * This is the intermediate format written into compiled JSON by CardCompiler
 * and later mapped to runtime AbilityData objects by AbilityFactory.
 *
 * Example card text:
 *   "[On Play] Rest up to 1 of your opponent's Characters with a cost of 4 or less."
 * Produces:
 *   trigger   = "On Play"
 *   condition = null
 *   cost      = null
 *   character = "opponent's Characters with a cost of 4 or less"
 *   effect    = "Rest up to 1 of your opponent's Characters with a cost of 4 or less."
 */
public record ParsedAbility(
        String trigger,     // What causes it: On Play, When Attacking, Activate:Main, Rush, etc.
        String condition,   // When/under what restriction: "Your Turn", "Once Per Turn; if you have 5 Characters"
        String cost,        // What must be paid: "DON!! x1", "(2) DON", "Optional: rest this Character"
        String character,   // Who/what is referenced: target phrases and [CardName] refs
        String effect       // The actual game action text
) {
    /** Returns true if this ability is a standalone keyword (Rush, Blocker, etc.) */
    public boolean isKeyword() {
        return "Keyword".equals(trigger);
    }
}
