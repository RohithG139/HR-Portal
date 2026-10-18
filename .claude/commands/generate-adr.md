# /generate-adr

Draft an Architecture Decision Record (ADR) following Michael Nygard's standard format and save it to `docs/adr/`.

## Usage

```
/generate-adr [title]
```

**Arguments (`$ARGUMENTS`):**
- `title` — short decision title in natural language (e.g. `Use PostgreSQL for primary storage`)

## Output

Creates `docs/adr/NNNN-<kebab-title>.md` where `NNNN` is the next sequential number.

## Execution Steps

### Step 0 — Parse Arguments

Extract `TITLE` from `$ARGUMENTS`. If missing, ask: "What is the architectural decision being recorded?"

### Step 1 — Determine Next ADR Number

```bash
ls docs/adr/*.md 2>/dev/null | sort | tail -1
```

If no ADRs exist yet, start at `0001`. Otherwise, increment the highest existing number by 1.
Create `docs/adr/` if it does not exist.

### Step 2 — Kebab-case the Title

Convert `TITLE` to lowercase kebab-case for the filename:
- `Use PostgreSQL for primary storage` → `use-postgresql-for-primary-storage`

### Step 3 — Read Template

Read `templates/adr/adr-template.md` if it exists. Otherwise use the built-in Nygard template below.

### Step 4 — Write ADR File

Write `docs/adr/<NNNN>-<kebab-title>.md` with:

```markdown
# <NNNN>. <Title>

Date: <YYYY-MM-DD>

## Status

Proposed

## Context

[What is the issue that motivates this decision or change? What forces are at play?]

## Decision

[What is the change being proposed or accepted?]

## Consequences

[What becomes easier or more difficult to do and any risks introduced by the change.]
```

Fill in the Date from today's date. Leave Status as `Proposed`. Leave Context, Decision, and Consequences as placeholder text — the human fills those in.

### Step 5 — Report

Print the created file path:
```
Created: docs/adr/0003-use-postgresql-for-primary-storage.md
Next step: fill in Context, Decision, and Consequences sections, then change Status to Accepted.
```

## Examples

```bash
/generate-adr Use PostgreSQL for primary storage
/generate-adr Adopt hexagonal architecture for backend services
/generate-adr Replace REST with GraphQL for mobile API
```

## ADR Statuses

Use one of: `Proposed` → `Accepted` → `Deprecated` / `Superseded by [NNNN]`

## Related

- Template: [`templates/adr/adr-template.md`](templates/adr/adr-template.md)
- ADR directory: [`docs/adr/`](docs/adr/)
