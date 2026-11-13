# 0003 — Schema export is faithful; proposed DDL is generated

- **Date:** 2026-11-13
- **Status:** accepted

## Context

JavaBytes has two ways DDL-related output reaches users. The
`export_schema_ddl` tool reports a live MySQL database's own DDL via
`SHOW CREATE TABLE`. The 1.1.0 conversion pipeline (`extract_data_models` →
`generate_database` → `translate_webapp`) needs DDL rendered from the new
shared schema model (`com.javabytes.mcp.schema`), which does not preserve
server-side details such as storage engine, charset, collation,
auto-increment counters, partitioning or column comments.

## Decision

Export tools report the database's own DDL verbatim (`SHOW CREATE TABLE`).
An export's job is truth, so `export_schema_ddl` stays untouched and its
output contract does not change.

Generated DDL comes only from the new model→DDL writer (`MySqlDdlWriter`),
built for the future `generate_database` tool. Every statement it emits is
marked "proposed — verify before executing", because it is derived from a
model that cannot carry all server-side details.

Rejected alternative: re-rendering exports through the schema model. That
would silently drop details the model does not represent (engine, charset,
collation, partitioning, comments) while looking authoritative, so it was
rejected.

## Consequences

- `export_schema_ddl` keeps its exact `SHOW CREATE TABLE` output; its tests
  stay as they are.
- Consumers who want normalized DDL use the schema model plus
  `MySqlDdlWriter` and review the "proposed — verify" markers before
  executing anything.
- The schema model stays an information-preserving intermediate: raw types
  are kept verbatim, and types that cannot be mapped losslessly stay
  `UNKNOWN` instead of being coerced.
