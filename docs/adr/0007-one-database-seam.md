# One database seam

JavaBytes reaches MySQL through three competing abstractions: the concrete `MyDatabaseClient` (two constructors), `InformationSchemaReader.ConnectionSupplier`, and `GenerateDatabaseTool.ConnectionFactory`. Decided 2026-11-14 (markymakes-dev): all database access goes through a single seam — a small `DatabaseService` interface that hands out a connection per request and offers the reads the tools actually need. `MyDatabaseClient` becomes the production **adapter** behind it; a fake in-memory adapter serves tests. Every DB-accessing tool (the eight read tools, `AnalyzeSchemaNormalizationTool`'s reader, and `generate_database`) sits behind the seam.

Why: the split let the same concerns drift. The read-only contract existed in three forms (`MyDatabaseClient.READ_ONLY`, `ExplainQueryTool.SAFE_INNER`, and the `IDENTIFIER` regex copied across `SchemaCatalog`, `DescribeTableTool`, and `FunctionalDependencyAnalyzer`), so tightening one left the others lax. Worse, all eight read tools built `new MyDatabaseClient()` inside `execute()`, freezing `AppConfig.DB_URL` — a `static final` env snapshot taken at class-load. A tool could never target a database other than the one baked into the environment, and nothing could be unit-tested without a live MySQL. The production adapter plus a fake is what makes the seam real rather than hypothetical.

## Considered options

- **Keep `MyDatabaseClient` concrete and just add parameters:** rejected — it leaves the connection duplication and still gives tests no seam to cross.
- **Unify only the read-only guard:** rejected — a real improvement, but the connection duplication is the larger defect and the one that blocks per-request targeting.

## Consequences

- DB tools accept an optional `jdbc_url` (credentials embedded in the URL, matching `generate_database`'s existing precedent); omitted means the env-configured database exactly as before, so existing users are unaffected.
- One read-only guard (statement allowlist, row cap, timeout, identifier validation, backtick quoting) is shared by every read tool; the write path is reachable only through an explicit capability, never as a public ungated method.
- Guard logic and catalog loaders gain fake-connection unit tests; the live-MySQL suite narrows to what genuinely needs MySQL (dialect DDL, `information_schema` shape).
- `CreateMyDatabaseClientTool`'s generated `MyDatabaseClient.java` template is untouched — it is user-owned generated code, independent of this internal seam. The shared name is noted as a future confusion risk.
