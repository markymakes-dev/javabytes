---
name: javabytes-defense
description: Generate a defense-ready documentation report for an app built with JavaBytes — title, introduction, objectives, scope and limitations, features with evidence, database design with data dictionary, architecture, and verification results. Use when the user asks for project documentation, a defense report, a school requirement, or "explain what my system can do".
---

# JavaBytes defense (documentation from evidence)

Produce the documentation a student can hand to a panel and defend —
Title, Introduction, Objectives, Scope, Features, Database Design,
Architecture, Verification — for a system built through the `javabytes`
MCP server (`npx -y javabytes-mcp`). The document is **compiled from
evidence, not written from memory**: every claim traces to a tool result,
a file in the generated project, or a run the student watched happen.

This skill usually runs *after* `javabytes-backend` (and optionally
`javabytes-desktop`): it documents what those loops produced. It can also
document an existing database-driven app — same rule, evidence first.

## The loop

```
gather evidence (schema + generated project + run results)
→ render sections (each cites its evidence)
→ read the draft against the project (nothing claimed that isn't there)
→ deliver DOCUMENTATION.md
```

### 1. Gather the evidence first

Collect all of it before writing a word — the document is only as honest
as this pile:

- **Schema:** the `models_to_schema` output if the loop was run in this
  session; otherwise live reads (`list_tables`, `describe_table`,
  `list_foreign_keys`, `list_indexes`) or `export_schema_ddl`.
- **Normalization story:** `analyze_schema_normalization` — which forms
  hold, what was proposed and why.
- **The generated project:** read the actual files (`read_text_file`) —
  entities, repositories, controllers, `application.properties`. The
  endpoint list comes from the controllers, not from memory.
- **Proof of life:** did the app boot? (`mvn spring-boot:run` + health
  endpoint). What did `verify_generated` report — clean, or findings?
  A document for a defense that never ran the app is a liability, not
  an asset; say so and run it first.
- **Origin:** what came from Phase 0 seeding (the user's idea), what was
  extracted, what was hand-written. Provenance is a defense asset.

### 2. Render the document

Write it as Markdown to the project root as `DOCUMENTATION.md` (or the
filename the school requires). Section format:

**Title page** — system name (ask the user; the folder name is a
fallback), author(s), date, one-line stack summary
(e.g. JavaFX + Spring Boot + MySQL).

**1. Introduction** — the problem the system solves and for whom, in the
user's own words (go back to the original idea from Phase 0), then one
paragraph on what was built. No feature list here — that's section 4.

**2. Objectives** — one general objective, then specific objectives.
Each specific objective maps to a concrete feature in section 4; if an
objective has no feature behind it, cut it or build it — never let the
document promise what the system doesn't do.

**3. Scope and Limitations** — in scope: the entities, the CRUD
operations, the client(s). Limitations: copy them **verbatim** from the
tool outputs — the `translate_webapp` limitations array, verify findings,
H2-vs-MySQL differences. Panels respect a limitation that is stated
plainly; they shred one that is hidden.

**4. Features** — a table, one row per feature:
`feature | where it lives (endpoint or view) | how it is verified`.
The endpoint column comes from reading the generated controllers; the
verification column cites the actual evidence (booted + health 200,
verify_generated clean, replayed request). No aspirational rows.

**5. Database Design** — an ER overview in prose (entities and their
relationships from `list_foreign_keys` / the schema model), then a **data
dictionary**: one Markdown table per entity — column, type, key, nullable,
notes. Close with the normalization outcome from
`analyze_schema_normalization` (which form, what was proposed).

**6. System Architecture** — the layering and *why*: client → REST →
JPA → database. State the reason in defense-ready terms: credentials and
business rules live in one place, every client speaks the same API, the
backend can move from localhost to a server without rewriting clients.
This is the answer to "why not direct JDBC?" — pre-write it.

**7. Technology Stack** — one line per layer with a reason (JDK 21,
Spring Boot, JPA/Hibernate, MySQL or H2, JavaFX). Reasons beat names at a
defense.

**8. Testing and Verification** — what was actually proved and how:
the boot + health evidence, verify_generated output, any gates run. If
something was checked on H2 but the school wants MySQL, this section says
exactly that — the difference between tested and assumed is the theme.

**9. Conclusion** — what exists now, what it does not do (mirrors
section 3), and the natural next step. Short.

**Appendix — anticipated panel questions.** Draft the likely probes with
honest answers: Why REST instead of direct JDBC? Why this normalization
form? What did the generator produce vs what did you hand-write? What
happens if two users save at once? Where do the database credentials
live? Each answer cites a section of this document.

### 3. Read the draft against the project

Before delivering: walk the Features table row by row against the
controllers and views; walk the data dictionary against
`describe_table` output. A claim that has no file or tool result behind
it gets deleted, not footnoted.

## Honesty rules (non-negotiable)

- Every Features row carries evidence; unevidenced features do not ship.
- Limitations are verbatim from tool outputs — no softening, no omission.
- Generated vs hand-written provenance is stated; claiming hand-crafted
  work for generated code is the one error a panel never forgives.
- The document documents the system **as built** — if the student wants
  more scope, that is new work through the pipeline, not new prose.
- Markdown is the deliverable; converting to the school's template
  (Word/PDF, page formatting) is the student's formatting step — say so.

## Vocabulary

This loop **documents**; it does not build. Building is
`javabytes-backend` (idea → seed → schema → REST) and `javabytes-desktop`
(JavaFX client). If the evidence pile is empty, stop and run the build
loop first — a defense document cannot precede the system it defends.
