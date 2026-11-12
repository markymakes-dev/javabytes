---
name: javabytes-setup
description: Install, connect, or troubleshoot the JavaBytes MCP server — npx launcher, release jar, OpenCode and other MCP client configuration, Java and MySQL environment variables, filesystem roots, and enabling the opt-in tool categories. Use when JavaBytes tools are missing, a client reports it as disconnected, or someone needs to set it up for the first time.
---

# Setting up JavaBytes

Two installs, one server:

- `npx -y javabytes-mcp` — the launcher locates Java, then runs the bundled jar.
- the `javabytes.jar` release asset — `java -jar javabytes.jar`.

Both need Java 21 or newer; the npx path also needs Node 18 or newer. The
launcher resolves Java from `JAVABYTES_JAVA`, then `JAVA_HOME`, then `PATH`.

## Client configuration

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

A client that takes a command array (OpenCode) wants
`"command": ["npx", "-y", "javabytes-mcp"]` and an `environment` object in place
of `env`.

Set `JAVABYTES_ALLOWED_DIRS` to a semicolon-separated list when the client does
not send MCP roots. Scope it to the project, never to a drive root: the server
refuses filesystem roots on purpose.

## Verify in order

1. `get_version` answers — the process started.
2. `list_services` lists tools — the catalog loaded.
3. `list_tables` answers — the database credentials work.

Each failure has one cause: no `get_version` means Java or the jar path; an empty
catalog means `JAVABYTES_DISABLE` excluded everything; `list_tables` failing means
the JDBC URL, user or password.

## Tool visibility

41 tools are enabled by default. The UI, design, codegen and Spring Boot
generators are opt-in: `JAVABYTES_ENABLE=all`, or a comma-separated list of
categories (`ui`, `design`, `codegen`, `springboot`) or individual tool names.
`JAVABYTES_DISABLE` takes precedence over `JAVABYTES_ENABLE`.

## Judgment calls

- Never commit real credentials; pass them through environment variables or a
  gitignored local file.
- JavaBytes is stdio only. A server reported as needing authentication is a
  remote server, not this one.
- After changing env vars or the tool lists, reconnect the server session; most
  clients launch MCP processes once and keep them.
