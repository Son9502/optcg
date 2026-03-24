# Phase 3 Roadmap — Engine Completion + GUI

## Tier 1 — Remaining Engine (Blocks Correct Gameplay)

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | `MAIN` trigger dispatch (231 cards) | pending | Event `[Main]` effect cards — largest single unlock. `CliController.runMainPhases()` needs a "Play Event" path that calls `am.dispatch(Trigger.MAIN, card, player)` after moving card to trash. |
| 2 | `OPPONENTS_ATTACK` trigger dispatch (42 cards) | pending | `[On Your Opponent's Attack]` fires after opponent's `WHEN_ATTACKING` resolves. Add `am.dispatchAll(Trigger.OPPONENTS_ATTACK, defender, defender)` in `BattleSystem` after attacker's WHEN_ATTACKING dispatch. |
| 3 | `ON_BLOCK` trigger dispatch (11 cards) | pending | `[On Block]` fires when player declares a Blocker. Hook in `BattleSystem` after blocker is declared, before battle resolves. |
| 4 | `WHILE_IN_PLAY` recalculate-on-query layer | pending | `getPowerWithEffects()` should scan all field cards for active continuous-effect abilities on each call instead of reading stale `bonusPowerPermanent`. Once this is done, items 4+5 from Future Rescoping collapse into one fix. |
| 5 | Replacement effects ("if this would be K.O.'d… instead") | pending | Needs an interrupt hook in `BattleSystem` and `GameState` that queries all field cards for active replacement-effect abilities before applying the action. |

## Tier 2 — Quality / Correctness

| # | Item | Status | Notes |
|---|------|--------|-------|
| 6 | Passive-global NEGATE (Teach OP09-081, Roger OP13-064) | pending | Negate all `[On Play]` effects globally. Requires a permanent-effect layer checked at dispatch time in `AbilityManager.dispatch()`. |
| 7 | `NO_ABILITIES` 107 cards — parser gap | pending | Cards with keyword-only or counter-only text produce no `AbilityData`. Audit via `CardCompatibilityReport` CSV to determine how many genuinely need parser work vs. are correctly ability-less. |
| 8 | CardCompiler compound cost pattern extraction | pending | `EffectParser` does not yet recognize all compound cost patterns from card text. Run `CardCompatibilityReport` after each compiler pass to track progress. |
| 9 | `GameFactory` real-card decks | pending | Replace the 12 synthetic dummy decks with real compiled cards from `CardDatabase`, selected using `CardCompatibilityReport` to ensure fully-playable picks. |

## Note on GUI

JavaFX GUI moved to **Phase 5** (Battle Mode). An existing OPTCG sim already covers the "play a game visually" use case; the engine + training work in Phases 3–4 are more valuable first. The IPC socket layer from Phase 2.5 makes the GUI swap trivial when Phase 5 arrives — it's a renderer drop-in, not a re-architecture.

---

## CardCompatibilityReport baseline (end of Phase 2)
- Total cards: 2322 | Fully playable: 1903 (82%)
- UNIMPLEMENTED_TRIGGER: 286 (MAIN×231, OPPONENTS_ATTACK×42, ON_BLOCK×11, and 2 others)
- UNIMPLEMENTED_COST: 0 (LIFE split into LIFE_TO_HAND / LIFE_TO_TRASH — resolved)
- COMPOUND_COST: 4 cards
- NO_ABILITIES: 107 cards
