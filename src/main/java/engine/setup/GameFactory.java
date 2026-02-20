package engine.setup;

import engine.cards.*;
import engine.cards.types.*;
import engine.core.GameState;
import engine.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a ready-to-play test GameState with two fully populated players.
 * Intended for local play-testing before real card data is wired up.
 *
 * Player 1 — "Monkey D. Luffy"  (Red,   5 life, 5000 power leader)
 * Player 2 — "Roronoa Zoro"     (Green, 5 life, 5000 power leader)
 * Each player gets a 50-card Character deck and a 10-card Don deck.
 */
public class GameFactory {

    public static GameState createTestGame() {
        Player p1 = new Player("p1", "Monkey D. Luffy", null);
        Player p2 = new Player("p2", "Roronoa Zoro", null);

        p1.setLeader(makeLeader("L-001", "Monkey D. Luffy", 5000, 5, Color.Red, p1));
        p2.setLeader(makeLeader("L-002", "Roronoa Zoro",    5000, 5, Color.Green, p2));

        DeckBuilder.buildDeck(p1, buildMainDeck(p1));
        DeckBuilder.buildDeck(p2, buildMainDeck(p2));
        DeckBuilder.buildDonDeck(p1, buildDonCards(p1));
        DeckBuilder.buildDonDeck(p2, buildDonCards(p2));

        return new GameState(p1, p2);
    }

    // -------------------------------------------------------------------------
    // Leader
    // -------------------------------------------------------------------------

    private static Leader makeLeader(String id, String name, int power, int life,
                                     Color color, Player owner) {
        CardData data = new CardData(
                id, "OP-TEST", name, "Test leader for local play.", "Test Set",
                null, CardType.Leader, Attribute.Strike, color,
                0, power, life, null, 0.0);
        return new Leader(id, data, owner);
    }

    // -------------------------------------------------------------------------
    // Main deck  (50 cards — 42 Characters + 5 Events + 3 Stages)
    // -------------------------------------------------------------------------

    private static List<Card> buildMainDeck(Player owner) {
        List<Card> deck = new ArrayList<>();
        String pid = owner.getPlayerId();

        // Characters (42 total: 9+9+9+7+5+2+1)
        addCards(deck, owner, pid + "-C1", "Pirate Grunt",      1, 1000, CardType.Character, 9);
        addCards(deck, owner, pid + "-C2", "Pirate Lieutenant", 2, 2000, CardType.Character, 9);
        addCards(deck, owner, pid + "-C3", "Pirate Captain",    3, 3000, CardType.Character, 9);
        addCards(deck, owner, pid + "-C4", "Pirate Commander",  4, 4000, CardType.Character, 7);
        addCards(deck, owner, pid + "-C5", "Pirate Admiral",    5, 5000, CardType.Character, 5);
        addCards(deck, owner, pid + "-C6", "Yonko Officer",     6, 6000, CardType.Character, 2);
        addCards(deck, owner, pid + "-C7", "Yonko",             7, 7000, CardType.Character, 1);

        // Events (5 total: 2+2+1)
        addCards(deck, owner, pid + "-E1", "Gomu Gomu no Pistol", 1, 0, CardType.Event, 2);
        addCards(deck, owner, pid + "-E2", "Pirate's Charge",     2, 0, CardType.Event, 2);
        addCards(deck, owner, pid + "-E3", "Conqueror's Haki",    4, 0, CardType.Event, 1);

        // Stages (3 total: 2+1)
        addCards(deck, owner, pid + "-S1", "Thousand Sunny", 1, 0, CardType.Stage, 2);
        addCards(deck, owner, pid + "-S2", "Marineford",     3, 0, CardType.Stage, 1);

        // total: 42 + 5 + 3 = 50
        return deck;
    }

    private static void addCards(List<Card> deck, Player owner, String idPrefix,
                                 String name, int cost, int power, CardType type, int count) {
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, "", "Test Set",
                    null, type, null, null,
                    cost, power, null, null, 0.0);
            deck.add(new Card(idPrefix + "-" + i, data, owner));
        }
    }

    // -------------------------------------------------------------------------
    // Don deck  (10 Don cards)
    // -------------------------------------------------------------------------

    private static List<Card> buildDonCards(Player owner) {
        CardData donData = new CardData(
                "DON", "OP-TEST", "DON!!",
                "Attach to your Leader or a Character card. (+1000 power during your turn)",
                "Test Set", null, CardType.Don, null, null,
                0, 0, null, null, 0.0);

        List<Card> dons = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            dons.add(new DonCard("don-" + owner.getPlayerId() + "-" + i, donData, owner));
        }
        return dons;
    }
}
