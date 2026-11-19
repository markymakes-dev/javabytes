---
name: javabytes-dashboard
description: Build data-centric web dashboards (Spring Boot REST + CRUD over MySQL) with the JavaBytes MCP server. Use when the user asks for a dashboard, admin panel, CRUD app, or reporting UI over a database — from schema extraction through generated entities, repositories and controllers to a verified running app.
---

# JavaBytes dashboard (data-centric webapp)

Build a **dashboard/admin/CRUD webapp** from a data model — existing database,
ORM files, or a plain description — using the `javabytes` MCP server
(`npx -y javabytes-mcp`). The loop is the 1.1.0 conversion pipeline pointed at
a greenfield target: extract → normalize → generate → prove.

## The loop

```
extract_data_models → models_to_schema → generate_database (optional)
→ translate_webapp (entities) → run + health check → verify_generated
```

### 1. Extract the data model — `extract_data_models`
Point it at the source of truth. Accepted inputs: mongoose schemas, prisma
schemas, sequelize models, plain SQL dumps, JSON. Unknown types come back as
`LogicalType.UNKNOWN` with the raw type preserved — never guess them into
something concrete.

- Working from an **existing database** instead? Skip extraction; use the
  read tools (`list_tables`, `describe_table`, `list_foreign_keys`,
  `list_indexes`) and `export_schema_ddl` to capture what is really there.
- `extract_data_models` reports per-field confidence (high / medium / low).
  Treat medium and low as questions for the user, not decisions.

### 2. Normalize — `models_to_schema`
Turns the parsed model into a proposed schema. Tools that are already in
their strongest applicable normal form pass through unchanged ("no changes
proposed" — that is success, not failure). Medium/low-confidence fields land
on a verification list; resolve them before generating.

### 3. Create the database (optional) — `generate_database`
Executes the proposed DDL against a user-supplied JDBC URL.

- **Creates-only.** It refuses to touch an existing schema without
  `confirm: true` (ADR 0005). Never work around that guard.
- No migrations/ALTERs in this tool — schema evolution is out of scope; say
  so plainly instead of hand-editing generated DDL into a migration.

### 4. Generate the app — `translate_webapp`
With entities supplied (from steps 1–2 or the live DB), the data-centric
path generates a Spring Boot project: JPA entities, repositories, real CRUD
controllers, `application.properties` (H2 by default), the full Maven file
map.

- Target selection: web dashboards default to the **keep-frontend** strategy
  (ADR 0004) when a working frontend exists; a fresh dashboard with no
  frontend is a new `spring-boot-rest` project plus whatever UI layer the
  user picks. When a human is driving and the choice is unclear, ask — the
  ask-first wizard in `javabytes-migrate` §1b applies here too (ADR 0013).
- Vaadin is never a silent fallback (ADR 0013).
- Generated code compiles and starts — the honesty rule is "compile AND
  start, no TODOs". If the generator reports a limitation, carry it into the
  final report; do not paper over it.

### 5. Prove it — run + verify
- Start the app (`mvn spring-boot:run` in the generated project) and hit the
  actuator/health endpoint; a dashboard that does not boot is not delivered.
- `verify_generated` checks the generated project for the trap classes
  (empty CRUD bodies, TODO stubs, unwired repositories). Fix the *generator
  input* if it flags something — never hand-patch output to pass.

### 6. Wire the real data
Use `springboot_generate_pagination`, `springboot_generate_validation`,
`springboot_generate_dto`, `springboot_generate_exception_handler` to harden
the generated CRUD, and `springboot_generate_actuator` for health endpoints.
Each is a template generator: read what it wrote, then adjust to the domain.

## Honesty rules (non-negotiable)

- Charts are not generated. JavaBytes produces the data layer and CRUD API;
  visualization (charts, grids) is the frontend's job — say which endpoints
  feed which widget instead of claiming rendered analytics.
- Schema confidence is per-field. "The schema is ready" means every field is
  high-confidence or explicitly user-confirmed.
- Report the database you actually generated against (H2 default vs live
  MySQL) — behavior differs, and only the tested one is proved.

## Vocabulary

Extraction, normalization, generation, verification — this loop **generates**;
it does not *migrate*. For porting an existing JS app, use `javabytes-migrate`.
