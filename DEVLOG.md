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

### Card text corpus analysis (collab)

**Problem:** `CardCompiler` always writes `"effects": []`. Before implementing `EffectParser`, the full range of effect patterns across the card set needed to be understood so the grammar could be designed against real data rather than assumptions.

**Approach:** Developer directed a Python analysis script that read all 48 compiled JSON files (2,836 cards, 3,326 ability blocks with body text), normalised effect bodies into generic templates, and classified them by effect type.

**Key findings:**
- 217 blocks are disclaimer/reprint noise — should be filtered before parsing
- The first word of every effect body is one of ~15 action verbs — the vocabulary is small and closed
- 1,294 blocks contain `and`/`then` (multi-effect chains); 724 start with `if` (conditional wrapper)
- True unclassifiable rate: ~6% — the rest follow predictable templates

**Output:** `effect_templates_generic.csv` (1,454 rows — parseable templates) and `effect_templates_unique.csv` (434 rows — complex/unique patterns) at project root. The generic CSV is the `EffectParser`'s spec; the unique CSV is the backlog of manual JSON overrides.

### EffectType enum redesign (collab)

**Developer reasoning:** The original 14-type enum included `DAMAGE` (belongs to the combat system, not card text) and `ADD_TO_HAND` (a result of other effects, not a standalone action). The corpus analysis revealed 9 missing types.

**Changes:**
- Removed: `DAMAGE`, `ADD_TO_HAND`
- Added: `POWER_REDUCE`, `COST_MODIFY`, `ADD_DON`, `SET_DON_ACTIVE`, `ADD_LIFE`, `GRANT_KEYWORD`, `PREVENT_ATTACK`, `PREVENT_BLOCK`, `PREVENT_KO`, `NEGATE`, `MODAL`
- Renamed: `COST_REDUCE` → `COST_MODIFY` (signed int: positive = cost increase, negative = cost decrease — both patterns exist in the card set)
- Renamed: `PLAY_FROM_HAND` → `PLAY_CARD` (covers hand, deck, and trash as source zones)

**Result:** 23 EffectTypes. 107 tests, 0 failures — neither removed type had references outside the enum itself.

### Effect grammar definition (manual)

**Developer observation:** All card effect text is built from the same small vocabulary joined by conjunctions and prepositions. Each effect body is one or more clauses sharing the shape: `[VERB] [QUANTITY?] [PLAYER?] [CARD_TYPE] [FILTER*] [ZONE?] [DURATION?]`. The action verb alone determines the `EffectType`; everything else fills `EffectData` fields.

**Key grammar decisions (developer):**
- `up to N` and `N or less` are synonymous in all contexts — both parse to `max=N`
- `COST_MODIFY` uses a signed int covering both cost reduction (opponent's card easier to target) and cost increase (harder to target)
- Color is a FILTER qualifier on targets, not a standalone condition: `"a purple {Big Mom Pirates} type Character"`
- Leader color (`IF_LEADER_COLOR`) is a distinct condition from leader type (`IF_LEADER_TYPE`)
- `IF_YOU_DO` is a follow-up condition token: the second clause fires only if the first resolved
- `MODAL` wraps two sub-lists of effects; requires a future `EffectData.choices` field before it can be fully serialised — deferred

**Output:** `EFFECT_GRAMMAR.md` at project root. Contains: full token vocabulary (9 token types), BNF grammar rules, verb → EffectType mapping with disambiguation, EffectData field mapping, and a catalogue of out-of-scope patterns.

**Lexer architecture decision (developer):** The lexer operates on multi-word phrases, not characters — longest-match-first over word sequences. `TokenType` enum and `Token` record will be written manually; phrase lookup tables and regex patterns within the lexer are collab territory.

### TokenType and Token — manual

**Developer wrote both files without AI involvement:**
- `TokenType` — 9-value enum (VERB, QUANTITY, PLAYER, CARD_TYPE, FILTER, ZONE, DURATION, CONDITION, CONNECTOR). Comments use OP TCG-specific examples.
- `Token` — Java record `(TokenType type, String raw, Object value)`. The `value` field is typed as `Object` to accommodate Integer (quantities, numeric filters), String (bracket keyword/subtype/attribute filters), and null (verbs, zones, connectors). The tradeoff — no compile-time type safety on `value` — was accepted because the downstream consumer (EffectParser) will always know the type from `type()`.

### EffectLexer skeleton (collab)

**Design constraints specified by developer before any AI prompting:**
- Operate on a word index, not a character index — split input first, then scan the word array
- Longest phrase first — pre-sort phrase keys by character length descending before the scan loop
- Skip unknown words rather than throwing — log them to `unknowns` for post-session inspection
- Bracket detection is word-array level — detect `[`, `{`, `(` as isolated array elements, find matching closer, join inner words

**Guidance given (collab):** Each iteration of the lexer body was reviewed and specific bugs flagged without rewriting the code for the developer:
1. `input.substring(position)` vs `words[position]` — position used as both a char offset and a word index; fix: match on word arrays with `matchesPhrase()`
2. `LIST_PHRASES.indexOf(phrase)` — reference equality on arrays, always returns -1, causes O(n²) accidental fallback; fix: index-based `for (int i = ...)` so both parallel lists stay in sync
3. `phrase.contains("N")` numeric extraction — phrase keys are real OP TCG text not templates; fix: handle QUANTITY digits with a separate `words[position].matches("\\d+")` guard before the phrase loop
4. Missing `if (!matched)` guard — phrase loop ran even after a filter match; fix: wrap phrase loop in the flag check
5. `new Matcher` stored as a field — Matcher is stateful and thread-unsafe; fix: store `Pattern` (compiled once, static final), create Matcher per `parseEffect()` call via `pattern.matcher(remainingText)`
6. `findClosingBracket` returning -1 on unclosed bracket — `position = -1 + 1 = 0` reset the loop to the start causing an infinite loop; fix: return `words.length` as fallback

**Input normalisation:** `"k.o."` contains periods that the split regex would shred into three tokens. Pre-normalised to `"ko"` before split; `"ko"` is the phrase table key. `isPunctuation` narrowed to `[.,;:]` only so `[`, `{`, `(` fall through to bracket detection.

### EffectTables (collab)

**Developer decision:** Rather than embedding the phrase table inline in EffectLexer, extracted all shared data to a separate `EffectTables` class. Motivation: EffectParser will need the same table without duplication, and keeping phrase definitions in one place makes additions and corrections easy.

**EffectLexer delegates entirely:**
```java
static {
    PHRASES_TABLE.putAll(EffectTables.PHRASES);
    SORTED_PHRASES.addAll(EffectTables.SORTED_PHRASES);
    LIST_PHRASES.addAll(EffectTables.LIST_PHRASES);
    FILTER_PATTERNS.addAll(EffectTables.FILTER_PATTERNS);
}
```

**Phrase table contents:** 9 token types, ~60 phrase entries covering all action verbs observed in the card corpus, QUANTITY modifiers, player references, all DON!! card type variants (multi-word preferred: "don!! cards" before "don!!", "your opponent's" before "your"), 15 source and destination zones, 6 durations, condition openers, and connectors. All entries use lowercased keys because `parseEffect()` normalises input to lowercase before scanning.

**Static block ordering:** All `PHRASES.put()` calls must complete before `SORTED_PHRASES` and `LIST_PHRASES` are derived from the key set. A bug in the initial draft reversed this order, producing empty derived lists; corrected in the final version.

**Filter patterns:** Seven regex Patterns for numeric FILTER clauses that cannot be expressed as literal phrase keys (cost and power thresholds with variable N). Longer/more-specific patterns listed first to prevent partial matches. `lookingAt()` used instead of `matches()` to anchor at the current position without requiring a full-string match.

### Test suite — TokenTest and EffectLexerTest (collab)

**Developer specified coverage requirements; AI generated the test code:**

`TokenTest` (8 tests): record field access, integer and String value retrieval, record equality on same/different type/raw/value, toString not null or empty.

`EffectLexerTest` (34 tests across 9 categories):
- Empty/blank input guards
- VERB detection (draw, K.O. normalisation, 2-word "choose one")
- QUANTITY detection (bare digit with int value, "up to" phrase, "all")
- PLAYER detection ("your opponent's" as single 2-word token, "your" in non-zone context)
- CARD_TYPE detection (2-word "don!! card" preferred over "don!!")
- FILTER — bracket detection for [keyword], {subtype}, (attribute) with String value
- FILTER — regex patterns with numeric value extraction (cost=4, power=5000, color literal)
- ZONE detection (from your deck, from your don!! deck preferred, as active)
- DURATION detection (during this battle, until the end of this turn)
- CONNECTOR detection
- Unknown word logging, punctuation skip
- 2 integration tests on full effect strings
- **6 CSV-sourced tests** using verbatim card text from `effect_templates_generic.csv` and `effect_templates_unique.csv`:
  - ADD_DON [Trigger]: "Add up to 1 DON!! card from your DON!! deck and set it as active."
  - ADD_DON [Main] with Then-if-power-filter chain: "Add up to 1 DON!! card... Then, if your opponent has a Character with 6000 power or more..."
  - DRAW + TRASH: "Draw 2 cards and trash 1 card from your hand."
  - REST + DRAW with cost filter: "Rest up to 1 of your opponent's Characters with a cost of 5 or less. Then, draw 1 card."
  - ADD_DON [On Play] two-verb chain: "Add up to 1 DON!! card from your DON!! deck and rest it."
  - COST_REDUCE [Passive] with if-condition: "If the number of DON!! cards on your field is at least 2 less than the number on your opponent's field, give this card in your hand -3 cost."

**One test failure during development (fixed):** `parseEffect_your_emitsPlayerSelf` initially used `"Draw 1 card from your deck."` — the "from your deck" ZONE phrase consumed "your" before a standalone PLAYER token could be emitted. Fixed by changing input to `"Rest your Characters."` where "your" appears in a targeting context with no competing zone phrase.

**Result:** 42 tests (34 EffectLexerTest + 8 TokenTest), 0 failures.

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

### EffectTemplateCsvGenerator (collab)

**Problem:** The original `effect_templates_generic.csv` and `effect_templates_unique.csv` were generated by a Python analysis script that could not reuse the Java `Parser`. After the Parser was fixed (multi-ability split, trigger detection), the CSVs needed to be rebuilt from Java so the output matched what the engine would actually see.

**Design decisions (developer):**
- Generic = `(simplified, trigger)` pair appearing on 2+ distinct cards; Unique = exactly 1 card — a clean corpus/backlog split
- Compound effects split before generalising: `•` bullet-point modal choices, `. Then, ` sequential chains, `If X, Y.` simple conditionals each produce one CSV row
- Modal trigger labels (`[On K.O.]`, `[Trigger]` etc.) embedded after `/` or `•` separators are stripped from the simplified description — the trigger is captured in the outer ability's `trigger` field and should not appear in the effect body
- Disclaimer/errata boilerplate stripped from `card_text` before `Parser.parse()` — three observed formats: `Disclaimer:` (sentence-case), `DISCLAIMER:` (upper-case), `"This card has been officially errata'd."` (inline sentence)
- Effect classification (`classifyEffect`) strips leading `/`/`•` before checking keywords, so modal sub-choices are classified by action type (KO, DRAW, …) not as MODAL

**Result:** 779 generic / 1,133 unique entries from 3,115 cards; 55 manual-override rows remaining in generic.

### Parser — Blocker + On Block inline split fix (collab)

**Bug:** `[Blocker] (explanation) [On Block] effect` on a single line was parsed as ONE ability instead of two. Root cause: the paren explanation ends with `)` not `.`, so the `afterSentence` guard in `splitOnEmbeddedTriggers` never fired for the subsequent `[On Block]`.

**Fix:** Added `sawKeyword` flag to `splitOnEmbeddedTriggers`. After a keyword bracket is consumed, the flag is set and the lexer actively skips the following `(…)` parenthetical by advancing `leadingEnd` past it. A guard `if (start < leadingEnd) continue` prevents any bracket inside the consumed paren from being processed. When the next leading-cluster bracket is a trigger and `sawKeyword` is true, the split fires.

**Test added:** `parse_blockerAndOnBlock_inlineSplit` — verifies two abilities, first with `trigger="Keyword"` and `isKeyword()=true`, second with `trigger="On Block"`.

**Result:** 153 tests, 0 failures (ParserTest grew from 17 → 21).

### Duration enum (developer decision + collab)

**Problem:** `ParsedEffect.duration` was `String` while `EffectData.duration` was `int` — an untyped mismatch with no enforcement.

**Decision (developer):** Promote `EffectData.duration` from `int` to a `Duration` enum. Keeps the engine type-safe and eliminates magic number conventions.

**Changes:**
- `Duration.java` created: `THIS_BATTLE`, `THIS_TURN`, `OPPONENTS_NEXT_TURN`, `PERMANENT`
- `EffectData.java`: `int duration` → `Duration duration`
- `ParsedEffect.java`: comment updated to reflect `Duration` enum name values

### EffectTables expansion — lexer coverage drive (collab)

**Approach:** Iterative coverage-report loop. Each round: run `LexerCoverageReport`, identify top unknown tokens, classify each token, add to `EffectTables`, repeat.

**Key additions across two sessions:**

| Batch | Notable additions |
|---|---|
| Duration | `until the start of your next turn` (31 occurrences), `until the end of your next turn` (3) |
| Condition | `when` (same conditional role as `if`) |
| SKIP glue | `other`, `order`, `by`, `equal`, `including`, `can`, `will`, `would`, `not`, `instead`, `removed`, `returned`, `given`, `there`, `rules`, `end`, `battle`, `according`, `using`, `back`, `only`, `own`, `game`, `same`, `without`, `damage`, `refresh`, `phase`, `being`, `until`, `don't`, `currently`, `except`, `after`, `target`, `different`, `include`, `least`, `start`, `under`, `leaders`, `between`, `attribute` |
| CARD_TYPE | `event`, `events`, `stages`, `leader's`, `card's`, `character's` |
| FILTER | `face-up`, `face-down`, `blocker`, `double`, `banish`, `rush`, `base`, `selected`, `revealed`, `chosen` |
| QUANTITY | `total`, `every`, `no`, `none`, `one`, `additional`, `extra` |
| ZONE | `life` (standalone), `counter` |
| VERB | `become`, `becomes`, `choose`, `select`, `shuffle`, `played`, `activated`, `trashed`, `negated`, `returns`, `attacks`, `trashes`, `activates`, `battles`, `places`, `draws`, `reveals`, `adds`, `plays`, `chooses`, `shuffles`, `placed`, `reduced`, `change`, `take`, `deal`, `swap`, `win`, `ko'd` |

**Developer judgment calls:**
- `battle` → SKIP (residual; "during this battle" multi-word catches the meaningful form)
- `counter` → ZONE (the Counter zone where counter cards are held)
- `when` → CONDITION (mirrors `if` as conditional opener in non-bracket effect text)
- `no` / `none` → QUANTITY (quantity of 0)

### EffectLexer bug fixes (collab)

**Quote-leak fix:** `"Straw Hat Pirates"` in effect text split into `["\"straw", "hat", "pirates\""]` because the split regex `" +|(?=[.,;:()\\[\\]{}])|(?<=[.,;:()\\[\\]{}])"` does not break on `"`. Fixed by adding a `"` handler in `parseEffect`: detects a word starting with `"`, scans forward to the word ending with `"`, joins and emits as a single FILTER token. Handles single-word quoted names (`"Luffy"`) and multi-word quoted names (`"Straw Hat Pirates"`) identically.

**En-dash normalisation:** Card text uses en-dash `–` (U+2013) for negative costs (`–2 cost`). The number regex `[+-]?\d+` only matches hyphen-minus. Added `input.replace("\u2013", "-")` (and `\u2014` for em-dash) before the split step.

**Coverage results (after all fixes and table additions):**

| | Start of session | End of session |
|---|---|---|
| Generic coverage | 57.6% | **99.0%** (8 rows manual) |
| Unique coverage | 42.1% | **96.0%** (45 rows manual) |

---

### CardType @JsonCreator deserialization fix (collab)

**Problem:** After renaming `CardType` enum values from mixed-case (`Character`) to UPPERCASE (`CHARACTER`), compiled JSON files still contained the old lowercase forms. Jackson's default enum deserialization is case-sensitive — the mismatch caused `cardType` fields to deserialize as `null`, breaking 3 `CardDatabaseTest` assertions.

**Fix:** Added a `@JsonCreator` static factory method to `CardType`:
```java
@JsonCreator
public static CardType fromValue(String value) {
    return CardType.valueOf(value.toUpperCase());
}
```
This allows any case variant in JSON (`"Character"`, `"character"`, `"CHARACTER"`) to map to the correct enum constant. The fix is transparent — no changes to compiled JSON files were needed.

**Developer reasoning:** Reserializing every compiled JSON file was rejected — it would break the audit trail and require a full re-scrape to regenerate. A single `@JsonCreator` absorbs all past and future case variation at the deserialization boundary.

**Result:** 180 tests, 0 failures.

---

### EffectParser Phase 1 — tools pipeline (collab)

**Problem:** `CardCompiler` always wrote `"effects": []`. After the EffectLexer reached 99%/96% coverage, the next layer — converting token lists to structured `ParsedEffect` records — needed to be built.

**Data flow:**
```
ParsedAbility.effect()  (String — trigger/cost already stripped)
    ↓ EffectLexer.parseEffect()
List<Token>
    ↓ EffectParser.parse()
List<ParsedEffect>
    ↓ CardCompiler.toEffectJson()
effects[] in compiled JSON
```

**Pre-pass changes (developer-directed):**
- `TokenType` — added `MODIFIER` (10th value) for `may`/`must`; these are semantic qualifiers that change a mandatory effect to optional, not words to skip
- `EffectTables` — moved `may`/`must` from `addSkips()` to new `addModifiers()` helper
- `ParsedEffect` — added `boolean optional` as the last field

**Key design decisions (developer):**

*Flat clause model:* `ParsedEffect` remains a flat record — no sub-effect nesting, no `choices` field. MODAL effects (Choose one: A / B) emit a single `ParsedEffect(type=MODAL)` stub. The `choices` field is deferred until `EffectData` gains `List<List<EffectData>> choices` to hold sub-effects.

*Subject-first clauses:* `"Up to 1 of your Characters gains +1000 power"` puts the target before the verb. Detected via a `pastVerb` flag in `extractQuantities()`: when QUANTITY tokens appear before the first VERB, the first QUANTITY goes to `max` (targeting) rather than `value` (effect magnitude).

*Condition two-pass:* Condition is extracted from the leading `if`/`when` clause before splitting into sub-clauses. FILTER tokens in the condition body are checked for `RESTED` (→ `IF_RESTED`) before the CARD_TYPE token, to avoid misclassifying "if this Character is rested" as `IF_CHARACTER_COUNT`.

*`IF_YOU_DO` propagation:* Clauses after a `then` CONNECTOR automatically receive `condition = "IF_YOU_DO"` — the clause fires only if the prior one resolved. Explicit `"If you do,"` tokens in text confirm this rather than starting a new condition classification.

*`"or"` ambiguity (known Phase 2 gap):* `"or"` appears both as a CONNECTOR between independent clauses ("Draw 2 *or* trash 1") and as a compound-target separator ("this Leader *or* 1 of your Characters"). EffectParser splits on all `"or"` CONNECTOR tokens uniformly. Disambiguation — checking whether both arms of an `"or"` split contain VERBs before committing to the split — is deferred to Phase 2.

**EffectTypes implemented (Phase 1):**
DRAW, TRASH, REST, KO, RETURN_TO_HAND, POWER_BOOST, COST_MODIFY, ADD_DON, SET_DON_ACTIVE, GRANT_KEYWORD, PLAY_CARD, NEGATE, PREVENT_ATTACK, UNREST, ATTACH_DON, MODAL (stub).

**Test results:** 27 `EffectParserTest` tests, 0 failures. Total suite: 180 tests, 0 failures.

---

### CardCompiler — toEffectJson() integration (collab)

**Problem:** `toAbilityJson()` hardcoded `node.set("effects", mapper.createArrayNode())`. With `EffectParser` complete, the stub could be replaced with real output.

**Design decisions (developer):**
- KEYWORD abilities keep `effects: []` — keywords are encoded on `AbilityData.keyword`, not in `EffectData`
- `buildTargetNode()` returns `null` (→ `"target": null` in JSON) when all targeting fields are zero/null, avoiding meaningless empty objects
- `mapZone()` maps lexer raw strings (`"hand"`, `"don!! deck"`, etc.) to `ZoneType` enum names; unmapped values return `null`
- `mapParsedCardType()` maps `ParsedEffect` type strings to `CardType` enum names; compound types (`"LEADER_OR_CHARACTER"`, `"CARD"`) map to `null` — they cannot be expressed as a single enum value and need manual override

**Result:** Compiled JSON now contains fully populated `effects[]` arrays for all cards where `EffectParser` can classify the verb. Cards with unresolvable verbs or MODAL effects get an empty or MODAL stub array.

---

### CardChecklistCsvGenerator (collab)

**Problem:** After `CardCompiler` runs, there was no way to quickly see which cards still had unresolved UNKNOWN tokens or needed manual JSON overrides. The existing `LexerCoverageReport` operates on the generic/unique CSV templates, not on individual cards.

**Design (developer-directed):**
- Called automatically from the end of `CardCompiler.main()` — no separate invocation needed
- One row per unique card (`card_set_id`); deduplication across sets/decks/promos uses a `LinkedHashMap<String, ObjectNode>` accumulated during compilation with `putIfAbsent`
- `collectUnknownTokens()`: calls `Parser.parse()` to strip trigger headers and costs from `card_text`, then runs `EffectLexer.parseEffect()` on each ability body, collects distinct UNKNOWN token raw values in encounter order
- `generalizeEffect()` in `EffectTemplateCsvGenerator` promoted from `private static` to package-private (`static`) so `CardChecklistCsvGenerator` can reuse it without duplication
- `stripDisclaimer()`: removes `"Disclaimer:"`, `"DISCLAIMER:"`, and inline errata sentences before lexing — same boilerplate-stripping logic used in `EffectTemplateCsvGenerator`
- `needs_override` column: `"true"` if `unknown_tokens` is non-empty; allows filtering the CSV to the actionable backlog

**Output:** `csv/card_checklist.csv` — sorted by `card_set_id`, columns: `card_name`, `card_set_id`, `card_text`, `generic_card_text`, `unknown_tokens`, `needs_override`.

---

### CliController — message consolidation and display improvements (collab)

**Problem:** `GameEngine` contained two direct `System.out.println` calls (turn announcement and game-over message), violating the "CliController is the sole output owner" rule. Additionally, battle announcements (`announceAttack`, `announceBlocker`, `announceCounter`, `announceBattleResult`) and the phase-result announcements after non-interactive phases (Draw, DON) were either stubs or missing entirely.

**Developer constraint:**
> "CliController owns ALL player-visible output. GameEngine, BattleSystem, GameState should never call System.out."

**Changes (collab):**
- `GameEngine.run()` — replaced both `System.out.println` calls with `cliController.announcePhase()` and `cliController.announceGameOver()`; added private `announcePhaseResult(Phase, int)` helper that calls `announceDrawPhaseResult` / `announceDonPhaseResult` after the non-interactive phase advances
- New announcement methods added to `CliController`: `announcePhase`, `announceDrawPhaseResult`, `announceDonPhaseResult`, `announceAttack`, `announceBlocker`, `announceCounter`, `announceBattleResult` (proper signature: attacker, target, attackerWon, defenderLifeAfter, lifesTaken), `announceCardPlayed`, `announceGameOver`
- `defend()` wired: captures `lifesBefore` before `battleSystem.resolve()`, then observes `lifesAfter` and field presence to determine `attackerWon` — no BattleSystem signature changes needed
- DON count announcement uses `turnNumber == 1 ? 1 : 2`, mirroring `TurnManager.advancePhase()` logic without exposing a new `isFirstTurn()` accessor

**Ability description display (collab):**
- `cardAbilityLine(Card)` helper: returns `CardData.description()` if non-blank; otherwise formats keyword abilities as `[Rush]`, `[Double Attack]` etc. via `formatKeyword(Keyword)` — covers both real cards loaded from JSON and generic GameFactory test cards
- Called in `printHand()`, `printField()` (Leader, Characters, Stage sections), and `announceCardPlayed()` — ability line prints in white beneath each card's stats line
- "Use Leader's Ability" menu option added as a commented-out stub in `runMainPhases()` with a `// TODO: ACTIVATED_MAIN` note; case 7 handler also stubbed and commented

**GameFactory named ability cards (collab):**

**Developer decision:** The test decks needed named cards to exercise each Phase 2 tier manually in the CLI. The GameFactoryTest pins exactly 42 Characters / 5 Events / 3 Stages = 50 total, so named cards had to replace generic cards rather than expand the deck.

| Card | Trigger | Effect | Tier |
|---|---|---|---|
| Nami (×2) | ON_PLAY | Draw 1 card | 1 (done) |
| Sanji (×2) | WHEN_ATTACKING | +1000 power this turn | 1 (done) |
| Coby (×2) | ON_KO | Draw 1 card | 1 (done) |
| Marco (×2) | END_OF_YOUR_TURN (DON!! ×1) | Rest 1 opponent's Character | 1 (done) |
| Vivi (×1) | TRIGGER | Draw 1 card | 2 (stub) |
| Perona (×1) | ON_PLAY | Gains [Blocker] until end of turn | 2 (stub) |
| Kuzan (×1) | ON_PLAY | MODAL — draw 2 or bounce | 3 (stub) |

Generic character counts reduced proportionally (Lieutenants 9→6, Captains 9→7, Commanders 7→5, Admirals 5→2, Yonko Officers 2→1) to maintain the 42-card total. All existing `GameFactoryTest` assertions pass unchanged.

---

### Multi-color `List<Color>` (collab)

**Problem:** `ColorDeserializer` handled the space-separated multi-color API format (`"Green Yellow"`) by silently dropping all colors after the first. `CardData.color` was a single `Color`, losing information for every dual-color card in the set.

**Developer decision:**
> "Replace `Color color` with `List<Color> colors`. No `color()` convenience accessor — both colors are primary, neither takes precedence. Callers use `colors()` directly."

**Changes:**
- `ColorDeserializer` → `StdDeserializer<List<Color>>`: splits on space, maps each valid token to a `Color` enum value, skips dirty/unknown tokens, returns an immutable list
- `CardData` — component renamed from `color` to `colors` (`List<Color>`); compact constructor normalises `null` → `List.of()`; `toString()` updated; no `color()` bridge method
- `CliController` — added `ansiCode(Color)` private helper (returns raw ANSI string); added `colorize(String, List<Color>)` and `boldColorize(String, List<Color>)` overloads: single-color delegates to existing method, multi-color alternates ANSI codes per character (e.g. "Yamato" in Green/Yellow → Y(green)a(yellow)m(green)a(yellow)t(green)o(yellow)); all `.getData().color()` call sites replaced with `.getData().colors()` using `replace_all`
- `GameFactory.makeLeader` — parameter changed to `List<Color>` — caller passes `List.of(Color.Red)` / `List.of(Color.Green)`
- `CardDatabaseTest` — two `assertEquals(Color.Red, data.color())` assertions changed to `assertTrue(data.colors().contains(Color.Red))`

**Developer reasoning on display:** Per-character alternation was chosen over per-word alternation because most dual-color card names in OP TCG are single words (Sanji, Nami, Yamato). Per-word would produce no visible alternation in those cases.

**Result:** 224 tests, 0 failures.

---

## What Was Never AI-Generated

- The overall phased architecture (CMU → Tmux → GUI → AI → Online)
- The data model design (Zone/Deck/Player ownership, Deque semantics)
- The CardScraper (API pagination, rate limiting, raw JSON storage)
- The decision to separate BattleSystem from CliController
- The CardData record field selection (what the engine actually needs vs. API noise)
- The [DON!! xX] condition vs. cost ruling (found in the official PDF)
- The multi-color `List<Color>` design decision — no `color()` bridge accessor (both colors are equally primary), per-character ANSI alternation chosen over per-word
- The sub_types tokenization analysis (developer identified the multi-word problem)
- The Tmux 3-pane layout concept
- The GameStateServer/GameStateClient socket architecture (developer specified the pattern; AI described implementation details)
- The decision to promote `EffectData.duration` from `int` to a typed `Duration` enum
- The `sawKeyword` + paren-skip logic in `splitOnEmbeddedTriggers` (developer identified the bug and root cause; AI implemented the fix)
- The generic/unique threshold choice (≥2 occurrences = generic) for the CSV corpus split
- The classification of `when` as CONDITION vs SKIP (OP TCG domain knowledge: "when X, do Y" is a conditional gate identical in semantics to "if X, do Y")
- The decision that `counter` is ZONE (not FILTER), `no`/`none` are QUANTITY (not SKIP), and `battle` is SKIP (residual after the full "during this battle" DURATION phrase fires)
- The `EffectParser` flat-record design (no sub-effect nesting, MODAL deferred) — rejecting the temptation to add `List<List<ParsedEffect>> choices` before the engine needs it
- The `MODIFIER` token type classification — `may`/`must` are semantic qualifiers on the whole clause, not words to skip; this maps to the `optional` boolean in `ParsedEffect`
- The `IF_RESTED` condition priority — checking FILTER tokens for `RESTED` before the CARD_TYPE token prevents misclassification of "if this Character is rested" as `IF_CHARACTER_COUNT`
- The `@JsonCreator` absorb-all-cases strategy for `CardType` — reserializing compiled JSON to uppercase was rejected in favour of tolerating past and future case variation at the deserialization boundary
- The `buildTargetNode()` null-return contract — emitting `"target": null` instead of `{}` when all targeting fields are absent, keeping compiled JSON clean for the engine's null checks
