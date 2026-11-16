# Architecture

This document describes how `optcg` is put together: the two subsystems in the repo, how a card goes from scraped text to a runtime object, how a turn actually executes, and the invariants that keep those pieces from drifting apart. For feature status and what's left to build, see [README.md](README.md)'s Roadmap and `memory/phase3.md`; for what "minimum shippable product" concretely means, see [MVP.md](MVP.md) — this file is about structure, not progress or scope.

---

## Two subsystems, one boundary

The repo is really two programs that share a data format:

```
tools/   offline pipeline   raw card text  ──►  compiled JSON   (run by hand, ahead of time)
engine/  game runtime       compiled JSON  ──►  a playable match (run every time someone plays)
```

Nothing in `engine/` ever calls into `tools/`, and nothing in `tools/` depends on game state. The only contract between them is the compiled JSON file shape that `CardCompiler` writes and `CardDatabase` reads. This separation is why `tools/` is allowed to be messy/iterative (CSV reports, coverage stats, manual-override lists) without risking the runtime, and why the runtime never pays a text-parsing cost during a game.

---

## The offline pipeline (`src/main/java/tools/`)

```
CardScraper
   │  hits the OPTCG API, writes raw JSON → src/main/resources/raw/data/{sets,decks,promos}/
   ▼
CardCompiler
   │  for each raw card: strips scrape-only fields, dedupes by card_set_id,
   │  calls Parser.parse(card_text) to get ability records, writes:
   │  compiled JSON → src/main/resources/compiled/data/{sets,decks,promos}/   (gitignored — regenerate locally, don't expect it to be committed)
   │  csv/card_checklist.csv (via CardChecklistCsvGenerator)
   ▼
EffectLexer → EffectParser → EffectTables
   │  word-level tokenizer + grammar used inside Parser/CardCompiler to turn
   │  ability text into {trigger, condition, cost(s), character, effect} records
   ▼
CardCompatibilityReport / LexerCoverageReport / CsvGenerator
      offline-only analysis tools — read compiled JSON or CSV templates,
      write reports to csv/. Never imported by engine/.
```

Key point: `CardCompiler` is the only place that writes `compiled/`. If `CardCompatibilityReport` or `CardDatabase` ever report numbers that look implausibly low, the fix is almost always "re-run `CardCompiler`," not "the engine regressed" — `compiled/` is a local build artifact (gitignored), and it can quietly go stale relative to the current `AbilityData`/`EffectData` schema if raw data changes or the compiler's output shape changes but the directory isn't regenerated. See the note at the bottom of `memory/phase3.md` for a concrete instance of this.

`Parser` (top-level, not in `tools/`'s sub-namespace despite living in `tools/`) is the bridge: it's what `CardCompiler` calls per-card, and it's also unit-tested directly (`ParserTest`) independent of the file-writing side of the pipeline.

---

## The runtime (`src/main/java/engine/`)

### Orchestration

```
Main.java
   └─► GameEngine
         ├─ GameSetup        — one-time: shuffle, draw opening hands, mulligan, set starting life
         ├─ TurnManager       — owns Phase sequencing (REFRESH → DRAW → DON → MAIN → END) and turn count
         ├─ AbilityManager    — dispatch(trigger, card, actingPlayer) / dispatchAll(...)
         ├─ CliController     — the only thing that talks to System.in/out
         ├─ GameHistoryManager — snapshots state after setup and after each recorded point (replay/undo chain)
         └─ GameState         — single source of truth; everything above reads/mutates through it
```

`GameEngine.run()` drives the loop: it asks `TurnManager` what phase it is, and only hands control to `CliController` when `Phase.isInteractive()` is true (MAIN). REFRESH/DRAW/DON auto-advance and are merely announced after the fact (`announcePhaseResult`) — there's no interactive step for them.

### Where logic is allowed to live

| Concern | Owner | Notes |
|---|---|---|
| Combat resolution | `battle/BattleSystem` | attacker/defender validity, blocker/counter windows, power math, K.O., trigger reveal |
| All state mutation | `core/GameState` | draw, trash, play, attach DON, pay costs — every zone move funnels through here |
| Phase/turn sequencing | `core/TurnManager` | advances `Phase`, fires `OPPONENTS_TURN` dispatch at the DON→MAIN boundary |
| Ability dispatch | `core/AbilityManager` | looks up a card's `Ability` objects, skips negated ones, calls into `EffectFactory` |
| Effect execution | `cards/effects/EffectFactory` | the `switch` over `EffectType` that actually does e.g. `DRAW`, `KO`, `GRANT_KEYWORD` |
| Human interaction | `ui/cli/CliController` + `InputHandler` | menus, prompts, announcements — **no rules logic**, only calls into `GameState`/`BattleSystem` |

`CliController` is UI-only by convention, not by compiler enforcement — if you find rules logic growing there (anything that decides a *legal* outcome rather than *displaying* one), it belongs in `BattleSystem` or `GameState` instead.

### Card data lifecycle (compiled JSON → object you can call `.attack()` on)

```
compiled JSON (on disk)
   │  CardDatabase.loadAllCards() — Jackson deserializes into CardData records,
   │  indexed by card_set_id (primary) and lowercase name (secondary, getByName/getBySubtype)
   ▼
CardData  (immutable record — the "template": base power/cost/text/abilities[])
   │  CardFactory.createCard(CardData, Player owner)
   ▼
Card / Leader / DonCard  (the runtime, mutable, owned-by-a-player instance)
   │  AbilityFactory hydrates each AbilityData in CardData.abilities() into a
   │  real Ability object attached to the Card — this is what AbilityManager.dispatch() iterates
   ▼
played onto a Zone (Deque-backed: add()=addFirst/top, draw()=removeFirst/top, addBottom()=addLast)
```

`GameFactory`/`DeckBuilder` currently build the 12 synthetic single/dual-color test decks directly (not yet sourced from `CardDatabase` — see `memory/phase3.md` item 9), so today's actual games never exercise the `CardDatabase` load path. `CardCompatibilityReport` is the only thing currently exercising full-database loading end-to-end.

---

## Directory layout

Concrete grounding for the two subsystems above — where each class from the diagrams actually lives:

```
src/
├── main/
│   ├── java/
│   │   ├── engine/
│   │   │   ├── Main.java                        ← Entry point
│   │   │   ├── battle/
│   │   │   │   └── BattleSystem.java            ← All combat logic
│   │   │   ├── cards/
│   │   │   │   ├── Card.java
│   │   │   │   ├── CardData.java                ← Immutable record; Jackson-mapped
│   │   │   │   ├── CardFactory.java             ← Creates Leader or Card from CardData
│   │   │   │   ├── ColorDeserializer.java       ← Handles space-separated multi-color API strings
│   │   │   │   ├── DonCard.java
│   │   │   │   ├── Leader.java
│   │   │   │   ├── abilities/                   ← Trigger, Condition, Cost, AbilityData, Ability, AbilityFactory
│   │   │   │   ├── effects/                     ← EffectData, EffectType, Duration, Target, EffectFactory
│   │   │   │   ├── keywords/                    ← Keyword enum (Rush, Blocker, Double Attack, Banish, Unblockable, Trigger, Rush: Character)
│   │   │   │   └── types/                       ← CardType, Color, Attribute, Rarity enums
│   │   │   ├── core/
│   │   │   │   ├── GameEngine.java              ← Main game loop
│   │   │   │   ├── GameState.java               ← All state mutations
│   │   │   │   ├── Phase.java                   ← Turn phase enum
│   │   │   │   └── TurnManager.java             ← Phase transitions, active player
│   │   │   ├── data/
│   │   │   │   ├── CardDatabase.java            ← Primary (id) + secondary (name) indexes; getByName, getBySubtype, getAllCards
│   │   │   │   └── NullableIntDeserializer.java ← Handles "NULL" string / string-encoded ints
│   │   │   ├── history/
│   │   │   │   ├── GameHistory.java
│   │   │   │   └── GameHistoryManager.java      ← Snapshot chain for replay/undo
│   │   │   ├── player/
│   │   │   │   └── Player.java
│   │   │   ├── setup/
│   │   │   │   ├── DeckBuilder.java
│   │   │   │   ├── GameFactory.java             ← Test decks (Luffy vs Zoro) + 12 selectable game decks
│   │   │   │   └── GameSetup.java               ← initializeGame, mulligan, setUpLife
│   │   │   ├── ui/
│   │   │   │   └── cli/
│   │   │   │       ├── CliController.java       ← All player-facing prompts and menus
│   │   │   │       └── InputHandler.java        ← readInt, confirm, readString
│   │   │   └── zones/
│   │   │       ├── Deck.java
│   │   │       ├── DonDeck.java
│   │   │       ├── Zone.java                    ← Deque-backed; add = top, draw = top
│   │   │       └── ZoneType.java
│   │   └── tools/                               ← Offline data pipeline (not part of game runtime)
│   │       ├── CardScraper.java                 ← Fetches raw card data from API
│   │       ├── CardCompiler.java                ← raw JSON → compiled JSON + abilities[]
│   │       ├── CsvGenerator.java                ← Rebuilds card_names_subtypes.csv from compiled/raw data
│   │       ├── Parser.java                      ← Parses card text into structured abilities
│   │       ├── ParsedAbility.java               ← Record: trigger, condition, cost, character, effect
│   │       ├── TokenType.java                   ← 13-value enum (VERB, QUANTITY, PLAYER, CARD_TYPE, FILTER, SUBTYPE_FILTER, NAME_FILTER, ATTRIBUTE_FILTER, ZONE, DURATION, CONDITION, CONNECTOR, MODIFIER)
│   │       ├── Token.java                       ← Record: (TokenType type, String raw, Object value)
│   │       ├── EffectTables.java                ← All phrase→TokenType entries, sorted lists, regex filter Patterns (shared by EffectLexer and EffectParser)
│   │       ├── EffectLexer.java                 ← Word-level scanner; produces List<Token> from raw effect text
│   │       ├── ParsedEffect.java                ← Intermediate structured effect record (tools pipeline only)
│   │       ├── EffectParser.java                ← Token→ParsedEffect parser; resolveVerb, splitClauses, extractPlayer/Zone/CardType/Filters/Duration/Condition
│   │       ├── EffectTemplateCsvGenerator.java  ← Reads compiled JSON → Parser → generalise → split compound effects → write generic/unique CSV pair
│   │       ├── CardChecklistCsvGenerator.java   ← Reads compiled cards → EffectLexer → collects UNKNOWN tokens → writes csv/card_checklist.csv
│   │       ├── LexerCoverageReport.java         ← Runs EffectLexer on all CSV templates; reports fully-tokenised % and top unknown tokens
│   │       ├── Report.java                      ← Shared CSV row record used by LexerCoverageReport
│   │       └── CardCompatibilityReport.java     ← Static check: flags cards using unimplemented mechanics, empty abilities or unknown words; writes csv/card_compatibility_report.csv
│   └── resources/
│       ├── compiled/data/                       ← Engine-ready JSON (CardDatabase reads this)
│       │   ├── sets/                            ← One file per card set (e.g. OP01.json)
│       │   ├── decks/                           ← Pre-constructed deck files
│       │   └── promos/                          ← Promotional card files
│       └── raw/data/                            ← Scraped JSON from API (CardScraper output)
│           ├── sets/
│           ├── decks/
│           └── promos/
└── test/
    └── java/
        ├── engine/
        │   ├── TestUtils.java                   ← Shared makeCardData / makeCard helpers
        │   ├── battle/BattleHandlerTest.java
        │   ├── cards/CardTest.java
        │   ├── core/GameStateTest.java
        │   ├── core/TurnManagerTest.java
        │   ├── data/CardDatabaseTest.java
        │   ├── player/PlayerTest.java
        │   ├── setup/GameSetupTest.java
        │   ├── setup/GameFactoryTest.java
        │   └── zones/DeckTest.java, ZoneTest.java
        └── tools/
            ├── ParserTest.java
            ├── TokenTest.java
            ├── EffectLexerTest.java
            └── EffectParserTest.java
```

---

## Invariants (violate these and something subtle breaks)

- **`BattleSystem` owns all combat logic; `CliController` is presentation only.**
- **`GameState` is the single source of truth.** Every draw/trash/play/attach/cost-payment mutation goes through it — no other class should mutate a `Zone` or `Player` directly.
- **DON attachment source = the cost zone, not hand** — matches `drawDon()` behavior. `DON!! −X` (§8-3-1-6) is a cost that *returns* DON to the DON deck, which is different from `[DON!! xX]` (§8-3-2-3), which is a *condition* (≥X DON attached) with no cost at all. These are easy to conflate; they're separate enum paths (`CostType.DON_MINUS` vs. a `Condition`).
- **Attack targets are opponent's leader + opponent's rested characters only.** Active (non-rested) characters can't be attacked.
- **`Zone` is `Deque`-backed.** `add()` → `addFirst` (top of pile), `draw()` → `removeFirst` (top), `addBottom()` → `addLast`. Anything that needs "put at the bottom of the deck" must use `addBottom`, not `add`.
- **`Duration` values route to separate, independently-cleared buckets on `Card`** (`bonusPower`/`bonusCost` for `THIS_TURN`, `bonusBattlePower` for `THIS_BATTLE`, `bonusPowerOpponentTurn`/`bonusCostOpponentTurn` for `OPPONENTS_NEXT_TURN`, `bonusPowerWhileInPlay`/`bonusCostWhileInPlay` for `WHILE_IN_PLAY`) — never add a temporary boost directly to base `power`/`cost`, or it won't clear at the right time.
- **`tools/` is an offline, run-once-per-data-change pipeline.** It is never imported by `engine/`, and its output directory (`compiled/`) is gitignored — don't assume it reflects the latest `raw/` + parser state without regenerating it.

---

## Testing shape

Tests mirror the package layout under `src/test/java/` (`engine.battle`, `engine.core`, `engine.cards.effects`, `tools`, …) and are unit-level: each test exercises one class's behavior in isolation (battle resolution math, a single `EffectFactory` case, turn-phase transitions). There is currently no test that plays a full game via `CliController`/`GameEngine` end-to-end to a win condition — that's a deliberate gap, not an oversight; see the README's "Honest Status"-equivalent notes before treating unit-green as "the game works."
