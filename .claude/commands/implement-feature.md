# Implement Feature — CubeWorks HR (Maker-Checker, gated)

You have been invoked to implement **one feature** of CubeWorks HR. Follow every phase below. Do not skip any.

**Feature:** $ARGUMENTS. This is a feature ID or folder, e.g. `F01` or `.claude/docs/features/F01-onboarding`. An optional gate number may follow it, e.g. `F01 2`.

Read `CLAUDE.md` completely before anything else. Its rules (R-SRC, R-ARC, R-DB, R-API, R-CM, R-UI, R-CODE, the Definition of Done in §2.8) apply to every phase. Where this file and `CLAUDE.md` disagree, **`CLAUDE.md` wins**.

---

## The stack (fixed, no detection needed)

| Layer | Technology | Location | Build / test command |
|---|---|---|---|
| Database | PostgreSQL 16, Flyway, schema `hr` in database `hr_app` | `hr-backend/src/main/resources/db/migration/` | covered by `mvnw verify` (Testcontainers) |
| Backend (BFF) | Java 21, Spring Boot 3, Maven wrapper, Spring Data JPA, Bean Validation, Spring Security (Keycloak JWT), Testcontainers | `hr-backend/` | `cd hr-backend && ./mvnw verify` (Windows: `mvnw.cmd verify`) |
| Case model | CaseFabric engine 1.1.35, CMMN 1.1 | `casefabric/getting-started/src/definitions/employeecase.*` | deploy the model, then start a stub case |
| Frontend | React 18, TypeScript (strict), Vite, React Query | `hr-frontend/` | `cd hr-frontend && npm run build && npm test` |
| Identity | Keycloak (realm roles HR, MANAGER, EMPLOYEE, IT_ADMIN) | `docker-compose.yml` (repo root, this app's own) | `docker compose up -d` |
| Runtime | Two compose projects: `casefabric/getting-started/docker-compose.yml` (Postgres 5431, engine 2027, Mailcatcher 1080 — never edited) + `docker-compose.yml` repo root (Postgres 5433, Keycloak 8180) | both dirs | `docker compose ps` in each |

Other stacks are **out of scope**: Laravel, Python, Go, Rust, .NET, Vue, Svelte, MySQL, MongoDB and Redis. If a request seems to need one, stop and ask the user.

## Agent routing

| Work | Maker agent | Checker agent(s) |
|---|---|---|
| Flyway migration, constraints, views, constraint tests | `postgres-architect` | `polyglot-code-reviewer` |
| JPA entities, repositories, services, controllers, engine client, outbox, security | `java-architect` | `springboot-security-reviewer`, `polyglot-security-reviewer`, `polyglot-code-reviewer` |
| CMMN model (`employeecase.case` / `.type` / `.caseteam`) | `cmmn-modeler` | `architect-reviewer` |
| React screens, API client, components | `react-architect` | `polyglot-security-reviewer`, `polyglot-code-reviewer` |
| docker-compose, Keycloak realm, Dockerfiles | `devops-docker-architect` | `polyglot-security-reviewer` |
| Tests (JUnit 5 + Testcontainers, Vitest + RTL, Playwright) | `polyglot-test-engineer` | – |
| Coverage of the spec | – | `spec-coverage-analyst` |

**Rule:** you orchestrate; you do not write implementation code yourself. Every agent is given:
- the paths of `CLAUDE.md`, `spec.md`, `wireframes.md` and the architecture documents it needs;
- the exact list of spec items in scope (screen IDs, API IDs, AC IDs);
- the instruction "do not invent fields, messages, endpoints or tables; report a gap instead" (R-SRC-2, R-API-4, R-DB-1).

---

## PHASE 0 — Load context and check preconditions

1. **Read the documents in full:**
   - `CLAUDE.md`;
   - `.claude/docs/features/README.md`;
   - `.claude/docs/features/<Fxx>-*/spec.md`;
   - `.claude/docs/features/<Fxx>-*/wireframes.md`;
   - `.claude/docs/architecture/01-overview.md`, `02-data-model.md` and `03-case-file.md`.
2. **Preconditions.** If any of these fails, **stop**, list the failures and ask the user:
   - The README shows the feature as **Spec ready** or **In progress**.
   - `spec.md` has no **[OPEN]** or **[NEEDS APPROVAL]** item that affects the gate about to run.
   - The toolchain works:
     - `java -version` shows 21;
     - `JAVA_HOME` is set;
     - `node -v` shows 20 or newer;
     - `docker info` succeeds.
   - Gate 3 only: engine assumptions E1–E5 in `01-overview.md` have a recorded result, not PENDING.
3. **Pick the gate.** Use the gate number given in `$ARGUMENTS`. Without one, take the first gate not ticked in the spec's "Definition of done" checklist.
4. **Announce the plan:**
   ```
   FEATURE: Fxx <name>   GATE: <n> <title>
   IN SCOPE: <screen / API / AC IDs>
   ROUTING: <maker agent(s)> → <checker agent(s)>
   ```
5. **Track progress.** Create a checklist of this gate's phases (TodoWrite if available).

---

## PHASE 1 — MAKE (one gate per run)

Run **only** the selected gate, then go to Phase 2. Gates always run in this order: database → backend → model → frontend (CLAUDE.md §2.9).

### Gate 0 — Scaffold (first feature only)
- **`java-architect`:**
  - `hr-backend/` Maven project with the wrapper (`mvnw`, `mvnw.cmd`), Spring Boot 3 and Java 21;
  - dependencies: web, validation, data-jpa, security + oauth2-resource-server, flyway, postgresql, Testcontainers and JUnit 5;
  - the package layout from CLAUDE.md §1.2;
  - `application.yml` (datasource `hr_app` on 5433, schema `hr`, Keycloak issuer on 8180);
  - a `Clock` bean;
  - the error JSON `@ControllerAdvice` (R-API-3).
- **`react-architect`:**
  - `hr-frontend/` with Vite, React 18 and strict TypeScript;
  - React Query, a router and Vitest + RTL;
  - the folder layout from CLAUDE.md §1.2;
  - a `dd-mm-yyyy` date helper.
- **`devops-docker-architect`:**
  - create/extend `docker-compose.yml` at the repo root with `hr-db-postgres` (`hr_app`, port 5433) and `keycloak` (8180) — **never** edit `casefabric/getting-started/docker-compose.yml`, it is CaseFabric's own file in its own git repo;
  - the `./infra/postgres-initdb/` init script that creates schema `hr`;
  - a Keycloak realm import with the four roles (HR, MANAGER, EMPLOYEE, IT_ADMIN).
- **Check:**
  - `./mvnw verify` passes;
  - `npm run build && npm test` pass;
  - `docker compose up -d` (repo root) brings `hr-db-postgres` and `keycloak` healthy, alongside the already-running `casefabric/getting-started` stack.

### Gate 1 — Database
- **`postgres-architect`:**
  - a new Flyway migration `V<n>__<feature>.sql` holding exactly the tables, columns, CHECKs, indexes and views of `02-data-model.md` that the spec's "Data touched" section lists;
  - the seed migration the spec asks for (settings, templates, e-mail templates);
  - never edit an applied migration (R-DB-1);
  - conventions R-DB-2: identity PK, audit columns, `version`, varchar + CHECK enums, `ON DELETE RESTRICT`;
  - no salary, bank or ID-number columns (R-DB-4).
- **`java-architect`:** JPA entities and repositories that match the migration, with `@Version` and typed tables as `SINGLE_TABLE`.
- **`polyglot-test-engineer`:**
  - one negative Testcontainers test per new constraint;
  - a clean-migration test from an empty database.
- **If a table or column is missing from `02-data-model.md`:** stop and propose the change. Do not add it.
- **Check:** `./mvnw verify`.

### Gate 2 — Backend
- **`java-architect`:** exactly the endpoints in the spec's API table, nothing more (R-API-4). For each endpoint:
  - thin controller; logic in the service;
  - server-side role and ownership check (G-02) and the G-13 self-check;
  - Bean Validation with the **exact** messages from the spec (R-SRC-3);
  - one transaction holding business rows + `audit_log` + `email_outbox`;
  - engine calls **after commit**, with retry and never a silent rollback (R-ARC-5);
  - the undo window `202 {undoUntil}` for task completion (R-API-6);
  - optimistic locking (`409 STALE_VERSION`);
  - case-file writes limited to the fields in `03-case-file.md` (R-ARC-2, R-ARC-3).
- **`polyglot-test-engineer`:**
  - an integration test per acceptance criterion;
  - a test per validation message that asserts the exact text;
  - per endpoint, an allowed-role test and a forbidden-role test (403).
- **Check:** `./mvnw verify`.

### Gate 3 — Case model
- **`cmmn-modeler`** (`.claude/agents/cmmn-modeler.md`):
  - edit only `employeecase.case`, `.type` and `.caseteam`; `emp.*` is legacy, read it only (R-CM-1);
  - build what the spec's "Case model needs" section lists: stages, human tasks, sentries, milestones and timers;
  - every human task has a performer role from `03-case-file.md`, a due date, and the task name from the spec (R-CM-3);
  - case-file fields only from `03-case-file.md` (R-CM-2);
  - no hard-coded timer durations; use the recorded spike result (R-CM-4).
- **Output:** the model files plus a short change note in the feature folder.
- **Check:** the model deploys to the engine on 2027, and a stub case starts with the example case file from `03-case-file.md`.

### Gate 4 — Frontend
- **`react-architect`:** the screens in `wireframes.md` for this feature, calling only hr-backend (R-ARC-4). Apply:
  - button placement R-UI-1, tables R-UI-2, top bar R-UI-3;
  - toast on save, and Cancel never saves (R-UI-4);
  - `dd-mm-yyyy` dates (R-UI-5) and due-date colours (R-UI-6);
  - menus per role (R-UI-7) and read-only archived cases (R-UI-8);
  - English only (R-UI-9);
  - validation texts mirrored from the API.
- **`polyglot-test-engineer`:**
  - Vitest + RTL tests per screen;
  - a Playwright test for the main flow when the spec asks for an end-to-end test.
- **Check:** `npm run build && npm test`.

### Gate 5 — Review
Phase 3 runs over the whole feature, together with `spec-coverage-analyst` against `.claude/docs/features/<Fxx>-*/spec.md`. Also check every item of the Definition of Done (CLAUDE.md §2.8).

---

## PHASE 2 — AUTOMATED CHECKS

The hooks in `.claude/settings.json` run automatically:
- `format-on-save.sh` runs on each edit;
- `verify-output.sh` runs on Stop;
- `block-dangerous.sh` runs before each Bash command.

Also run the gate's **Check** command yourself. Report the real output, failures included. If anything fails, go to Phase 4.

---

## PHASE 3 — VERIFY (sequential, after every gate)

### 3a — Security (first)
- Run `springboot-security-reviewer` for any backend change.
- Run `polyglot-security-reviewer` for every change.
- Focus areas:
  - G-02 server-side role checks and the G-13 self-check;
  - JWT validation;
  - no personal data in the case file;
  - no G-09 data;
  - CORS restricted to 5173;
  - no secrets in files.

| Severity | Action |
|---|---|
| CRITICAL | Hard stop. Present to the user. Do not auto-fix. |
| HIGH | Present the fix and wait for approval. Then fix and re-check (max 3). |
| MEDIUM / LOW | Auto-fix and re-check (max 3). |

### 3b — Code review (second)
Run `polyglot-code-reviewer` against CLAUDE.md R-CODE-1..4 and the names from the spec and the data model.

| Finding | Action |
|---|---|
| Blocker | Auto-fix and re-check (max 3). After that, stop and ask the user. |
| Warning | Auto-fix. No re-check. |
| Suggestion | Log only. |

### 3c — Tests (third, always on)
- Run `polyglot-test-engineer` to run the full suite of the touched projects.
- Tests are **not optional** here: CLAUDE.md §2.8 makes them part of Done.
- Auto-fix failures (max 3), then stop and ask the user.

### 3d — Spec coverage (gate 2, gate 4 and gate 5)
Run `spec-coverage-analyst` with `.claude/docs/features/<Fxx>-*/spec.md`, limited to the IDs in scope for the gate.
- **PASSED:** at least 95% coverage and nothing missed.
- **Any `[MISSED]`:** route it back to the right maker agent in Phase 1.

---

## PHASE 4 — SELF-HEAL

1. Read the exact error. Do not guess.
2. Fix the specific file through the right maker agent.
3. Re-run only the failing check.
4. **After 3 failed attempts on the same issue, stop and ask the user.**
5. **If the spec is wrong or incomplete:** stop and propose a spec change (R-SRC-4). Never code around it.

---

## PHASE 5 — CLOSE THE GATE (always stop here)

1. Tick the gate in the spec's "Definition of done" checklist.
2. Set the README status to **In progress**, or to **Done** after gate 5 passes.
3. **Report to the user:**
   - files created or changed;
   - check results (the actual output);
   - reviewer findings and how each was handled;
   - any spec gaps found.
4. **Stop.** Ask the user to review the gate before the next one. Never start the next gate in the same run. Commit only when the user asks.

---

## Invalid excuses

| Thought | Reality |
|---|---|
| "The gate is small, I'll skip the security review." | Phase 3a always runs. |
| "I'll do two gates in one go." | One gate per run, then stop for review (CLAUDE.md §2.9). |
| "The spec doesn't say it, but it's obviously needed." | Stop and propose a spec change (R-SRC-2, R-SRC-4). |
| "I'll add a column, the data model is close enough." | New migration + `02-data-model.md` update + approval (R-DB-1). |
| "Tests can come later." | Tests are part of each gate's Done (§2.8). |

If you are about to skip a phase, write **"PIPELINE SKIP DETECTED"** and the reason, then do the phase. The only valid skip is a category the gate genuinely has none of, for example no frontend in gate 1.
