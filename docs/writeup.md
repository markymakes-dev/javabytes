# The MCP server for Java shops that refuses to promise

*Shipping JavaBytes — a general-purpose Java MCP server with an honest JS→Java conversion pipeline.*

AI codegen fails in code review, not in the demo. It demos beautifully, gets merged, and then someone spends a weekend discovering that half the output never compiled. So when we built JavaBytes, we inverted the pitch: **the review report is the product**. A converter whose output you can trust *because it tells you what it didn't do*.

## Why Java, and what it is

The MCP ecosystem was TypeScript and Python first; Java shops had nothing general-purpose to build on, so we built it. JavaBytes is an MCP server written in Java — JSON-RPC 2.0 over stdio, **hand-rolled on Jackson, no MCP SDK**. MIT-licensed, one `npx -y javabytes-mcp` away, and listed in the official MCP Registry.

Under the hood: 75 tools, 45 on by default, 30 opt-in generators (UI, design, codegen, Spring Boot — off unless you ask with `JAVABYTES_ENABLE`). Tool families cover sandboxed filesystem access, read-only MySQL analysis, JSON, JavaFX, diagnostics, and the JS→Java conversion set (`analyze_javascript`, `translate_javascript`, `translate_webapp`).

## Hand-rolling the protocol (why no SDK)

The official MCP Java SDK exists — we chose not to use it. We wanted zero dependency risk on a fast-moving spec, full control of the stdio loop, and a shaded jar that stays small (~15.8 MB, bundled into the npm package).

The discipline that makes hand-rolling safe: **stdout is reserved exclusively for the protocol**. Every log line goes to stderr, enforced in the registry with a comment and tests. One stray `printf` to stdout by a library would corrupt the stream — so we made it structurally impossible to be casual about it. The loop is single-threaded, handles protocol-version negotiation, and runs the MCP roots handshake as loop-local state.

The whole server started as one ~393-line main class. It has since been divided into transport, protocol-session, schema-renderer, and composition-root modules — the original class is now a thin facade. The seam lines followed the smells: the duplication that predicted the schema module, the instanceof chain that predicted the schema renderer.

## The honesty posture

Three rules the server enforces on itself, asserted in tests:

1. **The honest subset.** Conversion tools emit templates and only for constructs that provably compile. Everything else goes on the manual-work list — `analyze_javascript` runs first, and *its findings are that list*. Translation never silently drops a construct; never presents non-compiling code as done.
2. **No inferred facts.** The MySQL functional-dependency analysis never infers dependencies from data — only caller-declared FDs, and it reports the strongest normal form *provable* (2NF→3NF→BCNF) or says nothing. Generated DDL carries `-- proposed — verify before executing` on every statement.
3. **Provenance over confidence theater.** Every fact is **Discovered** (live DB catalog), **Parsed** (from source), or **Proposed** (generated — always "verify"). Confidence is high/medium/low, never a number. `varchar(191)` parses as STRING *with the raw preserved*; `int unsigned` becomes UNKNOWN with the raw preserved. Lossless-or-nothing.

The gate at the end of the pipeline runs before compile: `verify_generated` checks deterministic properties — no TODO/FIXME, no raw types, Javadoc on public APIs — so slop never even reaches `mvn`.

## The pipeline

The conversion contract is **Ask → Extract → Propose+create → Generate → Prove**:

- **Ask:** the agent presents UI strategies and waits. It will not silently rewrite a working frontend — `keep-frontend` is the default: your HTML/CSS/JS stays untouched and pixel-identical while the backend becomes Spring Boot REST re-pointed at the API. JavaFX and Vaadin are opt-in, and `auto` is only for when no human is present — then it must state what it picked and why.
- **Extract:** `extract_data_models` parses mongoose, prisma, sequelize, plain SQL, or JSON stores into one schema model, with per-field confidence.
- **Propose + create:** normalized DDL is proposed, then executed against a user-supplied JDBC URL. `generate_database` **creates; it does not migrate** — it refuses to touch an existing schema without an explicit confirm flag.
- **Generate:** a complete Maven-standard file map — Spring Boot + JPA backend, repositories, controllers, and the chosen UI.
- **Prove:** **compiles AND starts.** The definition of done, not a stretch goal.

JavaFX gets its own honesty note: it is a desktop platform, not a web technology — no real CSS, no browser. Choosing it means a desktop app, presented as such.

## Engineering Java devs actually care about

- **Read-only by contract.** The analysis path is a regex allowlist (SELECT/SHOW/DESCRIBE/DESC/EXPLAIN/WITH/TABLE), single statement, `setReadOnly(true)`, 1000-row cap, 30-second timeout, identifier backtick-quoting. The write path was born *behind an explicit capability* — not by loosening the regexes.
- **A real sandbox.** Filesystem tools are sandboxed to the client's roots (the MCP roots handshake can replace env fallback mid-session), filesystem roots are refused, and containment is symlink-aware.
- **Tested like it matters.** 148 Java tests + 19 launcher tests + 39 stdio e2e checks, plus 6 live-MySQL integration tests gated behind `JAVABYTES_RUN_DB_TESTS=1` so CI never needs a database.
- **Java 21 floor** — nothing in the codebase needs more, and the launcher *tells you* what it resolved and from where.

First run looks like this:

```
PASS java: 25.0.5 via JAVA_HOME (C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe)
PASS jar: present (15.8 MB)
INFO db: JAVABYTES_DB_URL is not set - MySQL tools will be unconfigured
PASS update: up to date (1.2.1)
4 checks, 0 problems
```

`config` emits a ready-to-paste server block for OpenCode, Claude Desktop, Cursor, or VS Code. A self-update command was considered and rejected — `npx -y` already auto-resolves; doctor prints the update command instead.

## Shipping journey

npm 2FA and agents do not mix: `npm publish` from a non-TTY shell cannot complete web auth — the flow throws, and the auth URL prints masked as `***`. The fix was structural: **trusted publishing** — GitHub Actions OIDC, provenance signed into the sigstore transparency log. A release is now one `git tag vX.Y.Z` push; the jar build, GitHub release, and npm publish all happen downstream, automatically.

The official MCP Registry taught us its ownership gotcha: for npm packages, ownership is proven by a `mcpName` field in the *published* package — which means 1.0.0 could never be listed retroactively; it took a 1.0.1 republish. Worth an article of its own.

The directory landscape churns monthly — Smithery closed to submissions, a popular list archived, Glama indexes 120k+ servers with config-driven deploys, Docker's catalog needs a repo Dockerfile (theirs is handshake-verified) and has no "developer-tools" category. The durable move is the official registry, not chasing every portal. (And yes, [punkpeye/awesome-mcp-servers](https://github.com/punkpeye/awesome-mcp-servers) literally documents an agent fast-track: put 🤖🤖🤖 in your PR title.)

## Numbers, honestly

75 tools (45 default-on), 148 + 19 + 39 tests, an npm package bundling a 15.8 MB shaded jar, and **998 downloads in the first full week** — a number we cite with its caveat: registry counts include mirrors and CI pulls, so read it as "real adoption exists" rather than a victory lap. Pre-adoption, stated honestly.

Solo-maintainer economics in one line: batch the safe updates (Dependabot grouped minor+patch), pause the risky majors (JavaFX 21→27 and mysql-connector 9→26 are deliberately held), take the trivial ones the day they arrive (JUnit 5→6), automate the release, and make the doctor tell users what's wrong.

## The takeaway

The most valuable test suite here is the one that asserts the tool *says what it didn't do*. A converter that refuses to promise is more useful than one that promises and lies.

```bash
npx -y javabytes-mcp
```

Repo: [github.com/markymakes-dev/javabytes](https://github.com/markymakes-dev/javabytes) · npm: `javabytes-mcp` · MIT
