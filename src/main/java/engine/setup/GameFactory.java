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
 *  Tier 2 (implemented):
 *   "Vivi"   — [Trigger] Draw 1 card  (life-card trigger)
 *   "Perona" — [On Play] This Character gains [Blocker] until end of turn
 *
 *  Tier 3 (implemented — engine picks branch 0 by default; CliController menu pending):
 *   "Kuzan"  — [On Play] Choose: Draw 2 cards. Or, return 1 opponent Character
 *              with cost 3 or less to owner's hand.
 *
 *  Keywords across generic tiers:
 *   RUSH (Pirate Grunt), RUSH_CHARACTER (Pirate Scout), BLOCKER (Pirate Captain),
 *   DOUBLE_ATTACK (Pirate Admiral), BANISH (Yonko Officer), UNBLOCKABLE (Yonko)
 */
public class GameFactory {

    public static GameState createTestGame() {
        Player p1 = new Player("p1", "Monkey D. Luffy", null);
        Player p2 = new Player("p2", "Roronoa Zoro", null);

        p1.setLeader(makeLeader("L-001", "Monkey D. Luffy", 5000, 5, List.of(Color.Red), p1));
        p2.setLeader(makeLeader("L-002", "Roronoa Zoro",    5000, 5, List.of(Color.Green), p2));

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
                                     List<Color> colors, Player owner) {
        CardData data = new CardData(
                id, "OP-TEST", name, "Test leader for local play.", "", "Test Set",
                null, CardType.LEADER, Attribute.Strike, colors,
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
        addCardsWithKeyword(deck, owner, pid + "-C1",  "Pirate Grunt",      1, 1000, CardType.CHARACTER, 9, Keyword.RUSH);
        addCards           (deck, owner, pid + "-C2",  "Pirate Lieutenant", 2, 2000, CardType.CHARACTER, 5);
        addCardsWithKeyword(deck, owner, pid + "-C2r", "Pirate Scout",      2, 2000, CardType.CHARACTER, 1, Keyword.RUSH_CHARACTER);
        addCardsWithKeyword(deck, owner, pid + "-C3",  "Pirate Captain",    3, 3000, CardType.CHARACTER, 7, Keyword.BLOCKER);
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
        // [Main] Give 1 of your Leader or Characters +2000 power this turn.
        addAbilityCards(deck, owner, pid + "-E1", "Gomu Gomu no Pistol", 1, 0, CardType.EVENT, 2,
                "[Main] Give 1 of your Leader or Characters +2000 power this turn.",
                eventPowerBoost());
        // [Main] Draw 2 cards.
        addAbilityCards(deck, owner, pid + "-E2", "Pirate's Charge", 2, 0, CardType.EVENT, 2,
                "[Main] Draw 2 cards.",
                eventDraw2());
        // [Main] K.O. up to 1 of your opponent's Characters with a cost of 5 or less.
        addAbilityCards(deck, owner, pid + "-E3", "Conqueror's Haki", 4, 0, CardType.EVENT, 1,
                "[Main] K.O. up to 1 of your opponent's Characters with a cost of 5 or less.",
                eventKo());

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

    /** Character cards with a counter stat (no ability). */
    private static void addCardsWithCounter(List<Card> deck, Player owner, String idPrefix,
                                            String name, int cost, int power, int counter, int count) {
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, "", "", "Test Set",
                    null, CardType.CHARACTER, null, null,
                    cost, power, null, counter, 0.0, null);
            deck.add(new Card(idPrefix + "-" + i, data, owner));
        }
    }

    /** Cards with multiple abilities (e.g. event counters: [Main] + [Counter]). counter = card's counter stat. */
    private static void addAbilityCardsMulti(List<Card> deck, Player owner, String idPrefix,
                                             String name, int cost, int power, CardType type,
                                             int counter, int count, String description,
                                             List<AbilityData> abilities) {
        for (int i = 1; i <= count; i++) {
            CardData data = new CardData(
                    idPrefix + "-" + i, "OP-TEST", name, description, "", "Test Set",
                    null, type, null, null,
                    cost, power, null, counter, 0.0, abilities);
            Card card = new Card(idPrefix + "-" + i, data, owner);
            for (AbilityData ad : abilities) card.addAbility(new Ability(ad));
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
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(draw));
    }

    /** [When Attacking] Give this Character +1000 power this turn. */
    private static AbilityData whenAttackingPowerBoost() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0, null,null,null,null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 1000, self, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.WHEN_ATTACKING, 0, null, true, null, null, List.of(boost));
    }

    /** [On K.O.] Draw 1 card. */
    private static AbilityData onKoDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_KO, 0, null, false, null, null, List.of(draw));
    }

    /**
     * [DON!! x1][End of Your Turn] Rest 1 of your opponent's Characters.
     * The [DON!! x1] requirement means the card must have at least 1 DON attached.
     */
    private static AbilityData endTurnRestOpponent() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.END_OF_YOUR_TURN, 1, null, true, null, null, List.of(rest));
    }

    // ── Tier 2 ──────────────────────────────────────────────────────────────

    /**
     * [Trigger] Draw 1 card.
     * Fires when this card is revealed as a life card during the opponent's attack.
     * (Tier 2 item #5 — not yet wired in BattleSystem.)
     */
    private static AbilityData triggerDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(draw));
    }

    /**
     * [On Play] This Character gains [Blocker] until end of turn.
     * Uses GRANT_KEYWORD — a stub in EffectFactory (Tier 2 item #7).
     */
    private static AbilityData onPlayGrantBlocker() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0, null,null,null,null);
        EffectData grant = new EffectData(EffectType.GRANT_KEYWORD, 0, self, null, null, Duration.THIS_TURN, Keyword.BLOCKER, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(grant));
    }

    // ── Events ──────────────────────────────────────────────────────────────

    /** [Main] Give 1 of your Leader or Characters +2000 power this turn. */
    private static AbilityData eventPowerBoost() {
        Target self = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 2000, self, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(boost));
    }

    /** [Main] Draw 2 cards. */
    private static AbilityData eventDraw2() {
        EffectData draw = new EffectData(EffectType.DRAW, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(draw));
    }

    /** [Main] K.O. up to 1 of your opponent's Characters with a cost of 5 or less. */
    private static AbilityData eventKo() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 5, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(ko));
    }

    // ── Tier 3 ──────────────────────────────────────────────────────────────

    /**
     * [On Play] Choose one: Draw 2 cards. Or, return 1 of your opponent's
     * Characters with a cost of 3 or less to the owner's hand.
     * Branch 0 (default): Draw 2 cards.
     * Branch 1: Return 1 opponent Character (cost ≤3) to hand.
     * CliController interactive menu is pending; engine defaults to branch 0.
     */
    private static AbilityData onPlayModal() {
        // Branch 0: Draw 2 cards
        EffectData draw2 = new EffectData(EffectType.DRAW, 2, null, null, null, null, null, null, 0);
        // Branch 1: Return 1 opponent Character with cost 3 or less to owner's hand
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER,
                CardType.CHARACTER, 1, 3, 0, 0, 0, null,null,null,null);
        EffectData bounce = new EffectData(EffectType.RETURN_TO_HAND, 0, opp, null, null, null, null, null, 0);
        List<List<EffectData>> choices = List.of(List.of(draw2), List.of(bounce));
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, choices, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    // =========================================================================
    // Item 25 — 12 selectable dummy decks
    // =========================================================================

    public static final String[] DECK_NAMES = {
        " 1. Red          — Aggression          (Monkey D. Luffy)",
        " 2. Blue         — Control / Hand      (Nefertari Vivi)",
        " 3. Green        — Field Control       (Roronoa Zoro)",
        " 4. Purple       — DON!! Engine        (Charlotte Katakuri)",
        " 5. Black        — Removal / Graveyard (Trafalgar Law)",
        " 6. Yellow       — Life / Triggers     (Charlotte Linlin)",
        " 7. Red / Green  — Power + Lock        (Marco the Phoenix)",
        " 8. Blue / Black — Control + Removal   (Donquixote Doflamingo)",
        " 9. Purple/Green — DON!! Overdrive     (Kaidou)",
        "10. Yellow / Red — Life + Aggression   (Edward Newgate)",
        "11. Blue /Purple — Draw + DON!!        (Rob Lucci)",
        "12. Black/Yellow — Removal + Resilience (Bartholomew Kuma)",
    };

    public static GameState createGame(int p1Deck, int p2Deck) {
        p1Deck = Math.max(0, Math.min(11, p1Deck));
        p2Deck = Math.max(0, Math.min(11, p2Deck));
        Player p1 = new Player("p1", leaderNameFor(p1Deck), null);
        Player p2 = new Player("p2", leaderNameFor(p2Deck), null);
        equip(p1, p1Deck);
        equip(p2, p2Deck);
        return new GameState(p1, p2);
    }

    private static String leaderNameFor(int idx) {
        return switch (idx) {
            case 0  -> "Monkey D. Luffy";
            case 1  -> "Nefertari Vivi";
            case 2  -> "Roronoa Zoro";
            case 3  -> "Charlotte Katakuri";
            case 4  -> "Trafalgar Law";
            case 5  -> "Charlotte Linlin";
            case 6  -> "Marco the Phoenix";
            case 7  -> "Donquixote Doflamingo";
            case 8  -> "Kaidou";
            case 9  -> "Edward Newgate";
            case 10 -> "Rob Lucci";
            default -> "Bartholomew Kuma";
        };
    }

    private static void equip(Player player, int idx) {
        String pid = player.getPlayerId();
        switch (idx) {
            case 0  -> { player.setLeader(makeLeader("L-R",  "Monkey D. Luffy",       5000, 5, List.of(Color.Red),                 player)); DeckBuilder.buildDeck(player, buildRedDeck(player, pid));          }
            case 1  -> { player.setLeader(makeLeader("L-B",  "Nefertari Vivi",        5000, 5, List.of(Color.Blue),                player)); DeckBuilder.buildDeck(player, buildBlueDeck(player, pid));         }
            case 2  -> { player.setLeader(makeLeader("L-G",  "Roronoa Zoro",          5000, 5, List.of(Color.Green),               player)); DeckBuilder.buildDeck(player, buildGreenDeck(player, pid));        }
            case 3  -> { player.setLeader(makeLeader("L-P",  "Charlotte Katakuri",    5000, 5, List.of(Color.Purple),              player)); DeckBuilder.buildDeck(player, buildPurpleDeck(player, pid));       }
            case 4  -> { player.setLeader(makeLeader("L-Bl", "Trafalgar Law",         5000, 5, List.of(Color.Black),               player)); DeckBuilder.buildDeck(player, buildBlackDeck(player, pid));        }
            case 5  -> { player.setLeader(makeLeader("L-Y",  "Charlotte Linlin",      5000, 5, List.of(Color.Yellow),              player)); DeckBuilder.buildDeck(player, buildYellowDeck(player, pid));       }
            case 6  -> { player.setLeader(makeLeader("L-RG", "Marco the Phoenix",     5000, 5, List.of(Color.Red,    Color.Green),  player)); DeckBuilder.buildDeck(player, buildRedGreenDeck(player, pid));    }
            case 7  -> { player.setLeader(makeLeader("L-BB", "Donquixote Doflamingo", 5000, 5, List.of(Color.Blue,   Color.Black),  player)); DeckBuilder.buildDeck(player, buildBlueBlackDeck(player, pid));   }
            case 8  -> { player.setLeader(makeLeader("L-PG", "Kaidou",               5000, 5, List.of(Color.Purple, Color.Green),  player)); DeckBuilder.buildDeck(player, buildPurpleGreenDeck(player, pid)); }
            case 9  -> { player.setLeader(makeLeader("L-YR", "Edward Newgate",        5000, 5, List.of(Color.Yellow, Color.Red),   player)); DeckBuilder.buildDeck(player, buildYellowRedDeck(player, pid));   }
            case 10 -> { player.setLeader(makeLeader("L-BP", "Rob Lucci",             5000, 5, List.of(Color.Blue,   Color.Purple), player)); DeckBuilder.buildDeck(player, buildBluePurpleDeck(player, pid));  }
            default -> { player.setLeader(makeLeader("L-BY", "Bartholomew Kuma",      5000, 5, List.of(Color.Black,  Color.Yellow), player)); DeckBuilder.buildDeck(player, buildBlackYellowDeck(player, pid)); }
        }
        DeckBuilder.buildDonDeck(player, buildDonCards(player));
    }

    // ── Shared base sections ─────────────────────────────────────────────────────

    /**
     * 31 generic characters — same power curve and keywords across all 12 selectable decks.
     * Includes 4 counter characters (2× +2000, 2× +1000) replacing 4 plain filler slots.
     */
    private static List<Card> genericBase(Player owner, String pid) {
        List<Card> deck = new ArrayList<>();
        addCardsWithKeyword(deck, owner, pid+"-C1",  "Pirate Grunt",      1, 1000, CardType.CHARACTER, 7, Keyword.RUSH);
        addCardsWithCounter(deck, owner, pid+"-C1c", "Counter Recruit",   1, 1000, 2000, 2);  // +2000 counter
        addCards           (deck, owner, pid+"-C2",  "Pirate Lieutenant", 2, 2000, CardType.CHARACTER, 3);
        addCardsWithCounter(deck, owner, pid+"-C2c", "Counter Mate",      2, 2000, 1000, 2);  // +1000 counter
        addCardsWithKeyword(deck, owner, pid+"-C2r", "Pirate Scout",      2, 2000, CardType.CHARACTER, 1, Keyword.RUSH_CHARACTER);
        addCardsWithKeyword(deck, owner, pid+"-C3",  "Pirate Captain",    3, 3000, CardType.CHARACTER, 7, Keyword.BLOCKER);
        addCards           (deck, owner, pid+"-C4",  "Pirate Commander",  4, 4000, CardType.CHARACTER, 5);
        addCardsWithKeyword(deck, owner, pid+"-C5",  "Pirate Admiral",    5, 5000, CardType.CHARACTER, 2, Keyword.DOUBLE_ATTACK);
        addCardsWithKeyword(deck, owner, pid+"-C6",  "Yonko Officer",     6, 6000, CardType.CHARACTER, 1, Keyword.BANISH);
        addCardsWithKeyword(deck, owner, pid+"-C7",  "Yonko",             7, 7000, CardType.CHARACTER, 1, Keyword.UNBLOCKABLE);
        return deck;  // 7+2+3+2+1+7+5+2+1+1 = 31
    }

    /** 3 stages — same for all decks. */
    private static void addStages(List<Card> deck, Player owner, String pid) {
        addCards(deck, owner, pid+"-S1", "Thousand Sunny", 1, 0, CardType.STAGE, 2);
        addCards(deck, owner, pid+"-S2", "Marineford",     3, 0, CardType.STAGE, 1);
    }

    // ── 6 single-color decks ─────────────────────────────────────────────────────

    private static List<Card> buildRedDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-Ra",  "Portgas D. Ace", 2, 3000, CardType.CHARACTER, 2, "[When Attacking] +2000 power this turn.",           whenAttackingBigBoost());
        addAbilityCards(d, o, p+"-Rb",  "Eustass Kid",    3, 3000, CardType.CHARACTER, 2, "[When Attacking] +1000 power this turn.",            whenAttackingPowerBoost());
        addAbilityCards(d, o, p+"-Rc",  "Killer",         4, 4000, CardType.CHARACTER, 2, "[When Attacking] +1000 power this turn.",            whenAttackingPowerBoost());
        addAbilityCards(d, o, p+"-Rd",  "Nami",           2, 1000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                             onPlayDraw1());
        addAbilityCards(d, o, p+"-Re",  "Shanks",         6, 6000, CardType.CHARACTER, 1, "[When Attacking] +2000 power this turn.",            whenAttackingBigBoost());
        addAbilityCards(d, o, p+"-Rf",  "Vivi",           2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                             triggerDraw1());
        addAbilityCards(d, o, p+"-Rg",  "Kuzan",          5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: +3000 power. Or, K.O. 1 opp ≤3.", onPlayModalKo());
        addAbilityCards    (d, o, p+"-RE1",  "Red Hawk",        1, 0, CardType.EVENT, 2, "[Main] Give 1 of your Characters +2000 power.",      eventPowerBoost());
        addAbilityCards    (d, o, p+"-RE2",  "Gomu Gomu Pistol",2, 0, CardType.EVENT, 1, "[Main] K.O. 1 of your opponent's Characters ≤5.",    eventKo());
        addAbilityCards    (d, o, p+"-RE3",  "Conqueror's Haki",4, 0, CardType.EVENT, 1, "[Main] K.O. 1 of your opponent's Characters ≤5.",    eventKo());
        addAbilityCardsMulti(d, o, p+"-REC", "Flame Guard",     2, 0, CardType.EVENT, 2000, 1,
                "[Main] +2000 power. [Counter] +2000 power during this battle.",
                List.of(eventPowerBoost(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildBlueDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-Ba",  "Nico Robin",  2, 2000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                              onPlayDraw1());
        addAbilityCards(d, o, p+"-Bb",  "Brook",       3, 3000, CardType.CHARACTER, 2, "[On Play] Return 1 opponent Character ≤3 to hand.",   onPlayBounce());
        addAbilityCards(d, o, p+"-Bc",  "Coby",        2, 1000, CardType.CHARACTER, 2, "[On Play] Search 1 card from your deck.",             onPlaySearch());
        addAbilityCards(d, o, p+"-Bd",  "Nami",        2, 1000, CardType.CHARACTER, 2, "[When Attacking] Move 1 opponent Character to bottom.",whenAttackingBottomDeck());
        addAbilityCards(d, o, p+"-Be",  "Vivi",        2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                              triggerDraw1());
        addAbilityCards(d, o, p+"-Bf",  "Perona",      3, 2000, CardType.CHARACTER, 1, "[On Play] Move 1 opponent Character to bottom deck.", onPlayBottomDeck());
        addAbilityCards(d, o, p+"-Bg",  "Kuzan",       5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: Draw 2. Or, return 1 opp ≤3.",      onPlayModal());
        addAbilityCards    (d, o, p+"-BE1",  "Pirate's Charge", 2, 0, CardType.EVENT, 2, "[Main] Draw 2 cards.",                             eventDraw2());
        addAbilityCards    (d, o, p+"-BE2",  "Blue Gust",       3, 0, CardType.EVENT, 1, "[Main] Return 1 opponent Character ≤4 to hand.",  eventBounce());
        addAbilityCards    (d, o, p+"-BE3",  "Sea of Clouds",   5, 0, CardType.EVENT, 1, "[Main] Move 1 opponent Character to bottom deck.",eventBottomDeck());
        addAbilityCardsMulti(d, o, p+"-BEC", "Tidal Guard",     2, 0, CardType.EVENT, 2000, 1,
                "[Main] Draw 1 card. [Counter] +2000 power during this battle.",
                List.of(eventDraw1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildGreenDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-Ga",  "Jinbe",   3, 3000, CardType.CHARACTER, 2, "[On Play] Rest 1 of your opponent's Characters.",       onPlayRestOpponent());
        addAbilityCards(d, o, p+"-Gb",  "Chopper", 2, 1000, CardType.CHARACTER, 2, "[On Play] Unrest 1 of your Characters.",                onPlayUnrest());
        addAbilityCards(d, o, p+"-Gc",  "Franky",  4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Set 1 DON!! card as active.",        endTurnSetDonActive());
        addAbilityCards(d, o, p+"-Gd",  "Perona",  3, 2000, CardType.CHARACTER, 2, "[On Play] This Character gains [Blocker] until end.",   onPlayGrantBlocker());
        addAbilityCards(d, o, p+"-Ge",  "Usopp",   2, 2000, CardType.CHARACTER, 1, "[Trigger] Rest 1 of your opponent's Characters.",       triggerRestOpponent());
        addAbilityCards(d, o, p+"-Gf",  "Sanji",   3, 3000, CardType.CHARACTER, 1, "[When Attacking] +1000 power this turn.",               whenAttackingPowerBoost());
        addAbilityCards(d, o, p+"-Gg",  "Kuzan",   5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: Unrest 1 own. Or, Rest 1 opp ≤4.",   onPlayModalGreen());
        addAbilityCards    (d, o, p+"-GE1",  "Oni Giri",       2, 0, CardType.EVENT, 2, "[Main] Rest 1 of your opponent's Characters.",      eventRest());
        addAbilityCards    (d, o, p+"-GE2",  "Straw Hat Rally",1, 0, CardType.EVENT, 1, "[Main] Set 2 of your DON!! cards as active.",       eventSetDon2());
        addAbilityCards    (d, o, p+"-GE3",  "Diable Jambe",   3, 0, CardType.EVENT, 1, "[Main] Play 1 Character with cost 4 or less free.", eventPlayCard());
        addAbilityCardsMulti(d, o, p+"-GEC", "Vine Guard",     2, 0, CardType.EVENT, 2000, 1,
                "[Main] Rest 1 of your opponent's Characters. [Counter] +2000 power during this battle.",
                List.of(eventRest(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildPurpleDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-Pa",  "Charlotte Cracker",  4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Add 1 DON!! card.",              endTurnAddDon());
        addAbilityCards(d, o, p+"-Pb",  "Charlotte Smoothie", 4, 4000, CardType.CHARACTER, 2, "[On Play] Set 2 of your DON!! cards as active.",     onPlaySetDonActive2());
        addAbilityCards(d, o, p+"-Pc",  "Prometheus",         3, 3000, CardType.CHARACTER, 2, "[When Attacking] +2000 power this turn.",             whenAttackingBigBoost());
        addAbilityCards(d, o, p+"-Pd",  "Napoleon",           3, 2000, CardType.CHARACTER, 2, "[On Play] Play 1 Character ≤5 from hand free.",       onPlayFreePlayChar());
        addAbilityCards(d, o, p+"-Pe",  "Zeus",               2, 1000, CardType.CHARACTER, 1, "[Trigger] Add 1 DON!! card.",                         triggerAddDon());
        addAbilityCards(d, o, p+"-Pf",  "Charlotte Katakuri", 6, 6000, CardType.CHARACTER, 1, "[On Play] Add 1 DON!! card.",                         onPlayAddDon());
        addAbilityCards(d, o, p+"-Pg",  "Big Mom",            7, 7000, CardType.CHARACTER, 1, "[On Play] Choose: Add 2 DON!!. Or, Set all active.",  onPlayModalPurple());
        addAbilityCards    (d, o, p+"-PE1",  "Emperor's Power", 2, 0, CardType.EVENT, 2, "[Main] Add 2 DON!! cards from your DON!! deck.",  eventAddDon2());
        addAbilityCards    (d, o, p+"-PE2",  "Soul Pocus",      3, 0, CardType.EVENT, 1, "[Main] Set 3 of your DON!! cards as active.",     eventSetDon3());
        addAbilityCards    (d, o, p+"-PE3",  "Heavenly Strike", 1, 0, CardType.EVENT, 1, "[Main] Give 1 of your Characters +3000 power.",   eventPowerBoost3000());
        addAbilityCardsMulti(d, o, p+"-PEC", "Emperor's Guard", 2, 0, CardType.EVENT, 2000, 1,
                "[Main] Add 1 DON!! card. [Counter] +2000 power during this battle.",
                List.of(eventAddDon1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildBlackDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-La",  "Trafalgar Law",    3, 3000, CardType.CHARACTER, 2, "[On Play] K.O. 1 of your opponent's Characters ≤3.",  onPlayKoOpponent());
        addAbilityCards(d, o, p+"-Lb",  "Basil Hawkins",    3, 3000, CardType.CHARACTER, 2, "[On K.O.] Draw 1 card.",                               onKoDraw1());
        addAbilityCards(d, o, p+"-Lc",  "Scratchmen Apoo",  2, 2000, CardType.CHARACTER, 2, "[Trigger] K.O. 1 of your opponent's Characters ≤2.",   triggerKo());
        addAbilityCards(d, o, p+"-Ld",  "Killer",           3, 3000, CardType.CHARACTER, 2, "[On Play] Give 1 opponent Character -2 cost.",          onPlayCostReduce());
        addAbilityCards(d, o, p+"-Le",  "Jewelry Bonney",   3, 3000, CardType.CHARACTER, 1, "[On Play] Play 1 Character from your trash free.",      onPlayPlayFromTrash());
        addAbilityCards(d, o, p+"-Lf",  "X Drake",          4, 4000, CardType.CHARACTER, 1, "[On Play] Trash 1 from hand, then draw 1.",             onPlayTrashAndDraw());
        addAbilityCards(d, o, p+"-Lg",  "Kuzan",            5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: K.O. ≤4. Or, Trash 1 opp hand.",     onPlayModalBlack());
        addAbilityCards    (d, o, p+"-LE1",  "Shambles",     3, 0, CardType.EVENT, 2, "[Main] K.O. 1 of your opponent's Characters ≤5.",     eventKo());
        addAbilityCards    (d, o, p+"-LE2",  "K-ROOM",      2, 0, CardType.EVENT, 1, "[Main] Give 1 of your Characters -3 cost.",             eventCostReduce3());
        addAbilityCards    (d, o, p+"-LE3",  "Room",        4, 0, CardType.EVENT, 1, "[Main] Trash 2 cards from your opponent's hand.",       eventTrash2());
        addAbilityCardsMulti(d, o, p+"-LEC", "Death Guard", 2, 0, CardType.EVENT, 2000, 1,
                "[Main] Give 1 opponent Character -2 cost. [Counter] +2000 power during this battle.",
                List.of(eventCostReduce3(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildYellowDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-Ya",  "Charlotte Linlin", 4, 5000, CardType.CHARACTER, 2, "[On Play] Add 1 card from top of deck to your life.",   onPlayAddLife());
        addAbilityCards(d, o, p+"-Yb",  "Pudding",          2, 2000, CardType.CHARACTER, 2, "[Trigger] Add 1 card to your life.",                     triggerAddLife());
        addAbilityCards(d, o, p+"-Yc",  "Carrot",           2, 2000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                                 onPlayDraw1());
        addAbilityCards(d, o, p+"-Yd",  "Pedro",            3, 3000, CardType.CHARACTER, 2, "[On Play] K.O. 1 of your opponent's Characters ≤4.",     onPlayKoOpponent4());
        addAbilityCards(d, o, p+"-Ye",  "Vivi",             2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                                 triggerDraw1());
        addAbilityCards(d, o, p+"-Yf",  "Brook",            3, 3000, CardType.CHARACTER, 1, "[On Play] Add 2 cards to your life.",                    onPlayAddLife2());
        addAbilityCards(d, o, p+"-Yg",  "Jinbe",            5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: Add 2 life. Or, Draw 2 cards.",        onPlayModalYellow());
        addAbilityCards    (d, o, p+"-YE1",  "Soul King Brook",  3, 0, CardType.EVENT, 2, "[Main] Add 2 cards from top of deck to your life.",eventAddLife2());
        addAbilityCards    (d, o, p+"-YE2",  "Whole Cake Island",3, 0, CardType.EVENT, 1, "[Main] K.O. 1 of your opponent's Characters ≤4.",eventKo());
        addAbilityCards    (d, o, p+"-YE3",  "Mirro-World",      2, 0, CardType.EVENT, 1, "[Main] Draw 2 cards.",                             eventDraw2());
        addAbilityCardsMulti(d, o, p+"-YEC", "Soul Shield",      2, 0, CardType.EVENT, 2000, 1,
                "[Main] Add 1 card to your life. [Counter] +2000 power during this battle.",
                List.of(eventAddLife1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    // ── 6 dual-color decks ───────────────────────────────────────────────────────

    private static List<Card> buildRedGreenDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-RGa",  "Portgas D. Ace", 2, 3000, CardType.CHARACTER, 2, "[When Attacking] +2000 power this turn.",    whenAttackingBigBoost());
        addAbilityCards(d, o, p+"-RGb",  "Jinbe",          3, 3000, CardType.CHARACTER, 2, "[On Play] Rest 1 of your opponent's Characters.",onPlayRestOpponent());
        addAbilityCards(d, o, p+"-RGc",  "Franky",         4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Set 1 DON!! as active.",    endTurnSetDonActive());
        addAbilityCards(d, o, p+"-RGd",  "Nami",           2, 1000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                       onPlayDraw1());
        addAbilityCards(d, o, p+"-RGe",  "Usopp",          2, 2000, CardType.CHARACTER, 1, "[Trigger] Rest 1 opponent Character.",         triggerRestOpponent());
        addAbilityCards(d, o, p+"-RGf",  "Vivi",           2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                       triggerDraw1());
        addAbilityCards(d, o, p+"-RGg",  "Kuzan",          5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: +3000. Or, K.O. opp ≤3.",   onPlayModalKo());
        addAbilityCards    (d, o, p+"-RGE1",  "Red Hawk",       1, 0, CardType.EVENT, 2, "[Main] Give 1 of your Characters +2000 power.", eventPowerBoost());
        addAbilityCards    (d, o, p+"-RGE2",  "Oni Giri",       2, 0, CardType.EVENT, 1, "[Main] Rest 1 of your opponent's Characters.",  eventRest());
        addAbilityCards    (d, o, p+"-RGE3",  "Straw Hat Rally",1, 0, CardType.EVENT, 1, "[Main] Set 2 of your DON!! cards as active.",   eventSetDon2());
        addAbilityCardsMulti(d, o, p+"-RGEC", "Flame Guard",    2, 0, CardType.EVENT, 2000, 1,
                "[Main] +2000 power. [Counter] +2000 power during this battle.",
                List.of(eventPowerBoost(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildBlueBlackDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-BBa",  "Coby",           2, 1000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                       onPlayDraw1());
        addAbilityCards(d, o, p+"-BBb",  "Brook",          3, 3000, CardType.CHARACTER, 2, "[On Play] Return 1 opponent Character ≤3.",    onPlayBounce());
        addAbilityCards(d, o, p+"-BBc",  "Trafalgar Law",  3, 3000, CardType.CHARACTER, 2, "[On Play] K.O. 1 of your opponent's ≤3.",      onPlayKoOpponent());
        addAbilityCards(d, o, p+"-BBd",  "Basil Hawkins",  3, 3000, CardType.CHARACTER, 2, "[On K.O.] Draw 1 card.",                       onKoDraw1());
        addAbilityCards(d, o, p+"-BBe",  "Vivi",           2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                       triggerDraw1());
        addAbilityCards(d, o, p+"-BBf",  "Jewelry Bonney", 3, 3000, CardType.CHARACTER, 1, "[On Play] Play 1 Character from your trash.",  onPlayPlayFromTrash());
        addAbilityCards(d, o, p+"-BBg",  "Kuzan",          5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: Draw 2. Or, return 1 ≤3.",  onPlayModal());
        addAbilityCards    (d, o, p+"-BBE1",  "Pirate's Charge",2, 0, CardType.EVENT, 2, "[Main] Draw 2 cards.",                      eventDraw2());
        addAbilityCards    (d, o, p+"-BBE2",  "Shambles",       3, 0, CardType.EVENT, 1, "[Main] K.O. 1 of your opponent's ≤5.",      eventKo());
        addAbilityCards    (d, o, p+"-BBE3",  "K-ROOM",         2, 0, CardType.EVENT, 1, "[Main] Give 1 of your Characters -3 cost.",  eventCostReduce3());
        addAbilityCardsMulti(d, o, p+"-BBEC", "Tidal Guard",    2, 0, CardType.EVENT, 2000, 1,
                "[Main] Draw 1 card. [Counter] +2000 power during this battle.",
                List.of(eventDraw1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildPurpleGreenDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-PGa",  "Charlotte Cracker",  4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Add 1 DON!! card.",         endTurnAddDon());
        addAbilityCards(d, o, p+"-PGb",  "Jinbe",              3, 3000, CardType.CHARACTER, 2, "[On Play] Rest 1 of your opponent's Characters.",onPlayRestOpponent());
        addAbilityCards(d, o, p+"-PGc",  "Franky",             4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Set 1 DON!! as active.",      endTurnSetDonActive());
        addAbilityCards(d, o, p+"-PGd",  "Charlotte Smoothie", 4, 4000, CardType.CHARACTER, 2, "[On Play] Set 2 of your DON!! as active.",       onPlaySetDonActive2());
        addAbilityCards(d, o, p+"-PGe",  "Zeus",               2, 1000, CardType.CHARACTER, 1, "[Trigger] Add 1 DON!! card.",                    triggerAddDon());
        addAbilityCards(d, o, p+"-PGf",  "Napoleon",           3, 2000, CardType.CHARACTER, 1, "[On Play] Play 1 Character ≤5 from hand.",       onPlayFreePlayChar());
        addAbilityCards(d, o, p+"-PGg",  "Big Mom",            7, 7000, CardType.CHARACTER, 1, "[On Play] Choose: Add 2 DON!!. Or, Set all.",    onPlayModalPurple());
        addAbilityCards    (d, o, p+"-PGE1",  "Emperor's Power", 2, 0, CardType.EVENT, 2, "[Main] Add 2 DON!! cards.",                     eventAddDon2());
        addAbilityCards    (d, o, p+"-PGE2",  "Oni Giri",       2, 0, CardType.EVENT, 1, "[Main] Rest 1 of your opponent's Characters.",  eventRest());
        addAbilityCards    (d, o, p+"-PGE3",  "Soul Pocus",     3, 0, CardType.EVENT, 1, "[Main] Set 3 of your DON!! cards as active.",   eventSetDon3());
        addAbilityCardsMulti(d, o, p+"-PGEC", "Emperor's Guard",2, 0, CardType.EVENT, 2000, 1,
                "[Main] Add 1 DON!! card. [Counter] +2000 power during this battle.",
                List.of(eventAddDon1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildYellowRedDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-YRa",  "Charlotte Linlin", 4, 5000, CardType.CHARACTER, 2, "[On Play] Add 1 card to your life.",              onPlayAddLife());
        addAbilityCards(d, o, p+"-YRb",  "Pudding",          2, 2000, CardType.CHARACTER, 2, "[Trigger] Add 1 card to your life.",              triggerAddLife());
        addAbilityCards(d, o, p+"-YRc",  "Portgas D. Ace",   2, 3000, CardType.CHARACTER, 2, "[When Attacking] +2000 power this turn.",         whenAttackingBigBoost());
        addAbilityCards(d, o, p+"-YRd",  "Pedro",            3, 3000, CardType.CHARACTER, 2, "[On Play] K.O. 1 of your opponent's ≤4.",         onPlayKoOpponent4());
        addAbilityCards(d, o, p+"-YRe",  "Vivi",             2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                          triggerDraw1());
        addAbilityCards(d, o, p+"-YRf",  "Nami",             2, 1000, CardType.CHARACTER, 1, "[On Play] Draw 1 card.",                          onPlayDraw1());
        addAbilityCards(d, o, p+"-YRg",  "Kuzan",            5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: +3000. Or, K.O. opp ≤3.",      onPlayModalKo());
        addAbilityCards    (d, o, p+"-YRE1",  "Soul King Brook",  3, 0, CardType.EVENT, 2, "[Main] Add 2 cards from top of deck to life.",     eventAddLife2());
        addAbilityCards    (d, o, p+"-YRE2",  "Red Hawk",         1, 0, CardType.EVENT, 1, "[Main] Give 1 of your Characters +2000 power.",    eventPowerBoost());
        addAbilityCards    (d, o, p+"-YRE3",  "Gomu Pistol",      2, 0, CardType.EVENT, 1, "[Main] K.O. 1 of your opponent's Characters ≤5.",  eventKo());
        addAbilityCardsMulti(d, o, p+"-YREC", "Soul Shield",      2, 0, CardType.EVENT, 2000, 1,
                "[Main] Add 1 card to your life. [Counter] +2000 power during this battle.",
                List.of(eventAddLife1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildBluePurpleDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-BPa",  "Nico Robin",          2, 2000, CardType.CHARACTER, 2, "[On Play] Draw 1 card.",                       onPlayDraw1());
        addAbilityCards(d, o, p+"-BPb",  "Coby",                2, 1000, CardType.CHARACTER, 2, "[On Play] Search 1 card from your deck.",       onPlaySearch());
        addAbilityCards(d, o, p+"-BPc",  "Charlotte Cracker",   4, 4000, CardType.CHARACTER, 2, "[End of Your Turn] Add 1 DON!! card.",          endTurnAddDon());
        addAbilityCards(d, o, p+"-BPd",  "Charlotte Smoothie",  4, 4000, CardType.CHARACTER, 2, "[On Play] Set 2 of your DON!! as active.",      onPlaySetDonActive2());
        addAbilityCards(d, o, p+"-BPe",  "Vivi",                2, 2000, CardType.CHARACTER, 1, "[Trigger] Draw 1 card.",                        triggerDraw1());
        addAbilityCards(d, o, p+"-BPf",  "Napoleon",            3, 2000, CardType.CHARACTER, 1, "[On Play] Play 1 Character ≤5 from hand.",      onPlayFreePlayChar());
        addAbilityCards(d, o, p+"-BPg",  "Kuzan",               5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: Draw 2. Or, Add 2 DON!!.",    onPlayModalBluePurple());
        addAbilityCards    (d, o, p+"-BPE1",  "Pirate's Charge",  2, 0, CardType.EVENT, 2, "[Main] Draw 2 cards.",                        eventDraw2());
        addAbilityCards    (d, o, p+"-BPE2",  "Emperor's Power",  2, 0, CardType.EVENT, 1, "[Main] Add 2 DON!! cards.",                    eventAddDon2());
        addAbilityCards    (d, o, p+"-BPE3",  "Soul Pocus",       3, 0, CardType.EVENT, 1, "[Main] Set 3 of your DON!! cards as active.",  eventSetDon3());
        addAbilityCardsMulti(d, o, p+"-BPEC", "Tidal Guard",      2, 0, CardType.EVENT, 2000, 1,
                "[Main] Draw 1 card. [Counter] +2000 power during this battle.",
                List.of(eventDraw1(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    private static List<Card> buildBlackYellowDeck(Player o, String p) {
        List<Card> d = genericBase(o, p);
        addAbilityCards(d, o, p+"-BYa",  "Trafalgar Law",    3, 3000, CardType.CHARACTER, 2, "[On Play] K.O. 1 of your opponent's ≤3.",        onPlayKoOpponent());
        addAbilityCards(d, o, p+"-BYb",  "Basil Hawkins",    3, 3000, CardType.CHARACTER, 2, "[On K.O.] Draw 1 card.",                          onKoDraw1());
        addAbilityCards(d, o, p+"-BYc",  "Charlotte Linlin", 4, 5000, CardType.CHARACTER, 2, "[On Play] Add 1 card to your life.",              onPlayAddLife());
        addAbilityCards(d, o, p+"-BYd",  "Pudding",          2, 2000, CardType.CHARACTER, 2, "[Trigger] Add 1 card to your life.",              triggerAddLife());
        addAbilityCards(d, o, p+"-BYe",  "Scratchmen Apoo",  2, 2000, CardType.CHARACTER, 1, "[Trigger] K.O. 1 of your opponent's ≤2.",         triggerKo());
        addAbilityCards(d, o, p+"-BYf",  "Jewelry Bonney",   3, 3000, CardType.CHARACTER, 1, "[On Play] Play 1 Character from your trash.",     onPlayPlayFromTrash());
        addAbilityCards(d, o, p+"-BYg",  "Kuzan",            5, 5000, CardType.CHARACTER, 1, "[On Play] Choose: K.O. ≤4. Or, Add 2 life.",     onPlayModalBlackYellow());
        addAbilityCards    (d, o, p+"-BYE1",  "Shambles",        3, 0, CardType.EVENT, 2, "[Main] K.O. 1 of your opponent's Characters ≤5.", eventKo());
        addAbilityCards    (d, o, p+"-BYE2",  "Soul King Brook", 3, 0, CardType.EVENT, 1, "[Main] Add 2 cards from top of deck to life.",    eventAddLife2());
        addAbilityCards    (d, o, p+"-BYE3",  "K-ROOM",          2, 0, CardType.EVENT, 1, "[Main] Give 1 of your Characters -3 cost.",       eventCostReduce3());
        addAbilityCardsMulti(d, o, p+"-BYEC", "Death Guard",     2, 0, CardType.EVENT, 2000, 1,
                "[Main] Give 1 opponent Character -2 cost. [Counter] +2000 power during this battle.",
                List.of(eventCostReduce3(), counterBoost2000()));
        addStages(d, o, p);
        return d;
    }

    // ── New ability builders ─────────────────────────────────────────────────────

    private static AbilityData whenAttackingBigBoost() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0, null,null,null,null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 2000, self, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.WHEN_ATTACKING, 0, null, true, null, null, List.of(boost));
    }

    private static AbilityData onPlayBounce() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 3, 0, 0, 0, null,null,null,null);
        EffectData bounce = new EffectData(EffectType.RETURN_TO_HAND, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(bounce));
    }

    private static AbilityData onPlaySearch() {
        Target t = new Target(Target.TargetPlayer.SELF, ZoneType.DECK, null, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData search = new EffectData(EffectType.SEARCH_DECK, 0, t, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(search));
    }

    private static AbilityData whenAttackingBottomDeck() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData move = new EffectData(EffectType.MOVE_TO_BOTTOM_DECK, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.WHEN_ATTACKING, 0, null, true, null, null, List.of(move));
    }

    private static AbilityData onPlayBottomDeck() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData move = new EffectData(EffectType.MOVE_TO_BOTTOM_DECK, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(move));
    }

    private static AbilityData onPlayRestOpponent() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(rest));
    }

    private static AbilityData onPlayUnrest() {
        Target self = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData unrest = new EffectData(EffectType.UNREST, 0, self, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(unrest));
    }

    private static AbilityData endTurnSetDonActive() {
        EffectData set = new EffectData(EffectType.SET_DON_ACTIVE, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.END_OF_YOUR_TURN, 0, null, true, null, null, List.of(set));
    }

    private static AbilityData onPlayAddDon() {
        EffectData add = new EffectData(EffectType.ADD_DON, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData endTurnAddDon() {
        EffectData add = new EffectData(EffectType.ADD_DON, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.END_OF_YOUR_TURN, 0, null, true, null, null, List.of(add));
    }

    private static AbilityData onPlaySetDonActive2() {
        EffectData set = new EffectData(EffectType.SET_DON_ACTIVE, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(set));
    }

    private static AbilityData onPlayAddLife() {
        EffectData add = new EffectData(EffectType.ADD_LIFE, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData onPlayAddLife2() {
        EffectData add = new EffectData(EffectType.ADD_LIFE, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData triggerAddLife() {
        EffectData add = new EffectData(EffectType.ADD_LIFE, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData triggerAddDon() {
        EffectData add = new EffectData(EffectType.ADD_DON, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData triggerRestOpponent() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(rest));
    }

    private static AbilityData triggerKo() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 2, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.TRIGGER, 0, null, false, null, null, List.of(ko));
    }

    private static AbilityData onPlayKoOpponent() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 3, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(ko));
    }

    private static AbilityData onPlayKoOpponent4() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(ko));
    }

    private static AbilityData onPlayFreePlayChar() {
        Target t = new Target(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 5, 0, 0, 0, null,null,null,null);
        EffectData play = new EffectData(EffectType.PLAY_CARD, 0, t, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(play));
    }

    private static AbilityData onPlayPlayFromTrash() {
        Target t = new Target(Target.TargetPlayer.SELF, ZoneType.TRASH, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData play = new EffectData(EffectType.PLAY_CARD, 0, t, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(play));
    }

    private static AbilityData onPlayTrashAndDraw() {
        Target hand = new Target(Target.TargetPlayer.SELF, ZoneType.HAND, null, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData trash = new EffectData(EffectType.TRASH, 0, hand, null, null, null, null, null, 0);
        EffectData draw  = new EffectData(EffectType.DRAW,  1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(trash, draw));
    }

    private static AbilityData onPlayCostReduce() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData reduce = new EffectData(EffectType.COST_MODIFY, -2, opp, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(reduce));
    }

    // ── Modal variants ───────────────────────────────────────────────────────────

    private static AbilityData onPlayModalKo() {
        Target self = new Target(Target.TargetPlayer.THIS_CARD, null, null, 0, 0, 0, 0, 0, null,null,null,null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 3000, self, null, null, Duration.THIS_TURN, null, null, 0);
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 3, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(boost), List.of(ko)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalGreen() {
        Target self = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData unrest = new EffectData(EffectType.UNREST, 0, self, null, null, null, null, null, 0);
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(unrest), List.of(rest)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalPurple() {
        EffectData addDon = new EffectData(EffectType.ADD_DON, 2, null, null, null, null, null, null, 0);
        EffectData setDon = new EffectData(EffectType.SET_DON_ACTIVE, 10, null, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(addDon), List.of(setDon)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalBlack() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData ko = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        Target oppHand = new Target(Target.TargetPlayer.OPPONENT, ZoneType.HAND, null, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData trash = new EffectData(EffectType.TRASH, 0, oppHand, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(ko), List.of(trash)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalYellow() {
        EffectData addLife = new EffectData(EffectType.ADD_LIFE, 2, null, null, null, null, null, null, 0);
        EffectData draw2   = new EffectData(EffectType.DRAW, 2, null, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(addLife), List.of(draw2)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalBluePurple() {
        EffectData draw2   = new EffectData(EffectType.DRAW, 2, null, null, null, null, null, null, 0);
        EffectData addDon  = new EffectData(EffectType.ADD_DON, 2, null, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(draw2), List.of(addDon)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    private static AbilityData onPlayModalBlackYellow() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData ko      = new EffectData(EffectType.KO, 0, opp, null, null, null, null, null, 0);
        EffectData addLife = new EffectData(EffectType.ADD_LIFE, 2, null, null, null, null, null, null, 0);
        EffectData modal = new EffectData(EffectType.MODAL, 0, null, null, null, null, null, List.of(List.of(ko), List.of(addLife)), 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(modal));
    }

    // ── New event builders ───────────────────────────────────────────────────────

    private static AbilityData eventBounce() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData bounce = new EffectData(EffectType.RETURN_TO_HAND, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(bounce));
    }

    private static AbilityData eventBottomDeck() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData move = new EffectData(EffectType.MOVE_TO_BOTTOM_DECK, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(move));
    }

    private static AbilityData eventAddDon2() {
        EffectData add = new EffectData(EffectType.ADD_DON, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData eventSetDon2() {
        EffectData set = new EffectData(EffectType.SET_DON_ACTIVE, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(set));
    }

    private static AbilityData eventSetDon3() {
        EffectData set = new EffectData(EffectType.SET_DON_ACTIVE, 3, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(set));
    }

    private static AbilityData eventRest() {
        Target opp = new Target(Target.TargetPlayer.OPPONENT, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData rest = new EffectData(EffectType.REST, 0, opp, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(rest));
    }

    private static AbilityData eventAddLife2() {
        EffectData add = new EffectData(EffectType.ADD_LIFE, 2, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    private static AbilityData eventTrash2() {
        Target oppHand = new Target(Target.TargetPlayer.OPPONENT, ZoneType.HAND, null, 2, 0, 0, 0, 0, null,null,null,null);
        EffectData trash = new EffectData(EffectType.TRASH, 0, oppHand, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(trash));
    }

    private static AbilityData eventCostReduce3() {
        Target self = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData reduce = new EffectData(EffectType.COST_MODIFY, -3, self, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(reduce));
    }

    private static AbilityData eventPowerBoost3000() {
        Target self = new Target(Target.TargetPlayer.SELF, ZoneType.CHARACTER, CardType.CHARACTER, 1, 0, 0, 0, 0, null,null,null,null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 3000, self, null, null, Duration.THIS_TURN, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(boost));
    }

    private static AbilityData eventPlayCard() {
        Target t = new Target(Target.TargetPlayer.SELF, ZoneType.HAND, CardType.CHARACTER, 1, 4, 0, 0, 0, null,null,null,null);
        EffectData play = new EffectData(EffectType.PLAY_CARD, 0, t, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(play));
    }

    // ── Counter ability builders ──────────────────────────────────────────────────

    /**
     * [Counter] Give your Leader or 1 of your Characters +2000 power during this battle.
     * Used as the SECOND ability on dual-effect event counter cards alongside a [Main] ability.
     */
    private static AbilityData counterBoost2000() {
        Target t = new Target(Target.TargetPlayer.SELF, null, null, 1, 0, 0, 0, 0, null, null, null, null);
        EffectData boost = new EffectData(EffectType.POWER_BOOST, 2000, t, null, null, Duration.THIS_BATTLE, null, null, 0);
        return new AbilityData(Trigger.COUNTER, 0, null, false, null, null, List.of(boost));
    }

    // Primary [Main] effects paired with counterBoost2000() on event counter cards:

    /** [Main] Draw 1 card. */
    private static AbilityData eventDraw1() {
        EffectData draw = new EffectData(EffectType.DRAW, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(draw));
    }

    /** [Main] Add 1 DON!! card from your DON!! deck. */
    private static AbilityData eventAddDon1() {
        EffectData add = new EffectData(EffectType.ADD_DON, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }

    /** [Main] Add 1 card from the top of your deck to your life area. */
    private static AbilityData eventAddLife1() {
        EffectData add = new EffectData(EffectType.ADD_LIFE, 1, null, null, null, null, null, null, 0);
        return new AbilityData(Trigger.ON_PLAY, 0, null, false, null, null, List.of(add));
    }
}
