package engine.battle;

import engine.TestUtils;
import engine.cards.Card;
import engine.cards.CardData;
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

        CardData leaderData = new CardData("L-001", "SET", "Leader", "", "Set",
                null, null, null, null, 0, 5000, 4, null, 0.0);
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
        // Counter must push the defender's power strictly above the attacker's to save them
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
        // Give p2 a life card so removeLife moves it to hand rather than ending the game
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
}
