---
name: javabytes-convert
description: Convert or translate JavaScript, TypeScript or a web app to Java using JavaBytes' honest-subset pipeline. Use when the user asks to convert, translate, or port JS/TS code to Java, or pastes a GitHub repo URL expecting a Java version.
---

# Converting JavaScript to Java with JavaBytes

The translation pipeline produces a **running application** (Spring Boot REST + JPA + chosen UI) — not just sources. Every construct is either compiled and verified, or listed honestly as manual work.

## Get the code where the tools can read it

The tools take local paths inside the sandbox (the roots the client
advertised, or `JAVABYTES_ALLOWED_DIRS`). A pasted GitHub URL is not yet code:

1. Shallow-clone it into the workspace: `git clone --depth 1 <url>` into the
   working directory or a subdirectory the user agrees to.
2. Name the cloned path in every later step.
3. If the repo cannot be cloned (private, or the sandbox forbids writes), ask
   for a zip or the relevant files. Never promise conversion of code you
   cannot read.

## Two conversion paths

### A. Single file / small logic unit → code translation
- `analyze_javascript` on the target path.
- `translate_javascript` per unit the analysis deemed expressible.
- Compile-check with `javac` when a JDK is available; if no JDK, state unverified.

### B. Multi-file web app (`package.json`, framework directories) → **full working app**
This is the flagship pipeline. It requires a database.

## Pipeline (web app → running app)

1. **Ask** — If the target is a browser webapp, **ask the human** which UI strategy:
   - `keep-frontend` (default for webapps): original HTML/CSS/JS untouched (pixel-identical), backend → Spring Boot REST re-pointed.
   - `javafx`: desktop JavaFX client + Spring Boot REST backend (different platform, not web).
   - `spring-boot-vaadin`: all-Java browser UI (approximate design).
   - `auto`: only when no human present; must state its pick and why.
   - The agent **never silently rewrites a working frontend**.

2. **extract_data_models** — Parse source (mongoose, prisma, sequelize, plain SQL, JSON stores) into schema types: entities, fields, logical types, confidence (high/med/low) + honest `limitations` arrays.

3. **models_to_schema** — Propose normalized DDL from extracted models.
   - Pass-through rule: if already in strongest applicable normal form → "no changes proposed".
   - Verification list: medium AND low confidence fields flagged for human review; high passes silently.

4. **generate_database** — Execute proposed DDL against a **user-supplied JDBC URL** (creates-only, no migration).
   - Refuses existing-table collision without explicit `confirm=true` (ADR 0005).
   - Credentials embedded in the URL; no separate user/password params.

5. **translate_webapp** — Generate the complete Maven project file map:
   - Spring Boot REST + JPA entities + Spring Data JPA repositories + CRUD controllers.
   - Per-target: `keep-frontend` (static assets served as-is), `javafx` (desktop client + per-entity CRUD views + HTTP client), `spring-boot-vaadin` (browser), `auto`.
   - Output = `files` map (path → content) + `limitations` array + `README.md` with run instructions.
   - **Optional params**: `scan_only=true` (returns analysis + migration plan without generating scaffold), `report_path` (writes detailed TRANSLATION-REPORT.md with Summary, Scaffold Files, Limitations, Browser APIs, Data-Centric Generation, Written Files).
   - **Maven Wrapper 3.3.2** embedded in generated Spring Boot/JavaFX projects (mvnw, mvnw.cmd, .mvn/wrapper/).

6. **Prove** — The generated project **must compile AND start**:
   - `mvn -B package` (compiles)
   - `mvn spring-boot:start` (or `java -jar` for JavaFX) + endpoint health check
   - If no JDK on the machine, state that compilation/start is **unverified** — do not imply success.

## Report format

Every conversion ends with three lists:

- **Converted** — files and classes emitted, and where they were written (or `files` map keys for translate_webapp).
- **Manual work** — each skipped or approximated construct, with the reason analysis gave.
- **Unverified** — anything that was not compile-checked or start-checked.

**New in v1.1.0**: When `report_path` is provided, `translate_webapp` emits a detailed `TRANSLATION-REPORT.md` with:
- Summary table (files/lines/scaffold/written/limitations)
- Scaffold Files Generated table
- Limitations list
- Browser-Bound Files table (from analysis.browser_apis)
- Data-Centric Generation details (entities parse, JDBC URL)
- Written Files list

Never silently drop a construct, and never present plausible-but-non-compiling code as done.

## Refusals

- Do not run `translate_webapp` before `extract_data_models` + `models_to_schema` on the same target.
- Do not convert what analysis cannot type. When the code leans on dynamic behaviour — `eval`, prototype mutation, heavy metaprogramming — name coexistence options (GraalJS, Rhino, JSweet, GWT) instead of forcing a rewrite.
- Do not call the migration complete while manual items remain.
- Do not silently choose a UI strategy — ask the human (or state `auto`'s reasoning explicitly).
- `translate_javascript` `write_to` **must** end with `.java` (e.g., `.../MyClass.java`). Directory paths are rejected to prevent silent overwrites (collision bug: multiple units writing to `core.java`).

## Done when

- Analysis ran on the target.
- `extract_data_models` → `models_to_schema` → `generate_database` → `translate_webapp` executed in order.
- The generated project compiles (`mvn -B package` success) AND starts (endpoint responds).
- Every emitted file either compiles or is listed as unverified.
- The manual-work inventory covers every skipped construct.

---

See also `javabytes-tool-routing` for the rest of the catalog, and `javabytes-setup` if the server is not connected.