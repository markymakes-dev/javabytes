---
name: javabytes-backend
description: Build the data layer and REST backend for an app (Spring Boot + JPA + MySQL) with the JavaBytes MCP server. Use when the user asks for a dashboard, admin panel, CRUD app, or API backed by a database — whether they have an existing schema or only an idea (Phase 0 seeds the entities) — from schema extraction through generated entities, repositories and controllers to a verified running app. Pairs with javabytes-desktop (JavaFX client) or the user's own frontend.
---

# JavaBytes backend (data pipeline → REST API)

Build the **data layer and REST backend** — the part a web dashboard, admin
panel, or JavaFX desktop client sits on top of — from a data model (existing
database, ORM files, or a plain description), using the `javabytes` MCP
server (`npx -y javabytes-mcp`). The loop is the 1.1.0 conversion pipeline
pointed at a greenfield target: extract → normalize → generate → prove.

**Naming note:** this skill is a *layer* (backend), not a platform.
"Dashboard" is a layout pattern (see `javabytes-ui`), buildable on either
platform; the web browser and the desktop are platforms (the latter is
`javabytes-desktop`). The pipeline below feeds all of them — the REST API
it generates is what both clients talk to.

## The loop

```
(idea → seed entities) → extract_data_models → models_to_schema
→ generate_database (optional) → translate_webapp (entities)
→ run + health check → verify_generated
```

### 0. Starting from just an idea? Seed it first

The pipeline consumes an artifact, not a wish — `extract_data_models` needs
something to parse. When the user has only an idea ("a booking system for my
apartment studio"), **drafting the seed entities is your job, in the
session** — the server intentionally has no idea-to-schema tool (no
server-side agency). Write one small file, then continue at step 1:

1. Pick the 2–4 core entities the idea is *about* — the nouns that will
   hold rows, not every noun the user said. For the studio example:
   `Studio`, `Booking`, `Guest`.
2. Name the obvious references between them (a booking points at a studio
   and a guest) and the fields each one needs to be useful on day one.
3. Write them as **plain SQL DDL or JSON** — the two friendliest inputs for
   `extract_data_models`. A DDL seed is just a fence like:

   ```sql
   CREATE TABLE studio (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     name VARCHAR(120) NOT NULL,
     nightly_rate DECIMAL(10,2) NOT NULL
   );
   CREATE TABLE guest (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     full_name VARCHAR(120) NOT NULL,
     email VARCHAR(190) NOT NULL
   );
   CREATE TABLE booking (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     studio_id BIGINT NOT NULL,
     guest_id BIGINT NOT NULL,
     check_in DATE NOT NULL,
     check_out DATE NOT NULL,
     total_amount DECIMAL(10,2) NOT NULL
   );
   ```

4. Show the seed to the user before generating — it is a draft of *their*
   system, and the per-field confidence rules below still apply. Anything
   you were unsure about lands on the verification list at step 2; say so.
5. Then run the standard chain (steps 1–5). If the user will want a UI,
   that is where the ADR 0013 ask-first question (desktop vs web client)
   comes in — ask once, before scaffolding.

Do not gold-plate the seed. The loop below normalizes, questions, and
generates — a small honest seed beats a large invented one.

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

- Target selection: when a working frontend exists, the **keep-frontend**
  strategy (ADR 0004) preserves it and takes over routes; a fresh backend is
  a new `spring-boot-rest` app. When a human is driving and the choice is
  unclear, ask — the ask-first wizard in `javabytes-migrate` §1b applies
  here too (ADR 0013).
- Vaadin is never a silent fallback (ADR 0013).
- Generated code compiles and starts — the honesty rule is "compile AND
  start, no TODOs". If the generator reports a limitation, carry it into the
  final report; do not paper over it.

### 5. Prove it — run + verify
- Start the app (`mvn spring-boot:run` in the generated project) and hit the
  actuator/health endpoint; a backend that does not boot is not delivered.
- `verify_generated` checks the generated project for the trap classes
  (empty CRUD bodies, TODO stubs, unwired repositories). Fix the *generator
  input* if it flags something — never hand-patch output to pass.

### 6. Wire the real data
Use `springboot_generate_pagination`, `springboot_generate_validation`,
`springboot_generate_dto`, `springboot_generate_exception_handler` to harden
the generated CRUD, and `springboot_generate_actuator` for health endpoints.
Each is a template generator: read what it wrote, then adjust to the domain.

## Honesty rules (non-negotiable)

- Charts and pages are not generated. JavaBytes produces the data layer and
  CRUD API; visualization is the client's job (browser frontend or JavaFX
  views) — say which endpoints feed which widget instead of claiming
  rendered analytics.
- Schema confidence is per-field. "The schema is ready" means every field is
  high-confidence or explicitly user-confirmed.
- Report the database you actually generated against (H2 default vs live
  MySQL) — behavior differs, and only the tested one is proved.

## Vocabulary

Extraction, normalization, generation, verification — this loop **generates**;
it does not *migrate*. For porting an existing JS app, use `javabytes-migrate`.
