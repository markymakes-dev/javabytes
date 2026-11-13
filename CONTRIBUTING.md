# Contributing to JavaBytes

Thanks for looking. JavaBytes is a general-purpose Java MCP server, maintained by one person,
so the bar for merging is: **does this make the server more useful to someone who isn't us?**
If a change only makes sense for one project, it belongs in that project.

## Before you write code

Open an issue first for anything larger than a typo. The triage labels say what happens next:

| Label | Meaning |
|---|---|
| `needs-triage` | Not looked at yet |
| `needs-info` | Waiting on a reporter |
| `ready-for-agent` | Specified well enough to implement without further questions |
| `ready-for-human` | Needs a judgement call or manual verification |
| `wontfix` | Out of scope, with the reason in the thread |

## Setup

- **JDK 21+** — the build targets 21 (`<java.version>` in `pom.xml`); newer JDKs are fine
- **Maven 3.9+**
- **Node 18+** — only for the `npx` launcher in `npm/`

```bash
git clone https://github.com/markymakes-dev/javabytes.git
cd javabytes
mvn -B package          # compile, test, shade target/javabytes.jar
cd npm && npm test      # 7 launcher tests
```

Six additional tests run against a **live MySQL server**, and only when you opt in:

```bash
export JAVABYTES_RUN_DB_TESTS=1
export JAVABYTES_DB_URL='jdbc:mysql://localhost:3306/javabytes_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export JAVABYTES_DB_USER=root
export JAVABYTES_DB_PASSWORD=...
mvn -B test
```

They create and drop their own schema; point them at a scratch database, never at anything you care
about.

## What CI checks

`.github/workflows/ci.yml` runs on every push and pull request:

- `mvn -B package` on **JDK 21** — unit tests plus the shaded jar, so shade/relocation breakage fails the build
- `npm test` on **Node 18 and Node 22** — launcher unit tests only; 18 is the declared floor in `engines`, 22 is current

CI deliberately does **not** run the live MySQL tests or the stdio end-to-end harness: they need a
database and credentials. Run them locally before touching the database tools.

## Adding a tool

The server is 71 tools today; the catalog and the implementation are separate concerns and both
must agree.

1. **Implement a `Tool`** in `src/main/java/com/javabytes/mcp/tools/<area>/`.
   `stdout` is reserved for JSON-RPC — log through `System.err` or the error helper, never
   `System.out`.
2. **Describe it** in `src/main/java/com/javabytes/mcp/utils/ToolMetadata.java`: name, category,
   description, input schema. Tests in `ToolMetadataTest` and `ToolDescriptorTest` assert the
   catalog stays consistent, so a missing entry fails fast.
3. **Register it** in `McpServerMain` with `registerTool(registry, new YourTool())`.
4. **Test it** under `src/test/java/...`. A tool that touches the world (filesystem, database,
   process) needs a test that exercises the sandbox or connection, not just a happy-path string.
5. **Document it** in `README.md` under *Tool catalog* and update the counts if they move.

Tools whose only job is rewording a prompt are conveniences. The tools that carry the project are
the ones that touch the world: the filesystem sandbox, live MySQL, JavaFX rendering, project
inspection. Adding a convenience generator is fine, but it belongs in a category that is **off by
default** — see `DEFAULT_DISABLED_CATEGORIES` in `ToolMetadata`.

Note that a disabled-by-default tool still costs every user a schema in their context window if it
is enabled. Prefer fewer, sharper tools.

## The honesty rule

This is the one non-negotiable standard in the codebase.

The JavaScript-to-Java translation tools (`analyze_javascript`, `translate_javascript`,
`translate_webapp`) translate a **typed, compilable subset**. When the translator cannot prove an
input is inside that subset, it must report the construct as manual work required — never emit
code that looks complete and does not compile, and never describe an arbitrary web application as
mechanically portable.

The same standard applies to analysis tools. Normalisation and dependency analysis only report
what the evidence supports: only caller-declared functional dependencies, only the primary key as
a candidate key, and the strongest form that follows. Sample rows are bounded and stated. If you
are unsure, say less.

## Style

- Java 21; `var` for locals where the type is obvious, explicit types in signatures
- No new dependencies without a reason in the PR — the shaded jar ships to users, and a
  dependency is a licence obligation as well as bytes
- Errors carry a stable code (`ErrorHelper.error("CODE", ...)`) so clients can branch on them
- Comments explain *why*. The code already says what
- Keep the 41/71 enabled/opt-in split honest in the README whenever it moves

## Commits and pull requests

- One logical change per commit; message in the imperative mood (`Add EXPLAIN plan tool`)
- Run `mvn -B package` and `cd npm && npm test` before pushing
- In the PR body: what the change does, how you verified it, and anything a reviewer should look
  at hardest
- If the change is user-visible, update `README.md`; if it is a decision someone might revisit,
  consider `docs/adr/`

## Releasing

Releases are cut from `main` by pushing a version tag:

1. Bump `<version>` in `pom.xml` and `version` in `npm/package.json` to the same value.
2. Commit, push, and wait for CI to go green.
3. Tag and push:

   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

`.github/workflows/release.yml` then builds the shaded jar on JDK 21, **fails if the tag does not
match the pom version**, checks that the jar declares a `Main-Class`, and creates the GitHub Release
with `javabytes.jar` attached alongside its SHA-256.

Publishing to npm stays manual: npm's passkey flow needs a real terminal and the maintainer's
approval.

```bash
cd npm
npm publish --access public
```

The workflow can also be run by hand (`workflow_dispatch`) against an existing tag — the repair path
if the release job fails after the tag is already pushed.

## Working with agents

`AGENTS.md` describes the build/verify loop, the triage labels and where domain docs live. Agent
skills live under `skills/` and are installed into the contributor's client, not loaded by the
server. If you change how a tool should be sequenced or when it must refuse, that belongs in a
skill, not in the tool description.

## Licence

JavaBytes is MIT. By contributing you agree your contribution is licensed under the same terms;
there is no CLA to sign and no copyright assignment.

Bundled third-party code keeps its own licence — see the *License* section of `README.md`.
