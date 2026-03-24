# OPTCG Effect Grammar

Formal grammar for parsing One Piece TCG card effect text into structured `EffectData` objects.
This document is the specification for `EffectParser` and the authoritative reference for all
`EffectType`, `Condition`, and `Target` design decisions.

---

## Overview

Every card effect body — after stripping trigger headers and costs — follows a consistent grammar.
The core insight is that **the action verb determines the `EffectType`**, and everything else
fills in the fields of `EffectData` (`value`, `target`, `condition`, `duration`).

A card effect body is one or more **clauses** joined by connectors.
Each clause has the same shape: `[VERB] [QUANTITY?] [PLAYER?] [CARD_TYPE] [FILTER*] [ZONE?] [DURATION?]`

---

## Token Vocabulary (Lexer Layer)

Thirteen token types. Every word or phrase in a card effect body belongs to exactly one.

### VERB
The first meaningful word of a clause. Determines `EffectType`.
```
Draw | Rest | K.O. | Trash | Return | Play | Add | Give | Set | Place |
Look | Reveal | Negate | Choose one | Gain | Gains | Activate | cannot |
Become | Becomes | Choose | Select | Shuffle | Deal | Swap | Take | Win |
Change | Ko'd
```
3rd-person and past-tense inflections are also in the phrase table:
`draws`, `reveals`, `adds`, `plays`, `chooses`, `shuffles`, `places`, `returns`,
`attacks`, `trashes`, `activates`, `battles`, `played`, `activated`, `trashed`,
`negated`, `placed`, `reduced`.

> `cannot` opens a **prevention clause** (see below).
> `ko'd` is the normalised form of `K.O.'d` after lexer normalisations are applied.

---

### QUANTITY
How many cards are affected. `up to N` and `N or less` are **synonymous** in all contexts —
both parse to `max=N` (player may choose any count from 0 to N).
```
N               — exactly N (bare digit caught by number check)
up to N         — player chooses 0..N  (= N or less)
all             — every matching card
each            — one instance per matching card
every           — "for every N characters" (scaling)
more            — comparative upper bound
less            — comparative lower bound
number          — "the number of DON!! cards…"
total           — "a total of N", "N or more in total"
no / none       — zero matching cards
one             — word form of 1; "one of your characters"
additional      — "an additional +N power"
extra           — "an extra card", "N extra power"
```

---

### PLAYER
Whose cards are being affected.
```
your            → SELF
your opponent's → OPPONENT
```

---

### CARD_TYPE
The type of card being targeted.
```
Leader
Character(s)
card(s)          — untyped (any card)
DON!! card(s)
Event card(s)
Stage card(s)
```
Compound targets like `"your Leader or 1 of your Characters"` are parsed as a single target
with `cardType = LEADER_OR_CHARACTER` and `max` drawn from the QUANTITY token.

---

### FILTER
Numeric and text filters that narrow which cards within CARD_TYPE are valid targets.
Emitted by `FILTER_PATTERNS` (regex) or plain-text phrase entries in `EffectTables`.

| Filter | Syntax | Notes |
|---|---|---|
| Cost (max) | `with a cost of N or less` | Also: `with a cost of up to N` (synonym) |
| Cost (min) | `with a cost of N or more` | |
| Power (max) | `with [POWER] power or less` | Also: `with power of up to [POWER]` (synonym) |
| Power (min) | `with [POWER] power or more` | |
| Base cost/power | `with a base cost of N or less` | `base` = before any boost/reduce modifiers |
| State | `rested` / `active` | Card's current orientation |
| Placement | `face-up` / `face-down` | Orientation when entering a zone |
| Keyword (word) | `blocker` / `double` / `banish` / `rush` | Lowercase word-form in "gains the Blocker keyword" constructions |
| Placement/position | `top` / `bottom` | Deck positioning in MOVE_TO_TOP/BOTTOM_DECK clauses |
| Color | `red/green/blue/purple/yellow/black` / `multicolored` | Precedes CARD_TYPE or SUBTYPE_FILTER |
| Card state | `selected` / `revealed` / `chosen` | Result of a previous look/search clause |

---

### SUBTYPE_FILTER
A curly-brace-delimited subtype or type identifier: `{Straw Hat Crew}`, `{Animal Kingdom Pirates}`.
`EffectParser` uses this token type to classify `IF_LEADER_TYPE` and `IF_FIELD_CHARACTER_TYPE` conditions,
and to filter targets by subtype in `matchesFilters()` (item #21 — done).

---

### NAME_FILTER
A square-bracket-delimited named card or keyword reference: `[Sanji]`, `[Blocker]`, `[Rush]`.
`EffectParser` uses this token type to classify `IF_LEADER_NAME` conditions and to route
`GRANT_KEYWORD` / `PREVENT_BLOCK` verbs.

---

### ATTRIBUTE_FILTER
A parenthesis-delimited attribute identifier: `(Slash)`, `(Ranged)`, `(Strike)`.
`EffectParser` uses this token type to classify `IF_LEADER_ATTRIBUTE` conditions and to filter
targets by attribute in `matchesFilters()` (item #17 — done).

---

> **Type + name can combine in a condition:** `"If your Leader has the {Straw Hat} type or is [Luffy]"` —
> model as `IF_LEADER_TYPE` (SUBTYPE_FILTER) with an identity fallback. The identity check is
> rare enough to defer to manual JSON override.
>
> **Color filter on target** (item #24 — done): `"a purple {Big Mom Pirates} type Character"` — color
> is an additional FILTER. `Target.color` (String field, case-insensitive) stores it; `matchesFilters()` checks against `card.getData().colors()`. Parser does not yet extract this field — requires hand-compiled JSON for cards using color-filtered targets.

---

### ZONE
Where a card is moved from or to.

**Source zones (from):**
```
from your deck           from your DON!! deck
from your hand           from your trash
from your Life area      from your field
from the top of your deck
from the bottom of your deck
```

**Destination zones (to / placement):**
```
to your hand             to your trash
into your Life area      on your field
at the bottom of your deck (in any order)
at the top of your deck
as active                — placement state for DON!! (enters unrested)
rested                   — placement state for DON!! or Character (enters rested)
```

---

### DURATION
How long the effect lasts. If omitted, the effect persists while the card is on the field.
Maps to the `Duration` enum: `THIS_TURN`, `THIS_BATTLE`, `OPPONENTS_NEXT_TURN`, `WHILE_IN_PLAY`.

```
during this turn                                  → THIS_TURN
until end of this turn                            → THIS_TURN
until the end of this turn                        → THIS_TURN
during this battle                                → THIS_BATTLE
until the start of your next turn                 → OPPONENTS_NEXT_TURN  (most common "next turn" form)
until the end of your next turn                   → OPPONENTS_NEXT_TURN
until the end of your opponent's next turn        → OPPONENTS_NEXT_TURN
until the end of your opponent's next End Phase   → OPPONENTS_NEXT_TURN
(omitted)                                         → null in compiled JSON; treated as THIS_TURN by the engine (bonusPower/bonusCost, cleared each turn start); specify WHILE_IN_PLAY explicitly for lasting field effects
```

> **Duration bucket semantics (engine layer):**
> `THIS_BATTLE` → `bonusBattlePower` (cleared at end of each battle).
> `THIS_TURN` / null → `bonusPower` / `bonusCost` (cleared by `refreshField()` each turn start).
> `OPPONENTS_NEXT_TURN` → `bonusPowerOpponentTurn` / `bonusCostOpponentTurn` (cleared by `TurnManager.endTurn()` on the non-active player's cards at end of each turn).
> `WHILE_IN_PLAY` → `bonusPowerWhileInPlay` / `bonusCostWhileInPlay` (never cleared by `refreshField()`; cleared by `GameState.moveCard()` when card leaves the CHARACTER zone).

---

### CONDITION
A `"If [clause],"` prefix applied to the entire effect clause that follows.

Implemented conditions are marked ✓. Unimplemented are deferred to later phases.

| Condition | Syntax | Status |
|---|---|---|
| `IF_LIFE_COUNT` | `If you have N or less/more Life cards` | ✓ |
| `IF_OPPONENT_LIFE_COUNT` | `If your opponent has N or less Life cards` | ✓ |
| `IF_DON_COUNT` | `If you have N or more/fewer DON!! cards on your field` | ✓ |
| `IF_HAND_COUNT` | `If you have N or more cards in your hand` | ✓ |
| `IF_CHARACTER_COUNT` | `If you have N or more Characters` | ✓ |
| `IF_TRASH_COUNT` | `If you have N or more cards in your trash` | ✓ |
| `IF_DECK_COUNT` | `If you have N or more cards in your deck` | ✓ |
| `IF_FIELD_CHARACTER_COST` | `If any Character on your field has cost N or more` | ✓ |
| `IF_RESTED` | `If this Character is rested` — FILTER token `rested` detected in condition | ✓ |
| `IF_YOU_DO` | `If you do,` — follow-up conditional on the previous clause succeeding | ✓ |
| `IF_LEADER_MULTICOLORED` | `If your Leader is multicolored` | ✓ `colors().size() > 1` |
| `IF_LEADER_NAME` | `If your Leader is [CARD_REF]` — NAME_FILTER token in condition body | ✓ name equalsIgnoreCase |
| `IF_LEADER_TYPE` | `If your Leader has the {Type} type` — SUBTYPE_FILTER token | ✓ containsSubtype |
| `IF_LEADER_ATTRIBUTE` | `If your Leader has the (Attribute) attribute` — ATTRIBUTE_FILTER token | ✓ attribute enum name match |
| `IF_FIELD_CHARACTER_TYPE` | `If you have a {Type} type Character` — SUBTYPE_FILTER + CARD_TYPE=character before it | ✓ field card scan |
| `IF_OPPONENT_CHARACTER_COUNT` | `If your opponent has N or more Characters` | ✓ |
| `IF_OPPONENT_RESTED_CHARACTER_COUNT` | `If your opponent has N or more rested Characters` | ✓ |
| `IF_OPPONENT_DON_COUNT` | `If your opponent has N or more DON!! cards on their field` | ✓ |
| `IF_OPPONENT_FIELD_CHARACTER_POWER` | `If your opponent has a Character with N or more power` | ✓ |
| `IF_FIELD_CHARACTER_POWER` | `If you have a Character with N or more power` | ✓ |
| `IF_FIELD_CHARACTER_NAME` | `If you have a [Name] Character` — NAME_FILTER subtype, conditionValue = minBasePower | ✓ |
| `IF_FIELD_CHARACTER_NAME_RESTED` | `If you have a rested [Name] Character` — NAME_FILTER subtype | ✓ |
| `IF_FIELD_CHARACTER_COST_COUNT` | `If you have N or more Characters with a cost of N or more` — conditionValue = minCount, subtype = minCost string | ✓ |
| `IF_RESTED_CHARACTER_COUNT` | `If you have N or more rested Characters` | ✓ |
| `IF_NOT_FIELD_CHARACTER_NAME` | `If you do not have a [Name] Character on your field` | ✓ |
| `IF_DON_COMPARISON` | `If the number of DON!! cards on your field is equal to or less than your opponent's` | pending |
| `IF_THIS_POWER` | `If this Character has [POWER] power or more` | pending |
| `IF_REVEALED_COST` | `If the revealed card has a cost of N or more` — follows a SEARCH_DECK clause | pending |

> **Parser extraction note:** All conditions above marked ✓ are implemented in `EffectFactory.evaluateCondition()`. `EffectParser.classifyConditionClause()` extracts `IF_LEADER_NAME`, `IF_LEADER_TYPE`, `IF_LEADER_ATTRIBUTE`, `IF_LEADER_MULTICOLORED`, and `IF_FIELD_CHARACTER_TYPE` from raw card text via bracket token types. The remaining conditions (count-based and opponent-facing) still require hand-compiled JSON — the parser does not yet extract numeric threshold conditions from text.

---

### CONNECTOR
Joins clauses within an ability body.
```
and    — simultaneous or sequential; both effects happen
then   — sequential; second resolves after first completes
also   — additional effect, lower priority than and/then
or     — clause alternative (modal choice between two effects) OR compound target ("Leader or Characters")
,      — clause boundary (contextual: may separate filters or clauses)
•  /   — modal option separators (inside a MODAL clause)
```

> **Known Phase 2 gap:** `or` serves two distinct roles. When both arms of an `or` split contain
> a VERB, it separates independent clauses (split correctly). When neither arm contains a VERB —
> `"this Leader or 1 of your Characters"` — it is a compound-target alternation and should NOT
> produce two clauses. `EffectParser` Phase 1 splits on all `or` CONNECTOR tokens uniformly;
> disambiguation is deferred to Phase 2.

---

### MODIFIER
Semantic qualifier on the immediately following verb/clause. Does **not** describe a target or
action — describes whether executing the action is mandatory.
```
may    → optional = true   (player chooses whether to execute the effect)
must   → optional = false  (effect is mandatory, no choice)
```
`EffectParser` scans for a MODIFIER token before the VERB in each clause and sets
`ParsedEffect.optional` accordingly. MODIFIER tokens are consumed separately from SKIP tokens —
they carry game-logic meaning.

---

### OPTIONAL COST (Preamble)
Appears before the effect body. Parsed as `AbilityData.cost`, not as an `EffectData`.
```
You may [cost_expr]:
DON!! -N (You may return the specified number of DON!! cards from your field to your DON!! deck.):
```
`cost_expr` values:
```
rest this Character
trash N card(s) from your hand
return N DON!! card(s) to your DON!! deck
N card(s) from the top of your Life area (→ LIFE_TO_HAND or LIFE_TO_TRASH)
```
`LIFE_TO_HAND`: places life cards into hand (Big Mom archetype — `"place … into your hand"`).
`LIFE_TO_TRASH`: trashes life cards outright (`"trash … from your Life area"`).
`CardCompiler` detects the destination via `"into your hand"` / `"to your hand"` / `"add"` patterns in the cost text.

---

## Lexer Input Normalisations

Applied to `input` before the word-array is built, in this order:

| Source form | Normalised to | Reason |
|---|---|---|
| `K.O.` (any case) | `ko` | Periods shred into extra tokens under the split regex |
| `K.O.'d` (any case) | `ko'd` | Same; `ko'd` is then a VERB table entry |
| En-dash `–` (U+2013) | `-` (hyphen-minus) | Card text uses en-dash for negative costs; number regex only matches `-` |
| Em-dash `—` (U+2014) | `-` | Safety normalisation for em-dash variants |
| `"TypeName"` quoted span | Joined as single FILTER token | Split regex does not break on `"`, so `"Straw Hat Pirates"` becomes `["\"straw", "hat", "pirates\""]`; lexer detects leading `"` and scans forward to the closing `"` |

Disclaimer and errata boilerplate (`Disclaimer:`, `DISCLAIMER:`, `"This card has been officially errata'd."`)
is stripped from `card_text` before `Parser.parse()` is called in `EffectTemplateCsvGenerator`.

---

## Grammar Rules (BNF)

```
ability_body     ::= preamble? clause (CONNECTOR clause)*

preamble         ::= optional_cost
                   | condition
                   | optional_cost condition

clause           ::= effect_clause
                   | prevention_clause
                   | subject_first_clause
                   | modal_clause
                   | follow_up_clause

effect_clause    ::= VERB QUANTITY? PLAYER? CARD_TYPE FILTER* ZONE? DURATION?

prevention_clause ::= target "cannot" prevention_verb FILTER? DURATION?
prevention_verb  ::= "attack"
                   | "be attacked"
                   | "activate" "[Blocker]"
                   | "be K.O.'d"

subject_first_clause ::= QUANTITY "of" PLAYER CARD_TYPE VERB value DURATION?
                   -- e.g. "Up to 1 of your Leader or Character cards gains +1000 power"

follow_up_clause ::= IF_YOU_DO CONNECTOR clause
                   -- e.g. "If you do, draw 1 card."

modal_clause     ::= "Choose one:" modal_option ("/" modal_option)+
modal_option     ::= "•"? clause
```

---

## Verb → EffectType Mapping

### Unambiguous verbs
| Verb | EffectType |
|---|---|
| `Draw` | `DRAW` |
| `Rest` | `REST` |
| `K.O.` | `KO` |
| `Trash` | `TRASH` |
| `Return` | `RETURN_TO_HAND` |
| `Look` / `Reveal` | `SEARCH_DECK` |
| `Negate` | `NEGATE` |
| `Choose one` | `MODAL` |
| `cannot attack` / `cannot be attacked` | `PREVENT_ATTACK` |
| `cannot activate [Blocker]` | `PREVENT_BLOCK` |
| `cannot be K.O.'d` | `PREVENT_KO` |

### Context-dependent verbs
Disambiguation is done by inspecting the next meaningful token after the verb.

| Verb | Next token | EffectType |
|---|---|---|
| `Add` | CARD_TYPE = `DON!!` | `ADD_DON` |
| `Add` | ZONE = `Life area` | `ADD_LIFE` |
| `Give` | value = `+N power` | `POWER_BOOST` |
| `Give` | value = `±N cost` | `COST_MODIFY` |
| `Give` | CARD_TYPE = `rested DON!!` | `ATTACH_DON` |
| `Set` / `Activate` | CARD_TYPE = `DON!!` | `SET_DON_ACTIVE` |
| `Set` / `Activate` | CARD_TYPE = `Character` or `Leader` | `UNREST` |
| `Place` | ZONE = `into Life area` | `ADD_LIFE` |
| `Place` | ZONE = `at the bottom of deck` | `MOVE_TO_BOTTOM_DECK` |
| `Place` | ZONE = `at the top of deck` | `MOVE_TO_TOP_DECK` |
| `Play` | any | `PLAY_CARD` |
| `Gain/Gains` | value = `+N power` | `POWER_BOOST` |
| `Gain/Gains` | value = `+N cost` | `COST_MODIFY` |
| `Gain/Gains` | value = `[KEYWORD]` | `GRANT_KEYWORD` |

---

## EffectData Field Mapping

How parsed tokens map to `EffectData` record fields:

| `EffectData` field | Populated from |
|---|---|
| `type` | Verb → EffectType mapping above |
| `value` | QUANTITY token (for DRAW, POWER_BOOST, COST_MODIFY, etc.) |
| `target.player` | PLAYER token (`SELF` / `OPPONENT` / `ALL`) |
| `target.zone` | ZONE token (source or destination, depending on EffectType) |
| `target.cardType` | CARD_TYPE token |
| `target.max` | QUANTITY token (for targeting effects: REST, KO, RETURN_TO_HAND, etc.) |
| `target.maxCost` | FILTER(cost) token |
| `target.maxPower` | FILTER(power) token |
| `target.subType` | FILTER(type) token |
| `condition` | CONDITION token on the preamble or inline `If` clause |
| `duration` | DURATION token (`THIS_TURN`, `THIS_BATTLE`, `OPPONENTS_NEXT_TURN`, `WHILE_IN_PLAY`; null = WHILE_IN_PLAY) |

---

## Notes on Edge Cases

**`COST_MODIFY` sign convention:** Positive value = cost increase (harder to K.O./REST target);
negative value = cost reduction. Duration defaults to `during this turn` if unstated.

**`SEARCH_DECK` secondary effects:** "Look at the top N cards; add 1 to hand, place the rest at
the bottom" produces two `EffectData` objects: `SEARCH_DECK(value=N, max=1)` +
`MOVE_TO_BOTTOM_DECK`. The "rest at bottom" is the implicit second effect.

**`IF_YOU_DO` follow-up:** Only applies if the preceding clause was resolved. Modelled as a
second `EffectData` in the same ability's `effects` list, with `condition = IF_YOU_DO`.

**`MODAL` structure:** Produces one `EffectData(type=MODAL)` wrapping two sub-lists of effects.
Requires `EffectData.choices: List<List<EffectData>>` — a field addition needed before
`MODAL` can be fully serialised. Leave as empty `effects: []` with `type=MODAL` until then.

**Compound `IF_LEADER_IDENTITY`:** `"If your Leader has the {Type} type or is [CARD_REF]"` —
model as `IF_LEADER_TYPE` with an optional identity fallback. The identity check is rare enough
to defer to manual JSON override.

**Color filter on target:** `"a purple {Big Mom Pirates} type Character"` — color is an
additional FILTER, not a separate token type. `Target` needs a `color` field to store it.

**`"or"` as compound-target vs. clause separator (Phase 2 gap):** `"or"` is registered as a
CONNECTOR in `EffectTables`, so `EffectParser` Phase 1 always splits on it. This is correct for
modal choices (`"Draw 2 cards or trash 1"`) but incorrect for compound targets (`"this Leader or
1 of your Characters"`). The Phase 2 fix: before committing to an `or` split, check whether
both resulting sub-lists contain at least one VERB token; if neither sub-list has a VERB it is a
compound-target `or` and should not produce a clause split.

**Dynamic cost filters (Phase 2 gap):** FILTER_PATTERNS match only static integer thresholds
(e.g. `"cost of 4 or less"` → `maxCost = 4`). Relational expressions like `"cost equal to or
less than the number of DON!! cards on your field"` have no digit to capture — they produce no
FILTER token and the bound is silently dropped. These effects require a `"DYNAMIC"` filter
category and runtime evaluation of the referent count.

---

## Out of Scope (Unique CSV / Manual Override)

These patterns appear in `effect_templates_unique.csv` and require manual `effects[]` entries
in compiled JSON rather than parser templates:

- Attribute-specific prevention: `"cannot be K.O.'d by 'Slash' attribute cards"`
- Name aliasing: `"Also treat this card's name as [CARD_REF]"`
- Revealed-card conditionals: `"If the revealed card has the chosen cost, [effect]"`
- DON!! zero-or-max composite: `"If you have 0 or 8 or more DON!! cards on your field"`
- `"If you do"` chains where the first clause is non-deterministic
- Life-DON!! cross-comparisons between both players
- re-activate: `"Activate this card's [Main] effect"` (requires runtime ability lookup)
