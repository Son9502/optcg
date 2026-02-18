package engine.setup;

import java.util.List;
import engine.cards.Card;
import engine.player.Player;

public class DeckBuilder {
    /**
     * Builds the main deck for a player by adding a list of cards to the player's deck and shuffling it.
     * @param player The player for whom the main deck is being built.
     * @param cardList A list of cards to be added to the player's main deck.
     */
    public static void buildDeck(Player player, List<Card> cardList) {
        // Placeholder for deck building logic
        for (Card card : cardList) {
            player.getDeck().add(card);
        }
        player.getDeck().shuffle();
    }
    /**
     * Builds the Don deck for a player by adding a list of Don cards to the player's Don deck.
     * @param player The player for whom the Don deck is being built.
     * @param donCardList A list of Don cards to be added to the player's Don deck.
     */
    public static void buildDonDeck(Player player, List<Card> donCardList) {
        // Placeholder for Don deck building logic
        for (Card donCard : donCardList) {
            player.getDonDeck().add(donCard);
        }
    }
}
