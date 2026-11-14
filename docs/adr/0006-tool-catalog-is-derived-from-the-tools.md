# The tool catalog is derived from the tools; uncataloged tools fail closed

JavaBytes describes its tools in two places: the tool implementations registered in `McpServerMain`, and a hand-maintained string-keyed catalog (`ToolMetadata` + `ToolDescriptions`). Decided 2026-11-14 (markymakes-dev): each tool declares its own spec (title, description, category, default-on/off, wire annotations such as `readOnlyHint` and `destructiveHint`), the catalog becomes a derived index built from the registered tools at startup, and the parallel description file is deleted.

Why: the string-keyed split let the two sides drift invisibly. A tool registered but missing from the catalog used to fall through `ToolMetadata.fallback()`, which classified it as enabled by default with the default read-only hint — a forgotten catalog entry silently turned an opt-in DDL-executing tool ON. From now on, a registered tool with no spec is **disabled** (never advertised in `tools/list`) and produces a loud startup warning; a spec that maps to no registered tool fails the consistency test. The catalog can only be wrong in the safe direction.

## Considered options

- **Hard-fail startup on any inconsistency:** rejected — a mismatch should never take down an already-working server; the warning plus the CI consistency test is the gate.
- **Package-scanning manifests instead of the explicit `registerTool` list:** rejected for now — the typed, greppable registration list is worth keeping; manifests remain a separate future candidate.

## Consequences

- Every tool class carries its own spec; `ToolMetadata` is derived from registered tools, `ToolDescriptions` is deleted.
- New tools need one edit (the class + its registration), not six string-keyed ones.
- A consistency test in CI asserts every registered tool has a spec and every spec maps to a registered tool.
- `generate_database` is reclassified honestly: `readOnlyHint=false`, `destructiveHint=true` — its confirm-gate and collision refusal (ADR 0005) are the safety mechanisms, not the hint flags.
- Wire behaviour is unchanged: tool names, error codes (`SKILL_NOT_FOUND`, `SKILL_EXECUTION_ERROR`), and the enabled-tool set for all cataloged tools stay exactly as they were.
