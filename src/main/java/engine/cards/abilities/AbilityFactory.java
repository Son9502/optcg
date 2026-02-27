package engine.cards.abilities;

import engine.cards.CardData;
import engine.player.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts the static ability descriptors on a CardData into live Ability
 * instances ready to be attached to a Card.
 *
 * AbilityFactory is intended to be used as a long-lived instance (held by
 * CardFactory) so that any compiled regex patterns or lookup tables built
 * in later steps are constructed once and reused across all cards.
 */
public class AbilityFactory {

    /**
     * Wraps each AbilityData on the card into a live Ability instance.
     *
     * @param data  The static card descriptor (carries the parsed abilities list).
     * @param owner The player who will own the card.
     * @return Live Ability instances to be attached to the Card.
     */
    public List<Ability> createAbilities(CardData data, Player owner) {
        return data.abilities().stream()
                .map(Ability::new)
                .collect(Collectors.toList());
    }
}
