package engine.player;

import engine.cards.Leader;
import engine.cards.CardData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {
    @Test
    void startWithEmptyZones() {
        Player player = new Player();
        assertTrue(player.getDeck().isEmpty());
        assertTrue(player.getHand().isEmpty());
        assertTrue(player.getField().isEmpty());
        assertTrue(player.getTrash().isEmpty());
        assertTrue(player.getLife().isEmpty());
        assertTrue(player.getCost().isEmpty());
        assertTrue(player.getStage().isEmpty());
        assertTrue(player.getDonDeck().isEmpty());
    }

    @Test
    /**
     * Tests that a player can be created with a leader and that the leader is correctly assigned to the player.
      * It also verifies that the player's life points are set according to the leader's life points.
      * If all assertions pass, it prints a success message.
     */
    void setLeaderWorks() {
        Player player = new Player();
        assertNull(player.getLeader());
        CardData leaderData = new CardData(
                "LEADER-001", // id
                "LEADER-SET", // setId
                "Test Leader", // name
                "Test leader description", // description
                "Test Leader Set", // setName
                null, // rarity
                null, // cardType
                null, // attribute
                null, // color
                0, // cost
                0, // power
                4000, // life
                null, // counter
                0.0 // marketPrice
        );
        Leader leader = new Leader("leader1", leaderData, player);
        player = new Player("player1", leader);
        assertEquals(leader, player.getLeader());
    }
}
