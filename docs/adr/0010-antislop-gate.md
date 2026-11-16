# ADR 0010: Antislop Gate as Pipeline Verification Step

## Status
Accepted

## Context
Antislop practices (deterministic coding standards: no `// TODO` comments, immutable records, explicit nullability, no raw types, no `any`, etc.) should be enforced on generated code. The question: where does enforcement happen?

## Decision
Add a new capability tool `verify_generated` (default-on, runs before Prove):
- Input: the `files` map from `translate_webapp` (or any generator)
- Runs deterministic checks:
  - No `// TODO`, `// FIXME`, `// HACK` comments in generated source
  - All records are `final` / effectively immutable
  - No raw types (`List` not `List<String>`)
  - Explicit nullability annotations (`@Nullable` / `@NonNull` on all params/returns)
  - No `Object` as return type when a specific type is known
  - No `var` in field declarations (explicit types required)
  - All public APIs have Javadoc
- On violation: returns failure with file:line:rule, pipeline stops before Prove
- On success: returns clean, pipeline proceeds to Prove

## Consequences
- **Positive**: "No slop" is a gate, not a suggestion; generated code is always clean
- **Positive**: Deterministic — no AI judgement, pure AST checks
- **Positive**: Extensible — new rules added to tool, not scattered in prompts
- **Negative**: Tool must be maintained alongside generators; rule changes can break existing pipelines
- **Negative**: Strictness may require generator fixes before new features ship

## Alternatives Considered
1. **Code-review standard only**: Human reviews generated code against antislop checklist. Rejected — manual, inconsistent, doesn't scale.
2. **Prompt engineering**: Tell the generator "don't emit TODO". Rejected — unreliable, not verifiable.
3. **Existing linter (Checkstyle/SpotBugs)**: Run as Maven plugin in Prove step. Rejected — runs too late (after generation), slow, not tailored to generated-code patterns.

## Related
- GLOSSARY.md: Antislop gate, Pipeline atomicity
- `translate_webapp` Prove step (ADR 0004/0005 context)