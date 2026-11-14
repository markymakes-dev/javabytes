# ADR 0005: generate_database creates; it does not migrate

**Date:** 2026-11-14 · **Decided by:** markymakes-dev · **Status:** accepted

## Core decision

The `generate_database` tool (1.1.0 ticket #4) **creates** a schema from proposed normalized DDL against a user-supplied JDBC URL. It does **not migrate** existing schemas. It refuses to run against a database containing conflicting tables unless an explicit confirm flag is supplied. ALTER/migration of existing schemas is out of 1.1.0 scope.

## Why

1.1.0's conversion pipeline produces a *proposed* schema from parsed source-app models. The database is the source of truth for `generate_database`'s input, but it must never silently overwrite or alter an existing schema — data loss risk is too high and the tool cannot know the user's intent for collision resolution.

## Consequences

- `generate_database` takes a JDBC URL + proposed DDL + optional `--confirm` flag.
- Without `--confirm`, it rejects any database where a target table already exists, returning a clear conflict error.
- Migration (ALTER TABLE, data backfill, schema evolution) is explicitly deferred to a post-1.1.0 release.
- This aligns with ADR 0003 (`export_schema_ddl` remains faithful `SHOW CREATE TABLE`) and ADR 0004 (browser webapps default to keep-frontend — no silent rewrites of working systems).
