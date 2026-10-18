# /pipegen-validate

Validate `pipeline.yml` configuration without deploying or writing any files.

## Usage

```
/pipegen-validate [--config FILE]
```

**Arguments (`$ARGUMENTS`):**
- `--config FILE` — path to the pipeline config file (default: `pipeline.yml`)

## What This Does

Dry-run validation only. Reports actionable errors with config path and reason.
Never deploys. Never writes files. Returns non-zero on invalid configuration.

| Check | Rule |
|-------|------|
| Required fields | `app.name`, `ci.provider`, at least one environment |
| Enum values | `provider`, `language`, `artifact.type`, `deploy_target` must be known values |
| HTTPS URLs | All environment URLs must start with `https://` |
| Secret values | Literal credentials rejected; `${{ secrets.NAME }}` references only |
| Adapter fields | Target-specific fields required: `docker` → host/container_name/port; `aws` → region/cluster/service; `azure` → resource_group/app_name |

## Execution Steps

### Step 0 — Parse Arguments

Parse `$ARGUMENTS`. Extract:
- `CONFIG` = value of `--config` (default: `pipeline.yml`)

If `CONFIG` file does not exist:
```
AskUserQuestion: "No pipeline.yml found at [CONFIG]. Enter the path to the pipeline config file:"
```

### Step 1 — Invoke Pipeline Automation Agent

Call `@pipeline-automation-agent` in validation mode:

```
Validate the pipeline.yml at [CONFIG].

Run ALL validation checks:
- Required field presence
- Enum value enforcement
- HTTPS URL check on all environment URLs
- Secret-value rejection (literal credentials not allowed)
- Adapter-specific required field check per deploy_target

Collect ALL errors before reporting — do not stop at first failure.
Return a structured error list with config path and reason per error.
Do not generate any files. Do not deploy anything.
```

### Step 2 — Report

**On validation pass:**
```
✓ pipeline.yml is valid.
  app.name:    [value]
  provider:    [value]
  environments: [list]
  artifact:    [type] → [registry]
```

**On validation failure:**
```
✗ pipeline.yml has [N] error(s):

  [config.path]: [reason]
  [config.path]: [reason]
  ...

Fix these errors and re-run /pipegen-validate.
```

Exit non-zero on any validation error so CI can use this as a gate:
```yaml
# In CI (example):
- name: Validate pipeline config
  run: /pipegen-validate --config pipeline.yml
```

## Examples

```bash
# Validate default pipeline.yml in current directory
/pipegen-validate

# Validate a specific config file
/pipegen-validate --config /path/to/repo/pipeline.yml
```

## Related

- Agent: [`pipeline-automation-agent`](.claude/agents/pipeline-automation-agent.md)
- Registry: [`docs/AGENTS.md`](docs/AGENTS.md)
- Generate pipeline: `/pipegen-init`
