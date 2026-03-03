package engine.setup;

import engine.cards.*;
import engine.cards.abilities.Ability;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Trigger;
import engine.cards.effects.Duration;
import engine.cards.effects.EffectData;
import engine.cards.effects.EffectType;
import engine.cards.effects.Target;
import engine.cards.keywords.Keyword;
import engine.cards.types.*;
import engine.core.GameState;
import engine.player.Player;
import engine.zones.ZoneType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a ready-to-play test GameState with two fully populated players.
 * Intended for local play-testing before real card data is wired up.
 *
 * Player 1 — "Monkey D. Luffy"  (Red,   5 life, 5000 power leader)
 * Player 2 — "Roronoa Zoro"     (Green, 5 life, 5000 power leader)
 * Each player gets a 50-card main deck (42 Characters / 5 Events / 3 Stages)
 * and a 10-card Don deck.
 *
 * Named ability cards are included to exercise each Phase-2 tier manually:
 *
 *  Tier 1 (implemented):
 *   "Nami"   — [On Play] Draw 1 card
 *   "Sanji"  — [When Attacking] +1000 power to self this turn
 *   "Coby"   — [On K.O.] Draw 1 card
 *   "Marco"  — [DON!! x1][End of Your Turn] Rest 1 of opponent's Characters
 *
 *  Tier 2 (stub — fires once implemented):
 *   "Vivi"   — [Trigger] Draw 1 card  (life-card trigger)
 *   "Perona" — [On Play] This Character gains [Blocker] until end of turn
 *
 *  Tier 3 (stub — fires once implemented):
 *   "Kuzan"  — [On Play] Choose: Draw 2 cards. Or, return 1 opponent Character
 *              with cost 3 or less to owner's hand.
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
                id, "OP-TEST", name, "Test leader for local play.", "", "Test Set",
                null, CardType.LEADER, Attribute.Strike, color,
                0, power, life, null, 0.0, null);
        return new Leader(id, data, owner);
    }

    // -------------------------------------------------------------------------
    // Main deck  (50 cards — 42 Characters / 5 Events / 3 Stages)
    // -------------------------------------------------------------------------

    private static List<Card> buildMainDeck(Player owner) {
        List<Card> deck = new ArrayList<>();
        String pid = owner.getPlayerId();

        // ── Generic Characters (31 total) ────────────────────────────────────
        addCardsWithKeyword(deck, owner, pid + "-C1", "Pirate Grunt",      1, 1000, CardType.CHARACTER, 9, Keyword.RUSH);
        addCards           (deck, owner, pid + "-C2", "Pirate Lieutenant", 2, 2000, CardType.CHARACTER, 6);
        addCardsWithKeyword(deck, owner, pid + "-C3", "Pirate Captain",    3, 3000, CardType.CHARACTER, 7, Keyword.BLOCKER);
        addCards           (deck, owner, pid + "-C4", "Pirate Commander",  4, 4000, CardType.CHARACTER, 5);
        addCardsWithKeyword(deck, owner, pid + "-C5", "Pirate Admiral",    5, 5000, CardType.CHARACTER, 2, Keyword.DOUBLE_ATTACK);
        addCardsWithKeyword(deck, owner, pid + "-C6", "Yonko Officer",     6, 6000, CardType.CHARACTER, 1, Keyword.BANISH);
        addCardsWithKeyword(deck, owner, pid + "-C7", "Yonko",             7, 7000, CardType.CHARACTER, 1, Keyword.UNBLOCKABLE);

        // ── Tier 1 — implemented triggered abilities (8 total) ────────────────
        // [On Play] Draw 1 card.
        addAbilityCards(deck, owner, pid + "-T1a", "Nami",  2, 1000, CardType.CHARACTER, 2,
                "[On Play] Draw 1 card.",
                onPlayDraw1());
        // [When Attacking] Give this Character +1000 power this turn.
        addAbilityCards(deck, owner, pid + "-T1b", "Sanji", 3, 3000, CardType.CHARACTER, 2,
                "[When Attacking] Give this Character +1000 power this turn.",
                whenAttackingPowerBoost());
        // [On K.O.] Draw 1 card.
        addAbilityCards(deck, owner, pid + "-T1c", "Coby",  2, 2000, CardType.CHARACTER, 2,
                "[On K.O.] Draw 1 card.",
                onKoDraw1());
        // [DON!! x1][End of Your Turn] Rest 1 of your opponent's Characters.
        addAbilityCards(deck, owner, pid + "-T1d", "Marco", 4, 4000, CardType.CHARACTER, 2,
                "[DON!! x1][End of Your Turn] Rest 1 of your opponent's Characters.",
                endTurnRestOpponent());

        // ── Tier 2 — stub effects (2 total, fire when implemented) ───────────
        // [Trigger] Draw 1 card.  (fires when this card is revealed as a life card)
        addAbilityCards(deck, owner, pid + "-T2a", "Vivi",   2, 2000, CardType.CHARACTER, 1,
                "[Trigger] Draw 1 card.",
                triggerDraw1());
        // [On Play] This Character gains [Blocker] until end of turn. (GRANT_KEYWORD stub)
        addAbilityCards(deck, owner, pid + "-T2b", "Perona", 3, 2000, CardType.CHARACTER, 1,
                "[On Play] This Character gains [Blocker] until end of turn.",
                onPlayGrantBlocker());

        // ── Tier 3 — stub effects (1 total, fires when implemented) ──────────
        // [On Play] Choose: Draw 2 cards. OR Return 1 opponent's Character (cost 3 or less)
        //           to owner's hand.  (MODAL stub)
        addAbilityCards(deck, owner, pid + "-T3a", "Kuzan",  5, 5000, CardType.CHARACTER, 1,
                "[On Play] Choose one: Draw 2 cards. Or, return 1 of your opponent's " +
                "Characters with a cost of 3 or less to the owner's hand.",
                onPlayModal());

        // total characters: 31 generic + 8 T1 + 2 T2 + 1 T3 = 42

        // ── Events (5 total: 2+2+1) ────────────────────────────────────────
        addCards(deck, owner, pid + "-E1", "Gomu Gomu no Pistol", 1, 0, CardType.EVENT, 2);
        addCards(deck, owner, pid + "-E2", "Pirate's Charge",     2, 0, CardType.EVENT, 2);
        addCards(deck, owner, pid + "-E3", "Conqueror's Haki",    4, 0, CardType.EVENT, 1);

        // ── Stages (3 total: 2+1) ─────────────────────────────────────────
        addCards(deck, owner, pid + "-S1", "Thousand Sunny", 1, 0, CardType.STAGE, 2);
        addCards(deck, owner, pid + "-S2", "Marineford",     3, 0, CardType.STAGE, 1);

        // total: 42 + 5 + 3 = 50
        return deck;
    }

    // -------------------------------------------------------------------------
    // Deck-builder helpers
    // -------------------------------------------------------------------------

    private static void addCards(List<Card> deck, Player owner, String idPrefix,
                                 String name, int cost, int power, CardType type, int count) {
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, "", "", "Test Set",
                    null, type, null, null,
                    cost, power, null, null, 0.0, null);
            deck.add(new Card(idPrefix + "-" + i, data, owner));
        }
    }

    private static void addCardsWithKeyword(List<Card> deck, Player owner, String idPrefix,
                                            String name, int cost, int power, CardType type,
                                            int count, Keyword keyword) {
        AbilityData abilityData = new AbilityData(
                Trigger.KEYWORD, 0, null, false, null, keyword, List.of());
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, "", "", "Test Set",
                    null, type, null, null,
                    cost, power, null, null, 0.0, List.of(abilityData));
            Card card = new Card(idPrefix + "-" + i, data, owner);
            card.addAbility(new Ability(abilityData));
            deck.add(card);
        }
    }

    private static void addAbilityCards(List<Card> deck, Player owner, String idPrefix,
                                        String name, int cost, int power, CardType type,
                                        int count, String description, AbilityData abilityData) {
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, description, "", "Test Set",
                    null, type, null, null,
                    cost, power, null, null, 0.0, List.of(abilityData));
            Card card = new Card(idPrefix + "-" + i, data, owner);
            card.addAbility(new Ability(abilityData));
            deck.add(card);
        }
    }

    // -------------------------------------------------------------------------
    // Don deck  (10 Don cards)
    // -------------------------------------------------------------------------

    private static List<Card> buildDonCards(Player owner) {
        CardData donData = new CardData(
                "DON", "OP-TEST", "DON!!",
                "Attach to your Leader or a Character card. (+1000 power during your turn)",
                "", "Test Set", null, CardType.DON, null, null,
                0, 0, null, null, 0.0, null);

        List<Card> dons = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            dons.add(new DonCard("don-" + owner.getPlayerId() + "-" + i, donData, owner));
        }
        return dons;
    }

    // -------------------------------------------------------------------------
    // Ability builders — one per Phase-2 tier feature
    // -------------------------------------------------------------------------

    // ── Tier 1 ──────────────────────────────────────────────────────────────

    /** [On Play] Draw 1 card. */
    private static AbilityData onPlayDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(draw));
    }

    /** [When Attacking] Give this Character +1000 power this turn. */
    private static AbilityData whenAttackingPowerBoost() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 1000, self, null, Duration.THIS_TURN);
        return new AbilityData(Trigger.WHEN_ATTACKING, 0, null, true, null, null, List.of(boost));
    }

    /** [On K.O.] Draw 1 card. */
    private static AbilityData onKoDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null);
        return new AbilityData(Trigger.ON_KO, 0, null, false, null, null, List.of(draw));
    }

    /**
     * [DON!! x1][End of Your Turn] Rest 1 of your opponent's Characters.
     * The [DON!! x1] requirement means the card must have at least 1 DON attached.
     */
    private static AbilityData endTurnRestOpponent() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null);
        return new AbilityData(Trigger.END_OF_YOUR_TURN, 1, null, true, null, null, List.of(rest));
    }

    // ── Tier 2 ──────────────────────────────────────────────────────────────

    /**
     * [Trigger] Draw 1 card.
     * Fires when this card is revealed as a life card during the opponent's attack.
     * (Tier 2 item #5 — not yet wired in BattleSystem.)
     */
    private static AbilityData triggerDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(draw));
    }

    /**
     * [On Play] This Character gains [Blocker] until end of turn.
     * Uses GRANT_KEYWORD — a stub in EffectFactory (Tier 2 item #7).
     */
    private static AbilityData onPlayGrantBlocker() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0);
        EffectData grant = new EffectData(EffectType.GRANT_KEYWORD, 0, self, null, Duration.THIS_TURN);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(grant));
    }

    // ── Tier 3 ──────────────────────────────────────────────────────────────

    /**
     * [On Play] Choose one: Draw 2 cards. Or, return 1 of your opponent's
     * Characters with a cost of 3 or less to the owner's hand.
     * Uses MODAL — a stub in EffectFactory (Tier 3 item #12).
     */
    private static AbilityData onPlayModal() {
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }
}
