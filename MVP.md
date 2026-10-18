# MVP — Minimum Viable Project

This document defines the smallest version of `optcg` that actually fulfills the project's stated purpose (see README.md): **a text-based tool a new or returning player can use to learn the One Piece Card Game and practice against a difficulty-tuned AI opponent.** It exists so "MVP" has a concrete, checkable definition instead of meaning "whatever's next" — and so the full roadmap in README.md (online play, GUI, story mode, replay/broadcast, deck/player simulation) is clearly understood as *post*-MVP, not part of it.

Status reporting convention, same as the rest of this repo: a line item below is "done" only when it's actually been exercised, not when the code exists but is untested. See `memory/phase3.md` for the detailed engine-completion checklist this document summarizes against.

---

## The one-sentence target

A learner can launch the CLI, pick a difficulty, play a full game against the AI using real OPTCG decks to a win or loss, ask "what's the best move here and why" mid-game, and trust that what they're seeing reflects the actual rules — not a simplified or buggy approximation of them.

---

## In scope

| # | Item | Why it's load-bearing for the MVP | Current status |
|---|---|---|---|
| 1 | Finish Phase 3 engine completion (`MAIN`/`OPPONENTS_ATTACK`/`ON_BLOCK` trigger dispatch, `WHILE_IN_PLAY` recalculation, replacement effects, passive-global `NEGATE`, compound-cost extraction) | A "learning tool" that gets ~12% of cards wrong teaches the wrong game. See `memory/phase3.md` for the itemized list. | Not started |
| 2 | Real-card decks via `GameFactory`, replacing the 12 synthetic test decks | The point is to learn *this* game with *its* cards — leaders and strategies a learner will recognize, not placeholder decks | Not started (`memory/phase3.md` item 9) |
| 3 | End-to-end match-flow validation — an automated test that plays a full game through `GameEngine`/`CliController` to an actual win condition | Separates "the pieces work in isolation" (370 unit tests, already true) from "the game actually works," which is currently an open question. This is the single biggest gap between current status and MVP. | Not started — no such test exists in `src/test/` as of this writing |
| 4 | A heuristic AI opponent — something to practice against | Without this there's no "practice" in "practice tool." Doesn't need to be strong, just legal and not obviously dumb. Can reuse whatever scoring function a deck-rating pass would use (curve, removal density, keyword density, DON ramp) for greedy move selection — no training data needed. | Not started |
| 5 | Difficulty scaling on top of the heuristic opponent | "Based on a certain difficulty" is explicit in the project's purpose statement. Cheapest version: vary lookahead depth, how often the AI deliberately picks a suboptimal move, and how aggressively it blocks/attacks. Still no training data needed. | Not started |
| 6 | A move-advisor query ("what's the best move and why") | This *is* the "helper agent" half of the purpose — and it's nearly free once #4 exists, since it's the same scoring function exposed as a question instead of an action | Not started |
| 7 | A minimal guided walkthrough (even just a scripted first-few-turns explainer, not a polished tutorial UI) | "Learning tool for new players" implies *some* onboarding beyond "figure out the CLI menus yourself" | Not started |

Items 1–3 are prerequisite infrastructure (the game has to actually be correct and completable before "practice against it" means anything); items 4–7 are the purpose-defining features themselves.

## Explicitly out of scope for MVP

| Item | Why it's excluded |
|---|---|
| Training the AI on real played games | The purpose statement's "hopefully trained on games played by players" is a *direction*, not a near-term deliverable — no played-game dataset exists yet, and one won't exist until something like item 4 above has real usage. Building toward this is correct; claiming it as part of the MVP is not. |
| Simulating a specific deck archetype's playstyle (Phase 6) | Asked about explicitly when scoping this: **yes, this is too broad for MVP as a whole**, but the deck-archetype version (e.g. "play like a Red Zoro aggro list") is meaningfully more tractable than simulating one specific human, because it only needs games *pooled across everyone who played that decklist*, not one person's full history. Still gated on having a corpus of played games at all — see README.md's Phase 6 data caveat. Simulating one specific human player is data-starved enough that it isn't recommended as scoped, full stop. |
| CFR/MCTS via OpenSpiel, or any self-play training infrastructure | A materially bigger lift than a heuristic opponent, and not necessary to satisfy "practice against a difficulty-tuned AI" — heuristics get you there first. Worth doing *after* MVP if the heuristic ceiling turns out to be too low. |
| JavaFX GUI, Tmux UI, online play, tournament mode, story mode, replay/broadcast tooling | All real roadmap items (Phases 2.5, 5, 7, 8 in README.md), none of them required for the core learn/practice loop to work over a terminal. |

## Definition of done

MVP is complete when all of the following hold simultaneously, not individually:

- [ ] All Phase 3 engine-completion items in `memory/phase3.md` are checked off and re-verified (not just "the code looks done" — re-run `CardCompatibilityReport` and confirm near-100% playability, not the current 87.5%)
- [ ] `GameFactory` deals real compiled cards, not synthetic test decks
- [ ] An automated test plays a full game to completion via the actual engine entry points and asserts a winner is declared correctly
- [ ] A player can select an opponent difficulty and the AI's play measurably changes with it (not just a label)
- [ ] A player can ask for a move suggestion at any legal decision point and get a reasoned answer, not just a bare card name
- [ ] A new player with no prior exposure to the CLI can get through their first game with the built-in walkthrough, without needing to read source code or ask someone else how the menus work

## Relationship to the full roadmap

```
Phase 0 ──► Phase 1 ──► Phase 2        [done — current repo state]
              │
              ▼
Phase 3 (engine completion, finished)  ─┐
GameFactory real decks                  │
End-to-end match validation             ├──►  MVP
Heuristic AI opponent + difficulty      │
Move advisor                            │
Minimal walkthrough                    ─┘
              │
              ▼
Phase 2.5 (Tmux UI) ──► Phase 4 (full tutorial/Self-vs-Self polish) ──► Phase 5 (GUI, online, tournament)
              │
              ▼
Phase 6 (deck/player simulation — data-gated) ──► Phase 7 (Story Mode) ──► Phase 8 (Replay & TCG TV)
```

Everything below the "MVP" box is real, intentional roadmap — just not required to call the project a usable product.
