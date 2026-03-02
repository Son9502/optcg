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

Nine token types. Every word or phrase in a card effect body belongs to exactly one.

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
Zero or more filters narrow which cards within CARD_TYPE are valid targets.
Filters are AND-ed together (all must be true).

| Filter | Syntax | Notes |
|---|---|---|
| Cost (max) | `with a cost of N or less` | Also: `with a cost of up to N` (synonym) |
| Cost (min) | `with a cost of N or more` | |
| Power (max) | `with [POWER] power or less` | Also: `with power of up to [POWER]` (synonym) |
| Power (min) | `with [POWER] power or more` | |
| Base cost/power | `with a base cost of N or less` | `base` = before any boost/reduce modifiers |
| State | `rested` / `active` | Card's current orientation |
| Placement | `face-up` / `face-down` | Orientation when entering a zone |
| Keyword (bracket) | `[KEYWORD]` / `with [KEYWORD]` | Card must have that keyword; emitted as FILTER with String value |
| Keyword (word) | `blocker` / `double` / `banish` / `rush` | Lowercase form in "gains the Blocker keyword" constructions |
| Type (brace) | `{Type name} type` | Sub-type match using curly-brace syntax |
| Type (quote) | `"Type name" type` | Sub-type match using double-quote syntax; lexer joins multi-word quoted spans |
| Attribute (paren) | `(Slash) attribute` / `(Strike) attribute` | Card's printed attribute |
| Color | `red/green/blue/purple/yellow/black` / `multicolored` | Precedes CARD_TYPE or TYPE filter |
| Card state | `selected` / `revealed` / `chosen` | Result of a previous look/search clause |
| Identity | `other than [CARD_REF]` | Excludes a specific named card |
| Name | `named [CARD_REF]` | Only that specific named card |

Color and type can combine: `"a purple {Big Mom Pirates} type Character"` →
`FILTER(color=Purple, subType="Big Mom Pirates", cardType=Character)`

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
How long the effect lasts. If omitted, the effect is permanent (until the card leaves the field).
Maps to the `Duration` enum: `THIS_TURN`, `THIS_BATTLE`, `OPPONENTS_NEXT_TURN`, `PERMANENT`.

```
during this turn                                  → THIS_TURN
until end of this turn                            → THIS_TURN
until the end of this turn                        → THIS_TURN
during this battle                                → THIS_BATTLE
until the start of your next turn                 → OPPONENTS_NEXT_TURN  (most common "next turn" form)
until the end of your next turn                   → OPPONENTS_NEXT_TURN
until the end of your opponent's next turn        → OPPONENTS_NEXT_TURN
until the end of your opponent's next End Phase   → OPPONENTS_NEXT_TURN
(omitted)                                         → PERMANENT
```

---

### CONDITION
A `"If [clause],"` prefix applied to the entire effect clause that follows.

| Condition | Syntax |
|---|---|
| `IF_LIFE_COUNT` | `If you have N or less/more Life cards` |
| `IF_OPPONENT_LIFE_COUNT` | `If your opponent has N or less Life cards` |
| `IF_DON_COUNT` | `If you have N or more/fewer DON!! cards on your field` |
| `IF_DON_COMPARISON` | `If the number of DON!! cards on your field is equal to or less than your opponent's` |
| `IF_LIFE_COMPARISON` | `If the number of your Life cards is equal to or less than your opponent's` |
| `IF_CHARACTER_COUNT` | `If you have N or more Characters` |
| `IF_LEADER_TYPE` | `If your Leader has the {Type} type` |
| `IF_LEADER_ATTRIBUTE` | `If your Leader has the (Attribute) attribute` |
| `IF_LEADER_COLOR` | `If your Leader is [Red/Green/multicolor]` or `If your Leader has the [Color] color` |
| `IF_LEADER_IDENTITY` | `If your Leader is [CARD_REF]` or `If your Leader has the {Type} type or is [CARD_REF]` |
| `IF_THIS_POWER` | `If this Character has [POWER] power or more` |
| `IF_THIS_RESTED` | `If this Character is rested` |
| `IF_THIS_KO` | `If this Character would be K.O.'d` — replacement effect |
| `IF_CHARACTER_EXISTS` | `If you have a {Type} type Character` |
| `IF_CHARACTER_COLOR` | `If you have a [Color] Character` |
| `IF_REVEALED_COST` | `If the revealed card has a cost of N or more` — follows a SEARCH_DECK clause |
| `IF_YOU_DO` | `If you do,` — follow-up conditional on the previous clause succeeding |

---

### CONNECTOR
Joins clauses within an ability body.
```
and    — simultaneous or sequential; both effects happen
then   — sequential; second resolves after first completes
also   — additional effect, lower priority than and/then
,      — clause boundary (contextual: may separate filters or clauses)
•  /   — modal option separators (inside a MODAL clause)
```

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
```

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
| `duration` | DURATION token (`0` = permanent, `1` = this turn, `-1` = this battle) |

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
