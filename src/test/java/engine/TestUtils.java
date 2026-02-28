package engine;

import engine.battle.BattleSystem;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.abilities.Ability;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Trigger;
import engine.cards.keywords.Keyword;
import engine.cards.types.CardType;
import engine.player.Player;
import java.util.List;

/** Shared factory helpers for unit tests across all packages. */
public final class TestUtils {

    private TestUtils() {
    }
    public static CardData makeCardData(int power) {
        return new CardData("TEST-001", "TEST-SET", "TestCard", "", "", "TestSet",
                null, null, null, null, 0, power, 0, 2000, 0.0, null);
    }

    public static Card makeCard(Player owner, int power) {
        return new Card("c-" + power, makeCardData(power), owner);
    }   
    public static void attack(BattleSystem battleSystem, Card attacker, Card defender, int counterBoost) {
        // First check if the attacker can attack (not rested or summon sick)

        if (attacker.isRested() || attacker.isSummonSick()) {
            System.out.println(attacker.getData().name() + " cannot attack (rested or summon sick)");
            return;
        }
        // Next check if the defender can block (not rested and has a blocker keyword)
        Player defenderPlayer = defender.getOwner();
        boolean hasBlocker = defenderPlayer.getField().getCards().stream()
                .anyMatch(card -> card.hasKeyword(Keyword.BLOCKER) && !card.isRested());
        if (hasBlocker && !attacker.hasKeyword(Keyword.UNBLOCKABLE)) {
            defender = defenderPlayer.getField().getCards().stream()
                    .filter(card -> card.hasKeyword(Keyword.BLOCKER) && !card.isRested())
                    .findFirst()
                    .orElse(defender); // If no valid blocker found, defender remains the same 
        }
        // Determine the outcome of the battle based on effective power after boosts
        battleSystem.resolve(attacker, defender, counterBoost);
        
    }

    /**
     * Creates a Card pre-loaded with a single KEYWORD ability.
     * Used by BattleSystem keyword tests (Rush, Blocker, Double Attack, etc.).
     */
    public static Card makeCardWithKeyword(Player owner, int power, Keyword keyword) {
        AbilityData abilityData = new AbilityData(
                Trigger.KEYWORD, 0, null, false, null, keyword, List.of());
        CardData data = new CardData("TEST-KW", "TEST-SET", "TestCard", "", "", "TestSet",
                null, CardType.Character, null, null, 0, power, 0, null, 0.0, List.of(abilityData));
        Card card = new Card("c-kw-" + power + "-" + keyword, data, owner);
        card.addAbility(new Ability(abilityData));
        return card;
    }
}
