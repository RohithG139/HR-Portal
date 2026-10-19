# FRD-04 — Development plan and KPIs

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-04/FRD-04_Development_Plan_and_KPIs.docx` (primary, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), `FRD-00/FRD-00_Application_Overview.docx` (roles, permissions matrix, G-01…G-13, case-file inventory, Appendix A), `FRD-08/FRD-08_Reports_and_Settings.docx` (timers, KPI cycle, e-mail templates this flow reads). Cross-module reads: `FRD-06` (PIP pause/resume, skill-gap source), `FRD-05` (consumes KPI score and plan result), `FRD-01` (employee record "Development & reviews" tab, dashboards, My Team). The legacy v1.1 monolithic PDF is superseded — see `.claude/docs/source/README.md`. |
| Wireframes | `.claude/docs/source/FRD-04/WF-04_Development_Plan_and_KPIs.html` (v1.2, canonical) — pages `p-em-dev` (+ state variants `p-sb-dev` "no plan yet", `p-yt-dev` "paused"), `p-em-dev-define` (+ `p-sb-dev-define`), `p-mg-dev-approve`, `p-mg-dev-progress`, `p-mg-kpi-update`, `p-mg-dev-eval`, `p-mg-case-*` (Development & reviews tab; `p-mg-case-lotte` with data, `p-mg-case-jesse` empty), modals `m-add-kpi`, `m-add-activity`. All confirmed present by direct read. Text wireframes: `wireframes.md` (this folder). |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-04 wireframe HTML > legacy v1.1 (R-SRC-1). Conflicts are listed in §12. |
| Relationship to feature builds | **F05 "Development plan & KPIs"** in `.claude/docs/features/README.md` (depends on F01; status Not started). Nothing of FRD-04 is built: no backend code, no frontend screen, no case-model element. **Partly pre-applied schema:** `development_plan`, `checkin` and `pip` already exist (`V13__create_report_tables_and_views.sql`, F09 Gate 2), the `DEV_PLAN_PROGRESS_CHECK`/`KPI_CYCLE` settings (`V10`) and the `DEV_PLAN_SUBMITTED`/`DEV_PLAN_DECISION` e-mail templates (`V9`) are seeded — everything else (`goal`, `development_activity`, `checkin_goal`, **`decision`**, **`kpi`**, **`kpi_result`**, `v_kpi_result`, `v_kpi_score`) is unbuilt; see §9. This spec is written to double as F05's build spec (same pattern as FRD-01/F03 and FRD-08/F09). |

## 0. Status & sources

FRD-04 v1.2 describes one module — "Development goals and weighted KPIs that make each employee's growth measurable" — with two intertwined flows: the **development plan** (employee defines goals → manager approves → recurring progress checks → evaluation) and **KPIs** (manager sets weighted KPIs → enters results per period → system computes % met and a weighted score). Business objective, quoted: "Help every employee grow with clear goals, and give managers a measurable way to track results through KPIs."

The FRD is precise: 9 screens with routes and wireframe ids (its §3), a method/endpoint/table list for 6 features (§3 "Features, Endpoints & Database", numbered 4.1–4.6), a validation table with exact messages and six invariants DEV-01…DEV-06 (§4), the full column schema of every table it touches with a cross-check against `V1__init.sql` (§5), six Given/When/Then scenarios (§6) and one open item (Appendix A #9). **Note on numbering:** FRD-04 puts acceptance criteria in its §6 (not §7 as in FRD-02); citations below use FRD-04's own numbering.

What was verified against the repository for this spec (2026-10-19):
- **Schema:** `hr-backend/src/main/resources/db/migration/` V1–V14. Every `create table`/`create view` across all migrations was listed: `development_plan`, `checkin`, `pip` exist (V13); `goal`, `development_activity`, `checkin_goal`, `decision`, `kpi`, `kpi_result`, `v_kpi_result`, `v_kpi_score` do **not**. (`features/README.md` lists `goal`/`development_activity`/`checkin_goal`/`review_answer` as unbuilt but omits `decision`, `kpi` and `kpi_result` — they are unbuilt too.)
- **Backend code:** no controller, service, entity or repository for this module exists in `hr-backend/src/main/java`. Only placeholders refer to it: `EmployeeDashboardDto` / `TeamService` / `TeamMemberDto` comments ("deferred fields … added by F04/F05/F06").
- **Frontend:** `hr-frontend/src/features/employee/tabs/DevelopmentTab.tsx` is FRD-01's D-W1 "Not available yet" placeholder; no "My Development" route or menu item exists.
- **Case model:** the deployed `employeecase.case` is "F01 (Create employee -> Onboarding) scope only" (its own description) — no `DevelopmentPlan` item, task or milestone. The legacy `emp.case` has a "Development plan cycle" stage (Define goals and plan / Approve plan / Revise plan / Review progress / Evaluate plan, milestones "Plan approved"/"Plan performed") with a hard-coded `P14D` progress timer — read-only reference per R-CM-1, and its fixed timer violates R-CM-4.
- **"Create development plan" after onboarding:** `F01-onboarding/spec.md` §1/§7 (A19) says completing onboarding raises "a `Create development plan` row (F04's concern — F01 only writes the fact, per R-ARC-1)". **The built code does not do this:** `task/UndoWindowScheduler.java`'s post-completion block for "Onboarding checklist" sets `stage='ACTIVE'`, marks the onboarding `COMPLETED` and queues `ONBOARDING_COMPLETED` e-mails — nothing else — and the deployed model has no such task. F05 must add it (D-04-4).

Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` allow (R-SRC-2). Anything the sources do not settle is marked **[OPEN]** or **[ASSUMPTION]** and collected in §12.

## 1. Scope

**In scope (FRD-04's own "IN-SCOPE" list, quoted)**
- "Development plan: employee goals, manager approval, progress checks at a configurable interval (default 14 days), evaluation"
- "KPIs set by the manager and results entered per period"
- "Automatic % met and weighted KPI score"

Concretely: My Development (S1), define goals (S2), approve plan (S3), Add KPI pop-up (S4), progress check (S5), Add activity pop-up (S6), KPI results (S7), evaluation (S8), and the manager-side content of the employee record's Development & reviews tab (S9); the "Create development plan" / "Approve development plan" / "Progress check" / "Evaluate development plan" tasks; milestones "Plan approved" and "Plan closed"; views `v_kpi_result` and `v_kpi_score`.

**Out of scope**
- FRD-04's own list: "none specific to this module in v1."
- **PIP itself (FRD-06 / F07).** "Skill gap" only *informs HR and offers* Start PIP; drafting the PIP is FRD-06 Screen 1. Pausing and resuming the plan (DEV-05) is *written* by FRD-06's endpoints (`POST /pips/{id}/hr-decision` → `development_plan (PAUSED)`; `POST /checkins/{id}/hr-signoff` → `development_plan (ACTIVE)`) — this module only *honours* the `PAUSED` status.
- **Appraisal (FRD-05 / F06)** reads `v_kpi_score`, `v_kpi_result`, `development_plan` as evidence — not built here.
- **Settings screens (FRD-08 / F09, Done):** the progress-check interval and KPI cycle are edited there; this module only reads them.
- **The read-only HR view** of KPIs and plans is the HR employee record's Development & reviews tab — FRD-01's W8 contract (`GET /employees/{code}/development`), not an FRD-04 endpoint (see §2, §6 note).
- **The "Reviews" section** of the Development & reviews tab (project reviews, client feedback, appraisals) — FRD-03/FRD-05 data, not FRD-04.

## 2. Roles & permissions

FRD-00 §2 row "Set KPIs, approve and check development plans": HR **No** · Manager **Own team** · Employee **Own goals only** · IT admin **No**. FRD-04's access gate (§3), quoted: "Screens 1–2 are for the employee on their own plan and goals only. Screens 3–9 are for the employee's current manager only (DEV-01). HR sees KPIs and plans read-only on the HR employee record and does not approve them. While the employee is on a PIP the plan shows as paused and no progress-check tasks are created (DEV-05)." Plus FRD-04 §1 constraint: "HR does not approve KPIs."

| Capability | HR | Manager (current manager of the employee) | Employee | IT admin |
|---|---|---|---|---|
| Start a plan, add/edit/remove own goals, submit for approval (S1, S2) | No | **[OPEN] D-04-5** — FRD §2 says "The employee (or the manager for them) starts a plan", but no manager screen or endpoint exists for it | Own plan only | No |
| Update progress of own goals | No | Confirms it in the progress check (S5) | Own goals, "at any time" | No |
| Approve plan / request changes (S3) | No ("does not approve them") | Yes (DEV-01) | No | No |
| Add, edit, retire KPIs (S4, S9) | No | Yes (DEV-01) | No | No |
| Enter / correct KPI results (S7) | No | Yes (DEV-01) | No | No |
| Progress check, add activity (S5, S6) | No | Yes (DEV-01) | No | No |
| Evaluate plan (S8) | No | Yes (DEV-01) | No | No |
| View KPIs, latest % met, weighted score | Read-only, HR employee record (FRD-01 W8) | Own team | Own (My Development) | No |
| View full KPI result history | Read-only (FRD-01 W8) | Own team (S7 "Previous results") | Yes — FRD §1: "Employees can see their full KPI history, not only the latest result." (no screen draws it — D-04-21) | No |
| Receive "Skill gap" information | Yes (in-app notification) | — | No | No |

**DEV-01 "current manager"** = `employee.manager_id` at request time; after an HR Transfer manager (FRD-01, built) the new manager inherits the right, and open development tasks move per `manager_change_task`. **G-13:** a manager acts on their *own* plan only through the Employee functions (S1/S2); their plan is approved, checked and evaluated by *their* manager. Server-side checks on every endpoint (G-02, R-API-2); hiding buttons is never the only protection (R-UI-7).

## 3. Business rules

Copied from FRD-04 §4 "Hard Business Invariants" by ID (R-SRC-3 — wording kept exact):

| ID | Rule | Priority |
|---|---|---|
| DEV-01 | Only the employee's current manager can add KPIs, enter results, approve plans and do progress checks. | MUST |
| DEV-02 | The weights of an employee's active KPIs must add up to 100% before the manager approves the plan or enters results. A single KPI can be saved while the total is not yet 100%. | MUST |
| DEV-03 | % met is calculated by the system and cannot be typed in. | MUST |
| DEV-04 | KPI results are kept per period; earlier results cannot be overwritten, only corrected with a new entry. | SHOULD |
| DEV-05 | An active plan is paused while the employee is on a PIP and resumes when the PIP is passed. | MUST |
| DEV-06 | "Request changes" requires a comment. | MUST |

"Global rules G-01 … G-13 (FRD-00, section 4) also apply to this module." Most relevant: G-02 (server-side access), G-03 (task owner, due date, undo window), G-04 (task notification + e-mail), G-05 (due-soon/overdue colours, overdue reminder), G-06 (history), G-08 (periods from Settings, never fixed), G-11 (save toast; Cancel never saves), G-12 (dd-mm-yyyy), G-13 (never approve anything about yourself). FRD-06 PIP-07, quoted: "During a PIP the development plan is paused (DEV-05)."

**% met and weighted score (FRD-04 §2 "How % met is calculated", quoted):**

| Direction | Formula | Example |
|---|---|---|
| Higher is better | Actual ÷ target × 100, maximum 100% | Target 85%, actual 82% → 96% |
| Lower is better | 100% if actual is at or below target; otherwise target ÷ actual × 100 | Target 1.0 day, actual 1.3 days → 77% |
| Weighted KPI score | Sum of (% met × weight) ÷ sum of weights | 96%×30 + 100%×40 + 100%×30 → 99% |

Weighted score scope (FRD-00/FRD-04 §5 view list): "Weighted KPI score per employee (active KPIs with a result)". % met source: "Latest non-superseded result per KPI + % met (DEV-03)". Display rounding: **[ASSUMPTION] D-04-28** — whole percent, half-up (82/85 = 96.47 → 96 %; 1.0/1.3 = 76.9 → 77 %, matching both FRD examples).

**Process rules, from FRD-04 §2 (quoted, not paraphrased):**
1. "After onboarding (and when a plan ends with "Further needs") the employee gets a "Create development plan" task. The employee (or the manager for them) starts a plan and adds goals. Each goal has a type: Training / learning, Project / assignment or Coaching / mentoring."
2. "The employee selects "Submit for approval". The manager gets an "Approve development plan" task."
3. "The manager reviews goals and any KPIs, adds a comment and chooses: Approve plan — the plan becomes active and the employee is notified. Request changes — the plan goes back to the employee (comment required)."
4. "The employee can update the progress of their own goals at any time. At every progress-check interval (default 14 days, FRD-08) the manager gets a "Progress check" task, confirms the progress and status per goal, adds notes and chooses: Continue — the next check is created after the same interval. Plan performed — all goals are done; an "Evaluate development plan" task is created. Skill gap — HR is informed; the manager may start a PIP (FRD-06)."
5. "In the evaluation the manager records a result per goal and chooses Plan closed or Further needs (a new plan starts)."
6. KPIs: "The manager selects "+ Add KPI" on the employee's Development tab and sets name, target value, unit, direction (higher or lower is better), measurement frequency (default: the KPI cycle in FRD-08), weight, due date and how it is measured. KPIs are saved one at a time; while the weights do not add up to 100% the Development tab shows a warning."
7. "Each period the manager selects "Enter results", picks the period and enters the actual value for each KPI."
8. "The system calculates % met and the weighted KPI score immediately and keeps the history of results."
9. "The employee sees KPIs, latest % met and the weighted score in My Development."

**Pre-conditions (FRD-04 §2, quoted):** "The employee has completed onboarding." "The employee is not on an active PIP (the plan is paused during a PIP)."

**Post-conditions (FRD-04 §2, quoted):** "Approved plan, goal progress and evaluation on the employee record." "KPI results with % met and weighted KPI score (v_kpi_result, v_kpi_score), used in FRD-05." "Milestones "Plan approved" and "Plan closed"."

**Endpoint-level rules (FRD-04 §3 Features table):** one open plan per employee (`ux_plan_open`); submit needs "at least one goal"; KPI "Edit (target locked once results exist)"; results are "append-only", corrections are "a new entry (DEV-04)".

**Plan status lifecycle** (values from `development_plan.status`; transitions from the FRD flow above and the FRD §3 endpoint table):

```
DRAFT ──submit──▶ SUBMITTED ──Approve──▶ ACTIVE ──Plan performed──▶ PERFORMED ──evaluation──▶ CLOSED
  ▲                  │                    │  ▲
  └─ (edit goals) ◀── CHANGES_REQUESTED ◀─┘  │ (Request changes from SUBMITTED)
                                          PAUSED  (set/cleared by FRD-06: PIP approved → PAUSED; PIP passed → ACTIVE)
CANCELLED: allowed by the schema, never set by any FRD-04 flow — [OPEN] D-04-26
```

## 4. Screens

Numbering follows FRD-04 §3 "Screens" (Screen 1…9) one-to-one.

| # | Screen | Who | Route | Wireframe page / element | FRD |
|---|---|---|---|---|---|
| S1 | My Development | Employee (own) | `/me/development` · menu "My Development" | `p-em-dev` (plan + KPIs); state variants `p-sb-dev` (no plan yet → "Start a plan"), `p-yt-dev` (paused during PIP) | FRD-04 §3 Screen 1 |
| S2 | Development plan – define goals | Employee (own) | `/me/development/plan` · "Edit plan" / "Start a plan"; task "Create development plan" | `p-em-dev-define` (3 goals), `p-sb-dev-define` (1 goal) | Screen 2 |
| S3 | Approve development plan | Manager (DEV-01) | `/team/:code/development/approve` · task "Approve development plan" | `p-mg-dev-approve` | Screen 3 |
| S4 | Add KPI (pop-up) | Manager | Modal on S3 and S9 · "+ Add KPI" | `m-add-kpi` | Screen 4 |
| S5 | Progress check | Manager | `/team/:code/development/progress-check` · task "Progress check"; employee record | `p-mg-dev-progress` | Screen 5 |
| S6 | Add activity (pop-up) | Manager | Modal on S5 · "+ Add activity" | `m-add-activity` | Screen 6 |
| S7 | KPI results | Manager | `/team/:code/kpis/results` · "Enter results" on S9; progress check | `p-mg-kpi-update` | Screen 7 |
| S8 | Evaluate development plan | Manager | `/team/:code/development/evaluate` · task "Evaluate development plan" | `p-mg-dev-eval` | Screen 8 |
| S9 | Employee record – Development & reviews (manager actions) | Manager | `/team/:code?tab=development` · My Team row | `p-mg-case-*` tab "Development & reviews" (`p-mg-case-lotte` filled, `p-mg-case-jesse` empty) | Screen 9; FRD-01 W8 |

S9 replaces FRD-01's D-W1 placeholder tab (`DevelopmentTab.tsx`) for the KPI and goal sections; the "Reviews" section remains FRD-03/FRD-05's (F04/F06). The HR employee record's same tab (`/employees/:code?tab=development`, `p-hr-case-*`) is read-only and is FRD-01's W8 contract — this spec only supplies its data.

## 5. Fields & validation

FRD-04 §4 "Validation Table" (exact messages, R-SRC-3):

| Field Name | Rules | Exact Error Message |
|---|---|---|
| Goal, type, target date, how | Required | "This field is required." |
| Target date | After today | "The target date must be in the future." |
| Plan | At least one goal to submit | "Add at least one goal before submitting." |
| Request changes comment | Required | "Add a comment for the employee." |
| KPI name, target value, unit, direction, frequency, weight, due date | Required | "This field is required." |
| Target value | Number greater than 0 | "Enter a number greater than 0." |
| KPI weights | Total 100% when approving the plan or entering results | "KPI weights must add up to 100%." |
| Actual value | Number; 0 or more | "Enter a number." |
| Evaluation result per goal | Required | "Choose a result for every goal." |

Fields without an FRD message are marked; **[ASSUMPTION] D-04-15**: where the wireframe marks a field required (`*`) but the FRD gives no rule, the module's own generic "This field is required." is used — confirm before building.

### S1 — My Development (Employee)

Read-only page (`GET /api/me/development`, DV01). Content per `p-em-dev`:

| Section | Content | Source |
|---|---|---|
| Header | "Plan {period} · {status}", button "Edit plan" | `development_plan.period_label` (D-04-19), `status` |
| KPIs from your manager | KPI · Target · Weight · Measured · % met (latest); "Weighted KPI score {n}%" | `kpi` (status `ACTIVE`), `v_kpi_result`, `v_kpi_score` |
| My goals | stepper "1 · Plan → 2 · Perform plan → 3 · Evaluate"; Goal · Type · Target date · Status | `goal`, plan status |
| Previous plans | Plan · Goals · KPIs · Result | `development_plan` (status `CLOSED`, chain via `previous_plan_id`) — "KPIs" count has no source (D-04-18) |
| Last manager comment | listed in the FRD's DV01 purpose ("last manager comment") — not drawn in `p-em-dev` | `decision (PLAN_APPROVAL).comment` |

States: no open plan → `p-sb-dev` "You do not have a development plan yet" + "Start a plan"; plan `PAUSED` → `p-yt-dev` "Your development plan is paused while the improvement plan is active" (DEV-05). "Target" text is composed from direction + target + unit + frequency ("≥ 85% per quarter"); nothing new is stored.

### S2 — Define goals (Employee)

Repeating goal block ("Goal n ×", "+ Add goal"); buttons Cancel · Save draft · Submit for approval.

| Field | Column | Type | Rule | Message |
|---|---|---|---|---|
| Goal* | `goal.title` | text ≤ 200 | Required | "This field is required." |
| Type* | `goal.category` | Training / learning → `TRAINING`, Project / assignment → `PROJECT`, Coaching / mentoring → `COACHING` | Required | "This field is required." |
| Target date* | `goal.target_date` | date dd-mm-yyyy (G-12) | Required; after today | "This field is required." / "The target date must be in the future." |
| How will you get there?* | `goal.how_measured` | text | Required | "This field is required." |
| Support needed from manager | `goal.support` | text | Optional | — |
| (order) | `goal.sort_order` | int | position in the list | — |
| Submit for approval | — | — | at least one goal | "Add at least one goal before submitting." |

Remove a goal: "×" (R-UI-1). Save draft keeps the plan `DRAFT` (or `CHANGES_REQUESTED`). Editability after submission: **[OPEN] D-04-6**.

### S3 — Approve development plan (Manager)

| Element | Content / field | Rule | Message |
|---|---|---|---|
| KPIs set by you | KPI · Target · Weight · Measured; "+ Add KPI" (opens S4) | — | — |
| Employee's goals | Goal · Type · Target date · Support requested (read-only) | — | — |
| Comments to employee | `decision.comment` | Required when Request changes (DEV-06) | "Add a comment for the employee." |
| Decision | Approve plan ("Plan becomes active") → `APPROVE` / Request changes ("Back to employee") → `REQUEST_CHANGES` | Required — **[ASSUMPTION]** no FRD message; "This field is required." | — |
| Approve guard | active KPI weights total 100 % (DEV-02) | on Approve | "KPI weights must add up to 100%." |

Approving with **no** KPIs at all: **[OPEN] D-04-8**.

### S4 — Add KPI (pop-up, Manager)

| Field | Column | Type / options | Rule | Message |
|---|---|---|---|---|
| KPI* | `kpi.name` | text ≤ 150 | Required | "This field is required." |
| Target value* | `kpi.target_value` | number (12,3) | Required; > 0 | "This field is required." / "Enter a number greater than 0." |
| Unit* | `kpi.unit` | WF options: % · Score / 5 · Days · Hours · Count · € (column is free `varchar(20)`) | Required | "This field is required." |
| Direction* | `kpi.direction` | Higher is better → `HIGHER` / Lower is better → `LOWER` | Required | "This field is required." |
| Measured* (frequency) | `kpi.frequency` | **Quarterly / Half-yearly / Yearly** (FRD + DB CHECK); default = `setting.KPI_CYCLE.value_text`. The WF's Monthly / Per project are dropped (Appendix A #9, D-04-1) | Required | "This field is required." |
| Weight %* | `kpi.weight_pct` | integer; DB CHECK 1–100 | Required | "This field is required." (out-of-range message: **[OPEN] D-04-24**) |
| Due date* | `kpi.due_date` | date dd-mm-yyyy | Required | "This field is required." |
| How it is measured | `kpi.measurement_note` | text | Optional | — |

Saving one KPI while the total ≠ 100 % is allowed (DEV-02 second sentence). Edit (DV12) uses the same fields; `target_value` is locked once results exist (D-04-24 for the message).

### S5 — Progress check (Manager)

| Element | Field | Rule | Message |
|---|---|---|---|
| KPIs (Target · Current · Progress · Status) | **[OPEN] D-04-13** — WF draws editable "Current" and a per-KPI status picker; neither has a column or FRD rule. Proposed: read-only display of latest `v_kpi_result`, with "Enter KPI results" → S7 | — | — |
| Goals: Progress | `checkin_goal.progress_pct` → copied to `goal.progress_pct` | 0–100 (DB CHECK) | **[OPEN]** no FRD message |
| Goals: Status | `checkin_goal.progress_status` (On track `ON_TRACK` · At risk `AT_RISK` · Behind `BEHIND` · Done `ACHIEVED`) | — | — |
| Goals: note | `checkin_goal.note` | optional — FRD "adds notes"; not drawn per goal | — |
| "+ Add activity" | opens S6 | — | — |
| Check-in summary* | `checkin.notes` | WF required; FRD silent — [ASSUMPTION] D-04-15 | "This field is required." |
| Plan status | Continue ("Next check in {n} days") → `CONTINUE` · Plan performed ("Move to evaluation") → `PERFORMED` · Skill gap ("Flag to HR") → `SKILL_GAP` | Required — [ASSUMPTION] D-04-15 | "This field is required." |

### S6 — Add activity (pop-up, Manager)

| Field | Column | Rule | Message |
|---|---|---|---|
| Type* | `development_activity.activity_type` (TRAINING / PROJECT / COACHING) | Required [ASSUMPTION D-04-15] | "This field is required." |
| Activity* | `development_activity.name` (≤ 150) | Required [ASSUMPTION] | same |
| Date* | `development_activity.activity_date` | Required [ASSUMPTION] | same |
| Linked goal* | path `{goalId}` (goals of this plan) | Required [ASSUMPTION] | same |

### S7 — KPI results (Manager)

Header text (WF): "Enter the actual result for each KPI and period · % met is calculated automatically".

| Element | Field | Rule | Message |
|---|---|---|---|
| Per KPI: Target, Direction, Weight | read-only from `kpi` | — | — |
| Per KPI: Period | `kpi_result.period_start` / `period_end` | **[ASSUMPTION] D-04-12** periods offered per the KPI's frequency (quarter / half-year / year) | — |
| Per KPI: Actual | `kpi_result.actual_value` | number ≥ 0 | "Enter a number." |
| Per KPI: % met | computed live (DEV-03), never an input | — | — |
| Weighted KPI score | computed (`v_kpi_score` after save) | — | — |
| Comment for employee | `kpi_result.comment` (one comment drawn; column is per result — [ASSUMPTION] D-04-12: copied to each result row saved in this submission) | optional | — |
| Save guard | active KPI weights total 100 % (DEV-02) | on Save results | "KPI weights must add up to 100%." |
| Previous results | Period · KPI · Actual · % met · Entered by | read-only (DV16) | — |

Whether every active KPI needs an actual in one save: **[OPEN] D-04-29**. Second entry for an already-recorded period → correction path only (DEV-04; DV15).

### S8 — Evaluate development plan (Manager)

| Element | Field | Rule | Message |
|---|---|---|---|
| Per goal: Result | `goal.result` (Met `MET` · Partly met `PARTLY_MET` · Not met `NOT_MET`) | Required for every goal | "Choose a result for every goal." |
| Per goal: Evidence | **[OPEN] D-04-14** — drawn in WF, no column or FRD input | — | — |
| Evaluation* | `development_plan.evaluation_text` | WF required; FRD Inputs list "evaluation text"; [ASSUMPTION] D-04-15 | "This field is required." |
| Outcome | Plan closed ("No further needs") → `PLAN_CLOSED` · Further needs ("Start a new plan") → `FURTHER_NEEDS` | Required [ASSUMPTION] | "This field is required." |

### S9 — Development & reviews tab (Manager)

| Section | Content | Actions |
|---|---|---|
| KPIs set by manager | KPI · Target · Weight · Measured · % met (latest); weight-total warning when active weights ≠ 100 % (FRD §2) — text: **[ASSUMPTION] D-04-27** reuse "KPI weights must add up to 100%."; empty state "No KPIs set" | "Enter results" (→ S7; hidden when no KPIs, per `p-mg-case-jesse`), "+ Add KPI" (→ S4); retire: **[OPEN] D-04-22** |
| Development goals | Goal · Type · Target date · Progress; empty state "No development plan" | "Progress check" (→ S5) |
| Overview card "Development" | Status + "{n} goals · {m} KPIs" (`p-mg-case-lotte` Overview) | — |

## 6. API

Endpoints are taken one-for-one from FRD-04 §3 "Features, Endpoints & Database" (4.1–4.6). Path spelling adds this project's `/api` prefix (R-API-1), same adaptation FRD-01/FRD-02/FRD-08 specs make. **Path parameter for the employee:** the FRD writes `/employees/{code}`; the built `/api/employees/...` endpoints (F01, F03) all take the numeric `{id}` and FRD-01's W08 contract already adapted `/employees/{code}/development` the same way — kept consistent here (D-04-23). Errors follow R-API-3; every state change writes `audit_log` (R-API-5); updates check `version` → `409 STALE_VERSION` (R-DB-5).

| # | Method & path | Role | Notes (FRD purpose quoted) · tables | Responses |
|---|---|---|---|---|
| **4.1 Development plan and goals (S1, S2)** | | | | |
| DV01 | `GET /api/me/development` | Employee (self) | "Plan, goals, KPIs, latest % met, weighted score, last manager comment" — `development_plan, goal, kpi, v_kpi_result, v_kpi_score, decision (PLAN_APPROVAL)` | `200` (no open plan → plan `null`, renders `p-sb-dev`) |
| DV02 | `POST /api/me/development/plans` | Employee (self) | "Start a plan (one open plan, ux_plan_open)" — `development_plan (DRAFT, previous_plan_id)` | `201` / `409` open plan exists (`ux_plan_open`; message [OPEN] D-04-24) / `409` pre-condition not met (onboarding not complete or on PIP — [ASSUMPTION] D-04-30) |
| DV03 | `POST /api/development-plans/{id}/goals` | Employee (own plan) | "Add … goals" — `goal (plan_id)` | `201` / `400` §5 S2 messages / `403` not own plan / `409` plan not editable (D-04-6) |
| DV04 | `PUT /api/development-plans/{id}/goals/{goalId}` | Employee (own plan) | "edit … goals" — `goal` | `200` / `400` / `403` / `409` |
| DV05 | `DELETE /api/development-plans/{id}/goals/{goalId}` | Employee (own plan) | "remove goals" — `goal` | `204` / `403` / `409` |
| DV06 | `PATCH /api/goals/{id}/progress` | Employee (own goal) | "Employee updates own progress at any time" — `goal (progress_pct, progress_status)` | `200` / `400` (0–100) / `403` |
| DV07 | `POST /api/development-plans/{id}/submit` | Employee (own plan) | "Submit for approval; at least one goal" — `development_plan (SUBMITTED, submitted_at), email_outbox (DEV_PLAN_SUBMITTED)` · case file `DevelopmentPlan.status` | `202 {undoUntil}` (completes "Create development plan" / revise task, R-API-6) / `400` "Add at least one goal before submitting." / `409` not DRAFT/CHANGES_REQUESTED |
| **4.2 Plan approval (S3)** | | | | |
| DV08 | `GET /api/development-plans/{id}` | Manager (DEV-01); Employee (own) [ASSUMPTION — also serves S2 load and S8] | "Goals and KPIs for review" — `development_plan, goal, kpi` | `200` / `403` / `404` |
| DV09 | `POST /api/development-plans/{id}/decision` | Manager (DEV-01, G-13) | "Approve (weights must total 100%, DEV-02) or Request changes (comment required, DEV-06); milestone "Plan approved"" — `decision (PLAN_APPROVAL), development_plan (ACTIVE / CHANGES_REQUESTED), email_outbox (DEV_PLAN_DECISION)` · case file `DevelopmentPlan.decision` | `202 {undoUntil}` / `400` "KPI weights must add up to 100%." · "Add a comment for the employee." / `403` / `409` not SUBMITTED |
| **4.3 KPIs (S4, S9)** | | | | |
| DV10 | `GET /api/employees/{id}/kpis` | Manager (DEV-01) | "KPIs with latest % met and weighted score; weight-total warning" — `kpi, v_kpi_result, v_kpi_score` | `200 {kpis[], weightedScore, weightTotal}` / `403` |
| DV11 | `POST /api/employees/{id}/kpis` | Manager (DEV-01) | "Add one KPI; frequency defaults to KPI cycle" — `kpi, setting (KPI_CYCLE)` | `201` / `400` §5 S4 messages / `403` |
| DV12 | `PUT /api/kpis/{id}` | Manager (DEV-01) | "Edit (target locked once results exist)" — `kpi` | `200` / `400` / `403` / `409` target changed while results exist (message [OPEN] D-04-24) |
| DV13 | `POST /api/kpis/{id}/retire` | Manager (DEV-01) | "Retire a KPI" — `kpi (status RETIRED)` | `200` / `403` / `409` already retired |
| **4.4 KPI results (S7)** | | | | |
| DV14 | `POST /api/employees/{id}/kpi-results` | Manager (DEV-01) | "Enter actuals for a period; weights must total 100% (DEV-02)" — `kpi_result (append-only)` | `201` / `400` "Enter a number." · "KPI weights must add up to 100%." / `403` / `409` period already has a result (`ux_kpi_result_original`; message [OPEN] D-04-24) |
| DV15 | `POST /api/kpi-results/{id}/corrections` | Manager (DEV-01) | "Correct a result with a new entry (DEV-04)" — `kpi_result (corrects_result_id)` | `201` / `400` / `403` / `409` already corrected (`ux_kpi_result_corrects`) |
| DV16 | `GET /api/kpis/{id}/results` | Manager (DEV-01); Employee (own KPI) — FRD §1 "full KPI history" [ASSUMPTION on role] | "Full result history" — `kpi_result, v_kpi_result` | `200` / `403` |
| **4.5 Progress check (S5, S6)** | | | | |
| DV17 | `GET /api/development-plans/{id}/checkins/current` | Manager (DEV-01) | "Goals with current progress" — `checkin (PLAN_PROGRESS), checkin_goal, goal` | `200` / `403` / `404` no open check |
| DV18 | `POST /api/checkins/{id}/submit` | Manager (DEV-01) | "Confirm progress per goal; Continue, Plan performed or Skill gap (informs HR, offers Start PIP)" — `checkin (notes, outcome), checkin_goal, goal, notification` · case file `DevelopmentPlan.checkOutcome, progressCheckDays` | `202 {undoUntil}` / `400` / `403` / `409` already done or plan PAUSED |
| DV19 | `POST /api/goals/{id}/activities` | Manager (DEV-01) | "Add training, project or coaching activity" — `development_activity` | `201` / `400` / `403` / `409` goal is a PIP goal (`goal.pip_id` set — "development goals only: app check", `02-data-model.md`) |
| **4.6 Evaluation (S8)** | | | | |
| DV20 | `POST /api/development-plans/{id}/evaluation` | Manager (DEV-01, G-13) | "Result per goal; Plan closed (milestone) or Further needs (new plan task)" — `goal (result), development_plan (evaluation_text, evaluation_outcome, CLOSED)` · case file `DevelopmentPlan.furtherNeeds` | `202 {undoUntil}` / `400` "Choose a result for every goal." / `403` / `409` not PERFORMED |

**Not in the FRD's endpoint list (no new endpoint proposed — R-API-4):** a list of previous plans (S1 "Previous plans" — assumed part of DV01's payload, D-04-18); a manager "start plan for employee" call (D-04-5); an activities list. **HR's read-only view** uses FRD-01's `GET /api/employees/{id}/development` (W08, "Spec only — cannot be implemented before F05's/F06's `kpi`/`development_plan`/`review` migrations exist"); F05 implements the KPI/goal half of that endpoint's payload, F06 the reviews half. **Dashboards / My Team** (FRD-01 W01/W05) gain their deferred `plansToApprove`, `developmentPlan` and "Development" (`v_kpi_score`) fields additively when F05 ships (FRD-01 D-W2 contract).

## 7. Backend behaviour

Proposed, not yet implemented. All engine calls after commit (R-ARC-5); e-mails via outbox in the same transaction (R-ARC-6); periods from `setting` (R-ARC-7); `audit_log` on every change (R-API-5, G-06).

- **"Create development plan" task.** Raised when milestone "Employee onboarded" is reached (case model, §8) and again when `DevelopmentPlan.furtherNeeds = true`. Because the deployed model and `UndoWindowScheduler` do not do this today (§0), F05 adds it to the model; Spring only writes facts (R-ARC-1). G-04: `TaskNotificationService` (built) sends the in-app notification + `TASK_ASSIGNED` e-mail.
- **Start a plan (DV02).** Insert `development_plan (status DRAFT, employee_id = caller)`; `previous_plan_id` = the caller's most recent `CLOSED` plan with `evaluation_outcome='FURTHER_NEEDS'`, if any [ASSUMPTION]. `ux_plan_open` enforces one open plan. Pre-condition guard (onboarding complete, not on PIP — `employee.stage`) [ASSUMPTION, D-04-30]. Writes `DevelopmentPlan.planId` and `status` to the case file.
- **Goals (DV03–DV05).** Plain CRUD on `goal (plan_id)`; DB CHECKs require `category` and `target_date` for plan goals. Status gate per D-04-6.
- **Submit (DV07).** Validates ≥ 1 goal; sets `status='SUBMITTED'`, `submitted_at`; outbox `DEV_PLAN_SUBMITTED` (seeded `to_manager`); `202 {undoUntil}`; after the window completes the employee's open plan task with `{DevelopmentPlan:{status:'SUBMITTED'}}`; the model raises "Approve development plan" (Manager).
- **Decision (DV09).** Insert `decision (PLAN_APPROVAL, plan_id, decision, comment, decided_by)` — DB CHECK already requires a comment for `REQUEST_CHANGES` (DEV-06). On `APPROVE`: DEV-02 check (Σ `weight_pct` of `kpi.status='ACTIVE'` = 100, enforced in Spring — FRD §5 "Note: "Weights must add up to 100%" spans several rows and is enforced in Spring."), `status='ACTIVE'`, snapshot `progressCheckDays = setting.DEV_PLAN_PROGRESS_CHECK.value_int` into the case file (ADM-02); outbox `DEV_PLAN_DECISION` (seeded `to_employee`). On `REQUEST_CHANGES`: `status='CHANGES_REQUESTED'`, same e-mail. Engine output `{DevelopmentPlan:{decision:…, status:…}}`; model records milestone "Plan approved" or raises the revise task (D-04-9).
- **KPIs (DV10–DV13).** DV11 defaults `frequency` to `setting.KPI_CYCLE.value_text` when omitted (FRD-08 scenario "KPI cycle change": "new KPIs get the frequency Half-yearly by default / And existing KPIs keep their frequency"). `created_by_employee_id` = caller (D-04-11). DV12 rejects a `target_value` change when any `kpi_result` exists for the KPI. DV13 sets `status='RETIRED'`; retired KPIs drop out of DEV-02's sum and of `v_kpi_score`. No case-file write, no engine call.
- **Results (DV14–DV16).** DEV-02 check first, then insert one `kpi_result` per KPI (append-only; never UPDATE — DEV-04). DV15 inserts a new row with `corrects_result_id`; `v_kpi_result` treats the corrected row as superseded. % met is never accepted from the client (DEV-03).
- **Views (new migration, formulas from FRD §2, nothing else invented):** `v_kpi_result` — per KPI, the latest result for which no correction exists, with `pct_met` = `HIGHER`: `least(actual/target*100, 100)`; `LOWER`: `case when actual <= target then 100 else target/actual*100 end`. `v_kpi_score` — per employee over `ACTIVE` KPIs with a result: `sum(pct_met*weight_pct)/sum(weight_pct)`. ("Latest" = most recent `period_start` — [ASSUMPTION].)
- **Progress-check scheduler (timer fallback, §8).** For each plan with `status='ACTIVE'`: next due = (approval `decision.decided_at` or last `checkin.done_at` for this plan) + `DevelopmentPlan.progressCheckDays`. When due and no open `PLAN_PROGRESS` check exists: insert `checkin (PLAN_PROGRESS, plan_id, seq_no = max+1)`, raise "Progress check" (Manager) via the engine REST API, store `engine_task_id`. Skips `PAUSED` plans (DEV-05) — FRD AC "no progress check tasks are created". Same shape as F01's `OverdueReminderScheduler`.
- **Check-in submit (DV18).** Writes `checkin.notes, outcome, done_by, done_at`; one `checkin_goal` per goal (`progress_pct`, `progress_status`, `note`) and copies the confirmed values to `goal.progress_*` ("current value vs value at the check", `02-data-model.md` §5). `CONTINUE`: nothing else (the scheduler computes the next date from `done_at` — WF toast "Check-in saved · next 26 Oct"). `PERFORMED`: `development_plan.status='PERFORMED'`. `SKILL_GAP`: in-app `notification` to HR ("HR is informed" — recipients = users with app role HR [ASSUMPTION]) and the response tells the UI to offer "Start PIP" (FRD-06 Screen 1, which may set `pip.source_checkin_id` = this check-in). Plan status/next check after skill gap: **[OPEN] D-04-16**. `202 {undoUntil}`; engine output `{DevelopmentPlan:{checkOutcome:…}}`.
- **Activity (DV19).** Insert `development_activity (goal_id, activity_type, name, activity_date, created_by_employee_id)`; rejects PIP goals.
- **Evaluation (DV20).** Validates a `result` for every goal of the plan; writes `goal.result`, `evaluation_text`, `evaluation_outcome`, `evaluated_by`, `evaluated_at`, `status='CLOSED'`. `202 {undoUntil}`; engine output `{DevelopmentPlan:{furtherNeeds: outcome=='FURTHER_NEEDS', status:'CLOSED'}}` → milestone "Plan closed" (PLAN_CLOSED) or a new "Create development plan" task (FURTHER_NEEDS).
- **Pause / resume (DEV-05).** Not written here — FRD-06's HR decision sets `PAUSED`, its HR sign-off "Passed" sets `ACTIVE`. F05 must: block DV18 and the scheduler for `PAUSED`; render `p-yt-dev`; keep `DevelopmentPlan.status` in the case file in sync when FRD-06 changes it (F07's job, noted here as the contract).

## 8. Case model needs

`03-case-file.md` already designs this item with **6 fields**, matching FRD-04 §5 and FRD-00's inventory exactly ("DevelopmentPlan | 0..1 | 6 | planId, status, decision, progressCheckDays, checkOutcome, furtherNeeds") — no correction needed (unlike FRD-02's Probation item):

| Item | Field | Type | Written by | Read by (sentry/routing) | DB source |
|---|---|---|---|---|---|
| **DevelopmentPlan** (0..1) | planId | long | Spring (DV02) | link | `development_plan.id` |
| | status | plan status enum | Spring / task output | guards (e.g. no progress check while PAUSED) | `development_plan.status` |
| | decision | APPROVE / REQUEST_CHANGES | output of "Approve development plan" | APPROVE → milestone "Plan approved"; REQUEST_CHANGES → "Revise development plan" (03-case-file name — D-04-9) | `decision (PLAN_APPROVAL)` |
| | progressCheckDays | integer | Spring, on approve (snapshot, ADM-02) | repeating progress check | `setting` (`DEV_PLAN_PROGRESS_CHECK` — D-04-2) |
| | checkOutcome | CONTINUE / PERFORMED / SKILL_GAP | output of "Progress check" | PERFORMED → "Evaluate development plan" | `checkin (PLAN_PROGRESS)` |
| | furtherNeeds | boolean | output of "Evaluate development plan" | true → new "Create development plan" | `development_plan.evaluation_outcome` |

**Human tasks** (names exactly as in FRD-04 — they are also the `setting` TASK_DUE keys, R-CM-3):

| Task | Performer (case role) | Raised when |
|---|---|---|
| Create development plan | Employee | milestone "Employee onboarded"; `furtherNeeds == true` |
| Approve development plan | Manager | plan submitted |
| Revise development plan — **name [OPEN] D-04-9** | Employee | `decision == 'REQUEST_CHANGES'` |
| Progress check | Manager | raised by the Spring scheduler (below), not a native timer |
| Evaluate development plan | Manager | `checkOutcome == 'PERFORMED'` |

**Milestones:** "Plan approved", "Plan closed" (FRD post-conditions). No TASK_DUE rows exist for any of these five tasks (`02-data-model.md` open question 4) — **[OPEN] D-04-25**.

**Timer — conflict with the FRD, resolved by precedent.** FRD-04 §5 says "Integrations: CaseFabric timer for progress checks, read from the case file." The E1 spike (`01-overview.md` §5; `03-case-file.md` §1) proved CaseFabric 1.1.35 cannot read a case-file value in `timerExpression`, and the project adopted the Spring-scheduler fallback "uniformly" for every timer row. A literal `P14D` (as in legacy `emp.case`) would violate G-08/R-CM-4. So "Progress check" is raised by the scheduler (§7) reading `progressCheckDays`; the *interval* still comes from settings. No new case-file field is needed (the next date is derived from DB timestamps) [ASSUMPTION — D-04-3].

**Case roles:** Employee and Manager (already case-team members from case start, `03-case-file.md` §3; Manager is replaced on transfer). HR performs nothing in this flow.

**Not yet in the deployed model.** `employeecase.case`/`.type` hold only `Employee` and `Onboarding`. Building F05 requires a `cmmn-modeler` change: the `DevelopmentPlan` item, a "Development plan" stage (repeatable for further needs), the five tasks, two milestones and the sentries above — a prerequisite, not polish. The legacy `emp.case` "Development plan cycle" may be read for structure only (R-CM-1).

## 9. Data touched

**Already applied (do not re-create):** `development_plan` (V13, DDL matches FRD §5 incl. `ux_plan_open`), `checkin` (V13, incl. `PLAN_PROGRESS` CHECKs and `ux_checkin_plan`), `pip` (V13 — needed as FK target of `goal.pip_id`), `setting` rows `DEV_PLAN_PROGRESS_CHECK` (14 DAYS, MANAGER, `can_disable=false`) and `KPI_CYCLE` (`QUARTERLY`, MANAGER, `can_disable=false`) (V10), `email_template` rows `DEV_PLAN_SUBMITTED` (`to_manager`) and `DEV_PLAN_DECISION` (`to_employee`) (V9), plus platform tables `engine_task`, `notification`, `email_outbox`, `audit_log`, `employee`.

**New migration needed (DDL from FRD §5 / `02-data-model.md` §2.5, §2.7 — nothing invented):**

| Object | Notes |
|---|---|
| `goal` | typed table (plan or PIP goal); FRD-06 also uses it — F05 creates it first |
| `development_activity` | `created_by_employee_id` per FRD (D-04-11) |
| `checkin_goal` | composite PK `(checkin_id, goal_id)` |
| `decision` | all four `decision_type`s in one table (shared with FRD-03 BENCH_CHECK, FRD-06 PIP_HR, FRD-07 CONTRACT_RENEWAL) — F05 is the first feature to need it; the full DDL incl. the other types' CHECKs is created at once (it is one documented table) |
| `kpi` | `created_by_employee_id` per FRD (D-04-11) |
| `kpi_result` | `ux_kpi_result_original` **and** `ux_kpi_result_corrects` (FRD §5 lists both; `02-data-model.md` has only the first — D-04-11) |
| `v_kpi_result`, `v_kpi_score` | §7 formulas; `02-data-model.md` §2.9 describes them but gives no SQL |

**FRD §5 "Cross-check against V1__init.sql" (quoted):** "OK: kpi.frequency allows QUARTERLY / HALF_YEARLY / YEARLY, exactly the FRD list; Appendix A #9 is a wireframe-only issue (the Add KPI pop-up)." "OK: kpi.direction HIGHER / LOWER implements "Lower is better"; kpi.target_value > 0 and weight_pct 1–100." "OK: % met and the weighted score come from v_kpi_result and v_kpi_score (DEV-03), never typed in; corrected results are superseded via corrects_result_id." "OK: development_plan.status includes PAUSED (DEV-05 / PIP-07) and evaluation_outcome PLAN_CLOSED / FURTHER_NEEDS." "OK: decision PLAN_APPROVAL allows APPROVE / REQUEST_CHANGES, and a comment is required for REQUEST_CHANGES (DEV-06)." "Note: "Weights must add up to 100%" spans several rows and is enforced in Spring." — No "Fix:" items for FRD-04. Its only TBC is Appendix A #9 (D-04-1).

**Doc-vs-FRD drifts found while writing this spec** (to fix in `02-data-model.md` in the same change as the migration, R-DB-1 — not edited here): `created_by` vs `created_by_employee_id` on `kpi`/`development_activity`; missing `ux_kpi_result_corrects`; the `decision` CHECK comment says "Add a comment for the manager." where FRD-04 says "Add a comment for the employee." (D-04-10).

**Settings code naming:** FRD §5 lists `setting (PROGRESS_CHECK, KPI_CYCLE)`; the seeded code is `DEV_PLAN_PROGRESS_CHECK` (D-04-2).

**Case file:** `DevelopmentPlan` item (§8) — documented in `03-case-file.md`, not yet in the live model.

## 10. E-mails & timers

| Trigger | Template | To |
|---|---|---|
| Any new task (Create / Approve / Revise / Progress check / Evaluate) | `TASK_ASSIGNED` (G-04; seeded V6) | task owner |
| Employee submits a plan | `DEV_PLAN_SUBMITTED` ("Development plan submitted · The employee submits a plan", FRD-08; seeded V9 `to_manager` — V9 marks the recipient as its own [ASSUMPTION]) | manager |
| Manager approves or requests changes | `DEV_PLAN_DECISION` ("Development plan decision · The manager approves or requests changes"; seeded V9 `to_employee`, same [ASSUMPTION] note) | employee — FRD "the employee is notified" |
| Task overdue | `TASK_OVERDUE` path of G-05 (built by F01) | task owner |
| Skill gap | **no e-mail template** in FRD-08's 21; FRD says "HR is informed" → in-app `notification` only (FRD §3 DV18 tables list `notification`, not `email_outbox`) | HR |
| KPI added | WF toast "KPI added · employee notified" — **[OPEN] D-04-20**: no FRD rule and no template | — |
| KPI results saved | WF toast "KPI results saved · Lotte can see them in My Development" — visibility only, no notification | — |

| Timer / setting | Default | Guard |
|---|---|---|
| `DEV_PLAN_PROGRESS_CHECK` ("Development plan progress check") | 14 days, Manager, cannot be switched off (FRD-08 Timers & rules: "Development plan progress check \| 14 days \| Manager \| No") | snapshotted into `DevelopmentPlan.progressCheckDays` on approval; skipped while `PAUSED` |
| `KPI_CYCLE` | Quarterly (options quarterly, half-yearly, yearly), Manager, cannot be switched off | default `kpi.frequency` for new KPIs only |
| Undo window | 10 s (`UNDO_WINDOW_SECONDS`) | DV07, DV09, DV18, DV20 |
| Overdue reminder | 1 day after due (switchable) | G-05 |

## 11. Acceptance criteria

From FRD-04 §6 (literal):

**AC-01 (FRD §6 "Manager approves a plan")**
```
Given Lotte Jansen submitted a plan with 3 goals
When Mark Smit selects Approve plan and submits
Then the plan becomes active, Lotte is notified and the milestone "Plan approved" is recorded
And the approval task disappears from Mark's tasks
```
(Implies Lotte's active KPI weights total 100 % — 30/40/30 in the wireframe.)

**AC-02 (FRD §6 "% met is calculated")**
```
Given the KPI "Billable utilisation" has target 85, higher is better, weight 30%
When the manager enters an actual value of 82 for Q3 2026
Then % met shows 96%
And the weighted KPI score is recalculated
```

**AC-03 (FRD §6 "Lower is better")**
```
Given the KPI "Code review turnaround" has target 1.0 day, lower is better
When the manager enters 1.3
Then % met shows 77%
```

**AC-04 (FRD §6 "Weights do not add up")**
```
Given an employee has KPIs with weights 30% and 40%
When the manager selects Enter results
Then the message "KPI weights must add up to 100%." is shown
And each KPI can still be saved on its own
```

**AC-05 (FRD §6 "Plan paused during PIP")**
```
Given an employee with an active plan starts a PIP
When the employee opens My Development
Then the plan shows as paused and no progress check tasks are created
```

**AC-06 (FRD §6 "Plan starts after onboarding")**
```
Given Jesse Willems' onboarding is completed
Then Jesse receives a "Create development plan" task
```

Derived — **[ASSUMPTION]**, no literal FRD scenario; flag before building:

**AC-07 (DEV-06 + validation table)**
```
Given a manager is reviewing a submitted plan
When they choose Request changes with an empty comment and submit
Then the message "Add a comment for the employee." is shown
And the plan stays SUBMITTED
```

**AC-08 (validation table)**
```
Given an employee's plan has no goals
When they select Submit for approval
Then the message "Add at least one goal before submitting." is shown
```

**AC-09 (validation table)**
```
Given an employee enters a goal with target date today
When they save
Then the message "The target date must be in the future." is shown
```

**AC-10 (DEV-04)**
```
Given "Billable utilisation" has a result of 82 for Q3 2026
When the manager corrects it to 84
Then a new result referencing the original is stored, the original is kept in the history
And % met shows 99%
```

**AC-11 (lower is better, at target)**
```
Given "Code review turnaround" has target 1.0 day, lower is better
When the manager enters 0.8
Then % met shows 100%
```

**AC-12 (DEV-01, G-02)**
```
Given Lotte Jansen's manager is Mark Smit
When another manager calls any S3–S9 endpoint for Lotte
Then the response is 403 FORBIDDEN
```

**AC-13 (validation table, evaluation)**
```
Given a performed plan with 3 goals
When the manager completes the evaluation with a result for only 2 goals
Then the message "Choose a result for every goal." is shown
```

**AC-14 (process rule 4, Continue)**
```
Given an active plan with progress-check interval 14 days
When the manager saves a progress check with "Continue" on 12 Oct
Then the next "Progress check" task is created on 26 Oct
```

## 12. Decisions & open items

ID prefix `D-04-` (unique to this FRD). Nothing is resolved by the user yet; "Resolved" below means resolved by the FRD itself, R-SRC-1, or an existing project precedent.

| ID | Topic | Status |
|---|---|---|
| D-04-1 | KPI frequency options (Appendix A #9: "The Add KPI pop-up offers more frequencies than FRD (quarterly, half-yearly, yearly); data model open question 3. Suggested fix: Keep the FRD list (FRD wins) and update the pop-up.") | **Resolved (FRD's own suggested fix, R-SRC-1):** Quarterly / Half-yearly / Yearly only; DB CHECK already matches. Knock-on: WF's "Per project"/"Monthly" KPIs (S1, S7 examples) cannot exist. |
| D-04-2 | Setting code: FRD `PROGRESS_CHECK` vs seeded `DEV_PLAN_PROGRESS_CHECK` (V10, F09 Done) | **[OPEN]** — recommend keeping the seeded code (already live in the Settings UI); the FRD name is a label-level difference. |
| D-04-3 | FRD "CaseFabric timer … read from the case file" vs E1 spike | **Resolved by precedent:** Spring scheduler fallback (§8); next date derived from DB timestamps, no new case-file field [ASSUMPTION]. |
| D-04-4 | "Create development plan" after onboarding: F01 spec says it is raised; code and deployed model do not | **[OPEN]** — confirm F05 owns adding it (model task + sentry on "Employee onboarded"), and that already-onboarded employees need a one-off backfill or not. |
| D-04-5 | "The employee (or the manager for them) starts a plan" — no manager screen/endpoint | **[OPEN]** |
| D-04-6 | Can goals be added/edited/removed after submission or while ACTIVE? (WF shows "Edit plan" on a plan "in approval") | **[OPEN]** — proposal: editable only in DRAFT / CHANGES_REQUESTED. |
| D-04-7 | Where the employee updates own progress (DV06 exists; no screen draws it) | **[OPEN]** |
| D-04-8 | DEV-02 read literally blocks approving a plan when the employee has **no** KPIs (sum 0 ≠ 100) | **[OPEN]** — is at least one KPI mandatory before approval? |
| D-04-9 | Name of the "back to employee" task after Request changes (`03-case-file.md`: "Revise development plan"; FRD unnamed; legacy "Revise plan") | **[OPEN]** — recommend "Revise development plan" (already in the approved case-file doc). |
| D-04-10 | Request-changes message: FRD "Add a comment for the employee." vs `02-data-model.md` comment "Add a comment for the manager." | **Resolved (R-SRC-1):** FRD wording; data-model comment to be corrected with the migration. |
| D-04-11 | Column names `created_by_employee_id` (FRD) vs `created_by` (`02-data-model.md`, clashes with the R-DB-2 audit column); `ux_kpi_result_corrects` missing from the data-model doc | **[OPEN]** — recommend the FRD names/index (avoids the audit-column clash); update `02-data-model.md` in the same change. |
| D-04-12 | KPI result periods: WF shows per-KPI periods incl. "Per project"/monthly; `kpi_result` has `period_start/end`; one "Comment for employee" vs per-row `comment` | **[ASSUMPTION]** periods offered per KPI frequency; comment copied to each saved row. Confirm. |
| D-04-13 | Progress check (S5) KPI rows with editable "Current" and status | **[OPEN]** — no column/FRD rule; proposal: read-only + link to S7. |
| D-04-14 | Evaluation "Evidence" column (S8) | **[OPEN]** — no column or FRD input; drop or define. |
| D-04-15 | Required-ness without FRD messages (check-in summary, check outcome, evaluation text/outcome, decision choice, Add activity fields) | **[ASSUMPTION]** "This field is required." — confirm. |
| D-04-16 | After "Skill gap": plan status and whether progress checks continue | **[OPEN]** |
| D-04-17 | "Plan performed — all goals are done": enforce all goals `ACHIEVED`? | **[OPEN]** |
| D-04-18 | S1 "Previous plans" table: no endpoint named; "KPIs" count has no source (KPIs belong to the employee, not a plan) | **[OPEN]** |
| D-04-19 | `period_label` ("Nov 2026 – Oct 2027", "2026–27"): who sets it, how | **[OPEN]** — not in FRD inputs. |
| D-04-20 | Notify the employee when a KPI is added (WF toast) | **[OPEN]** — FRD silent, no template. |
| D-04-21 | Employee's "full KPI history" — no employee screen draws it | **[OPEN]** — DV16 role assumed to include the employee. |
| D-04-22 | Retire KPI (DV13) and correct result (DV15) have no drawn UI | **[OPEN]** |
| D-04-23 | Employee path param `{code}` (FRD) vs `{id}` (built endpoints, FRD-01 W08) | **Resolved by precedent:** `{id}`. |
| D-04-24 | `409`/`400` texts with no FRD wording: open plan exists, target locked, duplicate period, weight out of 1–100 | **[OPEN]** |
| D-04-25 | TASK_DUE due days for the five development tasks (`02-data-model.md` OQ 4) | **[OPEN]** — G-03 needs a due date. |
| D-04-26 | `development_plan.status = 'CANCELLED'` — never set by FRD-04 | **[OPEN]** (perhaps on exit, FRD-07). |
| D-04-27 | Text of the Development-tab weight warning | **[ASSUMPTION]** reuse "KPI weights must add up to 100%." |
| D-04-28 | % met display rounding | **[ASSUMPTION]** whole percent, half-up (matches both FRD examples). |
| D-04-29 | Must every active KPI receive an actual in one "Save results"? | **[OPEN]** |
| D-04-30 | Starting a plan before onboarding completes or during a PIP (pre-conditions) | **[ASSUMPTION]** blocked with `409`; message [OPEN] (D-04-24). |

## 13. Definition of done

- [ ] User answers for the blocking [OPEN] items: D-04-4, D-04-5, D-04-6, D-04-8, D-04-9, D-04-16, D-04-25 (CLAUDE.md "ask when blocked"); the rest may proceed on the stated assumptions once approved.
- [ ] Flyway migration: `goal`, `development_activity`, `checkin_goal`, `decision`, `kpi`, `kpi_result`, `v_kpi_result`, `v_kpi_score` (DDL per FRD §5 / `02-data-model.md`), TASK_DUE rows (D-04-25); `02-data-model.md` updated in the same change (D-04-10, D-04-11).
- [ ] CMMN model extended (§8): `DevelopmentPlan` item, five tasks, two milestones, sentries; "Create development plan" after "Employee onboarded" (D-04-4).
- [ ] Backend DV01–DV20 with server-side role/ownership checks (DEV-01, G-02, G-13), undo window on DV07/DV09/DV18/DV20, outbox e-mails, audit log; progress-check scheduler honouring `PAUSED`.
- [ ] Frontend S1–S9 (incl. `p-sb-dev`/`p-yt-dev` states), menu item "My Development", S9 replacing FRD-01's D-W1 placeholder; FRD-01 W01/W05/W08 deferred fields filled additively.
- [ ] Every acceptance criterion in §11 has an automated test.
- [ ] Gate 5 review (security, code, spec coverage) passed.
