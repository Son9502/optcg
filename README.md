# One Piece Virtual TCG — `optcg`

A Java-based digital engine for the One Piece Card Game (OPTCG). The long-term vision is a full-featured platform where players can battle locally or globally, train against themselves or an AI, observe and replay matches, and run strategic simulations — all built on top of a faithful rules engine.

---

## Current Status — CMU Prototype

> **CMU (Command-line Minimum UI)** is the first milestone: a fully playable single game via the terminal with no graphics. The prototype proves the core rules engine is correct before any UI or networking work begins.

**What works today:**
- Full turn cycle: REFRESH → DRAW → DON → MAIN → END
- MAIN phase menu: Attack, Play Card, Attach Don, View Hand/Field/Opponent Field, End Phase
- Battle resolution: attacker vs. opponent's leader or rested characters
- Counter window during battle
- Mulligan on game start
- Life system: successful attack on leader removes a life card to opponent's hand; game ends when life is empty and leader is attacked
- Cost payment enforcement: paying a card's DON cost by resting DON cards from the cost zone
- Summoning sickness: characters cannot attack the turn they are played
- Field character limit: 5 characters max per player; UI prompts to replace when full
- Stage card limit: 1 stage card per player; new stage replaces the old one
- Two pre-built test decks via `GameFactory` (Luffy/Red and Zoro/Green)
- Card data pipeline: raw scraped JSON → `CardCompiler` → compiled JSON → `CardDatabase`
- `Parser`: structured ability extraction from card text into `{trigger, condition, cost, character, effect}` records
- 93 unit tests — all passing

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
- **Rush: Character** (§10-1-6) — Character can attack opponent's characters (not just leader) on the turn it is played
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

`CardCompiler` deduplicates cards by `card_set_id` (keeping the base version over SP/Parallel variants), strips fields unused by the engine, and appends a structured `abilities[]` array to each card using `Parser`.

`CardDatabase` loads compiled JSON at startup. Cards are keyed by `card_set_id`.

---

## Running Tests

```bash
mvn test
```

**Current test suite: 93 tests, 0 failures.**

| Test class | Coverage area |
|---|---|
| `BattleHandlerTest` | `canAttack`, `getValidAttackers`, `getValidTargets`, `resolve` |
| `TurnManagerTest` | Phase transitions, turn count, Don draw counts |
| `GameStateTest` | Draw, trash, play card, remove life, refresh, cost payment |
| `GameSetupTest` | Initialization, mulligan, life setup |
| `CardTest` | Rest/activate, Don attachment and detach, summoning sickness |
| `DeckTest` | Draw, draw from empty, search |
| `ZoneTest` | Add, remove, draw top, move card, shuffle |
| `PlayerTest` | Zone initialization, leader assignment |
| `CardDatabaseTest` | Real card data loading, Leader/Character deserialization, CardFactory |
| `ParserTest` | Ability parsing: triggers, conditions, costs, keywords, multi-ability blocks |
| `GameFactoryTest` | Pre-built test deck construction |

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
│   │   │   │   ├── abilities/                   ← Trigger, Condition, Cost, AbilityData
│   │   │   │   ├── effects/                     ← EffectData, EffectType, EffectFactory (stubs)
│   │   │   │   ├── keywords/                    ← Keyword (stub)
│   │   │   │   └── types/                       ← CardType, Color, Attribute, Rarity enums
│   │   │   ├── core/
│   │   │   │   ├── GameEngine.java              ← Main game loop
│   │   │   │   ├── GameState.java               ← All state mutations
│   │   │   │   ├── Phase.java                   ← Turn phase enum
│   │   │   │   └── TurnManager.java             ← Phase transitions, active player
│   │   │   ├── data/
│   │   │   │   ├── CardDatabase.java            ← Loads compiled card JSON into memory
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
│   │       ├── Parser.java                      ← Parses card text into structured abilities
│   │       └── ParsedAbility.java               ← Record: trigger, condition, cost, character, effect
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
            └── ParserTest.java
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
- `tools/` is an offline pipeline; `CardScraper`, `CardCompiler`, and `Parser` run once at data-prep time and are not part of the game runtime

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
- Rulebook-correct keyword list: Rush, Rush: Character, Blocker, Double Attack, Banish, Unblockable, Trigger, Counter

### Phase 2 — Abilities, Effects, and Keywords
- Custom effect template parser: map each unique card text pattern to a deterministic `EffectData` object using regex templates (no NLP / external dependencies — ensures offline play and reproducible results)
- Implement the `Keyword` system as the first deliverable:
  - **Rush** / **Rush: Character**: character can attack on the turn it is played
  - **Blocker**: card can intercept an attack targeting another card
  - **Trigger**: activates when a life card is revealed, at no DON cost
  - **Counter**: played from hand during the opponent's attack; card goes to trash afterward
  - **Double Attack**: deals 2 damage on attack
  - **Banish**: target life card is trashed without activating its Trigger
  - **Unblockable**: opponent cannot activate Blocker against this card
- Wire the full `Ability` / `EffectData` / `Condition` / `Trigger` / `Cost` system
- Event card resolution (play, resolve effect, move to trash)
- `[Activate:Main]` effects: player-activated abilities during the Main Phase

### Phase 3 — Graphics / GUI
- Replace `CliController` with a graphical UI layer; the rules engine beneath stays unchanged
- Render the board: character area, hand, life zone, cost zone, trash, stage
- Card display with images, power, cost, and rest/active state
- Animated zone transitions (play card, attack, draw, life reveal)
- Design target: JavaFX or equivalent; `CliController` kept as a fallback / debug mode

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

*This project is in active development. Phase 1 (CMU Complete) is done — the core rules engine, cost enforcement, card data pipeline, and ability parser are all in place. Phase 2 begins wiring individual card effects using a custom deterministic template parser.*
