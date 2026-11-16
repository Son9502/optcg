# Phase 3 Roadmap — Engine Completion

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
| 7 | `NO_ABILITIES` parser gap | resolved | Re-verified 2026-09-14 by regenerating `compiled/` from `raw/` and re-running `CardCompatibilityReport`: 0 `NO_ABILITIES` rows in the current baseline (was 107 at end of Phase 2). No further work needed here. |
| 8 | CardCompiler compound cost pattern extraction | pending | `EffectParser` does not yet recognize all compound cost patterns from card text. Run `CardCompatibilityReport` after each compiler pass to track progress. |
| 9 | `GameFactory` real-card decks | pending | Replace the 12 synthetic dummy decks with real compiled cards from `CardDatabase`, selected using `CardCompatibilityReport` to ensure fully-playable picks. |

## Note on GUI

*(2026-11-15: the GUI is now **v2 — Play in the browser**, a web client instead of JavaFX; Phase 3's engine items are now scoped to the v1 starter decks — see MVP.md item 3.)* JavaFX GUI moved to **Phase 5** (Battle Mode). An existing OPTCG sim already covers the "play a game visually" use case; the engine + training work in Phases 3–4 are more valuable first. The IPC socket layer from Phase 2.5 makes the GUI swap trivial when Phase 5 arrives — it's a renderer drop-in, not a re-architecture.

---

## CardCompatibilityReport baseline (re-verified 2026-11-15, after OP15–OP17/EB05 rescrape)
- Total cards: 2851 | Fully playable: 2480 (87.0%)
- UNIMPLEMENTED_TRIGGER: 366 (MAIN×298, OPPONENTS_ATTACK×56, ON_BLOCK×12)
- UNIMPLEMENTED_COST: 0 (LIFE split into LIFE_TO_HAND / LIFE_TO_TRASH — resolved)
- COMPOUND_COST: 6 cards
- NO_ABILITIES: 0 cards (was 107 at end of Phase 2 — resolved since, see item 7 above)

Note: the `compiled/` directory is gitignored (regenerated locally from `raw/` via `CardCompiler`, never committed). The 1903/82% figure recorded in `DEVLOG.md` at Phase 2 completion is a legitimate historical snapshot from that session, but the local `compiled/` cache in this checkout had gone stale since (found still emitting the pre-Item-28 singular `"cost"` field instead of `"costs"`, causing `CardDatabase` to silently skip most cards on load). Regenerating via `CardCompiler` before reporting fixed it — the numbers above are from the fresh run. If a future session's `CardCompatibilityReport` output looks implausibly low (e.g. only a few hundred cards analyzed), regenerate `compiled/` first before trusting the number.
