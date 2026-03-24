# One Piece Virtual TCG — `optcg`

A Java-based digital engine for the One Piece Card Game (OPTCG). The long-term vision is a full-featured platform where players can battle locally or globally, train against themselves or an AI, observe and replay matches, and run strategic simulations — all built on top of a faithful rules engine.

---

## Current Status — Phase 2 *(complete)*

> **CMU (Command-line Minimum UI)** is the first milestone: a fully playable single game via the terminal with no graphics. Phase 1 and Phase 2 are complete. All 29 Phase 2 items are done — 370 tests green, 1903/2322 cards (82%) fully playable. Phase 2.5 builds the Tmux UI and the local socket IPC layer that Phase 3's GUI will reuse.

**What works today:**
- Full turn cycle: REFRESH → DRAW → DON → MAIN → END
- MAIN phase menu: Attack, Play Card, Attach Don, View Hand/Field/Opponent Field, End Phase
- Battle resolution: attacker vs. opponent's leader or rested characters
- Defend window during battle: defender offered blocker selection (skipped if attacker is Unblockable), then counter card loop, then single resolve
- Mulligan on game start
- Life system: successful attack on leader removes a life card to opponent's hand; game ends when life is empty and leader is attacked
- Cost payment enforcement: paying a card's DON cost by resting DON cards from the cost zone
- Summoning sickness: characters cannot attack the turn they are played
- Keyword enforcement: Rush (character can attack turn played), Blocker (can intercept attacks), Double Attack (deals 2 damage on hit), Banish (life card goes to trash instead of hand), Unblockable (opponent cannot activate Blocker), Trigger (activates when a life card is revealed; player is offered the choice to activate)
- `GRANT_KEYWORD` effect: cards can temporarily grant keywords (e.g. gains [Blocker] until end of turn); `WHILE_IN_PLAY` duration adds a permanent `Ability` object instead; cleared automatically during REFRESH for temporary grants
- `SEARCH_DECK` effect: look at top N cards, player selects up to M matching cards to hand, rest return to deck top
- `MOVE_TO_TOP_DECK` / `MOVE_TO_BOTTOM_DECK` effects: move cards to top or bottom of a player's deck
- `PREVENT_ATTACK` / `PREVENT_KO` / `PREVENT_BLOCK` effects: enforced via temporary keywords (`CANNOT_ATTACK`, `CANNOT_KO`, `UNBLOCKABLE`); cleared during REFRESH; `WHILE_IN_PLAY` duration adds permanent `Ability` object
- `PLAY_CARD` effect: play a card for free from hand or trash (routes by card type: CHARACTER → field, EVENT → trash, STAGE → stage); `ON_PLAY` dispatched normally
- `MODAL` effect: choose-one branching; `ModalSelector` functional interface stored on `GameState`; engine picks branch 0 by default (interactive CLI menu available via `setModalSelector`)
- `NEGATE` effect: suppresses all abilities on the targeted card for the current turn via `Ability.isNegated` flag; `AbilityManager.dispatch()` skips negated abilities; cleared by `ability.resetTurn()` during REFRESH
- `RUSH_CHARACTER` keyword: character can attack on the turn it is played; valid targets restricted to rested Characters only (cannot attack the opponent's Leader)
- Duration correctness: all four `Duration` values route to separate power/cost buckets — `THIS_BATTLE` → `bonusBattlePower` (cleared post-battle); `THIS_TURN` → `bonusPower`/`bonusCost` (cleared each turn); `OPPONENTS_NEXT_TURN` → `bonusPowerOpponentTurn`/`bonusCostOpponentTurn` (cleared by `TurnManager.endTurn()` on non-active player); `WHILE_IN_PLAY` → `bonusPowerWhileInPlay`/`bonusCostWhileInPlay` (cleared when card leaves field)
- Field character limit: 5 characters max per player; UI prompts to replace when full
- Stage card limit: 1 stage card per player; new stage replaces the old one
- 12 selectable dummy decks via `GameFactory`: 6 single-color (Red, Blue, Green, Purple, Black, Yellow) + 6 dual-color; players pick from a numbered menu before game start via `Main.java`; each deck's 50 cards mirror its color identity (aggression, control, DON!! engine, etc.)
- Ability/effect descriptions shown under each card in hand view, field view, and card-played announcements; falls back to formatted keyword list (e.g. `[Rush]`, `[Blocker]`) for generic test cards
- Multi-color card support: `CardData.colors` is `List<Color>`; CLI alternates ANSI codes per character for dual-color cards (e.g. Green/Yellow → alternating letters)
- Target filtering by subtype, name, attribute, and color: `Target` record carries `subtype`, `nameFilter`, `attribute`, `color` fields; `matchesFilters()` checks all four against card data
- 15 new `Condition` values implemented: `IF_FIELD_CHARACTER_POWER`, `IF_FIELD_CHARACTER_NAME`, `IF_FIELD_CHARACTER_NAME_RESTED`, `IF_FIELD_CHARACTER_COST_COUNT`, `IF_RESTED_CHARACTER_COUNT`, `IF_NOT_FIELD_CHARACTER_NAME`, `IF_OPPONENT_CHARACTER_COUNT`, `IF_OPPONENT_RESTED_CHARACTER_COUNT`, `IF_OPPONENT_DON_COUNT`, `IF_OPPONENT_FIELD_CHARACTER_POWER`, `IF_OPPONENT_LIFE_COUNT`, `IF_OPPONENT_HAND_COUNT`, `IF_LEADER_MULTICOLORED`, `IF_LEADER_NAME`, `IF_LEADER_ATTRIBUTE`
- Card data pipeline: raw scraped JSON → `CardCompiler` → compiled JSON → `CardDatabase`
- `Parser`: structured ability extraction from card text into `{trigger, condition, cost, character, effect}` records
- `CardData` record carries a `subTypes` field (normalized from API `sub_types` strings)
- `CardDatabase` secondary index: `getByName()` (O(1)), `getBySubtype()` (O(n)), `getAllCards()`
- `CsvGenerator`: offline tool that rebuilds `card_names_subtypes.csv` from compiled (or raw) JSON with name normalization and deduplication
- 370 unit tests — 0 failures (10 data-dependent `CardDatabaseTest` tests skip cleanly when `raw/data/` is empty; 1 shuffle test is probabilistically flaky by design)
- `CardCompatibilityReport` tool: flags UNIMPLEMENTED_TRIGGER, UNIMPLEMENTED_EFFECT, UNIMPLEMENTED_COST, COMPOUND_COST, WHILE_IN_PLAY_DURATION, NO_ABILITIES; baseline: 2322 total cards, 1903 fully playable (82%); writes `csv/card_compatibility_report.csv`
- `[Activate:Main]` abilities wired in `CliController`: `GameState.canAffordActivation()` / `payActivationCost()` for all 9 `CostType` values; `CliController.activateAbility(Player)` with interactive cost display; compound activation costs (`List<Cost>`) checked atomically
- `[Opponent's Turn]` trigger dispatch: `TurnManager.advancePhase(DON)` calls `am.dispatchAll(OPPONENTS_TURN, opponent, opponent)` — fires for non-active player's field + leader; power/keyword boosts use `THIS_TURN` duration and clear via `refreshField()`
- `CostType.LIFE` split into `LIFE_TO_HAND` (place life → hand, Big Mom archetype) and `LIFE_TO_TRASH` (trash life cards); `CardCompiler` detects destination from cost text patterns

---

## Game Rules Summary

| Zone | Contents |
|---|---|
| Deck | 50 Character/Event/Stage cards, same color as leader |
| Don Deck | 10 DON!! cards |
| Hand | Drawn cards |
| Character Area | Up to 5 played characters |
| Cost Area | Rested DON cards used to pay costs |
| Life | Cards placed face-down at game start (equal to leader's life value) |
| Stage | One stage card in play at a time |
| Trash | Used/defeated cards |

**Turn order:** Refresh → Draw (skip on first player's first turn) → DON (1 on P1's first turn, 2 all other turns) → Main → End

**Main Phase actions:**
- Play a card by paying its cost in DON (rest that many DON cards from the cost zone)
- Attack with your leader or any active (non-rested) character
  - Valid targets: opponent's leader, or opponent's rested characters only
  - Attacker rests after attacking; characters cannot attack the turn they are played
- Attach a DON card to your leader or a character (+1000 power **during your turn only**)
- Play Event cards for one-time effects (goes to trash)

**Win condition:** Successfully attack the opponent's leader when they have no life cards remaining.

**Key keywords:**
- **Rush** — Character can attack on the turn it is played
- **Rush: Character** — Character can attack on the turn it is played; valid targets restricted to rested Characters only (cannot attack the opponent's Leader)
- **Blocker** — Can intercept an attack targeting another card
- **Double Attack** — This card deals 2 damage when it attacks
- **Banish** — When this card deals damage, the target life card is trashed without activating its Trigger
- **Unblockable** (§10-1-7) — Opponent cannot activate Blocker against this card
- **Trigger** — Activates when a life card is revealed (no DON cost)
- **Counter** — Played from hand when your card is being attacked; goes to trash afterward

**Key rules clarifications (from official rulebook):**
- `[DON!! xX]` is a **condition** (§8-3-2-3), not a cost — the card must have ≥X DON attached to activate the effect
- `DON!! −X` (§8-3-1-6) is a distinct **cost type**: return X DON cards to the DON deck (not the cost zone)

---

## Color Identity

Each color has a dominant playstyle reflected in its most-played effect types (derived from the compiled card corpus across all sets):

| Color | Dominant Effects | Playstyle |
|-------|-----------------|-----------|
| **Red** | `POWER_BOOST` (181), `KO` (57), `ATTACH_DON` (32), `GRANT_KEYWORD` (24) | **Aggression.** Overwhelm through raw power. DON!! attachment pumps characters; keywords like Rush and Double Attack push for early damage. |
| **Blue** | `DRAW` (101), `MOVE_TO_BOTTOM_DECK` (100), `RETURN_TO_HAND` (75), `SEARCH_DECK` (68) | **Control / Hand.** Disrupt and grind. Bounce opponent's cards, bury threats to the deck bottom, dig for answers. Card advantage engine. |
| **Green** | `REST` (113), `PLAY_CARD` (58), `SET_DON_ACTIVE` (55), `UNREST` (45) | **DON!! / Field Control.** Tempo through DON!! manipulation. Rest opponent characters to lock attacks; unrest your own to reuse Blockers mid-turn. |
| **Purple** | `ADD_DON` (169), `REST` (84), `SET_DON_ACTIVE` (81), `PLAY_CARD` (50) | **DON!! Engine / Big Plays.** The most DON!!-centric color. Stacks DON!! to power up massive plays, activates spent resources. Explosive ceiling. |
| **Black** | `TRASH` (134), `KO` (76), `COST_MODIFY` (72), `PLAY_CARD` (52) | **Removal / Graveyard.** Surgical destruction. Trash as both resource and weapon — discard opponent cards, play threats from trash, cost-reduce for value. |
| **Yellow** | `PLAY_CARD` (101), `ADD_LIFE` (63), `DRAW` (56), `KO` (54) | **Life / Triggers.** Buffer damage with added life cards, stack Trigger effects that fire when life is hit. Plays reactively — deceptively resilient. |

**Common dual-color pairings and their combined identity:**

| Pairing | Identity |
|---------|----------|
| Red / Green | Power + Field Lock — pump your cards while resting theirs |
| Blue / Black | Control + Removal — bounce or KO everything, grind the opponent out |
| Purple / Green | DON!! Overdrive — both colors manipulate DON!! for explosive multi-threat turns |
| Yellow / Red | Life Buffer + Aggression — pad your life total while swinging hard |
| Blue / Purple | Draw + DON!! Engine — dig through your deck while fueling large plays |
| Black / Yellow | Removal + Resilience — eliminate threats and replenish life to extend the game |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven 3.8+ |
| Testing | JUnit 5 |
| JSON parsing | Jackson (for `CardDatabase`, `CardCompiler`) |

---

## Prerequisites

- **Java 21** or later (`java -version`)
- **Maven 3.8+** (`mvn -version`)

---

## Setup

```bash
git clone <repo-url>
cd optcg
mvn compile
```

---

## Running the Game (CMU Prototype)

```bash
mvn exec:java -Dexec.mainClass="engine.Main"
```

Or run `engine.Main` directly from your IDE. This starts a two-player local game using the pre-built test decks from `GameFactory`.

---

## Card Data Pipeline

Raw card data is scraped from the API by `CardScraper` and saved to `src/main/resources/raw/data/`. Run `CardCompiler` once after scraping to produce clean, engine-ready JSON in `src/main/resources/compiled/data/`:

```bash
mvn exec:java -Dexec.mainClass="tools.CardCompiler"
```

`CardCompiler` deduplicates cards by `card_set_id` (keeping the base version over SP/Parallel variants), strips fields unused by the engine, appends a structured `abilities[]` array to each card using `Parser`, and populates each ability's `effects[]` array by running `EffectParser` on the effect body text. At the end of compilation it calls `CardChecklistCsvGenerator` to write `csv/card_checklist.csv` — a per-card checklist of unknown tokens and override flags for tracking parser coverage.

`CardDatabase` loads compiled JSON at startup. Cards are indexed by `card_set_id` (primary) and by `name` (secondary, case-insensitive, multi-result). Three query methods are provided:

| Method | Returns | Notes |
|---|---|---|
| `getCardData(id)` | `CardData` | O(1); throws `IllegalArgumentException` if not found |
| `getByName(name)` | `List<CardData>` | O(1); case-insensitive; multiple results for reprints |
| `getBySubtype(subtype)` | `List<CardData>` | O(n) `contains()` scan (multi-word subtype names cannot be tokenised on whitespace) |
| `getAllCards()` | `Collection<CardData>` | Unmodifiable view of the full index |

Multi-color cards (e.g., `"Green Yellow"` in the API) are fully supported: `ColorDeserializer` parses all space-separated color tokens into a `List<Color>` on `CardData.colors`. The CLI alternates ANSI color codes per character when displaying multi-color card names.

`CsvGenerator` is a standalone offline tool that rebuilds `card_names_subtypes.csv` from compiled data (or falls back to raw), strips parenthetical variant suffixes and trailing ` - SETID` patterns from card names, deduplicates on `(name, cardType)`, and writes a sorted CSV. Run it after compiling:

```bash
mvn exec:java -Dexec.mainClass="tools.CsvGenerator"
```

---

## Running Tests

```bash
mvn test
```

**Current test suite: 370 tests, 0 failures, 0 errors.**
10 `CardDatabaseTest` cases are data-dependent and skip automatically (via `assumeTrue`) when `raw/data/` is empty — run `CardScraper` first to populate. 1 shuffle test is probabilistically flaky by design (documented in the test).

| Test class | Tests | Coverage area |
|---|---|---|
| `BattleHandlerTest` | 35 | `canAttack`, `getValidAttackers`, `getValidTargets`, `resolve`, keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable, Trigger, Rush:Character) |
| `AbilityManagerTest` | 20 | `dispatch` for ON_PLAY, WHEN_ATTACKING, ON_KO, END_OF_YOUR_TURN, DON!! xX condition gate, once-per-turn, TRIGGER; `canAffordActivation`/`payActivationCost` for all 9 CostType values; compound costs (atomic check + payment) |
| `TurnManagerTest` | 10 | Phase transitions, turn count, Don draw counts, OPPONENTS_TURN dispatch for field + leader |
| `GameStateTest` | 14 | Draw, trash, play card, remove life, refresh, cost payment, field/stage limits |
| `GameSetupTest` | 4 | Initialization, mulligan, life setup |
| `CardTest` | 7 | Rest/activate, Don attachment and detach, summoning sickness, grantKeyword, clearGrantedKeywords, hasKeyword (permanent survives clear) |
| `EffectFactoryTest` | 131 | All EffectTypes, target resolution, all condition gates, GRANT_KEYWORD, SEARCH_DECK, PREVENT_ATTACK/KO/BLOCK, PLAY_CARD, MODAL, NEGATE, FLIP_LIFE, COUNTER, all Duration buckets (THIS_BATTLE/THIS_TURN/OPPONENTS_NEXT_TURN/WHILE_IN_PLAY), all new field/opponent conditions |
| `DeckTest` | 4 | Draw, draw from empty, search |
| `ZoneTest` | 6 | Add, remove, draw top, move card, shuffle |
| `PlayerTest` | 2 | Zone initialization, leader assignment |
| `CardDatabaseTest` | 16 (10 skipped without data) | Card loading, deserialization, `getByName`, `getBySubtype`, `getAllCards`, `CardFactory` |
| `ParserTest` | 21 | Ability parsing: triggers, conditions, costs, keywords, multi-ability blocks, Blocker+On Block inline split |
| `GameFactoryTest` | 9 | Pre-built test deck construction, card type breakdown, ownership, field/stage/don counts |
| `TokenTest` | 8 | `Token` record field access, value types (int/String/null), record equality, toString |
| `EffectLexerTest` | 34 | All 13 token types, bracket detection (NAME_FILTER/SUBTYPE_FILTER/ATTRIBUTE_FILTER), regex filters, K.O. normalisation, phrase-length preference, 6 verbatim card text examples from CSV |
| `EffectParserTest` | 32 | All EffectTypes (DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST, COST_MODIFY, ADD_DON, SET_DON_ACTIVE, GRANT_KEYWORD, PLAY_CARD, NEGATE, PREVENT_ATTACK, UNREST, ATTACH_DON), optional flag, conditions (IF_YOU_DO, IF_RESTED, IF_LEADER_COLOR, IF_LEADER_NAME + conditionSubtype, IF_LEADER_TYPE + conditionSubtype), multi-clause chains, subject-first clauses |

---

## Project Architecture

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
│   │       └── CardCompatibilityReport.java     ← Flags cards not yet fully playable by the engine; writes csv/card_compatibility_report.csv
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

**Key architectural rules:**
- `BattleSystem` owns all game logic; `CliController` is UI-only
- `GameEngine.run()` drives the loop; non-interactive phases auto-advance via `TurnManager`
- `GameState` is the single source for all state mutations (move, draw, trash, attach, cost, etc.)
- Don source zone for attachment = `cost` zone (not hand), matching `drawDon()` behavior
- Attack targets = opponent's leader + opponent's **rested** characters only
- `[DON!! xX]` is a **condition** (§8-3-2-3), not a cost — the card must have ≥X DON attached
- `DON!! −X` (§8-3-1-6) is a separate cost type: return X DON to the DON deck
- `Zone` uses a `Deque` internally: `add()` = addFirst (top), `draw()` = removeFirst (top), `addBottom()` = addLast — supports top and bottom card placement for card effects
- `tools/` is an offline pipeline; `CardScraper`, `CardCompiler`, `CsvGenerator`, and `Parser` run once at data-prep time and are not part of the game runtime
- `CardData.colors` is `List<Color>` — `ColorDeserializer` parses all space-separated color tokens; no single-color convenience accessor exists, callers use `colors()` directly; `CliController.colorize()/boldColorize()` accept `List<Color>` and alternate ANSI codes per character for multi-color cards

---

## Roadmap

### Phase 0 — CMU Prototype *(complete)*
Single-machine two-player CLI game with dummy decks. Validates the core rules engine.

### Phase 1 — CMU Complete *(complete)*
- Cost payment enforcement (rest DON cards to play characters)
- Character summoning sickness (cannot attack turn played)
- Field character limit (5 characters max); stage limit (1 stage, replaced on play)
- Card data pipeline: raw scraped JSON → `CardCompiler` → compiled JSON → `CardDatabase`
- `Parser` producing structured `{trigger, condition, cost, character, effect}` ability records
- `CardData.subTypes` field mapped from API `sub_types`; normalized at load time (`null`/`"null"` → `""`)
- `CardDatabase` secondary indexes: `getByName()` (O(1), case-insensitive), `getBySubtype()` (O(n)), `getAllCards()`
- `CsvGenerator` tool: rebuilds `card_names_subtypes.csv` with name normalization and deduplication
- `asText()` deprecation resolved in `CardCompiler` and `CsvGenerator` (`asText(null)` → `textValue()`)
- Rulebook-correct keyword list: Rush, Rush: Character, Blocker, Double Attack, Banish, Unblockable, Trigger, Counter

### Phase 2 — Abilities, Effects, and Keywords *(complete)*

All 29 items complete. 370 tests green. 1903/2322 cards (82%) fully playable per `CardCompatibilityReport`.

**Complete:**
- `AbilityData` wired end-to-end: `CardCompiler` emits typed ability JSON, `CardDatabase` deserializes into `List<AbilityData>`, `AbilityFactory` hydrates runtime `Ability` objects on every card, `Card.hasKeyword()` works as a single-line predicate
- Keyword system implemented and tested:
  - **Rush**: character can attack the turn it is played
  - **Rush: Character**: character can attack the turn it is played; restricted to rested Characters only (cannot attack the opponent's Leader)
  - **Blocker**: can intercept attacks targeting another card (defend window)
  - **Double Attack**: deals 2 life damage on a successful attack
  - **Banish**: target life card goes to trash instead of opponent's hand
  - **Unblockable**: opponent's blocker window is skipped entirely
- CLI defend window: sequential blocker → counter loop → single `BattleSystem.resolve()`
- Card text corpus analysed (3,115 cards) → `effect_templates_generic.csv` + `effect_templates_unique.csv`; `EffectType` enum redesigned to 23 types; `EFFECT_GRAMMAR.md` written
- `EffectLexer` word-level scanner backed by `EffectTables` (shared phrase→TokenType map): longest-phrase-first matching; bracket detection emits distinct types — `[Name/Keyword]` → `NAME_FILTER`, `{Subtype}` → `SUBTYPE_FILTER`, `(Attribute)` → `ATTRIBUTE_FILTER`; 7 regex patterns for numeric cost/power filters; normalisations for K.O., en-dash, and `"TypeName"` quoted spans
- `EffectTemplateCsvGenerator`: reads compiled JSON, re-runs `Parser.parse()`, generalises effect text, splits compound effects on `•`/`. Then, `/`If X, Y.`, strips modal trigger labels, deduplicates on `(simplified, trigger)`, writes generic/unique CSV pair
- `LexerCoverageReport`: runs `EffectLexer` on all CSV templates, reports fully-tokenised vs. UNKNOWN, lists top unknown tokens by frequency — current coverage **99.0% generic / 96.0% unique**
- `Parser.splitOnEmbeddedTriggers`: fixed `[Blocker] (explanation) [On Block] effect` being parsed as one ability; added `sawKeyword` tracking + parenthetical skip so the embedded trigger fires the split
- `Duration` enum added (`THIS_BATTLE`, `THIS_TURN`, `OPPONENTS_NEXT_TURN`, `WHILE_IN_PLAY`); `EffectData.duration` promoted from `int` to `Duration`; each value routes to a separate power/cost bucket in `Card` cleared at the correct time
- `ParsedEffect` record: intermediate structured representation of a single parsed effect clause (tools pipeline; not part of game runtime)
- `MODIFIER` token type added to `TokenType` (10th value): `may` (optional action) and `must` (forced action); `EffectParser` reads the MODIFIER flag to populate `ParsedEffect.optional`
- `EffectParser` Phase 1 complete: `List<Token>` → `List<ParsedEffect>`; covers DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST, COST_MODIFY, ADD_DON, SET_DON_ACTIVE, GRANT_KEYWORD, PLAY_CARD, NEGATE, PREVENT_ATTACK, UNREST, ATTACH_DON; clause splitting on CONNECTOR; condition classification; optional flag from MODIFIER tokens; subject-first clause handling
- `SUBTYPE_FILTER`, `NAME_FILTER`, `ATTRIBUTE_FILTER` added to `TokenType` (11th–13th values): `{Subtype}` / `[Name]` / `(Attribute)` bracket forms now emit distinct token types; `EffectParser.classifyConditionClause()` uses them to classify `IF_LEADER_TYPE`, `IF_LEADER_NAME`, `IF_LEADER_ATTRIBUTE`, `IF_FIELD_CHARACTER_TYPE` conditions; `conditionSubtype` field added to `ParsedEffect` and written as `"subtype"` in compiled JSON
- `CardCompiler.toEffectJson()` implemented: each non-KEYWORD ability's `effects[]` is now populated from `EffectParser.parse()` output; `buildTargetNode()`, `mapZone()`, `mapParsedCardType()` helpers produce engine-compatible field names
- `CardChecklistCsvGenerator`: called automatically at the end of `CardCompiler.main()`; writes `csv/card_checklist.csv` with columns `card_name`, `card_set_id`, `card_text`, `generic_card_text`, `unknown_tokens`, `needs_override`; deduplicates across sets/decks/promos by `card_set_id`
- `CardType.fromValue()` `@JsonCreator` added: case-insensitive Jackson deserialization (`"Character"` → `CHARACTER`) so compiled JSON written before the enum rename round-trips correctly
- Life card **Trigger** system: `BattleSystem.resolve()` returns `List<Card>` (revealed life cards); `BattleSystem.resolveTrigger(Player, Card)` dispatches via `AbilityManager` then trashes the trigger card; `CliController.trigger()` offers player choice with effect text preview; BANISH path routes life card to trash — no trigger offered; `announceTrigger()` announces activation
- **`SHUFFLE_DECK`** effect: `Deck.shuffle()` implemented; wired in `EffectFactory`
- **`GRANT_KEYWORD`** effect: `Card.grantKeyword(Keyword)` / `clearGrantedKeywords()`; `hasKeyword()` checks runtime grants before permanent abilities; `GameState.refreshField()` clears grants each turn; `EffectData.keyword` field added

- `EffectFactory.evaluateCondition()` real implementations: `IF_LEADER_MULTICOLORED` (`colors().size() > 1`), `IF_LEADER_NAME` (name equalsIgnoreCase), `IF_LEADER_TYPE` (`containsSubtype`), `IF_LEADER_ATTRIBUTE` (attribute enum name match), `IF_FIELD_CHARACTER_TYPE` (field scan); `Condition` enum gained `IF_LEADER_NAME` and `IF_LEADER_ATTRIBUTE`; `IF_LEADER_COLOR` renamed to `IF_LEADER_MULTICOLORED`
- `[Activate:Main]` abilities wired in `CliController`: `GameState.canAffordActivation(Player, List<Cost>, Card)` + `payActivationCost`; all 9 `CostType` values handled (DON, DON_MINUS, DON_REST, TRASH, TRASH_SELF, REST_SELF, REST_NAMED, REST_CHARACTER, REST_ANY); `CliController.activateAbility(Player)` with interactive cost menu and `formatActivationCost()` display
- `[Opponent's Turn]` trigger dispatch: `TurnManager.advancePhase(DON)` calls `am.dispatchAll(OPPONENTS_TURN, opponent, opponent)` for non-active player's field + leader; power/keyword boosts use `THIS_TURN` duration
- Compound activation costs: `AbilityData` uses `List<Cost> costs`; `canAffordActivation` checks all costs atomically; `payActivationCost` iterates only after the guard succeeds
- `CostType.LIFE` split into `LIFE_TO_HAND` (place top life cards → hand, Big Mom archetype) and `LIFE_TO_TRASH` (trash life cards); `CardCompiler` detects destination from cost text; all 30 previously flagged LIFE cost cards resolved
- `CardCompatibilityReport` tool: offline analysis of `CardDatabase` against engine; flags UNIMPLEMENTED_TRIGGER, UNIMPLEMENTED_EFFECT, UNIMPLEMENTED_CONDITION, UNIMPLEMENTED_COST, COMPOUND_COST, WHILE_IN_PLAY_DURATION, NO_ABILITIES; writes `csv/card_compatibility_report.csv`; baseline: 2322 cards, 1903 fully playable (82%)

### Phase 2.5 — Enhanced CMU (Tmux UI)

A purely terminal-based upgrade that gives the CMU a persistent, multi-pane layout using **Tmux** — no graphics, no framework, no new runtime dependencies. The rules engine is completely unchanged; only the presentation layer is extended.

This phase also builds the **local socket IPC layer** that the Phase 3 GUI will reuse. Since the GUI and CLI will have identical capabilities in different formats, the state-broadcast infrastructure built here is not throwaway work — Phase 3 simply swaps the Tmux board renderer for a JavaFX one connected to the same socket.

**Layout (3 panes):**
```
┌──────────────────────────────────────┬──────────────────┐
│  PANE 0 — BOARD (read-only)          │  PANE 2 — CHAT   │
│                                      │                  │
│  ╔══════════ Opponent ═════════════╗ │  P1: gg          │
│  ║ Leader [RED 5000] Life:4 Don:3  ║ │  P2: nice game   │
│  ║ [Zoro 5k] [Nami 3k/REST]        ║ │                  │
│  ╠══════════ Your Side ════════════╣ │                  │
│  ║ Leader [GRN 5000] Life:5 Don:6  ║ │                  │
│  ║ [Luffy 4k] [Usopp 2k]           ║ │                  │
│  ╚════════════════════════════════╝ │                  │
├──────────────────────────────────────┴──────────────────┤
│  PANE 1 — ACTIONS (interactive)                         │
│  Hand: [Chopper(2)] [Robin(4)] [Franky(5)]              │
│  > 1) Attack  2) Play Card  3) Attach Don  4) End Phase │
└─────────────────────────────────────────────────────────┘
```

**IPC architecture (local socket):**

The game process runs a `GameStateServer` on a local socket. After every state mutation, it pushes a serialized snapshot to all connected clients. Each pane (and later the GUI) is a client that connects and renders what it receives:

```
GameEngine ──► GameState ──► GameStateServer (local socket)
                                    │
                    ┌───────────────┼─────────────────┐
                    ▼               ▼                 ▼
             BoardRenderer     ChatLog.java      [Phase 3]
             (ANSI/Tmux)      (chat.log tail)   JavaFX GUI
```

- **Board pane**: `BoardRenderer` connects to the socket, receives state snapshots, and redraws the ANSI board on each update
- **Actions pane**: `CliController` runs here unchanged — normal stdin/stdout
- **Chat pane**: an append-only `chat.log` that both players write to via `/say` from the actions pane; chat pane tails the file
- **State snapshots**: `GameState` is serialized to JSON (Jackson, already a dependency) and broadcast on the socket after each mutation — the same format `CardDatabase` already uses

**Why it's constructive:**
- The board stays visible at all times while the active player is typing — no scrolling past game state
- Opponent's field is always in view during decision-making
- Card colors are visually scannable (ANSI codes — Red/Green/Blue/Purple/Black/Yellow) rather than plain text labels
- Chat is persistent and kept separate from game output
- The socket layer is the foundation both the Tmux board renderer and the Phase 3 GUI connect to — building it here means Phase 3 is purely a renderer swap, not a re-architecture

**What it cannot do vs. a GUI:**
- No card images, drag-and-drop, or mouse interaction
- No animations

**New files this phase would add:**
- `engine/ui/GameStateServer.java` — local socket server; broadcasts serialized `GameState` snapshots to all connected clients
- `engine/ui/GameStateClient.java` — base client; connects to socket, receives and deserializes snapshots
- `engine/ui/cli/BoardRenderer.java` — `GameStateClient` subclass; renders `GameState` as ANSI/Unicode text to the board pane
- `engine/ui/cli/TmuxLauncher.java` — programmatically creates and configures the tmux session on startup
- `engine/ui/cli/ChatLog.java` — append-only shared chat file writer/tailer

### Phase 3 — Engine Completion
- `MAIN` trigger dispatch (231 cards): `[Main]` event effect cards; `CliController.runMainPhases()` needs a "Play Event" path
- `OPPONENTS_ATTACK` trigger dispatch (42 cards): fires after opponent's `WHEN_ATTACKING` resolves
- `ON_BLOCK` trigger dispatch (11 cards): fires when a Blocker is declared
- `WHILE_IN_PLAY` recalculate-on-query layer: `getPowerWithEffects()` scans active continuous-effect abilities on each call instead of stale buckets
- Replacement effects: interrupt hook in `BattleSystem` + `GameState` for "if this would be K.O.'d… instead" effects
- `GameFactory` real-card decks: replace 12 synthetic dummy decks with compiled cards selected via `CardCompatibilityReport`
- `CliController` + Tmux UI kept as the primary play surface throughout this phase

### Phase 4 — Training Mode
- **Tutorial**: guided walkthrough of rules and actions
- **Self vs Self**: control both sides from one machine
- **Self vs AI**: automated opponent with configurable difficulty and playstyle

### Phase 5 — Battle Mode + GUI
- **JavaFX GUI**: `GameStateClient` subclass in JavaFX connects to the same local socket as the Tmux board renderer; renders board graphically with card images (`card_image` / `card_image_id` already in compiled JSON), animated zone transitions, power/cost/rest state; `CliController` + Tmux kept as fallback / debug mode
- **Local play**: two players on one machine
- **Online play**: vs friend (gamer tag), vs local (region matchmaking), vs global
- **Tournament mode**: bracket-style matchups

### Phase 6 — Simulation
- AI reconstruction: analyze a recorded opponent's playstyle and simulate their decision-making
- Take over either side of a saved game at any point in time
- AI plays as the opponent from that point forward (analyzes and reconstructs behavior)

### Phase 7 — Story Mode *(future)*
- Choose a leader (Luffy, Zoro, …) and run a campaign
- Visual novel-style plot progression
- Progressive AI difficulty following the story arc
- Potential card unlocks through campaign progress

### Phase 8 — Replay & TCG TV *(future)*
- Record and replay any previous match in full
- Pause replay at any moment and take over either side manually
- Observe live or recorded games from other players (on-air and off-air)
- Broadcast infrastructure for tournament coverage

---

See [DEVLOG.md](DEVLOG.md) for a full account of what was built manually, what was AI-assisted, the key prompts used, and what changed through iteration.

*This project is in active development. Phase 1 (CMU Complete) is done — the core rules engine, cost enforcement, card data pipeline, and ability parser are all in place. Phase 2 is complete: all 29 items done, 370 tests green, 1903/2322 cards (82%) fully playable; keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable, Trigger, GRANT_KEYWORD), the full tools pipeline (EffectLexer → EffectParser → CardCompiler → compiled JSON) with 99%/96% lexer coverage, ACTIVATED_MAIN interactive abilities, OPPONENTS_TURN dispatch, compound costs, and the CardCompatibilityReport playability baseline are all in. Phase 2.5 builds the Tmux UI and the local socket IPC layer that Phase 3's GUI will reuse directly — the CLI and GUI share the same state-broadcast infrastructure and have identical capabilities in different formats.*
