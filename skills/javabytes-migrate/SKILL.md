---
name: javabytes-migrate
description: Migrate (not convert) a JavaScript/TypeScript codebase to Java with recorded-behavior evidence, using the JavaBytes MCP server. Use when the user asks to migrate, port, or rewrite JS/TS to Java — plan, port unit by unit, record original behavior, replay it against the port, gate, and report.
---

# JavaBytes migrate (java-anything)

Migrate JS/TS to Java with **evidence, not claims**. Every step needs the
`javabytes` MCP server (`npx -y javabytes-mcp`). Reference design:
`docs/java-anything.md`; ADRs 0014 (vocabulary), 0015 (gate = recorded
behavior), 0016 (trap catalog). Inspiration: [rust-anything](https://github.com/erkamyaman/rust-anything) (MIT).

## The loop

```
analyze → migrate_plan → [port → migrate_record → migrate_replay → migrate_gate] per unit → migrate_report
```

### 1. Plan — `migrate_plan`
Feed it the project path (or a single file, or inline `code`). It returns
units (ui / mixed / portable), dependency-ordered waves, risks, 5 numbered
starter decisions (`needs-owner`), vocabulary seed, limitations.

- **Never skip the decisions.** They are seeds; present them to the user and
  record the answers in DECISIONS.md before porting.
- Port in wave order: wave 0 units first, then dependents.
- `report_path` writes MIGRATION-PLAN.md — always persist it.

### 2. Port unit by unit (the agent writes Java)
The tooling does NOT translate code — you do. Use `translate_javascript` for
a first draft of portable units, then hand-fix. Keep the plan's honesty
rules: compile AND behave; no TODOs.

### 3. Record — `migrate_record`
Before declaring a unit ported, record the ORIGINAL under Node:

```
migrate_record { path: "<unit>.js", cases_per_function: 6 }
```

Writes `cases/<unit>.jsonl` + trap coverage (five ADR 0016 traps:
undefined_null, utf16_units, number_format, sort_order, date_parse).
Node is required; absence is `NODE_NOT_FOUND`, never a silent skip.
The recording is the ceiling — cases not recorded are behavior not proved.

### 4. Replay — `migrate_replay`
Generate the JUnit 5 replay test against your port:

```
migrate_replay { cases: "cases/<unit>.jsonl", class: "com.example.<Unit>Port",
                 project: "<generated project>" }
```

Writes `src/test/java/.../<Unit>ReplayTest.java`. One `@Test` per function;
throws-cases replay as "some exception was thrown" (JS error classes do not
map 1:1 — recorded limitation, not a bug).

### 5. Gate — `migrate_gate`
Tiered verdict on the generated Maven project:

| Tier | Runs | Pass means |
|---|---|---|
| Prove | `mvn test-compile` | the port exists and compiles |
| Gate | `mvn test -Dtest=<Unit>ReplayTest` | 100% of recorded cases replay |
| Traps | cases file scan | all five traps have recorded cases |

Verdicts: `GATE_PASS` > `GATE_PASSED_TRAPS_INCOMPLETE` >
`GATE_PASSED_WITHOUT_TRAP_EVIDENCE` > `PROVE_ONLY` / failures. Save output
as `target/migrate-gate-<unit>.json` so `migrate_report` includes it.

### 6. Report — `migrate_report`
Renders MIGRATION-REPORT.md from recorded evidence only: per-unit cases,
trap coverage, replay-test presence, gate verdicts, remaining work.

## Honesty rules (non-negotiable)

- Say **migrate**, not convert: the vocabulary is migration (ADR 0014).
- Never claim "the app is converted". Claim exactly what the gate proved:
  which units, how many cases, which traps, which verdict.
- Environment-dependent behavior (clock, locale, filesystem) is only proved
  on the machine that recorded the cases.
- Unportable recorded values (`__function`, `__symbol`, `__deep`) fail the
  replay loudly by design — restructure the ported function instead of
  weakening the comparator.

## Desktop / webapp targets

- Webapp-first strangler: keep the original frontend (ADR 0004) and take
  over routes unit by unit — `translate_webapp` with the keep-frontend
  target generates the Spring Boot shell.
- Desktop: JavaFX is the default UI strategy (ADR 0013); port behind the
  REST API seam the plan's coexistence decision names.
