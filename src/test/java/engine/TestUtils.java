package engine;

import engine.cards.Card;
import engine.cards.CardData;
import engine.player.Player;

/** Shared factory helpers for unit tests across all packages. */
public final class TestUtils {

    private TestUtils() {
    }

    public static CardData makeCardData(int power) {
        return new CardData("TEST-001", "TEST-SET", "TestCard", "", "TestSet",
                null, null, null, null, 0, power, 0, null, 0.0);
    }

    public static Card makeCard(Player owner, int power) {
        return new Card("c-" + power, makeCardData(power), owner);
    }
}
