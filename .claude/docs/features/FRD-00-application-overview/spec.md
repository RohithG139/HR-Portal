# FRD-00 — Application overview

| | |
|---|---|
| Status | **Draft** |
| Sources | **Canonical (v1.2, confirmed by the user 2026-10-16):** `.claude/docs/source/FRD-00/FRD-00_Application_Overview.docx` ("Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026") — the project's primary source for FRD-00 (`.claude/docs/source/README.md` "Canonical source"). **Superseded:** FRD v1.1 monolithic PDF (`.claude/docs/source/CubeWorks_HR_FRD.pdf`), §"FRD-00 — Application overview" (pp. 2–7) — kept for history only (README.md "Legacy source"); see §12 D-00-1 (resolved). |
| Wireframes | Shared chrome only — no FRD-00 screens of its own. Top bar (`.topbar`, `#role-pill`, `#bell`/`#notif`, `#avatar`/`#usermenu`) and the per-role sidebar (`CFG.nav` in `.claude/docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html`) |
| Order of truth | FRD > this spec > `wireframes.md` > wireframe HTML (R-SRC-1). Conflicts are listed in §12. |

## 0. Status & sources

FRD-00 is not a module with its own screens, API or database writes. It is the foundational, cross-cutting contract every other FRD (FRD-01…FRD-08) and every screen in the application must obey: the four application roles, the full permissions matrix, the thirteen global rules G-01…G-13, and the screen-layout conventions that apply everywhere.

F01-onboarding (`.claude/docs/features/F01-onboarding/spec.md`) already built the first slice of FRD-00 — the parts of the permissions matrix, G-04/G-05/G-13 wording and corrections it needed for onboarding — and is the first place several of these rules were actually implemented (`SelfActionGuard`, `TaskNotificationService`, `OverdueReminderScheduler`, the `GET /api/me` menu). This spec does not re-decide anything F01 already corrected; where F01's text and the FRD agree, F01's wording is treated as already-correct precedent (per this task's instructions) and is cross-referenced rather than restated as new.

**Source-version note (R-SRC-1/R-SRC-2, D-00-1 — resolved 2026-10-16):** the project's canonical source for FRD-00 is now the per-module `.claude/docs/source/FRD-00/FRD-00_Application_Overview.docx` ("Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), confirmed explicitly by the user as replacing the monolithic v1.1 PDF (`.claude/docs/source/README.md` "Canonical source" / "Order of truth", updated the same day). This spec is now grounded in that v1.2 text throughout. Cross-checking the two versions word-for-word (R-SRC-3) confirms the roles table, all thirteen G-01…G-13 rules and the six screen-layout rules are byte-identical between v1.1 and v1.2 — nothing changed there. What v1.2 adds, that v1.1 did not have at all, is: a complete Document Map naming every module's own FRD docx + WF-xx wireframe pair (FRD-01…FRD-08; FRD-00 itself has no WF-00 — README.md confirms the shared chrome stays sourced from the legacy interactive wireframe HTML, see `wireframes.md`); a cell-by-cell (unambiguous) permissions matrix, used below to confirm this spec's prior disambiguation of the "Write reviews" row was correct (§2.2, D-00-2); a new §5 "Technical Architecture & Data Platform" with the complete case-file item inventory (10 items, 56 fields, every module) and the full `hr` schema inventory (39 tables, 11 views) cross-checked against `V1__init.sql` (§8/§9 below); a new §6 "Modules & Dependencies" stating explicit module build-order dependencies; and an Appendix A of nine FRD/wireframe/data-model gaps, none of which are FRD-00's own to fix (they target FRD-01 through FRD-08's screens/data model) — see §12 for the one that is still worth flagging here.

Nothing in this spec invents a rule, capability, message or field beyond what the FRD states (R-SRC-2).

## 1. Scope

**In scope**
- The four application roles (HR, Manager, Employee, IT admin) and what each one is for (FRD-00 §2).
- The full cross-module permissions matrix (FRD-00 §2 "Permissions") — every capability row, every role column, not just the onboarding slice F01 needed.
- The employee lifecycle stages (Onboarding → Active employment → PIP (only when needed) → Offboarding) and the three cycles that run during Active employment (FRD-00 §3).
- The thirteen global rules G-01…G-13 (FRD-00 §4), quoted exactly, with a note on which existing feature(s) implement each one today.
- The screen-layout rules that apply to every screen in the application (FRD-00 §4 "Screen layout rules (all screens)").
- The shared application chrome every screen is wrapped in: top bar (role pill, bell, avatar/initials menu) and the per-role sidebar — described in `wireframes.md` since FRD-00 has no screens of its own to describe them from.

**Out of scope (left to the module that owns it, per CLAUDE.md §2.9.4 "never mix features")**
- Any module-specific screen, field, validation message, endpoint or business rule — those belong to FRD-01…FRD-08 and their own `Fxx-*`/`FRD-0x-*` specs.
- The *content* of the Dashboard, My Tasks, Employees/My Team, My Profile, Projects, Appraisals, Reports, Settings, My Development and My Reviews screens — FRD-00 only explains why each role sees that nav item (§2 permissions) and §4's rules about how tasks/notifications/dates/tables behave on them; the screens themselves belong to F01 (built) and F03–F09 (not yet built, per `docs/features/README.md`).
- Employee lifecycle *mechanics* beyond naming the four stages and three cycles — the stage transitions, their screens and their data are FRD-02 through FRD-07's concern.

## 2. Roles & permissions

### 2.1 Roles (FRD-00 §2 "Roles and what they can do")

| Role | Who | Main responsibilities |
|---|---|---|
| HR | HR team members | Employee records, onboarding, appraisal rounds, PIP approval, contracts, offboarding, reports and settings |
| Manager | Line managers (also project managers) | Own team, projects, client feedback, project reviews, development plans and KPIs, manager reviews, PIP drafting and check-ins, probation reviews |
| Employee | All staff | Own tasks, profile, development goals, self review, peer reviews, PIP acknowledgement |
| IT admin | IT team | Equipment and accounts for new joiners, access removal at exit, asset register |

"Every user is also an employee. Whatever their application role, every user can do the Employee tasks for their own record: own tasks, profile, development plan, self review, peer reviews and resignation (G-13)." — FRD-00 §2, quoted exactly. This is G-13 (§3 below); F01's 2026-10-16 correction (its spec §2) already applied it concretely: every role, not only Employee, can view/update its own contact and emergency details via `GET`/`PUT /api/me/profile`.

"Clients do not have access. Managers record client feedback on their behalf." — FRD-00 §2, quoted exactly. There is no Client role anywhere in the application (also G-07, §3). The legacy `emp.caseteam`'s Client role was removed from the CMMN model for this reason (`03-case-file.md` §3).

### 2.2 Permissions matrix (FRD-00 §2 "Permissions" — full matrix, every module)

This is the complete matrix from the FRD. F01's spec (§2) already carries the rows it needed, scoped and annotated for onboarding; this table is the generic original those rows were drawn from, for every module.

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| See employees | All | Own team, plus members of own projects (project information only) | Self only | Name, status and assets only |
| Edit employee profile | Yes | No | Own contact details | No |
| Start onboarding, transfer manager, start exit | Yes | Start exit (HR approves) | Resign | No |
| Create, edit, delete projects; assign people | No | Own projects | No | No |
| Change a project's manager | Yes | No | No | No |
| Add client feedback, write project reviews | No | Own projects | No | No |
| Set KPIs, approve and check development plans | No | Own team | Own goals only | No |
| Open and finalise appraisal rounds | Yes | No | No | No |
| Write reviews | Finalise | Manager review | Self and peer review | No |
| Draft PIP / approve PIP | Approve | Draft | Acknowledge or dispute | No |
| Equipment, accounts and asset register | View | No | View own | Yes |
| Reports and settings | Yes | No | No | No |

**Note on "Write reviews" (see D-00-2 in §12):** the v1.1 PDF's fixed-width table extraction ran the HR cell ("Finalise") and the Manager cell ("Manager review") together as one unbroken string with no visible gap, because both cells have short content and the column boundary fell mid-word-run. The canonical v1.2 docx's §2 "Permissions" table gives this row as four distinct, unambiguous cells — HR: "Finalise", Manager: "Manager review", Employee: "Self and peer review", IT admin: "No" — confirming the reading this spec already used, independently cross-checked against FRD-05 §2's Inputs table ("Manager review | Manager") and APR-06 ("Only HR can finalise an appraisal and record a promotion"). Used as shown above, now taken directly from the canonical source rather than reconstructed.

Every endpoint checks the role **and** ownership on the server (G-02); hiding a button is never the only protection (R-UI-7, R-API-2). FRD-01's `GET /api/me` (A01, built in F01) is the foundation this matrix is enforced through at the UI layer — see §6.

## 3. Business rules

The thirteen global rules, copied exactly from FRD-00 §4 (R-SRC-3). "Implements today" cross-references the F01-onboarding spec where a rule is already built; a rule with no reference is not yet built by any shipped feature.

| ID | Rule (verbatim) | Level | Implements today |
|---|---|---|---|
| G-01 | One employee has exactly one case. All projects, reviews, plans, KPIs, assets, documents and history belong to that case. | MUST | F01: `employee.case_id = employee_code` (`01-overview.md` §2, golden rule 3 — makes case creation idempotent); every table in `02-data-model.md` keys off `employee_id`. |
| G-02 | Users see and change only what their role allows (section 2). Access is checked by the system, not only by hiding menu items. | MUST | F01: every endpoint (A01–A38) has a server-side role/ownership check (R-API-2); `springboot-security-reviewer`'s gate-5 pass found and fixed cases where this was enforced inconsistently (F01 spec §13, H3 still open). |
| G-03 | Every task has one owner, a due date and an "Open" action. When the task is done it disappears from all task lists after the undo window (FRD-08), the task count updates and related statuses change everywhere they are shown. | MUST | F01: `engine_task`/`checklist_item` rows, A36/A37, the undo-window pattern (R-API-6), `setting.UNDO_WINDOW_SECONDS` (default 10s, FRD-08). |
| G-04 | Every new task creates an in-app notification and an e-mail to its owner. | MUST | F01: `TaskNotificationService.notifyTaskAssigned`, the `TASK_ASSIGNED` e-mail template (F01 spec §7/§9/§10, added 2026-10-16). |
| G-05 | Tasks due within 3 days are shown in orange; overdue tasks in red. An overdue reminder e-mail is sent 1 day after the due date (configurable, FRD-08). | MUST | F01: `OverdueReminderScheduler`, `setting.OVERDUE_REMINDER_DAYS`, the `TASK_OVERDUE` template, and the frontend's `task-due soon`/`overdue` classes (F01 spec §7/§10, R-UI-6). |
| G-06 | Every change to an employee's case is recorded in its History with who, what and when. | MUST | F01: `audit_log` writes in every write endpoint (R-API-5), the Employee record's History tab (S11). Gate-5 found WS-05 audit coverage for A33 untested (F01 spec §13) — not a design gap, a test gap. |
| G-07 | Clients never log in. | MUST | Structural: no Client role, login path or Keycloak client exists for clients anywhere in the application (§2.1 above). Nothing to build — there is no screen or endpoint for this role to reach. |
| G-08 | All time periods (check-ins, reminders, PIP length) come from Settings > Timers & rules. They are never fixed in the application. | MUST | F01: every period it needs (`ONBOARDING_OVERDUE`, `OVERDUE_REMINDER_DAYS`, `UNDO_WINDOW_SECONDS`, `SET_PASSWORD_LINK_DAYS`, `PASSWORD_MIN_LENGTH`) is a `setting` row (F01 spec §9), seeded ahead of FRD-08's own settings screens (F09, not yet built). |
| G-09 | The application does not store salary, bank details or ID document numbers. HR only records that an ID was checked. | MUST | F01: the Verification card's "ID document checked in person" is a boolean with no ID-number field (F01 spec §5, S2); `02-data-model.md` has no salary/bank/ID-number column anywhere in the schema. |
| G-10 | Archived cases are read-only. | MUST | **Not yet built.** No feature archives a case yet — that is F08 (Contract end, offboarding & assets, FRD-07), which is "Not started" (`docs/features/README.md`). See OPEN-G10 in §12. |
| G-11 | Saving a page shows a short confirmation message. Cancel never saves. | MUST | F01: the save-confirmation toast (R-UI-4) on every save action (e.g. S10 My Profile); Cancel buttons never call a save endpoint. |
| G-12 | Dates are shown as "dd-mm-yyyy" and entered as dd-mm-yyyy. | SHOULD | F01: R-UI-5, applied throughout (e.g. S2's Date of birth field); Spring stores/returns ISO `yyyy-MM-dd` and the frontend converts (F01 spec §7). |
| G-13 | Every role includes the Employee functions for the user's own record. A user never reviews, approves or finalises anything about themselves. | MUST | F01: `security/SelfActionGuard.java`, wired into A12/A13/A14/A16/A19/A21–A24/A33/A34 and the HR/IT task lists (F01 spec §2 correction, §13 gate-5 fix) — the first and so far only concrete implementation of this rule. |

### Screen layout rules (FRD-00 §4 "Screen layout rules (all screens)")

Quoted exactly (R-SRC-3); these are R-UI-1…R-UI-3 in CLAUDE.md and apply to every screen built by every feature, not just this one:

- "Positive actions (Save, Submit, Approve, Create) are green and placed on the right."
- "Cancel and Delete are red outline buttons on the left. Archive is an orange outline button on the left."
- "'+ Add' buttons sit at the top right of the section they add to."
- "Every table can be sorted by clicking a column header, filtered and searched. Export is a download icon next to the search box and downloads an Excel file."
- "Items in a list are removed with a small '×' button."
- "The top bar shows the role, the notification bell and the user's initials. The user's menu (initials) holds My profile and Log out."

F01 built the first concrete instance of this chrome (its spec §4, S12; its wireframes.md S12). `wireframes.md` in this folder describes the full, generic version — every nav item across every eventual module, not just what F01 built.

### Out of scope for v1 & global decisions (FRD-00 §7 "Out of Scope & Decisions" — new in v1.2, not present in the v1.1 PDF)

Quoted/summarised exactly from the canonical source (R-SRC-3); these are whole-application boundaries for version 1, not owned by any one module, so they belong here rather than being repeated per feature spec:

| Feature | Note |
|---|---|
| Leave and absence | Not planned for v1 |
| Payroll, salary, bank details | Handled outside this application |
| Timesheets | Not planned for v1 |
| Recruitment (before the employee record exists) | Not planned for v1 |
| Client login | Managers record client feedback (G-07) |
| Equipment stock and purchasing | Only assets issued to employees are tracked |
| Automatic account creation in IT systems | IT admin ticks items manually; integration handled by development |
| Login method: e-mail and password | Single sign-on is not used in v1 |

Decisions (FRD-00 §7, quoted exactly):
- "Employee records are kept indefinitely and are not deleted automatically."
- "In v1, IT admin creates accounts in IT systems manually and ticks them in the checklist."
- "A user can no longer log in after the end of their last working day." — relevant to G-10 (archived cases are read-only) and to the Login screen FRD-01 owns; see OPEN-LOGIN in §12 for the one Appendix A gap that touches this.
- "E-mails and screens are in English only." — already captured as R-UI-9.

## 4. Screens

FRD-00 has no screens of its own (it describes the whole application once, FRD-00 §0/§1 — FRD intro). What it does define is the chrome every other module's screen is wrapped in:

| Chrome element | Wireframe source | Built by |
|---|---|---|
| Top bar — brand mark, role pill (`#role-pill`) | `.topbar` in the wireframe HTML | F01 (`app/layout`) |
| Notification bell + dropdown (`#bell`, `#notif`) | `.bell`, `.dropdown.notif` | F01 (S12 "Notifications") |
| Avatar / initials + user menu (`#avatar`, `#usermenu`: My profile, Log out) | `.profile-avatar`, `.dropdown.usermenu` | F01 (S12 "User menu") |
| Sidebar, scoped per role (`CFG.nav`) | `.sidebar` / `#sidebar` | F01 built the items it needed (Dashboard stub, My Tasks, Employees/My Team/My Profile/Assets); the remaining nav items per role are not yet built — see `wireframes.md` |

`wireframes.md` in this folder is the full, generic description of this chrome — see it for the complete per-role nav list and what each item needs before it can be built.

## 5. Fields & validation

Not applicable at this level. FRD-00 defines no fields, forms or validation messages of its own — "This field is required.", exact e-mail/phone/postcode messages, and so on are each module's own concern (FRD-01…FRD-08 §5), reusing the generic validation-message pattern R-SRC-3 establishes (API error messages are copied from the FRD exactly, R-API-3). The only generic, cross-cutting interaction rules at this level are G-11 (confirmation toast / Cancel never saves) and G-12 (date format) above — those are behaviours, not fields, and are already covered in §3.

## 6. API

FRD-00 defines no endpoints of its own (R-API-4: endpoints come from the owning module's spec). The one endpoint that most directly embodies it is already built:

- **`GET /api/me`** (A01, built in F01-onboarding spec §6) returns `{employeeId, name, initials, appRole, menu[]}`. `appRole` and `menu[]` are the mechanism behind G-02/WS-01 ("the dashboard and menu shown depend only on the user's role") — the frontend's sidebar renders only the items `menu[]` lists, for exactly the reason `CFG.nav` is keyed by role in the wireframe. No new endpoint is proposed; as more of `CFG.nav` is built (F03–F09), `menu[]`'s contents grow with it, but the endpoint itself does not change shape.
- **`GET`/`PUT /api/me/profile`** (A29/A30, built in F01) is the concrete form of G-13's "every role includes the Employee functions for the user's own record" — every role, not only Employee, reaches it.
- The remaining G-rules (G-03/G-04/G-05/G-06) are implemented by cross-cutting backend *components*, not a dedicated endpoint: `TaskNotificationService`, `OverdueReminderScheduler`, and `audit_log` writes inside each module's own write endpoints (see §7). FRD-00 does not own these components' endpoints either — they live wherever the module that first needed them built them (F01), and are reused, not re-specified, by every later feature.

## 7. Backend behaviour

- **R-API-2 (G-02):** every controller checks role (`@PreAuthorize`/method security) and ownership (service-layer checks against `employee_id`/`manager_id`) before any read or write. F01's gate-5 review found and fixed IDOR gaps where this was missing (manager-scoping on checklist items) and logged one open architectural gap (H3: JWT realm roles and `employee.app_role` can disagree after a role change) that any later feature touching role changes must account for.
- **G-13 — `SelfActionGuard`:** `com.cubeworks.hr.security.SelfActionGuard` (built in F01, the "G-13 self-check helper" CLAUDE.md's repo layout names) is the one reusable component for "a user never reviews, approves or finalises anything about themselves" — every later feature with an approve/finalise/review action on another employee's record (PIP approval, appraisal finalisation, probation review, exit approval) must call it rather than re-implementing the check, so APR-09-style rules ("An HR user cannot finalise their own appraisal; another HR user does it (G-13)") are enforced consistently everywhere, not just in onboarding.
- **G-06 — audit:** every state-changing endpoint writes one `audit_log` row (who/what/when/before/after) in the same transaction as the business write (R-API-5), independent of which module owns the endpoint.
- **G-04/G-05 — task notifications:** any feature that creates an `engine_task` or a DB-backed task row (the F01 "D-08" pattern: engine tasks for roles whose work needs a CMMN human task, plain DB rows otherwise) must call `TaskNotificationService.notifyTaskAssigned` the same way F01's `OnboardingService` does, and let `OverdueReminderScheduler` pick it up for G-05 — these are generic, not onboarding-specific, components.
- **G-08 — settings-driven periods:** every timer/reminder value a later feature needs is a new `setting` row (seeded by that feature's own Flyway migration until F09 builds the settings screens), never a hard-coded constant (R-ARC-7).

## 8. Case model needs

FRD-00 does not add case-file items or tasks of its own (R-CM-2: a field exists only if `03-case-file.md` lists it, and nothing here needs one). What it establishes, which every feature's case-model work must honour:

- **G-01, "one employee has exactly one case":** `employee.case_id = employee_code` (e.g. `E-0050`), making case start idempotent (`01-overview.md` §2, golden rule 3). Every later feature's case-file additions (`03-case-file.md` §2 — Probation, Contract, Bench, ProjectReview, DevelopmentPlan, Appraisal, PIP, Exit) are items *within* that same one case per employee, never a second case.
- **Case roles (§2.1 above, `03-case-file.md` §3):** Employee and Manager are added as per-case **users** (the specific employee's user, the specific manager's user); HR and ITAdmin are added as **tenant roles** (every HR/IT-admin user, not named individuals) — set at case start by Spring, per the permissions matrix in §2.2 (HR "sees all", Manager "own team only"). Peer and ProjectManager roles (added by FRD-05/FRD-03 respectively) follow the same pattern when those features are built.
- **G-07, no Client role:** the legacy `emp.caseteam`'s Client role is permanently removed (`03-case-file.md` §3) — no feature should re-add it.
- **Case-file item inventory is now centrally documented (v1.2 §5, new):** the canonical source's "Technical Architecture & Data Platform" section lists the complete case-file item inventory across every module in one place — 10 items, 56 fields total (Employee 4, Onboarding 5, Probation 4, Contract 5, Bench 3, ProjectReview 2, DevelopmentPlan 6, Appraisal 8, PIP 13, Exit 6) — matching `03-case-file.md`'s per-item breakdown. This is now the single authoritative cross-module source for "does this case-file field exist," superseding the previous need to check each feature spec individually; any new field a later feature proposes must appear in this inventory (or be added to it with the user's approval, R-CM-2) before it is built.
- **Module build-order dependencies (v1.2 §6, new):** "Settings (FRD-08) must exist first" (already captured as F01's seed-migration workaround, §9 below and `docs/features/README.md`'s settings note); "Onboarding (FRD-02) creates the employee, the manager link and the first assets — the other modules start only after onboarding is complete" (already true of G-01's one-case-per-employee model); "Offboarding (FRD-07) uses the asset register filled during onboarding" (relevant to OPEN-ASSETS, §12 — reinforces but does not settle F08's ownership of the browsable asset-register screen).

## 9. Data touched

No new table and no migration belongs to FRD-00 itself — `employee.app_role` and `audit_log` already exist (added by F01's `V1__init.sql`, per `02-data-model.md`) and are simply the tables this cross-cutting contract is enforced through:

- **`employee.app_role`** — `varchar(10) check (app_role in ('HR','MANAGER','EMPLOYEE','IT_ADMIN'))` (`02-data-model.md` line 68) — the four roles from §2.1, one per employee; this is what `GET /api/me`'s `appRole` and every `@PreAuthorize`/service-layer role check reads.
- **`audit_log`** (`02-data-model.md` — `employee_id`, `entity_type`, `entity_id`, `action`, `changed_by`, `changed_at`, `before_value`, `after_value`, `details`) — the table G-06 is enforced through, written by every module's write endpoints, read back by the Employee record's History tab (S11, F01).
- **Schema inventory is now centrally documented (v1.2 §5, new):** the canonical source's cross-check against the real `V1__init.sql` confirms 39 tables and 11 views in schema `hr` (its own §5 tables list every table by area — Organisation & people, Settings, Onboarding & assets, Projects, Development/KPIs/PIP, Appraisal & reviews, Decisions & exit, Platform — and every view with its purpose), verdict "OK: all 39 tables and all 11 views are used by at least one FRD, and every table or view named in the FRDs exists." It also self-corrects an internal typo elsewhere in its own document ("Fix: FRD-00 section 10 says '39 tables, 10 views'; the SQL defines 11 views") — 11 views is the correct count, used throughout this spec and `02-data-model.md`. This is now the single authoritative cross-module source for "which tables/views exist and are they all used," superseding ad hoc per-feature checks. The same source also re-confirms G-09 ("OK: no salary, bank or ID-document columns exist anywhere in the schema") and R-DB-2's audit-column convention ("OK: audit columns created_at, created_by, updated_at, updated_by and version are added to every table").

## 10. E-mails & timers

Not applicable at this level — FRD-00 defines no e-mail template or timer of its own. It defines the *rules* every module's e-mails and timers must follow:
- G-04: every new task → one notification + one e-mail to its owner (the generic `TASK_ASSIGNED` template and `TaskNotificationService`, built once in F01, reused by every later feature rather than re-specified per module).
- G-05: an overdue task → one reminder e-mail, 1 day after the due date by default, configurable per `setting.OVERDUE_REMINDER_DAYS` (the generic `TASK_OVERDUE` template and `OverdueReminderScheduler`, same reuse pattern).
- G-08: the interval itself always comes from `setting`, never a literal duration in code or in the CMMN model (R-CM-4, R-ARC-7).

## 11. Acceptance criteria

F01's AC-17 (WS-01 role-based menu) and AC-20 (WS-02 manager team scoping) already exercise G-02 concretely for onboarding; F01's AC-02/AC-08/AC-09/AC-19/AC-24 already exercise G-03/G-04/G-13 for onboarding's own tasks. The scenarios below are the FRD-00-level ones not already covered by any shipped feature's test suite — they are written generically (not tied to one module's screen) since FRD-00 itself has none, and should be picked up as cross-cutting regression tests by whichever feature first builds the screen a scenario needs.

```
AC-G01 (G-01): One case per employee, idempotent
  Given an employee with employee_code E-0050 already has a case
  When the system is asked to start a case for E-0050 a second time (e.g. a retried engine call after R-ARC-5's "retry on failure")
  Then no second case is created
  And the existing case is used (case_id = employee_code)

AC-G02 (G-02): Hiding a button is never the only protection
  Given a Manager is logged in with a valid token
  When that Manager calls an HR-only endpoint directly (bypassing the UI, e.g. POST /api/onboardings)
  Then 403 {"code":"FORBIDDEN"} is returned
  Even though the "+ New Employee" button is not shown to Managers in the UI

AC-G06 (G-06): Every change is recorded with who, what and when
  Given HR edits a field on an employee's profile
  When the change is saved
  Then an audit_log row is written with changed_by = the HR user, changed_at = now, and before/after values for that field
  And the change appears in the employee's History tab

AC-G07 (G-07): No client login path exists
  Given the application's full set of Keycloak realm roles and React routes
  When searched for a "Client" role or a client-facing login path
  Then none exists — client feedback is always recorded by a Manager on the client's behalf (FRD-00 §2)

AC-G08 (G-08): Time periods are never hard-coded
  Given a new timer or reminder is needed by any feature
  When its interval is implemented
  Then the interval is read from a setting row (or, until FRD-08 ships, a Flyway-seeded one)
  And no literal duration appears in Java, TypeScript or the CMMN model for that interval (R-CM-4)

AC-G09 (G-09): No forbidden data is ever stored
  Given the full hr_app schema (02-data-model.md)
  When searched for salary, bank-detail or ID-document-number columns
  Then none exist anywhere in the schema

AC-G11 (G-11): Cancel never saves
  Given a user has unsaved changes on any form
  When they select Cancel
  Then no write endpoint is called and the data is unchanged
  And selecting Save instead shows a short confirmation toast

AC-G12 (G-12): Dates round-trip through dd-mm-yyyy
  Given a date field showing "21-10-2026"
  When the form is saved and the record is reopened
  Then the same date is shown as "21-10-2026" (R-UI-5), stored internally as ISO 2026-10-21 (R-API-1)

AC-G13 (G-13): A user cannot act on their own record in an approval/finalise step
  Given an HR user is also the subject employee of a case needing HR approval (e.g. a PIP approval, once F07 is built)
  When that HR user attempts the approval
  Then it is blocked by SelfActionGuard (as it already is for F01's onboarding-adjacent HR/IT actions)
  And another HR user must perform it instead
```

`AC-G03`, `AC-G04`, `AC-G05` and a stronger `AC-G13` are intentionally not duplicated here — F01's own AC-02 (IT notified = G-04), AC-19 (undo window = G-03), the OverdueReminderScheduler behaviour (G-05, F01 spec §7, not yet given its own AC number in F01), and AC-24 (HR ticks on behalf of another owner, G-13-adjacent) already exercise these for onboarding; a later feature's spec should extend coverage to its own screens rather than this document re-asserting the same rule abstractly.

## 12. Decisions & open items

| # | Topic | Decision / status |
|---|---|---|
| D-00-1 | Two FRD-00 source documents exist: the monolithic PDF (README: "v1.1") and a per-module docx set whose own header says "v1.2" ("Split from v1.2 (Oct 15, 2026 · @Rohith)"), found at `.claude/docs/source/FRD-00/FRD-00_Application_Overview.docx` (and similarly FRD-01…FRD-08's own docx+wireframe pairs). | **Resolved 2026-10-16.** The user explicitly confirmed the per-module v1.2 docx set is now the project's canonical source for every FRD, superseding the monolithic v1.1 PDF; `.claude/docs/source/README.md` and `CLAUDE.md` were updated the same day to record this. All G-rules/roles-table/screen-layout wording remains byte-identical between the two versions (verified in §0) — v1.2's only FRD-00-level additions are the unambiguous permissions table (D-00-2), §5's case-file/schema inventories (§8/§9), §6's module dependencies (§8) and §7's out-of-scope/decisions list (new subsection under §3). None of Appendix A's 9 gaps concern FRD-00 itself — see OPEN-LOGIN below for the one that is tangentially relevant; the other 8 are left for FRD-01 through FRD-08's own specs. |
| D-00-2 | The "Write reviews" permissions-matrix row is ambiguous in the v1.1 PDF's fixed-width text extraction (HR cell "Finalise" and Manager cell "Manager review" run together with no gap). | **Resolved**, now directly from the canonical v1.2 table's cleanly-delimited cells, cross-checked against FRD-05 §2 ("Manager review \| Manager") and APR-06 ("Only HR can finalise an appraisal"). See §2.2's note. Not an invention — both independent sources agree. |
| OPEN-G10 | G-10 ("Archived cases are read-only") has no feature to attach an acceptance test to yet — no feature archives a case. | **[OPEN].** Unaffected by v1.2 — it adds no new fact about archiving. Tracked here rather than silently dropped; F08 (Contract end, offboarding & assets, FRD-07) is the feature expected to build archiving (`docs/features/README.md`), and its spec should pick up G-10's concrete AC when it is written, plus R-UI-8 (an archived case is read-only in the UI). |
| OPEN-ASSETS | The wireframe's `p-it-assets` "Assets" sidebar page (a browsable, searchable asset register — distinct from F01's S5/S6 equipment-request screens, which only *create* assets) is not built by any shipped feature, and `docs/features/README.md` does not explicitly assign it to one feature, though "assets" appears in F08's title ("Contract end, offboarding & assets"). | **[OPEN]** for the user to confirm ownership (most likely F08, given its title, but not stated outright in the README) before `wireframes.md`'s nav table is treated as settled. v1.2 §6 "Modules & Dependencies" adds "Offboarding (FRD-07) uses the asset register filled during onboarding," which reinforces F08 as the natural owner of the asset register generally but still does not explicitly assign this browsable screen to any feature — **[OPEN]** remains. |
| OPEN-COMPANION-DOCS | FRD-00's own v1.2 cover text names two companion documents as the source for parts of its §5/§6 content — `CubeWorks_HR_CaseFabric_Integration_Guide.docx` (how the case engine and hr_app work together) and `CubeWorks_HR_DB_CrossCheck.docx` (every FRD statement checked against `V1__init.sql`) — but neither exists anywhere in this repository (`.claude/docs/source/README.md`, "Referenced but not delivered"). | **[OPEN].** Treated as undocumented, not merely unread (per README.md). The exact CaseFabric/Dex/Keycloak integration contract, and any per-FRD DB cross-check detail beyond what v1.2's own §5 already inlines (§9 above), should not be assumed to exist elsewhere until the user supplies these files or confirms they don't exist. |
| OPEN-LOGIN | Appendix A gap #1 (v1.2, new): "Set password, Forgot password and Reset password have no wireframe page, and the data model has no token table" (FRD-01 Screen 2). Suggested fix: "Assume the identity provider (`employee.idp_subject`) issues and checks the links; draw the three screens; create the token table." | **[OPEN], owned by FRD-01/F03, flagged here only because it touches this spec's territory:** `wireframes.md`'s "Login" section already defers the login screen itself to FRD-01 (F01's spec §5/S13); this gap means the *password-reset* variants of that screen, and any token table they'd need, are still undecided anywhere in the data model (`02-data-model.md` has no token table today). It also bears on FRD-00's own §7 decision "A user can no longer log in after the end of their last working day" (new subsection under §3) — whatever mechanism enforces that (Keycloak account disable vs. an app-level check) is not yet specified. Not an FRD-00 open item to resolve here — tracked so F03/FRD-01's spec picks it up rather than it being silently missed. |

D-00-1 and D-00-2 are now resolved. Three items remain genuinely the user's call (CLAUDE.md "ask when blocked") rather than inferable from the FRD alone: OPEN-G10, OPEN-ASSETS and OPEN-COMPANION-DOCS. OPEN-LOGIN is tracked here but is not this spec's to resolve — it belongs to FRD-01/F03's own spec.

## 13. Definition of done

FRD-00 is a foundational contract document, not a buildable feature with its own gates — CLAUDE.md §2.8's DoD (automated tests per AC, Testcontainers, etc.) does not apply to it directly, since it has no endpoints or screens of its own to test. Instead:

- [x] All four roles and the full permissions matrix (§2) transcribed from the FRD with no capability invented or narrowed, and the one ambiguous row resolved and justified (D-00-2).
- [x] G-01…G-13 quoted exactly (R-SRC-3) with a note on which shipped feature, if any, implements each one today (§3).
- [x] The screen-layout rules (R-UI-1…R-UI-3) quoted exactly and cross-referenced to CLAUDE.md's rule IDs.
- [x] No new endpoint, table, case-file field, e-mail template or screen invented — §§4–10 each either point to what F01 already built or say "not applicable" / "not yet built" explicitly.
- [x] FRD-00-level acceptance criteria written for every G-rule not already exercised by F01's test suite, without duplicating F01's existing ACs (§11).
- [x] D-00-1 resolved (user confirmed v1.2 canonical, 2026-10-16); D-00-2 resolved using the canonical source directly.
- [ ] OPEN-G10, OPEN-ASSETS and OPEN-COMPANION-DOCS resolved by the user (§12).
- [ ] Status changed to **Spec ready** once the user approves this document (CLAUDE.md §2.9.1).
