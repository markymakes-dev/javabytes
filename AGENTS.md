# JavaBytes

A general-purpose Java MCP server. It speaks JSON-RPC 2.0 over stdio, with the
protocol hand-rolled on Jackson and no MCP SDK dependency. The catalog holds 74
tools: 44 enabled by default and 30 opt-in generators.

User-facing install, configuration and per-tool documentation live in
`README.md` and `npm/README.md`. This file is for agents working on the code.

## Build and verify

JDK 21 or newer is required. `JAVABYTES_RUN_DB_TESTS=1` opts into the six live
MySQL integration tests, which need `JAVABYTES_DB_URL`, `JAVABYTES_DB_USER` and
`JAVABYTES_DB_PASSWORD`.

```bash
mvn -B package          # compile, run tests, shade target/javabytes.jar
mvn -B test             # 70 unit tests only
cd npm && npm test      # 7 launcher tests
```

The npm package ships a copy of the shaded jar, so `target/javabytes.jar` must be
rebuilt before packing or publishing or the tarball carries a stale server.

On the maintainer's Windows machine neither `java` nor `mvn` is on `PATH`: set
`JAVA_HOME` to a JDK 21+ install and invoke Maven by its full path.

## Agent skills

### Issue tracker

GitHub issues in `markymakes-dev/javabytes`, driven with the `gh` CLI. See
`docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage roles mapped to default label names. See
`docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` at the root, decisions in `docs/adr/`. See
`docs/agents/domain.md`.

## Navigation pointers

### oh-my-opencode-slim (OMOS)

- **Config:** `~/.config/opencode/oh-my-opencode-slim.json` (preset: `nvidia-nim-direct`)
- **Agents:** Orchestrator, Explorer, Oracle, Council, Designer, Fixer (+ optional Observer)
- **Presets:** `nvidia-nim-direct`, `nvidia-free`, `deepseek-free`, `glm-free`, `opencode-go`
- **API Key:** Set `NVIDIA_API_KEY` in Desktop Settings → Environment Variables AND in `~/.env.nvidia`
- **Run in Desktop:** Set API key → `/preset nvidia-nim-direct` → `ping all agents`
- **Commands:** `/plan`, `/tdd`, `/code-review`, `/security`, `/deepwork`, `/verify`, `@explorer`, `@oracle`, `@fixer`, `@designer`, `@observer`
- **Marketplace:** `bunx oh-my-opencode-slim@latest marketplace install <agent>`
- **Companion:** `bunx oh-my-opencode-slim@latest companion install` (floating window)
- **Web UI:** `opencode web --port 4096` → http://localhost:4096

### opencode TTY Requirement

⚠️ **opencode requires TTY** — run in real terminal (PowerShell/CMD) or opencode Desktop app. This environment (windowless service) cannot run opencode.
