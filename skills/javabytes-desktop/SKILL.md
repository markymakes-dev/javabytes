---
name: javabytes-desktop
description: Build JavaFX desktop applications (per-entity CRUD views over a REST API) with the JavaBytes MCP server. Use when the user asks for a desktop app, a JavaFX UI, or wants a browser webapp reshaped as a desktop shell — scaffold, extend the generated App.java, run headless-safe, and verify.
---

# JavaBytes desktop (JavaFX)

Build or reshape an app as a **JavaFX desktop client** using the `javabytes`
MCP server (`npx -y javabytes-mcp`). JavaFX is the default UI strategy for
desktop-shaped work (ADR 0013); this skill is the recipe for doing it well.

## The loop

```
decide (ask-first wizard, ADR 0013) → translate_webapp (javafx target)
→ extend App.java + views → mvn javafx:run (Prove) → verify_generated
```

### 1. Decide the strategy — ask-first wizard (ADR 0013)
When a human is driving and no target was pinned, ask before scaffolding
(radio-button question UI, as in `javabytes-migrate` §1b). JavaFX is the
preselected recommendation when the app is desktop-shaped or browser-bound UI
needs a full rewrite — but **Vaadin is never a silent fallback** and
`auto` must state its pick and why (`strategy_reason`).

### 2. Scaffold — `translate_webapp` with the javafx target
The generator produces: a Maven pom with `javafx-controls` (and the JavaFX
plugin), an `App.java` that extends `Application`, per-entity CRUD view
scaffolds over the REST API, `MIGRATION.md` + `README.md` notes, and a
`strategy_reason` naming why JavaFX was picked.

- Requires entities: run the `javabytes-backend` loop first (steps 1–2) or
  point at an existing REST API; views are generated per entity.
- The scaffold compiles and starts. `App.java` is a **shell** — the desktop
  limitation ("desktop platform, not a web technology") is real and is part
  of the generated report; carry it into the final report.

### 3. Extend the UI (the agent writes Java)
The scaffolds are starting points, not the app:

- Build real views: `TableView` + `TextField`/`DatePicker`/`CheckBox` forms
  bound to the REST client. `generate_javafx_component` drafts components —
  read the output before using it; it is a template generator.
- `generate_ui_code` and `generate_css` help with layout; convert web CSS
  with `html_css_to_javafx_css` (JavaFX CSS is a subset — the tool reports
  what it drops).
- Extract an existing web design system with `extract_design_system` /
  `analyze_ui_layout` before restyling so tokens survive the jump.

### 4. Prove it — run
- `mvn javafx:run` (or `mvn compile` + `mvn test-compile` at minimum) in the
  generated project. Headless CI: `fix_javafx_headless` reports the Monocle
  flags (`-Dglass.platform=Monocle -Dprism.order=sw`) needed to run JavaFX
  without a display — use them for automated verification, never to fake a
  desktop run.
- `javafx_init_canvas` + `javafx_draw_canvas` + `javafx_export_image` render
  a canvas headlessly and export a PNG — use them as smoke evidence when you
  cannot open a window.

### 5. Verify — `verify_generated`
Checks the project for stub classes and unwired views. Fix the generator
input, not the output.

## Honesty rules (non-negotiable)

- A generated `App.java` is a shell, not a finished desktop app. Claim
  "scaffolded + compiles + starts", never "the desktop app is done" until
  the views are real and the Prove step ran.
- Browser APIs (DOM, localStorage, service workers) do not exist in JavaFX.
  Migrating them is redesign work — enumerate what was dropped, per the
  migration vocabulary (ADR 0014).
- Canvas exports are headless renders; they prove rendering works, not that
  the interactive app behaves.

## Cross-references

- Coming from a JS webapp? `javabytes-migrate` (evidence loop) or
  `javabytes-convert` (one-shot scaffolding).
- Data layer first? `javabytes-backend` steps 1–3.
