# CubeWorks HR on CaseFabric — Project Guide for Claude

This file has two parts:

1. **Structure:** where things live.
2. **Global rules:** the rules every change must follow.

Read the whole file before you plan or write anything. Feature-specific rules live in `.claude/docs/features/Fxx-*/spec.md`.

---

## Part 1 — Structure

### 1.1 What we are building

CubeWorks HR manages the employee lifecycle: onboarding, projects, development and KPIs, appraisals, PIPs and offboarding.

- **Case per employee:** each employee is **one CaseFabric case**.
- **CaseFabric engine:** decides *what happens next*: stages, tasks, timers, milestones and case roles.
- **Spring Boot backend:** owns *all business data* in PostgreSQL.
- **React:** talks **only** to Spring Boot.

```
React (hr-frontend) ──REST/JSON──► Spring Boot BFF (hr-backend) ──REST──► CaseFabric engine 1.1.35
                                          │                                    │
                                          ▼                                    ▼
                          Postgres #1: hr_app (schema hr)      Postgres #2: case-engine-eventstore / -query
                          (this app's docker-compose.yml)      (casefabric/getting-started/docker-compose.yml)
                                          ▲
                             Keycloak (this app's docker-compose.yml, login/users/roles)
```

Two separate `docker-compose` projects, never merged (§1.3): CaseFabric's `getting-started` repo stays untouched, and the HR app owns its own Postgres + Keycloak in a `docker-compose.yml` at the repo root.

### 1.2 Repository layout

```
casefabric-new/
├── CLAUDE.md                         ← this file: structure + global rules
├── docker-compose.yml                 THIS app's infra + dev containers: hr-db-postgres, Keycloak, hr-backend, hr-frontend
├── infra/postgres-initdb/             init script that creates schema `hr` in hr_app
├── .claude/
│   ├── settings.json                 permissions + hooks for Claude Code
│   ├── commands/                     /implement-feature (gated pipeline), /review-pr, /spec-coverage, /create-migration, /generate-adr
│   ├── agents/                       makers: postgres-architect, java-architect, cmmn-modeler, react-architect, devops-docker-architect
│   │                                 checkers: springboot-security-reviewer, polyglot-security-reviewer, polyglot-code-reviewer,
│   │                                           polyglot-test-engineer, spec-coverage-analyst, architect-reviewer
│   ├── hooks/                        block-dangerous (pre-Bash), format-on-save (post-edit), verify-output + desktop-notify (on stop)
│   ├── templates/devops/             Dockerfile / CI templates (used by devops-docker-architect)
│   └── docs/                         project docs, kept inside `.claude/` on purpose (not a top-level `docs/`)
│       ├── source/                   FRD v1.2 per-module docs + wireframes (input documents; read-only) — see source/README.md
│       ├── architecture/
│       │   ├── 01-overview.md        stack, storage split, integration flow
│       │   ├── 02-data-model.md      all tables (DDL), views, constraints, 3NF notes
│       │   └── 03-case-file.md       case file items, case roles
│       └── features/
│           ├── README.md             feature index + status
│           └── Fxx-<name>/
│               ├── spec.md           what to build (the contract)
│               └── wireframes.md     how each screen looks (text wireframes)
├── casefabric/getting-started/       CaseFabric engine, its own git repo — never edit its docker-compose.yml
│   ├── docker-compose.yml            engine's own infra: case-engine-db-postgres + engine + IDE + Mailcatcher
│   └── src/definitions/              CMMN models: employeecase.case / .type / .caseteam
├── hr-backend/                       Spring Boot (created during F01 implementation)
└── hr-frontend/                      React app (created during F01 implementation)
```

#### hr-backend (Java 21, Spring Boot 3, Maven)

**Decided 2026-10-16:** new code follows a layer-first MVC layout, not package-by-feature.
F01's already-built code (`onboarding/`, `employee/`, `asset/`, `task/` — package-by-feature, each
folder holding its own controller/service/repository/entity/dto, 159 passing tests, the proven
live-engine integration) is **left exactly as it is** — not refactored retroactively. Every feature
from FRD-08 onward is built under `controller/`, `service/`, `repository/`, `model/`, `dto/`, each
with one subfolder per feature. Cross-cutting packages (`config/`, `security/`, `common/`,
`engine/`, `notification/`) stay top-level either way.

```
hr-backend/src/main/java/com/cubeworks/hr/
├── HrApplication.java
├── config/            Spring config, OpenAPI, Jackson (ISO dates), CORS
├── security/          Keycloak JWT → CurrentUser, role checks (G-02), G-13 self-check helper
├── common/
│   ├── error/         ApiException, error codes, @ControllerAdvice → error JSON (rule R-API-3)
│   ├── audit/         AuditLogService (G-06)
│   ├── outbox/        EmailOutbox writer + sender job
│   └── time/          Clock bean (never call LocalDate.now() directly)
├── engine/            CaseFabric client, case-file writer, task watcher, undo window (pending completion)
├── notification/      in-app notifications
├── employee/          F01 (legacy layout, unchanged): employee, contract, profile, documents
├── onboarding/        F01 (legacy layout, unchanged): onboarding, drafts, IT request, checklist
├── asset/             F01 (legacy layout, unchanged): asset register
├── task/              F01 (legacy layout, unchanged): My Tasks API (engine_task + checklist rows)
├── controller/        FRD-08 onward: one subfolder per feature (e.g. controller/settings/)
├── service/           FRD-08 onward: one subfolder per feature (e.g. service/settings/)
├── repository/        FRD-08 onward: one subfolder per feature (e.g. repository/settings/)
├── model/             FRD-08 onward: JPA entities, one subfolder per feature (e.g. model/settings/)
└── dto/               FRD-08 onward: one subfolder per feature (e.g. dto/settings/)
hr-backend/src/main/resources/
├── application.yml
└── db/migration/      Flyway: V1__init.sql, V2__seed_settings.sql, V3__… (never edit an applied migration)
```

#### hr-frontend (React 18+, TypeScript, Vite)

Scaffolded with React 19 (Vite's current template default as of gate 0, 2026-10-15) — functionally compatible with everything below; "React 18" elsewhere in this file means "React 18 or newer," not a hard pin.

```
hr-frontend/src/
├── app/               router, layout (top bar, sidebar per role), auth (Keycloak)
├── api/               typed API client (one file per backend feature), error mapping
├── components/        shared UI: Card, Field (floating label), Table (sort/filter/search/export), Badge, Modal, Toast, Checklist, TaskList
├── features/<feature>/ screens of one feature (e.g. features/onboarding/Step1Details.tsx)
└── lib/               date format dd-mm-yyyy, validation helpers
```

### 1.3 Local runtime — two separate `docker-compose` projects

**MUST: never edit `casefabric/getting-started/docker-compose.yml`.** It is CaseFabric's own file, in its own git repo. Everything this app needs that isn't already there goes into the HR app's own `docker-compose.yml` at the repo root instead.

| Service | Port | Compose project | Note |
|---|---|---|---|
| PostgreSQL (`case-engine-db-postgres`) | 5431 | `casefabric/getting-started/docker-compose.yml` | databases `case-engine-eventstore`, `case-engine-query` |
| CaseFabric engine (via nginx) | 2027 | same | REST API |
| CaseFabric IDE / UI | 2081 / 3317 | same | model editing, engine UI |
| Mailcatcher | 1080 (SMTP 1025) | same | all outgoing e-mail in dev — the app's `email_outbox` sender connects to this one; no second mailcatcher |
| PostgreSQL (`hr-db-postgres`) | 5433 | `docker-compose.yml` (repo root) | database `hr_app`, schema `hr` *(added in F01, gate 0)* |
| Keycloak | 8180 | `docker-compose.yml` (repo root) | replaces Dex for the HR app *(added in F01, gate 0)* |
| hr-backend | 8080 | `docker-compose.yml` (repo root) | **containerized dev container (2026-10-16)**, `hr-backend/Dockerfile.dev` — bind-mounts the host source, runs `./mvnw spring-boot:run` inside the container. No hot reload for Java changes yet (`spring-boot-devtools` is not on the classpath — a tracked follow-up); `docker compose restart hr-backend` picks up source changes. Can still be run directly on the host instead (`./mvnw spring-boot:run`) if preferred — both ways work, just not at the same time (port 8080 conflict). |
| hr-frontend | 5173 | `docker-compose.yml` (repo root) | **containerized dev container (2026-10-16)**, `hr-frontend/Dockerfile.dev` — bind-mounts the host source, runs `npm run dev -- --host 0.0.0.0`; Vite HMR hot-reloads the browser on every source change, same as running on the host. Can still be run directly on the host instead (`npm run dev`) if preferred. |

Run both compose projects together in dev: `docker compose -f casefabric/getting-started/docker-compose.yml up -d` and `docker compose up -d` (repo root — this now also builds and starts hr-backend/hr-frontend). hr-backend reaches `hr-db-postgres`/`keycloak` by their Docker service name on the shared `hr_app_network`; it still reaches the CaseFabric engine stack (a separate compose project, no shared network) through `host.docker.internal` + that stack's published host ports, same as every other cross-stack connection. hr-frontend's `VITE_*` env vars always point at `localhost`-published ports, never a Docker service name — the browser, not the container, makes those calls directly.

### 1.4 Key documents (read before working on a feature)

All paths below are rooted at `.claude/docs/`, not `docs/` (deliberate — see §1.2).

| Document | Use it for |
|---|---|
| `.claude/docs/source/` FRD v1.2 (canonical, decided 2026-10-16 — one `FRD-xx/` folder per module, each with its own FRD docx and matching `WF-xx` wireframe HTML; see `.claude/docs/source/README.md`) | The business requirements. **It always wins.** The legacy monolithic v1.1 PDF + single wireframe HTML are superseded; still cited by specs written before v1.2 existed (e.g. `F01-onboarding/`). |
| `.claude/docs/architecture/01-overview.md` | How the parts talk to each other |
| `.claude/docs/architecture/02-data-model.md` | Every table, column and constraint. Do not invent new ones. |
| `.claude/docs/architecture/03-case-file.md` | What may go into the case file |
| `.claude/docs/features/README.md` | Which feature is next and what status each one has |
| `.claude/docs/features/Fxx/spec.md` + `wireframes.md` | What to build for that feature |

---

## Part 2 — Global rules

Rule IDs (R-…) are referenced from specs and reviews. "MUST" rules are never broken.

### 2.1 Sources of truth and no guessing

- **R-SRC-1 MUST: order of truth.** When documents disagree, the order is **FRD v1.2 (per-module) > feature spec > wireframes.md > the module's own WF-xx wireframe HTML > legacy v1.1 PDF/HTML**. Note the conflict in the spec's "Decisions & open items" section.
- **R-SRC-2 MUST: never invent.** Do not invent fields, statuses, messages, roles, tasks or business rules. If something is missing, stop and ask, or add it to "Open items" and mark it **[OPEN]**.
- **R-SRC-3 MUST: messages are exact.** User-facing messages are copied **exactly** from the FRD or spec, including punctuation.
- **R-SRC-4 MUST: spec before code.** Code follows the spec. If the code needs something the spec does not say, update the spec first and get approval, then write the code.

### 2.2 Architecture

- **R-ARC-1 MUST: engine decides, UI reports facts.** React and Spring never decide workflow. Spring writes facts to the database and the case file; the engine model decides the next task, stage or milestone.
- **R-ARC-2 MUST: database vs case file.** Business data lives in `hr_app`. The case file holds only IDs, dates, booleans and enums that sentries, timers or routing need (`03-case-file.md`).
- **R-ARC-3 MUST: no personal data in the case file.** No names, e-mail addresses, addresses or free text.
- **R-ARC-4 MUST: React talks only to Spring.** React never calls the engine or Keycloak admin APIs directly.
- **R-ARC-5 MUST: engine after commit.** Spring calls the engine **after** the database transaction commits. A failed engine call is retried. It never rolls back business data silently; it is logged and retried.
- **R-ARC-6 MUST: outbox for e-mail.** E-mails are written to `email_outbox` inside the business transaction and sent by a background job.
- **R-ARC-7 MUST: settings, not constants.** Time periods come from the `setting` table (G-08). Values used by a running cycle are copied into the case file or row when that cycle starts (ADM-02).
- **R-ARC-8 MUST: read-only engine databases.** Spring never writes to the engine databases.

### 2.3 Database

- **R-DB-1 MUST: data model is the contract.** `02-data-model.md` defines the schema. Every change goes through a **new** Flyway migration and an update to `02-data-model.md` in the same change.
- **R-DB-2 MUST: conventions.** These apply to every table:
  - Primary key: `bigint generated always as identity`.
  - Audit columns: `created_at`, `created_by`, `updated_at`, `updated_by`, `version`.
  - Enums: `varchar` + `CHECK`.
  - Foreign keys: `ON DELETE RESTRICT` unless the data model says otherwise.
  - Schema: `hr`.
- **R-DB-3 MUST: 3NF, no stored derived values.** Derived values are computed in views or services. The documented snapshots are the exception.
- **R-DB-4 MUST: no forbidden data (G-09).** No salary, bank details or ID-document numbers, ever.
- **R-DB-5 MUST: optimistic locking.** Every update checks `version`; a conflict returns `409 STALE_VERSION`.

### 2.4 API (Spring Boot ↔ React)

- **R-API-1 MUST: paths and formats.**
  - Paths: `/api/...`, plural nouns.
  - JSON: camelCase.
  - Dates: ISO `yyyy-MM-dd`; date-times are ISO-8601 UTC.
- **R-API-2 MUST: server-side checks.** Every endpoint checks the role and ownership on the server (G-02). The FRD-00 permissions table is the reference. A user never reviews, approves or finalises anything about themselves (G-13).
- **R-API-3 MUST: error format.** It is always:
  - `400 {"code":"VALIDATION_FAILED","errors":[{"field","message"}]}`
  - `409 {"code":"<SPECIFIC_CODE>","message":"..."}`
  - `403 {"code":"FORBIDDEN"}`
  - `404 {"code":"NOT_FOUND"}`
- **R-API-4 MUST: endpoints come from the spec.** Endpoints are exactly those in the spec's API table. A new endpoint means updating the spec first.
- **R-API-5 MUST: every change is audited.** Every state change writes `audit_log` with who, what, when, before and after (G-06).
- **R-API-6 MUST: undo window.** Completing a task goes through the undo window (G-03): return `202 {undoUntil}`, and complete the engine task once the window ends.

### 2.5 Engine / CMMN model

- **R-CM-1 MUST: model files.** The model files are `casefabric/getting-started/src/definitions/employeecase.*`. `emp.case` and `emp.type` are legacy; read them but do not extend them.
- **R-CM-2 MUST: case file fields.** A case file field exists only if `03-case-file.md` lists it.
- **R-CM-3 MUST: tasks.** Every human task has a performer role from `03-case-file.md` §case roles, a due date (G-03) and a name that matches the task name in the spec. That name is the key used by `setting` category TASK_DUE.
- **R-CM-4 MUST: no fixed timers.** No hard-coded timer durations (G-08). Timers read case-file values or are raised by the Spring scheduler, depending on the timer-spike result recorded in `03-case-file.md`.

### 2.6 Frontend and screen layout (FRD-00 "Screen layout rules")

- **R-UI-1 MUST: button placement.**
  - Positive actions (Save, Submit, Approve, Create) are **green, on the right**.
  - Cancel and Delete are **red outline, on the left**.
  - Archive is **orange outline, on the left**.
  - "+ Add" buttons sit at the top right of the section they add to.
  - List items are removed with a small "×".
- **R-UI-2 MUST: tables.** Every table can be sorted by column, filtered and searched. Export is a download icon next to the search box and produces Excel.
- **R-UI-3 MUST: top bar.** It shows the role, the notification bell and the user's initials. The initials menu holds My profile and Log out.
- **R-UI-4 MUST: save and cancel.** Saving shows a short confirmation toast; Cancel never saves (G-11).
- **R-UI-5 SHOULD: dates.** Dates are shown and entered as `dd-mm-yyyy` (G-12).
- **R-UI-6 MUST: due-date colours.** Tasks due within 3 days are orange; overdue tasks are red (G-05).
- **R-UI-7 MUST: menus per role.** Menus and dashboards depend only on the role (WS-01). Hiding a button is never the only protection (see R-API-2).
- **R-UI-8 MUST: archived cases.** An archived case is read-only in the UI (G-10).
- **R-UI-9 MUST: English only.** All screens and e-mails are in English.

### 2.7 Code quality

- **R-CODE-1: backend style.**
  - Java 21, Spring Boot 3, Spring Data JPA, Flyway, MapStruct (or manual mappers), Bean Validation.
  - The service layer holds the logic; controllers stay thin.
  - Inject `Clock` for "today".
- **R-CODE-2: frontend style.**
  - React + TypeScript strict, functional components, and React Query for server state.
  - Form validation messages come from the API (and are mirrored client-side using the same texts).
- **R-CODE-3: names.** Names follow the spec and the data model: same column, field and task names everywhere.
- **R-CODE-4: no unused code.** No dead code, commented-out code or "TODO later" without an entry in the spec's open items.

### 2.8 Testing and Definition of Done

A feature is **Done** only when all of these hold:

1. Every **acceptance criterion** in the spec has an automated test:
   - backend: an integration test with Testcontainers PostgreSQL;
   - flow: an end-to-end test where needed.
2. Every **validation message** in the spec is covered by a test that asserts the exact text.
3. Every **DB constraint** added by the feature has a negative test.
4. Role checks are tested: an allowed role succeeds, and a forbidden role gets 403.
5. Flyway migrates cleanly from an empty database. `mvn verify` and `npm run build && npm test` pass.
6. Gate 5 of `/implement-feature` passes (security review, code review, tests, `/spec-coverage` PASSED), and the spec status is set to **Done** in `docs/features/README.md`.

### 2.9 Way of working (one feature at a time)

1. **Spec.** Create `.claude/docs/features/Fxx-name/spec.md` and `wireframes.md` from the FRD and the wireframes. Use the same sections as `.claude/docs/features/F01-onboarding/` (spec sections 0–13; one wireframe block per screen). Add the feature to `.claude/docs/features/README.md` as **Draft**. Status: **Draft → Spec ready** once the user approves it.
2. **Implement.** `/implement-feature Fxx` builds in this fixed order, runs **one gate per call**, and **stops for the user's review after each gate**:
   0. Scaffold, first feature only (`java-architect`, `react-architect`, `devops-docker-architect`)
   1. DB migration + entities (`postgres-architect`, `java-architect`)
   2. Backend API + services + tests (`java-architect`, `polyglot-test-engineer`)
   3. Engine model changes (`cmmn-modeler`)
   4. Frontend screens (`react-architect`, `polyglot-test-engineer`)
   5. Review (all checkers + `spec-coverage-analyst`)

   After every gate the checkers run in order: security (`springboot-security-reviewer` / `polyglot-security-reviewer`), then `polyglot-code-reviewer`, then the tests.
3. **Review.** Gate 5 (`/implement-feature Fxx 5`) reviews the whole feature against the spec and these rules. `/review-pr` and `/spec-coverage .claude/docs/features/Fxx-name/spec.md` can also be run on their own at any time. Fix the findings, then set the status to **Done**.
4. **Never mix features.** Do not touch another feature's screens or endpoints unless the spec says so.
5. **Ask when blocked.** When a decision belongs to the user (business rule, wording, scope), ask. Do not pick silently.
6. **Before committing,** run the tests. Commit only when the user asks.
