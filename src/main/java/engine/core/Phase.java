package engine.core;

/**
 * Enum representing the different phases of a turn in the game.
 */
public enum Phase {
    REFRESH, // The REFRESH phase is where players refresh their characters, reset abilities,
             // and prepare for the turn.
    DRAW, // The DRAW phase is where players draw cards from their deck to their hand.
    DON, // The DON phase is where players draw DON cards, which are used to pay costs
         // for playing characters and using abilities.
    MAIN, // The MAIN phase is where players can play character cards, use abilities, and
          // take actions.
    END; // The END phase is where players end their turn and any end-of-turn effects are
        // resolved.
    public boolean isInteractive(){
        return this == MAIN;
    }
    public String getName(){
        return this.name();
    }
}
