package engine.core;
import engine.TestUtils;
import engine.cards.Card;
import engine.cards.CardData;
import engine.cards.DonCard;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Trigger;
import engine.cards.effects.Duration;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectType;
import engine.cards.effects.Target;
import engine.player.Player;
import engine.zones.ZoneType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class TurnManagerTest {

    private Player player1;
    private Player player2;
    private GameState gameState;
    private TurnManager turnManager;
    private AbilityManager abilityManager;

    @BeforeEach
    void setUp() {
        player1 = new Player("player_1", null);
        player2 = new Player("player_2", null);
        gameState = new GameState(player1, player2);
        turnManager = new TurnManager(gameState);
        abilityManager = new AbilityManager(gameState);
        gameState.setAbilityManager(abilityManager);
    }

    @Test
    public void testInitialPhase() {
        // Create a mock GameState for testing
        // Test initial phase is DON
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
    }
    @Test 
    public void testAdvancePhase() {
        // Create a mock GameState and TurnManager for testing

        // Test initial phase is DON
        assertEquals(Phase.DON, turnManager.getCurrentPhase());

        // Advance to MAIN phase
        turnManager.advancePhase();
        assertEquals(Phase.MAIN, turnManager.getCurrentPhase());

        // Advance to END phase
        turnManager.advancePhase();
        assertEquals(Phase.END, turnManager.getCurrentPhase());

        // Advance to next turn (should go back to REFRESH)
        turnManager.advancePhase();
        assertEquals(Phase.REFRESH, turnManager.getCurrentPhase());

        // Advance to DRAW phase
        turnManager.advancePhase();
        assertEquals(Phase.DRAW, turnManager.getCurrentPhase());

        // Advance to DON phase
        turnManager.advancePhase();
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
    }
    @Test
    public void testEndTurn() {
        // Create a mock GameState and TurnManager for testing
        // Test initial active player is player1
        assertEquals(player1, turnManager.getActivePlayer());
        // Advance to END phase to trigger endTurn
        turnManager.advancePhase(); // DON
        turnManager.advancePhase(); // MAIN
        turnManager.advancePhase(); // END
        turnManager.advancePhase(); // REFRESH
        assertEquals(player2, turnManager.getActivePlayer());
    }

    @Test
    public void testTurnCount_incrementsAfterEndPhase() {

        assertEquals(1, turnManager.getTurnCount());

        // Need a Don card in p1's Don deck so the DON phase doesn't fail silently
        player1.getDonDeck().add(new DonCard(player1));

        turnManager.advancePhase(); // DON → MAIN
        turnManager.advancePhase(); // MAIN → END
        turnManager.advancePhase(); // END → endTurn (REFRESH, count becomes 2)

        assertEquals(2, turnManager.getTurnCount());
        assertEquals(Phase.REFRESH, turnManager.getCurrentPhase());
        assertEquals(player2, turnManager.getActivePlayer());
    }

    @Test
    public void testFirstTurnDonDraws1() {

        // Load 5 Don cards into p1's Don deck
        for (int i = 0; i < 5; i++) {
            player1.getDonDeck().add(new DonCard(player1));
        }

        // TurnManager starts at DON with firstTurn=true — advancing should draw exactly 1
        assertEquals(Phase.DON, turnManager.getCurrentPhase());
        turnManager.advancePhase(); // DON → MAIN, draws 1 Don for p1

        assertEquals(Phase.MAIN, turnManager.getCurrentPhase());
        assertEquals(1, player1.getCost().size()); // Don cards go to the cost zone, not hand
    }

    @Test
    public void testSubsequentTurnDrawsTwoDon() {

        // p1 needs 1 Don for the first-turn DON phase
        player1.getDonDeck().add(new DonCard(player1));

        // p2 needs a deck card for the DRAW phase in turn 2
        CardData deckData = new CardData("D1", "SET", "DeckCard", "", "", "Set",
                null, null, null, null, 0, 0, 0, null, 0.0, null);
        player2.getDeck().add(new Card("dc1", deckData, player2));

        // p2 needs Don cards for the turn-2 DON phase
        for (int i = 0; i < 5; i++) {
            player2.getDonDeck().add(new DonCard(player2));
        }

        // Complete turn 1: DON → MAIN → END → (endTurn: REFRESH, p2 active, firstTurn=false)
        turnManager.advancePhase(); // DON
        turnManager.advancePhase(); // MAIN → END
        turnManager.advancePhase(); // END → endTurn

        // Turn 2 for p2: REFRESH → DRAW → DON
        turnManager.advancePhase(); // REFRESH → DRAW
        turnManager.advancePhase(); // DRAW → DON (draws 1 card to p2.hand)

        assertEquals(Phase.DON, turnManager.getCurrentPhase());
        assertEquals(player2, turnManager.getActivePlayer());

        int donDeckBefore = player2.getDonDeck().size();
        turnManager.advancePhase(); // DON → MAIN (firstTurn=false → draws 2 Don for p2)

        assertEquals(donDeckBefore - 2, player2.getDonDeck().size());
    }
    // ── helpers ──────────────────────────────────────────────────────────────

    private EffectData drawEffect() {
        return new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
    }

    private AbilityData opponentsTurnAbility(boolean oncePerTurn) {
        return new AbilityData(Trigger.OPPONENTS_TURN, 0, null, oncePerTurn, null, null, List.of(drawEffect()));
    }

    // ── OPPONENTS_TURN ────────────────────────────────────────────────────────

    @Test
    void opponentsTurn_firesAtStartOfOpponentsTurn() {
        // player2's card fires during player1's DON phase (player1 is the active player)
        gameState.moveCard(TestUtils.makeCard(player2, 1000), player2.getDeck());
        Card card = TestUtils.makeCardWithAbility(player2, 2000, opponentsTurnAbility(false));
        gameState.moveCard(card, player2.getField());

        turnManager.advancePhase(); // DON → MAIN: dispatches OPPONENTS_TURN for opponent's field

        assertEquals(1, player2.getHand().getCards().size(),
                "OPPONENTS_TURN should fire for player2's card during player1's turn");
    }

    @Test
    void opponentsTurn_oncePerTurn_blocked() {
        gameState.moveCard(TestUtils.makeCard(player2, 1000), player2.getDeck());
        gameState.moveCard(TestUtils.makeCard(player2, 2000), player2.getDeck());
        Card card = TestUtils.makeCardWithAbility(player2, 3000, opponentsTurnAbility(true));
        gameState.moveCard(card, player2.getField());

        abilityManager.dispatchAll(Trigger.OPPONENTS_TURN, player2, player2);
        abilityManager.dispatchAll(Trigger.OPPONENTS_TURN, player2, player2); // blocked

        assertEquals(1, player2.getHand().getCards().size(),
                "Second OPPONENTS_TURN dispatch should be blocked by once-per-turn");
    }

    @Test
    void opponentsTurn_resetAfterTurn() {
        gameState.moveCard(TestUtils.makeCard(player2, 1000), player2.getDeck());
        gameState.moveCard(TestUtils.makeCard(player2, 2000), player2.getDeck());
        Card card = TestUtils.makeCardWithAbility(player2, 3000, opponentsTurnAbility(true));
        gameState.moveCard(card, player2.getField());

        abilityManager.dispatchAll(Trigger.OPPONENTS_TURN, player2, player2); // fires, marks used
        gameState.refreshField(player2);                                       // resets usedThisTurn
        abilityManager.dispatchAll(Trigger.OPPONENTS_TURN, player2, player2); // should fire again

        assertEquals(2, player2.getHand().getCards().size(),
                "Ability should fire again after refreshField resets usedThisTurn");
    }

    @Test
    void opponentsTurn_effectUsesThisTurnDuration_clearedNextRefresh() {
        // SELF target resolves against actingPlayer=player2, so player2's own field card gets the boost
        Target selfField = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER, null,
                1, 0, 0, 0, 0, null, null, null, null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 1000, selfField,
                null, null, Duration.THIS_TURN, null, null, 0);
        AbilityData abilityData = new AbilityData(Trigger.OPPONENTS_TURN, 0, null, false, null, null, List.of(boost));
        Card card = TestUtils.makeCardWithAbility(player2, 2000, abilityData);
        gameState.moveCard(card, player2.getField());

        abilityManager.dispatchAll(Trigger.OPPONENTS_TURN, player2, player2);

        assertEquals(3000, card.getPowerWithEffects(),
                "Power should be boosted during opponent's turn");

        gameState.refreshField(player2);

        assertEquals(2000, card.getPowerWithEffects(),
                "THIS_TURN power boost should be cleared by refreshField");
    }
}
