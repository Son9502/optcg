# One Piece Virtual TCG — `optcg`

A Java-based digital engine for the One Piece Card Game (OPTCG). The long-term vision is a full-featured platform where players can battle locally or globally, train against themselves or an AI, observe and replay matches, and run strategic simulations — all built on top of a faithful rules engine.

---

## Current Status — Phase 2 *(in progress)*

> **CMU (Command-line Minimum UI)** is the first milestone: a fully playable single game via the terminal with no graphics. Phase 1 is complete. Phase 2 is wiring keyword and effect semantics into the rules engine.

**What works today:**
- Full turn cycle: REFRESH → DRAW → DON → MAIN → END
- MAIN phase menu: Attack, Play Card, Attach Don, View Hand/Field/Opponent Field, End Phase
- Battle resolution: attacker vs. opponent's leader or rested characters
- Defend window during battle: defender offered blocker selection (skipped if attacker is Unblockable), then counter card loop, then single resolve
- Mulligan on game start
- Life system: successful attack on leader removes a life card to opponent's hand; game ends when life is empty and leader is attacked
- Cost payment enforcement: paying a card's DON cost by resting DON cards from the cost zone
- Summoning sickness: characters cannot attack the turn they are played
- Keyword enforcement: Rush (character can attack turn played), Blocker (can intercept attacks), Double Attack (deals 2 damage on hit), Banish (life card goes to trash instead of hand), Unblockable (opponent cannot activate Blocker)
- Field character limit: 5 characters max per player; UI prompts to replace when full
- Stage card limit: 1 stage card per player; new stage replaces the old one
- Two pre-built test decks via `GameFactory` (Luffy/Red and Zoro/Green), with keyword-bearing cards at each cost tier for manual testing
- Card data pipeline: raw scraped JSON → `CardCompiler` → compiled JSON → `CardDatabase`
- `Parser`: structured ability extraction from card text into `{trigger, condition, cost, character, effect}` records
- `CardData` record carries a `subTypes` field (normalized from API `sub_types` strings)
- `CardDatabase` secondary index: `getByName()` (O(1)), `getBySubtype()` (O(n)), `getAllCards()`
- `CsvGenerator`: offline tool that rebuilds `card_names_subtypes.csv` from compiled (or raw) JSON with name normalization and deduplication
- 180 unit tests — 0 failures (10 data-dependent `CardDatabaseTest` tests skip cleanly when `raw/data/` is empty)

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
- **Rush: Character** (§10-1-6) — Leader ability that grants Rush to Character cards played that turn (Phase 3)
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

Multi-color leaders (e.g., `"Red Green"` in the API) are stored as their primary color only for CMU; full multi-color support (requiring `List<Color>` and deck validation changes) is deferred to Phase 2.

`CsvGenerator` is a standalone offline tool that rebuilds `card_names_subtypes.csv` from compiled data (or falls back to raw), strips parenthetical variant suffixes and trailing ` - SETID` patterns from card names, deduplicates on `(name, cardType)`, and writes a sorted CSV. Run it after compiling:

```bash
mvn exec:java -Dexec.mainClass="tools.CsvGenerator"
```

---

## Running Tests

```bash
mvn test
```

**Current test suite: 180 tests, 0 failures, 0 errors.**
10 `CardDatabaseTest` cases are data-dependent and skip automatically (via `assumeTrue`) when `raw/data/` is empty — run `CardScraper` first to populate.

| Test class | Tests | Coverage area |
|---|---|---|
| `BattleHandlerTest` | 25 | `canAttack`, `getValidAttackers`, `getValidTargets`, `resolve`, keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable) |
| `TurnManagerTest` | 6 | Phase transitions, turn count, Don draw counts |
| `GameStateTest` | 14 | Draw, trash, play card, remove life, refresh, cost payment, field/stage limits |
| `GameSetupTest` | 4 | Initialization, mulligan, life setup |
| `CardTest` | 4 | Rest/activate, Don attachment and detach, summoning sickness |
| `DeckTest` | 4 | Draw, draw from empty, search |
| `ZoneTest` | 6 | Add, remove, draw top, move card, shuffle |
| `PlayerTest` | 2 | Zone initialization, leader assignment |
| `CardDatabaseTest` | 16 (10 skipped without data) | Card loading, deserialization, `getByName`, `getBySubtype`, `getAllCards`, `CardFactory` |
| `ParserTest` | 21 | Ability parsing: triggers, conditions, costs, keywords, multi-ability blocks, Blocker+On Block inline split |
| `GameFactoryTest` | 9 | Pre-built test deck construction, card type breakdown, ownership, field/stage/don counts |
| `TokenTest` | 8 | `Token` record field access, value types (int/String/null), record equality, toString |
| `EffectLexerTest` | 34 | All 10 token types, bracket detection, regex filters, K.O. normalisation, phrase-length preference, 6 verbatim card text examples from CSV |
| `EffectParserTest` | 27 | All EffectTypes (DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST, COST_MODIFY, ADD_DON, SET_DON_ACTIVE, GRANT_KEYWORD, PLAY_CARD, NEGATE, PREVENT_ATTACK, UNREST, ATTACH_DON), optional flag, conditions (IF_YOU_DO, IF_RESTED, IF_LEADER_TYPE), multi-clause chains, subject-first clauses |

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
│   │   │   │   ├── effects/                     ← EffectData, EffectType, Duration, EffectFactory (stub)
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
│   │   │   │   ├── GameFactory.java             ← Pre-built test decks (Luffy vs Zoro)
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
│   │       ├── TokenType.java                   ← 10-value enum (VERB, QUANTITY, PLAYER, CARD_TYPE, FILTER, ZONE, DURATION, CONDITION, CONNECTOR, MODIFIER)
│   │       ├── Token.java                       ← Record: (TokenType type, String raw, Object value)
│   │       ├── EffectTables.java                ← All phrase→TokenType entries, sorted lists, regex filter Patterns (shared by EffectLexer and EffectParser)
│   │       ├── EffectLexer.java                 ← Word-level scanner; produces List<Token> from raw effect text
│   │       ├── ParsedEffect.java                ← Intermediate structured effect record (tools pipeline only)
│   │       ├── EffectParser.java                ← Token→ParsedEffect parser; resolveVerb, splitClauses, extractPlayer/Zone/CardType/Filters/Duration/Condition
│   │       ├── EffectTemplateCsvGenerator.java  ← Reads compiled JSON → Parser → generalise → split compound effects → write generic/unique CSV pair
│   │       ├── CardChecklistCsvGenerator.java   ← Reads compiled cards → EffectLexer → collects UNKNOWN tokens → writes csv/card_checklist.csv
│   │       ├── LexerCoverageReport.java         ← Runs EffectLexer on all CSV templates; reports fully-tokenised % and top unknown tokens
│   │       └── Report.java                      ← Shared CSV row record used by LexerCoverageReport
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
            └── EffectLexerTest.java
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
- `CardData.color` stores the **primary** color only for CMU — multi-color leaders (e.g., Red/Green) store the first color token; full `List<Color>` support is deferred (requires deck validation changes not needed for CMU)

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

### Phase 2 — Abilities, Effects, and Keywords *(in progress)*

**Complete:**
- `AbilityData` wired end-to-end: `CardCompiler` emits typed ability JSON, `CardDatabase` deserializes into `List<AbilityData>`, `AbilityFactory` hydrates runtime `Ability` objects on every card, `Card.hasKeyword()` works as a single-line predicate
- Keyword system implemented and tested:
  - **Rush**: character can attack the turn it is played
  - **Blocker**: can intercept attacks targeting another card (defend window)
  - **Double Attack**: deals 2 life damage on a successful attack
  - **Banish**: target life card goes to trash instead of opponent's hand
  - **Unblockable**: opponent's blocker window is skipped entirely
- CLI defend window: sequential blocker → counter loop → single `BattleSystem.resolve()`
- Card text corpus analysed (3,115 cards) → `effect_templates_generic.csv` + `effect_templates_unique.csv`; `EffectType` enum redesigned to 23 types; `EFFECT_GRAMMAR.md` written
- `EffectLexer` word-level scanner backed by `EffectTables` (shared phrase→TokenType map): longest-phrase-first matching; bracket detection for `[Keyword]`/`{Type}`/`(Attribute)`/`"TypeName"` filters; 7 regex patterns for numeric cost/power filters; normalisations for K.O., en-dash, and `"TypeName"` quoted spans
- `EffectTemplateCsvGenerator`: reads compiled JSON, re-runs `Parser.parse()`, generalises effect text, splits compound effects on `•`/`. Then, `/`If X, Y.`, strips modal trigger labels, deduplicates on `(simplified, trigger)`, writes generic/unique CSV pair
- `LexerCoverageReport`: runs `EffectLexer` on all CSV templates, reports fully-tokenised vs. UNKNOWN, lists top unknown tokens by frequency — current coverage **99.0% generic / 96.0% unique**
- `Parser.splitOnEmbeddedTriggers`: fixed `[Blocker] (explanation) [On Block] effect` being parsed as one ability; added `sawKeyword` tracking + parenthetical skip so the embedded trigger fires the split
- `Duration` enum added (`THIS_BATTLE`, `THIS_TURN`, `OPPONENTS_NEXT_TURN`, `PERMANENT`); `EffectData.duration` promoted from `int` to `Duration`
- `ParsedEffect` record: intermediate structured representation of a single parsed effect clause (tools pipeline; not part of game runtime)
- `MODIFIER` token type added to `TokenType` (10th value): `may` (optional action) and `must` (forced action); `EffectParser` reads the MODIFIER flag to populate `ParsedEffect.optional`
- `EffectParser` Phase 1 complete: `List<Token>` → `List<ParsedEffect>`; covers DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST, COST_MODIFY, ADD_DON, SET_DON_ACTIVE, GRANT_KEYWORD, PLAY_CARD, NEGATE, PREVENT_ATTACK, UNREST, ATTACH_DON; clause splitting on CONNECTOR; condition classification; optional flag from MODIFIER tokens; subject-first clause handling
- `CardCompiler.toEffectJson()` implemented: each non-KEYWORD ability's `effects[]` is now populated from `EffectParser.parse()` output; `buildTargetNode()`, `mapZone()`, `mapParsedCardType()` helpers produce engine-compatible field names
- `CardChecklistCsvGenerator`: called automatically at the end of `CardCompiler.main()`; writes `csv/card_checklist.csv` with columns `card_name`, `card_set_id`, `card_text`, `generic_card_text`, `unknown_tokens`, `needs_override`; deduplicates across sets/decks/promos by `card_set_id`
- `CardType.fromValue()` `@JsonCreator` added: case-insensitive Jackson deserialization (`"Character"` → `CHARACTER`) so compiled JSON written before the enum rename round-trips correctly

**Remaining:**
- **Trigger**: activates when a life card is revealed, at no DON cost
- **Counter**: actual boost value from `CardData.counter()` (currently a placeholder returning 0)
- **Rush: Character**: Leader ability granting Rush to characters played that turn (requires effect system)
- Custom effect template parser: map each unique card text pattern to a deterministic `EffectData` object using regex templates (no NLP / external dependencies)
- Full `Condition` / `Cost` / `EffectData` evaluation system
- Event card resolution: play → resolve effect → move to trash
- `[Activate:Main]` effects: player-activated abilities during the Main Phase

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

### Phase 3 — Graphics / GUI
- `GameStateClient` subclass in JavaFX: connects to the same local socket as the Tmux board renderer and renders game state graphically
- Render the board: character area, hand, life zone, cost zone, trash, stage
- Card display with images (`card_image` / `card_image_id` already in compiled JSON), power, cost, and rest/active state
- Animated zone transitions (play card, attack, draw, life reveal)
- `CliController` + Tmux layout kept as a fallback / debug mode; the socket layer means both can run simultaneously

### Phase 4 — Training Mode
- **Tutorial**: guided walkthrough of rules and actions
- **Self vs Self**: control both sides from one machine
- **Self vs AI**: automated opponent with configurable difficulty and playstyle

### Phase 5 — Battle Mode
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

*This project is in active development. Phase 1 (CMU Complete) is done — the core rules engine, cost enforcement, card data pipeline, and ability parser are all in place. Phase 2 is in progress: keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable) are implemented and tested; the full tools pipeline (EffectLexer → EffectParser → CardCompiler → compiled JSON) is operational with 99%/96% lexer coverage and EffectParser Phase 1 complete; Counter resolution, Trigger, Event effects, and [Activate:Main] ability evaluation remain. Phase 2.5 builds the Tmux UI and the local socket IPC layer that Phase 3's GUI will reuse directly — the CLI and GUI share the same state-broadcast infrastructure and have identical capabilities in different formats.*
