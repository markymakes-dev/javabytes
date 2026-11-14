# JavaBytes

[![CI](https://github.com/markymakes-dev/javabytes/actions/workflows/ci.yml/badge.svg)](https://github.com/markymakes-dev/javabytes/actions/workflows/ci.yml)

A general-purpose Java MCP (Model Context Protocol) server that speaks JSON-RPC 2.0 over stdio.

It exposes Java-oriented tools for file access, MySQL, JavaFX canvas rendering/export, project
diagnostics, and code/CSS generation. It has no MCP SDK dependency: the protocol is implemented
directly with Jackson.

> **Scope note:** tools that only reword a prompt (most `*_generate_*` tools) are conveniences.
> The tools that add real capability are the ones that touch the world: the file sandbox, live
> MySQL, JavaFX rendering, and project inspection.

## Requirements

- Node.js 18+ (only for the `npx` launcher)
- JDK 21 or newer (built and tested with JDK 25)
- Maven 3.9+ (only to build from source)

## Install with npx

The `javabytes-mcp` package bundles the shaded jar, so a client only needs Node.js and Java:

```bash
npx -y javabytes-mcp
```

It resolves `java` from `JAVABYTES_JAVA`, then `JAVA_HOME`, then `PATH`, and the jar from
`JAVABYTES_JAR` or the bundled copy.

Client config — Claude Desktop and most clients:

```json
{
  "mcpServers": {
    "javabytes": {
      "command": "npx",
      "args": ["-y", "javabytes-mcp"],
      "env": {
        "JAVABYTES_DB_URL": "jdbc:mysql://localhost:3306/appdb",
        "JAVABYTES_DB_USER": "app",
        "JAVABYTES_DB_PASSWORD": "change-me"
      }
    }
  }
}
```

OpenCode (`~/.config/opencode/opencode.json`) uses a different shape — `mcp`, a `command` array,
and `environment` rather than `env`:

```json
{
  "mcp": {
    "javabytes": {
      "type": "local",
      "command": ["npx", "-y", "javabytes-mcp"],
      "environment": {
        "JAVABYTES_DB_URL": "jdbc:mysql://localhost:3306/appdb",
        "JAVABYTES_DB_USER": "app",
        "JAVABYTES_DB_PASSWORD": "change-me"
      },
      "enabled": true
    }
  }
}
```

## Build from source

```bash
mvn clean test        # 70 unit tests (6 more run live only when JAVABYTES_RUN_DB_TESTS=1)
mvn package           # produces target/javabytes.jar (shaded, runnable)
```

Run from source:

```bash
mvn -q exec:java
```

Run the shaded jar:

```bash
java -jar target/javabytes.jar
```

stdout is reserved for JSON-RPC. All logging goes to stderr.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `JAVABYTES_ALLOWED_DIRS` | working dir + JVM temp dir | Fallback filesystem sandbox roots |
| `JAVABYTES_ENABLE` | category defaults | Enable/disable tools by group or name |
| `JAVABYTES_DISABLE` | category defaults | Disable specific tools |
| `JAVABYTES_LOG_FILE` | unset | Optional JSON-RPC trace log |
| `JAVABYTES_DB_URL` | `jdbc:mysql://localhost:3306/mydb` | MySQL JDBC URL |
| `JAVABYTES_DB_USER` | `user` | MySQL user |
| `JAVABYTES_DB_PASSWORD` | `password` | MySQL password |

Example:

```bash
JAVABYTES_ALLOWED_DIRS=/home/me/projects java -jar target/javabytes.jar
```

## Tool groups and feature flags

Tools are grouped, and a group can be toggled without touching code:

| Group | Default | Contents |
|---|---|---|
| `files` | on | sandboxed filesystem tools |
| `database` | on | read-only MySQL query/list/describe |
| `system` | on | system info, time conversion, version, tool list |
| `json` | on | JSON path query |
| `javafx` | on | off-screen canvas, drawing and PNG export |
| `analysis` | on | code/UI/security/TODO analysis and Spring Boot inspection |
| `diagnostics` | on | evidence-based project diagnostics |
| `ui`, `design`, `codegen`, `springboot` | **off** | template generators |

A fresh server exposes **44 capability tools**; the **30 template generators** are opt-in
(`JAVABYTES_ENABLE=all` exposes all 74).

Enable or disable by group or by individual tool name:

```bash
# Add every Spring Boot and code generator
JAVABYTES_ENABLE=springboot,codegen java -jar target/javabytes.jar

# Everything, except one destructive tool
JAVABYTES_ENABLE=all JAVABYTES_DISABLE=delete_file java -jar target/javabytes.jar
```

Precedence, highest first: disabled by tool name → enabled by tool name → disabled group →
enabled group → `all` in the disable list → `all` in the enable list → the group default.
`tools/list` only advertises enabled tools; calling a disabled tool returns JSON-RPC `-32602`
with the flag needed to enable it.

Every tool descriptor also carries a `title` and MCP `annotations` (`readOnlyHint`,
`destructiveHint`, `idempotentHint`, `openWorldHint`) so clients can ask for confirmation or
auto-approve reads.

## MCP roots

If the client advertises `capabilities.roots`, the server requests `roots/list` after
initialization and again after `notifications/roots/list_changed`. Usable `file://` roots
replace the environment sandbox for the session, narrowing what file tools may touch. If the
client returns no usable roots, or the request fails, the `JAVABYTES_ALLOWED_DIRS` fallback
stays in effect. Filesystem roots such as `C:\` and `/` are refused so a misconfiguration
cannot expose the whole disk.

## Tool catalog

### Capabilities

| Tool | What it does |
|---|---|
| `query_database` | Read-only SQL (`SELECT`/`SHOW`/`DESCRIBE`/`EXPLAIN`/`WITH`), max 1000 rows |
| `list_tables` | Tables and views |
| `describe_table` | Column metadata; identifiers are validated and backtick-quoted |
| `javafx_init_canvas` | Create an off-screen canvas |
| `javafx_draw_canvas` | Draw rectangle/circle/line/text |
| `javafx_set_color` | Set fill/stroke color |
| `javafx_clear_canvas` | Clear the canvas |
| `javafx_export_image` | Snapshot to a real PNG via `SwingFXUtils` + `ImageIO` |
| `project_diagnostic` | Evidence-based dependency/configuration check |
| `analyze_springboot_project` | Spring Boot layout inspection |
| `extract_data_models` | Parse mongoose/prisma/sequelize/SQL/JSON data declarations into tables and columns, with per-field confidence and an honest limitations list |
| `models_to_schema` | Convert parsed schema models into normalized schema proposals: proposed DDL, pass-through verdicts, and a verification list of medium/low-confidence fields |
| `generate_database` | Execute proposed normalized DDL against a user-supplied JDBC URL to create the schema (creates only; refuses existing-table collision unless confirm=true) |
| `fix_javafx_headless` | Adds the Monocle property (dry run by default; `apply=true` to write) |
| `create_mysql_client` | Generates a self-contained JDBC helper that reads credentials from env |
| `get_system_info` | JVM/OS/hardware info |
| `environment_variables` | Read env vars; secret-like values masked unless `reveal=true` |

### Filesystem (sandboxed)

`read_text_file`, `write_file`, `list_directory`, `get_file_info`, `make_directory`,
`delete_file`, `search_files` (glob + optional content regex), `backup_file` (no overwrite),
`compare_files`.

### Convenience generators

`generate_code`, `generate_tests`, `generate_docs`, `refactor_code`, `check_security`,
`find_todos`, `analyze_code`, `generate_css`, `generate_ui_code`, `generate_javafx_component`,
`extract_design_system`, `analyze_ui_layout`, `html_css_to_javafx_css`, `design_tokens`,
`anti_slop_review`, `translate_webapp`, `json_query`, `convert_time`, `get_version`, `list_services`, and the
`springboot_generate_*` family (entity, repository, service, controller, DTO, properties,
security, relationship, actuator, exception handler, validation, pagination, file upload,
scheduled, async, cache, profile, docker, test).

These emit templates. They do not compile or run the result. Prefer prompting for anything
beyond their exact shape. All of them are disabled by default; enable them with
`JAVABYTES_ENABLE=ui,design,codegen,springboot` (or `all`).

## Security model

- **Path sandbox.** `AppConfig.isPathAllowed` rejects empty paths and null bytes, normalizes,
  resolves the real path of the deepest existing ancestor (so non-existent targets and symlinks
  are handled), and compares on component boundaries. `C:\dev-evil` never matches `C:\dev`.
- **Read-only SQL.** `MyDatabaseClient.query` validates the statement, refuses multiple
  statements, sets the connection read-only, caps rows,(sf and applies a query timeout.
- **Identifier validation.`describe_table` accepts only plain identifiers or `schema.table`.
- **Secret masking.`Environment variables whose names look like credentials are masked by
default. Generated MySQL clients read `DB_URL`/`DB_USER`/`DB_PASSWORD`; no password is written
into source.
- **Advisory diagnostics.`fix_all` and `fix_maven_dependencies` never rewrite the project.
  `fix_javafx_headless` writes only with `apply=true`.
- **Client roots.`When a client provides roots, they replace the environment sandbox. Roots
  that are not `file://`, are not directories, or are filesystem roots (`C:\`, `/`) are ignored.

## Tests

`mvn test` runs 70 tests covering the sandbox (traversal, null bytes, sibling-prefix),
directory creation results, glob and content search, CSS conversion, generated UI code, SQL
rejection, dependency false positives, error shapes, tool metadata completeness and
annotations, feature-flag precedence, and client-root narrowing/reset.

MySQL and JavaFX integration tests require a real database. Canvas export was verified
end-to-end over stdio (200x100 PNG, correct magic bytes). A 26-check stdio scenario also
covers roots negotiation, sandbox narrowing, `roots/list_changed`, disabled-tool errors,
launcher resolution, and annotations in `tools/list`.

## Agent skills

`skills/` holds three agent skills that make the tool catalog usable without
reading it: `javabytes-setup` (install, configure, troubleshoot),
`javabytes-tool-routing` (which tool to call, in what order, and what counts
as done) and `javabytes-convert` (the JavaScript-to-Java pipeline: clone, scope,
analyze, translate, report — with an honest manual-work inventory). Install
them into an agent with:

```bash
npx skills@latest add markymakes-dev/javabytes
```

They are plain markdown; the server is still required for the tools themselves.

## License

MIT — see [LICENSE](LICENSE).

You may use, modify, and redistribute JavaBytes commercially. In return, the only
obligation is keeping the copyright notice and licence text with copies.

The bundled `mysql-connector-j` is GPL-2.0 *with the Universal FOSS Exception*, which
permits redistribution inside an OSI-approved (MIT) program. If this project ever ships a
proprietary build, that dependency must be replaced (for example with
`mariadb-java-client`, LGPL-2.1) or licensed commercially from Oracle.