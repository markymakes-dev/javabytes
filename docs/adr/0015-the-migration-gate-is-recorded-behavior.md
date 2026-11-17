# The migration gate is recorded behavior, not just compile-and-start

Decided 2026-11-17 (markymakes-dev): the proof model for java-anything is **tiered**. Prove — the existing "generated project compiles AND starts" guarantee — stays as the floor. The new headline gate is **record/replay**: run the original JavaScript unit under Node on generated edge cases plus the semantic trap catalog (ADR 0016), save the observed behavior as JSONL cases, replay every case against the Java port, and gate the unit on a 100% match. Replay runs as plain `mvn test` via a generated JUnit 5 test per unit.

Why: compile-and-start proves the port exists and boots; it cannot prove the port *behaves* like the original. rust-anything's core insight — "the recording is the source of truth" — is the strongest honest answer to "what does done mean?" that a migration tool can offer. It also draws a hard, honest line: the recording is the ceiling. Regex subtleties, floats, unrecorded inputs and non-output side effects are exactly as captured (or missed) by the cases; the gate report must say so rather than imply total equivalence.

## Considered options

- **Replace Prove with record/replay:** rejected — compile-and-start catches whole classes of failure (missing deps, broken wiring) that behavior replay on a unit cannot see; both tiers are needed.
- **Methodology reference only, no replay tooling:** rejected — without a mechanical gate, "matches the original" is an agent's claim, not evidence.

## Consequences

- New tools: `migrate_record` (runs the original unit under Node → `cases.jsonl`), `migrate_replay` (drives the Java port against the cases → mismatch report), `migrate_gate` (tiered verdict: Prove + Gate + trap coverage).
- The recorder requires Node on PATH (the original runs in Node; the port runs in the JVM). Absence of Node is a reported limitation, never a silent skip.
- Case files and generated replay tests live in the generated project (`src/test/java/<unit>ReplayTest.java` + `cases/<unit>.jsonl`) so they run in CI like any other test.
- `migrate_gate` output states the coverage ceiling explicitly: which inputs were exercised, which traps have recorded cases (ADR 0016), and what the recording cannot catch.
