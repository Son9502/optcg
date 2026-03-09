package engine.battle;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.keywords.Keyword;
import engine.cards.types.CardType;
import engine.cards.types.Color;
import engine.cards.Leader;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Cost;
import engine.cards.abilities.Trigger;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectFactory;
import engine.cards.effects.EffectType;
import engine.cards.effects.Target;
import engine.cards.effects.Target.TargetPlayer;
import engine.core.GameState;
import engine.player.Player;
import engine.zones.ZoneType;
import engine.core.TurnManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class BattleHandlerTest {

    private Player p1;
    private Player p2;
    private GameState gameState;
    private BattleSystem battleSystem;
    private TurnManager turnManager;

    @BeforeEach
    void setUp() {
        p1 = new Player("p1");
        p2 = new Player("p2");

        CardData leaderData = new CardData("L-001", "SET", "Leader", "", "", "Set",
                null, null, null, null, 0, 5000, 4, null, 0.0, null);
        p1.setLeader(new Leader("l1", leaderData, p1));
        p2.setLeader(new Leader("l2", leaderData, p2));

        gameState = new GameState(p1, p2);
        gameState.setAbilityManager(new engine.core.AbilityManager(gameState));
        turnManager = new TurnManager(gameState);
        battleSystem = new BattleSystem(gameState, turnManager);
    }

    // --- canAttack ---

    @Test
    void canAttack_activeCard_returnsTrue() {
        Card card = TestUtils.makeCard(p1, 3000);
        assertFalse(card.isRested());
        assertTrue(battleSystem.canAttack(card));
    }

    @Test
    void canAttack_restedCard_returnsFalse() {
        Card card = TestUtils.makeCard(p1, 3000);
        card.rest();
        assertFalse(battleSystem.canAttack(card));
    }

    @Test
    void canAttack_summonSickCard_returnsFalse() {
        Card card = TestUtils.makeCard(p1, 3000);
        card.setSummonSick(true);
        assertFalse(battleSystem.canAttack(card));
    }

    @Test
    void canAttack_afterSummonSickCleared_returnsTrue() {
        Card card = TestUtils.makeCard(p1, 3000);
        card.setSummonSick(true);
        card.setSummonSick(false); // simulates REFRESH clearing the flag
        assertTrue(battleSystem.canAttack(card));
    }

    // --- getValidAttackers ---

    @Test
    void getValidAttackers_includesActiveLeader() {
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertTrue(attackers.contains(p1.getLeader()));
    }

    @Test
    void getValidAttackers_excludesRestedLeader() {
        p1.getLeader().rest();
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertFalse(attackers.contains(p1.getLeader()));
    }

    @Test
    void getValidAttackers_includesActiveFieldCard() {
        Card card = TestUtils.makeCard(p1, 3000);
        p1.getField().add(card);
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertTrue(attackers.contains(card));
    }

    @Test
    void getValidAttackers_excludesSummonSickFieldCard() {
        Card card = TestUtils.makeCard(p1, 3000);
        p1.getField().add(card);
        card.setSummonSick(true);
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertFalse(attackers.contains(card));
    }

    @Test
    void getValidAttackers_excludesRestedFieldCard() {
        Card card = TestUtils.makeCard(p1, 3000);
        p1.getField().add(card);
        card.rest();
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertFalse(attackers.contains(card));
    }

    @Test
    void getValidAttackers_emptyField_onlyLeader() {
        List<Card> attackers = battleSystem.getValidAttackers(p1);
        assertEquals(1, attackers.size());
        assertEquals(p1.getLeader(), attackers.get(0));
    }

    // --- getValidTargets ---

    @Test
    void getValidTargets_alwaysIncludesLeader() {
        List<Card> targets = battleSystem.getValidTargets(p2);
        assertTrue(targets.contains(p2.getLeader()));
    }

    @Test
    void getValidTargets_includesRestedCharacter() {
        Card card = TestUtils.makeCard(p2, 3000);
        p2.getField().add(card);
        card.rest();
        List<Card> targets = battleSystem.getValidTargets(p2);
        assertTrue(targets.contains(card));
    }

    @Test
    void getValidTargets_excludesActiveCharacter() {
        Card card = TestUtils.makeCard(p2, 3000);
        p2.getField().add(card);
        // card is active (not rested)
        List<Card> targets = battleSystem.getValidTargets(p2);
        assertFalse(targets.contains(card));
    }

    // --- resolve ---

    @Test
    void resolve_alwaysRestsAttacker() {
        Card attacker = TestUtils.makeCard(p1, 5000);
        Card target = TestUtils.makeCard(p2, 6000);
        p2.getField().add(target);
        target.rest();
        battleSystem.resolve(attacker, target, 0);
        assertTrue(attacker.isRested());
    }

    @Test
    void resolve_attackerWins_characterMovedToTrash() {
        Card attacker = TestUtils.makeCard(p1, 6000);
        Card target = TestUtils.makeCard(p2, 5000);
        p2.getField().add(target);
        target.rest();
        battleSystem.resolve(attacker, target, 0);
        assertTrue(p2.getTrash().contains(target));
        assertFalse(p2.getField().contains(target));
    }

    @Test
    void resolve_attackerLoses_targetStaysOnField() {
        Card attacker = TestUtils.makeCard(p1, 4000);
        Card target = TestUtils.makeCard(p2, 5000);
        p2.getField().add(target);
        target.rest();
        battleSystem.resolve(attacker, target, 0);
        assertTrue(p2.getField().contains(target));
        assertFalse(p2.getTrash().contains(target));
    }

    @Test
    void resolve_tiedPower_attackerWins() {
        // Rule: attacker wins if power >= defender power, so a tie goes to the attacker
        Card attacker = TestUtils.makeCard(p1, 5000);
        Card target = TestUtils.makeCard(p2, 5000);
        p2.getField().add(target);
        target.rest();
        battleSystem.resolve(attacker, target, 0);
        assertTrue(p2.getTrash().contains(target));
        assertFalse(p2.getField().contains(target));
    }

    @Test
    void resolve_counterBoostSavesDefender() {
        // Counter must push the defender's power strictly above the attacker's to save
        // them
        Card attacker = TestUtils.makeCard(p1, 6000);
        Card target = TestUtils.makeCard(p2, 5000);
        Card counterCard = TestUtils.makeCard(p2, 2000);
        p2.getField().add(target);
        target.rest();
        // 2000 counter: 5000 + 2000 = 7000 > 6000 — attack fails, defender saved
        int counterBoost = battleSystem.applyCounter(p2, counterCard, target);
        battleSystem.resolve(attacker, target, counterBoost);
        assertTrue(p2.getField().contains(target));
        assertFalse(p2.getTrash().contains(target));
    }

    @Test
    void resolve_attackerWins_leaderLosesLife() {
        // Give p2 a life card so removeLife moves it to hand rather than ending the
        // game
        Card lifeCard = TestUtils.makeCard(p2, 0);
        p2.getLife().add(lifeCard);
        int lifeBefore = p2.getLife().size();

        Card attacker = TestUtils.makeCard(p1, 6000); // beats leader's 5000
        battleSystem.resolve(attacker, p2.getLeader(), 0);

        assertEquals(lifeBefore - 1, p2.getLife().size());
        assertFalse(gameState.isGameOver());
    }

    @Test
    void resolve_attackerWins_leaderNoLife_gameOver() {
        // p2 has no life cards — defeating the leader ends the game
        assertTrue(p2.getLife().isEmpty());

        Card attacker = TestUtils.makeCard(p1, 6000);
        battleSystem.resolve(attacker, p2.getLeader(), 0);

        assertTrue(gameState.isGameOver());
        assertEquals(p1, gameState.getWinner());
    }

    // --- KeyWords Tests ---
    @Test
    /**
     * Test that a card with the RUSH keyword is not summon sick and can attack
     * the turn it is played.
     */
    void testRushKeyword() {
        // A card with RUSH should not be summon sick and should be able
        // to attack the turn it is played
        Card rushCard = TestUtils.makeCardWithKeyword(p1, 0, Keyword.RUSH);
        p1.getHand().add(rushCard);
        gameState.playCard(p1, rushCard);
        assertFalse(rushCard.isSummonSick());
    }

    /**
     * Test that a card with the BLOCKER keyword can be chosen as a defender
     * when the opponent attacks.
     */
    @Test
    void testBlockerKeyword() {
        // Since the battle system requires input, we will simulate the defender's
        // choice by
        //
        Card blockerCard = TestUtils.makeCardWithKeyword(p1, 4000, Keyword.BLOCKER);
        p1.getField().add(blockerCard);
        int lifeBefore = p1.getLife().size();
        Card lower_attacker = TestUtils.makeCard(p2, 3000);
        TestUtils.attack(battleSystem, lower_attacker, p1.getLeader(), 0);

        // Blocker should take the hit instead of the leader, so life should be
        // unchanged

        assertEquals(lifeBefore, p1.getLife().size());
        Card higher_attacker = TestUtils.makeCard(p2, 5000);
        TestUtils.attack(battleSystem, higher_attacker, p1.getLeader(), 0);

        // Blocker should be defeated and sent to trash, but leader should still be
        // alive
        assertTrue(p1.getTrash().contains(blockerCard));
        assertFalse(p1.getTrash().contains(p1.getLeader()));
        assertEquals(lifeBefore, p1.getLife().size());
    }

    /**
     * Test that a card with the DOUBLE_ATTACK keyword takes two life from the
     * opponent when it successfully attacks.
     */
    @Test
    void testDoubleAttackKeyword() {
        Card doubleAttackCard = TestUtils.makeCardWithKeyword(p1, 7000, Keyword.DOUBLE_ATTACK);
        for (int i = 0; i < 5; i++) {
            Card lifeCard = TestUtils.makeCard(p2, i * 1000);
            p2.getLife().add(lifeCard);
        }
        int lifeBefore = p2.getLife().size();
        battleSystem.resolve(doubleAttackCard, p2.getLeader(), 0);
        // A successful attack with DOUBLE_ATTACK should cause the opponent to lose 2
        // life
        assertEquals(lifeBefore - 2, p2.getLife().size());
    }

    /**
     * Test that a card with the UNBLOCKABLE keyword cannot be blocked by other
     * cards.
     */
    @Test
    void testUnblockableKeyword() {
        // An UNBLOCKABLE card should be able to attack the opponent's leader
        // even if they have a blocker on the field
        Card unblockableCard = TestUtils.makeCardWithKeyword(p1, 10000, Keyword.UNBLOCKABLE);
        Card blockerCard = TestUtils.makeCardWithKeyword(p2, 3000, Keyword.BLOCKER);
        for (int i = 0; i < 5; i++) {
            Card lifeCard = TestUtils.makeCard(p2, i * 1000);
            p2.getLife().add(lifeCard);
        }
        p2.getField().add(blockerCard);
        int lifeBefore = p2.getLife().size();
        TestUtils.attack(battleSystem, unblockableCard, p2.getLeader(), 0);
        assertFalse(lifeBefore == p2.getLife().size());

    }

    @Test
    /**
     * Test that a card with the BANISH keyword is removed from the game instead of
     * going to the trash when it is defeated in battle.
     */
    void testBanishKeyword() {
        Card banishCard = TestUtils.makeCardWithKeyword(p1, 10000, Keyword.BANISH);
        for (int i = 0; i < 5; i++) {
            Card lifeCard = TestUtils.makeCard(p2, i * 1000);
            p2.getLife().add(lifeCard);
        }
        int handSizeBefore = p2.getHand().size();
        int trashSizeBefore = p2.getTrash().size();
        battleSystem.resolve(banishCard, p2.getLeader(), 0);
        // BANISH: life card goes to trash instead of hand
        assertEquals(handSizeBefore, p2.getHand().size()); // hand unchanged
        assertEquals(trashSizeBefore + 1, p2.getTrash().size()); // life → trash
    }

    // -- TriggerTests

    @Test
    void triggerAbilityWhenLifeLost() {
        // Arrange: p2 has a Vivi (TRIGGER → DRAW 1) as their only life card
        // Act: p1 attacks p2 leader; attack succeeds
        // Assert: p2.getHand().size() == initialHandSize + 1
        // (the draw from Trigger)
        // Trigger card should be moved to trash after being drawn to hand and
        // triggering, so assert that as well
        // Assert: p2.getLife().size() == 0
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null);
        AbilityData triggerData = new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(draw));
        Card triggerCard = TestUtils.makeCardWithAbility(p2, 3000, triggerData);
        p2.getLife().add(triggerCard);
        Card attacker = TestUtils.makeCard(p1, 6000);
        Card deckCard = TestUtils.makeCard(p2, 2000);
        p2.getDeck().add(deckCard);
        List<Card> triggers = battleSystem.resolve(attacker, p2.getLeader(), 0);
        battleSystem.resolveTrigger(p2, triggers.get(0)); // Simulate the trigger activating by moving the card to the
                                                          // hand and then resolving the trigger
        assertEquals(1, p2.getHand().size()); // 1 life card + 1 draw from trigger
        assertEquals(0, p2.getDeck().size());
        assertEquals(0, p2.getLife().size());
        assertEquals(1, p2.getTrash().size()); // the trigger card should be trashed after being drawn to hand and
                                               // triggering

    }

    @Test
    void noTriggerAbilityWhenCardPlayed() {
        // Arrange: p2 has a plain Character as their only life card
        // Act: p1 attacks p2 leader; attack succeeds
        // Assert: p2.getHand().size() == initialHandSize + 1 (only the life card)
        Card nonTriggerCard = TestUtils.makeCard(p2, 3000);
        p2.getLife().add(nonTriggerCard);
        Card attacker = TestUtils.makeCard(p1, 6000);
        battleSystem.resolve(attacker, p2.getLeader(), 0);
        assertEquals(1, p2.getHand().size()); // only the life card,
        assertEquals(0, p2.getLife().size());
    }

    @Test
    void noTriggerWhenBanished() {
        Card banishCard = TestUtils.makeCardWithKeyword(p1, 8000, Keyword.BANISH);
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null);
        AbilityData triggerData = new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(draw));
        Card triggerCard = TestUtils.makeCardWithAbility(p2, 3000, triggerData);
        p2.getLife().add(triggerCard);
        Card deckCard = TestUtils.makeCard(p2, 2000);
        p2.getDeck().add(deckCard);
        battleSystem.resolve(banishCard, p2.getLeader(), 0);
        assertEquals(0, p2.getHand().size()); // only the life card,
        assertEquals(0, p2.getLife().size());
        assertEquals(1, p2.getDeck().size()); // the draw from trigger did not occur
        assertEquals(1, p2.getTrash().size()); // the banished card should be in the trash
    }

    @Test
    void noTriggerifNotActivated() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null);
        AbilityData triggerData = new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(draw));
        Card triggerCard = TestUtils.makeCardWithAbility(p2, 3000, triggerData);
        p2.getLife().add(triggerCard);
        Card attacker = TestUtils.makeCard(p1, 6000);
        Card deckCard = TestUtils.makeCard(p2, 2000);
        p2.getDeck().add(deckCard);
        battleSystem.resolve(attacker, p2.getLeader(), 0);
        assertEquals(1, p2.getHand().size()); // 1 life card + 1 draw from trigger
        assertEquals(1, p2.getDeck().size());
        assertEquals(0, p2.getLife().size());
        assertEquals(0, p2.getTrash().size());

    }

    // -- Prevent Keywords
    // ────────────────────────────────────────────────────────────────
    @Test
    void preventKo_opponentCharacter() {
        Card defender = TestUtils.makeCard(p2, 3000);
        p2.getField().add(defender);
        Target t = new Target(TargetPlayer.THIS_CARD, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = new EffectData(EffectType.PREVENT_KO, 0, t, null, null, null, null);
        EffectFactory.build(e, defender).apply(gameState, p1);
        Card attacker = TestUtils.makeCard(p1, 4000);
        battleSystem.resolve(attacker, defender, 0);
        assertFalse(defender.getZone().getType() == ZoneType.TRASH,
                "Defender should not be sent to trash when KO is prevented");
    }

    @Test
    void preventAttack_opponentCharacter() {
        Card attacker = TestUtils.makeCard(p2, 4000);
        p2.getField().add(attacker);
        Target t = new Target(TargetPlayer.THIS_CARD, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = new EffectData(EffectType.PREVENT_ATTACK, 0, t, null, null, null, null);
        EffectFactory.build(e, attacker).apply(gameState, p1);
        Card defender = TestUtils.makeCard(p1, 3000);
        p1.getField().add(defender);
        battleSystem.resolve(attacker, defender, 0);
        assertFalse(defender.getZone().getType() == ZoneType.TRASH,
                "Defender should not be sent to trash when attack is prevented");
    }

    @Test
    void preventBlock_opponentCharacter() {
        Card blocker = TestUtils.makeCard(p1, 8000);
        Card attacker = TestUtils.makeCard(p2, 6000); // must exceed leader power (5000) after block redirect
        Card lifCard = TestUtils.makeCard(p1, 0);
        p1.getLife().add(lifCard);
        p1.getField().add(blocker);
        Target t = new Target(TargetPlayer.THIS_CARD, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData e = new EffectData(EffectType.PREVENT_BLOCK, 0, t, null, null, null, null);
        EffectFactory.build(e, attacker).apply(gameState, p1);
        battleSystem.resolve(attacker, blocker, 0);
        assertFalse(blocker.getZone().getType() == ZoneType.TRASH,
                "Blocker should not be sent to trash when block is prevented");
        assertTrue(p1.getHand().size() == 1, "1 life should be lost when block is prevented");
        assertTrue(p1.getLife().size() == 0, "");
        assertTrue(p1.getTrash().size() == 0, "Blocker should not be sent to trash when block is prevented");
    }

    // -- Rush:Character Tests
    @Test
    void rushCharacter_notSummonSick() {
        // A card with RUSH_CHARACTER should be able to give other characters the
        // ability to attack the turn they are played
        Card rushCharacterCard = TestUtils.makeCardWithKeyword(p1, 0,
                Keyword.RUSH_CHARACTER);
        p1.getHand().add(rushCharacterCard);
        gameState.playCard(p1, rushCharacterCard);
        assertFalse(rushCharacterCard.isSummonSick());
    }

    @Test
    void rushCharacter_canAttackCharacter() {
        // A card with RUSH_CHARACTER should be able to attack other characters the
        // turn they are played, but not the leader
        Card rushCharacterCard = TestUtils.makeCardWithKeyword(p1, 4000,
                Keyword.RUSH_CHARACTER);
        p1.getHand().add(rushCharacterCard);
        gameState.playCard(p1, rushCharacterCard);
        Card defender = TestUtils.makeCard(p2, 3000);
        p2.getField().add(defender);
        defender.rest(); // defender must be rested to be a valid target
        List<Card> targets = battleSystem.getValidTargets(p2, rushCharacterCard);
        assertTrue(targets.contains(defender), "Defender should be a valid target for RUSH_CHARACTER");
    }

    @Test
    void rushCharacter_cantAttackLeader() {
        // A card with RUSH_CHARACTER should not be able to attack the leader the turn
        // they are played
        Card rushCharacterCard = TestUtils.makeCardWithKeyword(p1, 4000,
                Keyword.RUSH_CHARACTER);
        p1.getHand().add(rushCharacterCard);
        gameState.playCard(p1, rushCharacterCard);
        List<Card> targets = battleSystem.getValidTargets(p2, rushCharacterCard);
        assertFalse(targets.contains(p2.getLeader()), "Leader should not be a valid target for RUSH_CHARACTER");
    }

}
