# The semantic trap catalog starts at five and grows on evidence

Decided 2026-11-17 (markymakes-dev): `migrate_record` seeds its generated edge cases with a fixed catalog of **five semantic traps** for JS→Java — the highest-frequency divergence points between JavaScript semantics and Java semantics:

1. **`undefined` vs `null`** — JS has both; Java has only `null`. Records how the original behaves on missing keys, missing args, and sparse arrays.
2. **UTF-16 code units vs Java chars/strings** — JS string indexing and `.length` count UTF-16 code units; astral-plane characters (emoji etc.) behave differently in Java. Records lengths, indexing and iteration of astral input.
3. **Number formatting** — `Number.prototype.toFixed/toPrecision`, exponent formatting, `-0`, integer precision beyond 2^53 vs Java's `BigDecimal`/`String.format` defaults.
4. **Sort and iteration order** — JS array sort coercion (default string sort) and object key insertion order vs Java's `Comparator` contracts and `HashMap` ordering.
5. **Date parsing** — `new Date(string)` leniency and timezone behavior vs `java.time` strict parsing.

Why a starter set rather than a big-bang catalog: each supported trap must carry a recorded failing case requirement in the gate (a port that changes behavior on a supported trap without a passing replay case fails the gate), and every trap multiplies recorder surface. Five covers the traps observed in real conversions (Shiftee rounds 1–2); growth is evidence-driven.

## Considered options

- **Ship with ~10 traps immediately:** rejected — scope creep before the loop exists; the marginal traps (truthiness coercion, prototype mutation) are rarer in real codebases.
- **No trap catalog in v1 (user inputs only):** rejected — the traps above diverge silently and are exactly what an agent porting by hand gets wrong with confidence.

## Consequences

- The trap catalog lives in one module (`migrate_record`'s case generator) with one test per trap.
- Adding a trap is a **minor** version bump (new capability, no wire change); removing or changing a trap's recorded semantics is a **major** bump.
- `migrate_gate` output lists each supported trap with its recorded-case status (present/passing/failing-mismatch) so the coverage claim is inspectable, not asserted.
