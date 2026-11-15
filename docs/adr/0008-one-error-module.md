# One error module

JavaBytes serializes tool results into the JSON-RPC envelope `{success, error, message}` in three disconnected places: `ErrorHelper` (SCREAMING_CASE codes, 18 lines), `MyDatabaseClient.error()` (private lowercase codes, lines 130–136), and `McpServerRegistry` (wraps exceptions as `SKILL_NOT_FOUND`/`SKILL_EXECUTION_ERROR`). The transport layer then infers `isError` by sniffing `result.has("success")` (McpServerMain:353). Decided 2026-11-15 (markymakes-dev): one `Outcome` module owns the entire error-code namespace; tools return typed outcomes; the registry/transport serializes them in one place.

Why: the split let the same concepts drift. `ErrorHelper` uses `MISSING_PARAM`, `INVALID_IDENTIFIER`, `PATH_NOT_ALLOWED`; `MyDatabaseClient` has `read_only_violation`, `multiple_statements`, `database_error`; the registry produces `SKILL_NOT_FOUND`, `SKILL_EXECUTION_ERROR` — and a single failure can pass through all three vocabularies (e.g., DB error → `MyDatabaseClient.database_error` → tool re-wraps as `ErrorHelper.DB_ERROR` → registry sees `success=false` and passes through). The sniffing logic at 353 is a convention, not a contract, and the catch block at 356 is effectively dead code because tools never throw — they return error nodes. One module concentrates the taxonomy, removes the sniffing, and makes the envelope construction explicit.

## Considered options

- **Keep the three layers and just add a common base:** rejected — the duplication is the problem; the fix is deleting the copies.
- **Make tools throw exceptions instead of returning error nodes:** rejected — the "no exception escapes execute()" discipline is intentional (tools must return the envelope themselves); throwing would make the wire protocol implicit.

## Consequences

- New `com.javabytes.mcp.Outcome<T>` record: `success(T data)` / `failure(String code, String message)` — tools return this instead of hand-building `ObjectNode`.
- `ErrorHelper` shrinks to a thin delegate over `Outcome` (the SCREAMING_CASE vocabulary stays; `MISSING_PARAM`, `DB_ERROR`, etc. are still the codes tools use).
- `SchemaCatalog.rows()` throws typed `CatalogException` (with the error message) → mapped to `DB_ERROR` by the outcome layer; no more `IllegalStateException` bubbling through the registry.
- `McpServerRegistry` wraps `SKILL_NOT_FOUND`/`SKILL_EXECUTION_ERROR` as typed outcomes; these remain the only wire codes the registry itself produces.
- `McpServerMain:353` deleted; transport writes `isError` from `outcome.isFailure()`.
- Wire protocol **unchanged**: the JSON-RPC envelope is byte-identical `{success, error, message}`; tool names, existing error codes, and the enabled-tool set stay exactly as before.