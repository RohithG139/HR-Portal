# 01 — Architecture overview

| | |
|---|---|
| Status | Approved baseline |
| Related | `02-data-model.md`, `03-case-file.md`, `CLAUDE.md` |

## 1. Components

| Component | Technology | Responsibility |
|---|---|---|
| hr-frontend | React 18 + TypeScript (Vite), React Query | All screens. Talks **only** to hr-backend. |
| hr-backend | Java 21, Spring Boot 3, Spring Data JPA, Flyway | REST API (BFF), validation, business data, e-mail outbox, engine integration, scheduler |
| CaseFabric engine | `cafienne/engine:1.1.35`, CMMN 1.1 | One case per employee: stages, tasks, timers, sentries, milestones, case roles |
| Keycloak | OpenID Connect | Login (e-mail + password; no SSO in v1), set-password and reset pages, application roles. Replaces Dex for the HR app. Runs in the app's own `docker-compose.yml` (repo root). |
| PostgreSQL 16 (×2, separate instances) | | **hr-db-postgres** — `hr_app` (schema `hr`, owned by Spring), in the app's own `docker-compose.yml`. **case-engine-db-postgres** — `case-engine-eventstore` and `case-engine-query` (owned by the engine), in `casefabric/getting-started/docker-compose.yml`. |
| Mailcatcher (dev) | | Receives every outgoing e-mail on port 1080. Runs only in `casefabric/getting-started/docker-compose.yml`; the app connects to it rather than running a second one. |

## 2. Storage split

| Store | Owner | Holds |
|---|---|---|
| `case-engine-eventstore` | CaseFabric | Event journal, timers. The application never touches it. |
| `case-engine-query` | CaseFabric | Projections: cases, tasks, case file. Spring has **read-only** access. |
| `hr_app` (schema `hr`) | Spring (Flyway) | All business data (see `02-data-model.md`). Its own Postgres instance, not shared with the engine's. |
| Case file | Spring writes; the model reads | Only values that sentries, timers or routing need, plus the `*Id` of the matching `hr_app` row (see `03-case-file.md`) |

**Golden rules**
1. Detail lives in the database. The case file holds no personal data or free text.
2. Every value a sentry evaluates is written to the case file **in the same request** that saves it to the database.
3. `employee.case_id` = `employee_code` (for example `E-0050`), so creating a case is idempotent.
4. `employee.stage` is a **cache** of the engine stage, used for list filters and reports. The engine is the source of truth.

## 3. Identity

- **Login name:** Keycloak username = `employee.private_email` at first (F01 decision Q2). HR can switch it to the work e-mail later.
- **Roles:** the Keycloak realm role is `employee.app_role` (`HR`, `MANAGER`, `EMPLOYEE`, `IT_ADMIN`). `employee.idp_subject` stores the Keycloak `sub`.
- **Token use:** React sends the Keycloak access token to Spring. Spring checks the role and ownership (G-02), then calls the engine as that user (token pass-through) or as a technical user. Which of the two is used is **[UNVERIFIED]** until the engine spike.
- **Case team** (who may do which task):
  - Spring adds the employee and the manager **as users**.
  - HR and IT admin are added **as tenant roles**.
- **Password rules:** FRD-08 settings `PASSWORD_MIN_LENGTH`, `SET_PASSWORD_LINK` and `RESET_PASSWORD_LINK` are synced to the Keycloak realm policy.

## 4. Request flow for a state-changing action

```
React ──► Spring controller
            1. authenticate (JWT) + authorize (role / ownership / G-13)
            2. validate (exact FRD messages)
            3. ONE DB transaction: business rows + audit_log + email_outbox
            4. commit
            5. engine call(s): case-file update / start case / complete task (retry on failure, never silent)
          ◄── response (200/201, or 202 {undoUntil} for task completion)
Background jobs in Spring:
  • outbox sender   → SMTP (mailcatcher in dev); retries; status SENT/FAILED
  • task watcher    → polls the engine query DB for new and finished tasks → engine_task rows,
                      notification + "Task assigned" e-mail (G-04), stage cache updates from milestones
  • undo executor   → completes engine tasks whose undo window has passed (G-03)
  • scheduler       → overdue reminders (G-05), nightly jobs (for example project Overdue), and timer
                      fallback user events (if the timer spike fails)
```

**Example: Approve PIP**
1. HR submits.
2. Spring runs its checks: G-13, the checklist, and the start date.
3. In one transaction Spring:
   - inserts the `decision` (PIP_HR);
   - updates the `pip`;
   - writes the `audit_log`;
   - adds the "PIP notice" mail to the `email_outbox`.
4. After the undo window, Spring completes the engine task with output `{PIP:{hrDecision, startDate}}`.
5. The engine creates the "PIP notice" task for the employee.
6. The task watcher stores it in `engine_task` and sends the notification and e-mail.

## 5. Engine assumptions verified (spike run 2026-10-15, before F01 gate 3)

Run against a live CaseFabric engine 1.1.35 (`casefabric/getting-started` compose project, started fresh), using the Dex IDP's `local` password connector (user `lana`, authorization-code flow — Dex's `/dex/token` endpoint does not accept the ROPC `password` grant for the static password DB in this setup, so the full browser-style code exchange was scripted with curl instead). Evidence gathered via the engine's own Swagger spec (`/api-docs/swagger.json`) and direct REST calls against the deployed `helloworld` case and a throwaway purpose-built `spiketimer` case (deployed via `POST /repository/deploy/{fileName}`, removed again after the spike — never committed).

| # | Assumption | Result | Evidence |
|---|---|---|---|
| E1 | A timer expression can read a case-file value (a duration or an absolute ISO date-time) | **Does not hold — fallback adopted.** | A literal ISO-8601 duration in `timerExpression` (`PT10S`, matching the legacy `emp.case` usage) fires correctly and reliably gates a dependent task via a CMMN sentry. A bare case-file property reference (`Timer.DueAt`) is *not* evaluated as a SpEL expression at all — the engine takes the literal body text and tries `Duration.parse()` on it directly, regardless of what the case file actually holds (fails identically whether the field is unset, an absolute ISO instant, or even a valid `"PT10S"` string). Wrapping the reference in an explicit `T(...)` SpEL construct (e.g. `T(java.time.Duration).parse(Timer.DueAt.toString())`) *does* get evaluated, and does resolve to a non-null object (`.getClass()`/`.toString()` both succeed without error) — but whatever the case file's wrapped value `.toString()`s to is still not `Duration`/`Instant`-parseable, including after stripping quote characters. The engine's HTTP error response does not echo the actual parsed text, so the exact wrapper representation could not be pinned down further without engine source access or more invasive debugging than a pre-gate-3 spike warrants. |
| E2 | Spring can update the case file through the REST API with a token | **Holds.** | `PUT /cases/{id}/casefile/update/{path}` with a bearer token (acting as a tenant user, not the case's own `From`/`To` business actor) updated `Greeting.Message` successfully. Note: the query-side read (`GET .../casefile`) lagged the write by roughly 1–2 seconds (event-sourced projection), so Spring must not assume read-after-write consistency immediately after a case-file write — matches the spec's existing assumption that React re-fetches after a short delay (§7c.1). |
| E3 | A task can be completed with output parameters that map to case-file fields | **Holds.** | `POST /tasks/{taskId}/complete` with a JSON body matching the task's output schema correctly wrote the mapped case-file item (`Response`), and the completion correctly triggered a sentry-gated dependent task to start. |
| E4 | The query DB exposes tasks with assignee, due date and case id in readable tables | **Holds.** | `GET /tasks/case/{caseInstanceId}` (and `/tasks/case-name/{caseName}`, `/tasks` for a cross-case list) returns `assignee`, `owner`, `dueDate`, `caseInstanceId`, `taskName`, `createdOn`/`createdBy`, `input`/`output` in one call — sufficient for the `engine_task` mirror without extra round-trips. |
| E5 | The case team can be changed at runtime (manager transfer, peers) | **Holds.** | `POST /cases/{id}/caseteam/users` added a new user with specific case roles to a running case without a restart; `DELETE /cases/{id}/caseteam/users/{userId}` removed one. Both took effect immediately (confirmed via `GET /cases/{id}/caseteam`). |

**Consequence for gate 3 (F01's "Onboarding overdue" timer, §8 of the F01 spec):** per the pre-agreed fallback, the model does **not** use a native `timerExpression` reading `Onboarding.overdueDate`. Instead, the Spring scheduler (already planned for the undo-window executor, `task/UndoWindowScheduler.java`) polls for onboardings where `Onboarding.overdueEnabled = true` and `overdueDate <= today`, and raises the equivalent business action directly (e.g. completing/triggering the case's overdue path via the REST API, the same way E2/E3 are already proven to work) rather than relying on the engine's own timer. This keeps R-CM-4 ("no hard-coded timer durations") satisfied — the *interval* is still read from `setting`, only the *firing mechanism* moves to Spring.

Recorded here and cross-referenced from `03-case-file.md` and the F01 spec §8 before the case model is built.

## 6. Environments and ports (dev)

Two separate `docker-compose` projects, run side by side — never merged into one file (R-ARC keeps CaseFabric's `getting-started` repo untouched):

| Compose project | Service | Port |
|---|---|---|
| `casefabric/getting-started/docker-compose.yml` (CaseFabric's own, untouched) | PostgreSQL (`case-engine-db-postgres`: `case-engine-eventstore`, `case-engine-query`) | 5431 |
| same | Engine (nginx proxy) | 2027 |
| same | IDE / engine UI | 2081 / 3317 |
| same | Mailcatcher | 1080 (SMTP 1025) |
| `docker-compose.yml` (repo root, this app's own) | PostgreSQL (`hr-db-postgres`: `hr_app`) | 5433 |
| same | Keycloak | 8180 |
| run on the host, not containerized | hr-backend | 8080 |
| run on the host, not containerized | hr-frontend | 5173 |

hr-backend reaches every service above through its published host port; no Docker network is shared between the two compose projects.
