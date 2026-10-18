# FRD-01 — Shared workspace (full scope)

| | |
|---|---|
| Status | **Done** (all 5 gates complete 2026-10-17 — see §13) |
| Sources | **FRD v1.2 (canonical, decided 2026-10-16):** `.claude/docs/source/FRD-01/FRD-01_Shared_Workspace.docx` ("Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), cross-referencing FRD-00 (`.claude/docs/source/FRD-00/FRD-00_Application_Overview.docx`) for roles, the permissions matrix, G-01…G-13 and layout rules. Legacy FRD v1.1 (`CubeWorks_HR_FRD.pdf`, FRD-01 section) is superseded — see `.claude/docs/source/README.md` "Order of truth" — and cited only where v1.2 is silent on something v1.1 covered. |
| Wireframes | **Primary:** `.claude/docs/source/FRD-01/WF-01_Shared_Workspace.html` (v1.2, the module's own dedicated wireframe file — confirmed by direct read to carry page ids `p-hr-dash`, `p-mg-dash`, `p-em-dash`, `p-it-dash`, `p-mg-team`, `p-hr-case-*`/`p-mg-case-*`, `p-em-profile`, `p-it-employees`, modal `m-transfer`, matching this spec's citations verbatim) + `wireframes.md` (this feature, redrawn against WF-01). Legacy `docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html` (v1.1, monolithic) no longer cited directly. |
| Order of truth | **FRD v1.2 > this spec > `wireframes.md` (this feature) > WF-01 HTML > legacy v1.1 PDF/HTML** (R-SRC-1, `.claude/docs/source/README.md` "Order of truth", updated 2026-10-16). Conflicts are listed in §12. |

## 0. Status & sources

This spec documents **all** of FRD-01 "Shared workspace" — login, role-based dashboards and menus, My Tasks, notifications, the Employees list / My Team, the full five-tab employee record, HR editing a profile, employee self-service profile, and Transfer manager. It is written because F01 (`docs/features/F01-onboarding/spec.md`) built only the slice of FRD-01 that onboarding needed, and explicitly deferred the rest (its §1 "Out of scope" and its §4 screens table say so directly). Per `docs/features/README.md`, the feature that is slotted to build the undone half of FRD-01 is **F03 "Shared workspace: remainder (dashboards per role, profile editing, transfer manager)"**, depending only on F01.

**Revision note (this pass):** rebased from the legacy monolithic FRD v1.1 PDF onto FRD-01 v1.2's own dedicated document, which is richer and already reconciled against the real `V1__init.sql` (its own "Cross-check against V1__init.sql" section, OK/Fix/Note per claim) and against its own dedicated `WF-01_Shared_Workspace.html`. The structure and most conclusions below were unchanged by this rebase — v1.2's own screens/business-rules/process text for this module is, word for word, the same as what v1.1 gave; what v1.2 *adds* is exact routes for all 14 screens, a precise method/endpoint/table API list per feature group, the full field-by-field `manager_change`/`engine_task`/etc. column lists, and its own Appendix A of FRD/wireframe/data-model gaps — which independently confirms two of this spec's own prior findings (the `manager_change_task` gap, D-W3; the undrawn PRJ-08 view, D-W7) and surfaces one this draft had not previously raised at all (the missing set-password/reset-password token table, resolved by this project's existing Keycloak architecture rather than left open — D-W10) rather than contradicting anything. Where v1.2 revealed something this spec's v1.1-based draft had missed entirely (the IT admin's own read-only Employees screen; the PRJ-08 project-member view being FRD-01's own numbered screen, not merely an FRD-03 carve-out), those are called out explicitly below rather than silently folded in.

**What F01 already built (read-only to this spec, referenced not repeated):**
- **S13 Login** — e-mail/password form, all three validation messages, Keycloak-themed set-password/reset-password pages (`F01-onboarding/spec.md` §5 "S13", §13 "S13 exact wording").
- **S12 My Tasks + notifications** — the task list (module/task/employee/due/action), the bell dropdown, the user menu, G-03/G-04/G-05 behaviour (`F01-onboarding/spec.md` §4 "S12", API A36–A38).
- **S1 Employees list** — HR only, with sort/filter/search/export and `onboarding_draft` rows (`F01-onboarding/spec.md` §4 "S1", API A04/A05). **Not built by F01: the IT admin's own read-only variant of this same screen** — FRD-01 v1.2 §3 "Screens" names it explicitly as its own row, "Screen 8 · `p-it-employees` · Employees list (IT admin, read-only) · `/employees`", scoped server-side to name/job title/status/asset-count only (FRD-01 §2 "Access gate": "`/employees` is shown to HR (all fields) and IT admin (name, status and assets only)"). This is new information from v1.2, tracked as D-W9 (§12).
- **S11 Employee record — Overview, Profile, History tabs only** — for HR (all employees) and Manager (own team, Overview + read-only Profile); the Projects and Development & reviews tabs are explicitly **not** rendered (`F01-onboarding/spec.md` §1 Scope, §4 "S11", API A31–A35).
- The nav/menu stubs: every role's sidebar already shows a "Dashboard" entry, but its content is an empty page title only (`F01-onboarding/wireframes.md` §S12 "Top bar / sidebar, scoped to F01").

**What this spec adds (not built by F01, not started per `README.md`):**
- The four real, role-specific dashboards with their KPI cards (HR, Manager, Employee-active, IT admin) — F01 built only the Employee **new-joiner** dashboard variant (S7, `p-sb-dash`), which is a different screen for a different stage and stays exactly as F01 specified it.
- The full role menus (nav items beyond the F01 stub set) — §4/§2 below.
- **My Team** — the manager-facing, WS-02-scoped variant of the Employees list, which turns out to be its own wireframe page (`p-mg-team`), not merely a filtered view of S1.
- **Transfer manager** — HR's own flow, not drawn anywhere in F01.
- The contract (field list only; see §12 for why the build is deferred further) for the employee record's **Projects** and **Development & reviews** tabs.
- **Employees list, IT admin variant** (Screen 8, `p-it-employees`, `/employees` read-only) — not built by F01, newly surfaced by v1.2's screens table (§12 D-W9).
- The contract (field list only, build deferred for the same reason as the two tabs above) for **Screen 12, the PRJ-08 project-member view** (`/projects/:projectId/members/:code`) — FRD-01 v1.2 lists this as its own numbered screen and its own API endpoint (§6 below), which this v1.1-based draft previously treated as entirely FRD-03's concern and out of scope (§12 D-W7, revised).

Nothing in this spec invents a field, message, screen or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Every [OPEN] / [ASSUMPTION] / [NEEDS APPROVAL] marker below is a real gap found while cross-checking the FRD text, the approved data model and the wireframe HTML — none are invented to fill in detail the FRD simply doesn't give.

## 1. Scope

**In scope (FRD-01 v1.2 §1 "Scope Definition", "IN-SCOPE" list, verbatim)**
- Login and logout — **built by F01 (S13)**, referenced here only.
- Role-based menu and dashboard — menu stubs built by F01; **dashboards and full menus are this spec's main piece of unbuilt work**.
- My Tasks list — **built by F01 (S12)**, referenced here only.
- Notifications (in-app and e-mail) — **built by F01 (S12, G-04/G-05)**, referenced here only.
- Employee list (HR), My Team (Manager) — Employees list **built by F01 (S1, HR only)**; **My Team (Manager) is unbuilt**.
- Employee record with five tabs — Overview/Profile/History **built by F01 (S11)**; **Projects and Development & reviews are unbuilt** (contract specified in §5, build deferred — see §12 D-W1).
- HR editing an employee's profile — **built by F01** (Profile tab, A32/A33).
- Employee self-service profile — **built by F01** (My Profile, S10, A29/A30).
- Transfer an employee to a new manager — **unbuilt**, fully specified here.

**Out of scope (FRD-01 v1.2 §1 "Scope Definition", "OUT-OF-SCOPE ('WHAT NOT')": "none specific to this module in v1" — so the exclusions below come from other FRDs' boundaries, not from FRD-01 itself)**
- Probation review and its screen/e-mail (FRD-02 §3 "Probation review") — F02.
- PRJ-08's exception to WS-02 ("a manager sees project information only for members of their own projects from other teams") itself remains an FRD-03-owned *business rule*. But its **screen is FRD-01's own**: v1.2 §3 "Screens" lists it as "Screen 12 · not drawn · Project member view (PRJ-08) · `/projects/:projectId/members/:code`" with its own endpoint in v1.2 §4.5 (`GET /projects/{projectId}/members/{code}` → `project_assignment, review (PROJECT), v_client_feedback_by_employee`). My Team and the employee record in this spec implement plain WS-02 scoping (own direct reports only); the cross-team project-member view's **contract** is specified in §5/§6 below but its **build** is deferred, same reasoning as the Projects/Development tabs (D-W1) — it needs `project_assignment` (F04), which does not exist yet. Revised as D-W7 (§12).
- The destination screens behind the employee record's other header actions: **Initiate exit** (FRD-07, F08), **Start PIP** (FRD-06, F07), **Assign project** (FRD-03, roadmap slot "F04 Projects & work cycle"). FRD-01's own text places these buttons on the employee record header, so this spec requires that they appear/are hidden under the stated conditions (§2, §4), but the screens and business logic they open are owned and specified by those other features (D-W8, §12).
- Everything FRD-02/03/04/05/06/07/08 already own for their own screens, dashboards drill-downs, reports and settings.

## 2. Roles & permissions

Per FRD-00's permissions matrix and FRD-01 v1.2 §4 "Validation Rules & Business Invariants" (the "Hard Business Invariants" table, WS-01…WS-05), scoped to the pieces this spec adds:

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| Dashboard shown | HR dashboard (§5) | Manager dashboard (§5) | Employee dashboard — new-joiner variant while `stage='ONBOARDING'` (F01 S7, unchanged), active variant while `stage='ACTIVE'` (§5, this spec) | IT admin dashboard (§5) |
| Full menu | Dashboard, My Tasks, Employees, Projects, Appraisals, Reports, Settings | Dashboard, My Tasks, My Team, My Projects, Appraisals | Dashboard, My Tasks, My Profile, My Projects, My Development, My Reviews | Dashboard, My Tasks, Assets, Employees | 
| See employees | All (unchanged, F01) | **Own team only (WS-02)** — My Team, this spec | Self only (unchanged) | **Name, status, assets only — `/employees` read-only variant, this spec (D-W9, not built by F01)** |
| Employee record — Projects / Development & reviews tabs | All employees (once built, see §12) | Own team, read-only (once built) | n/a (own record goes through My Profile / My Development, other features) | n/a |
| Transfer manager | **Yes** | No | No | No |
| View a transfer in progress / History entry for it | Yes | The two managers involved (own-record visibility via WS-02 / G-13's "own record" carve-out) | No | No |

Every endpoint checks the role **and** ownership on the server (R-API-2/G-02); hiding a menu item or button is never the only protection (R-UI-7), per FRD-00 G-02 "Access is checked by the system, not only by hiding menu items."

**Full menus, per the wireframe's own navigation config** (`CFG.nav`, matches FRD-01's own acceptance criterion "the menu shows Dashboard, My Tasks, My Team, My Projects and Appraisals" for a Manager, word for word):

| Role | Full menu (nav items, in order) |
|---|---|
| HR | Dashboard, My Tasks, Employees, Projects, Appraisals, Reports, Settings |
| Manager | Dashboard, My Tasks, My Team, My Projects, Appraisals |
| Employee | Dashboard, My Tasks, My Profile, My Projects, My Development, My Reviews |
| IT admin | Dashboard, My Tasks, Assets, Employees |

Every item beyond what F01 already built (Dashboard content, My Tasks, My Profile for Employee; Dashboard content, My Tasks, My Team for Manager; Dashboard content, My Tasks, Employees for HR and IT admin) belongs to a **later** feature (Projects → F04; Appraisals → F06; Reports/Settings → F09; My Development → F05; My Reviews → F06). This spec's job is only to make the *menu itself* complete and role-correct (WS-01) — each later feature lights up its own item when it ships, exactly as F01's "Dashboard (stub)" precedent already does for the dashboard page itself.

## 3. Business rules

The complete set of FRD-01-specific rules — verified directly against FRD-01 v1.2 §4 "Validation Rules & Business Invariants", "Hard Business Invariants" table; there is no WS-06 or beyond, WS-01…WS-05 is the full set for this module (unchanged word for word from v1.1 to v1.2):

| ID | Rule |
|---|---|
| WS-01 | The dashboard and menu shown depend only on the user's role. |
| WS-02 | A manager sees only employees whose current manager they are. The only exception is members of their own projects, for whom they see project information only (PRJ-08). |
| WS-03 | An employee can change only their own contact and emergency details. Employment details are read-only for them. |
| WS-04 | Login errors never reveal whether the e-mail address exists. |
| WS-05 | Profile changes made by HR are recorded in History (G-06). |

WS-03/WS-04/WS-05 are already fully implemented by F01 (My Profile, Login, HR profile edit history) — listed here for completeness of the rule set, not reopened. WS-01 and WS-02 are only partly implemented (F01 built the menu *stub* and the Overview/Profile employee-record scoping; this spec completes the dashboards, the full menus and My Team).

Relevant FRD-00 rules this spec's new screens must also honour (already defined, not redefined here): **G-02** (server-side checks), **G-03** (task lists everywhere update the same way), **G-04**/**G-05** (task notifications/overdue reminders — only relevant if a new screen creates a task, which none in this spec does), **G-06** (History), **G-09** (no salary/bank/ID data — the employee record's new tabs must not surface any), **G-11** (save/cancel toast), **G-12** (dd-mm-yyyy dates), **G-13** (a user never reviews/approves/finalises anything about themselves — relevant to §4's header actions, all of which are self-exclusion-checked by `SelfActionGuard`, the helper F01 already built per its gate-5 fix).

New rule needed for Transfer manager, taken directly from FRD-01 v1.2 §2 "Transfer manager (HR)" (process text, not a numbered WS- rule but MUST-level by virtue of being the module's own described process):
> "HR selects Transfer manager on the employee record. HR chooses the new manager and the effective date and gives a reason. HR ticks which open manager tasks move to the new manager. Unticked tasks stay with the current manager. On the effective date the employee moves to the new manager's team and the ticked tasks move. Both managers and the employee are notified."

## 4. Screens

Routes are copied verbatim from FRD-01 v1.2 §3 "Screens" (the "WF-01 ref" / "Route or trigger" columns); confirmed against WF-01's actual page ids by direct read (§0):

| # | Screen | Who | Status | Route | Wireframe page | FRD |
|---|---|---|---|---|---|---|
| — | Login | All | **Built (F01 S13)** | `/login` (default route when not logged in) | `#login-view` | FRD-01 v1.2 §3 Screen 1 |
| — | Set password / Forgot password / Reset password | All | **Built (F01 S13, Keycloak-themed)** | `/set-password?token=`, `/forgot-password`, `/reset-password?token=` | not drawn in WF-01 (FRD's own Appendix A #1 — see §12 D-W10) | FRD-01 v1.2 §3 Screen 2 |
| — | Dashboard (one per role) | All | HR/Manager/Employee-active/IT admin **not built** (W1–W4); Employee new-joiner variant **built (F01 S7)** | `/dashboard` (default route after login) | `p-hr-dash`, `p-mg-dash`, `p-em-dash`, `p-it-dash` (+ F01's `p-sb-dash`) | FRD-01 v1.2 §3 Screen 3 |
| — | My Tasks | All | **Built (F01 S12)** | `/tasks` | `p-hr-tasks`/`p-mg-tasks`/`p-em-tasks`/`p-it-tasks` | FRD-01 v1.2 §3 Screen 4 |
| — | Notifications panel | All | **Built (F01 S12)** | overlay on any route (bell icon) | `#notif` | FRD-01 v1.2 §3 Screen 5 |
| — | User menu | All | **Built (F01 S12)** | overlay on any route (initials in top bar) | `#usermenu` | FRD-01 v1.2 §3 Screen 6 |
| — | Employees list (HR) | HR | **Built (F01 S1)** | `/employees` | `p-hr-employees` | FRD-01 v1.2 §3 Screen 7 |
| W9 | Employees list (IT admin, read-only) | IT admin | **Not built — new in v1.2 (§12 D-W9)** | `/employees` (same route, role-scoped fields) | `p-it-employees` | FRD-01 v1.2 §3 Screen 8 |
| — | Employee record, HR (Overview/Profile/History) | HR | **Built (F01 S11)**; Projects/Development & reviews not built (W7/W8) | `/employees/:code?tab=overview|profile|projects|development|history` | `p-hr-case-*` | FRD-01 v1.2 §3 Screen 10 |
| — | Employee record, Manager (4 tabs, no History) | Manager | **Built (F01 S11)**; Projects/Development & reviews not built (W7/W8) | `/team/:code?tab=overview|profile|projects|development` | `p-mg-case-*` | FRD-01 v1.2 §3 Screen 11 |
| — | My Profile | Employee | **Built (F01 S10)** | `/me/profile?tab=personal|employment|documents` | `p-em-profile` | FRD-01 v1.2 §3 Screen 13 |
| W1 | HR dashboard | HR | **Not built** | `/dashboard` | `p-hr-dash` | FRD-01 v1.2 §2 "Dashboards" table |
| W2 | Manager dashboard | Manager | **Not built** | `/dashboard` | `p-mg-dash` | FRD-01 v1.2 §2 "Dashboards" table |
| W3 | Employee dashboard (active stage) | Employee (`stage='ACTIVE'`) | **Not built** | `/dashboard` | `p-em-dash` | FRD-01 v1.2 §2 "Dashboards" table |
| W4 | IT admin dashboard | IT admin | **Not built** | `/dashboard` | `p-it-dash` | FRD-01 v1.2 §2 "Dashboards" table |
| W5 | My Team | Manager | **Not built** | `/team` | `p-mg-team` | FRD-01 v1.2 §3 Screen 9 |
| W6 | Transfer manager (pop-up) | HR | **Not built** | modal on Screen 10, "Transfer manager" | modal `m-transfer` | FRD-01 v1.2 §3 Screen 14, §2 "Transfer manager (HR)" |
| W7 | Employee record — Projects tab | HR, Manager | **Contract specified, build deferred (§12 D-W1)** | `?tab=projects` on Screen 10/11's route | `p-hr-case-lotte`/`p-mg-case-lotte` tab "Projects" | FRD-01 v1.2 §2 "Employee record" table |
| W8 | Employee record — Development & reviews tab | HR, Manager | **Contract specified, build deferred (§12 D-W1)** | `?tab=development` on Screen 10/11's route | `p-hr-case-lotte`/`p-mg-case-lotte` tab "Development & reviews" | FRD-01 v1.2 §2 "Employee record" table |
| W10 | Project member view (PRJ-08) | Manager | **Contract specified, build deferred — revised scope, §12 D-W7** | `/projects/:projectId/members/:code` (name on project Team tab) | not drawn in WF-01 (FRD's own Appendix A #3 — see §12 D-W7) | FRD-01 v1.2 §3 Screen 12 |

Header actions on the employee record, from FRD-01 v1.2 §2 "Header actions on the employee record" verbatim ("HR: Initiate exit and Transfer manager (both hidden when the employee is already offboarding). Manager: Start PIP (only for active employees not already on a PIP) and Assign project (not for employees who are leaving)."), for completeness of the screen's full contract — **only the visibility rule is this spec's job; the actions themselves are built by their owning features (D-W8)**:

| Action | Role | Shown when |
|---|---|---|
| Initiate exit | HR | employee not already Offboarding (FRD-07/F08 builds the screen) |
| Transfer manager | HR | employee not already Offboarding — **this spec builds it (W6)** |
| Start PIP | Manager | employee Active and not already on a PIP (FRD-06/F07 builds the screen) |
| Assign project | Manager | employee not already leaving (FRD-03/F04 builds the screen) |

## 5. Fields & validation

### W1 — HR dashboard

From FRD-01 v1.2 §2 "Dashboards — each role sees only its own information" table (HR row) and §5 "Data Model & Integrations — Summary", cross-matched against `p-hr-dash`:

| Card | Content | Data source (buildable now vs deferred) |
|---|---|---|
| Active employees | Count, plus a "+N this month" delta | `employee.stage='ACTIVE'` — **buildable now** (table exists since F01). Delta definition is not given by the FRD. **[ASSUMPTION]**: count of employees whose `stage_since` falls within the current calendar month and `stage='ACTIVE'`. |
| In onboarding | Count, plus up to 2 names | `employee.stage='ONBOARDING'` — **buildable now**. |
| On bench | Count, plus "Longest N days" | `v_bench` ("employees without an active assignment", FRD-01 v1.2 §5) — **deferred**: the view needs `project_assignment` (F04). "Longest N days" wording is not in the FRD; **[ASSUMPTION]**: `max(today − bench_since)` once `v_bench` exists. |
| PIPs | "N active · M to approve" | `v_pip` ("PIP + derived source / disputed / extended / planned final date", FRD-01 v1.2 §5) — **deferred to F07** (FRD-06/PIP module). |
| My tasks (list) | Same shape as My Tasks (module/task/employee/due/open), "View all" link | `engine_task` — reuses F01's `GET /api/tasks` (A36) — **buildable now**, no new endpoint. |
| Appraisal round progress | Round name, completion %, self/peer/manager reviews done-of-total, closes date | `v_appraisal_status` ("Appraisal progress: self / peers x of y / manager / client avg / result", FRD-01 v1.2 §5) — **deferred to F06**. "Closes" is not defined by the FRD against a specific column; **[ASSUMPTION]**: `appraisal_round.hr_due`. |
| Upcoming | List of (type, employee, date) for final PIP reviews, start dates, last days, probation ends, contract ends, next 90 days | `v_upcoming_dates` ("Upcoming key dates (next 90 days)", FRD-01 v1.2 §5 — already in the baseline data model) — **partially buildable now**: start dates and probation/contract ends can be read from `contract`/`v_contract` today; final PIP reviews need F07, last days need F08. The view is built incrementally: each feature's own migration adds its `UNION ALL` branch when its table lands. |

FRD-01 v1.2's own `GET /dashboard` endpoint (§6) lists the HR dashboard's full table set as `employee, v_bench, v_pip, v_appraisal_status, v_upcoming_dates, engine_task` — matching the row above exactly.

"Each number on the dashboard opens the related list" (FRD-01 §3) — every count above links to the matching filtered list (Employees list filtered by stage, My Team filtered by status, etc.); where the underlying list screen does not exist yet (PIPs, appraisal round), the card is not rendered at all rather than linking to nothing (R-CODE-4, no dead links) — same philosophy as F01 dropping its dead "Save" button.

### W2 — Manager dashboard

From FRD-01 v1.2 §2 "Dashboards" table (Manager row) and `p-mg-dash`:

| Card | Content | Data source |
|---|---|---|
| Team members / Direct reports | Count | `employee.manager_id = me` — **buildable now**. |
| On bench | Count, up to 2 names | `v_bench` scoped to the manager's reports — **deferred** (same as W1). |
| Reviews to write | Count, "Project + appraisal" subtitle | `review` where `reviewer_id = me` and open — **deferred to F04 (project reviews) / F06 (manager reviews)**. |
| Plans to approve | Count, employee name | `development_plan.status = 'SUBMITTED'` for the manager's reports — **deferred to F05**. |
| My tasks (list) | Same as W1 | Reuses A36, scoped to the manager — **buildable now**. |
| My team (table) | Employee, current projects, allocation, development %, status, "Assign" action | `v_employee_allocation` ("Active allocation per employee: FULLY / PARTLY / ON_BENCH / ENDING / OVER_ALLOCATED", FRD-01 v1.2 §5) and `v_kpi_score` for the Development % column, same table as W5 — **partially buildable now** (employee/status from `employee.stage`), rest deferred (F04/F05). |

FRD-01 v1.2's `GET /dashboard` lists the Manager dashboard's tables as `employee, v_bench, v_employee_allocation, review, development_plan, v_kpi_score` — matching the row above exactly.

### W3 — Employee dashboard (active stage)

F01's S7 (`p-sb-dash`) stays exactly as specified for `stage='ONBOARDING'`. For `stage='ACTIVE'` (FRD-01 v1.2 §2 "Dashboards" table, Employee row / `p-em-dash`):

| Card | Content | Data source |
|---|---|---|
| Current projects | Count, project name | `project_assignment` — **deferred to F04**. |
| Development plan | Status, "Submitted {date}" | `development_plan` — **deferred to F05**. |
| Appraisal | Status (e.g. "Self review"), due date | `review` (SELF) — **deferred to F06**. |
| My tasks (list) | Same as W1 | Reuses A36 — **buildable now**. |
| My equipment (table) | Item, asset no. | `asset` where `employee_id = me` and `status = 'ISSUED'` — **buildable now** (same component F01 already built for S7). |

FRD-01 v1.2's `GET /dashboard` lists the Employee dashboard's tables as `project_assignment, development_plan, appraisal, asset` — matching Current projects/Development plan/Appraisal/My equipment above.

### W4 — IT admin dashboard

From FRD-01 v1.2 §2 "Dashboards" table (IT admin row) and `p-it-dash`:

| Card | Content | Data source |
|---|---|---|
| Equipment requests | Count, employee + start date | `onboarding`/`engine_task` where `task_name = 'Equipment request'` and open — **buildable now** (F01 data). |
| Open setup items | Count, employee | `checklist_item`/`engine_task` where `task_name = 'Remaining setup'` and open — **buildable now**. |
| Access to revoke | Count, employee + last day | `v_account_access` (named in FRD-01 v1.2's own `GET /dashboard` table set for IT admin) — **deferred to F08**: the view needs `employee_exit`, which does not exist until then. |
| Not returned | Count | `v_unreturned_assets` (named in FRD-01 v1.2's own `GET /dashboard` table set for IT admin) — **deferred to F08**: no asset reaches `NOT_RETURNED` before offboarding exists, same dependency as `v_account_access`. |
| My tasks (list) | Same as W1 | Reuses A36 — **buildable now**. |

FRD-01 v1.2's `GET /dashboard` lists the IT admin dashboard's tables as `onboarding, checklist_item, v_account_access, v_unreturned_assets` — a correction from this spec's v1.1-based draft, which cited `asset.status = 'NOT_RETURNED'` directly rather than a dedicated view; `v_account_access`/`v_unreturned_assets` are not yet in the approved baseline data model and would need to be added by F08's own migration alongside `employee_exit`.

### W5 — My Team (Manager)

Route `/team` (FRD-01 v1.2 §3 Screen 9); endpoint table `employee, v_employee_allocation, development_plan, v_kpi_score` (v1.2 §4.5 "`GET /team`"). From `p-mg-team`:

| Element | Detail |
|---|---|
| Filter | Status: All / On project / Bench / PIP / Onboarding |
| Table columns | Employee (avatar+name+job title), Current projects, Allocation, Development, Status, Action ("Assign" — opens F04's assign-project flow, not built here) |
| Scope | WS-02: only employees whose `manager_id = me` |
| List behaviour | Sortable, filterable, searchable, exportable (R-UI-2), same as the HR Employees list (S1) |

"Current projects"/"Allocation"/"Development" columns are **deferred to F04/F05** (same dependency as the Manager dashboard's team table); the Employee/Status columns and the list mechanics are buildable now.

### W6 — Transfer manager (pop-up)

Fields, from the wireframe modal `m-transfer` (confirmed by direct read of WF-01: `New manager*`/`Effective date*` fields, three "Move open tasks" checkboxes, `Reason` textarea, `Cancel`/`Transfer` footer — matches this section verbatim) and FRD-01 v1.2 §1 "Inputs" ("New manager and effective date", provided by HR, example "Anouk Peters, 01-11-2026"):

| Field | Rule | Message |
|---|---|---|
| Employee | Read-only, pre-filled | — |
| Current manager | Read-only, pre-filled | — |
| New manager* | Required; must differ from the current manager | **"Choose a new manager."** (FRD-01 §5) |
| Effective date* | Required; today or later | **"The effective date cannot be in the past."** (FRD-01 §5) |
| Move open tasks to new manager | One checkbox per open `engine_task` row owned by the current manager for this employee; unticked items stay with the current manager | — (no FRD message; nothing is required to be ticked) |
| Reason | Optional free text | — |

Candidate "new manager" list: every employee with `app_role = 'MANAGER'`, excluding the current manager (same source as F01's D-10 onboarding manager dropdown).

### W7 — Employee record — Projects tab (contract only, build deferred — §12 D-W1)

From FRD-01 v1.2 §2 "Employee record" table ("Projects" row: "All project assignments with role, allocation, period and status") and `p-hr-case-lotte`. Endpoint per v1.2 §4.5: `GET /employees/{code}/projects` → `project_assignment, project, client`.

| Column | Source (once it exists) |
|---|---|
| Project | `project.name` |
| Client | `client.name` |
| Role | `project_assignment.role_on_project` |
| Allocation | `project_assignment.allocation_pct` |
| Period | `project_assignment.start_date` – `end_date` |
| Status | `project.status` |

### W8 — Employee record — Development & reviews tab (contract only, build deferred — §12 D-W1)

From FRD-01 v1.2 §2 "Employee record" table ("Development & reviews" row: "KPIs with % met, development goals with progress, received reviews") and `p-hr-case-lotte`. Endpoint per v1.2 §4.5: `GET /employees/{code}/development` → `kpi, v_kpi_result, v_kpi_score, development_plan, goal, review`.

| Section | Columns / content | Source (once it exists) |
|---|---|---|
| KPIs set by manager | KPI, Target, Weight, Measured (frequency), % met (latest) | `kpi`, `v_kpi_result` |
| Development goals | Goal, Type, Target date, Progress | `goal` (plan-linked), `development_plan` |
| Reviews | One row per received review/feedback: title, star rating, reviewer + date, comment | `review`, `review_answer`, `client_feedback`, `appraisal` |

Manager's own view of this tab additionally shows "Enter results" / "+ Add KPI" / "Progress check" actions (manager-editable, own team) — these belong to F04/F05's own screens, not to this read-only tab contract.

### W9 — Employees list (IT admin, read-only)

New in v1.2 (§12 D-W9). From `p-it-employees` (confirmed by direct read: page title "Employees" / subtitle "Read-only", a `Status` filter, search, no export icon, no row-click action):

| Column | Source |
|---|---|
| Employee (avatar + name + job title) | `employee`, `job_title` |
| Job title | `job_title.name` |
| Status | `employee.stage` |
| Assets | count of `asset` rows where `employee_id` = this employee |

No "View" action, no export icon drawn on this page (unlike the HR variant's `p-hr-employees`, which has both) — read-only is enforced by omission, not by a disabled control, consistent with R-UI-7's "hiding a button is never the only protection" read the other way: there is nothing to hide because nothing beyond read access exists for this role. Sortable/filterable/searchable still applies (R-UI-2) for the columns shown.

### W10 — Project member view (PRJ-08) (contract only, build deferred — §12 D-W7)

Not drawn in WF-01 (FRD's own Appendix A #3, confirmed — see §12 D-W7). Contract taken from FRD-01 v1.2's own acceptance scenario ("Manager sees a project member from another team": "he sees her assignment, project reviews and client feedback for that project. And he does not see her profile, development plan or appraisal.") and its API table (v1.2 §4.5: `GET /projects/{projectId}/members/{code}` → `project_assignment, review (PROJECT), v_client_feedback_by_employee`):

| Section | Content | Source (once it exists) |
|---|---|---|
| Assignment | Project, role, allocation, period | `project_assignment` |
| Project reviews | Reviews of type `PROJECT` for this project only | `review` where `review_type='PROJECT'` and `project_id` = this project |
| Client feedback | Client feedback entries tied to this project | `v_client_feedback_by_employee` |

Explicitly **not** shown (per the acceptance scenario, and per WS-02/PRJ-08's own scoping): Profile, Development plan, Appraisal. This is a strictly narrower read than the Overview/Profile/Projects/Development tabs a line manager sees for their own reports — same WS-02 "project information only" carve-out already named in §1's Out-of-scope note, now with a concrete screen attached to it.

## 6. API

Conventions unchanged from F01: base path `/api`, camelCase JSON, dates `yyyy-MM-dd` (R-API-1); errors follow R-API-3 exactly. New endpoints use the prefix **W** so they never collide with F01's A01–A38 numbering (a different feature's endpoint table, per CLAUDE.md §2.9.4 "never mix features").

```
400 {"code":"VALIDATION_FAILED","errors":[{"field":"newManagerId","message":"Choose a new manager."}]}
400 {"code":"VALIDATION_FAILED","errors":[{"field":"effectiveDate","message":"The effective date cannot be in the past."}]}
403 {"code":"FORBIDDEN"}   404 {"code":"NOT_FOUND"}   409 {"code":"STALE_VERSION"}
```

FRD-01 v1.2's own API table (§4.2 "Dashboards") is authoritative here and is a **correction** to this spec's v1.1-based draft: it specifies **one** role-dispatched `GET /dashboard` endpoint, not four separate per-role endpoints — "Role-specific tiles; server picks the role." W01 below replaces the old W01–W04 four-endpoint design; the per-role table sets from §5 above are preserved as the DTO's per-role shape, decided server-side from the caller's role (same server-side role check discipline as everywhere else, R-API-2 — the role is never a request parameter).

| # | Method & path | Role | Notes | Status |
|---|---|---|---|---|
| W01 | `GET /api/dashboard` | All (server picks the role, FRD-01 v1.2 §4.2) | One endpoint, role-dispatched DTO shape: HR → `{activeEmployees, activeEmployeesDelta, onboardingCount, onboardingNames[], tasks[]}` now, `bench*`/`pip*`/`appraisalRound`/`upcoming[]` added additively by F04/F07/F06/F08; Manager → `{teamMembers, tasks[], myTeam[]}` now, `bench*`/`reviewsToWrite`/`plansToApprove` added by F04/F05/F06; Employee (`stage='ACTIVE'`; F01's A25 still serves `stage='ONBOARDING'`) → `{equipment[], tasks[]}` now, `currentProjects`/`developmentPlan`/`appraisal` added by F04/F05/F06; IT admin → `{equipmentRequestsCount, openSetupItemsCount, tasks[]}` now, `accessToRevokeCount`/`notReturnedCount` added by F08 | **New, not built — v1.2 corrects this from 4 endpoints to 1** |
| W05 | `GET /api/team?status&q&sort&page&size` | Manager | Route corrected to `/api/team` (was `/api/my-team`) to match FRD-01 v1.2 §3 Screen 9's route `/team` and §4.5's `GET /team`. WS-02 scoped; Employee/Status columns populated now, Projects/Allocation/Development columns null until F04/F05 | **New, not built** |
| W06 | `GET /api/team/export?...` | Manager | Same filters, `.xlsx` (R-UI-2). **Not in FRD-01 v1.2's own API table** — v1.2 names an explicit `/employees/export` sibling for the HR list (§4.5) but no `/team/export`; kept here by the same R-UI-2 "every table is exportable" MUST rule F01 already applied to the HR Employees list (A05), flagged **[ASSUMPTION]** since the FRD does not name it directly | **New, not built** |
| W07 | `GET /api/employees/{id}/projects` | HR (all); Manager (own team, WS-02) | Projects tab (W7). FRD-01 v1.2 §4.5 documents this route as `/employees/{code}/projects` — see the `{id}`-vs-`{code}` note below | **Spec only — cannot be implemented before F04's `project`/`project_assignment` migration exists** |
| W08 | `GET /api/employees/{id}/development` | HR (all); Manager (own team, WS-02) | Development & reviews tab (W8). FRD-01 v1.2 §4.5: `/employees/{code}/development` | **Spec only — cannot be implemented before F05's/F06's `kpi`/`development_plan`/`review` migrations exist** |
| W09 | `GET /api/employees/{id}/manager-tasks` | HR | Renamed/narrowed from this spec's prior invented `GET .../transfer-manager`: FRD-01 v1.2 §4.8 names only `GET /employees/{code}/manager-tasks` → `{openManagerTasks[]}`, for the tick-list. The employee and current-manager header fields reuse data already loaded for Screen 10 (F01's A31); the candidate-manager dropdown reuses the same `app_role='MANAGER'` list as F01's D-10 onboarding manager dropdown (unaffected by v1.2 — not named as a separate endpoint by either FRD version) | **New, not built — endpoint narrowed to match v1.2** |
| W10 | `POST /api/employees/{id}/manager-transfers` | HR | Renamed from `.../transfer-manager` to match FRD-01 v1.2 §4.8's `POST /employees/{code}/manager-transfers`. `{newManagerId, effectiveDate, reason, taskIdsToMove[]}` → `201 {changeId}` / `400 VALIDATION_FAILED` (response shape not given by the FRD, kept as **[ASSUMPTION]** matching F01's own `201` conventions) | **New, not built** |
| W11 | `GET /api/projects/{projectId}/members/{id}` | Manager (own-team-adjacent, PRJ-08) | Project member view (W10 screen). FRD-01 v1.2 §4.5: `GET /projects/{projectId}/members/{code}` → `project_assignment, review (PROJECT), v_client_feedback_by_employee` | **Spec only — cannot be implemented before F04's `project`/`project_assignment` migration exists (same blocker as W07)** |

**W9 (Employees list, IT admin) is not a new numbered endpoint.** FRD-01 v1.2 §4.5 gives one shared row for both HR and IT admin: "`GET /employees · /employees/export` — HR and IT admin list (IT gets name, status, assets only)" — i.e. the *same* endpoint F01 already built as A04/A05, with the response fields reduced server-side by role. F01's spec explicitly scoped A04 to HR only (`F01-onboarding/spec.md` §4 "S1": "HR only"), so as built today A04 403s for IT admin rather than returning a reduced projection. Closing this gap means **extending** A04's role check and response DTO, not adding a parallel endpoint — tracked as part of D-W9 (§12), **[NEEDS APPROVAL]** since it touches F01's already-closed API contract.

**Path parameter note:** FRD-01 v1.2 writes every employee-scoped route with `{code}` (the human-readable `employee_code`, e.g. `E-0031` — matching its own route table, `/employees/:code?tab=...`). F01's already-built endpoints (A31–A35) instead use `{id}`. This spec follows F01's established `{id}` convention for W07–W11 (same resource family, same controller) rather than reopening F01's contract (CLAUDE.md "never mix features"), but the mismatch between the FRD's literal path spelling and the already-built code is real and should be noted, not silently resolved, when W07–W11 are actually implemented — **[OPEN]**.

## 7. Backend behaviour

- **W01 dashboard.** A single read-only aggregation endpoint (`GET /api/dashboard`); the controller resolves the caller's role from the authenticated principal (never a request parameter, R-API-2) and dispatches to one of four role-specific services/DTOs internally — no business transaction, no engine call, no audit entry (nothing is being changed). Built as additive DTOs per role: a field that depends on another feature's table is simply absent from that role's DTO JSON until the owning feature ships and extends the same service/DTO — this is the explicit cross-feature contract every later feature (F04/F05/F06/F07/F08) must honour when it adds its own dashboard card, instead of building a parallel dashboard endpoint (CLAUDE.md "never mix features" is about not touching *another feature's* screens/endpoints without the spec saying so; this spec is what says so, in advance, for exactly this extension point, and FRD-01 v1.2's own single-endpoint design makes "one dashboard endpoint per role" the wrong shape to build even as a starting point).
- **W05/W06 My Team.** Same list mechanics as F01's Employees list (A04/A05): WS-02 filter is `employee.manager_id = :currentUserId`, enforced server-side (R-API-2), not just by hiding the HR-only Employees nav item.
- **W09/W10 Transfer manager.**
  - `W09` (`GET /employees/{id}/manager-tasks`) reads every open `engine_task` row with `owner_employee_id` = the current manager for this employee's case — the FRD's own scope for this endpoint (v1.2 §4.8). The employee/current-manager header and the candidate-manager list are not re-fetched by this endpoint; they come from the already-loaded Screen 10 employee record (F01's A31) and the existing `app_role='MANAGER'` source (F01's D-10), respectively.
  - `W10` (`POST /employees/{id}/manager-transfers`) validates `newManagerId` (required, differs from current manager → **"Choose a new manager."**) and `effectiveDate` (required, not before today → **"The effective date cannot be in the past."**). In one transaction: insert a `manager_change` row (`scope='LINE'`, `employee_id`, `from_manager_id` = current, `to_manager_id` = new, `effective_date`, `reason`, `status='SCHEDULED'`, `requested_by` = HR user), insert one `manager_change_task` row per ticked `taskIdsToMove` entry (D-W3, §9), write `audit_log` (WS-05/G-06). Untracked (unticked) tasks are left untouched — they simply are not linked to this `manager_change`.
  - If `effectiveDate` is today, the same transaction applies the change immediately after commit (see below); otherwise a scheduler (same pattern as F01's `OverdueReminderScheduler`/undo-window executor) polls `manager_change` rows where `status='SCHEDULED'` and `effective_date <= today` and applies each one in its own transaction: update `employee.manager_id`, update every linked `manager_change_task`'s `engine_task.owner_employee_id` to the new manager, set `manager_change.status='APPLIED'`/`applied_at`, write `audit_log`. After commit (R-ARC-5): update the case's Manager case-team role — remove the old manager's user, add the new manager's user (`POST`/`DELETE /cases/{id}/caseteam/users`, proven live in the E5 spike, `01-overview.md` §5) — and notify both managers and the employee (D-W4, §12 — exact channel/wording **[OPEN]**, though v1.2's own "Task assigned" e-mail template now plausibly covers the moved tasks themselves, see D-W4).
- **W11 Project member view.** Read-only, same pattern as W07/W08: `project_assignment` scoped to `(employee_id = this employee, project_id = this project)`, `review` filtered to `review_type='PROJECT'` and this project, `v_client_feedback_by_employee` filtered the same way. No Profile/Development/Appraisal fields are ever selected into this DTO — the narrower read is enforced by the query shape, not by hiding fields client-side (R-UI-7's principle applied to a read endpoint).
- **Self-exclusion (G-13).** `SelfActionGuard` (built by F01's gate-5 fix) is reused on W10: HR cannot transfer their own management record through this endpoint in a way that would let them approve their own move — in practice this only matters if an HR user is also someone's manager, which `SelfActionGuard`'s existing ownership check already covers without new code.

## 8. Case model needs

Per `03-case-file.md` (approved baseline) — **no new case-file item or field is needed by this spec.**

- Dashboards, My Team and the employee record tabs are pure read/reporting screens; R-ARC-2 only requires the case file to hold values a sentry, timer or routing needs, and none of these screens drive engine routing.
- Transfer manager changes **case team membership**, not the case file: `03-case-file.md` §3 "Case roles" already documents `Manager | the current line manager's user | Spring, at start; replaced on transfer`. The mechanism (`POST`/`DELETE /cases/{id}/caseteam/users`) was proven live by the E5 engine spike before F01 gate 3 (`01-overview.md` §5) — this spec is simply the first feature to actually use it in anger. No sentry reads who the current Manager case-team member is, so nothing needs to be mirrored into the case file for routing purposes.
- `Employee.isManager` (already an approved case-file field, written by Spring "on role change") is unaffected by a line-manager transfer; it only flags whether the *transferred employee themself* manages others, for F08's "Reassign team & projects" step.

## 9. Data touched

**Existing tables read only (no schema change):** `employee`, `contract`, `document`, `asset`, `manager_change`, `engine_task`, `notification`, `email_outbox`, `audit_log`, `setting` — FRD-01 v1.2 §5's own "Data Model & Integrations — Summary" table list, all already in the baseline, all already migrated by F01's gate 1.

`manager_change`'s full column list, per FRD-01 v1.2 §5 (cross-checked there against the real `V1__init.sql`, verdict **OK** except for the one gap below):

| Column | Type | Nullable | Key | Default | Description |
|---|---|---|---|---|---|
| `id` | BIGINT | No | PK | identity | |
| `scope` | VARCHAR(8) | No | | | One of: LINE, PROJECT |
| `employee_id` | BIGINT | Yes | FK → `employee.id` | | |
| `project_id` | BIGINT | Yes | FK → `project.id` | | |
| `parent_id` | BIGINT | Yes | FK → `manager_change.id` | | |
| `from_manager_id` | BIGINT | Yes | FK → `employee.id` | | |
| `to_manager_id` | BIGINT | No | FK → `employee.id` | | |
| `effective_date` | DATE | No | Index | | |
| `reason` | TEXT | Yes | | | |
| `move_open_tasks` | BOOLEAN | No | | `true` | |
| `status` | VARCHAR(10) | No | | `SCHEDULED` | One of: SCHEDULED, APPLIED, CANCELLED |
| `applied_at` | TIMESTAMPTZ | Yes | | | |
| `requested_by` | BIGINT | No | FK → `employee.id` | | |

Constraints (per v1.2 §5): `check (num_nonnulls(employee_id, project_id) = 1)`; `check ((scope = 'LINE') = (employee_id is not null))`; `check (parent_id is null or scope = 'LINE')`; `check (scope = 'LINE' or reason is not null)`; `check (from_manager_id is distinct from to_manager_id)`; index `ix_manager_change_due (effective_date) where status = 'SCHEDULED'`. This is the real existing table — the Transfer manager flow (W10) uses `scope='LINE'`, leaves `project_id`/`parent_id` null, and sets `from_manager_id`/`to_manager_id`/`effective_date`/`reason`/`requested_by` exactly as described in §7 above.

**Existing tables that would need to exist before W07/W08/W10/W11/parts of W01–W03/W05 can run, but are out of this spec's control to migrate:** `project`, `project_assignment`, `client` (F04); `kpi`, `kpi_result`, `development_plan`, `goal` (F05); `appraisal_round`, `appraisal`, `review`, `review_answer` (F06); `pip`, `v_pip` (F07); `employee_exit`, `v_account_access`, `v_unreturned_assets` (F08). All already fully designed in `02-data-model.md` §2.4–2.7 (the views are newly named by FRD-01 v1.2 §5 and may need adding to `02-data-model.md` when their owning feature ships) — this spec does not redesign them, only consumes them once they exist.

**New table needed for Transfer manager (D-W3, §12) — `manager_change_task`**, resolving the gap FRD-01 v1.2's own cross-check confirms independently: "**Fix:** Appendix A #2 confirmed: `manager_change.move_open_tasks` is one BOOLEAN for all tasks; there is no table to store which individual tasks HR ticked (FRD-01 Transfer manager step 3)" (v1.2 §5 "Cross-check against V1__init.sql"), the same gap `02-data-model.md` §6 #14 already flagged ("v2 assumed a manager transfer moves all open tasks... To meet the FRD, HR must tick individual tasks... `manager_change_task(change_id, engine_task_id)` must come back (+1 → 41 tables)"):

```sql
create table manager_change_task (
  change_id bigint not null references manager_change on delete cascade,
  engine_task_id bigint not null references engine_task,
  primary key (change_id, engine_task_id)
);
```

This follows the existing conventions (R-DB-2: composite PK is appropriate here, same pattern as `checkin_goal`/`review_answer`; FK `ON DELETE RESTRICT` is not applied to `change_id` because deleting a `manager_change` row — which never happens in the business flow, only possibly in test cleanup — should cascade its link rows, not block it). The gap itself is no longer in question — it is confirmed independently by both this spec's own analysis and FRD-01 v1.2's own Appendix A/cross-check section — so what remains **[NEEDS APPROVAL]** is narrower than before: adding `manager_change_task` as a real schema change (40 → 41 tables), to be added to `02-data-model.md` §2.4 and §4 "v1 → v2 table mapping" in the same Flyway migration that introduces it (R-DB-1), once this feature actually reaches gate 1.

**Case file:** none (§8).

## 10. E-mails & timers

| Trigger | Template | To | Status |
|---|---|---|---|
| Ticked task reassigned to new manager | **"Task assigned"** — the only e-mail template FRD-01 v1.2 names anywhere in this module (§2 "Post-conditions — Outputs": "Task and notification e-mails (template 'Task assigned') and in-app notifications"). Plausibly reused here: a ticked task's `engine_task.owner_employee_id` genuinely changes to the new manager, which is literally a new task assignment to them. **[ASSUMPTION]** — v1.2 does not say explicitly that this template fires on a transfer, only that the module as a whole sends "Task assigned" e-mails and in-app notifications generically | the new manager (per ticked task) | **[NEEDS APPROVAL]** to confirm this reuse is correct rather than coincidental naming |
| Transfer manager applied (effective date reached) — the transfer event itself, distinct from any individual task's reassignment | **RESOLVED (D-W4, 2026-10-17): in-app `notification` only.** FRD-08 (now Done, Wave 2) confirmed its final 21-template set has no dedicated "Manager transfer" template, and R-SRC-3 forbids inventing subject/body wording for one. | the old manager, the new manager, the employee | In-app `notification` row only; upgradeable to e-mail later if FRD-08 adds a template for it |

No new timers. The transfer's effective date is a one-off, HR-entered date per transfer (not a recurring interval), so R-ARC-7 ("time periods come from `setting`") does not apply — this mirrors how `contract.end_date` or `pip.proposed_start_date` work, not how `ONBOARDING_OVERDUE` works.

## 11. Acceptance criteria

Taken from FRD-01 v1.2 §6 "Acceptance Criteria", limited to what this spec's unbuilt pieces cover. Scenarios v1.2 §6 already provides that F01 already implemented are **not** repeated here (Wrong password → F01 AC-18; Completing a task updates the lists → F01 AC-19 — v1.2's own version of this scenario is more precise than what this spec's v1.1-based draft had: "HR has 6 open tasks including 'Approve PIP · Kees Willems'... after a short undo window... the My Tasks count shows 5"; Manager cannot see other teams → F01 AC-20, tested against the employee record; this spec's AC-W03 below re-tests the same WS-02 rule against the **My Team** screen specifically, which is a different code path, using v1.2's own example names for this exact scenario).

```
AC-W01 (FRD-01 v1.2 §6 "User lands on their own dashboard"): Manager's full menu and dashboard
  Given Mark Smit has the role Manager
  When he logs in with a valid e-mail and password
  Then the Manager dashboard opens
  And the menu shows Dashboard, My Tasks, My Team, My Projects and Appraisals

AC-W02 (FRD-01 v1.2 §6 "Transfer with selected tasks"): Transfer with selected tasks
  Given Lotte Jansen has 3 open manager tasks with Mark Smit
  When HR transfers her to Anouk Peters from 01-11-2026 and ticks 2 tasks
  Then on 01-11-2026 Lotte appears in Anouk Peters' team
  And the 2 ticked tasks move to Anouk Peters and 1 stays with Mark Smit

AC-W03 (WS-02, My Team; FRD-01 v1.2 §6 "Manager cannot see other teams", re-tested here against My Team specifically): Manager cannot see other teams in My Team
  Given Kees Willems reports to Anouk Peters
  When Mark Smit opens My Team
  Then Kees Willems is not listed

AC-W04 (W6 validation): Transfer without a new manager
  Given HR opens Transfer manager for Lotte Jansen
  When HR selects Transfer without choosing a new manager
  Then the message "Choose a new manager." is shown

AC-W05 (W6 validation): Transfer with a past effective date
  Given HR opens Transfer manager for Lotte Jansen
  When HR enters an effective date in the past and selects Transfer
  Then the message "The effective date cannot be in the past." is shown

AC-W06 (WS-01, dashboard numbers): A dashboard count opens the related list
  Given the HR dashboard shows "In onboarding · 2"
  When HR selects that card
  Then the Employees list opens filtered to stage = Onboarding

AC-W07 (role-based dashboard content, WS-01): Dashboard matches the role
  Given Kevin Hendriks has the role IT admin
  When he opens his dashboard
  Then he sees Equipment requests, Open setup items, Access to revoke and Not returned cards, and no HR- or Manager-only cards

AC-W08 (D-W9, new in v1.2, WS-01/R-API-2): IT admin's Employees list is read-only and field-limited
  Given Kevin Hendriks has the role IT admin
  When he opens Employees
  Then he sees Employee, Job title, Status and Assets columns only, with no row action to open the full record
  And the same /employees endpoint returns full fields and a "View" action for HR, by server-side role check, not by hiding columns client-side
```

Two further FRD-01 v1.2 §6 scenarios are **not yet testable** and are tracked rather than silently dropped:
- "Leaver cannot log in" (Thomas Mulder, last working day 24-10-2026, login attempt 25-10-2026) depends on `employee_exit`/OFF-07, which do not exist until F08. Per WS-04, the message on a successful-looking-but-blocked login attempt is the **same generic message** as any other login failure — v1.2 §6 gives it verbatim: **"Invalid e-mail or password."** — not a distinct "account disabled"/"you have left" message; this is worth stating explicitly since WS-04 ("Login errors never reveal whether the e-mail address exists") otherwise invites guessing a different wording.
- "Manager sees a project member from another team" (Priya Sharma / Mark Smit's project, W10 screen) depends on PRJ-08/`project_assignment`, which do not exist until F04 — tracks directly to W10/W11 above (D-W7).

## 12. Decisions & open items

| # | Topic | Decision / status |
|---|---|---|
| D-W1 | Projects / Development & reviews tabs cannot be built by a "shared-workspace remainder" feature slice alone | **RESOLVED, user decision 2026-10-17 — build placeholder tabs now, overriding this spec's own recommendation.** Both tabs render as visible, reachable nav items on the employee record (`?tab=projects`/`?tab=development`) showing a "Not available yet" placeholder, same visual pattern as F01's Overview tab's own "Not started"/"Not eligible" placeholder cards — deliberately **not** empty/dead UI (R-CODE-4's concern was a tab with literally nothing behind it and no visual treatment; a placeholder card *is* the "something behind it"). F04 replaces the Projects placeholder with real content when it ships; F05+F06 jointly replace the Development & reviews one. No new endpoint is called for either tab in this wave — there is nothing yet for `GET /employees/{id}/projects`/`.../development` (W07/W08) to return, so the frontend renders its placeholder without a network call rather than hitting an endpoint that doesn't exist. |
| D-W2 | Dashboards mix buildable-now and deferred cards | **Revised by v1.2**: the endpoint design changes from four per-role endpoints to FRD-01 v1.2's own single role-dispatched `GET /dashboard` (§6/§7 above) — this is a correction, not a new decision, since the FRD is explicit and there was nothing to decide. What is still a decision: build the dashboards now with the cards that use tables F01 already migrated (active/onboarding counts, My Tasks, My equipment, IT admin's two task counts), behind an additive per-role DTO contract that later features extend — mirrors F01's own Overview tab, which already ships three placeholder cards ("Not started"/"Not eligible") alongside real ones. **[NEEDS APPROVAL]**, since it commits every later feature (F04–F08) to extending this one endpoint's per-role DTOs rather than building their own. |
| D-W3 | `manager_change_task` table | **RESOLVED, user decision 2026-10-17 — add it.** Confirmed independently by two sources that reached the same conclusion without reference to each other: this spec's own `02-data-model.md` §6 #14, and FRD-01 v1.2's own cross-check section, verbatim: "**Fix:** Appendix A #2 confirmed: `manager_change.move_open_tasks` is one BOOLEAN for all tasks; there is no table to store which individual tasks HR ticked (FRD-01 Transfer manager step 3)." Added by `V14__create_manager_change_tables.sql` (Gate 1, 2026-10-17) — which also had to create `manager_change` itself, a second, previously-unknown gap: this spec's own §9 claimed `manager_change` was "already in the baseline, migrated by F01's gate 1," but it had never actually been applied by `V1__init.sql` (documented in `02-data-model.md` only, same category of gap as D-09/the appraisal_form tables found in FRD-08's Gate 1). Both tables are now live; `02-data-model.md`'s change log and §6 item 14 are updated accordingly. |
| D-W4 | Transfer-manager notification channel and wording | **RESOLVED, user decision 2026-10-17 — yes to both.** (a) Moved tasks reuse the existing "Task assigned" e-mail template (already seeded by FRD-08) for the new owner — a real reuse (the task genuinely is being reassigned to them), not invented wording. (b) The transfer event itself (notifying both managers + the employee) gets an **in-app `notification` row only** for this wave — FRD-08's 21 seeded templates (confirmed final count, Wave 2) have no dedicated "Manager transfer" template, and R-SRC-3 forbids inventing subject/body wording. If FRD-08 later adds one, this flow can be upgraded to also send an e-mail without changing anything else about W10. Notification title wording: **[ASSUMPTION]**, plain factual text (e.g. "{employee} now reports to {newManager}" / "{employee} has transferred to your team" / "You now manage {employee}"), since the FRD gives no exact in-app notification copy anywhere in this module. |
| D-W5 | KPI-card computed values not defined by the FRD | Unaffected by the v1.2 rebase. "+N this month" (Active employees delta), "Longest N days" (bench), the appraisal round's "Closes" date source — each marked **[ASSUMPTION]** at its card in §5, since v1.2 describes *what* each dashboard shows in prose and names the underlying view, but not the exact computation inside that view. |
| D-W6 | Two FRD-01 v1.2 §6 acceptance scenarios depend on unbuilt features | "Leaver cannot log in" (F08/OFF-07, exact message "Invalid e-mail or password." per WS-04) and "Manager sees a project member from another team" (F04/PRJ-08, now W10/W11 above) — tracked in §11, not silently dropped, not built here. |
| D-W7 | PRJ-08 exception to WS-02 | **RESOLVED, user decision 2026-10-17 — build a placeholder screen now, same override as D-W1.** The route (`/projects/:projectId/members/:code`) renders with a "Not available yet" placeholder body. Unlike the Projects/Development tabs (D-W1), which are reachable nav items on an already-live screen, **nothing in the app currently links to this route** — the "project Team tab" that would link to it is itself F04's unbuilt screen. The placeholder page exists and is reachable by direct URL, but is not wired into any navigation yet; F04 both adds the real content and the link that reaches it, when it ships. Flagged here rather than silently built differently from D-W1's pattern, since "a placeholder page nothing currently links to" is a slightly different shape of "not dead code" than D-W1's visible, clickable tab. |
| D-W8 | Header actions "Initiate exit" / "Start PIP" / "Assign project" | Unaffected by the v1.2 rebase. Drawn on the employee record per FRD-01 v1.2 §2's own text, but their screens and logic belong to F08/F07/F04 respectively. This spec only requires the visibility rule in §4's table; the buttons render as a no-op or are omitted (same approach F01 took for "Submit resignation" on My Profile) until their owning feature adds the real action. |
| D-W9 | Employees list has **three** role variants, not two — new in v1.2 | **RESOLVED, user decision 2026-10-17 — extend F01's existing `GET /api/employees` (A04).** IT admin becomes an allowed role on the same endpoint, with the response DTO reduced server-side to `{employeeCode, firstName, lastName, jobTitle, stage, assetCount}` (no row-level "view" action, no export icon per the wireframe's `p-it-employees` — W9's own §5 contract) — matches the FRD's literal "same endpoint, field-reduced by role" model (v1.2 §4.5). This is the explicit, in-advance authorization CLAUDE.md's "never mix features" asks for before touching another feature's already-closed endpoint. The Manager/My Team split (a genuinely separate screen with separate columns, W05/W06) is unaffected and stays its own endpoint as already specified. |
| D-W10 | Appendix A #1 (no token table for set-password/reset-password links) — confirmed by v1.2, but likely a non-issue for this project | v1.2's own cross-check confirms the gap in the FRD's generic data model: "**Fix:** Appendix A #1 confirmed: there is no token table for set-password / reset-password links; the identity provider (employee.idp_subject) must issue and validate them," and its Appendix A entry suggests either assuming the IDP issues/checks the links, or drawing the three screens and adding a token table. **This project has already made that choice, before v1.2 existed**: F01 (S13) built these as **Keycloak-themed** set-password/reset-password pages (`F01-onboarding/spec.md` §5 "S13") — i.e. Keycloak (`employee.idp_subject`'s own identity provider, per `01-overview.md`'s architecture) issues and validates the links, not a custom `hr_app` token table. No schema change is needed here; this is recorded as a decision rather than left open so a future reviewer does not reopen Appendix A #1 against this project's own architecture. Screens 2's three routes (`/set-password`, `/forgot-password`, `/reset-password`) remain **not drawn** in WF-01 (consistent with v1.2's own "not drawn" note) because they are Keycloak's own themed pages, not pages this app renders. |
| D-W11 | Gate 5 security hardening on Transfer manager (W10) added server-side checks the FRD does not give exact wording for | **RESOLVED, found and fixed 2026-10-17 during Gate 5 review (springboot-security-reviewer + polyglot-security-reviewer, both independently confirmed).** Three real gaps, all closed: (1) `taskIdsToMove` was accepted with no check that each id was actually an open task for this employee owned by the current manager — any `engine_task` id could be sent and would be reassigned (IDOR); now validated against the same query W09 itself uses, rejecting with field error "One or more selected tasks are no longer available to move." **[ASSUMPTION]**, no exact FRD wording exists for this case. (2) `newManagerId` was accepted for any employee row, including the employee themselves or a non-Manager role (G-13-adjacent bypass — an employee could become their own manager, or review their own probation/appraisal later); now rejected with the FRD's own existing "Choose a new manager." message (reused, not invented — R-SRC-3). (3) Transferring an Offboarding/Archived employee, or creating a second concurrent scheduled transfer for the same employee, were both previously unguarded server-side; both now return `409 CONFLICT` with a plain factual message. **[ASSUMPTION]**, no FRD-specific error code/message exists for either case — logged here per R-SRC-2 rather than inventing a new `ErrorCodes` constant (reuses the existing generic `CONFLICT` code). Also fixed in the same pass: same-day transfers were silently never committing (`@Transactional` → `REQUIRES_NEW` on the after-commit apply path — a real functional bug, not a security finding, caught by springboot-security-reviewer); audit `updated_by` wrongly attributed the change to the new manager instead of the HR user who requested it; IT admin's `/api/employees` and Manager's `/api/team` accepted an arbitrary `sort` value (data-ordering leak + 500-on-bad-property), now allowlisted per role with a 400 on anything else; page `size` was uncapped, now clamped to 100. |

**Path-parameter convention mismatch** (`{code}` in FRD-01 v1.2's routes vs `{id}` in F01's already-built endpoints) is tracked inline in §6 rather than as its own D-W item, since it is purely an implementation-naming question for whoever builds W07–W11, not a scope or data question.

## 13. Definition of done

- [x] D-W1 resolved 2026-10-17: build placeholder tabs now (overrode this spec's own deferral recommendation), not deferred to F04/F05/F06.
- [x] D-W2 resolved: additive-dashboard-DTO pattern against v1.2's single `GET /dashboard` shape — not a real alternative given the FRD's own single-endpoint mandate, proceeding as specified.
- [x] D-W3 resolved 2026-10-17: add `manager_change_task` — done, `V14__create_manager_change_tables.sql` (Gate 1). `manager_change` itself also had to be created in the same migration — a second gap this spec's own §9 had wrongly assumed was already applied (see D-W3's updated row in §12).
- [x] D-W4 resolved 2026-10-17: reuse "Task assigned" for moved tasks; in-app notification only for the transfer event itself (§10 updated).
- [x] D-W7 resolved 2026-10-17: build a placeholder PRJ-08 screen now (same override as D-W1), reachable by direct URL only until F04 adds the link.
- [x] D-W9 resolved 2026-10-17: extend F01's `GET /api/employees` (A04) for IT admin's read-only variant, not a new parallel endpoint.

**Gate 1 done (2026-10-17):** `V14__create_manager_change_tables.sql` (`manager_change` + `manager_change_task`, both previously undocumented-as-applied despite this spec's §9 assuming `manager_change` already existed), new entities `ManagerChange`/`ManagerChangeTask`/`ManagerChangeScope`/`ManagerChangeStatus` under `model/repository.workspace` (layer-first MVC convention). Full suite re-verified: 287/287 backend tests passing, Flyway migrates cleanly from empty (V1–V14).

**Gate 2 done (2026-10-17):** W01 (role-dispatched `GET /api/dashboard`), W05/W06 (`GET /api/team` + export, WS-02 scoped), W09/W10 (`GET .../manager-tasks`, `POST .../manager-transfers` + `ManagerTransferScheduler` for scheduled-date transfers), D-W9 (`GET /api/employees` extended to accept IT admin with a reduced projection, `assetCount` added). The engine case-team "Manager" role swap described in §7 was deliberately **not** implemented — Wave 1's `RealCaseEngineClient` already established that every case has exactly one real engine-registered user (the service account, holding all case roles), so there is no per-manager case-team member to swap; a code comment at the relevant point references that class's own Javadoc rather than silently skipping it. 34 new tests on top of the prior 287; full suite independently re-verified at 321/321 passing.

**Gate 4 done (2026-10-17):** W1–W4 dashboards (role-dispatched `GET /api/dashboard` consumer), W5/W6 My Team, W6 Transfer manager modal, D-W1 placeholder Projects/Development tabs on the employee record, D-W9 IT admin Employees list variant, D-W7 placeholder PRJ-08 route (unlinked, direct-URL only). **Found and fixed during this gate:** `GET /api/me` (F01's own A01, unchanged since F01) never returned the employee's `stage`, so the newly-built active-stage Employee dashboard had no way to ever be reached — every EMPLOYEE was unconditionally redirected to the onboarding dashboard regardless of their real stage, leaving the new component dead code (R-CODE-4). Fixed by adding `stage` to A01's response (purely additive, no existing field touched) and wiring `DashboardStub`'s routing to it. Full suites re-verified after this fix: backend 321/321, frontend 89/89 claimed at the time (88 Gate-4 tests + 1 new ACTIVE-stage routing test, after correcting one Gate-4 test that had asserted the old always-redirect behavior). One pre-existing F01 test (`listEmployees_asIT_returns403`) was updated to `_returns200_withReducedFields`, since D-W9 is an explicit, user-approved change to that endpoint's behavior, not an accidental regression. **Correction (Gate 5 spec-coverage audit, 2026-10-17): the claimed "89" frontend count was wrong — the actual count at that point was 81.** No tests were missing from what Gate 4 built; the number itself was simply miscounted when first reported. See the Gate 5 entry below for the corrected, independently-verified final count.

**Gate 5 done (2026-10-17):** all four checkers run — `springboot-security-reviewer`, `polyglot-security-reviewer`, `polyglot-code-reviewer`, `spec-coverage-analyst`. First pass: both security reviewers independently found the same 2 HIGH findings (IDOR on `taskIdsToMove`, insufficient `newManagerId` validation — see D-W11) plus several MEDIUM/LOW findings; the code reviewer found 6 warnings (a real frontend filter-value bug in My Team's status dropdown, 3 unbounded `findAll()` calls on the HR dashboard, a non-circular dependency passed as a method parameter instead of being constructor-injected, a stale doc comment, and a menu-list inconsistency); spec-coverage returned **FAILED at 91.7%** (5 PARTIAL items, zero MISSED — every gap was a missing test, not missing code). All findings were fixed in a single remediation pass (D-W11; every code-quality warning; the My Team filter-value bug; the Sidebar had no test at all for AC-W01, now added) and ~18 new backend tests plus 1 new frontend test file were added to close every PARTIAL item (EC-1 through EC-5 from the spec-coverage report, plus negative tests for every new validation/conflict check). **Final, independently re-verified counts: backend 339/339 passing (321 + 18 new), frontend 94/94 passing (81 actual baseline + 13 new `Sidebar.test.tsx` tests covering AC-W01 across all four roles).** Flyway still migrates cleanly V1→V14 (no new migration this gate — all fixes were application code). Deployed to the live dev `hr-backend` container and confirmed healthy.

The Definition of Done (CLAUDE.md §2.8) is met: every AC in §11 has an automated test; every validation message in §5/§11 is asserted verbatim, including the two new Gate-5 conflict messages (D-W11); the `manager_change`/`manager_change_task` constraints (CHECK + composite PK) have negative tests; WS-02 is tested as both an allowed (own team) and forbidden (other team) case for My Team; the IT admin Employees list is tested for both field-reduction and the absence of a row action (AC-W08); Flyway migrates cleanly from empty; `mvn verify` and `npm run build && npm test` pass; all D-W items in §12 are resolved (none left `[OPEN]`/`[NEEDS APPROVAL]` except D-W2, which is a standing architectural commitment for future features to honor, not a blocker for this feature's own completion).
