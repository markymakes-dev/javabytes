# keep-frontend is the default UI strategy

JavaBytes converts JS/TS apps to Java, and the obvious assumption is that a converter rebuilds the UI. Decided 2026-11-13 (markymakes-dev): for browser webapps the default UI strategy is `keep-frontend` — the original HTML/CSS/JS ships untouched (pixel-identical) and the backend becomes a Spring Boot REST API that the original frontend is re-pointed at. JavaFX and Vaadin remain explicit opt-ins, and `auto` (used only when no human is present to ask) must state which strategy it picked and why.

Why: UI rewrites silently destroy design work and produce an app the user never asked for; JavaFX is a desktop platform, not a web technology, so "converting" a browser UI to JavaFX was never mechanical. When a human drives the conversion, the agent presents the strategies with honest one-line recommendations and waits for the choice; it never silently rewrites a working frontend.

## Considered options

- **All-Java by default** (Vaadin or JavaFX rendering everything): rejected — destroys existing design and oversells mechanical UI translation.

## Consequences

- `translate_webapp` gains the `keep-frontend` target (static assets served as-is; frontend re-pointed at the generated API).
- The JavaFX desktop target is user-selectable, not the default (spec issue #1, Decision A and the target table).
- Generated projects must compile AND start regardless of strategy.
