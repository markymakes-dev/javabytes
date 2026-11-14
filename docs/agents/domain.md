# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root, or
- **`GLOSSARY-MAP.md`** at the repo root if it exists: it points at one `GLOSSARY.md` per context. Read each one relevant to the topic.
- **`docs/adr/`**: read ADRs that touch the area you're about to work in. In multi-context repos, also check `src/<context>/docs/adr/` for context-scoped decisions.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo (this one):

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0003-schema-export-is-faithful.md
│   └── 0004-keep-frontend-is-the-default-ui-strategy.md
└── src/
```

Multi-context repo (presence of `GLOSSARY-MAP.md` at the root):

```
/
├── GLOSSARY-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── GLOSSARY.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── GLOSSARY.md
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Vocabulary hazard: "tool" vs "agent skill"

This repo uses **tool** for a Java class that implements one MCP tool
(`src/main/java/com/javabytes/mcp/tools/**`, e.g. `AnalyzeSchemaNormalizationTool`).
Agent skills (`SKILL.md` documents under `skills/`) are a different thing —
those are agent-facing workflow documents, not code. When either sense is
ambiguous in output, say "tool implementation" or "agent skill" instead of "skill".

## Before changing behavior, read these

- **`GLOSSARY.md`** at the repo root — use its vocabulary; don't drift to synonyms it explicitly avoids.
- **`docs/adr/`** — read ADRs that touch the area you're about to work in, and **flag any contradiction** explicitly rather than silently overriding:

> _Contradicts ADR-0004 (keep-frontend as default UI strategy), but worth reopening because…_

If the concept you need isn't in the glossary yet, either you're inventing language the project doesn't use (reconsider) or there's a real gap — note it for `/domain-modeling`.

## .scratch/ convention

Exploration debris (throwaway prototypes, scratch queries, one-off scripts) lives in `.scratch/` at the repo root — never committed, never pushed. `.scratch/` is gitignored.
