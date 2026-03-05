package engine.core;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.DonCard;
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
        source   = new Player("p1");
        opponent = new Player("p2");
        gs = new GameState(source, opponent);
        am = new AbilityManager(gs);
        gs.setAbilityManager(am);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Ability with a single DRAW effect and configurable once-per-turn flag. */
    private Ability drawAbility(Trigger trigger, int drawCount, boolean oncePerTurn) {
        EffectData draw = new EffectData(EffectType.DRAW, drawCount, null, null, null, null);
        AbilityData data = new AbilityData(trigger, 0, null, oncePerTurn, null, null, List.of(draw));
        return new Ability(data);
    }

    /** Ability gated by a [DON!! xX] requirement. */
    private Ability donGatedDrawAbility(Trigger trigger, int donRequired) {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null);
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
}
