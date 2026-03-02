package tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EffectParserTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses and asserts exactly one ParsedEffect is returned. */
    private ParsedEffect one(String text) {
        List<ParsedEffect> effects = EffectParser.parse(text);
        assertEquals(1, effects.size(),
                "Expected exactly 1 effect for: \"" + text + "\" but got " + effects.size());
        return effects.get(0);
    }

    /** Parses and asserts exactly N ParsedEffects are returned. */
    private List<ParsedEffect> many(String text, int n) {
        List<ParsedEffect> effects = EffectParser.parse(text);
        assertEquals(n, effects.size(),
                "Expected " + n + " effects for: \"" + text + "\" but got " + effects.size());
        return effects;
    }

    // ── DRAW ──────────────────────────────────────────────────────────────────

    @Test
    void parse_draw2Cards_drawValue2() {
        ParsedEffect e = one("Draw 2 cards.");
        assertEquals("DRAW", e.effectType());
        assertEquals(2, e.value());
        assertEquals("SELF", e.targetPlayer());
    }

    @Test
    void parse_draw1Card_drawValue1() {
        ParsedEffect e = one("Draw 1 card.");
        assertEquals("DRAW", e.effectType());
        assertEquals(1, e.value());
    }

    // ── TRASH ─────────────────────────────────────────────────────────────────

    @Test
    void parse_trash1CardFromHand_trashValue1ZoneHand() {
        ParsedEffect e = one("Trash 1 card from your hand.");
        assertEquals("TRASH", e.effectType());
        assertEquals(1, e.value());
        assertEquals("SELF", e.targetPlayer());
        assertEquals("hand", e.targetZone());
    }

    // ── REST ──────────────────────────────────────────────────────────────────

    @Test
    void parse_restUpTo1OpponentCharacter_restMax1Opponent() {
        ParsedEffect e = one("Rest up to 1 of your opponent's Characters.");
        assertEquals("REST", e.effectType());
        assertEquals(0, e.value());
        assertEquals(1, e.max());
        assertEquals("OPPONENT", e.targetPlayer());
        assertEquals("CHARACTER", e.targetCardType());
    }

    // ── KO ────────────────────────────────────────────────────────────────────

    @Test
    void parse_ko1OpponentCharacterCost4orLess_koMax1MaxCost4() {
        ParsedEffect e = one("K.O. 1 of your opponent's Characters with a cost of 4 or less.");
        assertEquals("KO", e.effectType());
        assertEquals(1, e.max());
        assertEquals("OPPONENT", e.targetPlayer());
        assertEquals("CHARACTER", e.targetCardType());
        assertEquals(4, e.maxCost());
    }

    // ── RETURN_TO_HAND ────────────────────────────────────────────────────────

    @Test
    void parse_returnUpTo1CharacterCost5orLess_returnToHandMax1MaxCost5() {
        ParsedEffect e = one("Return up to 1 Character with a cost of 5 or less to the owner's hand.");
        assertEquals("RETURN_TO_HAND", e.effectType());
        assertEquals(1, e.max());
        assertEquals(5, e.maxCost());
    }

    // ── POWER_BOOST ───────────────────────────────────────────────────────────

    @Test
    void parse_givePlus2000PowerThisTurn_powerBoostValue2000DurationThisTurn() {
        ParsedEffect e = one("Give this Character +2000 power during this turn.");
        assertEquals("POWER_BOOST", e.effectType());
        assertEquals(2000, e.value());
        assertEquals("THIS_TURN", e.duration());
    }

    @Test
    void parse_gainsMinus2000Power_powerBoostNegativeValue() {
        ParsedEffect e = one("This Character gains -2000 power during this turn.");
        assertEquals("POWER_BOOST", e.effectType());
        assertEquals(-2000, e.value());
        assertEquals("THIS_TURN", e.duration());
    }

    // ── COST_MODIFY ───────────────────────────────────────────────────────────

    @Test
    void parse_giveMinus2Cost_costModifyValueMinus2() {
        ParsedEffect e = one("Give this Character -2 cost.");
        assertEquals("COST_MODIFY", e.effectType());
        assertEquals(-2, e.value());
    }

    // ── GRANT_KEYWORD ─────────────────────────────────────────────────────────

    @Test
    void parse_gainsBlockerKeyword_grantKeyword() {
        ParsedEffect e = one("Gains the [Blocker] keyword.");
        assertEquals("GRANT_KEYWORD", e.effectType());
    }

    // ── ADD_DON ───────────────────────────────────────────────────────────────

    @Test
    void parse_add1DonCard_addDonValue1() {
        ParsedEffect e = one("Add 1 DON!! card from your DON!! deck.");
        assertEquals("ADD_DON", e.effectType());
        assertEquals(1, e.value());
    }

    // ── Condition: IF_YOU_DO ──────────────────────────────────────────────────

    @Test
    void parse_ifYouDo_drawGetsConditionIfYouDo() {
        ParsedEffect e = one("If you do, draw 1 card.");
        assertEquals("DRAW", e.effectType());
        assertEquals(1, e.value());
        assertEquals("IF_YOU_DO", e.condition());
    }

    // ── Connector: two effects from one clause ────────────────────────────────

    @Test
    void parse_drawAndTrash_twoEffects() {
        List<ParsedEffect> effects = many("Draw 2 cards and trash 1 card from your hand.", 2);
        assertEquals("DRAW",  effects.get(0).effectType());
        assertEquals(2,       effects.get(0).value());
        assertEquals("TRASH", effects.get(1).effectType());
        assertEquals(1,       effects.get(1).value());
    }

    @Test
    void parse_addDonThenSetActive_twoEffects() {
        List<ParsedEffect> effects = many(
                "Add 1 DON!! card from your DON!! deck and set it as active.", 2);
        assertEquals("ADD_DON",       effects.get(0).effectType());
        assertEquals(1,               effects.get(0).value());
        assertEquals("SET_DON_ACTIVE",effects.get(1).effectType());
    }

    // ── Optional flag ─────────────────────────────────────────────────────────

    @Test
    void parse_youMayDraw_optionalTrue() {
        ParsedEffect e = one("You may draw 1 card.");
        assertEquals("DRAW", e.effectType());
        assertTrue(e.optional(), "Effect preceded by 'may' should be optional");
    }

    @Test
    void parse_draw_withoutMay_optionalFalse() {
        ParsedEffect e = one("Draw 1 card.");
        assertFalse(e.optional(), "Mandatory effect should not be optional");
    }
}
