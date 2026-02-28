package engine.battle;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.keywords.Keyword;
import engine.cards.Leader;
import engine.core.GameState;
import engine.player.Player;
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
        p2.getField().add(target);
        target.rest();
        // 2000 counter: 5000 + 2000 = 7000 > 6000 — attack fails, defender saved
        battleSystem.resolve(attacker, target, 2000);
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

    // TRIGGER deferred — per-card effect activation requires the effect execution
    // system to be wired first (Phase 3). The test below is a placeholder only.
    // @Test
    // void testTriggerKeyword() { ... }

    /*@Test
    void testRushCharacterKeyword() {
        // A card with RUSH_CHARACTER should be able to give other characters the
        // ability to attack the turn they are played
        Card rushCharacterCard = TestUtils.makeCardWithKeyword(p1, 0, Keyword.RUSH_CHARACTER);
        Card normalCharacterCard = TestUtils.makeCard(p1, 3000);
        p1.getHand().add(rushCharacterCard);
        p1.getHand().add(normalCharacterCard);
        gameState.playCard(p1, rushCharacterCard);
        gameState.playCard(p1, normalCharacterCard);
        assertFalse(normalCharacterCard.isSummonSick());
    }
    */
}
