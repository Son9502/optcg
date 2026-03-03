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
| 5 | Life card [Trigger] effect | pending | BattleSystem takes life but doesn't fire Trigger.TRIGGER |
| 6 | `SHUFFLE_DECK` effect | pending | Trivial; needed by SEARCH_DECK |
| 7 | `GRANT_KEYWORD` effect | pending | Runtime keyword grant (e.g. gains [Rush] until end of turn) |
| 8 | `SEARCH_DECK` effect | pending | Look top N, choose, rest to bottom |
| 9 | `MOVE_TO_TOP_DECK` / `MOVE_TO_BOTTOM_DECK` | pending | Used by search effects |
| 10 | `PREVENT_ATTACK` / `PREVENT_KO` | pending | Flag on Card; checked in BattleSystem |
| 11 | `PLAY_CARD` effect | pending | Play a card from hand for free |

## Tier 3 — Quality / Correctness

| # | Item | Status | Notes |
|---|------|--------|-------|
| 12 | `MODAL` choices in CliController | pending | Present branches, fire chosen one |
| 13 | Multi-color card support | done | `CardData.color` → `List<Color> colors`; `ColorDeserializer` returns all tokens; `colorize()`/`boldColorize()` alternate ANSI codes per character; no MULTI_COLOR enum value needed |
| 14 | `IF_LEADER_COLOR` condition (real check) | pending | `CardData.colors()` now available; condition evaluation still always returns true |
| 15 | `IF_LEADER_TYPE` condition | pending | Requires leader subtype metadata on CardData |

## CliController — completed display work
- All `System.out` removed from `GameEngine`; moved to dedicated announcement methods (`announcePhase`, `announceDrawPhaseResult`, `announceDonPhaseResult`, `announceAttack`, `announceBlocker`, `announceCounter`, `announceBattleResult`, `announceCardPlayed`, `announceGameOver`)
- `cardAbilityLine(Card)`: single-line ability description shown under each card in hand, field, and card-played announcements — uses `CardData.description()` first, falls back to formatted keyword list
- `formatKeyword(Keyword)`: converts enum to bracket tag (e.g. `DOUBLE_ATTACK` → `[Double Attack]`)
- `ansiCode(Color)`: returns raw ANSI escape string; used by multi-color overloads
- "Use Leader's Ability" menu option stubbed and commented out — uncomment when `ACTIVATED_MAIN` trigger is wired
- `GameFactory` named ability cards: Nami (ON_PLAY draw), Sanji (WHEN_ATTACKING boost), Coby (ON_KO draw), Marco (END_OF_YOUR_TURN rest opponent), Vivi (TRIGGER stub), Perona (GRANT_KEYWORD stub), Kuzan (MODAL stub)

## AbilityManager — trigger dispatch points
- `ON_PLAY` → `GameState.playCard()` after card enters zone
- `WHEN_ATTACKING` → `BattleSystem` after attacker is declared
- `ON_KO` → `GameState.trash()` when source was on field (CHARACTER zone)
- `END_OF_YOUR_TURN` / `END_OF_OPPONENTS_TURN` → `TurnManager.endTurn()` / `advancePhase(END)`
- `TRIGGER` → `BattleSystem.takeLifeDamage()` when a life card is revealed
- `ACTIVATED_MAIN` → player activates manually in CliController (spends DON cost first)
- `OPPONENTS_ATTACK` → after opponent's WHEN_ATTACKING fires
