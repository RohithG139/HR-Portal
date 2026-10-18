# /generate-pipeline

Generate production-grade CI/CD pipeline configuration and Docker setup for this project.

## Usage

```
/generate-pipeline [provider] [cloud] [--docker]
```

**Arguments (`$ARGUMENTS`):**
- `provider` — `github` | `bitbucket` | `jenkins` | `gitlab` (required)
- `cloud` — `aws` | `azure` | `gcp` (required)
- `--docker` — also generate `Dockerfile` + `.dockerignore` (optional; auto-enabled if no Dockerfile found)

## What Gets Generated

| Flag | Output Files |
|------|-------------|
| `--docker` or no Dockerfile found | `Dockerfile`, `.dockerignore` |
| `provider=github` | `.github/workflows/ci-cd.yml` |
| `provider=bitbucket` | `bitbucket-pipelines.yml` |
| `provider=jenkins` | `Jenkinsfile` |
| `provider=gitlab` | `.gitlab-ci.yml` |

All pipelines enforce:
- **Mandatory automated tests** on `feature/*`, `bugfix/*`, and `develop` branches
- **HITL approval gates** before every test/staging/production deployment
- **Zero hardcoded credentials** — platform secret stores only

## Execution Steps

### Step 0 — Parse Arguments

Parse `$ARGUMENTS`. Extract:
- `PROVIDER` = `github` | `bitbucket` | `jenkins` | `gitlab`
- `CLOUD` = `aws` | `azure` | `gcp`
- `DOCKER_FLAG` = `true` if `--docker` present, or if no `Dockerfile` found in repo root

If `PROVIDER` is missing or unrecognized:
```
AskUserQuestion: "Which CI provider should I generate the pipeline for?
Options: github (GitHub Actions), bitbucket (Bitbucket Pipelines), jenkins (Jenkinsfile), gitlab (GitLab CI)"
```

If `CLOUD` is missing:
```
AskUserQuestion: "Which cloud target?
Options: aws (ECR + ECS Fargate), azure (ACR + Container Apps), gcp (Artifact Registry + Cloud Run)"
```

### Step 1 — Inspect Project

Scan project root for:
- Build tool: `pom.xml` → Maven/Java; `build.gradle` → Gradle/Java; `pyproject.toml` → Python; `package.json` → Node/React
- Existing `Dockerfile` (skip Docker generation if found and `--docker` not set)
- Existing pipeline files (warn if overwriting)

Report:
```
DETECTED STACK: [Java/Maven | Python/Poetry | Node/React]
DOCKER: [generate new | skip (existing found) | generate (--docker flag)]
PROVIDER: [github | bitbucket | jenkins]
CLOUD: [aws | azure]
```

### Step 2 — Invoke DevOps Agent

Call `@devops-docker-architect` with the following explicit instruction:

```
Generate a production-grade CI/CD setup for a [STACK] project.

Target provider: [PROVIDER]
Target cloud: [CLOUD]
Docker: [yes/no]

Reference templates in .claude/templates/devops/ as starting points.
Adapt test commands to the detected stack.

MANDATORY REQUIREMENTS:
1. Automated tests MUST run on every push to feature/*, bugfix/*, and develop.
   Pipeline terminates immediately if tests fail.
2. Deployments to test/staging/production MUST use HITL approval gates:
   - GitHub: environment: test / environment: production with reviewer protection
   - Bitbucket: trigger: manual on every deploy step
   - Jenkins: input() block with submitter and timeout
3. Zero hardcoded credentials — use platform secret stores only.
4. Non-root Docker runtime user.
5. No FROM latest — pin all image tags.
```

### Step 3 — Generate Files

Agent generates the requested files. Expected output locations:

- `Dockerfile` (if requested)
- `.dockerignore` (if requested)
- `.github/workflows/ci-cd.yml` (GitHub Actions)
- `bitbucket-pipelines.yml` (Bitbucket)
- `Jenkinsfile` (Jenkins)
- `.gitlab-ci.yml` (GitLab CI — Docker-in-Docker service, `stages: [test, build, deploy]`, `when: manual` gate on production deploy job)

### Step 4 — Verify & Report

After generation, verify:
1. No secrets hardcoded: `grep -r "password\|secret\|AKIA" .github/ bitbucket-pipelines.yml Jenkinsfile 2>/dev/null`
2. HITL gate present (provider-specific check)
3. Test step present before deploy steps

Report generated files with paths and a brief summary of what each enforces.

## Examples

```bash
# GitHub Actions + AWS, with new Dockerfile
/generate-pipeline github aws --docker

# Bitbucket Pipelines + Azure (no Docker — existing Dockerfile)
/generate-pipeline bitbucket azure

# Jenkins + AWS
/generate-pipeline jenkins aws --docker

# GitLab CI + GCP
/generate-pipeline gitlab gcp --docker
```

## Related

- Agent: [`devops-docker-architect`](.claude/agents/devops-docker-architect.md)
- Templates: [`.claude/templates/devops/`](.claude/templates/devops/)
- Registry: [`docs/AGENTS.md`](docs/AGENTS.md)
