package engine.cards.effects;

import engine.cards.Card;
import engine.cards.DonCard;
import engine.cards.abilities.Condition;
import engine.cards.keywords.Keyword;
import engine.cards.types.CardType;
import engine.core.GameState;
import engine.player.Player;
import engine.zones.Zone;
import engine.zones.ZoneType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
            if (!evaluateCondition(effectData.condition(), gameState, source, sourceCard)) return;

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
                case POWER_BOOST  -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> c.addPowerBoost(effectData.value()));
                case POWER_REDUCE -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> c.addPowerBoost(-effectData.value()));
                case ATTACH_DON   -> applyAttachDon(effectData, gameState, source, sourceCard);
                case ADD_LIFE     -> applyAddLife(effectData, gameState, source);
                case COST_MODIFY  -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> c.addCostModifier(effectData.value()));
                case SHUFFLE_DECK -> sourceCard.getOwner().getDeck().shuffle();
                case GRANT_KEYWORD -> {
                    if (effectData.keyword() != null) {
                        resolveTargets(effectData, gameState, source, sourceCard)
                                .forEach(c -> c.grantKeyword(effectData.keyword()));
                    }
                }
                case MOVE_TO_TOP_DECK -> resolveTargets(effectData, gameState, source, sourceCard)
                                             .forEach(c -> gameState.moveCard(c, c.getOwner().getDeck()));
                case MOVE_TO_BOTTOM_DECK -> resolveTargets(effectData, gameState, source, sourceCard)
                                             .forEach(c -> gameState.moveCardtoBottom(c, c.getOwner().getDeck()));
                case SEARCH_DECK -> applySearchDeck(effectData, gameState, source, sourceCard);
                case PREVENT_ATTACK -> resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> c.grantKeyword(Keyword.CANNOT_ATTACK));
                case PREVENT_BLOCK -> resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> c.grantKeyword(Keyword.UNBLOCKABLE));
                case PREVENT_KO -> resolveTargets(effectData, gameState, source, sourceCard).forEach(c -> c.grantKeyword(Keyword.CANNOT_KO));
                case PLAY_CARD -> resolveTargets(effectData, gameState, source, sourceCard)
                                         .forEach(c -> gameState.playCardFree(source, c));
                case MODAL -> applyModal(effectData, gameState, source, sourceCard);
                // Phase-2 stubs — no-op until implemented
                case NEGATE, FLIP_LIFE -> {}
            }
        };
    }

    // ── Condition gate ──────────────────────────────────────────────────────────

    private static boolean evaluateCondition(Condition condition, GameState gs,
                                              Player source, Card sourceCard) {
        if (condition == null) return true;
        return switch (condition) {
            // Enforced externally by TurnManager / Ability.canActivate()
            case YOUR_TURN, OPPONENTS_TURN, ONCE_PER_TURN -> true;
            // Permissive stub: caller's chain guarantees the prior effect resolved
            case IF_YOU_DO -> true;
            // Precise: check whether the source card is currently rested
            case IF_RESTED -> sourceCard != null && sourceCard.isRested();
            // Threshold stubs — no conditionValue in EffectData yet (Phase 2)
            case IF_LIFE_COUNT, IF_HAND_COUNT, IF_DON_COUNT, IF_CHARACTER_COUNT -> true;
            // Type / colour stubs — require Leader subtype metadata (Phase 2)
            case IF_LEADER_TYPE, IF_LEADER_COLOR -> true;
        };
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

        // LEADER_OR_CHARACTER: also include the relevant Leader(s) since Leaders
        // live outside the CHARACTER zone and are not returned by addZoneCards.
        if (t.cardType() == CardType.LEADER_OR_CHARACTER) {
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
        if (zone == null || p == null) return;
        Zone z = getZone(p, zone);
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
     * Draw the top N cards from the player's deck and place them in the Life area.
     */
    private static void applyAddLife(EffectData e, GameState gs, Player source) {
        int count = Math.max(e.value(), 1);
        for (int i = 0; i < count; i++) {
            if (source.getDeck().getCards().isEmpty()) break;
            Card card = source.getDeck().draw();
            if (card != null) gs.addLife(source, card);
        }
    }
}
