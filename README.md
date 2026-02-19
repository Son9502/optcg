# One Piece Virtual TCG — `optcg`

A Java-based digital engine for the One Piece Card Game (OPTCG). The long-term vision is a full-featured platform where players can battle locally or globally, train against themselves or an AI, observe and replay matches, and run strategic simulations — all built on top of a faithful rules engine.

---

## Current Status — CMU Prototype

> **CMU (Command-line Minimum UI)** is the first milestone: a fully playable single game via the terminal with no graphics. The prototype proves the core rules engine is correct before any UI or networking work begins.

**What works today:**
- Full turn cycle: REFRESH → DRAW → DON → MAIN → END
- MAIN phase menu: Attack, Play Card, Attach Don, View Hand/Field, End Phase
- Battle resolution: attacker vs. opponent's leader or rested characters
- Counter window during battle
- Mulligan on game start
- Life system: successful attack on leader removes a life card to opponent's hand; game ends when life is empty and leader is attacked
- Two pre-built test decks via `GameFactory` (Luffy/Red and Zoro/Green)
- 51 unit tests — all passing

**What is stubbed (not yet implemented):**
- Cost payment (`payCost`)
- Card-specific abilities and effects (Rush, Blocker, Trigger, Counter)
- Character summoning sickness (can't attack turn it's played) — rule exists in design, not enforced in engine yet
- Field character limit (5 per player)

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
- Play a card by paying its cost in DON (rest that many DON cards)
- Attack with your leader or any active (non-rested) character
  - Valid targets: opponent's leader, or opponent's rested characters only
  - Attacker rests after attacking; characters cannot attack the turn they are played
- Attach a DON card to your leader or a character (+1000 power **during your turn only**)
- Play Event cards for one-time effects (goes to trash)

**Win condition:** Successfully attack the opponent's leader when they have no life cards remaining.

**Key keywords:**
- **Rush** — Character can attack on the turn it is played
- **Blocker** — Can intercept an attack targeting another card
- **Trigger** — Activates when a life card is revealed (no DON cost)
- **Counter** — Played from hand when your card is being attacked; goes to trash afterward

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Build | Maven 3.8+ |
| Testing | JUnit 5 |
| JSON parsing | Jackson (for `CardDatabase`) |

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

## Running Tests

```bash
mvn test
```

**Current test suite: 51 tests, 0 failures.**

| Test class | Coverage area |
|---|---|
| `BattleHandlerTest` | `canAttack`, `getValidAttackers`, `getValidTargets`, `resolve` |
| `TurnManagerTest` | Phase transitions, turn count, Don draw counts |
| `GameStateTest` | Draw, trash, play card, remove life, refresh |
| `GameSetupTest` | Initialization, mulligan, life setup |
| `CardTest` | Rest/activate, Don attachment and detach |
| `DeckTest` | Draw, draw from empty, search |
| `ZoneTest` | Add, remove, draw top, move card, shuffle |
| `PlayerTest` | Zone initialization, leader assignment |

---

## Project Architecture

```
src/
├── main/
│   ├── java/
│   │   └── engine/
│   │       ├── Main.java                        ← Entry point
│   │       ├── battle/
│   │       │   └── BattleSystem.java            ← All combat logic
│   │       ├── cards/
│   │       │   ├── Card.java
│   │       │   ├── CardData.java                ← Immutable record; Jackson-mapped
│   │       │   ├── CardFactory.java             ← stub
│   │       │   ├── DonCard.java
│   │       │   ├── Leader.java
│   │       │   ├── abilities/                   ← Ability, Trigger, Condition, Cost (stubs)
│   │       │   ├── effects/                     ← Effect, EffectType, EffectFactory (stubs)
│   │       │   ├── keywords/                    ← Keyword (stub)
│   │       │   └── types/                       ← CardType, Color, Attribute, Rarity enums
│   │       ├── core/
│   │       │   ├── GameEngine.java              ← Main game loop
│   │       │   ├── GameState.java               ← All state mutations
│   │       │   ├── Phase.java                   ← Turn phase enum
│   │       │   └── TurnManager.java             ← Phase transitions, active player
│   │       ├── data/
│   │       │   └── CardDatabase.java            ← Loads card JSON into memory
│   │       ├── history/
│   │       │   ├── GameHistory.java
│   │       │   └── GameHistoryManager.java      ← Snapshot chain for replay/undo
│   │       ├── player/
│   │       │   └── Player.java
│   │       ├── setup/
│   │       │   ├── DeckBuilder.java
│   │       │   ├── GameFactory.java             ← Pre-built test decks (Luffy vs Zoro)
│   │       │   └── GameSetup.java               ← initializeGame, mulligan, setUpLife
│   │       ├── ui/
│   │       │   └── cli/
│   │       │       ├── CliController.java       ← All player-facing prompts and menus
│   │       │       └── InputHandler.java        ← readInt, confirm, readString
│   │       └── zones/
│   │           ├── Deck.java
│   │           ├── DonDeck.java
│   │           ├── Zone.java                    ← Deque-backed; add = top, draw = top
│   │           └── ZoneType.java
│   └── resources/
│       ├── compiled/data/                       ← Normalized JSON (CardDatabase reads this)
│       └── raw/data/                            ← Raw scraped card data (input to parser)
└── test/
    └── java/
        └── engine/
            ├── TestUtils.java                   ← Shared makeCardData / makeCard helpers
            ├── battle/BattleHandlerTest.java
            ├── cards/CardTest.java
            ├── core/GameStateTest.java
            ├── core/TurnManagerTest.java
            ├── player/PlayerTest.java
            ├── setup/GameSetupTest.java
            └── zones/DeckTest.java, ZoneTest.java
```

**Key architectural rules:**
- `BattleSystem` owns all game logic; `CliController` is UI-only
- `GameEngine.run()` drives the loop; non-interactive phases auto-advance via `TurnManager`
- `GameState` is the single source for all state mutations (move, draw, trash, attach, etc.)
- Don source zone for attachment = `cost` zone (not hand)
- Attack targets = opponent's leader + opponent's **rested** characters only
- `Zone` uses a `Deque` internally: `add()` = addFirst (top), `draw()` = removeFirst (top), `addBottom()` = addLast — supports both top and bottom card placement for card effects

---

## Roadmap

### Phase 0 — CMU Prototype *(current)*
Single-machine two-player CLI game with dummy decks. Validates the core rules engine.

### Phase 1 — CMU Complete
- Cost payment enforcement (rest DON cards to play characters)
- Character summoning sickness (cannot attack turn played)
- Field limit enforcement (5 characters max)
- Real card data pipeline: raw JSON → compiled JSON → `CardDatabase`

### Phase 2 — Abilities and Effects
- Implement keyword effects: Rush, Blocker, Trigger, Counter
- Wire `Ability` / `Effect` / `Condition` / `Trigger` system
- Event card resolution

### Phase 3 — Training Mode
- **Tutorial**: guided walkthrough of rules and actions
- **Self vs Self**: control both sides from one machine
- **Self vs AI**: automated opponent with configurable difficulty and playstyle

### Phase 4 — Battle Mode
- **Local play**: two players on one machine
- **Online play**: vs friend (gamer tag), vs local (region matchmaking), vs global
- **Tournament mode**: bracket-style matchups

### Phase 5 — Simulation and Replay *(TCG TV)*
- Record full game history; replay any previous match
- Pause replay at any moment and take over either side
- AI reconstruction: analyze opponent playstyle and simulate their decision-making
- Observe live or recorded games from other players

### Phase 6 — Story Mode *(future)*
- Choose a leader (Luffy, Zoro, …) and run a campaign
- Visual novel-style plot progression
- Progressive AI difficulty following the story arc
- Potential card unlocks through campaign progress

---

*This project is in active development. The CMU prototype is the proving ground — all design decisions made here (architecture, zone semantics, turn management, battle resolution) carry forward into every later phase.*
