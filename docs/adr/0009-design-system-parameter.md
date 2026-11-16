# ADR 0009: Design System Parameter for Vaadin/JavaFX Targets

## Status
Accepted

## Context
The `translate_webapp` tool generates Vaadin (browser) and JavaFX (desktop) UIs as part of the conversion pipeline. Currently these use vanilla components. Users want Anthropic-informed design systems (Material, Fluent, Ant Design, custom) applied to generated UIs.

The decision: where does the design system selection live?

## Decision
Add a `design_system` parameter to `translate_webapp`:
- Type: string enum (`none` | `material` | `fluent` | `ant-design` | `custom`)
- Default: `none` (vanilla components, current behavior)
- When set, the generated Vaadin/JavaFX code uses the selected design system's component library and tokens
- The design system choice is embedded in the generated project (dependencies, imports, component usage)
- The "Prove" step (`mvn package` + `spring-boot:start` / `java -jar`) verifies the design system integrates correctly

## Consequences
- **Positive**: Generated app is self-contained and verifiable; design system is part of the atomic pipeline output
- **Positive**: Human chooses per-conversion (or `auto` states its pick and why)
- **Negative**: `translate_webapp` grows in complexity; each design system needs maintained templates
- **Negative**: Adding a new design system requires updating the tool (not a config file)

## Alternatives Considered
1. **Post-processing step**: Run a separate tool after `translate_webapp` to apply design system. Rejected — breaks atomic compile+start verification.
2. **Config file in generated project**: User edits `design-system.yaml` after generation. Rejected — not verifiable in pipeline, manual step.
3. **Separate generator tools**: `springboot_generate_vaadin_material`, etc. Rejected — fragments the pipeline, loses holistic verification.

## Related
- ADR 0004 (keep-frontend is default UI strategy)
- GLOSSARY.md: Design system, Pipeline atomicity