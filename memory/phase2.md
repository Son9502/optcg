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
| 16 | `FLIP_LIFE` effect | pending | Flip N life cards face-up (Charlotte Pudding mechanic); requires `faceUp` state on life-zone cards |

## Tier 3 — Quality / Correctness

| # | Item | Status | Notes |
|---|------|--------|-------|
| 12 | `MODAL` choices in CliController | done | `ModalSelector` interface; `GameState.setModalSelector()`; `applyModal()` in EffectFactory executes chosen branch; default picks idx 0; 263 tests green |
| 13 | Multi-color card support | done | `CardData.color` → `List<Color> colors`; `ColorDeserializer` returns all tokens; `colorize()`/`boldColorize()` alternate ANSI codes per character; no MULTI_COLOR enum value needed |
| 14 | `IF_LEADER_COLOR` condition (real check) | pending | `CardData.colors()` now available; condition evaluation still always returns true |
| 15 | `IF_LEADER_TYPE` condition | pending | Requires leader subtype metadata on CardData |
| 17 | Attribute filtering in `Target` + `IF_LEADER_ATTRIBUTE` condition | pending | `Attribute` enum + `CardData.attribute` already exist; `Target` needs `attribute` field; `matchesFilters()` needs attribute check; `IF_LEADER_ATTRIBUTE` in `evaluateCondition()`; every `new Target(...)` call needs a 9th arg |

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
