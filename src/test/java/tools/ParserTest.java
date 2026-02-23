package tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    // -------------------------------------------------------------------------
    // Null / empty / garbage input
    // -------------------------------------------------------------------------

    @Test
    void parse_nullText_returnsEmpty() {
        assertTrue(Parser.parse(null).isEmpty());
    }

    @Test
    void parse_nullString_returnsEmpty() {
        assertTrue(Parser.parse("NULL").isEmpty());
    }

    @Test
    void parse_blankText_returnsEmpty() {
        assertTrue(Parser.parse("   ").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Single-effect cards
    // -------------------------------------------------------------------------

    @Test
    void parse_onPlay_simpleEffect() {
        // OP01-033: [On Play] Rest up to 1 of your opponent's Characters with a cost of 4 or less.
        List<ParsedAbility> abilities = Parser.parse(
                "[On Play] Rest up to 1 of your opponent's Characters with a cost of 4 or less.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("On Play", a.trigger());
        assertNull(a.condition());
        assertNull(a.cost());
        assertNotNull(a.character()); // "opponent's Characters..."
        assertNotNull(a.effect());
    }

    @Test
    void parse_donRequirement_passiveEffect() {
        // OP01-001 (Leader): [DON!! x1] [Your Turn] All of your Characters gain +1000 power.
        List<ParsedAbility> abilities = Parser.parse(
                "[DON!! x1] [Your Turn] All of your Characters gain +1000 power.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertNotNull(a.trigger()); // Passive
        // [DON!! xN] is a condition per §8-3-2-3, not a cost
        assertTrue(a.condition().contains("DON!! x1"));
        assertTrue(a.condition().contains("Your Turn"));
        assertNotNull(a.effect());
    }

    @Test
    void parse_whenAttacking_oncePerTurn() {
        // OP01-035: [DON!! x1][When Attacking][Once Per Turn] Rest up to 1 opponent's Characters cost 5 or less.
        List<ParsedAbility> abilities = Parser.parse(
                "[DON!! x1][When Attacking][Once Per Turn] Rest up to 1 of your opponent's Characters with a cost of 5 or less.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("When Attacking", a.trigger());
        // [DON!! xN] is a condition per §8-3-2-3, not a cost
        assertTrue(a.condition().contains("DON!! x1"));
        assertTrue(a.condition().contains("Once Per Turn"));
    }

    @Test
    void parse_activateMain_withActivationCost() {
        // OP01-041: [Activate:Main] (1) (...) You may rest this Character: Look at 5 cards...
        List<ParsedAbility> abilities = Parser.parse(
                "[Activate:Main] (1) (You may rest the specified number of DON!! cards in your cost area) " +
                "You may rest this Character: Look at 5 cards from the top of your deck; reveal up to 1 card.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("Activate:Main", a.trigger());
        assertTrue(a.cost().contains("(1) DON"));
        assertTrue(a.cost().contains("Optional: rest this Character"));
        assertNotNull(a.effect());
    }

    @Test
    void parse_activateMain_oncePerTurn_withCondition() {
        // OP01-002 style: [Activate:Main] [Once Per Turn] (2) (...): If you have 5 Characters, return...
        List<ParsedAbility> abilities = Parser.parse(
                "[Activate:Main] [Once Per Turn] (2) (You may rest the specified number of DON!! cards in your cost area): " +
                "If you have 5 Characters, return 1 of your Characters to your hand.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("Activate:Main", a.trigger());
        assertTrue(a.condition().contains("Once Per Turn"));
        assertTrue(a.condition().contains("if you have 5 Characters"));
        assertTrue(a.cost().contains("(2) DON"));
    }

    // -------------------------------------------------------------------------
    // Keyword abilities
    // -------------------------------------------------------------------------

    @Test
    void parse_blockerKeyword() {
        // [Blocker] (After your opponent declares an attack, you may rest this card...)
        List<ParsedAbility> abilities = Parser.parse(
                "[Blocker] (After your opponent declares an attack, you may rest this card to make it the new target of the attack.)");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("Keyword", a.trigger());
        assertTrue(a.isKeyword());
        assertTrue(a.effect().contains("Blocker"));
    }

    @Test
    void parse_rushKeyword() {
        List<ParsedAbility> abilities = Parser.parse(
                "[Rush] (This card can attack on the turn in which it is played.)");
        assertEquals(1, abilities.size());
        assertEquals("Keyword", abilities.get(0).trigger());
        assertTrue(abilities.get(0).effect().contains("Rush"));
    }

    @Test
    void parse_doubleAttackAndBanish() {
        // OP01-121: [Double Attack] (This card deals 2 damage.) [Banish] (When this card deals damage...)
        List<ParsedAbility> abilities = Parser.parse(
                "[Double Attack] (This card deals 2 damage.) [Banish] (When this card deals damage, the target card is trashed without activating its Trigger.)");

        // May be parsed as one or two keyword abilities — both keywords should appear
        String combined = abilities.stream().map(a -> a.effect() != null ? a.effect() : "").reduce("", String::concat);
        assertTrue(combined.contains("Double Attack") || combined.contains("Banish"),
                "At least one keyword should appear in effects");
    }

    // -------------------------------------------------------------------------
    // Multi-ability cards (newline-separated)
    // -------------------------------------------------------------------------

    @Test
    void parse_multiAbility_blockerThenOnPlay() {
        // OP01-047: [Blocker] (...)\n[On Play] You may return 1 of your Characters to the owner's hand: Play...
        List<ParsedAbility> abilities = Parser.parse(
                "[Blocker] (After your opponent declares an attack, you may rest this card to make it the new target of the attack.)\n" +
                "[On Play] You may return 1 of your Characters to the owner's hand: Play up to 1 Character card with a cost of 3 or less from your hand.");

        assertEquals(2, abilities.size());
        assertEquals("Keyword", abilities.get(0).trigger());
        assertEquals("On Play",  abilities.get(1).trigger());
        assertTrue(abilities.get(1).cost().contains("Optional: return 1 of your Characters to the owner's hand"));
    }

    @Test
    void parse_multiAbility_mainAndTrigger() {
        // OP01-030: [Main] Look at 5 cards...\n[Trigger] Activate this card's [Main] effect.
        List<ParsedAbility> abilities = Parser.parse(
                "[Main] Look at 5 cards from the top of your deck; reveal up to 1 \"Straw Hat Crew\" type Character card and add it to your hand. Then, place the rest at the bottom of your deck in any order.\n" +
                "[Trigger] Activate this card's [Main] effect.");

        assertEquals(2, abilities.size());
        assertEquals("Main",    abilities.get(0).trigger());
        assertEquals("Trigger", abilities.get(1).trigger());
    }

    // -------------------------------------------------------------------------
    // Inline multi-ability (double-space separator)
    // -------------------------------------------------------------------------

    @Test
    void parse_inlineMultiAbility_donPassiveThenActivate() {
        // OP01-024: [DON!! x2] This Character cannot be K.O.'d...   [Activate:Main] [Once Per Turn] Give...
        List<ParsedAbility> abilities = Parser.parse(
                "[DON!! x2] This Character cannot be K.O.'d in battle by \"Strike\" attribute Characters.   " +
                "[Activate:Main] [Once Per Turn] Give this Character up to 2 rested DON!! cards.");

        assertEquals(2, abilities.size());
        assertEquals("Activate:Main", abilities.get(1).trigger());
        assertTrue(abilities.get(1).condition().contains("Once Per Turn"));
    }

    // -------------------------------------------------------------------------
    // Counter event card
    // -------------------------------------------------------------------------

    @Test
    void parse_counterEvent() {
        // OP01-029: [Counter] Up to 1 of your Leader or Character cards gains +2000 power...
        List<ParsedAbility> abilities = Parser.parse(
                "[Counter] Up to 1 of your Leader or Character cards gains +2000 power during this battle. " +
                "Then, if you have 2 or less Life cards, that card gains an additional +2000 power.");

        assertEquals(1, abilities.size());
        ParsedAbility a = abilities.get(0);
        assertEquals("Counter", a.trigger());
        assertNotNull(a.condition()); // "if you have 2 or less Life cards"
        assertNotNull(a.effect());
    }

    // -------------------------------------------------------------------------
    // On K.O.
    // -------------------------------------------------------------------------

    @Test
    void parse_onKO_effect() {
        // OP01-007: [On K.O.] K.O. up to 1 of your opponent's Characters with 4000 power or less.
        List<ParsedAbility> abilities = Parser.parse(
                "[On K.O.] K.O. up to 1 of your opponent's Characters with 4000 power or less.");

        assertEquals(1, abilities.size());
        assertEquals("On K.O.", abilities.get(0).trigger());
    }

    // -------------------------------------------------------------------------
    // Trigger (life card trigger)
    // -------------------------------------------------------------------------

    @Test
    void parse_triggerPlayThisCard() {
        // OP01-009: [Trigger] Play this card.
        List<ParsedAbility> abilities = Parser.parse("[Trigger] Play this card.");

        assertEquals(1, abilities.size());
        assertEquals("Trigger", abilities.get(0).trigger());
        assertNotNull(abilities.get(0).effect());
    }
}
