package engine.cards.abilities;

public enum Trigger {
    ON_PLAY,                 // [On Play]           — auto effect when card is played (§10-2-6)
    WHEN_ATTACKING,          // [When Attacking]     — auto effect on attack declaration (§10-2-5)
    ON_KO,                   // [On K.O.]            — auto effect when card is K.O.'d on field (§10-2-17)
    ON_BLOCK,                // [On Block]           — auto effect when Blocker is activated (§10-2-15)
    ACTIVATED_MAIN,          // [Activate: Main]     — activate effect during Main Phase (§10-2-2)
    MAIN,                    // [Main]               — Event card effect in Main Phase (§10-2-3)
    COUNTER,                 // [Counter]            — Event card effect in Counter Step (§10-2-4)
    TRIGGER,                 // [Trigger]            — life card trigger on damage (§10-1-5)
    END_OF_YOUR_TURN,        // [End of Your Turn]   — auto effect at End Phase (§10-2-7)
    END_OF_OPPONENTS_TURN,   // [End of Your Opponent's Turn] — auto effect at opponent's End Phase (§10-2-8)
    OPPONENTS_ATTACK,        // [On Your Opponent's Attack]  — auto effect after opponent's When Attacking (§10-2-16)
    KEYWORD,                 // Rush, Blocker, Double Attack, Banish, Unblockable, Rush:Character
    PASSIVE,                 // [DON!! xX] passive with no further trigger bracket
}
