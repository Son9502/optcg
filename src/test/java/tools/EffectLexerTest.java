package tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class EffectLexerTest {

    @BeforeEach
    void clearUnknowns() {
        EffectLexer.unknowns.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns just the TokenType sequence for easy assertion. */
    private List<TokenType> types(List<Token> tokens) {
        return tokens.stream().map(Token::type).collect(Collectors.toList());
    }

    /** Returns the first token with a matching type, or null. */
    private Token first(List<Token> tokens, TokenType type) {
        return tokens.stream().filter(t -> t.type() == type).findFirst().orElse(null);
    }

    // ── Empty / blank input ───────────────────────────────────────────────────

    @Test
    void parseEffect_emptyString_returnsEmptyList() {
        assertTrue(EffectLexer.parseEffect("").isEmpty());
    }

    @Test
    void parseEffect_onlyPunctuation_returnsEmptyList() {
        assertTrue(EffectLexer.parseEffect("...").isEmpty());
    }

    // ── VERB detection ───────────────────────────────────────────────────────

    @Test
    void parseEffect_draw_emitsVerb() {
        List<Token> tokens = EffectLexer.parseEffect("Draw 2 cards.");
        assertTrue(types(tokens).contains(TokenType.VERB));
        Token verb = first(tokens, TokenType.VERB);
        assertNotNull(verb);
        assertEquals("draw", verb.raw());
    }

    @Test
    void parseEffect_koDot_normalisedToVerb() {
        // "K.O." must be normalised to "ko" before split so the periods don't shred it
        List<Token> tokens = EffectLexer.parseEffect("K.O. 1 of your opponent's Characters.");
        Token verb = first(tokens, TokenType.VERB);
        assertNotNull(verb);
        assertEquals("ko", verb.raw());
    }

    @Test
    void parseEffect_chooseOne_twoWordVerb() {
        // "choose one" is a 2-word phrase — must match before "choose" alone
        List<Token> tokens = EffectLexer.parseEffect("Choose one:");
        Token verb = first(tokens, TokenType.VERB);
        assertNotNull(verb);
        assertEquals("choose one", verb.raw());
    }

    // ── QUANTITY detection ────────────────────────────────────────────────────

    @Test
    void parseEffect_bareDigit_emitsQuantityWithIntValue() {
        List<Token> tokens = EffectLexer.parseEffect("Draw 3 cards.");
        Token qty = first(tokens, TokenType.QUANTITY);
        assertNotNull(qty);
        assertEquals(3, qty.value());
    }

    @Test
    void parseEffect_upTo_emitsQuantityPhrase() {
        List<Token> tokens = EffectLexer.parseEffect("Rest up to 1 of your opponent's Characters.");
        List<TokenType> t = types(tokens);
        // Both "up to" (phrase) and "1" (digit) should produce QUANTITY tokens
        assertEquals(2, t.stream().filter(tt -> tt == TokenType.QUANTITY).count());
        // First QUANTITY raw should be "up to"
        Token first = tokens.stream().filter(tt -> tt.type() == TokenType.QUANTITY).findFirst().orElse(null);
        assertNotNull(first);
        assertEquals("up to", first.raw());
    }

    @Test
    void parseEffect_all_emitsQuantity() {
        List<Token> tokens = EffectLexer.parseEffect("Rest all of your opponent's Characters.");
        Token qty = first(tokens, TokenType.QUANTITY);
        assertNotNull(qty);
        assertEquals("all", qty.raw());
    }

    // ── PLAYER detection ──────────────────────────────────────────────────────

    @Test
    void parseEffect_yourOpponents_emitsSinglePlayerToken() {
        // "your opponent's" is a 2-word phrase; must not split into two PLAYER tokens
        List<Token> tokens = EffectLexer.parseEffect("Rest your opponent's Characters.");
        long playerCount = types(tokens).stream().filter(t -> t == TokenType.PLAYER).count();
        assertEquals(1, playerCount);
        Token player = first(tokens, TokenType.PLAYER);
        assertNotNull(player);
        assertEquals("your opponent's", player.raw());
    }

    @Test
    void parseEffect_your_emitsPlayerSelf() {
        // "your" must not be consumed by a zone phrase here — use a plain targeting context
        List<Token> tokens = EffectLexer.parseEffect("Rest your Characters.");
        Token player = first(tokens, TokenType.PLAYER);
        assertNotNull(player);
        assertEquals("your", player.raw());
    }

    // ── CARD_TYPE detection ───────────────────────────────────────────────────

    @Test
    void parseEffect_donCardTwoWords_preferredOverDonAlone() {
        // "don!! card" (2-word phrase) must match before "don!!" (1-word)
        List<Token> tokens = EffectLexer.parseEffect("Add 1 DON!! card from your DON!! deck.");
        Token cardType = first(tokens, TokenType.CARD_TYPE);
        assertNotNull(cardType);
        assertEquals("don!! card", cardType.raw());
    }

    @Test
    void parseEffect_characters_emitsCardType() {
        List<Token> tokens = EffectLexer.parseEffect("Rest 1 of your Characters.");
        Token cardType = first(tokens, TokenType.CARD_TYPE);
        assertNotNull(cardType);
        assertEquals("characters", cardType.raw());
    }

    // ── FILTER — bracket detection ────────────────────────────────────────────

    @Test
    void parseEffect_bracketKeyword_emitsFilterWithKeywordValue() {
        List<Token> tokens = EffectLexer.parseEffect("Your opponent cannot activate [Blocker].");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals("blocker", filter.value());
        assertEquals("[blocker]", filter.raw());
    }

    @Test
    void parseEffect_braceSubtype_emitsFilterWithSubtypeValue() {
        List<Token> tokens = EffectLexer.parseEffect("Play 1 {Straw Hat Crew} Character.");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals("straw hat crew", filter.value());
    }

    @Test
    void parseEffect_parenAttribute_emitsFilterWithAttributeValue() {
        List<Token> tokens = EffectLexer.parseEffect("K.O. 1 (Slash) Character.");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals("slash", filter.value());
    }

    // ── FILTER — regex patterns ───────────────────────────────────────────────

    @Test
    void parseEffect_costFilter_extractsNumericValue() {
        // "with a cost of 4 or less" → FILTER with value 4
        List<Token> tokens = EffectLexer.parseEffect(
                "Rest 1 of your opponent's Characters with a cost of 4 or less.");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals(4, filter.value());
    }

    @Test
    void parseEffect_powerFilter_extractsNumericValue() {
        // "with 5000 power or less" → FILTER with value 5000
        List<Token> tokens = EffectLexer.parseEffect(
                "K.O. 1 of your opponent's Characters with 5000 power or less.");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals(5000, filter.value());
    }

    @Test
    void parseEffect_colorFilter_emitsFilter() {
        List<Token> tokens = EffectLexer.parseEffect("Play 1 red Character.");
        Token filter = first(tokens, TokenType.FILTER);
        assertNotNull(filter);
        assertEquals("red", filter.raw());
    }

    // ── ZONE detection ────────────────────────────────────────────────────────

    @Test
    void parseEffect_fromYourDeck_emitsZone() {
        List<Token> tokens = EffectLexer.parseEffect("Draw 1 card from your deck.");
        Token zone = first(tokens, TokenType.ZONE);
        assertNotNull(zone);
        assertEquals("from your deck", zone.raw());
    }

    @Test
    void parseEffect_fromYourDonDeck_preferredOverFromYourDeck() {
        // "from your don!! deck" must match before "from your deck"
        List<Token> tokens = EffectLexer.parseEffect("Add 1 DON!! card from your DON!! deck.");
        Token zone = first(tokens, TokenType.ZONE);
        assertNotNull(zone);
        assertEquals("from your don!! deck", zone.raw());
    }

    @Test
    void parseEffect_asActive_emitsZone() {
        List<Token> tokens = EffectLexer.parseEffect("Add 1 DON!! card from your DON!! deck and set it as active.");
        List<Token> zones = tokens.stream().filter(t -> t.type() == TokenType.ZONE).collect(Collectors.toList());
        assertTrue(zones.stream().anyMatch(z -> "as active".equals(z.raw())));
    }

    // ── DURATION detection ────────────────────────────────────────────────────

    @Test
    void parseEffect_duringThisBattle_emitsDuration() {
        List<Token> tokens = EffectLexer.parseEffect(
                "Your opponent cannot activate [Blocker] during this battle.");
        Token duration = first(tokens, TokenType.DURATION);
        assertNotNull(duration);
        assertEquals("during this battle", duration.raw());
    }

    @Test
    void parseEffect_untilEndOfTurn_emitsDuration() {
        List<Token> tokens = EffectLexer.parseEffect(
                "Give this Character +1000 power until the end of this turn.");
        Token duration = first(tokens, TokenType.DURATION);
        assertNotNull(duration);
        assertEquals("until the end of this turn", duration.raw());
    }

    // ── CONNECTOR detection ───────────────────────────────────────────────────

    @Test
    void parseEffect_and_emitsConnector() {
        List<Token> tokens = EffectLexer.parseEffect("Draw 2 cards and trash 1 card from your hand.");
        Token connector = first(tokens, TokenType.CONNECTOR);
        assertNotNull(connector);
        assertEquals("and", connector.raw());
    }

    // ── Unknown word logging ──────────────────────────────────────────────────

    @Test
    void parseEffect_unknownWord_addedToUnknownsList() {
        EffectLexer.parseEffect("zzzunknownword");
        assertTrue(EffectLexer.unknowns.contains("zzzunknownword"));
    }

    @Test
    void parseEffect_punctuationOnly_noUnknowns() {
        EffectLexer.parseEffect("...,;;");
        assertTrue(EffectLexer.unknowns.isEmpty());
    }

    // ── Integration: full effect strings ─────────────────────────────────────

    @Test
    void parseEffect_addDonAndSetActive_correctTypeSequence() {
        // "Add 5 DON!! card from your DON!! deck and set it as active."
        // Expected typed tokens (ignoring unknowns like "it"):
        //   VERB, QUANTITY, CARD_TYPE, ZONE, CONNECTOR, VERB, ZONE
        List<Token> tokens = EffectLexer.parseEffect(
                "Add 5 DON!! card from your DON!! deck and set it as active.");
        List<TokenType> t = types(tokens);

        assertTrue(t.contains(TokenType.VERB));
        assertTrue(t.contains(TokenType.QUANTITY));
        assertTrue(t.contains(TokenType.CARD_TYPE));
        assertTrue(t.contains(TokenType.ZONE));
        assertTrue(t.contains(TokenType.CONNECTOR));
    }

    @Test
    void parseEffect_restOpponentCharacterWithCostFilter_allTypesPresent() {
        // "Rest up to 1 of your opponent's Characters with a cost of 4 or less."
        List<Token> tokens = EffectLexer.parseEffect(
                "Rest up to 1 of your opponent's Characters with a cost of 4 or less.");
        List<TokenType> t = types(tokens);

        assertTrue(t.contains(TokenType.VERB));
        assertTrue(t.contains(TokenType.QUANTITY)); // "up to" + "1"
        assertTrue(t.contains(TokenType.PLAYER));
        assertTrue(t.contains(TokenType.CARD_TYPE));
        assertTrue(t.contains(TokenType.FILTER)); // cost filter
    }

    // ── CSV-sourced card text ─────────────────────────────────────────────────
    // Examples taken verbatim from the `example` column of
    // effect_templates_generic.csv and effect_templates_unique.csv.

    @Test
    void csv_addDon_setActive_triggerEffect() {
        // generic CSV — ADD_DON [Trigger]:
        // "Add up to 1 DON!! card from your DON!! deck and set it as active."
        List<Token> tokens = EffectLexer.parseEffect(
                "Add up to 1 DON!! card from your DON!! deck and set it as active.");
        List<TokenType> t = types(tokens);

        Token firstVerb = first(tokens, TokenType.VERB);
        assertNotNull(firstVerb);
        assertEquals("add", firstVerb.raw());
        // "up to" phrase + bare digit "1" both produce QUANTITY
        assertEquals(2, t.stream().filter(tt -> tt == TokenType.QUANTITY).count());
        // 2-word phrase preferred over single "don!!"
        assertEquals("don!! card", first(tokens, TokenType.CARD_TYPE).raw());
        // "from your don!! deck" + "as active"
        assertEquals(2, t.stream().filter(tt -> tt == TokenType.ZONE).count());
        assertTrue(t.contains(TokenType.CONNECTOR)); // "and"
    }

    @Test
    void csv_addDon_thenIfPowerFilter_chainedEffect() {
        // generic CSV — ADD_DON [Main]:
        // "Add up to 1 DON!! card from your DON!! deck and set it as active.
        //  Then, if your opponent has a Character with 6000 power or more,
        //  add up to 1 DON!! card from your DON!! deck and set it as active."
        List<Token> tokens = EffectLexer.parseEffect(
                "Add up to 1 DON!! card from your DON!! deck and set it as active. " +
                "Then, if your opponent has a Character with 6000 power or more, " +
                "add up to 1 DON!! card from your DON!! deck and set it as active.");
        List<TokenType> t = types(tokens);

        assertTrue(t.contains(TokenType.CONDITION));  // "if"
        assertTrue(t.contains(TokenType.CONNECTOR));  // "and" + "then"
        // "with 6000 power or more" regex → FILTER with numeric value
        Token powerFilter = tokens.stream()
                .filter(tk -> tk.type() == TokenType.FILTER && Integer.valueOf(6000).equals(tk.value()))
                .findFirst().orElse(null);
        assertNotNull(powerFilter);
        assertEquals(6000, powerFilter.value());
    }

    @Test
    void csv_draw_andTrash_fromHand() {
        // unique CSV — DRAW [On Play] (effect text after DON!! cost preamble):
        // "Draw 2 cards and trash 1 card from your hand."
        List<Token> tokens = EffectLexer.parseEffect(
                "Draw 2 cards and trash 1 card from your hand.");
        List<TokenType> t = types(tokens);

        // Two verbs: "draw" and "trash"
        List<Token> verbs = tokens.stream()
                .filter(tk -> tk.type() == TokenType.VERB).collect(Collectors.toList());
        assertEquals(2, verbs.size());
        assertEquals("draw", verbs.get(0).raw());
        assertEquals("trash", verbs.get(1).raw());
        // Two bare-digit QUANTITYs: 2 and 1
        assertEquals(2, t.stream().filter(tt -> tt == TokenType.QUANTITY).count());
        assertTrue(t.contains(TokenType.CONNECTOR)); // "and"
        Token zone = first(tokens, TokenType.ZONE);
        assertNotNull(zone);
        assertEquals("from your hand", zone.raw());
    }

    @Test
    void csv_rest_thenDraw_costFilter5() {
        // unique CSV — DRAW [Main] (effect text after DON!! cost preamble):
        // "Rest up to 1 of your opponent's Characters with a cost of 5 or less.
        //  Then, draw 1 card."
        List<Token> tokens = EffectLexer.parseEffect(
                "Rest up to 1 of your opponent's Characters with a cost of 5 or less. " +
                "Then, draw 1 card.");
        List<TokenType> t = types(tokens);

        // First verb is "rest", second is "draw"
        List<Token> verbs = tokens.stream()
                .filter(tk -> tk.type() == TokenType.VERB).collect(Collectors.toList());
        assertEquals(2, verbs.size());
        assertEquals("rest", verbs.get(0).raw());
        assertEquals("draw", verbs.get(1).raw());

        assertEquals("your opponent's", first(tokens, TokenType.PLAYER).raw());
        assertEquals("characters", first(tokens, TokenType.CARD_TYPE).raw());
        assertEquals("then", first(tokens, TokenType.CONNECTOR).raw());

        // "with a cost of 5 or less" → FILTER with value 5
        Token costFilter = tokens.stream()
                .filter(tk -> tk.type() == TokenType.FILTER && Integer.valueOf(5).equals(tk.value()))
                .findFirst().orElse(null);
        assertNotNull(costFilter);
        assertEquals(5, costFilter.value());
    }

    @Test
    void csv_addDon_andRest_twoVerbChain() {
        // generic CSV — ADD_DON [On Play]:
        // "Add up to 1 DON!! card from your DON!! deck and rest it."
        List<Token> tokens = EffectLexer.parseEffect(
                "Add up to 1 DON!! card from your DON!! deck and rest it.");
        List<TokenType> t = types(tokens);

        List<Token> verbs = tokens.stream()
                .filter(tk -> tk.type() == TokenType.VERB).collect(Collectors.toList());
        assertEquals(2, verbs.size());
        assertEquals("add",  verbs.get(0).raw());
        assertEquals("rest", verbs.get(1).raw());

        assertEquals("don!! card",          first(tokens, TokenType.CARD_TYPE).raw());
        assertEquals("from your don!! deck", first(tokens, TokenType.ZONE).raw());
        assertTrue(t.contains(TokenType.CONNECTOR)); // "and"
    }

    @Test
    void csv_ifDonField_giveCardInHand_costReduce() {
        // unique CSV — COST_REDUCE [Passive]:
        // "If the number of DON!! cards on your field is at least 2 less than
        //  the number on your opponent's field, give this card in your hand -3 cost."
        List<Token> tokens = EffectLexer.parseEffect(
                "If the number of DON!! cards on your field is at least 2 less than " +
                "the number on your opponent's field, give this card in your hand -3 cost.");
        List<TokenType> t = types(tokens);

        assertTrue(t.contains(TokenType.CONDITION)); // "if"
        assertTrue(t.contains(TokenType.CARD_TYPE)); // "don!! cards"
        assertEquals("don!! cards", first(tokens, TokenType.CARD_TYPE).raw());
        assertTrue(t.contains(TokenType.ZONE));      // "on your field"
        assertTrue(t.contains(TokenType.VERB));      // "give"
        assertTrue(t.contains(TokenType.PLAYER));    // "your opponent's"
    }
}
