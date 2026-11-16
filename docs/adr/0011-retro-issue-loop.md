# ADR 0011: Retro-Issue Loop — Automatic GitHub Issue Creation

## Status
Accepted

## Context
The `/retro` skill identifies tooling gaps after a conversion (e.g., "translate_webapp didn't handle this JS pattern"). These findings must feed back into javabytes development. The decision: how does retro output become actionable work?

## Decision
`/retro` automatically creates GitHub issues in the javabytes repo (`markymakes-dev/javabytes`):
- Trigger: `/retro` completes in a conversion session
- Action: For each tooling gap found, `gh issue create` with:
  - Title: `[retro] <tool> — <gap summary>`
  - Labels: `retro`, `needs-triage`, `<tool-name>`
  - Body: Conversion context (source path, UI strategy, manual work inventory, scan_only analysis if available), reproduction steps, expected vs actual behavior
  - Assignees: none (triaged by `/triage` in next cycle)
- Issues are created via the existing GitHub MCP server (already configured in opencode.json)

## Consequences
- **Positive**: Zero manual filing; gaps become tracked work immediately
- **Positive**: Context preserved (source path, strategy, inventory) — reproducible
- **Positive**: Feeds existing `/triage` → `/implement` flow; no new process
- **Negative**: Requires GitHub auth (PAT/token) in the session running `/retro`
- **Negative**: Noise risk — low-value retro items create spam; mitigated by `needs-triage` label

## Alternatives Considered
1. **Local notes only**: `/retro` writes `RETROSPECTIVE.md` in conversion dir. Rejected — lost when session ends, not visible to team.
2. **Manual issue filing**: Human copies retro findings to GitHub. Rejected — forgotten, context stripped.
3. **Periodic batch import**: Collect retro files weekly, bulk-create issues. Rejected — delay, context decay.

## Related
- GLOSSARY.md: Retro-issue loop
- `/triage` skill (processes `needs-triage` issues)
- GitHub MCP server config (opencode.json)