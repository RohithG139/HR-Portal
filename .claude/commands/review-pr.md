# /review-pr

Run the full 3-step verification pipeline (security → code review → tests) on the current git diff without going through `/implement-feature`.

## Usage

```
/review-pr [--with-tests]
```

**Arguments (`$ARGUMENTS`):**
- `--with-tests` — also run `polyglot-test-engineer` (optional; off by default)

## What Runs

| Step | Agent | Stop condition |
|------|-------|---------------|
| 1. Security | `polyglot-security-reviewer` | CRITICAL finding → hard stop, no auto-fix |
| 2. Code quality | `polyglot-code-reviewer` | Blocker → auto-fix loop (max 3 retries) |
| 3. Tests | `polyglot-test-engineer` | Only with `--with-tests` flag |

## Execution Steps

### Step 0 — Collect Changed Files

```bash
git diff --name-only HEAD
git diff --name-only --cached
```

If no changes found, report "No changes to review" and exit.

### Step 1 — Security Review

Delegate to `@polyglot-security-reviewer` with the list of changed files and the diff output.

```bash
git diff HEAD
```

**On CRITICAL finding:** Print the finding, halt immediately. Do not proceed to Step 2.
Do not attempt to auto-fix CRITICAL security findings — surface them to the human.

**On HIGH finding:** Pause, print the finding, ask user to approve before continuing.

**On MEDIUM/LOW:** Continue; they will be auto-fixed in Step 2 if applicable.

### Step 2 — Code Quality Review

Delegate to `@polyglot-code-reviewer` with the diff and the list of changed files.

**Blocker findings:** Attempt auto-fix (delegate back to the appropriate specialist agent).
Retry up to 3 times. If still failing after 3 retries, surface to human.

**Warning/Suggestion findings:** Log and continue — do not retry.

### Step 3 — Test Coverage (optional)

Only runs when `--with-tests` is present in `$ARGUMENTS`.

Delegate to `@polyglot-test-engineer` to verify tests cover the changed code paths.

### Step 4 — Summary Report

Print a findings table:

```
┌─────────────────────────────────────────────────────┐
│ /review-pr Results                                  │
├──────────┬────────────┬────────────┬───────────────-┤
│ Step     │ Findings   │ Auto-fixed │ Status         │
├──────────┼────────────┼────────────┼───────────────-┤
│ Security │ 0 CRITICAL │ —          │ ✅ PASSED       │
│ Code     │ 1 Blocker  │ 1          │ ✅ PASSED       │
│ Tests    │ —          │ —          │ ⏭️ SKIPPED      │
└──────────┴────────────┴────────────┴───────────────-┘
```

## Examples

```bash
# Security + code review only
/review-pr

# Full pipeline including tests
/review-pr --with-tests
```

## Related

- Agents: [`polyglot-security-reviewer`](.claude/agents/polyglot-security-reviewer.md), [`polyglot-code-reviewer`](.claude/agents/polyglot-code-reviewer.md), [`polyglot-test-engineer`](.claude/agents/polyglot-test-engineer.md)
- Full feature pipeline: [`/implement-feature`](.claude/commands/implement-feature.md)
