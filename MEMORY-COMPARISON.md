# Agent Memory Comparison — Bloat vs Utility

## Current: `@agentmemory/mcp` (in opencode.json)
- **What it does**: Persistent key-value + sessions + recall
- **Bloat**: Low — single MCP server, simple API
- **Good for**: Basic preferences, decisions, gotchas

## Alternatives (Least Bloat → Most Bloat)

| Repo | Bloat | Why |
|------|-------|-----|
| **`engram`** (Gentleman-Programming) | **Lowest** | Single Go binary, SQLite FTS5, no deps, TUI/CLI/HTTP/MCP. No background daemon. |
| **`okf-agent-memory`** | Low | Pure Go, git-native, BM25 <300µs, embedded MCP, no external DB. |
| **`aoci-code`** | Low-Med | Go binary, Git-versioned codebase map + schema. Focused on code intelligence. |
| **`projectmem`** | Med | Python, focuses on "don't repeat failed approaches" — narrower scope. |
| **`xerj`** | High | Rust binary, Elasticsearch-compatible, Corpus Hub, full-text+vector+hybrid. Overkill for most. |
| **`nocturne_memory`** | High | PostgreSQL, graph-like, visual TUI, rollback. Heavy. |

---

## ECC Includes Memory

**ECC has built-in memory:**
- `continuous-learning` / `continuous-learning-v2` skills
- `instinct-*` commands (status, import, export, evolve, promote)
- Hook: `Session Context` + `Session Compacting`
- Works with any MCP memory server too

**If you install ECC on opencode** → you get memory *without adding another MCP server*.

---

## Recommendation

| If you want... | Do this |
|----------------|---------|
| **Simplest, no bloat** | Keep `@agentmemory/mcp` — it works |
| **Lightweight upgrade** | Swap to `engram` (single Go binary, SQLite FTS5) |
| **Code intelligence** | Add `aoci-code` alongside current |
| **Full ECC memory** | Install ECC opencode adapter (includes instincts/learning) |
| **Maximum power** | `xerj` (but only if you need Elasticsearch-scale search) |

---

**My vote:** Stay with `@agentmemory/mcp`. It's not bloating your context — billion-context compresses the *history*, not the memory server. The memory server just stores key-value; billion-context handles the conversation compression.

**ECC on opencode** gives you memory *plus* 26 agents, 26 commands, 11 skills, 15 hooks — but only if you want that whole package.