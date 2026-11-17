# migrate is the vocabulary; translate_* keeps working until 2.0

Decided 2026-11-17 (markymakes-dev): JavaBytes' user-facing vocabulary for its JS/TS→Java pipeline is **migrate**, not convert. The capability is branded **java-anything by JavaBytes**, inspired by [rust-anything](https://github.com/erkamyaman/rust-anything) (MIT). New wire names use the `migrate_` prefix; the existing `translate_*` names stay functional, marked deprecated, and are removed no earlier than 2.0. Docs, skill descriptions, README and the writeup say "migrate" immediately.

Why: "convert" promises a one-shot transformation — exactly the over-promise the honesty posture refuses. "Migrate" describes what the tool family actually does: an incremental loop (analyze → plan → port unit by unit → record the original's behavior → replay against the port → gate) in which the application keeps working the whole way. The rename is positioning, and it should not cost users anything: existing client configs, Glama's config-driven deploy, `server.json`, the MCP registry listing and every e2e test reference `translate_javascript` / `translate_webapp`.

## Considered options

- **Hard rename now (wire names change in the next release):** rejected — breaks every existing config for zero functional gain; would orphan the npm install base and registry entries.
- **Docs-only rename, wire names untouched forever:** rejected — the tool family is growing real `migrate_*` operations (plan/record/replay/gate/report); a vocabulary that never reaches the wire cannot describe them.

## Consequences

- New tools ship only under `migrate_*` names (`migrate_plan`, `migrate_record`, `migrate_replay`, `migrate_gate`, `migrate_report`).
- `translate_*` tools gain a deprecation note in their tool descriptions; removal is a 2.0 change, announced in the changelog of the release that deprecates them.
- The `javabytes-convert` agent skill is superseded by a `javabytes-migrate` skill (the convert skill stays available until the migrate skill reaches parity).
- Attribution: docs carry a line crediting rust-anything (MIT) as the methodology reference — mirroring the credit rust-anything gives Matt Pocock's skills.
