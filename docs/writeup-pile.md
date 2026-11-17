# Write-up raw material pile (JavaBytes)

Read-only quarry for the shaping session. Fragments, facts, quotes, numbers, opinions — no structure promised.
Built 2026-11-14 from the repo (`@88480e8`/`fc8bd06`) and the maintainer session history. Correct anything wrong BEFORE shaping starts.

---

## What the thing is

- JavaBytes: a general-purpose **MCP server written in Java**. JSON-RPC 2.0 over stdio, **hand-rolled on Jackson — no MCP SDK**.
- 75 tools; 45 enabled by default; 30 opt-in generators (UI/DESIGN/CODEGEN/SPRINGBOOT categories off unless `JAVABYTES_ENABLE`).
- Tool families: filesystem (sandboxed), MySQL analysis (read-only by contract), JSON, JavaFX canvas, diagnostics, Spring Boot generators, and the JS→Java conversion set (`analyze_javascript`, `translate_javascript`, `translate_webapp`).
- Install paths: `npx -y javabytes-mcp` (bundles a ~15.8 MB shaded jar; launcher resolves Java via `JAVABYTES_JAVA` → `JAVA_HOME` → `PATH`); GitHub release jar; Docker image (verified by JSON-RPC handshake); Glama hosted endpoint (zero local install); listed in the official MCP Registry (`io.github.markymakes-dev/javabytes`, provenance-signed publishes).
- MIT. Repo: github.com/markymakes-dev/javabytes. npm: `javabytes-mcp`. Solo maintainer (markymakes.dev), team consumes.

## The hand-rolled protocol (why no SDK)

- The official MCP Java SDK exists — chose not to use it. Wanted zero dependency risk on a fast-moving spec, full control of the stdio loop, and a shaded jar that stays small.
- Line-delimited JSON-RPC; **stdout is reserved exclusively for the protocol** — every log line goes to stderr, enforced in the registry with a comment and tests. One printf to stdout by a library would corrupt the stream.
- Single-threaded loop; protocol-version negotiation; MCP roots handshake as loop-local state.
- Numbers: whole server was one ~393-line main class plus small modules (pre-refactor) — then split into transport / protocol-session / schema-renderer / composition-root modules (ADR 0006–0008 + the McpServerMain division); the original class is now a thin facade.

## The honesty posture (the thesis candidate)

- Conversion tools **emit templates, do not compile/run/verify** — stated in the server's own instructions; tests assert those strings.
- `analyze_javascript` runs FIRST; its findings **are** the manual-work list. Translation never silently drops a construct; never presents non-compiling code as done.
- FD analysis **never infers functional dependencies from data** — only caller-declared FDs; reports the strongest form provable (2NF→3NF→BCNF), or says nothing.
- Generated DDL carries `-- proposed — verify before executing` on every statement.
- The vocabulary: **Honest subset** (only provably-compiling constructs) vs **Manual work** (everything else, reported, never hidden).
- Contrast with the industry default: codegen that demos well and dies in review. The pitch: a converter whose output you can trust *because it tells you what it didn't do*.

## The conversion pipeline (1.1.0, in flight)

- Contract: **Ask → Extract → Propose+create → Generate → Prove.**
  - Ask: the agent presents UI strategies and waits — never silently rewrites a working frontend.
  - Extract: `extract_data_models` parses mongoose / prisma / sequelize / plain SQL / JSON stores into one schema model, with confidence per field.
  - Propose+create: normalized DDL proposed, then executed against a **user-supplied JDBC URL**.
  - Generate: Spring Boot + JPA backend + chosen UI as a complete Maven-standard file map.
  - Prove: **compiles AND starts** — the definition of done, not a stretch goal. A `verify_generated` gate runs first: deterministic checks (no TODO/FIXME, no raw types, Javadoc on public APIs…) so slop never reaches the compile step.
- **keep-frontend is the default** (ADR 0004): original HTML/CSS/JS untouched and pixel-identical; backend becomes Spring Boot REST; frontend re-pointed at the API. JavaFX (desktop) and Vaadin (browser, all-Java) are opt-in. `auto` only when no human is present — and then it must state what it picked and why.
- **generate_database creates; it does not migrate** — refuses to touch an existing schema without an explicit confirm flag. ALTER/migration is out of 1.1.0 scope. (ADR 0005.)
- Provenance is a first-class word: **Discovered** (live DB catalog), **Parsed** (from source code), **Proposed** (generated — always "verify"). Confidence is high/medium/low, **never a number**.
- JavaFX honesty: JavaFX is **not a web technology** — no real CSS, no browser. Choosing it means a desktop app, presented as such.

## The schema model (the pipeline's currency)

- One shared representation: `SchemaModel` / `Table` / `Column` / `Relation` / `Index` + `LogicalType` enum.
- `LogicalType.fromRaw()` is **lossless-or-nothing**: `varchar(191)` → STRING + raw preserved; `int unsigned`, `enum('a','b')` → UNKNOWN + raw preserved. Never a lossy guess.
- `information_schema` reader behind a `ConnectionSupplier` seam (the one place DB creds flow); model→MySQL-DDL writer (backticks, FKs as ALTER TABLE, dedup'd names ≤64 chars).
- Deleted ~140 lines of duplicated private records/loaders from one tool when this landed — the duplication was the smell that predicted the module.

## Shipping journey (the "how it actually went" fragments)

- Official MCP Registry: ownership for npm packages is proven by a `mcpName` field in the **published** package — our 1.0.0 could never be listed retroactively; needed a 1.0.1 republish. Gotcha worth an article of its own.
- npm publish + passkey 2FA: `npm publish` from an agent shell **cannot** complete web auth (non-TTY throws; the auth URL prints masked as `***`). Real-terminal dance, later made moot by **trusted publishing**: GitHub Actions OIDC, provenance signed, sigstore transparency log. Releases are now one `git tag` push — jar + npm publish both automatic.
- Glama: listing is free; maintainer must OAuth-click (proof of repo write access); their deploy is **config-driven and does NOT use your repo Dockerfile**; build steps are an array of plain strings; the field named "CMD arguments" is actually the full start command (`["java","-jar","javabytes.jar"]`); Java installed via `apt-get install openjdk-21-jre-headless` in their debian base.
- Docker MCP catalog: needs a Dockerfile in the repo (we built and handshake-verified one), category enum has no "developer-tools" (used `devops`), Docker team reviews.
- punkpeye/awesome-mcp-servers (97.7k★) has an **agent fast-track**: put 🤖🤖🤖 in the PR title, merging is fast-tracked. (Their CONTRIBUTING literally documents it.)
- Smithery closed to submissions (Oct 31); PulseMCP paused (auto-ingests the official registry — publish there and they pick it up); appcypher's list archived. The directory landscape churns; the official registry is the keystone.
- GitHub bots: Dependabot configured with **grouped minor+patch PRs** (one batch per ecosystem for a solo maintainer) and majors held with `@dependabot ignore` — JavaFX 21→27 and mysql-connector 9→26 paused deliberately (floor decision + shaded-driver risk). JUnit 5→6 taken same day it arrived.
- CI: 3 jobs — Java 21 `mvn -B package` (tests + shaded jar), launcher tests on Node 18 AND 22 (the declared floor, not an arbitrary LTS).
- Launcher grew `version` + `doctor` + `config` subcommands: doctor checks Java resolution source + major version (WARN < 21), jar presence/size, DB env presence, best-effort latest-version check (3s timeout, offline-safe); `config` emits ready-to-paste MCP config for opencode / Claude Desktop / Cursor / VS Code. `javabytes upgrade` was considered and **rejected** — `npx -y` already auto-resolves; doctor prints the update command instead.

## Design decisions with reasons (ADR-flavored fragments)

- MIT license: adoption-first; also resolves the mysql-connector-j GPL / Universal-FOSS-Exception hazard for the shaded jar. Copyright line uses the handle markymakes.dev.
- Java 21 floor: nothing in the codebase needs >21; JavaFX animation API stable since 8; newer JavaFX lines track newer JDKs. JavaFX 21.x still patched.
- Read-only DB contract: regex allowlist (SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|TABLE), single statement, `setReadOnly(true)`, MAX_ROWS 1000, 30s timeout, identifier backtick-quoting. Write path (`generate_database`) is being born *behind an explicit capability*, not by loosening the regexes.
- File sandbox: client roots replace env fallback mid-session (MCP roots handshake); filesystem roots refused; symlink-aware containment.
- Naming: internal Java `skills` package renamed to `tools` (commit `9185eae`, 87 files) because MCP *tools* and agent *skills* are different things and the collision was confusing contributors. Wire names and error codes untouched.

## Numbers worth citing

- 75 tools / 45 default-on / 30 opt-in.
- 148 Java tests + 19 launcher tests + 39 stdio e2e checks; 6 live-MySQL integration tests gated by `JAVABYTES_RUN_DB_TESTS=1`.
- npm tarball ~14 MB bundling a 15.8 MB shaded jar (v1.2.1).
- 998 npm downloads in the first full week (api.npmjs.org, 2026-11-10 → 16; includes mirrors/CI pulls — cite with the honesty caveat).
- Glama indexes 120k+ MCP servers; ours listed with maintenance grade B, claimed, badge live.
- One release = `git tag vX.Y.Z` + push. Everything downstream automatic.

## Fresh captures (2026-11-17, v1.2.1)

- `javabytes doctor` (local run — 4 checks, 0 problems):

  ```
  PASS java: 25.0.5 via JAVA_HOME (C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe)
  PASS jar: present (15.8 MB)
  INFO db: JAVABYTES_DB_URL is not set - MySQL tools will be unconfigured
  PASS update: up to date (1.2.1)
  4 checks, 0 problems
  ```

- npm downloads: 998/week (first full week post-publish).

## Opinion fragments (usable as thesis seasoning)

- "The MCP ecosystem has 120k listings and a directory landscape that changes monthly; the durable move is the official registry, not chasing every portal."
- "AI codegen fails in code review, not in the demo. So we built the review report into the product."
- "A converter that refuses to promise is more useful than one that promises and lies."
- "The most valuable test suite here is the one that asserts the tool *says what it didn't do*."
- Solo-maintainer economics: batch the safe updates, pause the risky majors, automate the release, and make the doctor tell users what's wrong.

## Gaps (need user input or get cut)

- [x] Audience (user, 2026-11-17): **both, lead with Java devs** — Java devs curious about MCP primary; AI-tooling people secondary.
- [x] Real usage numbers: 998 downloads/week (first full week) — write pre-adoption honestly, caveat included.
- [x] Terminal capture of `doctor` output exists (Fresh captures above); a polished screenshot still optional.
- [x] npm 2FA saga depth (user, 2026-11-17): **one tight paragraph** — the non-TTY gotcha + OIDC resolution, no blow-by-blow.
- [x] Origin (user, 2026-11-17): **gap in the MCP ecosystem** — draft quote to tune: "The MCP ecosystem was TypeScript and Python first; Java shops had nothing general-purpose to build on, so we built it."
