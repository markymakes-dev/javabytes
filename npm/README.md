# @javabytes/mcp

JavaBytes is a general-purpose Java MCP server for filesystem work, MySQL
analysis, JavaFX, Spring Boot and JavaScript-to-Java migration planning. It
speaks JSON-RPC over stdio and bundles its shaded jar, so a client only needs
Node.js and Java.

## Requirements

- Node.js 18 or newer
- Java 21 or newer (`java` on `PATH`, or `JAVA_HOME` set)

## Run

```bash
npx -y @javabytes/mcp
```

The process reads MCP messages on stdin and writes only protocol messages to
stdout. Logs go to stderr.

## Agent configuration

```json
{
  "mcpServers": {
    "javabytes": {
      "command": "npx",
      "args": ["-y", "@javabytes/mcp"],
      "env": {
        "JAVABYTES_DB_URL": "jdbc:mysql://localhost:3306/appdb",
        "JAVABYTES_DB_USER": "app",
        "JAVABYTES_DB_PASSWORD": "change-me"
      }
    }
  }
}
```

Set `JAVABYTES_ALLOWED_DIRS` to a semicolon-separated allow-list when the
client does not send MCP roots.

## Environment

| Variable | Purpose |
|---|---|
| `JAVABYTES_JAR` | Use a specific shaded jar instead of the bundled one |
| `JAVABYTES_JAVA` | Use a specific `java` executable |
| `JAVABYTES_JAVA_OPTS` | Extra JVM flags, e.g. `-Xmx512m` |
| `JAVABYTES_ALLOWED_DIRS` | Fallback filesystem sandbox |
| `JAVABYTES_DB_URL` | JDBC URL, default `jdbc:mysql://localhost:3306/mydb` |
| `JAVABYTES_DB_USER` | Database user |
| `JAVABYTES_DB_PASSWORD` | Database password |
| `JAVABYTES_ENABLE` | Comma-separated tools/categories to force-enable |
| `JAVABYTES_DISABLE` | Comma-separated tools/categories to disable |
| `JAVABYTES_LOG_FILE` | Append protocol/server logs to a file |

Database tools are read-only: they accept `SELECT`, `SHOW`, `DESCRIBE`,
`EXPLAIN`, `WITH` and `TABLE` statements only.

## Local development

```bash
npm run prepack        # copy ../../target/javabytes.jar into jar/
node bin/javabytes.js  # start without npx
npm test               # launcher unit tests
```

`prepack` also runs automatically on `npm pack` and `npm publish`.

## Publishing

1. Choose a license for the package (the repository does not declare one yet).
2. `npm login`
3. `npm publish --access public`

The package name `@javabytes/mcp` was unclaimed on npm at the time of writing;
re-check with `npm view @javabytes/mcp` before publishing.
