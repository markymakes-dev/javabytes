# EOC — Everything Opencode

*Does not exist. This is a joke file.*

---

## What we actually have (and it's pretty good):

| Component | What it does |
|-----------|--------------|
| **billion-context** | Context compression (compress/decompress/search_context) — active ✅ |
| **JavaBytes skills** | `javabytes-convert`, `javabytes-setup`, `javabytes-tool-routing` — 3 skills ✅ |
| **opencode.json** | 12 MCP servers configured (javabytes, glama, github, rea, agentmemory, etc.) ✅ |
| **AGENTS.md** | Navigation pointers + request economy + environment lessons (needs pruning) |
| **Custom skills** | Can add any skill via `@skill` tool ✅ |

---

## What we don't need to build:

- ❌ 71 agents (ECC has them; we use subagents)
- ❌ 314 skills (we install what we need via `@skill`)
- ❌ Hook runtime (opencode has native hooks)
- ❌ Marketplace (we use git + npm)

---

## If you *really* want EOC:

```bash
# The "framework" is already here:
# 1. opencode.json → MCP servers
# 2. AGENTS.md → pointers
# 3. .agents/skills/ → your skills
# 4. billion-context → compression
# 5. subagents → delegation
```

**That's it.** The "framework" is the opencode harness itself + your config.

---

*File created to memorialize the joke. Delete anytime.*