---
name: javabytes-tool-routing
description: Choose and sequence JavaBytes MCP tools for MySQL schema analysis, index and foreign-key review, query-plan reading, normalization checks, and JavaScript-to-Java migration. Use when a task involves a database schema, a slow query, table design, or porting a JS/TS project to Java, or when the JavaBytes tool list looks ambiguous.
---

# Routing JavaBytes tools

JavaBytes reads databases; it never writes them. `query_database` accepts only
`SELECT`, `SHOW`, `DESCRIBE`, `EXPLAIN`, `WITH` and `TABLE`, caps results at 1000
rows with a truncation notice, and enforces a query timeout. Anything that
changes data or schema comes out as text for a human to apply.

## Establish the catalog before promising anything

`list_services` returns the tools this server actually exposes. The UI, design,
codegen and Spring Boot generators are opt-in, so a missing tool means disabled,
not broken: name the `JAVABYTES_ENABLE` value that turns it on (for example
`JAVABYTES_ENABLE=springboot`) and keep working with what is enabled.

## Schema review

Order matters; each step narrows the next.

1. `list_tables` — never guess a table name.
2. `describe_table` — columns, types, primary key.
3. `list_indexes` — existing coverage before proposing any index.
4. `list_foreign_keys` — both directions; a missing FK is itself a finding.
5. `analyze_schema_normalization` — heuristics by default.

Pass `functional_dependencies` whenever the user has stated any, one per line:

```
orders: customer_id, order_date -> customer_name, customer_email
```

Without them the report is heuristic: repeating groups, missing primary keys,
unindexed foreign keys, delimited columns. Say which mode produced the findings,
and ask for FDs before answering a question about 2NF, 3NF or BCNF. The analyzer
uses only caller-declared FDs, treats the primary key as the only candidate key,
and reports the strongest form that applies.

Done when every table in scope has been through all five steps, each finding is
tied to the tool output that produced it, and DDL is proposed only after the
normal-form answer.

## Query performance

`explain_query` is for `SELECT` and `WITH` only; a plan for a write statement is
noise, so ask for the read behind it instead. Read `list_indexes` before
recommending an index, and name the index the existing plan already uses.

Done when the plan is quoted, the access type and examined-row count are named,
and the recommendation states whether a new index or a rewritten predicate is
the cheaper fix.

## JavaScript to Java

`translate_javascript` and `translate_webapp` emit a bounded, typed subset. Never
present their output as a finished port.

1. `analyze_javascript` first: it inventories what the project actually does.
2. `translate_javascript` for the units it can express.
3. Report every skipped or approximated construct as manual work.

When the code leans on dynamic behaviour — `eval`, prototype mutation, heavy
metaprogramming — name GraalJS, Rhino, JSweet or GWT as coexistence options
instead of forcing a rewrite.

## Files and diagnostics

`write_file` and `delete_file` are destructive and confined to the roots the
client advertised or `JAVABYTES_ALLOWED_DIRS` allows. Read a file before
overwriting it, and run `backup_file` first when the user has not asked for a
throwaway edit. `project_diagnostic` and `fix_all` report what they checked;
repeat their findings, not findings they never produced.

## Refusals

- Do not run DDL or DML through `query_database`; it rejects them by design.
- Do not infer functional dependencies from sample data.
- Do not call a migration complete while `translate_*` reported manual work.
- Do not describe a tool as available before `list_services` confirms it.
