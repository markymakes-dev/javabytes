---
name: javabytes-ui
description: Design-system-aware UI generation with the JavaBytes MCP server — tokens, UI code, CSS, JavaFX CSS conversion, and the anti-slop review gate. Use when generating or restyling UI code, converting web CSS to JavaFX, extracting a design system from a screenshot/repo, or reviewing generated UI for genericness.
---

# JavaBytes ui (design-system-aware UI work)

Generate and restyle UI **with a design system, not vibes**, using the
`javabytes` MCP server (`npx -y javabytes-mcp`). This is the shared UI layer
under `javabytes-backend` (the REST API) and `javabytes-desktop` (JavaFX).

## The loop

```
extract_design_system (or design_tokens) → generate_ui_code / generate_css
→ (JavaFX: html_css_to_javafx_css) → anti_slop_review (gate)
```

### 1. Ground the design system
- Working from an existing app/repo: `extract_design_system` pulls the real
  tokens (colors, spacing, type scale) so new UI matches what exists.
- Greenfield: `design_tokens` seeds a token set — agree the tokens with the
  user *before* generating screens; retrofitting tokens later is rewrite
  work.
- From a mock/screenshot: `analyze_ui_layout` reads structure (columns,
  cards, nav) — layout facts, not pixel-perfect claims.

### 2. Generate
- `generate_ui_code` drafts component/screen code for the chosen stack.
- `generate_css` drafts stylesheets driven by the tokens from step 1.
- **These are template generators.** Read what they wrote; they do not
  compile or run anything (the server's workflow rule for generation tools).

### 3. JavaFX bridge (desktop targets only)
- `html_css_to_javafx_css` converts web CSS to JavaFX CSS. JavaFX CSS is a
  subset (no pseudo-element selectors, different property names) — the tool
  reports what it drops; surface that list instead of shipping silently
  broken styles.
- Validate visually headless via `javafx_init_canvas` → `javafx_draw_canvas`
  → `javafx_export_image` (see `javabytes-desktop` §4).

### 4. Gate — `anti_slop_review`
Run it on every generated screen before claiming done. It flags the
genericness tells (default blue/purple gradients, emoji-as-icons, lorem
ipsum, unstyled forms, identical card grids). Treat findings like the
migration gate: fix the *input* (tokens, component code) and regenerate —
never hand-patch one screen to make the report green.

## Honesty rules (non-negotiable)

- Generated UI is a draft: it must compile and render, but "looks right" is
  the user's call. Ship with the token set and screenshots/evidence, not a
  claim of design approval.
- Never claim pixel-perfection from `analyze_ui_layout` — it extracts
  structure, not exact measurements.
- Token drift is a bug: if generated screens hardcode values instead of
  tokens, fix the generator input.

## Cross-references

- Web dashboards: `javabytes-backend` (REST API + data layer around this).
- Desktop shells: `javabytes-desktop` (JavaFX views around this).
- Porting an existing frontend: `javabytes-migrate`.
