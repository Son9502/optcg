# Phase 2.5 Roadmap — Enhanced CMU (Tmux UI + IPC Layer)

A purely terminal-based upgrade. The rules engine is **completely unchanged** — only the presentation layer is extended. This phase also builds the local socket IPC layer that the Phase 3 GUI will reuse.

## Architecture

```
GameEngine ──► GameState ──► GameStateServer (local socket)
                                    │
                    ┌───────────────┼─────────────────┐
                    ▼               ▼                 ▼
             BoardRenderer     ChatLog.java      [Phase 3]
             (ANSI/Tmux)      (chat.log tail)   JavaFX GUI
```

## Layout (3 panes)

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

## Items

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | `GameStateServer` — local socket server | pending | Broadcasts serialized `GameState` JSON snapshots to all connected clients after every state mutation. Uses Jackson (already a dep). Hook: call `server.broadcast(gs)` at the end of each `GameState` mutating method, or from `GameEngine` after each action. |
| 2 | `GameStateClient` — base socket client | pending | Connects to the local socket; receives and deserializes `GameState` snapshots. Base class for `BoardRenderer` and later the JavaFX GUI client. |
| 3 | `BoardRenderer` — ANSI board pane | pending | `GameStateClient` subclass. Renders `GameState` as ANSI/Unicode text to the board pane (Pane 0). Redraws on each snapshot received. Shows: opponent field + leader, your field + leader, life counts, DON!! counts. |
| 4 | `TmuxLauncher` — session setup | pending | Programmatically creates and configures the tmux session on startup: 3 panes, correct sizes, starts `BoardRenderer` in Pane 0, tails `chat.log` in Pane 2, runs `CliController` in Pane 1. |
| 5 | `ChatLog` — append-only chat | pending | Shared append-only `chat.log` file. Both players write via `/say <message>` from the actions pane. Chat pane tails the file. |
| 6 | `GameState.snapshot()` | pending | Serializes the current `GameState` to JSON for broadcast. Uses Jackson. Must be a shallow, read-only view — not a deep copy (deep copy deferred to Phase 4 AI work). Fields: both players' zones (field, hand size, life count, don count), active player, current phase. Hand contents hidden from opponent view. |

## New files

- `engine/ui/GameStateServer.java` — local socket server
- `engine/ui/GameStateClient.java` — base client
- `engine/ui/cli/BoardRenderer.java` — ANSI board renderer
- `engine/ui/cli/TmuxLauncher.java` — tmux session setup
- `engine/ui/cli/ChatLog.java` — chat file writer/tailer

## Key constraint

`CliController` (Pane 1) is **unchanged** — normal stdin/stdout. The socket layer is additive only. All existing tests continue to pass because nothing in the engine changes.
