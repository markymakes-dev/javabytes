# JavaBytes

A general-purpose Java MCP server. It speaks JSON-RPC 2.0 over stdio, with the
protocol hand-rolled on Jackson and no MCP SDK dependency. The tool catalog is
derived at startup from the per-tool registrations (ADR 0006) — never restate
tool or test counts here; read them from `README.md` or the test output.

User-facing install, configuration and per-tool documentation live in
`README.md` and `npm/README.md`. This file is for agents working on the code.

## Build and verify

JDK 21 or newer is required. `JAVABYTES_RUN_DB_TESTS=1` opts into the six live
MySQL integration tests, which need `JAVABYTES_DB_URL`, `JAVABYTES_DB_USER` and
`JAVABYTES_DB_PASSWORD`.

```bash
mvn -B test             # unit tests — output prints the current count
cd npm && npm test      # launcher tests
mvn -B package          # full build; shades target/javabytes.jar
```

The npm package ships a copy of the shaded jar, so `target/javabytes.jar` must be
rebuilt before packing or publishing or the tarball carries a stale server.

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

## Releases

Tag `vX.Y.Z` and push — CI builds the shaded jar, attaches it to the GitHub
Release, and publishes npm via OIDC trusted publishing. The post-tag
verification sequence (npm CDN lag, Official MCP Registry republish, isLatest
check) lives in `docs/agents/release-runbook.md`.
