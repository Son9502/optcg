package engine.cards.effects;

import engine.TestUtils;
import engine.battle.BattleSystem;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.DonCard;
import engine.cards.abilities.Condition;
import engine.cards.types.CardType;
import engine.core.GameState;
import engine.player.Player;
//import engine.cards.effects.Target;
import engine.zones.ZoneType;
import engine.cards.keywords.Keyword;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectFactoryTest {

    private Player source;
    private Player opponent;
    private GameState gs;
    private BattleSystem bs;

    @BeforeEach
    void setUp() {
        source = new Player("p1");
        opponent = new Player("p2");
        gs = new GameState(source, opponent);
        bs = new BattleSystem(gs, null); // battle system not needed for these tests
    }

    // ── local helpers ───────────────────────────────────────────────────────────

    /** Card with CardType.CHARACTER — needed for card-type filters. */
    private Card makeCharCard(Player owner, int power, int cost) {
        CardData data = new CardData("TEST-C", "TEST-SET", "TestCard", "", "", "TestSet",
                null, CardType.CHARACTER, null, null, cost, power, 0, 2000, 0.0, null);
        return new Card("c-char-" + power, data, owner);
    }

    private Card makeStageCard(Player owner, int cost) {
        CardData data = new CardData("TEST-C", "TEST-SET", "TestCard", "", "", "TestSet",
                null, CardType.STAGE, null, null, cost, 0, 0, 0, 0.0, null);
        return new Card("c-stage-" + cost, data, owner);
    }

    private Card makeEventCard(Player owner, int cost) {
        CardData data = new CardData("TEST-C", "TEST-SET", "TestCard", "", "", "TestSet",
                null, CardType.EVENT, null, null, cost, 0, 0, 0, 0.0, null);
        return new Card("c-event-" + cost, data, owner);
    }

    private Target tgt(Target.TargetPlayer player, ZoneType zone, CardType cardType,
            int max, int maxCost, int minCost, int maxPower, int minPower) {
        return new Target(player, zone, cardType, max, maxCost, minCost, maxPower, minPower, null);
    }

    private EffectData eff(EffectType type, int value, Target target, String subtype,
            Condition condition, Duration duration, Keyword keyword, List<List<EffectData>> choices,
            int conditionValue) {
        return new EffectData(type, value, target, subtype, condition, duration, keyword, choices, conditionValue);
    }

    private EffectData eff(EffectType type, int value, Target target,
            Condition condition, Duration duration, Keyword keyword, List<List<EffectData>> choices,
            int conditionValue) {
        return eff(type, value, target, null, condition, duration, keyword, choices, conditionValue);
    }

    private EffectData eff(EffectType type, int value, Target target,
            Condition condition, Duration duration, Keyword keyword, List<List<EffectData>> choices) {
        return eff(type, value, target, null, condition, duration, keyword, choices, 0);
    }

    private DonCard newDon(Player owner) {
        return new DonCard(owner);
    }

    // ── DRAW ────────────────────────────────────────────────────────────────────

    @Test
    void draw_twoCards() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 3000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 2, null, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(2, source.getHand().getCards().size());
        assertEquals(1, source.getDeck().getCards().size());
    }

    @Test
    void draw_zeroValueDefaultsToOne() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 0, null, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    // ── REST ────────────────────────────────────────────────────────────────────

    @Test
    void rest_opponentCharacter() {
        Card c = makeCharCard(opponent, 3000, 0);
        gs.moveCard(c, opponent.getField());
        assertFalse(c.isRested());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.REST, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(c.isRested());
    }

    @Test
    void rest_maxLimitsTargets() {
        Card c1 = makeCharCard(opponent, 3000, 0);
        Card c2 = makeCharCard(opponent, 4000, 0);
        gs.moveCard(c1, opponent.getField());
        gs.moveCard(c2, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.REST, 0, t, null, null, null, null), null)
                .apply(gs, source);

        long restedCount = opponent.getField().getCards().stream()
                .filter(Card::isRested).count();
        assertEquals(1, restedCount, "max=1 should only rest one character");
    }

    // ── UNREST ──────────────────────────────────────────────────────────────────

    @Test
    void unrest_ownCharacter() {
        Card c = makeCharCard(source, 3000, 0);
        gs.moveCard(c, source.getField());
        c.rest();

        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.UNREST, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertFalse(c.isRested());
    }

    // ── KO ──────────────────────────────────────────────────────────────────────

    @Test
    void ko_opponentCharacter_goesToOwnerTrash() {
        Card c = makeCharCard(opponent, 3000, 0);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.KO, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(opponent.getField().getCards().isEmpty());
        assertEquals(1, opponent.getTrash().getCards().size());
    }

    @Test
    void ko_maxCostFilter_excludesExpensiveCard() {
        // makeCharCard creates cards with cost=3; maxCost=2 should exclude them
        Card c = makeCharCard(opponent, 3000, 3); // cost=3
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 2, 0, 0, 0);
        EffectFactory.build(eff(EffectType.KO, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertFalse(opponent.getField().getCards().isEmpty(), "Card should NOT be KO'd (cost too high)");
    }

    // ── TRASH ───────────────────────────────────────────────────────────────────

    @Test
    void trash_ownCardFromHand() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getHand());

        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, null, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.TRASH, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(source.getHand().getCards().isEmpty());
        assertEquals(1, source.getTrash().getCards().size());
    }

    // ── RETURN_TO_HAND ──────────────────────────────────────────────────────────

    @Test
    void returnToHand_opponentCharacter() {
        Card c = makeCharCard(opponent, 3000, 0);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.RETURN_TO_HAND, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(opponent.getField().getCards().isEmpty());
        assertEquals(1, opponent.getHand().getCards().size());
    }

    // ── POWER_BOOST ─────────────────────────────────────────────────────────────

    @Test
    void powerBoost_thisCard() {
        Card sourceCard = makeCharCard(source, 4000, 0);
        gs.moveCard(sourceCard, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.POWER_BOOST, 2000, t, null, null, null, null), sourceCard)
                .apply(gs, source);

        assertEquals(6000, sourceCard.getTotalPower());
    }

    @Test
    void powerBoost_clearedByRefreshField() {
        Card c = makeCharCard(source, 4000, 0);
        gs.moveCard(c, source.getField());
        c.addPowerBoost(2000);
        assertEquals(6000, c.getTotalPower());

        gs.refreshField(source);

        assertEquals(4000, c.getTotalPower(), "Bonus power should be cleared on refresh");
    }

    // ── POWER_REDUCE ────────────────────────────────────────────────────────────

    @Test
    void powerReduce_opponentCharacter() {
        Card c = makeCharCard(opponent, 5000, 0);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.POWER_REDUCE, 2000, t, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(3000, c.getTotalPower());
    }

    // ── COST_MODIFY ─────────────────────────────────────────────────────────────

    @Test
    void costModify_reducesEffectiveCost() {
        Card c = makeCharCard(source, 3000, 3); // base cost = 3
        gs.moveCard(c, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.COST_MODIFY, -2, t, null, null, null, null), c)
                .apply(gs, source);

        assertEquals(1, c.getEffectiveCost(), "Cost should be 3 + (-2) = 1");
    }

    @Test
    void costModify_floorIsZero() {
        Card c = makeCharCard(source, 3000, 3); // base cost = 3
        gs.moveCard(c, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.COST_MODIFY, -5, t, null, null, null, null), c)
                .apply(gs, source);

        assertEquals(0, c.getEffectiveCost(), "Effective cost should not go below 0");
    }

    @Test
    void costModify_clearedByRefreshField() {
        Card c = makeCharCard(source, 3000, 3); // base cost = 3
        gs.moveCard(c, source.getField());
        c.addCostModifier(-2);
        assertEquals(1, c.getEffectiveCost());

        gs.refreshField(source);

        assertEquals(3, c.getEffectiveCost(), "Cost modifier should be cleared on refresh");
    }

    // ── ADD_DON ─────────────────────────────────────────────────────────────────

    @Test
    void addDon_movesFromDonDeckToCostZone() {
        DonCard don = newDon(source);
        gs.moveCard(don, source.getDonDeck());

        EffectFactory.build(eff(EffectType.ADD_DON, 1, null, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(1, source.getCost().getCards().size());
        assertTrue(source.getDonDeck().getCards().isEmpty());
    }

    // ── SET_DON_ACTIVE ──────────────────────────────────────────────────────────

    @Test
    void setDonActive_activatesRestedDon() {
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        don.rest();
        assertTrue(don.isRested());

        EffectFactory.build(eff(EffectType.SET_DON_ACTIVE, 1, null, null, null, null, null), null)
                .apply(gs, source);

        assertFalse(don.isRested());
    }

    @Test
    void setDonActive_skipsAttachedDon() {
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        don.rest();
        don.setAttached(true);

        EffectFactory.build(eff(EffectType.SET_DON_ACTIVE, 1, null, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(don.isRested(), "Attached DON!! should not be activated");
    }

    // ── ATTACH_DON ──────────────────────────────────────────────────────────────

    @Test
    void attachDon_toThisCard() {
        Card character = makeCharCard(source, 3000, 0);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 1, t, null, null, null, null), character)
                .apply(gs, source);

        assertEquals(1, character.getAttachedDons().size());
        assertTrue(don.isAttached());
    }

    @Test
    void attachDon_restedDon_isAttachedToFirstFieldCard() {
        // Rested DON!! in cost zone + RESTED_DON filter → gets attached to first field
        // card.
        Card character = makeCharCard(source, 3000, 0);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        don.rest(); // DON is now rested

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.RESTED_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(don.isAttached(), "Rested DON!! should be attached");
        assertEquals(1, character.getAttachedDons().size());
    }

    @Test
    void attachDon_activeDon_notAttached_whenFilterIsRested() {
        // Active DON!! (not rested) in cost zone + RESTED_DON filter → skipped.
        Card character = makeCharCard(source, 3000, 0);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        // don is NOT rested (default state)

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.RESTED_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertFalse(don.isAttached(), "Active DON!! should not be attached by a RESTED_DON filter");
        assertTrue(character.getAttachedDons().isEmpty());
    }

    @Test
    void attachDon_activeDon_isAttached_whenFilterIsActive() {
        // Active DON!! in cost zone + ACTIVE_DON filter → gets attached.
        Card character = makeCharCard(source, 3000, 0);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        // don is NOT rested (active) — matches ACTIVE_DON filter

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.ACTIVE_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null, null, null), null)
                .apply(gs, source);

        assertTrue(don.isAttached(), "Active DON!! should be attached by ACTIVE_DON filter");
        assertEquals(1, character.getAttachedDons().size());
    }

    // ── ADD_LIFE ────────────────────────────────────────────────────────────────

    @Test
    void addLife_topCardMovesToLifeZone() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getDeck());

        EffectFactory.build(eff(EffectType.ADD_LIFE, 1, null, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(1, source.getLife().getCards().size());
        assertTrue(source.getDeck().getCards().isEmpty());
    }

    // ── Conditions ──────────────────────────────────────────────────────────────

    @Test
    void condition_ifRested_fires_whenSourceCardIsRested() {
        Card sourceCard = makeCharCard(source, 3000, 0);
        gs.moveCard(sourceCard, source.getField());
        sourceCard.rest();
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, Condition.IF_RESTED, null, null, null), sourceCard)
                .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    @Test
    void condition_ifRested_skipped_whenSourceCardIsActive() {
        Card sourceCard = makeCharCard(source, 3000, 0);
        gs.moveCard(sourceCard, source.getField());
        assertFalse(sourceCard.isRested());
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, Condition.IF_RESTED, null, null, null), sourceCard)
                .apply(gs, source);

        assertTrue(source.getHand().getCards().isEmpty());
    }

    @Test
    void condition_null_alwaysFires() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, null, null, null, null), null)
                .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    // ── Null / stub guards ───────────────────────────────────────────────────────

    @Test
    void nullEffectType_doesNotThrow() {
        EffectData e = new EffectData(null, 0, null, null, null, null, null, null, 0);
        assertDoesNotThrow(() -> EffectFactory.build(e, null).apply(gs, source));
    }

    @Test
    void stubEffect_costModify_isNoOp() {
        assertDoesNotThrow(
                () -> EffectFactory.build(eff(EffectType.COST_MODIFY, -2, null, null, null, null, null), null)
                        .apply(gs, source));
    }

    @Test
    void stubEffect_grantKeyword_isNoOp() {
        assertDoesNotThrow(
                () -> EffectFactory.build(eff(EffectType.GRANT_KEYWORD, 0, null, null, null, null, null), null)
                        .apply(gs, source));
    }

    // ── TargetSelector ──────────────────────────────────────────────────────────

    /**
     * Proves the TargetSelector callback is invoked when there are more
     * candidates than the effect limit.
     *
     * The custom selector always picks {@code c1} by reference. Without the
     * selector the default auto-pick would choose {@code c2} (it is first in the
     * field list after addFirst insertion). The two choices diverge, so the
     * surviving card tells us which path was taken.
     */
    @Test
    void targetSelector_invokedWhenCandidatesExceedLimit() {
        Card c1 = makeCharCard(opponent, 1000, 0);
        Card c2 = makeCharCard(opponent, 2000, 0);
        gs.moveCard(c1, opponent.getField());
        gs.moveCard(c2, opponent.getField()); // c2 is now at index 0 (addFirst)

        // Selector that always picks c1 (would be c2 without this override)
        gs.setTargetSelector((candidates, count, prompt) -> List.of(c1));

        // KO up to 1 of opponent's Characters
        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER,
                1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.KO, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);

        // c1 was chosen by the selector and KO'd; c2 survives
        assertFalse(opponent.getField().getCards().contains(c1), "c1 should be KO'd");
        assertTrue(opponent.getField().getCards().contains(c2), "c2 should survive");
        assertTrue(opponent.getTrash().getCards().contains(c1), "c1 should be in trash");
    }
    // -- Grant Keyword
    // ─────────────────────────────────────────────────────────────

    @Test
    void grantKeyword_thisCard() {
        Card sourceCard = TestUtils.makeCard(source, 3000);
        gs.moveCard(sourceCard, source.getField());
        assertFalse(sourceCard.hasKeyword(Keyword.BLOCKER));
        Target t = tgt(Target.TargetPlayer.THIS_CARD, ZoneType.CHARACTER, CardType.CHARACTER, 0, 0, 0, 0, 0);
        EffectData e = eff(EffectType.GRANT_KEYWORD, 0, t, null, Duration.THIS_TURN, Keyword.BLOCKER, null);
        EffectFactory.build(e, sourceCard).apply(gs, source);
        assertTrue(sourceCard.hasKeyword(Keyword.BLOCKER));
        sourceCard.clearGrantedKeywords();
        assertFalse(sourceCard.hasKeyword(Keyword.BLOCKER),
                "Granted keyword should be cleared after clearGrantedKeywords()");
    }
    // --- Search Deck ─────────────────────────────────────────────────────────────

    @Test
    void movetoBottomDeck() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.MOVE_TO_BOTTOM_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().isEmpty());
        assertEquals(c, source.getDeck().getCards().getLast(), "Card should be moved to bottom of deck");
    }

    @Test
    void moveToTopDeck() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.MOVE_TO_TOP_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().isEmpty());
        assertEquals(c, source.getDeck().getCards().getFirst(), "Card should be moved to top of deck");
    }

    @Test
    void searchDeck() {
        for (int i = 0; i < 5; i++) {
            Card c = TestUtils.makeCard(source, 1000 + i);
            gs.moveCard(c, source.getDeck());
        }
        Card searchCard = TestUtils.makeCard(source, 2000);
        gs.moveCard(searchCard, source.getDeck());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.SEARCH_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().contains(searchCard), "Card should be added to hand");

    }

    @Test
    void searchDeck_noCardsInDeck() {
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.SEARCH_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().isEmpty(), "No cards should be added to hand");
    }

    @Test
    void searchDeck_noMatchingCards() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getDeck());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.SEARCH_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().isEmpty(), "No cards should be added to hand");
        assertTrue(source.getDeck().getCards().contains(c), "Card should remain in deck");
    }

    @Test
    void searchDeck_addsSelectedCardToHand() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getDeck());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.SEARCH_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);
        assertTrue(source.getHand().getCards().contains(c), "Card should be added to hand");
    }

    @Test
    void searchDeck_putRestAtTopOfDeck() {
        // Build deck bottom→top: c0..c4, then topCard on top
        List<Card> expectedOrder = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Card c = TestUtils.makeCard(source, 1000 + i);
            gs.moveCard(c, source.getDeck());
            expectedOrder.add(0, c); // mirror addFirst order
        }
        Card topCard = TestUtils.makeCard(source, 2000);
        gs.moveCard(topCard, source.getDeck());
        // deck: [topCard, c4, c3, c2, c1, c0]; expectedOrder = [c4, c3, c2, c1, c0]

        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.SEARCH_DECK, 0, t, null, null, null, null);
        EffectFactory.build(e, null).apply(gs, source);

        assertTrue(source.getHand().getCards().contains(topCard), "Top card should be selected to hand");
        assertEquals(5, source.getDeck().size(), "5 unselected cards should remain in deck");
        assertEquals(expectedOrder, source.getDeck().getCards(), "Rest should be back at top in original order");
    }

    @Test
    void searchDeck_putRestAtBottomOfDeck() {
        // resident card added first — will end up at bottom of the pile
        Card resident = TestUtils.makeCard(source, 500);
        gs.moveCard(resident, source.getDeck());
        for (int i = 0; i < 5; i++) {
            Card c = TestUtils.makeCard(source, 1000 + i);
            gs.moveCard(c, source.getDeck());
        }
        Card topCard = TestUtils.makeCard(source, 2000);
        gs.moveCard(topCard, source.getDeck());
        // deck: [topCard, c4, c3, c2, c1, c0, resident]

        // Step 1: SEARCH_DECK — picks topCard, rest return to top
        Target searchT = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0);
        EffectData search = eff(EffectType.SEARCH_DECK, 0, searchT, null, null, null, null);
        EffectFactory.build(search, null).apply(gs, source);
        // deck: [c4, c3, c2, c1, c0, resident]

        // Step 2: MOVE_TO_BOTTOM_DECK — move the 5 rest cards to bottom
        Target moveT = tgt(Target.TargetPlayer.SELF, ZoneType.DECK, null, 5, 0, 0, 0, 0);
        EffectData move = eff(EffectType.MOVE_TO_BOTTOM_DECK, 0, moveT, null, null, null, null);
        EffectFactory.build(move, null).apply(gs, source);
        // deck: [resident, c4, c3, c2, c1, c0]

        assertTrue(source.getHand().getCards().contains(topCard), "Top card should be in hand");
        assertEquals(resident, source.getDeck().getCards().getFirst(),
                "Resident card should be at top after rest moved to bottom");
        assertEquals(6, source.getDeck().size());
    }

    // -- Prevent Keywords
    // ────────────────────────────────────────────────────────────────
    @Test
    void preventKo_opponentCharacter() {
        Card c = makeCharCard(source, 3000, 0);
        Target t = tgt(Target.TargetPlayer.THIS_CARD, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PREVENT_KO, 0, t, null, null, null, null);
        gs.moveCard(c, source.getField());
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(c.hasKeyword(Keyword.CANNOT_KO), "Character should be marked as prevented from KO");
    }

    @Test
    void preventAttack_opponentCharacter() {
        Card c = makeCharCard(source, 3000, 0);
        Target t = tgt(Target.TargetPlayer.THIS_CARD, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PREVENT_ATTACK, 0, t, null, null, null, null);
        gs.moveCard(c, source.getField());
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(c.hasKeyword(Keyword.CANNOT_ATTACK), "Character should be marked as prevented from attacking");
    }

    @Test
    void preventBlock_opponentCharacter() {
        Card c = makeCharCard(source, 3000, 0);
        Target t = tgt(Target.TargetPlayer.THIS_CARD, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PREVENT_BLOCK, 0, t, null, null, null, null);
        gs.moveCard(c, source.getField());
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(c.hasKeyword(Keyword.UNBLOCKABLE), "Character should be marked as prevented from blocking");
    }

    // -- Play ────────────────────────────────────────────────────────────────
    @Test
    void playCard_fromHand_CharactertoField() {
        Card c = makeCharCard(source, 3000, 0);
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(source.getField().getCards().contains(c), "Character should be moved to field");
        assertTrue(source.getHand().getCards().isEmpty(), "Hand should be empty after playing card");
    }

    @Test
    void playCard_eventCard_goesToTrash() {
        Card c = makeEventCard(source, 3000);
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.EVENT, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(source.getTrash().getCards().contains(c), "Event card should be moved to trash after playing");
        assertTrue(source.getHand().getCards().isEmpty(), "Hand should be empty after playing card");
    }

    @Test
    void playCard_stageCard_goesToStage() {
        Card c = makeStageCard(source, 0);
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.STAGE, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, c).apply(gs, source);
        assertTrue(source.getStage().getCards().contains(c), "Stage card should be moved to stage after playing");
        assertTrue(source.getHand().getCards().isEmpty(), "Hand should be empty after playing card");
    }

    @Test
    void playCard_fromHand_NoCost() {
        for (int i = 0; i < 10; i++) {
            DonCard don = newDon(source);
            gs.moveCard(don, source.getCost());
        }
        Card c = makeCharCard(source, 3000, 10); // cost=10
        gs.moveCard(c, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, c).apply(gs, source);
        int rested = 0;
        for (Card don : source.getCost().getCards()) {
            if (((DonCard) don).isRested()) {
                rested++;
            }
        }
        assertEquals(0, rested, "No DON should be used when playing card with no cost");
    }

    @Test
    void playCard_fromTrash_toField() {
        Card trashCard = makeCharCard(source, 3000, 0);
        gs.moveCard(trashCard, source.getTrash());
        Card otherCard = makeCharCard(source, 4000, 0);
        gs.moveCard(otherCard, source.getField());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.TRASH, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, otherCard).apply(gs, source);
        assertTrue(source.getField().getCards().contains(trashCard), "Character should be moved to field");
        assertTrue(source.getTrash().getCards().isEmpty(), "Trash should be empty after playing card from trash");
    }

    @Test
    void playCard_respectsMaxCost() {
        Card overCard = makeCharCard(source, 3000, 8); // cost=8
        Card maxCard = makeCharCard(source, 3000, 7); // cost=7
        Card otherCard = makeCharCard(source, 4000, 0);
        gs.moveCard(overCard, source.getHand());
        gs.moveCard(maxCard, source.getHand());
        gs.moveCard(otherCard, source.getField());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 7, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, otherCard).apply(gs, source);
        assertTrue(source.getField().getCards().contains(maxCard), "Card at max cost should be played");
        assertFalse(source.getField().getCards().contains(overCard), "Card over max cost should NOT be played");
    }

    @Test
    void playCard_respectsMaxPower() {
        Card overCard = makeCharCard(source, 7000, 0); // power=7000
        Card maxCard = makeCharCard(source, 5000, 0); // power=5000
        Card otherCard = makeCharCard(source, 4000, 0);
        gs.moveCard(overCard, source.getHand());
        gs.moveCard(maxCard, source.getHand());
        gs.moveCard(otherCard, source.getField());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 0, 6000, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, otherCard).apply(gs, source);
        assertTrue(source.getField().getCards().contains(maxCard), "Card at max power should be played");
        assertFalse(source.getField().getCards().contains(overCard), "Card over max power should NOT be played");

    }

    @Test
    void playCard_respectsMinCost() {
        Card underCard = makeCharCard(source, 3000, 2); // cost=2
        Card minCard = makeCharCard(source, 3000, 4); // cost=4
        Card otherCard = makeCharCard(source, 4000, 0);
        gs.moveCard(underCard, source.getHand());
        gs.moveCard(minCard, source.getHand());
        gs.moveCard(otherCard, source.getField());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 3, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, otherCard).apply(gs, source);
        assertTrue(source.getField().getCards().contains(minCard), "Card at min cost should be played");
        assertFalse(source.getField().getCards().contains(underCard), "Card under min cost should NOT be played");

    }

    @Test
    void playCard_respectsMinPower() {
        Card underCard = makeCharCard(source, 1000, 2); // power=1000
        Card minCard = makeCharCard(source, 3000, 4); // power=3000
        Card otherCard = makeCharCard(source, 4000, 0);
        gs.moveCard(underCard, source.getHand());
        gs.moveCard(minCard, source.getHand());
        gs.moveCard(otherCard, source.getField());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 0, 0, 3000);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, otherCard).apply(gs, source);
        assertTrue(source.getField().getCards().contains(minCard), "Card at min power should be played");
        assertFalse(source.getField().getCards().contains(underCard), "Card under min power should NOT be played");

    }

    @Test
    void playCard_fieldFull_doesNotPlay() {
        for (int i = 0; i < 5; i++) {
            Card c = makeCharCard(source, 3000, 0);
            gs.moveCard(c, source.getField());
        }
        Card handCard = makeCharCard(source, 3000, 0);
        gs.moveCard(handCard, source.getHand());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, handCard).apply(gs, source);
        assertFalse(source.getField().getCards().contains(handCard), "Card should NOT be played when field is full");
    }

    @Test
    void playCard_stageFull_doesNotPlay() {
        Card stageCard = makeStageCard(source, 0);
        gs.moveCard(stageCard, source.getHand());
        Card otherStage = makeStageCard(source, 0);
        gs.moveCard(otherStage, source.getStage());
        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.STAGE, 1, 0, 0, 0, 0);
        EffectData e = eff(EffectType.PLAY_CARD, 0, t, null, null, null, null);
        EffectFactory.build(e, stageCard).apply(gs, source);
        assertFalse(source.getStage().getCards().contains(stageCard),
                "Stage card should NOT be played when stage is full");
    }

    // ── MODAL ──────────────────────────────────────────────────────────────────
    @Test
    void modal_chooseFirst() {
        // 1st choice: gain 2000 power
        // 2nd choice: draw 2 cards
        Card sourceCard = makeCharCard(source, 3000, 0);
        EffectData firstOption = eff(EffectType.POWER_BOOST, 2000,
                tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0), null, Duration.THIS_TURN, null, null);
        EffectData secondOption = eff(EffectType.DRAW, 2, tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0),
                null, null, null, null);
        List<List<EffectData>> options = List.of(
                List.of(firstOption),
                List.of(secondOption));
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, options, 0);
        EffectFactory.build(modal, sourceCard).apply(gs, source);
        // Verify first option applied
        assertEquals(5000, sourceCard.getTotalPower(), "Source card should gain 2000 power");
        assertEquals(0, source.getHand().getCards().size(), "No cards should be drawn");
    }

    @Test
    void modal_chooseSecond() {
        // 1st choice: gain 2000 power
        // 2nd choice: draw 2 cards
        EffectData firstOption = eff(EffectType.POWER_BOOST, 2000,
                tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0), null, Duration.THIS_TURN, null, null);
        EffectData secondOption = eff(EffectType.DRAW, 2, tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0),
                null, null, null, null);
        List<List<EffectData>> options = List.of(
                List.of(firstOption),
                List.of(secondOption));
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());
        gs.setModalSelector((choices, prompt) -> 1); // always pick second option
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, options, 0);
        Card sourceCard = makeCharCard(source, 3000, 0);
        EffectFactory.build(modal, sourceCard).apply(gs, source);
        // Verify first option applied
        assertEquals(3000, sourceCard.getTotalPower(), "Source card should gain 2000 power");
        assertEquals(2, source.getHand().getCards().size(), "Two cards should be drawn");
    }

    @Test
    void modal_onlyOneOption() {
        // 1st choice: Rest opponent character
        // 2nd choice: draw 2 cards
        Card opponentChar = makeCharCard(opponent, 3000, 0);
        gs.moveCard(opponentChar, opponent.getField());
        EffectData firstOption = eff(EffectType.REST, 0,
                tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0), null, null,
                null, null);
        EffectData secondOption = eff(EffectType.DRAW, 2, tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0),
                null, null, null, null);
        List<List<EffectData>> options = List.of(
                List.of(firstOption),
                List.of(secondOption));
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, options, 0);
        EffectFactory.build(modal, null).apply(gs, source);
        // Verify first option applied (second option should be auto-chosen since first
        // is not valid)
        assertTrue(opponentChar.isRested(), "Opponent character should be rested");
        assertEquals(0, source.getHand().getCards().size(), "No cards should be drawn");
    }

    // -- Condition Tests
    // ────────────────────────────────────────────────────────────────
    @Test
    void condition_ifLifeCost_fires_when_life_low() {
        for (int i = 0; i < 10; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 2; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_LIFE_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when life is 2 or less");
    }

    @Test
    void condition_ifLifeCost_skips_when_life_high() {
        for (int i = 0; i < 10; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_LIFE_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Should not draw a card when life is 5 or more");
    }

    @Test
    void condition_ifOpponentLifeCost_fires_when_life_low() {
        for (int i = 0; i < 10; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 2; i++) {
            gs.moveCard(TestUtils.makeCard(opponent, 1000), opponent.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_OPPONENT_LIFE_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when life is 2 or less");

    }

    @Test
    void condition_ifOpponentLifeCost_skips_when_life_high() {
        for (int i = 0; i < 10; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(opponent, 1000), opponent.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_OPPONENT_LIFE_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Should not draw a card when life is 4 or more");
    }

    @Test
    void condition_ifActiveDon_fires_when_active_don_low() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 2; i++) {
            opponent.getCost().add(newDon(opponent));
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_DON_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when active Don is 2 or less");
    }

    @Test
    void condition_ifActiveDon_skips_when_active_don_high() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            source.getCost().add(newDon(source)); // source has 5 active DON (> threshold of 3)
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_DON_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Shouldn't draw a card when active Don is more than 3");
    }

    @Test
    void condition_ifCharacterCount_fires_when_character_count_low() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 2; i++) {
            source.getField().add(makeCharCard(source, 3000, 0));
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_CHARACTER_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when character count is 2 or less");

    }

    @Test
    void condition_ifCharacterCount_skips_when_character_count_high() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            source.getField().add(makeCharCard(source, 3000, 0));
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_CHARACTER_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Shouldn't draw a card when character count is 3 or more");
    }

    @Test
    void condition_ifDeckCount_fires_when_deck_low() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_DECK_COUNT, null, null, null, -7);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when deck count is 7 or less");
    }

    @Test
    void condition_ifDeckCount_skips_when_deck_high() {
        for (int i = 0; i < 10; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_DECK_COUNT, null, null, null, -7);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Shouldn't draw a card when deck count is more than 7");
    }

    @Test
    void condition_ifHandCount_fires_when_hand_low() {
        for (int i = 0; i < 2; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        // source hand is empty (0 <= 3 → fires)
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_HAND_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when hand count is 3 or less");

    }

    @Test
    void condition_ifHandCount_skips_when_hand_high() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            source.getHand().add(makeCharCard(source, 3000, 0));
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_HAND_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(5, source.getHand().getCards().size(), "Shouldn't draw a card when hand count is more than 3");
    }

    @Test
    void condition_ifOpponentHandCount_fires_when_hand_low() {
        for (int i = 0; i < 2; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        // opponent hand is empty (0 <= 3 → fires)
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_OPPONENT_HAND_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when opponent hand count is 3 or less");

    }

    @Test
    void condition_ifOpponentHandCount_skips_when_hand_high() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        for (int i = 0; i < 5; i++) {
            opponent.getHand().add(makeCharCard(opponent, 3000, 0)); // opponent has 5 cards (> 3 → skips)
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_OPPONENT_HAND_COUNT, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(),
                "Shouldn't draw a card when opponent hand count is more than 3");
    }

    @Test
    void condition_ifCost_skips_when_cost_high() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        Card charCard = makeCharCard(source, 0, 5); // cost=5 on source's field (5 > 3 → skips)
        gs.moveCard(charCard, source.getField());
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_FIELD_CHARACTER_COST, null, null, null, -3);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(0, source.getHand().getCards().size(), "Shouldn't draw a card when cost is more than 3");
    }

    @Test
    void condition_ifCost_fires_when_cost_low() {
        for (int i = 0; i < 5; i++) {
            gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        }
        Card charCard = makeCharCard(source, 0, 2); // cost=2 on source's field (2 <= 2 → fires)
        gs.moveCard(charCard, source.getField());
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.DRAW, 1, t, Condition.IF_FIELD_CHARACTER_COST, null, null, null, -2);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertEquals(1, source.getHand().getCards().size(), "Should draw a card when cost is 2 or less");
    }

    // -- flipLife Tests -------
    @Test
    void flipLife_topCard_becomesFaceUp() {
        // Add 1 face-down card to life, apply FLIP_LIFE card.isFaceUp() == true
        Card faceCard = TestUtils.makeCard(source, 1000);
        gs.moveCard(faceCard, source.getLife());
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 1, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertTrue(faceCard.isFaceUp(), "Top card of life should become face-up");
    }

    @Test
    void flipLife_flipN_flipsOnlyN() {
        // Add 3 life cards (all face-down), FLIP_LIFE value=2 first 2 are face-up, 3rd
        for (int i = 0; i < 3; i++) {
            Card faceCard = TestUtils.makeCard(source, 1000 + i);
            gs.moveCard(faceCard, source.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 2, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        // still face-down
        assertTrue(source.getLife().getCards().get(0).isFaceUp(), "1st card should be face-up");
        assertTrue(source.getLife().getCards().get(1).isFaceUp(), "2nd card should be face-up");
        assertFalse(source.getLife().getCards().get(2).isFaceUp(), "3rd card should still be face-down");
    }

    @Test
    void flipLife_alreadyFaceUp_togglesFaceDown() {
        // Add 1 face-up card to life, apply FLIP_LIFE card.isFaceUp() == true
        Card faceCard = TestUtils.makeCard(source, 1000);
        faceCard.flip(); // make it face-up
        gs.moveCard(faceCard, source.getLife());
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 1, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertFalse(faceCard.isFaceUp(), "Card should be face-down after flip");
    }

    @Test
    void flipLife_emptyLife_isNoOp() {
        // Empty life zone, apply FLIP_LIFE no throw, life still empty
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 1, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertTrue(source.getLife().getCards().isEmpty(), "Life zone should remain empty");
    }

    @Test
    void flipLife_value0_defaultsToOne() {
        // Add 2 life cards, FLIP_LIFE value=0 only 1st flipped
        for (int i = 0; i < 2; i++) {
            Card faceCard = TestUtils.makeCard(source, 1000 + i);
            gs.moveCard(faceCard, source.getLife());
        }
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.SELF, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 0, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertTrue(source.getLife().getCards().get(0).isFaceUp(), "1st card should be face-up");
        assertFalse(source.getLife().getCards().get(1).isFaceUp(), "2nd card should still be face-down");
    }

    @Test
    void flipLife_targetPlayer_self_vs_opponent() {
        // FLIP_LIFE with Target.TargetPlayer.OPPONENT flips opponent's
        // life, not source's
        Card sourceCard = TestUtils.makeCard(source, 1000);
        Card opponentCard = TestUtils.makeCard(opponent, 2000);
        gs.moveCard(sourceCard, source.getLife());
        gs.moveCard(opponentCard, opponent.getLife());
        Card ifCard = makeEventCard(source, 0);
        Target t = tgt(Target.TargetPlayer.OPPONENT, null, null, 0, 0, 0, 0, 0);
        EffectData effect = eff(EffectType.FLIP_LIFE, 1, t, null, null, null, null, 0);
        EffectFactory.build(effect, ifCard).apply(gs, source);
        assertFalse(sourceCard.isFaceUp(), "Source's life card should remain face-down");
        assertTrue(opponentCard.isFaceUp(), "Opponent's life card should be flipped face-up");
    }
}
