# FRD-03 — Projects and work cycle

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-03/FRD-03_Projects_and_Work_Cycle.docx` (primary, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), `FRD-00/FRD-00_Application_Overview.docx` (roles, permissions matrix, G-01…G-13, case-file inventory), `FRD-08/FRD-08_Reports_and_Settings.docx` (the `BENCH_CHECK` timer, the "Project assigned" e-mail template, the Bench and Project allocation reports that read this module's views). Legacy v1.1 PDF superseded — not cited. |
| Wireframes | `.claude/docs/source/FRD-03/WF-03_Projects_and_Work_Cycle.html` (v1.2, canonical) — page ids `p-hr-projects`, `p-mg-projects`, `p-hr-proj`, `p-mg-proj`, `p-mg-proj-new`, `p-mg-proj-edit`, `p-mg-bench`, `p-mg-proj-review`, `p-em-projects` and modals `m-assign`, `m-client-fb`, `m-change-pm`, all confirmed present by direct read (exactly the 12 the FRD's §3 screens table cites, nothing more) + `wireframes.md` (this folder). |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-03 HTML > legacy v1.1 (R-SRC-1). Conflicts are listed in §12. |
| Relationship to feature builds | This is the full contract for roadmap slot **F04 "Projects & work cycle"** (`features/README.md`, depends on F01; status "Not started — schema already applied"). **Nothing of FRD-03 is built** — no controller, service, entity or screen exists for projects, assignments, bench checks, client feedback, project reviews or project-manager changes. **But part of its schema is already applied**: `V13__create_report_tables_and_views.sql` (F09 Gate 2, user-approved 2026-10-17) created `client`, `project`, `project_assignment`, `client_feedback`, `review`, `v_bench`, `v_employee_allocation`; `V14__create_manager_change_tables.sql` (F03) created `manager_change` + `manager_change_task`. This spec does **not** redesign them (§9 lists what is still missing). Placeholders already shipped by F03 (FRD-01) wait for this feature: the employee record's Projects tab (FRD-01 W7, D-W1), the PRJ-08 project-member page `/projects/:projectId/members/:code` (FRD-01 W10/W11, D-W7), My Team's "Assign" action and the "Assign project" header action (FRD-01 D-W8). |

## 0. Status & sources

FRD-03 v1.2 describes the **work cycle** of active employment (FRD-00 §3: "Work cycle — project assignments, bench checks and project reviews (FRD-03)"). Business objective, verbatim: "Keep track of who works on which client project, who is on the bench, and how each person performed, including what the client said, so this evidence is ready at appraisal time."

The FRD is organised as: §1 Context/scope/inputs, §2 User flow (seven sub-flows + project-status and allocation-status tables), §3 Screens (12 screens) with "Features, Endpoints & Database" sub-sections 4.1–4.8, §4 Validation table + PRJ-01…PRJ-08, §5 Database requirements (case-file items, full column lists, views, its own "Cross-check against V1__init.sql"), §6 Acceptance criteria (8 scenarios) and "Open Items (TBC)" — which reads, in full, "No open items were found for this module while mapping the FRD, wireframes v6.1 and data model v2." This spec found several anyway, mostly FRD-vs-applied-schema and FRD-vs-wireframe gaps (§12); each one is cited to its source, none is a design choice made silently.

Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Every `[OPEN]` / `[ASSUMPTION]` marker is a real gap.

**What exists in the repository today (checked 2026-10-19):**
- **Schema:** see header row above, and §9 for the gap list (`project_contact`, `review_answer`, `decision`, `v_client_feedback_by_employee` and two FRD-listed indexes are **not** applied; `v_employee_allocation` is applied but drifts from the FRD's allocation-status table — D-03-4).
- **Backend:** only read-side uses of the applied tables — `ReportService` (F09) reads `v_bench` and `v_employee_allocation` for the Bench/Allocation reports; `TeamService` (F03) still notes "project_assignment (F04) … tables do not exist yet" in a comment (stale since V13, harmless); `ManagerChange`/`ManagerChangeScope` entities + `ManagerTransferApplier`/`ManagerTransferScheduler` (F03) implement `scope='LINE'` only. No `PROJECT`-scope handling exists.
- **Settings seeded:** `BENCH_CHECK` (V10: 7 days, `MANAGER`, `can_disable=false`), `PROJECT_ASSIGNED` e-mail template (V9, `to_employee` only). **Not seeded:** a `PROJECT`-type `appraisal_form` (V12 seeds Standard/Leadership/Probation only — D-03-6); any `TASK_DUE` row (D-03-17).
- **Case model:** the deployed `employeecase.type` declares only `Employee` and `Onboarding`; `employeecase.case` has only onboarding tasks + "Employee onboarded". `employeecase.caseteam` already declares a `ProjectManager` role ("stay unassigned until later features (F03+) nominate members"). No `Bench` / `ProjectReview` item, no "Check project status" / "Project review" task. (The legacy `emp.case` has `Checkprojectstatus`, `StayOnBench`, `AssignProject`, `ProjectReview`, `ClientReview` human tasks — reference only, R-CM-1 forbids extending it.)
- **Frontend:** `ProjectsTab.tsx` and `ProjectMemberPage.tsx` placeholders (FRD-01 D-W1/D-W7); no `/projects`, `/me/projects`, `/project-reviews` or bench-check route.

## 1. Scope

**In scope (FRD-03 §1 "IN-SCOPE", verbatim)**
- Projects: create, view, edit, delete (Manager)
- Assigning employees with role and allocation %
- Bench and the 7-day bench check
- Project review per team member when a project closes or a person leaves it
- Client feedback recorded by the manager
- HR changing a project's manager

**Out of scope (FRD-03 §1 "OUT-OF-SCOPE", verbatim)**
- Timesheets and billing — not in v1
- Client login — not in v1 (also G-07 "Clients never log in.")

**Also out of scope here (other FRDs own them):**
- The employee record's Projects tab and the PRJ-08 project-member view are **FRD-01 screens** (FRD-01 v1.2 §3 Screens 10–12); F04 supplies their data and the link from the Team tab, FRD-01's spec owns their layout (FRD-01 W7, W10/W11).
- Starting a PIP from a "Performance concern" review (FRD-06 / F07) — this module only offers the entry point.
- Using project reviews and client feedback in the appraisal (FRD-05 / F06) — this module only produces them.
- The Bench and Project allocation reports (FRD-08 / F09, **Done**) — they read `v_bench` / `v_employee_allocation`; this module owns the data behind them.
- Ending assignments because an employee leaves (`end_reason='EMPLOYEE_LEAVING'`) and "Reassign team & projects" at offboarding (FRD-07 / F08) — see D-03-19.

**Pre-conditions (FRD-03 §2, verbatim):** "The user is a Manager (for creating and changing projects) or HR (read-only, change manager)." / "Employees to assign have completed onboarding." Dependency (FRD-03 §1): "Employees must have completed onboarding (FRD-02)." / "Project reviews and client feedback are used in FRD-05."

## 2. Roles & permissions

From FRD-00 §2 "Permissions" (rows quoted) and FRD-03 §3 "Access gate": "HR sees every project read-only; its only action is Screen 11. A manager sees the projects they manage, and members from other teams only for that project (PRJ-08). Screens 5–10 are for the project manager only (PRJ-01). Employees see their own assignments in Screen 12."

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| See projects | All, read-only (S1, S3) | Projects they manage (S2, S4) | Own assignments only (S12) | No |
| Create, edit, delete projects; assign people (FRD-00: "Create, edit, delete projects; assign people") | No | Own projects | No | No |
| Change a project's manager (FRD-00) | **Yes** (S11) | No | No | No |
| Add client feedback, write project reviews (FRD-00) | No | Own projects | No | No |
| Bench check ("Check project status" task) | No | Yes — the employee's manager (FRD-08 Timers: "Bench check \| 7 days \| Manager") | No | No |
| See employees (FRD-00) | All | "Own team, plus members of own projects (project information only)" | Self only | Name, status and assets only |
| See client feedback (FRD-03 §1 constraint) | All (read-only, S3) | "A manager sees client feedback for their own team and for members of their own projects (PRJ-08)." | **[OPEN]** — not stated (D-03-16) | No |

Every endpoint checks role **and** ownership server-side (R-API-2/G-02): "own projects" means `project.project_manager_id = current user`. G-13: a manager never writes a project review about themselves — relevant when a manager assigns themself to their own project; **[ASSUMPTION]** such an assignment's review is not created for that manager as reviewer (no FRD wording; flagged as D-03-20).

## 3. Business rules

Copied from FRD-03 §4 "Hard Business Invariants" (R-SRC-3 — wording exact):

| ID | Rule | Priority |
|---|---|---|
| PRJ-01 | Only the project manager creates, edits and deletes their projects, assigns people and adds client feedback. HR can only view projects and change the project manager. When the project manager changes, the project and all its assignments move to the new manager, and team members who reported to the previous manager move to the new manager as their line manager (unless HR unticks them). | MUST |
| PRJ-02 | An employee can be on several projects at the same time. | MUST |
| PRJ-03 | A project whose end date has passed shows status Overdue until the manager marks it Completed. | MUST |
| PRJ-04 | Setting a project to Completed or Withdrawn creates one project review task per team member still assigned. | MUST |
| PRJ-05 | Client feedback is linked to the project and to every person it applies to, and is kept for appraisal. | MUST |
| PRJ-06 | A project with assignments or reviews cannot be deleted; it can be set to Completed, or to Withdrawn if the client stops it midway. | SHOULD |
| PRJ-07 | Assigning above 100% total allocation is allowed after a warning. | SHOULD |
| PRJ-08 | A project manager sees members of their projects who belong to other teams, limited to that project: assignment, project reviews and client feedback. The employee's line manager also sees these items, and they are used in the employee's appraisal (FRD-05). | MUST |

"Global rules G-01 … G-13 (FRD-00, section 4) also apply to this module." Most relevant: **G-02** (server-side checks), **G-03** (task disappears after the undo window), **G-04** (every new task → in-app + e-mail), **G-06** (History), **G-08** (periods from Settings, never fixed), **G-11** (save toast / Cancel never saves), **G-12** (dd-mm-yyyy), **G-13** (never review oneself).

**Project status** (FRD-03 §2 "Project status", verbatim):

| Status | When |
|---|---|
| Planned | The project is created and the start date is in the future |
| Active | The start date has been reached |
| On hold | The manager pauses the project; assignments stay |
| Overdue | The end date has passed and the project is not Completed (set by the system) |
| Completed | The manager closes the project; project review tasks are created (PRJ-04) |
| Withdrawn | The client stops the project midway; the manager enters the withdrawal date and reason; assignments end on that date and project review tasks are created (PRJ-04) |

**Allocation status** (FRD-03 §2 "Allocation status", verbatim):

| Status | When |
|---|---|
| Fully allocated | Total allocation 80–100% |
| Partly available · x% | Total allocation 1–79%; x = free percentage |
| On bench | No active assignment |
| Ending | An assignment ends within 14 days |
| Over-allocated | Total allocation above 100% |

Process rules quoted from FRD-03 §2 (not paraphrased):
1. Assign: "Any active employee can be assigned, including employees from other teams; their line manager is notified." / "The employee is notified (template "Project assigned"). If the employee was on the bench, the bench check stops."
2. Bench: "An employee with no active assignment is "On bench"." / "Every 7 days (configurable) the manager gets a "Check project status" task." / "The manager chooses "Assign project" or "Stay on bench" and can add a note. "Stay on bench" creates the next check in 7 days."
3. Client feedback: "At any time during or after a project, the manager selects "+ Client feedback"." / "The feedback appears on the project and on the record of each person it applies to."
4. Project review: "When the manager sets a project to Completed or Withdrawn, or an employee's assignment ends early, the manager gets one "Project review" task per person." / "The manager rates the person, writes strengths and areas to develop, and sees all client feedback for that project." Outcomes: "Project delivered — the review is saved; the employee continues to the next project or the bench." / "Performance concern — the review is saved and the manager may start a PIP (FRD-06)." Inputs row: "(one review per team member, individually, not as a batch)".
5. Change PM: "On the effective date the new manager becomes the project manager with full edit rights. The whole project team (all assignments) moves with the project." / "Team members whose line manager is the previous project manager also move to the new manager as their line manager (Transfer manager, FRD-01). This is ticked by default; HR can untick it per person. Team members from other teams keep their own line manager." / "Both managers and every employee whose line manager changes are notified."

## 4. Screens

Routes and wireframe refs copied from FRD-03 §3 "Screens" ("FRD-03 has 12 screens across 8 features"). S-numbers = the FRD's own screen numbers.

| # | Screen | Who | Route / trigger | Wireframe page | FRD |
|---|---|---|---|---|---|
| S1 | Projects list (HR, all, read-only) | HR | `/projects` · menu "Projects" | `p-hr-projects` | §3 Screen 1, 4.1 |
| S2 | My Projects (Manager) | Manager | `/projects` · menu "My Projects" (scoped to own projects) | `p-mg-projects` | §3 Screen 2, 4.1 |
| S3 | Project page, HR read-only (Overview, Team, Client feedback) | HR | `/projects/:projectId?tab=overview\|team\|feedback` · row in S1 | `p-hr-proj` | §3 Screen 3, 4.1 |
| S4 | Project page, Manager | Manager (PM) | `/projects/:projectId?tab=…` · row in S2 | `p-mg-proj` | §3 Screen 4, 4.1 |
| S5 | New project (full page) | Manager | `/projects/new` · "+ New Project" | `p-mg-proj-new` | §3 Screen 5, 4.2 |
| S6 | Edit project (full page, Delete on the left) | Manager (PM) | `/projects/:projectId/edit` · "Edit project" | `p-mg-proj-edit` | §3 Screen 6, 4.2 |
| S7 | Assign to project (pop-up) | Manager | Modal on S4, My Team, employee record, S9 · "+ Assign employee", "Assign", "Assign project" | `m-assign` | §3 Screen 7, 4.3 |
| S8 | Add client feedback (pop-up) | Manager (PM) | Modal on S4 and S10 · "+ Client feedback", "+ Add" | `m-client-fb` | §3 Screen 8, 4.5 |
| S9 | Check project status (bench) | Manager | `/team/:code/bench-check/:taskId` · task "Check project status" | `p-mg-bench` | §3 Screen 9, 4.4 |
| S10 | Project review | Manager (PM) | `/project-reviews/:reviewId` · task "Project review"; project Team tab ("Write review") | `p-mg-proj-review` | §3 Screen 10, 4.6 |
| S11 | Change project manager (pop-up) | HR | Modal on S3 · "Change project manager" | `m-change-pm` | §3 Screen 11, 4.7 |
| S12 | My Projects (Employee) | Employee | `/me/projects` · menu "My Projects" | `p-em-projects` | §3 Screen 12, 4.8 |

S1 and S2 share the route `/projects`; the server picks the scope by role (same pattern as FRD-01's `/employees` HR/IT-admin split). The menu label differs ("Projects" for HR, "My Projects" for Manager — FRD-01 §2 full menus). **Related FRD-01 screens fed by this module:** employee record Projects tab (FRD-01 W7, `?tab=projects`) and project member view (FRD-01 W10, `/projects/:projectId/members/:code`, reached from "name on project Team tab" — FRD-00 Appendix A #3: "The limited project-member view (PRJ-08) is not drawn. Suggested fix: Add a read-only page reached from the project Team tab.").

## 5. Fields & validation

All messages are FRD-03 §4 "Validation Table" wording, exact (R-SRC-3). Dates are entered/shown dd-mm-yyyy (G-12), sent as ISO (R-API-1).

### S1 / S2 — Projects lists

| Column | Source |
|---|---|
| ID | `project.project_code` (e.g. "P-024") |
| Project | `project.name` |
| Client | `client.name` |
| Manager (S1 only) | PM name via `project.project_manager_id` |
| People | count of `project_assignment` rows for the project — **[ASSUMPTION]** counts current (not ended) assignments; the wireframe shows "3" on a Completed project whose assignments have all ended, so it may mean *all* assignments ever. D-03-21. |
| Period | `start_date` – `end_date` |
| Status | `project.status` |
| Action | "View" → S3/S4 |

Filters per wireframe: S1 Status + Manager, S2 Status, plus search; sort/export per R-UI-2 (FRD 4.1 lists `/projects/export`). Wireframe status filters list only All/Active/Planned/Completed — **[ASSUMPTION]** offer all six FRD statuses (FRD wins).

### S3 / S4 — Project page

Header: name, `project_code · client · status`. KPI cards (wireframe): Period, Team (people count), Client feedback (count, "Last {date}"), Project reviews ("x / y completed" — submitted `review` (PROJECT) rows vs. reviews for this project). Actions: S3 → "Change project manager" only; S4 → "Edit project", "+ Client feedback".

| Tab | Content | Source |
|---|---|---|
| Overview — Project | Project ID, Project manager, Type, Start date, End date, Status, Work mode, Tech stack | `project` |
| Overview — Client | Client, Industry, Address, Point of contact (name · role), E-mail, Phone, Secondary contact, Contract ref. | `client`, `project_contact` (POC, SECONDARY), `project.contract_reference` |
| Overview — Description | free text | `project.description` |
| Team | Team member, Role, Allocation, Assigned (period), Review ("Due {date}" / "Reviewed · {date}" / S4 only: "Write review" → S10). S4 has "+ Assign employee". | `project_assignment`, `employee`, `v_employee_allocation`, `review` (PROJECT) |
| Client feedback | Contact · role, star rating, `received_date · collected_via · applies to`, comments, attachment. S4 has "+ Add". | `client_feedback`, `document` |

HR acceptance criterion (FRD §6): "the only action available is "Change project manager"" and "the Team and Client feedback tabs have no add buttons".

### S5 / S6 — New / Edit project

| Field | Type | Rule | Message |
|---|---|---|---|
| Project name | text (≤150) | Required | "This field is required." |
| Project manager | dropdown | Required | "This field is required." — see D-03-8 on who may be chosen |
| Type | Fixed price / Time & material / Internal → `FIXED_PRICE` / `TIME_MATERIAL` / `INTERNAL` | Required | "This field is required." |
| Status | dropdown | Required | "This field is required." — see D-03-7 (wireframe offers Planned/Active/On hold/Completed only) |
| Start date | date | Required | "This field is required." |
| End date | date | After start date | "The end date must be after the start date." |
| Work mode | text (≤100) | optional | – |
| Tech stack | text (≤250) | optional | – |
| Client name | text (≤150) | Required | "Enter the client name and a valid contact e-mail." |
| Industry, Address | text | optional | – |
| Point of contact | text | Required | "Enter the client name and a valid contact e-mail." |
| POC role, POC phone | text | optional | – |
| POC e-mail | e-mail | Required; valid e-mail | "Enter the client name and a valid contact e-mail." |
| Secondary contact | text | optional | – (D-03-22: wireframe has one text box; `project_contact` SECONDARY has name/role/email/phone) |
| Contract reference | text (≤60) | optional | – (`project.contract_reference`) |
| Description | text area | optional | – |
| Withdrawal date and reason | date + text | Required when status is Withdrawn | "Add the withdrawal date and reason." (FRD Inputs: "withdrawal date and reason when Withdrawn"; **not drawn** — D-03-7) |
| Delete project (S6 only) | button, left, red outline | Only without assignments or reviews | "This project has team members or reviews and cannot be deleted. Set the status to Completed or Withdrawn instead." |

S5 has no Delete button (FRD §6: "no Delete button was shown on the new project form"). S5 primary button "Create project", S6 "Save changes" (green, right); Cancel red outline left (R-UI-1).

### S7 — Assign to project

| Field | Rule | Message |
|---|---|---|
| Employee (dropdown shows "{name} — {current allocation}%", from `/employees/assignable`) | Required | "This field is required." |
| Project (shown when opened outside a project: My Team, employee record, S9) | Required | "This field is required." **[ASSUMPTION]** — not in the FRD's validation row, but the assignment cannot exist without it |
| Role on project | Required | "This field is required." |
| Allocation % | Required; 1 to 100 | "This field is required." / "Allocation must be between 1 and 100." |
| Start date | Required | "This field is required." |
| End date | optional; DB: `end_date >= start_date` | **[OPEN]** no FRD message for an assignment end date before its start (D-03-23) |
| Total allocation | Warning above 100% (PRJ-07) | "Total allocation will be {x}%. Do you want to continue?" — confirm sends `confirmOverAllocation=true` (FRD 4.3) |

### S8 — Add client feedback

| Field | Rule | Message |
|---|---|---|
| Client | read-only, from the project | – |
| Client contact | Required | "This field is required." |
| Contact role | optional | – |
| Date received | Required; not in the future | "This field is required." / "The date cannot be in the future." (FRD Note: "needs today and is enforced in Spring") |
| Collected via (Call / E-mail / Meeting / Survey → `CALL`/`EMAIL`/`MEETING`/`SURVEY`) | DB NOT NULL; not in FRD's required list, not starred in wireframe | **[ASSUMPTION]** "This field is required." (D-03-14) |
| Applies to (Whole project team / one assigned person → `TEAM` / `PERSON` + `employee_id`) | starred in wireframe; DB NOT NULL | **[ASSUMPTION]** "This field is required." (D-03-14) |
| Client satisfaction (1–5) | Required | "This field is required." |
| Client comments | Required | "This field is required." |
| Attachment ("Attach e-mail or notes, or browse file") | optional; one file | – (type/size limits: data-model open question 5, still open) |

### S9 — Check project status (bench)

Read-only status block (FRD 4.4 "Bench since, days, last project"): On bench since {date} · {n} days (`v_bench`), Last project · ended {date}, Previous checks (`decision` BENCH_CHECK rows). The wireframe also shows **Skills** and a **"Projects needing people"** table — no data source exists for either (D-03-11).

| Field | Rule | Message |
|---|---|---|
| Decision: Assign project ("Pick a project now") / Stay on bench ("Check again in {n} days") | Required | **[ASSUMPTION]** "This field is required." (no FRD row) |
| Notes | optional (FRD: "can add a note") | – (`decision.comment`) |

### S10 — Project review

Header: "{project} · ended {date} · {role} · {allocation}%". Form = the active `PROJECT`-type `appraisal_form` (data-model assumption 12: "project review ratings and texts are questions of a seeded `PROJECT` form"). Wireframe content:

| Field | Rule | Message |
|---|---|---|
| Delivery & quality (1–5) | Required | "Rate all items and add strengths before submitting." |
| Collaboration with client & team (1–5) | Required | same |
| Ownership & initiative (1–5) | Required | same |
| Strengths | Required | same |
| Areas to develop | optional (no star in wireframe; FRD Inputs lists it) | – |
| Outcome: Project delivered ("Next project round") / Performance concern ("Propose PIP") → `DELIVERED` / `PERFORMANCE_CONCERN` | DB: required on submit | **[OPEN]** no FRD message (D-03-24) |

Side panel: all client feedback for the project (FRD: "sees all client feedback for that project"; 4.6 reads `v_client_feedback_by_employee`) with "+ Add" (S8). Buttons: Cancel (left), Save draft + Submit review (right).

### S11 — Change project manager

| Field | Rule | Message |
|---|---|---|
| Project, Current manager | read-only | – |
| New project manager | Required; different from current | "Choose a new project manager." |
| Effective date | Required (starred) | **[OPEN]** no FRD message; FRD-01's Transfer manager uses "The effective date cannot be in the past." for the same field — reuse only if approved (D-03-9) |
| Move open project tasks (reviews, client feedback) to the new manager | checkbox, default ticked | – (`manager_change.move_open_tasks`) |
| Members whose line manager is the current PM — one tick each, ticked by default | FRD 4.7 preview + PRJ-01 "unless HR unticks them" | – **not drawn** in `m-change-pm` (D-03-9) |
| Reason | Required (starred; DB `check (scope = 'LINE' or reason is not null)`) | **[OPEN]** no FRD message (D-03-9) |

### S12 — My Projects (Employee)

Columns (wireframe): Project, Client, Role, Allocation, Period, Status — own `project_assignment` rows (FRD 4.8). Read-only, no actions.

## 6. API

Taken from FRD-03 §3 "Features, Endpoints & Database" 4.1–4.8 — every row below is one FRD row; no endpoint added (R-API-4). Paths get the project's `/api` prefix (R-API-1), same adaptation FRD-01/02 specs made. Error bodies per R-API-3; every update checks `version` → `409 STALE_VERSION` (R-DB-5). Response shapes are not given by the FRD and are left to Gate 1.

| # | Method & path | Role | Tables (FRD, exact) | Notes / status codes |
|---|---|---|---|---|
| PR01 | `GET /api/projects` | HR (all), Manager (own) | project, client, employee (PM), project_assignment | "All projects (HR) or own projects (Manager)". `200`; Employee/IT admin `403` |
| PR02 | `GET /api/projects/export` | HR, Manager | same | Excel (R-UI-2) |
| PR03 | `GET /api/projects/{id}` | HR, Manager (PM) | project, client, project_contact | Overview tab; also prefills S6. `404` / `403` not own |
| PR04 | `GET /api/projects/{id}/team` | HR, Manager (PM) | project_assignment, employee, v_employee_allocation, review (PROJECT) | "Team tab with allocation and review status" |
| PR05 | `GET /api/projects/{id}/client-feedback` | HR, Manager (PM) | client_feedback, document | Client feedback tab |
| PR06 | `POST /api/projects` | Manager | client (find or create), project, project_contact (POC, SECONDARY) | "Create project with client and contacts". `201` / `400` |
| PR07 | `PUT /api/projects/{id}` | Manager (PM) | project, client, project_contact | "Edit project". `200` / `400` / `409 STALE_VERSION` |
| PR08 | `PATCH /api/projects/{id}/status` | Manager (PM) | project, project_assignment (end_date, end_reason), review (PROJECT) · case file ProjectReview | "On hold, Completed, or Withdrawn with date and reason; Completed/Withdrawn ends assignments and creates one review per member (PRJ-04)". `200` / `400` "Add the withdrawal date and reason." |
| PR09 | `DELETE /api/projects/{id}` | Manager (PM) | project, project_assignment, review | "409 when assignments or reviews exist (PRJ-06)" → `409` with the PRJ-06 message; `204` otherwise |
| PR10 | `GET /api/employees/assignable` | Manager | employee, v_employee_allocation | "Active employees from any team with current allocation" |
| PR11 | `POST /api/projects/{id}/assignments` | Manager (PM) | project_assignment, email_outbox (PROJECT_ASSIGNED) · case file Bench.onBench | "Assign; warns above 100% and needs confirmOverAllocation=true (PRJ-07); notifies employee and line manager". `201` / `400` / `409` over-allocation warning when the flag is absent — **[ASSUMPTION]** status + code name (D-03-13) |
| PR12 | `PATCH /api/assignments/{id}` | Manager (PM) | project_assignment (end_reason ENDED_EARLY), review (PROJECT) | "Change or end early; ending early creates a project review" |
| PR13 | `GET /api/bench-checks/{taskId}` | Manager (task owner) | v_bench, project_assignment, project | "Bench since, days, last project" |
| PR14 | `POST /api/bench-checks/{taskId}` | Manager (task owner) | decision (BENCH_CHECK), setting (BENCH_CHECK) · case file Bench.lastDecision, Bench.checkIntervalDays | "Assign project or Stay on bench (next check after interval)". Completes an engine task → `202 {undoUntil}` (R-API-6) |
| PR15 | `POST /api/projects/{id}/client-feedback` | Manager (PM) | client_feedback, document (CLIENT_FEEDBACK_ATTACHMENT); read later via v_client_feedback_by_employee | "Record feedback for the team or one person, with attachment (multipart); date not in future". `201` / `400` |
| PR16 | `GET /api/project-reviews/{reviewId}` | Manager (reviewer) | review, review_answer, appraisal_form (PROJECT), form_section, form_question, project_assignment, v_client_feedback_by_employee | "Review form plus all client feedback for the project" |
| PR17 | `PUT /api/project-reviews/{reviewId}` | Manager (reviewer) | review, review_answer | "Save draft". `200` |
| PR18 | `POST /api/project-reviews/{reviewId}/submit` | Manager (reviewer; G-13) | review (outcome DELIVERED / PERFORMANCE_CONCERN) · case file ProjectReview.outcome | "Project delivered, or Performance concern (offers Start PIP)". `202 {undoUntil}` (R-API-6) / `400` "Rate all items and add strengths before submitting." |
| PR19 | `GET /api/projects/{id}/manager-change-preview` | HR | project_assignment, employee | "Members whose line manager is the current PM, ticked by default" |
| PR20 | `POST /api/projects/{id}/manager-changes` | HR | manager_change (PROJECT, plus one LINE row per ticked member via parent_id) | "Schedule new PM, date, reason, task move and line-manager moves". `201` / `400` "Choose a new project manager." |
| PR21 | `GET /api/me/projects` | Employee (every user, G-13 "own record") | project_assignment, project, client | "Own assignments" |

**Background jobs (FRD rows with method "—"), not endpoints:**

| # | Job | Tables (FRD) | Purpose |
|---|---|---|---|
| J1 | Nightly job | project (status) | "Set Overdue / Active from dates (PRJ-03)" |
| J2 | Scheduled job on effective date | project.project_manager_id, employee.manager_id, engine_task.owner_employee_id, manager_change (APPLIED) · case role ProjectManager | "Apply PM and line-manager changes; notify" |
| J3 | Bench-check scheduler | `Bench.*`, `setting` BENCH_CHECK | Not an FRD row — the FRD says "CaseFabric timers for the bench check" (§5 Integrations), which the E1 spike rules out for a configurable interval; see §8 / D-03-3. |

**Endpoints owned by FRD-01's spec but blocked on this module's data** (listed for completeness, not re-specified): FRD-01 W07 `GET /api/employees/{id}/projects` (employee record Projects tab) and W11 `GET /api/projects/{projectId}/members/{id}` (PRJ-08 member view, reads `project_assignment, review (PROJECT), v_client_feedback_by_employee`).

**Not in the FRD's endpoint list — needed by the screens, `[OPEN]` (D-03-12):** downloading a client-feedback attachment (S3/S4/S10 show it); the list of managers for S5/S6 "Project manager" and S11 "New project manager" (reuse an existing employee list endpoint vs. a new one).

## 7. Backend behaviour

All writes follow `01-overview.md` §4: authorize (role + ownership + G-13) → validate (exact messages) → one transaction (business rows + `audit_log` (G-06/R-API-5) + `email_outbox` (R-ARC-6)) → commit → engine calls with retry (R-ARC-5). Nothing below is built yet.

- **Create / edit (PR06/PR07).** Find `client` by `name` (unique) or create it; insert/update `project` + `project_contact` (POC required, SECONDARY optional). `project_code` generation — **[OPEN]** format not specified (wireframe shows "P-024"; D-03-25). Editing the client section of one project updates the shared `client` row — **[OPEN]** whether that is intended when other projects reference the same client (D-03-25). Initial status per D-03-7.
- **Status change (PR08).** `ON_HOLD`: no side effects ("assignments stay"). `COMPLETED`: set `closed_at`; for every assignment still open, set `end_date` (= today, **[ASSUMPTION]**) and `end_reason='PROJECT_COMPLETED'`; create one `review` (`review_type='PROJECT'`, `assignment_id`, `form_id` = active PROJECT form, `reviewer_id` = PM, `status='DRAFT'`, `due_date` per D-03-17) per such assignment (`ux_review_project` guarantees at most one per assignment). `WITHDRAWN`: as Completed but `end_date = withdrawal_date`, `end_reason='PROJECT_WITHDRAWN'`, and `withdrawal_date`/`withdrawal_reason` set (DB CHECK enforces both together). After commit, for each review: write `ProjectReview.reviewId` to that employee's case and raise "Project review" (§8). Each ended assignment may flip the employee onto the bench (see Bench below).
- **Delete (PR09).** `409` with the PRJ-06 message if any `project_assignment` or `review` references the project; else delete (`project_contact` cascades).
- **Assign (PR11).** Validate; compute the employee's total active allocation (`v_employee_allocation`) + new %; above 100 without `confirmOverAllocation=true` → warning response (D-03-13). Insert `project_assignment`; enqueue `PROJECT_ASSIGNED` for the employee; notify the line manager when they are not the PM (channel D-03-10). After commit: if `Bench.onBench` was true and the assignment is active today, write `Bench.onBench=false` (stops the bench check). If the PM is not the employee's line manager, add the PM to the case team as `ProjectManager` (`03-case-file.md` §3) — see D-03-26 on whether per-user case-team membership exists at all.
- **Change / end early (PR12).** Ending before the planned end: `end_reason='ENDED_EARLY'`, create the project review exactly as above.
- **Bench check (J3, PR13/PR14).** On `onBench` turning true, Spring computes the next check date from `setting.BENCH_CHECK` (snapshot into `Bench.checkIntervalDays`, ADM-02/R-ARC-7); the scheduler raises "Check project status" for the employee's line manager when due. PR14 inserts `decision` (`decision_type='BENCH_CHECK'`, `employee_id`, `engine_task_id`, `decision` ASSIGN_PROJECT/STAY_ON_BENCH, `comment` = notes, `decided_by`), completes the task with `{Bench:{lastDecision}}` after the undo window. `STAY_ON_BENCH` → next check after the interval (FRD §6: "a new "Check project status" task is created for 7 days later"). `ASSIGN_PROJECT` → opens S7; **[ASSUMPTION]** the bench check only stops when an assignment is actually saved (FRD: "If the employee was on the bench, the bench check stops") — D-03-11.
- **Client feedback (PR15).** Multipart; reject `received_date > today`; optional attachment → `document` (`doc_type='CLIENT_FEEDBACK_ATTACHMENT'`); insert `client_feedback` (`recorded_by` = PM). `PERSON` requires an `employee_id` assigned to this project (**[ASSUMPTION]**, implied by the wireframe's "Applies to" list of team members). Read side (PRJ-05) via `v_client_feedback_by_employee` (not yet created — §9).
- **Project review (PR16–PR18).** Draft writes `review_answer` rows only. Submit validates all PROJECT-form required questions + Strengths, sets `status='SUBMITTED'`, `outcome`, `submitted_at`; after the undo window completes "Project review" with `{ProjectReview:{outcome}}`. `PERFORMANCE_CONCERN` → the UI offers "Start PIP" (F07 — `pip.source_review_id` already references `review`); nothing is started automatically ("the manager may start a PIP").
- **Change PM (PR19/PR20, J2).** PR20 inserts one `manager_change` (`scope='PROJECT'`, `project_id`, `from_manager_id` = current PM, `to_manager_id`, `effective_date`, `reason`, `move_open_tasks`, `requested_by` = HR) and one child `scope='LINE'` row per ticked member (`parent_id` = the PROJECT row, `employee_id`). J2 (same shape as F03's `ManagerTransferScheduler`, which today handles LINE only) on the effective date: set `project.project_manager_id`; for each child LINE row apply the existing FRD-01 line-transfer logic (`employee.manager_id`); if `move_open_tasks`, move the project's open engine tasks (project reviews; **[OPEN]** which tasks count as "client feedback" tasks — none exists in this FRD, D-03-9) to the new PM (`engine_task.owner_employee_id`); mark rows `APPLIED`; notify both managers and every moved employee. `manager_change_task` (per-task picking, FRD-01) is **not** used by PROJECT changes — FRD-03 gives one checkbox, not a task list.
- **Nightly job (J1).** `PLANNED → ACTIVE` when `start_date <= today`; `→ OVERDUE` when `end_date < today` and status not `COMPLETED`/`WITHDRAWN` (PRJ-03). **[OPEN]** whether an `ON_HOLD` project past its end date becomes Overdue (D-03-18).

## 8. Case model needs

FRD-03 §5 "Case File Items (CaseFabric)" (and FRD-00 §5, same rows):

| Item | Multiplicity | Fields |
|---|---|---|
| Bench | 1 | onBench, checkIntervalDays, lastDecision |
| ProjectReview | 0..1 | projectReviewId, outcome |

`03-case-file.md` §2 already designs both (approved baseline):

| Item | Field | Type | Written by | Used by | DB source |
|---|---|---|---|---|---|
| **Bench** (1) | onBench | boolean | Spring (assignment start / end) | Bench stage entry and exit | v_bench |
| | checkIntervalDays | integer | Spring = BENCH_CHECK | repeating "Check project status" | setting |
| | lastDecision | ASSIGN_PROJECT / STAY_ON_BENCH | task output | STAY → next check | decision (BENCH_CHECK) |
| **ProjectReview** (0..1, overwritten per request) | reviewId | long | Spring, before raising "Project review requested" | task input | review (PROJECT) |
| | outcome | DELIVERED / PERFORMANCE_CONCERN | task output | shown as the PIP source | review.outcome |

**Naming, flagged by the FRD itself:** "Fix: Section 8 names the case-file field ProjectReview.reviewId; the case-file item list uses projectReviewId. Use one name." Recommend **`reviewId`** (what `03-case-file.md` already has; same choice the FRD-02 spec recommended for `Probation.reviewId`) — D-03-1. Also: `03-case-file.md` calls the task "Project review requested" while FRD-03 calls it **"Project review"** (S10 trigger, §2, §6 AC); R-CM-3 requires the model task name to match the spec, and it keys `setting` TASK_DUE — use "Project review" (D-03-1).

**Tasks, milestones, roles needed** (none exist in the deployed `employeecase.case`):
- Human task **"Check project status"** — performer `Manager` (FRD-08: "Bench check \| 7 days \| Manager \| No"); repeating while `Bench.onBench`; output `Bench.lastDecision`.
- Human task **"Project review"** — performer: the project manager. `03-case-file.md` §3 defines `ProjectManager` as "the PM of a project the employee is on, if not the line manager", so when PM = line manager the performer is `Manager`. **[OPEN]** a CMMN human task has one performer role (D-03-26).
- Case role `ProjectManager` — already declared in `employeecase.caseteam`.
- No milestone is named by FRD-03.

**Timer (E1 spike, `01-overview.md` §5 / `03-case-file.md` §1):** FRD-03 §5 says "Integrations: CaseFabric timers for the bench check". A native timer cannot read `Bench.checkIntervalDays`, and a literal duration would hard-code 7 days (violates G-08 / R-CM-4). Use the established fallback: Spring stores the next check date, a scheduler (same pattern as F01's `OverdueReminderScheduler`) raises the task via the engine REST API (D-03-3). No new case-file field is proposed — **[ASSUMPTION]** the next-due date lives in Spring (e.g. derived from the last `decision.decided_at` + `checkIntervalDays`), not in the case file; if the model needs it, add it to `03-case-file.md` as [NEEDS APPROVAL] first (R-CM-2).

**Concurrency, `[OPEN]` (D-03-2):** `ProjectReview` is 0..1 "overwritten per request", but PRJ-02 lets an employee be on several projects and PRJ-04 can raise two "Project review" tasks for the same employee case at once (two projects closing the same day). Either the item becomes 0..n or tasks must carry `reviewId` as task input only.

Extending `employeecase.case`/`.type` (two items, two human tasks, the bench repetition) is a `cmmn-modeler` prerequisite for F04, same as FRD-02 §8 for probation.

## 9. Data touched

**Already applied (do not re-create — `features/README.md` "schema already applied" note):** `client`, `project`, `project_assignment` (+ `ix_assignment_emp`), `client_feedback`, `review` (+ `ux_review_project`) — V13; `manager_change` (+ `ix_manager_change_due`), `manager_change_task` — V14; `appraisal_form`, `form_section`, `form_question` — V11; `document`, `employee`, `engine_task`, `notification`, `email_outbox`, `audit_log`, `setting`, `email_template` — V1; views `v_bench`, `v_employee_allocation` — V13. Columns match FRD-03 §5 exactly (checked line by line).

**Required by FRD-03 §5 but not applied — F04 Gate 1 must add (all already designed in `02-data-model.md` unless noted; R-DB-1):**
- `project_contact` (02-data-model §2.4) — PR03/PR06/PR07 can't run without it.
- `review_answer` (02-data-model §2.6) — PR16/PR17.
- `decision` (02-data-model §2.7) — PR14. Its CHECKs reference `development_plan` (applied), `pip` (applied) and `contract` (applied), so it can be created now.
- `v_client_feedback_by_employee` (02-data-model §2.9 lists it, no DDL written yet) — PR15 read side, PR16, FRD-01 W11.
- Indexes `ix_assignment_project (project_id)` and `ix_client_feedback_project (project_id)` — listed by FRD-03 §5 but **absent from `02-data-model.md`** as well as from V13; add to both (D-03-5).

**Applied but drifting from the FRD (D-03-4):** V13's `v_employee_allocation` returns `ON_BENCH` / `OVER_ALLOCATED` / `FULLY_ALLOCATED` (exactly 100) / `PARTLY_ALLOCATED` (else) — FRD-03 says Fully = 80–100%, Partly = 1–79%, plus **Ending** ("An assignment ends within 14 days"), and FRD-00/`02-data-model.md` name the values `FULLY / PARTLY / ON_BENCH / ENDING / OVER_ALLOCATED`. Fixing it changes the output of F09's (Done) Allocation report — needs approval.

**Schema notes from FRD-03's own cross-check (all "OK" except one):** status/withdrawal CHECK, allocation 1–100 with over-allocation derived (never stored), client-feedback rating/applies-to CHECK, PROJECT outcomes, BENCH_CHECK decisions, `manager_change` PROJECT + child LINE rows — all OK. The one "Fix" is the case-file naming (D-03-1). "Note: "Feedback date cannot be in the future" needs today and is enforced in Spring." Also: FRD-03 §5 lists `document.employee_id` as NOT NULL, but the applied V1 column is **nullable** — which is what TEAM-wide feedback attachments need (no single employee); keep the applied schema.

**Seed data needed:**
- A `PROJECT`-type `appraisal_form` (status ACTIVE) with the three rating questions + Strengths / Areas to develop text questions from `p-mg-proj-review` — FRD-08 §5 says "Seed data: … forms Standard, Leadership, Probation and Project", but V12 seeded only three (D-03-6).
- `TASK_DUE` rows for "Check project status" and "Project review" — none exist for any task; due-day values are not in the FRD (data-model open question 4; D-03-17).
- Already seeded, reuse: `setting.BENCH_CHECK` (V10), `email_template.PROJECT_ASSIGNED` (V9).

**Case file:** `Bench`, `ProjectReview` (§8) — documented in `03-case-file.md`, not yet in the deployed model.

## 10. E-mails & timers

| Trigger | Template | To |
|---|---|---|
| Employee assigned to a project (PR11) | `PROJECT_ASSIGNED` ("Project assigned — An employee is assigned to a project", FRD-08) — seeded V9 | the employee (`to_employee`) |
| Same — line manager when from another team | **[OPEN]** FRD says "their line manager is notified" / 4.3 "notifies employee and line manager", but the seeded template addresses the employee only; no other FRD-08 template fits. In-app `notification` only (FRD-01 D-W4 precedent) or flip `to_manager` on the template? (D-03-10) |
| "Check project status" / "Project review" task created | `TASK_ASSIGNED` (G-04 generic, seeded V6) | task owner |
| PM change applied (J2) | **[OPEN]** no FRD-08 template — FRD-01 D-W4 precedent: in-app notification only, moved tasks reuse "Task assigned" (D-03-9) | both managers + every employee whose line manager changes |

| Timer / rule | Default | Source | Guard |
|---|---|---|---|
| Bench check | 7 days, goes to Manager | `setting.BENCH_CHECK`, `can_disable=false` | always on |
| Overdue / Active status | nightly | J1 — not a setting | – |
| PM change | on `manager_change.effective_date` | J2 | – |
| "Ending" allocation window | 14 days | **hard-coded in the FRD text**, no `setting` row — **[OPEN]** vs G-08 ("All time periods … come from Settings") (D-03-4) | – |
| Overdue reminder | G-05 generic, `OVERDUE_REMINDER_DAYS` | already built (F01) | `enabled` |

## 11. Acceptance criteria

From FRD-03 §6 (verbatim scenarios):

**AC-03-1 Manager creates a project**
```
Given Mark Smit is on My Projects
When he selects + New Project, fills in all required fields and selects Create project
Then the project appears in My Projects
And no Delete button was shown on the new project form
```

**AC-03-2 HR cannot edit projects**
```
Given HR opens the Harbor Logistics Portal project
Then the only action available is "Change project manager"
And the Team and Client feedback tabs have no add buttons
```

**AC-03-3 Closing a project creates reviews**
```
Given Harbor Logistics Portal has 2 assigned team members
When the manager sets the project to Completed
Then the manager receives 2 "Project review" tasks
```

**AC-03-4 Client feedback appears in the review**
```
Given client feedback "Delivered ahead of peak season" applies to the whole team
When the manager opens Lotte Jansen's project review
Then the feedback is shown with contact, date, method and rating
```

**AC-03-5 Bench check**
```
Given Ruben Bakker has had no project since 06-10-2026
When the manager chooses Stay on bench
Then a new "Check project status" task is created for 7 days later
```

**AC-03-6 Future feedback date**
```
Given the manager is adding client feedback
When the date received is later than today
Then the message "The date cannot be in the future." is shown
```

**AC-03-7 Client withdraws midway**
```
Given Harbor Logistics Portal has 2 assigned team members
When the manager sets the status to Withdrawn with date 15-10-2026 and a reason
Then both assignments end on 15-10-2026
And the manager receives 2 "Project review" tasks
```

**AC-03-8 Project moves to a new manager**
```
Given Mark Smit is leaving and manages a project with Lotte Jansen (reports to Mark) and Priya Sharma (reports to Anouk Peters)
When HR changes the project manager to Anouk Peters from 01-11-2026
Then on 01-11-2026 Anouk Peters manages the project and both assignments
And Lotte Jansen's line manager becomes Anouk Peters
And Priya Sharma keeps her own line manager
```

Derived (no literal FRD scenario — **[ASSUMPTION]**, confirm before building):

**AC-03-9 (PRJ-06)**
```
Given a project has at least one assignment
When the manager selects Delete project
Then the message "This project has team members or reviews and cannot be deleted. Set the status to Completed or Withdrawn instead." is shown
And the project still exists
```

**AC-03-10 (PRJ-07)**
```
Given Priya Sharma's total allocation is 80%
When the manager assigns her at 40%
Then the message "Total allocation will be 120%. Do you want to continue?" is shown
And the assignment is saved only after the manager confirms
```

**AC-03-11 (PRJ-01 / G-02)**
```
Given HR is logged in
When HR calls the create-project, assign, client-feedback or project-review endpoints directly
Then the server answers 403
```

**AC-03-12 (Assign — bench stops, FRD §2)**
```
Given Ruben Bakker is on the bench
When the manager assigns him to a project starting today
Then he no longer appears in the Bench report
And no further "Check project status" task is created for him
```

**AC-03-13 (PRJ-03)**
```
Given an Active project whose end date was yesterday
When the nightly job runs
Then its status is Overdue
```

(FRD-01's "Manager sees a project member from another team" scenario depends on this module's data — tracked by FRD-01 D-W6/D-W7, not duplicated here.)

## 12. Decisions & open items

| ID | Topic | Status |
|---|---|---|
| D-03-1 | Case-file field `ProjectReview.reviewId` vs `projectReviewId` (FRD's own "Fix"), and task name "Project review" vs `03-case-file.md`'s "Project review requested" | **[OPEN]**, recommendation: `reviewId` + task name "Project review" (FRD wording, R-CM-3). Update `03-case-file.md` in the same change as the model. |
| D-03-2 | `ProjectReview` multiplicity 0..1 vs several concurrent project reviews for one employee (PRJ-02 + PRJ-04) | **[OPEN]** — needs a modelling decision before the CMMN work. |
| D-03-3 | FRD says "CaseFabric timers for the bench check" | Resolved by existing architecture (E1 spike): Spring scheduler fallback, interval from `setting.BENCH_CHECK`. Contradicts the FRD's wording, not its intent; recorded per R-SRC-1. |
| D-03-4 | `v_employee_allocation` (V13) drifts from FRD allocation statuses (80–100 / 1–79 / Ending); "Ending" uses a fixed 14 days with no setting (G-08) | **[OPEN] / [NEEDS APPROVAL]** — replacing the view changes F09's Allocation report output; adding a setting for 14 days would be new. |
| D-03-5 | Missing applied objects: `project_contact`, `review_answer`, `decision`, `v_client_feedback_by_employee`; FRD-only indexes `ix_assignment_project`, `ix_client_feedback_project` | Known gap, not a design question — F04 Gate 1 migration + `02-data-model.md` update (indexes are new to the data-model doc → **[NEEDS APPROVAL]**). |
| D-03-6 | No `PROJECT` appraisal form seeded (FRD-08 §5 says there is one) | **[OPEN]** — seed with the wireframe's three ratings + Strengths/Areas to develop (question wording from `p-mg-proj-review`); confirm. FRD-08's "Appraisal forms — Standard, Leadership and Probation forms" text doesn't mention editing the Project form either. |
| D-03-7 | Status on S5/S6: wireframe dropdown = Planned/Active/On hold/Completed (no Withdrawn, no withdrawal fields); FRD has a separate `PATCH …/status` for On hold/Completed/Withdrawn and a nightly job for Active/Overdue | **[OPEN]** — where does the manager change status (S6 dropdown vs. a status action on S4)? Can a project be *created* as Completed? Recommendation: S5 offers Planned/Active only; S4/S6 status changes go through PR08 with the withdrawal fields shown when Withdrawn. |
| D-03-8 | S5 "Project manager*" dropdown vs PRJ-01 ("Only the project manager creates … their projects") | **[OPEN]** — may a manager create a project for another manager? Recommendation: default to and allow only self. |
| D-03-9 | S11 gaps: per-member line-manager ticks not drawn in `m-change-pm`; no FRD messages for Effective date / Reason; "client feedback" open tasks don't exist; notification channel; do line-moved members' own manager tasks move (FRD-01 lets HR pick tasks, FRD-03 has none)? | **[OPEN]** — FRD wins on the tick list (draw it, see `wireframes.md`). Rest needs the user. |
| D-03-10 | Line-manager notification on assignment: template is employee-only | **[OPEN]** — in-app only (FRD-01 D-W4 precedent) vs. change the template's recipients. |
| D-03-11 | S9: "Skills" and "Projects needing people" have no data source (no skills column, no open-role table); semantics of choosing "Assign project" | **[OPEN]** — omit both panels until specified (R-SRC-2); [ASSUMPTION] bench check stops only when an assignment is saved. |
| D-03-12 | Endpoints the screens need but the FRD doesn't list (attachment download; manager pick-list) | **[OPEN]** — add to §6 before Gate 1 if approved (R-API-4). |
| D-03-13 | Over-allocation warning transport (FRD: "needs confirmOverAllocation=true") | **[ASSUMPTION]** `409` with a specific code and the FRD's message text; confirm code name. |
| D-03-14 | Client feedback: "Collected via" and "Applies to" are DB NOT NULL but not in the FRD's required list | **[ASSUMPTION]** both required, message "This field is required." |
| D-03-15 | (closed) `document.employee_id` NOT NULL per FRD-03 §5 vs nullable in V1 | Resolved: applied schema (nullable) stands; needed for TEAM feedback attachments. |
| D-03-16 | Can an employee see client feedback / project reviews about themselves? | **[OPEN]** — FRD-03 S12 shows assignments only; FRD-05 "My Reviews" may cover it (F06). |
| D-03-17 | Due dates of "Check project status" and "Project review" (no `TASK_DUE` rows; `review.due_date` NOT NULL) | **[OPEN]** (data-model open question 4). |
| D-03-18 | ON_HOLD project past its end date → Overdue? | **[OPEN]** — PRJ-03 says "until the manager marks it Completed", which reads as yes. |
| D-03-19 | Does an assignment ended by offboarding (`EMPLOYEE_LEAVING`) create a project review? Scope says "when a project closes or a person leaves it" | **[OPEN]** — F08 dependency. |
| D-03-20 | PM assigned to their own project — who reviews them (G-13)? | **[OPEN]** |
| D-03-21 | "People" count on lists: current vs all assignments | **[ASSUMPTION]** current; wireframe example suggests all. |
| D-03-22 | Secondary contact: one text box vs `project_contact` SECONDARY (name/role/email/phone) | **[OPEN]** — [ASSUMPTION] name only until confirmed. |
| D-03-23 | No FRD message for assignment end date before start date | **[OPEN]** |
| D-03-24 | No FRD message for a missing review outcome | **[OPEN]** |
| D-03-25 | `project_code` format; editing a shared `client` row from one project | **[OPEN]** — wireframe suggests "P-" + 3 digits. |
| D-03-26 | "Project review" performer when PM ≠ line manager (`ProjectManager` role); F03 found `RealCaseEngineClient` registers one service-account user holding all case roles, so per-user case-team swaps aren't implemented anywhere | **[OPEN]** — resolve with the CMMN work. |
| D-03-28 | No control drawn for changing / ending an assignment early (PR12) on the Team tab | **[OPEN]** — layout needs approval (see `wireframes.md` S4). |
| D-03-27 | FRD-03 "Open Items (TBC)" says none | Noted: FRD-00 Appendix A #3 (PRJ-08 view not drawn) concerns this module's data; FRD-01 D-W7 already handles it with a placeholder that F04 replaces. |

## 13. Definition of done

- [ ] Spec approved by the user; D-03-1, -2, -4, -6, -7, -8, -9, -10, -11, -12, -16, -17, -18, -19, -20, -22…-26 answered (CLAUDE.md §2.9.5 "ask when blocked").
- [ ] Flyway migration for the missing objects in §9 (`project_contact`, `review_answer`, `decision`, `v_client_feedback_by_employee`, two indexes, the `v_employee_allocation` fix if approved, PROJECT form seed, TASK_DUE rows) — building on V13/V14, never re-creating them; `02-data-model.md` updated in the same change (R-DB-1).
- [ ] `employeecase.case`/`.type` extended (Bench, ProjectReview, "Check project status", "Project review", bench repetition) and `03-case-file.md` updated (D-03-1/-2).
- [ ] PR01–PR21 and J1–J3 implemented with role + ownership checks, exact messages, audit, outbox, undo window.
- [ ] S1–S12 built; FRD-01 placeholders (Projects tab, PRJ-08 member page + Team-tab link, My Team "Assign", header "Assign project", dashboard bench / reviews-to-write cards) replaced with real data.
- [ ] Every acceptance criterion in §11 has an automated test.
