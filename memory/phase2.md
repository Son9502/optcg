# Phase 2 Roadmap

## Tier 1 — Blocks a Playable Game

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | `payCost()` + use `getEffectiveCost()` in `playCard()` | done | Was already present; fixed to respect COST_MODIFY |
| 2 | `AbilityManager` — trigger dispatch system | done | ON_PLAY, WHEN_ATTACKING, ON_KO, END_OF_YOUR/OPPONENTS_TURN; once-per-turn gate; DON!! xX |
| 3 | Interactive targeting in CliController | done | TargetSelector callback on GameState; CliController wires selectTargets() |

## Tier 2 — Blocks Correct Gameplay

| # | Item | Status | Notes |
|---|------|--------|-------|
| 4 | `conditionValue` in EffectData + parser extraction | done | All threshold conditions wired; `meetsThreshold()` helper (negative=≤, positive=≥); parser extracts numeric thresholds |
| 5 | Life card [Trigger] effect | done | `BattleSystem.resolve()` returns `List<Card>` triggers; `resolveTrigger(Player, Card)` dispatches + trashes; `CliController.trigger()` offers player choice; BANISH path skips trigger (card → trash); 228 tests green |
| 6 | `SHUFFLE_DECK` effect | done | `Deck.shuffle()` added; wired in `EffectFactory` as `sourceCard.getOwner().getDeck().shuffle()` |
| 7 | `GRANT_KEYWORD` effect | done | `Card.grantKeyword(Keyword)`, `clearGrantedKeywords()`; `hasKeyword()` checks abilities list then grantedKeywords; `refreshField()` clears each turn; 232 tests green |
| 8 | `SEARCH_DECK` effect | done | look top N → TargetSelector picks M → hand; rest returned to top; chained MOVE_TO_BOTTOM_DECK handles placement; LIFE zone supported via target.zone(); 240 tests green |
| 9 | `MOVE_TO_TOP_DECK` / `MOVE_TO_BOTTOM_DECK` | done | resolveTargets → moveCard/moveCardtoBottom to c.getOwner().getDeck(); LIFE-destination deferred with MODAL |
| 10 | `PREVENT_ATTACK` / `PREVENT_KO` | done | Implemented as temporary keywords (CANNOT_ATTACK, CANNOT_KO, CANNOT_BLOCK + UNBLOCKABLE); enforced in BattleSystem.canAttack() and resolve(); cleared by refreshField(); 246 tests green |
| 11 | `PLAY_CARD` effect | done | `GameState.playCardFree(Player, Card)` — zone routing + ON_PLAY, no cost; `applyPlayCard` via `resolveTargets` → `playCardFree`; source zone from `target.zone()` (HAND or TRASH); 257 tests green |
| 16 | `FLIP_LIFE` effect | done | `applyFlipLife()` in EffectFactory; toggles via `Card.flip()`; TargetSelector for interactive pick (face-down cards shown as [???] in CLI); fallback = top N; 289 tests green |
| 18 | `COUNTER` trigger on event cards | done | `am.dispatch(Trigger.COUNTER, counterCard, defender)` in `applyCounter()`; null zone defaults to CHARACTER in `resolveTargets`; LEADER and null/null targets include leader; `getPowerWithEffects()` on `Card` for defender power (base + bonusPower + bonusBattlePower, no DON!!); KO'd cards cleared explicitly in `resolve()` before field sweep; 298 tests green |
| 19 | `NEGATE` effect | done | `EffectFactory` line 127: `resolveTargets → c.getAbilities().forEach(a -> a.setNegated(true))`; `AbilityManager.dispatch()` already skips negated abilities; `Ability.resetTurn()` clears `isNegated`; 5 tests (targeted card, scoping, dispatch integration, multi-card, reset); 361 tests green. Passive-global variants (Teach OP09-081, Roger OP13-064) deferred to Future Rescoping. |

## Tier 3 — Quality / Correctness

| # | Item | Status | Notes |
|---|------|--------|-------|
| 12 | `MODAL` choices in CliController | done | `ModalSelector` interface; `GameState.setModalSelector()`; `applyModal()` in EffectFactory executes chosen branch; default picks idx 0; 263 tests green |
| 13 | Multi-color card support | done | `CardData.color` → `List<Color> colors`; `ColorDeserializer` returns all tokens; `colorize()`/`boldColorize()` alternate ANSI codes per character; no MULTI_COLOR enum value needed |
| 14 | `IF_LEADER_COLOR` + `IF_LEADER_NAME` conditions | done | `IF_LEADER_COLOR` → `colors().size() > 1`; `IF_LEADER_NAME` → `leader.getData().name().equalsIgnoreCase(subtype)`; `[Name]` bracket in EffectLexer now emits `NAME_FILTER` (distinct from `SUBTYPE_FILTER` / `ATTRIBUTE_FILTER`); `conditionSubtype` field added to `ParsedEffect`; `EffectParser.classifyConditionClause()` populates it; `CardCompiler` writes it as `"subtype"` in JSON; 300 tests green |
| 15 | `IF_LEADER_TYPE` + `IF_FIELD_CHARACTER_TYPE` conditions | done | `containsSubtype(leader.subTypes(), subtype)` for `IF_LEADER_TYPE`; field-card scan for `IF_FIELD_CHARACTER_TYPE`; subtype extracted from `SUBTYPE_FILTER` token in condition window; 300 tests green |
| 17 | Attribute filtering in `Target` + `IF_LEADER_ATTRIBUTE` condition | done | `IF_LEADER_ATTRIBUTE` in `evaluateCondition()`; `attributeMatches()` checks `CardData.attribute().name()`; `Target` has `String attribute` field; `matchesFilters()` guards with `!= null && !isBlank()`; blank/"" treated as no-filter |
| 22 | New field-character & opponent conditions | done | `IF_FIELD_CHARACTER_POWER`, `IF_FIELD_CHARACTER_NAME` (subtype=pipe-joined names, conditionValue=minBasePower), `IF_FIELD_CHARACTER_NAME_RESTED` (subtype=name), `IF_FIELD_CHARACTER_COST_COUNT` (conditionValue=minCount, subtype=minCost string), `IF_OPPONENT_RESTED_CHARACTER_COUNT`, `IF_OPPONENT_FIELD_CHARACTER_POWER`, `IF_OPPONENT_DON_COUNT`, `IF_RESTED_CHARACTER_COUNT`. Parser extraction still pending. |
| 23 | `IF_LEADER_COLOR` → `IF_LEADER_MULTICOLORED` rename | done | Renamed enum value + switch case + EffectParser string + all compiled JSON; `SHUFFLE_DECK` fixed to use `source.getDeck()` instead of `sourceCard.getOwner().getDeck()` |
| 24 | `Target` color filter | done | 12th field `String color` added to `Target` record; `matchesFilters()` checks against `card.getData().colors()` (case-insensitive); all callsites updated |
| 20 | Duration correctness across all effect types | done | All four `Duration` values must route to the right bucket in `EffectFactory` and clear at the right time. **THIS_BATTLE** → `bonusBattlePower` (already wired ✓). **THIS_TURN** → `bonusPower` / `bonusCost`, cleared by `refreshField()` (already wired ✓). **OPPONENTS_NEXT_TURN** → new `bonusPowerOpponentTurn` / `bonusCostOpponentTurn` buckets on `Card`; included in `getPowerWithEffects()` / `getEffectiveCost()`; `TurnManager.endTurn()` clears them on the non-active player's cards at end of each turn. **PERMANENT** → new `bonusPowerPermanent` / `bonusCostPermanent` buckets on `Card`; never cleared by `refreshField()` or `TurnManager`; correct for one-shot permanent grants ("this Character gains +1000 power" with no qualifier). `GRANT_KEYWORD` with PERMANENT adds a `KEYWORD` `Ability` object to `card.abilities` instead of `grantedKeywords`. Note: source-dependent continuous effects ("while X is in play, all Characters gain +N") still need the Future Rescoping layer system — PERMANENT bucket only handles one-shot grants. |
| 27 | `OPPONENTS_TURN` trigger dispatch | done | `TurnManager.advancePhase(DON)` calls `am.dispatchAll(OPPONENTS_TURN, opponent, opponent)` — fires for non-active player's field + leader, with opponent as actingPlayer so DRAW/POWER_BOOST effects resolve from card owner's perspective; bug found+fixed (was dispatching for activePlayer's cards); 4 tests in `TurnManagerTest`; 365 tests green. Replacement effects deferred to Future Rescoping. |
| 26 | `ACTIVATED_MAIN` abilities in `CliController` | done | `GameState.canAffordActivation(Player, Cost, Card)` + `payActivationCost(Player, Cost, Card)` for all 9 `CostType` values; `CliController.activateAbility(Player)` with flat `ActivatablePair` list, `collectActivatable()`, `formatActivationCost()`, `announceActivation()`; `runMainPhases()` fixed (duplicate `case 7` removed, End Phase wired to `case 8`, `readInt` range corrected); 4 tests in `AbilityManagerTest`; 359 tests green. Compound costs (~25 cards) deferred to Item 28. |
| 28 | Compound activation costs | done | `AbilityData` migrated to `List<Cost> costs`; `canAffordActivation(Player, List<Cost>, Card)` checks each independently; `payActivationCost` iterates atomically (blocked by `canAffordActivation` guard); `REST_NAMED`, `REST_CHARACTER`, `REST_ANY` added to all exhaustive switches (`GameState`, `CliController`); `CostType.REST` removed (duplicate of `REST_SELF`); 5 compound tests in `AbilityManagerTest`; 370 tests green. CardCompiler `costs` array emission + parser compound cost extraction still pending (deferred to Item 29 / tools pipeline work). |
| 25 | 12 selectable dummy decks in `GameFactory` | done | 6 single-color + 6 dual-color decks; `DECK_NAMES[12]`, `createGame(int,int)`, `equip(Player,int)`; each 50-card deck = 31 generic base + 11 ability chars + 5 events + 3 stages; `Main.java` shows numbered menu, reads P1/P2 choice, calls `createGame(p1-1, p2-1)`; 349 tests green |
| 21 | Subtype + name filtering in `Target` + `matchesFilters()` | done | `Target` has `String subtype`, `String nameFilter`, `String attribute` (11-arg record); `matchesFilters()` checks all three with `!= null && !isBlank()` guards; blank/"" treated as no-filter so old test callsites using `""` pass through correctly |
| 29 | `CardCompatibilityReport` tool | done | `src/main/java/tools/CardCompatibilityReport.java`; flags: UNIMPLEMENTED_TRIGGER, UNIMPLEMENTED_EFFECT, UNIMPLEMENTED_CONDITION, UNIMPLEMENTED_COST, COMPOUND_COST, WHILE_IN_PLAY_DURATION, NO_ABILITIES; outputs console + `csv/card_compatibility_report.csv`. Baseline: 2322 cards, 1903 fully playable (82%). `LIFE` split into `LIFE_TO_HAND` / `LIFE_TO_TRASH` (`CostType`) — all 30 previously flagged cards resolved. |

## CliController — completed display work
- All `System.out` removed from `GameEngine`; moved to dedicated announcement methods (`announcePhase`, `announceDrawPhaseResult`, `announceDonPhaseResult`, `announceAttack`, `announceBlocker`, `announceCounter`, `announceBattleResult`, `announceCardPlayed`, `announceGameOver`)
- `cardAbilityLine(Card)`: single-line ability description shown under each card in hand, field, and card-played announcements — uses `CardData.description()` first, falls back to formatted keyword list
- `formatKeyword(Keyword)`: converts enum to bracket tag (e.g. `DOUBLE_ATTACK` → `[Double Attack]`)
- `ansiCode(Color)`: returns raw ANSI escape string; used by multi-color overloads
- "Use Leader's Ability" menu option stubbed and commented out — uncomment when `ACTIVATED_MAIN` trigger is wired
- `GameFactory` named ability cards: Nami (ON_PLAY draw), Sanji (WHEN_ATTACKING boost), Coby (ON_KO draw), Marco (END_OF_YOUR_TURN rest opponent), Vivi (TRIGGER — done), Perona (GRANT_KEYWORD Blocker until end of turn — done), Kuzan (MODAL stub)

## Phase 2 complete — 370 tests green, 1903/2322 cards fully playable

Deferred engine work moved to `memory/phase3.md`. Next phase: `memory/phase2.5.md` (Tmux UI + IPC layer).

---

## AbilityManager — trigger dispatch points
- `ON_PLAY` → `GameState.playCard()` after card enters zone
- `WHEN_ATTACKING` → `BattleSystem` after attacker is declared
- `ON_KO` → `GameState.trash()` when source was on field (CHARACTER zone)
- `END_OF_YOUR_TURN` / `END_OF_OPPONENTS_TURN` → `TurnManager.endTurn()` / `advancePhase(END)`
- `TRIGGER` → `BattleSystem.resolveTrigger(Player, Card)` — called by `CliController.trigger()` after player confirms; card trashed post-effect
- `ACTIVATED_MAIN` → player activates manually in CliController (spends DON cost first)
- `OPPONENTS_ATTACK` → after opponent's WHEN_ATTACKING fires
