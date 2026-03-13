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
| 4 | `conditionValue` in EffectData + parser extraction | pending | IF_LIFE_COUNT etc. always return true currently |
| 5 | Life card [Trigger] effect | done | `BattleSystem.resolve()` returns `List<Card>` triggers; `resolveTrigger(Player, Card)` dispatches + trashes; `CliController.trigger()` offers player choice; BANISH path skips trigger (card → trash); 228 tests green |
| 6 | `SHUFFLE_DECK` effect | done | `Deck.shuffle()` added; wired in `EffectFactory` as `sourceCard.getOwner().getDeck().shuffle()` |
| 7 | `GRANT_KEYWORD` effect | done | `Card.grantKeyword(Keyword)`, `clearGrantedKeywords()`; `hasKeyword()` checks abilities list then grantedKeywords; `refreshField()` clears each turn; 232 tests green |
| 8 | `SEARCH_DECK` effect | done | look top N → TargetSelector picks M → hand; rest returned to top; chained MOVE_TO_BOTTOM_DECK handles placement; LIFE zone supported via target.zone(); 240 tests green |
| 9 | `MOVE_TO_TOP_DECK` / `MOVE_TO_BOTTOM_DECK` | done | resolveTargets → moveCard/moveCardtoBottom to c.getOwner().getDeck(); LIFE-destination deferred with MODAL |
| 10 | `PREVENT_ATTACK` / `PREVENT_KO` | done | Implemented as temporary keywords (CANNOT_ATTACK, CANNOT_KO, CANNOT_BLOCK + UNBLOCKABLE); enforced in BattleSystem.canAttack() and resolve(); cleared by refreshField(); 246 tests green |
| 11 | `PLAY_CARD` effect | done | `GameState.playCardFree(Player, Card)` — zone routing + ON_PLAY, no cost; `applyPlayCard` via `resolveTargets` → `playCardFree`; source zone from `target.zone()` (HAND or TRASH); 257 tests green |
| 16 | `FLIP_LIFE` effect | done | `applyFlipLife()` in EffectFactory; toggles via `Card.flip()`; TargetSelector for interactive pick (face-down cards shown as [???] in CLI); fallback = top N; 289 tests green |
| 18 | `COUNTER` trigger on event cards | done | `am.dispatch(Trigger.COUNTER, counterCard, defender)` in `applyCounter()`; null zone defaults to CHARACTER in `resolveTargets`; LEADER and null/null targets include leader; `getPowerWithEffects()` on `Card` for defender power (base + bonusPower + bonusBattlePower, no DON!!); KO'd cards cleared explicitly in `resolve()` before field sweep; 298 tests green |
| 19 | `NEGATE` effect | pending | Cancels the activation of a card (typically an opponent's Event) mid-resolution; requires a hook in `GameState.playCard()` or a dedicated cancellation stack; the effect targets the card-in-resolution by reference, not a zone; deferred until event-cancellation stack exists |

## Tier 3 — Quality / Correctness

| # | Item | Status | Notes |
|---|------|--------|-------|
| 12 | `MODAL` choices in CliController | done | `ModalSelector` interface; `GameState.setModalSelector()`; `applyModal()` in EffectFactory executes chosen branch; default picks idx 0; 263 tests green |
| 13 | Multi-color card support | done | `CardData.color` → `List<Color> colors`; `ColorDeserializer` returns all tokens; `colorize()`/`boldColorize()` alternate ANSI codes per character; no MULTI_COLOR enum value needed |
| 14 | `IF_LEADER_COLOR` + `IF_LEADER_NAME` conditions | done | `IF_LEADER_COLOR` → `colors().size() > 1`; `IF_LEADER_NAME` → `leader.getData().name().equalsIgnoreCase(subtype)`; `[Name]` bracket in EffectLexer now emits `NAME_FILTER` (distinct from `SUBTYPE_FILTER` / `ATTRIBUTE_FILTER`); `conditionSubtype` field added to `ParsedEffect`; `EffectParser.classifyConditionClause()` populates it; `CardCompiler` writes it as `"subtype"` in JSON; 300 tests green |
| 15 | `IF_LEADER_TYPE` + `IF_FIELD_CHARACTER_TYPE` conditions | done | `containsSubtype(leader.subTypes(), subtype)` for `IF_LEADER_TYPE`; field-card scan for `IF_FIELD_CHARACTER_TYPE`; subtype extracted from `SUBTYPE_FILTER` token in condition window; 300 tests green |
| 17 | Attribute filtering in `Target` + `IF_LEADER_ATTRIBUTE` condition | partial | `IF_LEADER_ATTRIBUTE` in `evaluateCondition()` done — `attributeMatches()` checks `CardData.attribute().name()`; `(Attribute)` bracket now emits `ATTRIBUTE_FILTER`; **`Target` attribute field and `matchesFilters()` attribute check still pending** (every `new Target(...)` call needs a new arg) |
| 20 | Cross-turn effect tracking (`OPPONENTS_NEXT_TURN` duration) | pending | Affects: POWER_BOOST (11), SEARCH_DECK (4), COST_MODIFY (1), NEGATE (1). All currently fall back to turn-scoped behavior (cleared at turn start). Need a separate bucket or expiry-list to persist effects through active player's turn and clear at start of opponent's next turn. |
| 21 | Subtype + name filtering in `Target` + `matchesFilters()` | pending | Many effects filter by subtype (e.g. `{Straw Hat Crew}`) or named card (e.g. `"Luffy"`). Currently ignored — all candidates pass. Requires: (a) `Target` gains `String subtype` and `String nameFilter` fields (every `new Target(...)` call gets two extra args); (b) `matchesFilters()` checks `card.getData().subTypes().contains(subtype)` and `card.getData().name().equals(nameFilter)`; (c) CardCompiler + EffectParser extract `{X}` blocks as subtype and quoted names as nameFilter; (d) reference `csv/card_names_subtypes.csv` for the full name/subtype catalogue. |

## CliController — completed display work
- All `System.out` removed from `GameEngine`; moved to dedicated announcement methods (`announcePhase`, `announceDrawPhaseResult`, `announceDonPhaseResult`, `announceAttack`, `announceBlocker`, `announceCounter`, `announceBattleResult`, `announceCardPlayed`, `announceGameOver`)
- `cardAbilityLine(Card)`: single-line ability description shown under each card in hand, field, and card-played announcements — uses `CardData.description()` first, falls back to formatted keyword list
- `formatKeyword(Keyword)`: converts enum to bracket tag (e.g. `DOUBLE_ATTACK` → `[Double Attack]`)
- `ansiCode(Color)`: returns raw ANSI escape string; used by multi-color overloads
- "Use Leader's Ability" menu option stubbed and commented out — uncomment when `ACTIVATED_MAIN` trigger is wired
- `GameFactory` named ability cards: Nami (ON_PLAY draw), Sanji (WHEN_ATTACKING boost), Coby (ON_KO draw), Marco (END_OF_YOUR_TURN rest opponent), Vivi (TRIGGER — done), Perona (GRANT_KEYWORD Blocker until end of turn — done), Kuzan (MODAL stub)

## AbilityManager — trigger dispatch points
- `ON_PLAY` → `GameState.playCard()` after card enters zone
- `WHEN_ATTACKING` → `BattleSystem` after attacker is declared
- `ON_KO` → `GameState.trash()` when source was on field (CHARACTER zone)
- `END_OF_YOUR_TURN` / `END_OF_OPPONENTS_TURN` → `TurnManager.endTurn()` / `advancePhase(END)`
- `TRIGGER` → `BattleSystem.resolveTrigger(Player, Card)` — called by `CliController.trigger()` after player confirms; card trashed post-effect
- `ACTIVATED_MAIN` → player activates manually in CliController (spends DON cost first)
- `OPPONENTS_ATTACK` → after opponent's WHEN_ATTACKING fires
