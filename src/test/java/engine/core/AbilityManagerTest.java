package engine.core;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.DonCard;
import engine.cards.abilities.Cost;
import engine.cards.abilities.Ability;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Trigger;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectType;
import engine.player.Player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityManagerTest {

    private Player source;
    private Player opponent;
    private GameState gs;
    private AbilityManager am;

    @BeforeEach
    void setUp() {
        source = new Player("p1");
        opponent = new Player("p2");
        gs = new GameState(source, opponent);
        am = new AbilityManager(gs);
        gs.setAbilityManager(am);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Ability with a single DRAW effect and configurable once-per-turn flag. */
    private Ability drawAbility(Trigger trigger, int drawCount, boolean oncePerTurn) {
        EffectData draw = new EffectData(EffectType.DRAW, drawCount, null, null, null, null, null, null, 0);
        AbilityData data = new AbilityData(trigger, 0, null, oncePerTurn, null, null, List.of(draw));
        return new Ability(data);
    }

    /** Ability gated by a [DON!! xX] requirement. */
    private Ability donGatedDrawAbility(Trigger trigger, int donRequired) {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        AbilityData data = new AbilityData(trigger, donRequired, null, false, null, null, List.of(draw));
        return new Ability(data);
    }

    // ── dispatch ─────────────────────────────────────────────────────────────

    @Test
    void dispatch_matchingTrigger_firesEffect() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        card.addAbility(drawAbility(Trigger.ON_PLAY, 1, false));

        am.dispatch(Trigger.ON_PLAY, card, source);

        assertEquals(1, source.getHand().getCards().size());
    }

    @Test
    void dispatch_wrongTrigger_doesNotFire() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        card.addAbility(drawAbility(Trigger.WHEN_ATTACKING, 1, false));

        am.dispatch(Trigger.ON_PLAY, card, source);

        assertTrue(source.getHand().getCards().isEmpty());
    }

    @Test
    void dispatch_oncePerTurn_onlyFiresOnce() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());
        Card card = TestUtils.makeCard(source, 3000);
        card.addAbility(drawAbility(Trigger.ON_PLAY, 1, true));

        am.dispatch(Trigger.ON_PLAY, card, source);
        am.dispatch(Trigger.ON_PLAY, card, source); // should be blocked

        assertEquals(1, source.getHand().getCards().size(), "Fired twice despite once-per-turn");
    }

    @Test
    void dispatch_oncePerTurn_resetTurn_allowsFireAgain() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());
        Card card = TestUtils.makeCard(source, 3000);
        gs.moveCard(card, source.getField());
        card.addAbility(drawAbility(Trigger.ON_PLAY, 1, true));

        am.dispatch(Trigger.ON_PLAY, card, source);
        gs.refreshField(source); // resets usedThisTurn
        gs.moveCard(TestUtils.makeCard(source, 4000), source.getDeck());
        am.dispatch(Trigger.ON_PLAY, card, source);

        assertEquals(2, source.getHand().getCards().size(), "Should fire again after turn reset");
    }

    @Test
    void dispatch_donRequired_skippedWhenNotMet() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        card.addAbility(donGatedDrawAbility(Trigger.ON_PLAY, 2)); // needs 2 DON attached

        am.dispatch(Trigger.ON_PLAY, card, source); // card has 0 DON attached

        assertTrue(source.getHand().getCards().isEmpty(), "Effect should not fire (DON!! xX not met)");
    }

    @Test
    void dispatch_donRequired_firesWhenMet() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        // Attach 2 DON cards
        DonCard don1 = new DonCard(source);
        DonCard don2 = new DonCard(source);
        gs.moveCard(don1, source.getCost());
        gs.moveCard(don2, source.getCost());
        gs.attachDon(card, don1);
        gs.attachDon(card, don2);
        card.addAbility(donGatedDrawAbility(Trigger.ON_PLAY, 2));

        am.dispatch(Trigger.ON_PLAY, card, source);

        assertEquals(1, source.getHand().getCards().size(), "Effect should fire (DON!! x2 met)");
    }

    @Test
    void dispatch_keywordTrigger_alwaysSkipped() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        card.addAbility(drawAbility(Trigger.KEYWORD, 1, false));

        am.dispatch(Trigger.KEYWORD, card, source);

        assertTrue(source.getHand().getCards().isEmpty(), "KEYWORD trigger must never fire via dispatch");
    }

    @Test
    void dispatch_nullCard_doesNotThrow() {
        assertDoesNotThrow(() -> am.dispatch(Trigger.ON_PLAY, null, source));
    }

    // ── dispatchAll ──────────────────────────────────────────────────────────

    @Test
    void dispatchAll_firesForEveryFieldCard() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());

        Card c1 = TestUtils.makeCard(source, 3000);
        Card c2 = TestUtils.makeCard(source, 4000);
        c1.addAbility(drawAbility(Trigger.END_OF_YOUR_TURN, 1, false));
        c2.addAbility(drawAbility(Trigger.END_OF_YOUR_TURN, 1, false));
        gs.moveCard(c1, source.getField());
        gs.moveCard(c2, source.getField());

        am.dispatchAll(Trigger.END_OF_YOUR_TURN, source, source);

        assertEquals(2, source.getHand().getCards().size());
    }

    // ── Integration: ON_KO fired by GameState.trash() ─────────────────────────

    @Test
    void onKo_firesWhenCardTrashedFromField() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        card.addAbility(drawAbility(Trigger.ON_KO, 1, false));

        gs.trash(source, card); // should trigger ON_KO

        assertEquals(1, source.getHand().getCards().size(), "ON_KO should fire when trashed from field");
    }

    @Test
    void onKo_doesNotFireWhenCardTrashedFromHand() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getHand());
        card.addAbility(drawAbility(Trigger.ON_KO, 1, false));

        gs.trash(source, card); // card was in hand, not field

        assertTrue(source.getHand().getCards().isEmpty(), "ON_KO should not fire when trashed from hand");
    }

    // ── ACTIVATED_MAIN ────────────────────────────────────────────────────────

    /**
     * ACTIVATED_MAIN ability with a REST cost and a DRAW effect (once-per-turn).
     */
    private Ability activatedDrawAbility() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        Cost cost = new Cost();
        cost.type = Cost.CostType.REST_SELF;
        cost.amount = 1;
        AbilityData data = new AbilityData(Trigger.ACTIVATED_MAIN, 0, List.of(cost), true, null, null, List.of(draw));
        return new Ability(data);
    }

    @Test
    void activatedMain_firesAndMarksUsed() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        Card card = TestUtils.makeCard(source, 2000);
        Ability ability = activatedDrawAbility();
        card.addAbility(ability);

        am.dispatch(Trigger.ACTIVATED_MAIN, card, source);

        assertEquals(1, source.getHand().getCards().size());
        assertTrue(ability.isUsedThisTurn(), "Ability should be marked used after activation");
    }

    @Test
    void activatedMain_oncePerTurn_blocksRepeat() {
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getDeck());
        gs.moveCard(TestUtils.makeCard(source, 2000), source.getDeck());
        Card card = TestUtils.makeCard(source, 3000);
        Ability ability = activatedDrawAbility();
        card.addAbility(ability);

        am.dispatch(Trigger.ACTIVATED_MAIN, card, source);
        am.dispatch(Trigger.ACTIVATED_MAIN, card, source); // blocked

        assertEquals(1, source.getHand().getCards().size(), "Second activation should be blocked");
    }

    @Test
    void payActivationCost_restSelf_restsCardAndReturnsTrue() {
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());

        Cost cost = new Cost();
        cost.type = Cost.CostType.REST_SELF;
        cost.amount = 1;

        boolean result = gs.payActivationCost(source, List.of(cost), card);

        assertTrue(result, "Should return true when REST cost can be paid");
        assertTrue(card.isRested(), "Card should be rested after paying REST cost");
    }

    @Test
    void payActivationCost_donMinus_cannotAffordWhenNoDon() {
        Card card = TestUtils.makeCard(source, 2000);

        Cost cost = new Cost();
        cost.type = Cost.CostType.DON_MINUS;
        cost.amount = 1;

        boolean result = gs.payActivationCost(source, List.of(cost), card);

        assertFalse(result, "Should return false when no DON in cost zone");
    }

    // --- Compound Cost Tests ---

    @Test
    void canAffordActivation_compound_trueWhenAllAffordable() {
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        gs.moveCard(TestUtils.makeCard(source, 1000), source.getHand()); // for TRASH

        Cost cost1 = new Cost();
        cost1.type = Cost.CostType.REST_SELF;
        Cost cost2 = new Cost();
        cost2.type = Cost.CostType.TRASH;
        cost2.amount = 1;

        assertTrue(gs.canAffordActivation(source, List.of(cost1, cost2), card),
                "Should be affordable: card not rested + hand has 1 card");
    }

    @Test
    void canAffordActivation_compound_falseWhenOneUnaffordable() {
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        // TRASH requires 1 hand card, but hand is empty

        Cost cost1 = new Cost();
        cost1.type = Cost.CostType.REST_SELF;
        Cost cost2 = new Cost();
        cost2.type = Cost.CostType.TRASH;
        cost2.amount = 1;

        assertFalse(gs.canAffordActivation(source, List.of(cost1, cost2), card),
                "Should be unaffordable: hand is empty, cannot pay TRASH cost");
    }

    @Test
    void canAffordActivation_emptyList_returnsTrue() {
        Card card = TestUtils.makeCard(source, 2000);
        assertTrue(gs.canAffordActivation(source, List.of(), card),
                "Empty cost list should always be affordable");
    }

    @Test
    void payActivationCost_compound_allCostsPaid() {
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        Card handCard = TestUtils.makeCard(source, 1000);
        gs.moveCard(handCard, source.getHand());

        Cost trash = new Cost();
        trash.type = Cost.CostType.TRASH;
        trash.amount = 1;
        Cost trashSelf = new Cost();
        trashSelf.type = Cost.CostType.TRASH_SELF;

        boolean result = gs.payActivationCost(source, List.of(trash, trashSelf), card);

        assertTrue(result, "Both costs should be paid");
        assertTrue(source.getHand().getCards().isEmpty(), "Hand card should be trashed");
        assertTrue(source.getTrash().getCards().contains(card), "Source card should be trashed");
    }

    @Test
    void payActivationCost_compound_atomicFailure() {
        // TRASH (1 from hand) + REST_SELF — hand is empty so canAffordActivation blocks both
        Card card = TestUtils.makeCard(source, 2000);
        gs.moveCard(card, source.getField());
        // hand is empty — TRASH cost cannot be paid

        Cost trash = new Cost();
        trash.type = Cost.CostType.TRASH;
        trash.amount = 1;
        Cost restSelf = new Cost();
        restSelf.type = Cost.CostType.REST_SELF;

        boolean result = gs.payActivationCost(source, List.of(trash, restSelf), card);

        assertFalse(result, "Should return false when any cost cannot be paid");
        assertFalse(card.isRested(), "Card should not be rested (atomic failure — no partial payment)");
    }
}
