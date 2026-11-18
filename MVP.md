# MVP — Minimum Viable Project

This document defines the smallest version of `optcg` that actually fulfills the project's stated purpose (see README.md): **a text-based tool a new or returning player can use to learn the One Piece Card Game and practice against a difficulty-tuned AI opponent.** It exists so "MVP" has a concrete, checkable definition instead of meaning "whatever's next" — and so the full roadmap in README.md (browser play, game recording, deck builder, online play, stronger AI and deck simulation) is clearly understood as *post*-MVP, not part of it.

Status reporting convention, same as the rest of this repo: a line item below is "done" only when it's actually been exercised, not when the code exists but is untested. See `memory/phase3.md` for the detailed engine-completion checklist this document summarizes against.

---

## The one-sentence target

A learner can launch the CLI, pick a difficulty, play a full game against the AI using real OPTCG decks to a win or loss, ask "what's the best move here and why" mid-game, and trust that what they're seeing reflects the actual rules — not a simplified or buggy approximation of them.

---

## In scope

Ordered by build sequence. Items 1–4 make the game correct and completable; items 5–8 are the purpose-defining features.

| # | Item | Why it's load-bearing for the MVP | Current status |
|---|---|---|---|
| 1 | **Player controller layer**: a `PlayerController` interface (human CLI, AI, scripted), an engine-generated legal-move list, and a single input reader replacing the two `Scanner`s on `System.in` | Every later item needs to ask "what are the legal moves?" and hand the choice to someone other than the keyboard. Today all decisions live inside `CliController` (1,100+ lines). Two stdin readers also break piped and scripted input, which item 2 relies on. | Not started |
| 2 | **End-to-end match test**: an automated test that plays a full game through `GameEngine` to an actual win condition | Separates "the pieces work in isolation" (370 unit tests, already true) from "the game actually works". Proof it's needed: until November 2026, every main-menu choice on turns 1–2 ran the wrong action and all 370 unit tests still passed. | Not started — no such test exists in `src/test/` |
| 3 | **Engine completion, scoped to the starter decks**: the Phase 3 items in `memory/phase3.md` that any card in the v1 decks needs (`MAIN` dispatch is almost certainly one) | A learning tool that gets cards wrong teaches the wrong game. Scoping to the decks a learner can actually pick turns "near-100% of 2,851 cards" into a finishable target; the rest can follow after v1. | Not started |
| 4 | **Real-card decks**: 4–6 real starter decks in `GameFactory`, shipped as card-ID lists and chosen to cover the basic concepts between them; card data fetched and compiled on first run | The point is to learn *this* game with *its* cards. Shipping IDs only keeps card text out of the repo and spares a new player from running the scraper by hand. | Not started (`memory/phase3.md` item 9) |
| 5 | **Heuristic AI opponent** | Without this there's no "practice" in "practice tool." Doesn't need to be strong, just legal and not obviously dumb: greedy selection over item 1's legal-move list, scored on board, life, DON and card advantage. No training data needed. | Not started |
| 6 | **Difficulty levels** | "Based on a certain difficulty" is explicit in the project's purpose statement. Cheapest version: vary lookahead depth, how often the AI deliberately picks a weaker move, and how aggressively it blocks/attacks. | Not started |
| 7 | **Move advisor** ("what's the best move and why") | This *is* the "helper agent" half of the purpose, and nearly free once #5 exists: the same scoring function exposed as a question instead of an action. The AI returns its reasons as structured data and the advisor prints them as sentences. An optional LLM may reword those reasons, but never chooses the move or states a rule, and the advisor must work offline without it. | Not started |
| 8 | **Minimal guided walkthrough** (a scripted first-few-turns explainer, not a polished tutorial UI) | "Learning tool for new players" implies *some* onboarding beyond "figure out the CLI menus yourself". | Not started |

### Design rules for v1 (so v4 can plug in)

Not separate deliverables, but item 1 and the engine work should hold to them. They make the v4 AI (ISMCTS, OpenSpiel) a bridge instead of a rewrite:

- **Numbered actions** — every legal move at a decision point is one entry in a list.
- **Cloneable, seeded state** — `GameState` can be deep-copied and every shuffle takes a seed, so a search can try a move and rewind, and a recorded game replays exactly.
- **Per-player views** — a player's view hides the opponent's hand, life cards and deck order. The AI and the move advisor must decide from this view, never from the full state.
- **Loggable decisions** — every decision records its type, the options offered, the choice made and a reference to the game state, so v1.5 can measure playstyle and v2.1 can replay games.

## Explicitly out of scope for MVP

| Item | Why it's excluded | Where it lives |
|---|---|---|
| Web client, Tmux UI, JavaFX GUI | None are required for the learn/practice loop over a terminal. The Tmux and JavaFX plans are replaced by a single web client. | v2 |
| Game recording and replay | Cheap, and the data source for every learned AI, but not needed to practice. | v2.1 |
| Deck builder, online play, tournament mode | Need accounts and hosted servers. | v3 |
| ISMCTS, OpenSpiel, self-play or any training infrastructure | A materially bigger lift than a heuristic opponent, and not necessary to satisfy "practice against a difficulty-tuned AI". Heuristics get there first; v1's design rules keep this cheap to add later. | v4 |
| LLM features beyond advisor rewording (rules Q&A with RAG, LLM-assisted card parsing) | Useful ideas, but not needed to learn or practice, and an LLM stating rules risks teaching the wrong game. | README ML track (ideas) |
| Training the AI on real played games | The purpose statement's "hopefully trained on games played by players" is a *direction*, not a near-term deliverable. No played-game dataset exists yet, and one won't until v2.1 has been collecting. | v4 |
| Playstyle quiz, style profile and deck recommendations | Needs v1's advisor as the baseline that style is measured against, and a decision log to measure from. v1 only has to make decisions loggable. | v1.5 |
| Leader guides | Each leader needs its whole sample deck supported and tested first, so guides roll out as engine coverage grows. | From v1.5 |
| Simulating a deck archetype's playstyle | The deck-archetype version (e.g. "play like a Red Zoro aggro list") is tractable because it pools games across everyone who played that decklist, but it still needs a corpus of played games. Simulating one specific human is data-starved enough that it isn't recommended at all. | v4 |

## Definition of done

MVP is complete when all of the following hold simultaneously, not individually:

- [ ] Decisions go through `PlayerController`; human, AI and scripted players are interchangeable, and there is one stdin reader
- [ ] An automated test plays a full game to completion via the actual engine entry points and asserts a winner is declared correctly
- [ ] `GameFactory` deals 4–6 real starter decks from card-ID lists, not synthetic test decks
- [ ] `CardCompatibilityReport` shows **every card in those decks** with no flags, re-run and confirmed (not just "the code looks done"), and each of those cards is played at least once by a scripted test
- [ ] A player can select an opponent difficulty and the AI's play measurably changes with it (not just a label)
- [ ] A player can ask for a move suggestion at any legal decision point and get a reasoned answer, not just a bare card name
- [ ] A new player with no prior exposure to the CLI can get through their first game with the built-in walkthrough, without needing to read source code or ask someone else how the menus work

## Relationship to the full roadmap

```
Phase 0 ──► Phase 1 ──► Phase 2              [done — current repo state]
                           │
                           ▼
        v1 — MVP: controller layer, full-game test, starter-deck engine
             completion, real decks, heuristic AI + difficulty,
             move advisor, walkthrough
                           │
                           ▼
        v1.5 — find your playstyle: beginner quiz, teaching decks,
               style profile + explained deck recommendations
               (leader guides roll out from here as coverage grows)
                           │
                           ▼
        v2 — play in the browser (web client over WebSocket snapshots)
                           │
                           ▼
        v2.1 — game recording & replay  ──────────────┐
                           │                          │ (data)
                           ▼                          ▼
        v3 — deck builder, online play,     v4 — ISMCTS, OpenSpiel training,
             tournaments                         deck-archetype simulation
```

Everything after v1 is real, intentional roadmap — just not required to call the project a usable product. See the README's Roadmap section for each version's detail.
