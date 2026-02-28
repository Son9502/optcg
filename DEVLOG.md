# OPTCG — Development Log

This log documents the decision-making process behind each phase: what was designed and written manually, what was AI-assisted, where the line between the two was drawn, and what changed through iterative prompting. It is intended to demonstrate both manual engineering judgment and prompt engineering craft.

**Tag convention used in git commits:**
- `[manual]` — designed and written by hand with no AI involvement
- `[ai-assisted]` — AI-generated output reviewed and accepted with minimal changes
- `[collab]` — iterative back-and-forth: user defined the problem and constraints, AI produced code, user reviewed, corrected, and re-prompted until the result was correct

---

## Phase 0 — CMU Prototype

**Goal:** Prove the core data model and game loop work before adding any rules logic.

### Manual work

The entire project scaffolding was built manually before any AI was introduced:

- Maven project structure and `pom.xml`
- Package layout (`engine.cards`, `engine.zones`, `engine.player`, `engine.core`, `engine.data`, `tools`)
- Initial enumerations: `CardType`, `Color`, `Attribute`, `Rarity`
- `CardData` as an immutable record with `@JsonProperty` annotations for Jackson mapping
- `CardScraper` — a standalone tool that hits the OPTCG API, paginates through all card sets, and writes raw JSON to `src/main/resources/raw/data/`
- Initial `CardDatabase` — loads JSON files into a `HashMap<String, CardData>` keyed by `card_set_id`
- Data record stubs: `AbilityData`, `EffectData`
- Preliminary game classes: `Zone`, `Deck`, `Card`, `Leader`, `Player`
- `GameEngine` class skeleton

These were all written from scratch. The data model decisions — what a `CardData` record should contain, how `Zone` should be backed, what a `Player` owns — were made by the developer before any AI session began.

### Where AI was introduced (collab)

Once the skeleton existed, Claude Code was brought in to help with implementation velocity. The first meaningful collaboration was the `Zone` backing structure:

**Key prompt:**
> "Zone is currently backed by a List. We need add() to put cards on top and draw() to pull from the top. What's the right backing structure?"

**Result:** Switched to `ArrayDeque`. The developer accepted the suggestion after understanding the LIFO semantics — `addFirst` / `removeFirst` maps directly to how card zones work (top of deck, top of life pile). The `addBottom()` method was added as a consequence to support card effects that place cards at the bottom of the deck.

**What changed through iteration:**
- First implementation used `LinkedList` — rejected because `ArrayDeque` is more efficient and conveys the intent more clearly
- `draw()` returning `null` on empty (not throwing) was explicitly specified in the prompt; AI initially used an exception

### First AI-heavy commit (`e74b2af` — "Claude cleanup and insertion of TestCases for CMU")

This was the first session where the developer handed off an existing codebase and asked Claude to add test coverage and clean up inconsistencies. The output was reviewed line-by-line before committing. The commit message honestly reflects this.

---

## Phase 1 — CMU Complete

**Goal:** Fully playable two-player CLI game: cost enforcement, field limits, summoning sickness, card data pipeline, ability parser.

### Key architectural decision: BattleSystem separation

**Manual decision:** The developer recognised early that mixing game logic into `CliController` would make the engine untestable and tightly coupled to the UI. This was a deliberate upfront constraint given to Claude in every relevant prompt.

**Prompt pattern used throughout:**
> "CliController is UI-only. All game logic goes in BattleSystem / GameState. The controller calls the system and renders the result — it does not compute anything itself."

This boundary was enforced consistently. Every time AI-generated code placed logic in `CliController`, it was rejected and re-prompted.

### Cost enforcement (collab)

**Prompt:**
> "playCard should enforce DON cost: count non-rested DonCards in the cost zone, if fewer than card.cost() reject the play and leave the card in hand, otherwise rest exactly card.cost() of them."

**What changed through iteration:**
- First version rested all DonCards regardless of count — incorrect, rejected
- Second version rested correct count but counted both rested and active DonCards — rejected
- Third version filtered for `!isRested()` before counting and resting — accepted

The test suite for cost enforcement (`testPlayCard_insufficientDon`, `testPlayCard_exactDon`, `testPlayCard_surplusDon`, `testPlayCard_zeroCost`) was written manually first, then the implementation was prompted to make those tests pass (test-driven prompting).

### Field and stage limits (collab)

**Prompt:**
> "Field has a max of 5 characters. Stage has a max of 1. If the limit is reached, reject the play and leave the card in hand — no exception, no silent drop."

**Key constraint stated in prompt:** "reject silently from the caller's perspective — the card stays in hand, nothing is thrown." This prevented the AI from using exceptions as flow control, which would have broken the CLI menu loop.

### Summoning sickness (collab)

**Prompt:**
> "Characters cannot attack on the turn they are played. Add a summonedThisTurn flag to Card that is set true when a character is played and cleared during REFRESH. BattleSystem.canAttack() should check this flag."

The developer specified the flag name, where it is set, and where it is cleared — AI wrote the implementation. The test (`testSummoningSickness_preventAttack`, `testSummoningSickness_canAttackNextTurn`) was manually specified as the acceptance criteria.

### CardCompiler and Parser (collab)

**Problem:** Raw API JSON contains verbose card text with no structure. The engine needs structured ability data.

**Prompt:**
> "Write a CardCompiler that reads raw JSON from raw/data/, deduplicates by card_set_id (keeping base over SP/Parallel variants), strips fields the engine doesn't use, and appends an abilities[] array. Abilities should be parsed from card_text."

**Ability structure prompt (critical):**
> "The Parser should extract abilities into this structure: `{trigger: when it fires, condition: what must be true, cost: what you pay, character: who/what group is affected, effect: what happens}`. This maps to the OPTCG card text grammar without NLP — use regex templates."

**What changed through iteration:**
- Initial Parser used broad regex patterns that conflated triggers and conditions — rejected
- The developer specified example card texts and expected parsed outputs; the Parser was re-prompted to match them
- `[DON!! xX]` being a condition (§8-3-2-3), not a cost, was a ruling the developer found in the official PDF and corrected the AI's first interpretation

### CardData subTypes field + CardDatabase secondary indexes (collab)

**Prompt:**
> "Add a subTypes field to CardData mapped from `sub_types` in the JSON. Normalize null and the string `\"null\"` to empty string in the compact constructor."

**Index design prompt:**
> "Add a getByName() method to CardDatabase. It should be O(1), case-insensitive, and return a List because the same card name appears across multiple sets."

**Critical sub-type design decision (developer reasoning, not AI):**

When designing `getBySubtype()`, the developer explicitly rejected a tokenized approach:

> "Sub-types in the API are space-separated, but individual subtypes are multi-word (e.g., 'Straw Hat Crew', 'Fish-Man Island'). Splitting on spaces produces garbage tokens. Use `String.contains()` at query time instead of building an inverted index."

The AI's first response built an inverted index splitting on spaces. This was incorrect and required a re-prompt after the developer explained the data structure. The Javadoc comment in `getBySubtype()` explicitly documents why this design choice was made.

**Multi-color leaders:** The developer noted that the API returns `"Red Green"` for dual-color leaders. `ColorDeserializer` takes the first valid token. Multi-color support is intentionally deferred — storing `List<Color>` requires changes to deck validation, the CMU scope doesn't need it, and the developer chose not to over-engineer.

### CsvGenerator (collab)

**Prompt:**
> "Create a CsvGenerator in tools/ that reads compiled JSON (or falls back to raw if compiled is empty), applies two name-cleaning rules — strip `(...)` parenthetical suffixes and strip ` - SETID` trailing patterns — deduplicates on (name, cardType), and writes a sorted CSV."

**Developer-supplied cleaning rules:** The parenthetical pattern and the ` - SETID` pattern were discovered by the developer manually inspecting the CSV. The regex patterns were specified in the prompt; the AI wrote the implementation. The deduplication conflict resolution rule (keep longest sub_types string) was also developer-specified.

### asText() deprecation fix (ai-assisted)

**Prompt:**
> "Fix the Jackson asText() deprecation warnings. `asText(null)` → `textValue()`, `asText(\"\")` → `asText()`. Apply to CardCompiler and CsvGenerator."

This was a straightforward replacement with no iteration needed. Classified as `[ai-assisted]`.

### Test-driven approach throughout Phase 1

The developer's pattern across Phase 1 was consistent:
1. Write the test cases first (manually or via explicit prompt specifying the assertions)
2. Prompt the implementation to make those tests pass
3. Review the implementation for correctness, not just test passage (a test can pass for the wrong reasons)
4. Add edge-case tests for any scenario the implementation handled unexpectedly

This produced a test suite of 102 tests with 0 failures where every test reflects a deliberate game rule or API contract, not an artifact of how the code happened to be written.

---

## Phase 2 — Abilities, Effects, and Keywords *(in progress)*

**Goal:** Wire structured ability data end-to-end: from compiled JSON through `CardData` into runtime `Ability` objects, then enforce keyword semantics (Rush, Blocker, Double Attack, Banish) in `BattleSystem`.

### Ability data model — one-pass wiring (collab)

**Problem:** `AbilityData` and `EffectData` existed as stubs with no Jackson annotations. `CardData` had no `abilities` field. `AbilityFactory` was unimplemented. `CardCompiler` dumped `ParsedAbility` as unstructured strings rather than typed JSON.

**Design decision (developer):** Rather than a gradual rollout, the developer specified a single coordinated change across all layers to avoid intermediate states where the type system is inconsistent.

**Prompt pattern:**
> "In one pass: add `@JsonProperty` to every `AbilityData` and `EffectData` component, add `List<AbilityData> abilities` as the 16th field of `CardData` with null-to-empty normalization, rewrite `CardCompiler.toAbilityJson()` to emit typed AbilityData JSON, implement `AbilityFactory`, add `Card.hasKeyword()`, add `TestUtils.makeCardWithKeyword()`, and update every `new CardData(...)` call site to pass `null` as the 16th arg."

**What changed through iteration:**
- Jackson requires `@JsonProperty` on each record component for the canonical constructor to be used during deserialization; the first attempt omitted these annotations and deserialization silently produced empty lists
- `CardCompiler` lives in the `tools` package and must not import engine classes — the solution was to write JSON nodes manually using string field names matching the `@JsonProperty` values rather than calling `mapper.valueToTree(abilityData)`
- `BattleHandlerTest` had a pre-existing brace error in a stub method that surfaced only once the file was touched for the `CardData` constructor fix; corrected as part of the same pass

**Key design ruling (developer):** `Keyword` is stored directly on `AbilityData` (non-null only when `triggerType == KEYWORD`). Encoding the keyword as a string and parsing it at runtime was rejected — the engine should never re-parse data it already parsed at compile time.

**Result:** `CardDatabase.getCardData()` returns fully hydrated `CardData` with deserialized `List<AbilityData>`; `CardFactory` calls `AbilityFactory` on every card; `Card.hasKeyword(Keyword.RUSH)` works as a single-line predicate in `BattleSystem`.

### Test infrastructure — @BeforeEach refactoring (collab)

**Prompt:**
> "Check each test file and add a `@BeforeEach setUp()` function where applicable, so future test additions don't require repeating shared setup."

**Developer constraint:** `@BeforeEach` not `@BeforeAll` — every test in the suite touches mutable state (`Player`, `GameState`, `TurnManager`). Sharing instances across tests would cause order-dependent failures. `CardDatabaseTest` correctly keeps `@BeforeAll` for the expensive DB load.

**Files refactored:** `GameStateTest` (removed 3-line boilerplate from 14 tests), `TurnManagerTest` (4-line boilerplate from 6 tests, standardised `mockGameState` → `gameState`), `CardTest`, `DeckTest`, `ZoneTest` (converted field initialisers to explicit `@BeforeEach`), `PlayerTest`.

**Result:** 102 tests, 0 failures. New tests in any of these files require only `@Test void myTest() { ... }` — shared state is already wired.

### Keyword semantics — implementation (collab + manual tests)

**Keywords implemented:** RUSH, BLOCKER, DOUBLE_ATTACK, BANISH, UNBLOCKABLE. TRIGGER and RUSH_CHARACTER deferred to Phase 3 (require the effect execution system).

**Implementation split:**
- `GameState.playCard()` — RUSH skips `setSummonSick(true)` when the played card has the keyword (collab)
- `GameState.removeLifeBanished()` — new method routing the top life card to trash instead of hand when BANISH is active (collab)
- `BattleSystem.resolve()` — BANISH and DOUBLE_ATTACK logic wired into the leader-hit path (collab)
- `BattleSystem.getValidBlockers()` / `getValidCounters()` — new query methods for the defend window (collab)
- `TestUtils.attack()` — helper updated with BLOCKER redirect and UNBLOCKABLE bypass; `makeCardWithKeyword()` given `CardType.Character` so `playCard()` routes it correctly (collab, fixing bugs found by reviewer)

**Tests:** The keyword test stubs in `BattleHandlerTest` were written manually by the developer first. Several had logical errors (wrong player zones checked in `testBanishKeyword`, attacker power too low in `testDoubleAttackKeyword`, no life cards seeded in `testUnblockableKeyword`, UNBLOCKABLE check applied to the wrong card in `TestUtils.attack`). These were identified through review and corrected iteratively.

**RUSH vs RUSH_CHARACTER clarification (developer):** The developer identified that both values are needed in the `Keyword` enum despite appearing similar. `RUSH` is a self-keyword on Character cards. `RUSH_CHARACTER` maps to the card text "Rush: Character" which appears on Leader cards as a triggered effect that grants RUSH to characters played that turn — a different concept requiring the Phase 3 effect system.

**Result:** 107 tests, 0 failures (BattleHandlerTest grew from 20 → 25).

### GameFactory keyword distribution (collab)

**Problem:** The test deck had no keyword-bearing cards, making CLI keyword testing impossible without manually constructing edge cases.

**Change:** Added `addCardsWithKeyword()` helper to `GameFactory`. Keyword spread across the character tiers:

| Card | Cost | Keyword |
|---|---|---|
| Pirate Grunt | 1 | RUSH |
| Pirate Captain | 3 | BLOCKER |
| Pirate Admiral | 5 | DOUBLE_ATTACK |
| Yonko Officer | 6 | BANISH |
| Yonko | 7 | UNBLOCKABLE |

Each keyword card has the `AbilityData` set in both `CardData.abilities()` and the card's runtime ability list so `hasKeyword()` works correctly.

### defend() — CliController (manual + collab review)

**Initial implementation (manual):** The developer wrote the `defend()` method from scratch — a dynamic numbered menu covering three options (take attack / block / counter) built from runtime availability checks.

**Review findings (collab):** Six bugs identified in the draft:
1. `opponent` variable undefined in scope — should be `otherPlayer`
2. `case 2` missing `break` — fell through to `case 3`, calling `battleSystem.resolve()` twice
3. Dynamic option numbers (1/2/3) did not match hardcoded switch cases when some options were absent
4. Double card selection in `case 2` — `selectCard()` then a second `readInt()` discarded the first choice
5. Chosen blocker was never rested
6. UNBLOCKABLE not checked before offering the blocker menu

**Restructure (collab):** Replaced the menu-based switch with a sequential confirm-based flow:
1. UNBLOCKABLE check → offer blocker via `confirm()` if available → rest chosen blocker, redirect `target`
2. Counter loop: repeat `confirm()` while counters remain, accumulate `counterBoost`
3. Single `battleSystem.resolve()` against the final target

**Developer judgment:** The sequential confirm design was preferred over a renumbered dynamic menu because it eliminates the numbering mismatch class of bug entirely and mirrors how the defend window actually plays out at a real table (blocker decision first, then counters).

### Counter resolution — applyCounter() and playCounter() (collab)

**Initial implementation (manual):** The developer wrote the Counter changes across `BattleSystem` and `GameState`:
- `getValidCounters()` changed from two separate `if` blocks (could double-add a card with both `counter > 0` and a KEYWORD) to `if/else if`, with the TRIGGER check replaced by `Keyword.COUNTER`
- `applyCounter()` rewritten with a `switch` on `cardType` — Character returns `counterCard.getData().counter()`, Event delegates to `gameState.playCounter()`, with `gameState.trash(defender, counterCard)` called after the switch for both paths
- `GameState.playCounter()` changed from `void` to `int` (returns 0 placeholder) to support the Event counter return path

**Review finding (collab):** `TestUtils.makeCard()` creates cards with `null` cardType. Java's `switch` on an enum throws NPE on null, causing `resolve_counterBoostSavesDefender` to fail.

**Fix:** Replaced the `switch` with an if-else chain that guards for null — a null cardType falls through to the Character path and uses `counterCard.getData().counter()`. This is safe because generic test cards represent Characters and will always have a `counter()` field value.

**Result:** 107 tests, 0 failures. Character counter value is now fully resolved at runtime; Event counter value is correctly deferred to `gameState.playCounter()` (stub, pending Phase 3 effect system).

---

## Prompt Engineering Patterns Used

These patterns recurred throughout the project and proved effective:

| Pattern | Description | Example |
|---|---|---|
| **Constraint-first prompting** | State what the output must NOT do before what it must do | "CliController is UI-only. No logic." |
| **Test-driven prompting** | Write failing tests manually, then prompt to make them pass | Cost enforcement tests written before implementation |
| **Domain knowledge injection** | Provide rulebook citations when the AI's interpretation is wrong | "§8-3-2-3: [DON!! xX] is a condition, not a cost" |
| **Rejection with explanation** | When AI output is wrong, explain exactly why before re-prompting | Explained why tokenizing sub_types on spaces is wrong |
| **Scope pinning** | Explicitly name the files and methods to change; explicitly name what to leave alone | Naming exact call sites for `new CardData()` constructor fixes |
| **Incremental complexity** | Start with the simple case, add edge cases only after the base case is solid | Zero-cost card play → exact-cost → surplus → insufficient |

---

## What Was Never AI-Generated

- The overall phased architecture (CMU → Tmux → GUI → AI → Online)
- The data model design (Zone/Deck/Player ownership, Deque semantics)
- The CardScraper (API pagination, rate limiting, raw JSON storage)
- The decision to separate BattleSystem from CliController
- The CardData record field selection (what the engine actually needs vs. API noise)
- The [DON!! xX] condition vs. cost ruling (found in the official PDF)
- The multi-color deferral decision (out of scope for CMU)
- The sub_types tokenization analysis (developer identified the multi-word problem)
- The Tmux 3-pane layout concept
- The GameStateServer/GameStateClient socket architecture (developer specified the pattern; AI described implementation details)
