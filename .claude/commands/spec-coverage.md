# Spec Coverage Audit

**Usage:** `/spec-coverage [spec_path] [target_dir]`

Audits the codebase against a specification document and reports requirement coverage.

## Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| `spec_path` | Most recent file in `docs/specs/`, or `spec.md` at project root | Path to the specification document |
| `target_dir` | Project root (`.`) | Directory to scan for implementation |

## Behavior

1. **Resolve spec_path** — if not provided, check `docs/specs/` for the most recently modified
   `.md` file. Fallback to `spec.md` at project root. If neither exists, halt and report.

2. **Invoke `@spec-coverage-analyst`** — pass `spec_path` and `target_dir`. The analyst:
   - Extracts all atomic requirements into an indexed inventory (`FR-N`, `API-N`, `DM-N`, `VR-N`, `EC-N`)
   - Traces each requirement across controllers, services, models, migrations, components, and tests
   - Classifies each as `[COVERED]`, `[PARTIAL]`, `[MISSED]`, or `[DRIFT]`
   - Calculates: `Coverage % = (COVERED + 0.5×PARTIAL) / Total × 100`

3. **Display results** — full coverage matrix, layer breakdown, critical gaps checklist,
   and a final verdict:
   - `PASSED` — Coverage >= 95% and zero `[MISSED]` items
   - `FAILED` — Coverage < 95% or any `[MISSED]` items; includes remediation instructions

## Examples

```bash
# Audit against the most recent spec in docs/specs/
/spec-coverage

# Audit against a specific spec file
/spec-coverage docs/specs/order-management.spec.md

# Audit a subdirectory only
/spec-coverage docs/specs/order-management.spec.md src/orders/
```

## On FAILED Verdict

If the analyst returns `FAILED` or any `[MISSED]` items, the remediation checklist is presented.
To fix missed requirements, delegate to the appropriate specialist:

- Backend gaps → `@java-architect` or `@python-architect`
- Frontend gaps → `@react-architect`
- DB model gaps → `@postgres-architect`

Then re-run `/spec-coverage` to confirm coverage improved.
