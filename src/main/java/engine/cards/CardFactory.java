package engine.cards;

import engine.cards.abilities.AbilityFactory;
import engine.cards.types.CardType;
import engine.player.Player;

import java.util.List;
import engine.cards.abilities.Ability;

/**
 * Creates Card instances (or appropriate subclasses) from CardData records.
 */
public class CardFactory {

    private static final AbilityFactory abilityFactory = new AbilityFactory();

    private CardFactory() {
    }

    /**
     * Creates a Card or Leader from the given CardData, owned by the given player.
     * Returns a Leader when cardType is LEADER, a plain Card otherwise.
     * Abilities are generated via AbilityFactory and attached before returning.
     */
    public static Card createCard(CardData data, Player owner) {
        Card card;
        if (data.cardType() == CardType.LEADER) {
            card = new Leader(data.id(), data, owner);
        } else {
            card = new Card(data.id(), data, owner);
        }
        card.addAbilities(createAbility(data, owner));
        return card;
    }

    /**
     * Delegates to AbilityFactory to produce the live Ability instances for
     * the given card. Called once per card at creation time.
     */
    private static List<Ability> createAbility(CardData data, Player owner) {
        return abilityFactory.createAbilities(data, owner);
    }
}
