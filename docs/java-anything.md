# java-anything by JavaBytes — design (v1)

**Status:** approved via grilling rounds 1–2, 2026-11-17. Target release: 1.3.0.
**Reference:** [rust-anything](https://github.com/erkamyaman/rust-anything) (MIT, Erkam Yaman) — methodology inspiration, not a fork. Its own docs adapt artifacts from Matt Pocock's skills; our docs credit rust-anything the same way.

java-anything is JavaBytes' migration loop for JS/TS → Java. Like rust-anything, it does **not** translate code — the coding agent writes the port. The tooling supplies what agents cannot guess honestly: where the units are and in what order to port them, a recording of the original's actual behavior, a mechanical replay gate, and a report that states its own limits.

## The loop

```
analyze → plan → [per unit: port → record → replay] → gate → report
```

- **analyze** (exists: `analyze_javascript`) — units, browser-bound vs portable classification.
- **plan** (`migrate_plan`) — units with file:line, port order by dependency, risks, numbered decisions (DECISIONS.md), shared vocabulary (GLOSSARY.md).
- **port** — the agent writes the Java unit; `translate_javascript` remains the mechanical assistant for the honest subset.
- **record** (`migrate_record`) — runs the *original* JS unit under Node on generated edge cases + the trap catalog (ADR 0016) → `cases/<unit>.jsonl`.
- **replay** (`migrate_replay`) — drives the Java port against the cases via a generated JUnit 5 test → mismatch report.
- **gate** (`migrate_gate`) — tiered verdict: Prove (compiles AND starts — unchanged) + Gate (100% replay match) + trap coverage (a recorded case per supported trap).
- **report** (`migrate_report`) — three-list output (ported / manual / unverified), coverage ceiling, DECISIONS/HANDOFF artifacts.

## Coexistence: webapp-first strangler

The app keeps working the whole way. For webapps this reuses the keep-frontend strategy (ADR 0004): the original frontend ships untouched and the Spring REST backend takes over routes one unit at a time behind the same URLs — the route table is the front door, per-unit toggles are the kill switch. For JavaFX targets (the default UI strategy, ADR 0013) the front door is the REST API seam the desktop client already calls: port server units behind the API first, then the desktop shell. Library coexistence (GraalJS / REST sidecar, per the Shiftee MIGRATION.md) is documented as an architecture choice, not built in v1; a JS↔Java sidecar bridge is v2 research.

## Proof tiers

| Tier | What it proves | Mechanism |
|------|----------------|-----------|
| Prove | Port exists, wires up, boots | compile AND start (unchanged) |
| Gate | Port behaves like the original on recorded cases | JSONL cases → generated JUnit 5 replay tests (`mvn test`) |
| Traps | Divergence-prone semantics are exercised | recorded case per supported trap (ADR 0016) |

**Honesty ceiling (stated in every gate/report):** the recording is the source of truth and its ceiling — regex subtleties, floats, unrecorded inputs, and non-output side effects are captured only as far as the cases capture them. Node missing on PATH is a reported limitation, never a silent skip.

## v1 scope

- **Source:** JS/TS only. **Traps:** the five of ADR 0016.
- **Tools:** `migrate_plan` first; then `migrate_record` → `migrate_replay` → `migrate_gate` → `migrate_report`, each shipped incrementally with tests.
- **Wire names:** `migrate_*` only; `translate_*` deprecated, removal no earlier than 2.0 (ADR 0014).
- **Artifacts in the generated project:** `DECISIONS.md`, `GLOSSARY.md`, unit map, `HANDOFF.md` — same roles rust-anything gives them.

## Non-goals (v1)

Python/Go/etc. sources; UI mechanical rewrite (never — ADR 0004's core rule stands); database migration (ADR 0005 boundary holds); autonomous cutover — cutover steps stay human-executed.
