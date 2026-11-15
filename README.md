# One Piece Virtual TCG — `optcg`

A Java-based digital engine for the One Piece Card Game (OPTCG), built primarily as a **text-based learning and practice tool**: a place for new or returning players to learn the game by playing it correctly against a terminal opponent, and to practice against an AI whose difficulty can be tuned. The longer-term goal is for that AI's play to be grounded in real games played by real people, rather than hand-tuned heuristics alone — see [MVP.md](MVP.md) for what "done enough to call it a product" actually means, and why that's scoped well short of the full roadmap below. Secondary extensions explored further down this roadmap — play in the browser, game recording and replay, online play, simulating specific decks — are stretch goals that follow *after* the core learn/practice loop works, not prerequisites for it.

> This is an unofficial, non-commercial fan project. "One Piece Card Game," its card data, names, and text are the property of Bandai and the One Piece Card Game's respective rights holders. This project is not affiliated with or endorsed by them. Card data is sourced from the community-run [optcgapi.com](https://www.optcgapi.com) API, not from Bandai directly. The official rules this engine implements are published by Bandai as the [Comprehensive Rules](https://en.onepiece-cardgame.com/pdf/rule_comprehensive.pdf) — § references throughout this repo's docs point to that document.

---

## Current Status — Phase 2 *(complete)*

> **CMU (Command-line Minimum UI)** is the first milestone: a fully playable single game via the terminal with no graphics. Phase 1 and Phase 2 are complete. All 29 Phase 2 items are done — 370 tests green, 2032/2322 cards (87.5%) fully playable. Next is **v1**, the learn-and-practice MVP — see [Roadmap](#roadmap) and [MVP.md](MVP.md).

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
- `CardCompatibilityReport` tool: flags UNIMPLEMENTED_TRIGGER, UNIMPLEMENTED_EFFECT, UNIMPLEMENTED_COST, COMPOUND_COST, WHILE_IN_PLAY_DURATION, NO_ABILITIES; baseline: 2322 total cards, 2032 fully playable (87.5%); writes `csv/card_compatibility_report.csv`
- `[Activate:Main]` abilities wired in `CliController`: `GameState.canAffordActivation()` / `payActivationCost()` for all 9 `CostType` values; `CliController.activateAbility(Player)` with interactive cost display; compound activation costs (`List<Cost>`) checked atomically
- `[Opponent's Turn]` trigger dispatch: `TurnManager.advancePhase(DON)` calls `am.dispatchAll(OPPONENTS_TURN, opponent, opponent)` — fires for non-active player's field + leader; power/keyword boosts use `THIS_TURN` duration and clear via `refreshField()`
- `CostType.LIFE` split into `LIFE_TO_HAND` (place life → hand, Big Mom archetype) and `LIFE_TO_TRASH` (trash life cards); `CardCompiler` detects destination from cost text patterns

---

## Game Rules Summary

The table and bullets below are a condensed summary of what this engine implements — for the official source, see Bandai's [Rule Manual](https://en.onepiece-cardgame.com/pdf/rule_manual.pdf?20230623) (quick-start basics) and [Comprehensive Rules](https://en.onepiece-cardgame.com/pdf/rule_comprehensive.pdf) (full reference; § citations throughout this repo point here).

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

`raw/`, `compiled/`, `csv/card_checklist.csv`, and `csv/effect_templates_{generic,unique}.csv` are gitignored and not shipped in this repo — they're large, regenerable outputs that reproduce most of the scraped card-text database in bulk. Run `CardScraper` → `CardCompiler` locally to populate them; nothing in `engine/` depends on them being present at build time, only on `CardDatabase`'s runtime load path actually finding data in `compiled/`.

---

## Running Tests

```bash
mvn test
```

**Current test suite: 370 tests, 0 failures, 0 errors.**
10 `CardDatabaseTest` cases are data-dependent and skip automatically (via `assumeTrue`) when `raw/data/` is empty — run `CardScraper` first to populate. 1 shuffle test is probabilistically flaky by design (documented in the test).

| Test class | Tests | Coverage area |
|---|---|---|
| `BattleHandlerTest` | 40 | `canAttack`, `getValidAttackers`, `getValidTargets`, `resolve`, keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable, Trigger, Rush:Character) |
| `AbilityManagerTest` | 20 | `dispatch` for ON_PLAY, WHEN_ATTACKING, ON_KO, END_OF_YOUR_TURN, DON!! xX condition gate, once-per-turn, TRIGGER; `canAffordActivation`/`payActivationCost` for all 9 CostType values; compound costs (atomic check + payment) |
| `TurnManagerTest` | 10 | Phase transitions, turn count, Don draw counts, OPPONENTS_TURN dispatch for field + leader |
| `GameStateTest` | 18 | Draw, trash, play card, remove life, refresh, cost payment, field/stage limits |
| `GameSetupTest` | 4 | Initialization, mulligan, life setup |
| `CardTest` | 7 | Rest/activate, Don attachment and detach, summoning sickness, grantKeyword, clearGrantedKeywords, hasKeyword (permanent survives clear) |
| `EffectFactoryTest` | 139 | All EffectTypes, target resolution, all condition gates, GRANT_KEYWORD, SEARCH_DECK, PREVENT_ATTACK/KO/BLOCK, PLAY_CARD, MODAL, NEGATE, FLIP_LIFE, COUNTER, all Duration buckets (THIS_BATTLE/THIS_TURN/OPPONENTS_NEXT_TURN/WHILE_IN_PLAY), all new field/opponent conditions |
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

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full directory layout, data flow, who owns combat vs. state vs. presentation, and the invariants that keep them from drifting apart.

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

All 29 items complete. 370 tests green. 2032/2322 cards (87.5%) fully playable per `CardCompatibilityReport`.

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
- `CardCompatibilityReport` tool: offline analysis of `CardDatabase` against engine; flags UNIMPLEMENTED_TRIGGER, UNIMPLEMENTED_EFFECT, UNIMPLEMENTED_CONDITION, UNIMPLEMENTED_COST, COMPOUND_COST, WHILE_IN_PLAY_DURATION, NO_ABILITIES; writes `csv/card_compatibility_report.csv`; baseline: 2322 cards, 2032 fully playable (87.5%)

### What's next: versions

Phases 0–2 built the engine. From here the roadmap is organised by **versions**, each one something a player can actually use:

| Version | What a player gets | Status |
|---|---|---|
| **v1 — MVP** | Learn and practice in the terminal: real decks, an AI opponent with difficulty levels, a move advisor and a short walkthrough | Not started — see [MVP.md](MVP.md) |
| **v2 — Play in the browser** | The same game against the AI in a web page, replacing the planned Tmux UI and JavaFX GUI | Planned |
| **v2.1 — Game recording** | Every game saved as a replayable log; the data the v4 AI learns from | Planned |
| **v3 — Deck builder & online play** | Build and rate decks; play friends online; tournaments | Planned |
| **v4 — Stronger AI** | Search- and learning-based opponents (OpenSpiel), and "play like this deck" simulation | Planned; needs v2.1 data |

### v1 — MVP: learn and practice in the terminal

The full definition, with a checkable definition of done, is in [MVP.md](MVP.md). Build order:

1. **Player controller layer.** Today every decision is read from the keyboard inside `CliController`. Add a `PlayerController` interface (human CLI, AI, scripted) and a legal-move list the engine generates. The AI, the move advisor, the full-game test and the v2 web client all sit on top of this, so it comes first. Merge the two `Scanner`s on `System.in` (`Main` and `InputHandler`) into one input reader at the same time; two readers break piped and scripted input.
2. **Full-game test.** An automated test that plays a whole game through the real engine entry points to a declared winner. Unit tests didn't catch that every turn 1–2 menu choice ran the wrong action; this would have.
3. **Engine completion, scoped to the starter decks.** Every card in the 4–6 v1 decks must be fully playable (100%), rather than chasing all 2,322 cards. The remaining engine work:
   - `MAIN` trigger dispatch (231 cards): `[Main]` event effect cards; `CliController.runMainPhases()` needs a "Play Event" path
   - `OPPONENTS_ATTACK` trigger dispatch (42 cards): fires after opponent's `WHEN_ATTACKING` resolves
   - `ON_BLOCK` trigger dispatch (11 cards): fires when a Blocker is declared
   - `WHILE_IN_PLAY` recalculate-on-query layer: `getPowerWithEffects()` scans active continuous-effect abilities on each call instead of stale buckets
   - Replacement effects: interrupt hook in `BattleSystem` + `GameState` for "if this would be K.O.'d… instead" effects
   - Passive-global `NEGATE` (e.g. Teach OP09-081, Roger OP13-064): negate all `[On Play]` effects globally, not just on one targeted card; needs a permanent-effect layer checked at dispatch time in `AbilityManager.dispatch()`
   - `CardCompiler` compound-cost pattern extraction (4 cards): `EffectParser` does not yet recognize every compound cost pattern from card text

   `CardCompatibilityReport`'s current baseline (2322 cards, 2032 playable) is entirely UNIMPLEMENTED_TRIGGER (286: MAIN×231, OPPONENTS_ATTACK×42, ON_BLOCK×11, 2 others) and COMPOUND_COST (4). Only the gaps that touch the starter decks block v1; the rest can follow.
4. **Real-card decks.** Replace the 12 synthetic decks in `GameFactory` with 4–6 real starter decks, shipped as **card-ID lists only**. Card data is fetched and compiled on first run, so no card text lives in this repo.
5. **Heuristic AI opponent.** Greedy move selection over the legal-move list, scored on board, life, DON and card advantage. No training data needed.
6. **Difficulty levels.** Vary lookahead depth, how often the AI picks a weaker move on purpose, and how aggressively it attacks and blocks.
7. **Move advisor.** The AI's scoring, exposed as "what's the best move here and why".
8. **Short walkthrough.** A scripted first few turns that explain each menu and rule as it comes up.

**Built so v4 can plug in later.** Three design rules for v1, so OpenSpiel needs a bridge rather than a rewrite:
- **Numbered actions:** every legal move at a decision point is one entry in a list.
- **Cloneable, seeded state:** `GameState` can be deep-copied and every shuffle uses a seed, so a search can try a move and rewind.
- **Per-player views:** a player's view hides the opponent's hand, life cards and deck order. OPTCG is a hidden-information game, and both the AI and OpenSpiel must only see what that player could see.

### v2 — Play in the browser

Replaces the earlier Tmux UI (old Phase 2.5) and JavaFX GUI (old Phase 5) plans with one web client. It keeps their core idea: after every state change, the engine broadcasts a JSON snapshot of the game, and clients render it.

```
GameEngine ──► GameState ──► GameStateServer (WebSocket, JSON snapshots)
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                  Browser board          Terminal client
                (React; plays vs AI)    (CLI, kept for debugging)
```

- Single-player against the v1 AI, hosted alongside the project site
- Card names, costs and power shown as text; **no card art**, keeping with the disclaimer above
- The old Tmux design notes stay in [memory/phase2.5.md](memory/phase2.5.md) for reference

### v2.1 — Game recording

- Save every game as an action log (seed + decks + each chosen action), which replays exactly through the engine
- Replay any previous match, pause it, and take over either side
- Opt-in, anonymous collection from the web client; this is the dataset v4 depends on. No public OPTCG game-log dataset exists, so starting early matters

### v3 — Deck builder & online play

- Deck builder with legality checks and a deck rating (curve, removal density, keyword density, DON ramp)
- Online play: vs friend (gamer tag), vs local (region matchmaking), vs global
- Tournament mode: bracket-style matchups
- Needs accounts and a hosted game server, so it's the largest jump in hosting cost

### v4 — Stronger AI and simulation

- **Search-based AI in Java first:** Information Set Monte Carlo Tree Search (ISMCTS), running directly on the v1 cloneable state. Plays hidden-information games well and needs no training data or Python.
- **OpenSpiel for training:** wrap the engine as an OpenSpiel game (a Python `pyspiel` game backed by the Java engine through the v2 server, or a JSON action protocol). Use it to train policies with self-play (e.g. PPO or NFSP) and with behaviour cloning from v2.1 recordings, then export them back to Java (e.g. ONNX). Training runs offline; the game itself never depends on Python.
- **Deck-archetype simulation:** "play like a Red Zoro aggro list", trained on recorded games pooled by decklist
- **Take over a saved game:** load any point in a v2.1 recording and let the AI play the other side from there

**Data caveat:** behaviour cloning and archetype simulation need real played games, which only exist once v2.1 has been collecting for a while. Simulating one specific person's style is data-starved enough that it isn't recommended at all.

---

## License

The engine's source code is MIT-licensed — see [LICENSE](LICENSE). "One Piece Card Game" and all card data/names/text belong to Bandai and are not covered by this license; see the disclaimer at the top of this README.

---

See [DEVLOG.md](DEVLOG.md) for a full account of what was built manually, what was AI-assisted, the key prompts used, and what changed through iteration.

*This project is in active development. Phase 1 (CMU Complete) is done — the core rules engine, cost enforcement, card data pipeline, and ability parser are all in place. Phase 2 is complete: all 29 items done, 370 tests green, 2032/2322 cards (87.5%) fully playable; keyword semantics (Rush, Blocker, Double Attack, Banish, Unblockable, Trigger, GRANT_KEYWORD), the full tools pipeline (EffectLexer → EffectParser → CardCompiler → compiled JSON) with 99%/96% lexer coverage, ACTIVATED_MAIN interactive abilities, OPPONENTS_TURN dispatch, compound costs, and the CardCompatibilityReport playability baseline are all in. Next is v1, the learn-and-practice MVP (see [MVP.md](MVP.md)); none of its items have been started. The end-to-end match-flow validation (playing a full game through to a win condition via the CLI, confirming the whole turn/phase/battle/trigger sequence holds together) also has not been done yet — unit-test coverage and integration/playthrough correctness are separate claims.*
