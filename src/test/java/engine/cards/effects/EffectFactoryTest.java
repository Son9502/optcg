package engine.cards.effects;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.DonCard;
import engine.cards.abilities.Condition;
import engine.cards.types.CardType;
import engine.core.GameState;
import engine.player.Player;
import engine.zones.ZoneType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectFactoryTest {

    private Player source;
    private Player opponent;
    private GameState gs;

    @BeforeEach
    void setUp() {
        source   = new Player("p1");
        opponent = new Player("p2");
        gs       = new GameState(source, opponent);
    }

    // ── local helpers ───────────────────────────────────────────────────────────

    /** Card with CardType.CHARACTER — needed for card-type filters. */
    private Card makeCharCard(Player owner, int power) {
        CardData data = new CardData("TEST-C", "TEST-SET", "TestCard", "", "", "TestSet",
                null, CardType.CHARACTER, null, null, 3, power, 0, 2000, 0.0, null);
        return new Card("c-char-" + power, data, owner);
    }

    private Target tgt(Target.TargetPlayer player, ZoneType zone, CardType cardType,
                       int max, int maxCost, int minCost, int maxPower, int minPower) {
        return new Target(player, zone, cardType, max, maxCost, minCost, maxPower, minPower);
    }

    private EffectData eff(EffectType type, int value, Target target,
                           Condition condition, Duration duration) {
        return new EffectData(type, value, target, condition, duration);
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

        EffectFactory.build(eff(EffectType.DRAW, 2, null, null, null), null)
                     .apply(gs, source);

        assertEquals(2, source.getHand().getCards().size());
        assertEquals(1, source.getDeck().getCards().size());
    }

    @Test
    void draw_zeroValueDefaultsToOne() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 0, null, null, null), null)
                     .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    // ── REST ────────────────────────────────────────────────────────────────────

    @Test
    void rest_opponentCharacter() {
        Card c = makeCharCard(opponent, 3000);
        gs.moveCard(c, opponent.getField());
        assertFalse(c.isRested());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.REST, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(c.isRested());
    }

    @Test
    void rest_maxLimitsTargets() {
        Card c1 = makeCharCard(opponent, 3000);
        Card c2 = makeCharCard(opponent, 4000);
        gs.moveCard(c1, opponent.getField());
        gs.moveCard(c2, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.REST, 0, t, null, null), null)
                     .apply(gs, source);

        long restedCount = opponent.getField().getCards().stream()
                .filter(Card::isRested).count();
        assertEquals(1, restedCount, "max=1 should only rest one character");
    }

    // ── UNREST ──────────────────────────────────────────────────────────────────

    @Test
    void unrest_ownCharacter() {
        Card c = makeCharCard(source, 3000);
        gs.moveCard(c, source.getField());
        c.rest();

        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.UNREST, 0, t, null, null), null)
                     .apply(gs, source);

        assertFalse(c.isRested());
    }

    // ── KO ──────────────────────────────────────────────────────────────────────

    @Test
    void ko_opponentCharacter_goesToOwnerTrash() {
        Card c = makeCharCard(opponent, 3000);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.KO, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(opponent.getField().getCards().isEmpty());
        assertEquals(1, opponent.getTrash().getCards().size());
    }

    @Test
    void ko_maxCostFilter_excludesExpensiveCard() {
        // makeCharCard creates cards with cost=3; maxCost=2 should exclude them
        Card c = makeCharCard(opponent, 3000); // cost=3
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 2, 0, 0, 0);
        EffectFactory.build(eff(EffectType.KO, 0, t, null, null), null)
                     .apply(gs, source);

        assertFalse(opponent.getField().getCards().isEmpty(), "Card should NOT be KO'd (cost too high)");
    }

    // ── TRASH ───────────────────────────────────────────────────────────────────

    @Test
    void trash_ownCardFromHand() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getHand());

        Target t = tgt(Target.TargetPlayer.SELF, ZoneType.HAND, null, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.TRASH, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(source.getHand().getCards().isEmpty());
        assertEquals(1, source.getTrash().getCards().size());
    }

    // ── RETURN_TO_HAND ──────────────────────────────────────────────────────────

    @Test
    void returnToHand_opponentCharacter() {
        Card c = makeCharCard(opponent, 3000);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.RETURN_TO_HAND, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(opponent.getField().getCards().isEmpty());
        assertEquals(1, opponent.getHand().getCards().size());
    }

    // ── POWER_BOOST ─────────────────────────────────────────────────────────────

    @Test
    void powerBoost_thisCard() {
        Card sourceCard = makeCharCard(source, 4000);
        gs.moveCard(sourceCard, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.POWER_BOOST, 2000, t, null, null), sourceCard)
                     .apply(gs, source);

        assertEquals(6000, sourceCard.getTotalPower());
    }

    @Test
    void powerBoost_clearedByRefreshField() {
        Card c = makeCharCard(source, 4000);
        gs.moveCard(c, source.getField());
        c.addPowerBoost(2000);
        assertEquals(6000, c.getTotalPower());

        gs.refreshField(source);

        assertEquals(4000, c.getTotalPower(), "Bonus power should be cleared on refresh");
    }

    // ── POWER_REDUCE ────────────────────────────────────────────────────────────

    @Test
    void powerReduce_opponentCharacter() {
        Card c = makeCharCard(opponent, 5000);
        gs.moveCard(c, opponent.getField());

        Target t = tgt(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                       CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.POWER_REDUCE, 2000, t, null, null), null)
                     .apply(gs, source);

        assertEquals(3000, c.getTotalPower());
    }

    // ── COST_MODIFY ─────────────────────────────────────────────────────────────

    @Test
    void costModify_reducesEffectiveCost() {
        Card c = makeCharCard(source, 3000); // base cost = 3
        gs.moveCard(c, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.COST_MODIFY, -2, t, null, null), c)
                     .apply(gs, source);

        assertEquals(1, c.getEffectiveCost(), "Cost should be 3 + (-2) = 1");
    }

    @Test
    void costModify_floorIsZero() {
        Card c = makeCharCard(source, 3000); // base cost = 3
        gs.moveCard(c, source.getField());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.COST_MODIFY, -5, t, null, null), c)
                     .apply(gs, source);

        assertEquals(0, c.getEffectiveCost(), "Effective cost should not go below 0");
    }

    @Test
    void costModify_clearedByRefreshField() {
        Card c = makeCharCard(source, 3000); // base cost = 3
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

        EffectFactory.build(eff(EffectType.ADD_DON, 1, null, null, null), null)
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

        EffectFactory.build(eff(EffectType.SET_DON_ACTIVE, 1, null, null, null), null)
                     .apply(gs, source);

        assertFalse(don.isRested());
    }

    @Test
    void setDonActive_skipsAttachedDon() {
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        don.rest();
        don.setAttached(true);

        EffectFactory.build(eff(EffectType.SET_DON_ACTIVE, 1, null, null, null), null)
                     .apply(gs, source);

        assertTrue(don.isRested(), "Attached DON!! should not be activated");
    }

    // ── ATTACH_DON ──────────────────────────────────────────────────────────────

    @Test
    void attachDon_toThisCard() {
        Card character = makeCharCard(source, 3000);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());

        Target t = tgt(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 1, t, null, null), character)
                     .apply(gs, source);

        assertEquals(1, character.getAttachedDons().size());
        assertTrue(don.isAttached());
    }

    @Test
    void attachDon_restedDon_isAttachedToFirstFieldCard() {
        // Rested DON!! in cost zone + RESTED_DON filter → gets attached to first field card.
        Card character = makeCharCard(source, 3000);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        don.rest(); // DON is now rested

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.RESTED_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(don.isAttached(), "Rested DON!! should be attached");
        assertEquals(1, character.getAttachedDons().size());
    }

    @Test
    void attachDon_activeDon_notAttached_whenFilterIsRested() {
        // Active DON!! (not rested) in cost zone + RESTED_DON filter → skipped.
        Card character = makeCharCard(source, 3000);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        // don is NOT rested (default state)

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.RESTED_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null), null)
                     .apply(gs, source);

        assertFalse(don.isAttached(), "Active DON!! should not be attached by a RESTED_DON filter");
        assertTrue(character.getAttachedDons().isEmpty());
    }

    @Test
    void attachDon_activeDon_isAttached_whenFilterIsActive() {
        // Active DON!! in cost zone + ACTIVE_DON filter → gets attached.
        Card character = makeCharCard(source, 3000);
        gs.moveCard(character, source.getField());
        DonCard don = newDon(source);
        gs.moveCard(don, source.getCost());
        // don is NOT rested (active) — matches ACTIVE_DON filter

        Target t = tgt(Target.TargetPlayer.SELF, null, CardType.ACTIVE_DON, 1, 0, 0, 0, 0);
        EffectFactory.build(eff(EffectType.ATTACH_DON, 0, t, null, null), null)
                     .apply(gs, source);

        assertTrue(don.isAttached(), "Active DON!! should be attached by ACTIVE_DON filter");
        assertEquals(1, character.getAttachedDons().size());
    }

    // ── ADD_LIFE ────────────────────────────────────────────────────────────────

    @Test
    void addLife_topCardMovesToLifeZone() {
        Card c = TestUtils.makeCard(source, 1000);
        gs.moveCard(c, source.getDeck());

        EffectFactory.build(eff(EffectType.ADD_LIFE, 1, null, null, null), null)
                     .apply(gs, source);

        assertEquals(1, source.getLife().getCards().size());
        assertTrue(source.getDeck().getCards().isEmpty());
    }

    // ── Conditions ──────────────────────────────────────────────────────────────

    @Test
    void condition_ifRested_fires_whenSourceCardIsRested() {
        Card sourceCard = makeCharCard(source, 3000);
        gs.moveCard(sourceCard, source.getField());
        sourceCard.rest();
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, Condition.IF_RESTED, null), sourceCard)
                     .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    @Test
    void condition_ifRested_skipped_whenSourceCardIsActive() {
        Card sourceCard = makeCharCard(source, 3000);
        gs.moveCard(sourceCard, source.getField());
        assertFalse(sourceCard.isRested());
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, Condition.IF_RESTED, null), sourceCard)
                     .apply(gs, source);

        assertTrue(source.getHand().getCards().isEmpty());
    }

    @Test
    void condition_null_alwaysFires() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());

        EffectFactory.build(eff(EffectType.DRAW, 1, null, null, null), null)
                     .apply(gs, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    // ── Null / stub guards ───────────────────────────────────────────────────────

    @Test
    void nullEffectType_doesNotThrow() {
        EffectData e = new EffectData(null, 0, null, null, null);
        assertDoesNotThrow(() -> EffectFactory.build(e, null).apply(gs, source));
    }

    @Test
    void stubEffect_costModify_isNoOp() {
        assertDoesNotThrow(() ->
            EffectFactory.build(eff(EffectType.COST_MODIFY, -2, null, null, null), null)
                         .apply(gs, source));
    }

    @Test
    void stubEffect_grantKeyword_isNoOp() {
        assertDoesNotThrow(() ->
            EffectFactory.build(eff(EffectType.GRANT_KEYWORD, 0, null, null, null), null)
                         .apply(gs, source));
    }
}
