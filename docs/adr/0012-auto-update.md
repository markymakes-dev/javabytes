# ADR 0012: User Auto-Update Mechanism for javabytes-mcp

**Date:** 2026-11-16
**Status:** Declined (superseded by npx cache behavior)

## Decision

**No auto-update mechanism will be implemented.** The current `npx -y javabytes-mcp` installation method already provides automatic updates via npm's cache TTL (~24 hours).

## Rationale

1. **npx users (recommended)** — `npx -y javabytes-mcp` refetches `latest` from registry after cache expiry. Zero code needed.
2. **Global install users** — Run `npm update -g javabytes-mcp` manually (one command). Document this.
3. **MCP ecosystem** — No stdio MCP server implements background auto-update. Remote servers update server-side.
4. **Complexity vs value** — Self-update command, background daemon, ANSI hyperlinks, MCP notifications all add code/maintenance for marginal benefit over existing npx/npm workflows.

## Current Behavior (Sufficient)

| User Type | Update Mechanism |
|-----------|------------------|
| `npx -y javabytes-mcp` | Automatic via npx cache TTL (≤24h) |
| `npm i -g javabytes-mcp` | Manual: `npm update -g javabytes-mcp` |
| Docker | Manual: `docker pull` |
| Source build | Manual: `git pull && mvn package` |

## Documentation Update

README "First Run" section already documents npx as recommended. No code changes needed.

---

*Grilling rounds 1-3 archived in this file for reference. No implementation required.*