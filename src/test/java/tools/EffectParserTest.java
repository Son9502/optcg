package tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EffectParser}.
 *
 * <p>Each test parses one or more effect clauses via the convenience
 * {@link EffectParser#parse(String)} entry point and asserts that the
 * resulting {@link ParsedEffect} fields match expected engine values.
 *
 * <p>Test coverage (Phase 1 + Phase 2 MVP):
 * <ul>
 *   <li>Core effects: DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST,
 *       COST_MODIFY, GRANT_KEYWORD, ADD_DON, SET_DON_ACTIVE, SHUFFLE_DECK</li>
 *   <li>Connectors: "and" splits into two ParsedEffects; "then" implies IF_YOU_DO</li>
 *   <li>Optional flag: "may" before verb → {@code optional = true}</li>
 *   <li>Conditions: IF_YOU_DO, IF_LIFE_COUNT, IF_DON_COUNT, IF_CHARACTER_COUNT,
 *       IF_LEADER_COLOR, IF_HAND_COUNT, IF_RESTED</li>
 *   <li>Subject-first clauses: "N of your Characters gains +K power"</li>
 *   <li>MODAL branch splitting: "Choose one: • A • B" → [MODAL, A, B]</li>
 * </ul>
 *
 * <p>Helper methods:
 * <ul>
 *   <li>{@link #one(String)} — asserts exactly one effect and returns it</li>
 *   <li>{@link #many(String, int)} — asserts exactly N effects and returns them</li>
 * </ul>
 */
public class EffectParserTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses {@code text} and asserts that exactly one {@link ParsedEffect} is
     * produced, failing with a descriptive message if the count differs.
     *
     * @param text effect clause text to parse
     * @return the single resulting {@link ParsedEffect}
     */
    private ParsedEffect one(String text) {
        List<ParsedEffect> effects = EffectParser.parse(text);
        assertEquals(1, effects.size(),
                "Expected exactly 1 effect for: \"" + text + "\" but got " + effects.size());
        return effects.get(0);
    }

    /**
     * Parses {@code text} and asserts that exactly {@code n} {@link ParsedEffect}
     * objects are produced, failing with a descriptive message otherwise.
     *
     * @param text effect clause text to parse
     * @param n    expected number of effects
     * @return the full list of resulting {@link ParsedEffect} objects
     */
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

    // ── Subject-first clauses ─────────────────────────────────────────────────

    @Test
    void parse_subjectFirst_characterGainsPowerBoost_maxAndValue() {
        ParsedEffect e = one("1 of your Characters gains +1000 power during this turn.");
        assertEquals("POWER_BOOST", e.effectType());
        assertEquals(1000, e.value());
        assertEquals(1, e.max());
        assertEquals("SELF", e.targetPlayer());
        assertEquals("THIS_TURN", e.duration());
    }

    @Test
    void parse_subjectFirst_upToCharacterGainsPowerBoost_maxAndValue() {
        ParsedEffect e = one("Up to 1 of your Leader or Characters gains +1000 power during this turn.");
        assertEquals("POWER_BOOST", e.effectType());
        assertEquals(1000, e.value());
        assertEquals(1, e.max());
        assertEquals("THIS_TURN", e.duration());
    }

    // ── Condition: IF_LIFE_COUNT ──────────────────────────────────────────────

    @Test
    void parse_ifLifeCardsOrLess_conditionIsLifeCount() {
        List<ParsedEffect> effects = many("If you have 1 or less Life cards, draw 1 card.", 1);
        ParsedEffect e = effects.get(0);
        assertEquals("DRAW", e.effectType());
        assertEquals(1, e.value());
        assertEquals("IF_LIFE_COUNT", e.condition());
    }

    @Test
    void parse_ifLifeCardsOrMore_conditionIsLifeCount() {
        List<ParsedEffect> effects = many("If you have 4 or more Life cards, draw 1 card.", 1);
        assertEquals("IF_LIFE_COUNT", effects.get(0).condition());
    }

    // ── Condition: IF_DON_COUNT ───────────────────────────────────────────────

    @Test
    void parse_ifDonCardsOnField_conditionIsDonCount() {
        List<ParsedEffect> effects = many("If you have 8 or more DON!! cards on your field, draw 1 card.", 1);
        assertEquals("IF_DON_COUNT", effects.get(0).condition());
    }

    // ── Condition: IF_CHARACTER_COUNT ─────────────────────────────────────────

    @Test
    void parse_ifCharacterCount_conditionIsCharacterCount() {
        List<ParsedEffect> effects = many("If you have 2 or more Characters, draw 1 card.", 1);
        assertEquals("IF_CHARACTER_COUNT", effects.get(0).condition());
    }

    // ── Condition: IF_LEADER_COLOR ────────────────────────────────────────────

    @Test
    void parse_ifLeaderMulticolored_conditionIsLeaderColor() {
        List<ParsedEffect> effects = many("If your Leader is multicolored, draw 1 card.", 1);
        assertEquals("IF_LEADER_COLOR", effects.get(0).condition());
    }

    // ── Condition: IF_HAND_COUNT ──────────────────────────────────────────────

    @Test
    void parse_ifHandCountOrLess_conditionIsHandCount() {
        List<ParsedEffect> effects = many("If you have 2 or less cards in hand, draw 1 card.", 1);
        assertEquals("IF_HAND_COUNT", effects.get(0).condition());
    }

    // ── Condition: IF_RESTED ──────────────────────────────────────────────────

    @Test
    void parse_ifThisCharacterIsRested_conditionIsRested() {
        List<ParsedEffect> effects = many("If this Character is rested, draw 1 card.", 1);
        assertEquals("IF_RESTED", effects.get(0).condition());
    }

    // ── ATTACH_DON ────────────────────────────────────────────────────────────

    @Test
    void parse_giveUpTo1RestedDonToLeaderOrCharacters_attachDonRestedDon() {
        // ST01-007 Nami pattern — "or" must NOT split into two clauses
        ParsedEffect e = one("Give up to 1 rested DON!! card to your Leader or 1 of your Characters.");
        assertEquals("ATTACH_DON",   e.effectType());
        assertEquals(1,              e.max());
        assertEquals("SELF",         e.targetPlayer());
        assertEquals("RESTED_DON",   e.targetCardType());
        assertNull(e.targetZone(),   "ATTACH_DON zone should be null (cost zone is implicit)");
    }

    @Test
    void parse_give1ActiveDonToLeader_attachDonActiveDon() {
        ParsedEffect e = one("Give 1 active DON!! card to your Leader.");
        assertEquals("ATTACH_DON",  e.effectType());
        assertEquals(1,             e.max());
        assertEquals("SELF",        e.targetPlayer());
        assertEquals("ACTIVE_DON",  e.targetCardType());
    }

    @Test
    void parse_giveRestedDonToLeaderOrCharacters_producesOneEffect() {
        // Verify "or" compound connector does NOT split into multiple effects
        List<ParsedEffect> effects = many(
                "Give up to 1 rested DON!! card to your Leader or 1 of your Characters.", 1);
        assertEquals("ATTACH_DON", effects.get(0).effectType());
    }

    // ── SHUFFLE_DECK ──────────────────────────────────────────────────────────

    @Test
    void parse_shuffleYourDeck_shuffleDeckType() {
        ParsedEffect e = one("Shuffle your deck.");
        assertEquals("SHUFFLE_DECK", e.effectType());
        assertEquals("SELF", e.targetPlayer());
    }

    // ── MODAL branch splitting ────────────────────────────────────────────────

    @Test
    void parse_chooseOne_threeEffects_modalPlusBranches() {
        List<ParsedEffect> effects = many(
                "Choose one: • Draw 2 cards. • Rest up to 1 of your opponent's Characters.", 3);
        assertEquals("MODAL",  effects.get(0).effectType());
        assertEquals("DRAW",   effects.get(1).effectType());
        assertEquals(2,        effects.get(1).value());
        assertEquals("REST",   effects.get(2).effectType());
        assertEquals(1,        effects.get(2).max());
        assertEquals("OPPONENT", effects.get(2).targetPlayer());
    }
}
