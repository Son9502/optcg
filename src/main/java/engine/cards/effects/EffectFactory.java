package engine.cards.effects;

import engine.cards.Card;
import engine.cards.DonCard;
import engine.cards.abilities.Ability;
import engine.cards.abilities.AbilityData;
import engine.cards.abilities.Condition;
import engine.cards.abilities.Trigger;
import engine.cards.keywords.Keyword;
//import engine.cards.types.Attribute;
import engine.cards.types.CardType;
import engine.core.GameState;
import engine.player.Player;
import engine.zones.Zone;
import engine.zones.ZoneType;
import engine.cards.types.Color; 

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EffectFactory {

    /**
     * Build a runtime {@link Effect} from compiled {@link EffectData}.
     *
     * @param effectData the compiled effect descriptor
     * @param sourceCard the card that carries this effect — used for THIS_CARD
     *                   targeting and IF_RESTED condition checks; may be null for
     *                   event cards whose source has already moved to trash
     * @return an Effect lambda ready to be applied to a GameState
     */
    public static Effect build(EffectData effectData, Card sourceCard) {
        return (gameState, source) -> {
            if (effectData == null || effectData.type() == null) return;
            if (!evaluateCondition(effectData.condition(), effectData.conditionValue(), effectData.subtype(), gameState, source, sourceCard)) return;

            switch (effectData.type()) {
                case DRAW         -> gameState.draw(source, Math.max(effectData.value(), 1));
                case ADD_DON      -> gameState.drawDon(source, Math.max(effectData.value(), 1));
                case SET_DON_ACTIVE -> applySetDonActive(source, effectData.value());
                case REST         -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(Card::rest);
                case UNREST       -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(Card::activate);
                case KO           -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> gameState.trash(c.getOwner(), c));
                case TRASH        -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> gameState.trash(c.getOwner(), c));
                case RETURN_TO_HAND -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> gameState.moveCard(c, c.getOwner().getHand()));
                case POWER_BOOST  -> {
                    Duration dur = effectData.duration();
                    int val = effectData.value();
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (dur == Duration.THIS_BATTLE)        c.addBattlePowerBoost(val);
                        else if (dur == Duration.OPPONENTS_NEXT_TURN) c.addBonusPowerOpponentTurn(val);
                        else if (dur == Duration.WHILE_IN_PLAY) c.addPowerBoostWhileInPlay(val);
                        else                                    c.addPowerBoost(val);
                    });
                }
                case POWER_REDUCE -> {
                    Duration dur = effectData.duration();
                    int val = effectData.value();
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (dur == Duration.THIS_BATTLE)        c.addBattlePowerBoost(-val);
                        else if (dur == Duration.OPPONENTS_NEXT_TURN) c.addBonusPowerOpponentTurn(-val);
                        else if (dur == Duration.WHILE_IN_PLAY) c.addPowerBoostWhileInPlay(-val);
                        else                                    c.addPowerBoost(-val);
                    });
                }
                case ATTACH_DON   -> applyAttachDon(effectData, gameState, source, sourceCard);
                case ADD_LIFE     -> applyAddLife(effectData, gameState, source);
                case COST_MODIFY  -> {
                    Duration costDur = effectData.duration();
                    int costVal = effectData.value();
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (costDur == Duration.OPPONENTS_NEXT_TURN) c.addBonusCostOpponentTurn(costVal);
                        else if (costDur == Duration.WHILE_IN_PLAY)  c.addCostModifierWhileInPlay(costVal);
                        else                                         c.addCostModifier(costVal);
                    });
                }
                case SHUFFLE_DECK -> source.getDeck().shuffle();
                case GRANT_KEYWORD -> {
                    if (effectData.keyword() != null) {
                        boolean permanent = effectData.duration() == Duration.WHILE_IN_PLAY;
                        resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                            if (permanent) c.addAbility(new Ability(new AbilityData(Trigger.KEYWORD, 0, null, false, null, effectData.keyword(), List.of())));
                            else           c.grantKeyword(effectData.keyword());
                        });
                    }
                }
                case MOVE_TO_TOP_DECK -> resolveTargets(effectData, gameState, source, sourceCard)
                                             .forEach(c -> gameState.moveCard(c, c.getOwner().getDeck()));
                case MOVE_TO_BOTTOM_DECK -> resolveTargets(effectData, gameState, source, sourceCard)
                                             .forEach(c -> gameState.moveCardtoBottom(c, c.getOwner().getDeck()));
                case SEARCH_DECK -> applySearchDeck(effectData, gameState, source, sourceCard);
                case PREVENT_ATTACK -> {
                    boolean permanent = effectData.duration() == Duration.WHILE_IN_PLAY;
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (permanent) c.addAbility(new Ability(new AbilityData(Trigger.KEYWORD, 0, null, false, null, Keyword.CANNOT_ATTACK, List.of())));
                        else           c.grantKeyword(Keyword.CANNOT_ATTACK);
                    });
                }
                case PREVENT_BLOCK -> {
                    boolean permanent = effectData.duration() == Duration.WHILE_IN_PLAY;
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (permanent) c.addAbility(new Ability(new AbilityData(Trigger.KEYWORD, 0, null, false, null, Keyword.UNBLOCKABLE, List.of())));
                        else           c.grantKeyword(Keyword.UNBLOCKABLE);
                    });
                }
                case PREVENT_KO -> {
                    boolean permanent = effectData.duration() == Duration.WHILE_IN_PLAY;
                    resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> {
                        if (permanent) c.addAbility(new Ability(new AbilityData(Trigger.KEYWORD, 0, null, false, null, Keyword.CANNOT_KO, List.of())));
                        else           c.grantKeyword(Keyword.CANNOT_KO);
                    });
                }
                case PLAY_CARD -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> gameState.playCardFree(source, c));
                case MODAL -> applyModal(effectData, gameState, source, sourceCard);
                case FLIP_LIFE -> applyFlipLife(effectData, gameState, source);
                // Phase-2 stubs — no-op until implemented
                case NEGATE -> resolveTargets(effectData, gameState, source, sourceCard)
                        .forEach(c -> c.getAbilities().forEach(a -> a.setNegated(true)));
            }
        };
    }

    // ── Condition gate ──────────────────────────────────────────────────────────

    private static boolean evaluateCondition(Condition condition, int conditionValue,
                                              String subtype,
                                              GameState gs, Player source, Card sourceCard) {
        if (condition == null) return true;
        return switch (condition) {
            // Enforced externally by TurnManager / Ability.canActivate()
            case YOUR_TURN, OPPONENTS_TURN, ONCE_PER_TURN -> true;
            // Permissive stub: caller's chain guarantees the prior effect resolved
            case IF_YOU_DO -> true;
            // Precise: check whether the source card is currently rested
            case IF_RESTED -> sourceCard != null && sourceCard.isRested();
            // Threshold conditions: check whether the relevant count meets the conditionValue
            case IF_LIFE_COUNT        -> meetsThreshold(source.getLife().size(), conditionValue);
            case IF_HAND_COUNT        -> meetsThreshold(source.getHand().size(), conditionValue);
            case IF_DON_COUNT         -> meetsThreshold(source.getCost().getActive(), conditionValue);
            case IF_CHARACTER_COUNT   -> meetsThreshold(source.getField().size(), conditionValue);
            case IF_TRASH_COUNT       -> meetsThreshold(source.getTrash().size(), conditionValue);
            case IF_DECK_COUNT        -> meetsThreshold(source.getDeck().size(), conditionValue);
            case IF_FIELD_CHARACTER_COST -> source.getField().getCards().stream()
                    .anyMatch(c -> c.getData() != null && meetsThreshold(c.getEffectiveCost(), conditionValue));
            case IF_OPPONENT_LIFE_COUNT -> meetsThreshold(getOpponent(gs, source).getLife().size(), conditionValue);
            case IF_OPPONENT_HAND_COUNT -> meetsThreshold(getOpponent(gs, source).getHand().size(), conditionValue);
            // Leader is multicolored (has more than one color)
            case IF_LEADER_MULTICOLORED -> source.getLeader() != null
                    && source.getLeader().getData() != null
                    && source.getLeader().getData().colors() != null
                    && source.getLeader().getData().colors().size() > 1;
            // Leader has the required named card (e.g. "if your Leader is [Sanji]")
            case IF_LEADER_NAME -> source.getLeader() != null
                    && source.getLeader().getData() != null
                    && subtype != null
                    && source.getLeader().getData().name().equalsIgnoreCase(subtype);
            // Leader has the required subtype (e.g. "if your Leader has the {Straw Hat} type")
            case IF_LEADER_TYPE -> source.getLeader() != null
                    && source.getLeader().getData() != null
                    && (subtype == null || containsSubtype(source.getLeader().getData().subTypes(), subtype));
            // Leader has the required attribute (e.g. "if your Leader has the (Slash) attribute")
            case IF_LEADER_ATTRIBUTE -> source.getLeader() != null
                    && source.getLeader().getData() != null
                    && (subtype == null || attributeMatches(source.getLeader(), subtype));
            // Any Character on your field has the required subtype
            case IF_FIELD_CHARACTER_TYPE -> subtype == null || source.getField().getCards().stream()
                    .filter(c -> c.getData() != null)
                    .anyMatch(c -> containsSubtype(c.getData().subTypes(), subtype));
            // Any field Character has base power >= conditionValue
            case IF_FIELD_CHARACTER_POWER -> source.getField().getCards().stream()
                    .anyMatch(c -> c.getBasePower() >= conditionValue);
            // Any field Character matches one of the pipe-joined names;
            // if conditionValue > 0, also requires base power >= conditionValue
            case IF_FIELD_CHARACTER_NAME -> {
                String[] names = (subtype != null) ? subtype.split("\\|") : new String[0];
                yield source.getField().getCards().stream()
                        .filter(c -> c.getData() != null)
                        .filter(c -> Arrays.stream(names)
                                .anyMatch(n -> c.getData().name().equalsIgnoreCase(n.trim())))
                        .anyMatch(c -> conditionValue <= 0 || c.getBasePower() >= conditionValue);
            }
            case IF_NOT_FIELD_CHARACTER_NAME -> {
                // "there is a [Name] character" scans both fields — Luffy will be on the opponent's side
                String[] names = (subtype != null) ? subtype.split("\\|") : new String[0];
                Player opp = getOpponent(gs, source);
                yield Stream.concat(
                        source.getField().getCards().stream(),
                        opp.getField().getCards().stream())
                    .filter(c -> c.getData() != null)
                    .noneMatch(c -> Arrays.stream(names)
                            .anyMatch(n -> c.getData().name().equalsIgnoreCase(n.trim())));
            }
            // Any rested field Character matches the given name
            case IF_FIELD_CHARACTER_NAME_RESTED -> subtype != null
                    && source.getField().getCards().stream()
                    .filter(c -> c.getData() != null && c.isRested())
                    .anyMatch(c -> c.getData().name().equalsIgnoreCase(subtype));
            // At least conditionValue Characters on field have cost >= the int in subtype
            case IF_FIELD_CHARACTER_COST_COUNT -> {
                int minCost = 0;
                try { if (subtype != null && !subtype.isBlank()) minCost = Integer.parseInt(subtype.trim()); }
                catch (NumberFormatException ignored) {}
                final int costFloor = minCost;
                long count = source.getField().getCards().stream()
                        .filter(c -> c.getData() != null && c.getEffectiveCost() >= costFloor)
                        .count();
                yield count >= conditionValue;
            }
            // Opponent field conditions
            case IF_OPPONENT_RESTED_CHARACTER_COUNT -> {
                Player opp = getOpponent(gs, source);
                long rested = opp.getField().getCards().stream().filter(Card::isRested).count();
                yield meetsThreshold((int) rested, conditionValue);
            }
            case IF_OPPONENT_FIELD_CHARACTER_POWER ->
                getOpponent(gs, source).getField().getCards().stream()
                        .anyMatch(c -> c.getBasePower() >= conditionValue);
            case IF_OPPONENT_CHARACTER_COUNT ->
                meetsThreshold(getOpponent(gs, source).getField().size(), conditionValue);
            case IF_OPPONENT_DON_COUNT ->
                meetsThreshold(getOpponent(gs, source).getCost().size(), conditionValue);
            case IF_OPPONENT_FIELD_CHARACTER_COST ->
                getOpponent(gs, source).getField().getCards().stream()
                        .anyMatch(c -> c.getData() != null && meetsThreshold(c.getEffectiveCost(), conditionValue));
            case IF_OPPONENT_ATTRIBUTE -> {
                Player opp = getOpponent(gs, source);
                yield opp.getLeader() != null
                        && opp.getLeader().getData() != null
                        && attributeMatches(opp.getLeader(), subtype);
            }
            // Self rested-character count
            case IF_RESTED_CHARACTER_COUNT -> {
                long rested = source.getField().getCards().stream().filter(Card::isRested).count();
                yield meetsThreshold((int) rested, conditionValue);
            }
        };
    }

    /** True if the space-separated subTypes string contains the given token (case-insensitive). */
    private static boolean containsSubtype(String subTypes, String subtype) {
        if (subTypes == null || subTypes.isBlank() || subtype == null) return false;
        String target = subtype.toLowerCase();
        for (String token : subTypes.toLowerCase().split(" ")) {
            if (token.equals(target)) return true;
        }
        return false;
    }

    /** True if the card's attribute enum name matches the given string (case-insensitive). */
    private static boolean attributeMatches(Card card, String subtype) {
        if (card.getData() == null || card.getData().attribute() == null || subtype == null) return false;
        return card.getData().attribute().name().equalsIgnoreCase(subtype);
    }

    // ── Target resolution ───────────────────────────────────────────────────────

    /**
     * Returns the list of cards this effect will act on.
     * Selection is automatic (first N matching cards). Interactive player choices
     * are handled by CliController, not here.
     */
    private static List<Card> resolveTargets(EffectData e, GameState gs,
                                              Player source, Card sourceCard) {
        Target t = e.target();
        if (t == null) return Collections.emptyList();

        // THIS_CARD — the card that owns this ability
        if (t.player() == Target.TargetPlayer.THIS_CARD) {
            return (sourceCard != null) ? List.of(sourceCard) : Collections.emptyList();
        }

        Player opponent = getOpponent(gs, source);
        List<Card> pool = new ArrayList<>();

        switch (t.player()) {
            case SELF     -> addZoneCards(pool, source,   t.zone());
            case OPPONENT -> addZoneCards(pool, opponent, t.zone());
            case ALL      -> {
                addZoneCards(pool, source,   t.zone());
                addZoneCards(pool, opponent, t.zone());
            }
            default -> {}
        }

        // Include Leaders when explicitly requested or when zone+cardType are both null
        // (which the compiler emits for "Leader or Character" phrasing).
        // LEADER-only: zone=null + cardType=LEADER (field chars filtered out by matchesFilters).
        // LEADER_OR_CHARACTER: explicit type (test/GameFactory paths).
        // null/null: compiled counter cards — treat as in-play (field + leader).
        boolean includeLeader = t.cardType() == CardType.LEADER_OR_CHARACTER
                || t.cardType() == CardType.LEADER
                || (t.cardType() == null && t.zone() == null);
        if (includeLeader) {
            if (t.player() == Target.TargetPlayer.SELF || t.player() == Target.TargetPlayer.ALL) {
                Card leader = source.getLeader();
                if (leader != null) pool.add(leader);
            }
            if (t.player() == Target.TargetPlayer.OPPONENT || t.player() == Target.TargetPlayer.ALL) {
                Card leader = opponent.getLeader();
                if (leader != null) pool.add(leader);
            }
        }

        List<Card> filtered = pool.stream()
                .filter(c -> matchesFilters(c, t))
                .collect(Collectors.toList());

        // Determine how many to act on: max takes priority, else value; 0 = all
        int limit = t.max() > 0 ? t.max() : (e.value() > 0 ? e.value() : filtered.size());

        // When there are more candidates than the limit, delegate the choice to
        // the TargetSelector (real implementation prompts the player; default
        // auto-picks the first N so tests stay deterministic).
        if (limit > 0 && filtered.size() > limit) {
            TargetSelector selector = gs.getTargetSelector();
            if (selector != null) {
                String prompt = "Select target (" + e.type().name().toLowerCase().replace('_', ' ') + "):";
                return new ArrayList<>(selector.select(filtered, limit, prompt));
            }
        }
        return filtered.subList(0, Math.min(limit, filtered.size()));
    }

    private static void addZoneCards(List<Card> pool, Player p, ZoneType zone) {
        if (p == null) return;
        // null zone means "in play" — default to CHARACTER (field) zone.
        // Specific zones (HAND, DECK, etc.) are always explicit in compiled data.
        ZoneType effectiveZone = (zone != null) ? zone : ZoneType.CHARACTER;
        Zone z = getZone(p, effectiveZone);
        if (z != null) pool.addAll(z.getCards());
    }

    private static Zone getZone(Player p, ZoneType zone) {
        if (zone == null) return null;
        return switch (zone) {
            case HAND      -> p.getHand();
            case CHARACTER -> p.getField();
            case TRASH     -> p.getTrash();
            case LIFE      -> p.getLife();
            case COST      -> p.getCost();
            case STAGE     -> p.getStage();
            case DECK      -> p.getDeck();
            case DON_DECK  -> p.getDonDeck();
        };
    }

    private static boolean matchesFilters(Card card, Target t) {
        // Card-type filter
        if (t.cardType() != null) {
            if (t.cardType() == CardType.LEADER_OR_CHARACTER) {
                // Accept any card whose type is CHARACTER or LEADER; reject DON!! and others.
                if (card instanceof DonCard) return false;
                CardType ct = (card.getData() == null) ? null : card.getData().cardType();
                if (ct != CardType.CHARACTER && ct != CardType.LEADER) return false;
            } else if (card instanceof DonCard) {
                if (t.cardType() != CardType.DON) return false;
            } else {
                CardType ct = (card.getData() == null) ? null : card.getData().cardType();
                if (t.cardType() != ct) return false;
            }
        }
        // Cost bounds (0 = unset)
        if (t.maxCost() > 0 || t.minCost() > 0) {
            Integer cost = (card.getData() == null) ? null : card.getData().cost();
            if (cost == null) return false;
            if (t.maxCost() > 0 && cost > t.maxCost()) return false;
            if (t.minCost() > 0 && cost < t.minCost()) return false;
        }
        // Power bounds (0 = unset)
        if (t.maxPower() > 0 || t.minPower() > 0) {
            int power = card.getTotalPower();
            if (t.maxPower() > 0 && power > t.maxPower()) return false;
            if (t.minPower() > 0 && power < t.minPower()) return false;
        }
        // Subtye filter (e.g. "with the {Straw Hat} type")
        if (t.subtype() != null && !t.subtype().isBlank()) {
            if (card.getData() == null) return false;
            if (!containsSubtype(card.getData().subTypes(), t.subtype())) return false;
        }
        // Name filter (e.g. "named [Luffy]")
        if (t.nameFilter() != null && !t.nameFilter().isBlank()) {
            if (card.getData() == null) return false;
            if (!card.getData().name().toLowerCase().contains(t.nameFilter().toLowerCase())) return false;
        }
        // Attribute filter (e.g. "with the (Slash) attribute")
        if (t.attribute() != null) {
            if (card.getData() == null || card.getData().attribute() != t.attribute()) return false;
        }
        // Color filter (e.g. "black Character", "red Character")
        if (t.colors() != null && !t.colors().isEmpty()) {
            if (card.getData() == null || card.getData().colors() == null) return false;
            for (Color targetColor : t.colors()) {
                if (card.getData().colors().stream()
                        .noneMatch(c -> c == targetColor)) return false;
            }
        }
        return true;
    }

    private static Player getOpponent(GameState gs, Player source) {
        return (gs.getPlayer1() == source) ? gs.getPlayer2() : gs.getPlayer1();
    }

    // ── Compound-action helpers ─────────────────────────────────────────────────

    /** Activate up to {@code value} rested, unattached DON!! in the cost zone. */
    private static void applySetDonActive(Player source, int value) {
        int limit = (value <= 0) ? Integer.MAX_VALUE : value;
        int count = 0;
        for (Card c : source.getCost().getCards()) {
            if (count >= limit) break;
            if (c instanceof DonCard don && don.isRested() && !don.isAttached()) {
                don.activate();
                count++;
            }
        }
    }

    /**
     * Give N DON!! cards from the source's cost zone to the resolved recipient
     * (Leader or Character on field).  The DON state filter (RESTED_DON /
     * ACTIVE_DON) comes from {@code e.target().cardType()}; {@code null} means
     * no state check (any unattached DON qualifies — backward-compatible path).
     */
    private static void applyAttachDon(EffectData e, GameState gs,
                                        Player source, Card sourceCard) {
        Target t = e.target();
        int count = (t != null && t.max() > 0) ? t.max() : Math.max(e.value(), 1);

        // Resolve the recipient card.
        Card recipient;
        if (t != null && t.player() == Target.TargetPlayer.THIS_CARD) {
            recipient = sourceCard;
        } else {
            Player tp = (t != null && t.player() == Target.TargetPlayer.OPPONENT)
                    ? getOpponent(gs, source) : source;
            recipient = tp.getLeader();
            if (recipient == null && !tp.getField().getCards().isEmpty())
                recipient = tp.getField().getCards().get(0);
            if (recipient == null) recipient = sourceCard;
        }
        if (recipient == null) return;

        // DON state filter: null → no state check; RESTED_DON → rested; ACTIVE_DON → active.
        CardType donFilter = (t != null) ? t.cardType() : null;
        boolean checkState = donFilter != null;
        boolean wantActive = donFilter == CardType.ACTIVE_DON;

        int attached = 0;
        for (Card c : new ArrayList<>(source.getCost().getCards())) {
            if (attached >= count) break;
            if (c instanceof DonCard don && !don.isAttached()) {
                boolean stateOk = !checkState || (wantActive ? !don.isRested() : don.isRested());
                if (stateOk) {
                    gs.attachDon(recipient, don);
                    attached++;
                }
            }
        }
    }

    /**
     * Look at the top N cards of the source zone (DECK or LIFE), let the player
     * select up to target.max() matching cards to add to hand, then return the
     * rest to the TOP of the same zone in their original order.
     *
     * Placement of the rest (top vs bottom) is handled by a subsequent
     * MOVE_TO_TOP_DECK or MOVE_TO_BOTTOM_DECK effect in the effect chain.
     */
    private static void applySearchDeck(EffectData e, GameState gs,
                                         Player source, Card sourceCard) {
        Target t = e.target();
        if (t == null) return;

        Player targetPlayer = (t.player() == Target.TargetPlayer.OPPONENT)
                ? getOpponent(gs, source) : source;

        ZoneType sourceZoneType = (t.zone() != null) ? t.zone() : ZoneType.DECK;
        Zone sourceZone = getZone(targetPlayer, sourceZoneType);
        if (sourceZone == null || sourceZone.isEmpty()) return;

        // Snapshot top N cards (index 0 = top of zone)
        List<Card> zoneCards = new ArrayList<>(sourceZone.getCards());
        int lookCount = e.value() > 0 ? Math.min(e.value(), zoneCards.size()) : zoneCards.size();
        List<Card> looked = new ArrayList<>(zoneCards.subList(0, lookCount));

        // Temporarily remove from zone (card.getZone() becomes null)
        for (Card c : looked) {
            sourceZone.remove(c);
        }

        // Selectable = looked cards matching the filter
        List<Card> selectable = looked.stream()
                .filter(c -> matchesFilters(c, t))
                .collect(Collectors.toList());

        // Player selects up to max from selectable
        int selectLimit = t.max() > 0 ? t.max() : selectable.size();
        List<Card> selected;
        if (selectable.isEmpty() || selectLimit <= 0) {
            selected = Collections.emptyList();
        } else if (selectable.size() > selectLimit) {
            TargetSelector selector = gs.getTargetSelector();
            String prompt = "Select card to add to hand (search):";
            selected = selector != null
                    ? new ArrayList<>(selector.select(selectable, selectLimit, prompt))
                    : new ArrayList<>(selectable.subList(0, selectLimit));
        } else {
            selected = new ArrayList<>(selectable);
        }

        // Selected → hand (card.getZone() is null so moveCard skips origin.remove)
        for (Card c : selected) {
            gs.moveCard(c, targetPlayer.getHand());
        }

        // Rest → top of source zone in original order
        // Add in reverse so the first card in the original order ends up on top
        List<Card> rest = new ArrayList<>(looked);
        rest.removeAll(selected);
        Collections.reverse(rest);
        for (Card c : rest) {
            sourceZone.add(c); // addFirst; also calls card.setZone(sourceZone)
        }
    }

    /**
     * Present the player with the MODAL choice branches and execute the selected one.
     */
    private static void applyModal(EffectData e, GameState gs,
                                    Player source, Card sourceCard) {
        List<List<EffectData>> choices = e.choices();
        if (choices == null || choices.isEmpty()) return;
        ModalSelector selector = gs.getModalSelector();
        int idx = (selector != null) ? selector.select(choices, "Choose one:") : 0;
        idx = Math.max(0, Math.min(idx, choices.size() - 1));
        for (EffectData sub : choices.get(idx)) {
            build(sub, sourceCard).apply(gs, source);
        }
    }

    /**
     * Take the top N cards from the source zone (default: DECK) and place them in the Life area.
     * The source zone is read from {@code e.target().zone()}; falls back to DECK if null.
     */
    private static void applyAddLife(EffectData e, GameState gs, Player source) {
        ZoneType sourceZoneType = (e.target() != null && e.target().zone() != null)
                ? e.target().zone() : ZoneType.DECK;
        Zone sourceZone = getZone(source, sourceZoneType);
        if (sourceZone == null) return;
        int count = Math.max(e.value(), 1);
        for (int i = 0; i < count; i++) {
            if (sourceZone.isEmpty()) break;
            Card card = sourceZone.remove();
            if (card != null) gs.addLife(source, card);
        }
    }
    /**
     * Flip N life cards of the target player (toggle face-up ↔ face-down).
     * N = {@code e.value()}, defaulting to 1 if zero.
     * When a {@link TargetSelector} is registered (CLI / interactive), the player
     * picks which cards to flip; otherwise the first N cards in the life zone are
     * flipped automatically (fallback used by tests and AI).
     */
    private static void applyFlipLife(EffectData e, GameState gs, Player source) {
        Target t = e.target();
        Player targetPlayer = (t != null && t.player() == Target.TargetPlayer.OPPONENT)
                ? getOpponent(gs, source) : source;
        List<Card> lifeCards = new ArrayList<>(targetPlayer.getLife().getCards());
        if (lifeCards.isEmpty()) return;

        int count = Math.max(e.value(), 1);

        List<Card> toFlip;
        if (lifeCards.size() > count) {
            TargetSelector selector = gs.getTargetSelector();
            String prompt = "Choose " + count + " life card(s) to flip:";
            toFlip = selector != null
                    ? new ArrayList<>(selector.select(lifeCards, count, prompt))
                    : new ArrayList<>(lifeCards.subList(0, count));
        } else {
            toFlip = lifeCards;
        }

        toFlip.forEach(Card::flip);
    }

    /**
     * Helper for threshold-based conditions: checks whether the count meets the conditionValue threshold.
     * @param count the actual count to compare against the threshold
     * @param threshold the conditionValue from EffectData; negative means "less than or equal to -threshold", positive means "greater than or equal to threshold"
     * @return true if the count meets the threshold condition, false otherwise
     */
    private static boolean meetsThreshold(int count, int threshold){
        return threshold < 0 ? count <= -threshold : count >= threshold;
    }
}
