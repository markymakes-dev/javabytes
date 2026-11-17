# JavaFX is the default UI strategy

Decided 2026-11-17 (markymakes-dev): for JS/TS → Java conversions the default UI
strategy is `javafx` — the JavaFX desktop shell target. This supersedes the
**default** in ADR-0004 (keep-frontend). `keep-frontend`, `spring-boot-rest` and
`spring-boot-vaadin` remain explicit, user-selectable options; Vaadin is never a
silent fallback.

Why: the owner's conversions target desktop applications (the Shifts/Shiftee
migration produced a `shifts-javafx` desktop project), so the expected outcome of
a convert run is a native desktop app. The ask-first wizard from issue #16 still
applies whenever a human is driving the conversion: all four strategies are
presented with honest one-line trade-offs, with JavaFX preselected as the
recommendation. The classifier fallback for fullstack/unknown browser apps
(previously `spring-boot-vaadin`) becomes `javafx`.

Contradicts ADR-0004 (keep-frontend is the default UI strategy) deliberately.
ADR-0004's core concern was silent UI rewrites without user consent; pairing the
new default with the issue #16 wizard keeps the choice explicit. ADR-0004's other
rules still stand: generated projects must compile AND start regardless of
strategy, and `auto` must state which strategy it picked and why.

## Considered options

- **Keep keep-frontend as the default (ADR-0004):** rejected by the owner —
  conversions are expected to produce desktop apps, not preserved web frontends.
- **Silent auto-pick without a wizard:** rejected — see issue #16.

## Consequences

- The `TranslateWebappTool` classifier fallback for fullstack/unknown + browser
  APIs changes from `spring-boot-vaadin` to `javafx` (tracked in issue #16).
- The `javabytes-convert` agent skill documents the ask-first wizard with JavaFX
  as the recommended default; keep-frontend is demoted to an explicit option.
- The `javafx` target must be first-class: its pom template, the compile-and-start
  guarantee, and tests cover it like any other strategy.
