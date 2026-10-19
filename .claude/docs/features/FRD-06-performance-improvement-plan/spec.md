# FRD-06 — Performance Improvement Plan (PIP)

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-06/FRD-06_Performance_Improvement_Plan_PIP.docx` (primary, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), `FRD-00/FRD-00_Application_Overview.docx` (roles, permissions matrix, G-01…G-13, lifecycle stages, case-file inventory, Appendix A), `FRD-08/FRD-08_Reports_and_Settings.docx` (the PIP timers and the 3 PIP e-mail templates this flow reads). Cross-module rule text quoted from `FRD-03` (Performance concern), `FRD-04` (DEV-05, Skill gap), `FRD-05` (Under-performing) and `FRD-07` (OFF-01). The legacy v1.1 monolithic PDF is superseded — see `.claude/docs/source/README.md`. |
| Wireframes | `.claude/docs/source/FRD-06/WF-06_Performance_Improvement_Plan_PIP.html` (v1.2, canonical) — pages `p-mg-pip-define`, `p-hr-pip-approve`, `p-yt-pip`, `p-mg-pip-checkin`, `p-mg-pip-final`; modals `m-pip-goal`, `m-dispute` (all 7 ids confirmed present by direct read; no other `p-*`/`m-*` id exists in the file). Redrawn as text in `wireframes.md`. |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-06 wireframe HTML > legacy v1.1 (R-SRC-1). Conflicts are listed in §12. |
| Relationship to feature builds | This FRD is feature **F07 "Performance Improvement Plan"** in `docs/features/README.md`, depending on **F04** (Projects — source "project review / Performance concern"), **F05** (Development plan — source "skill gap", and the plan pause/resume of DEV-05/PIP-07) and **F06** (Appraisal — source "Under-performing"). **Nothing of FRD-06's own flow is built.** What already exists: the `pip` and `checkin` tables and the `v_pip` view (applied early by F09's `V13__create_report_tables_and_views.sql`, user-approved 2026-10-17), the read-only PIPs report that reads them (F09, `GET /api/reports/pips`), the 3 PIP timer settings (`V10`) and the 3 PIP e-mail templates (`V9`). This spec is F07's starting point. |

## 0. Status & sources

FRD-06 v1.2 describes one flow — "Give an employee who is not meeting expectations a clear, fair and documented plan to improve, with regular check-ins and a clear outcome." — in five parts that map one-to-one onto its own §3 "Features, Endpoints & Database" groups 4.1–4.5: draft the PIP (Manager), HR review and approval, employee response (acknowledge or dispute once), periodic check-ins, and the final review with HR sign-off. It gives 7 screens (5 pages + 2 pop-ups), 18 endpoints (counting each method of the goals resource separately), a 12-row validation table with exact messages, 10 MUST invariants PIP-01…PIP-10, the full column list of every table it touches with its own "Cross-check against V1__init.sql", 6 acceptance scenarios, and one open item (Appendix A #5).

**What already exists in the repo (verified 2026-10-19, not assumed):**
- **Schema.** `pip`, `checkin`, `review`, `development_plan`, `employee_exit` and the view `v_pip` exist (`V13__create_report_tables_and_views.sql`, DDL transcribed from `02-data-model.md`). `document` (with doc types `PIP_ATTACHMENT`, `DISPUTE_ATTACHMENT`) and `employee.stage` (with value `PIP`) exist since `V1__init.sql`. **`goal`, `checkin_goal` and `decision` do NOT exist** — `grep "create table (goal|checkin_goal|decision)"` over every migration returns nothing. `docs/features/README.md`'s note lists `goal` and `checkin_goal` as still unbuilt but does not mention `decision`; it is unbuilt too (§9, D-06-2).
- **Settings.** `PIP_CHECKIN` (14 DAYS, `MANAGER`, cannot disable), `PIP_DURATION` (90 DAYS, `MANAGER_HR`, cannot disable), `PIP_EXTENSION` (30 DAYS, `MANAGER_HR`, can disable) — `V10__seed_remaining_timers.sql`, matching FRD-08's Timers & rules table exactly. No `TASK_DUE` row exists for any PIP task (§10, D-06-12).
- **E-mail templates.** `PIP_NOTICE`, `PIP_DISPUTED`, `PIP_OUTCOME` — `V9__seed_remaining_email_templates.sql` (placeholder bodies, editable by HR).
- **Backend/frontend code.** Only read-only consumers: `ReportController`/`ReportService` (`GET /api/reports/pips`, reads `v_pip`), `PipsReportPage.tsx`, `EmployeeStage.PIP`. `TeamService` accepts the My Team filter "PIP" but returns an empty page ("deferred to F04/F07"). `EmployeeRecord.tsx` omits the Manager's "Start PIP" header button (FRD-01 spec D-W8, owned by this feature). No PIP controller, service, entity or screen exists.
- **Engine model.** The deployed `employeecase.case` has no PIP item, stage, task or milestone (`grep -ci pip` = 0 on both `.case` and `.type`). Standalone `DefinePIP`/`ApprovePIP`/`AcknowledgePIP`/`WorkOnPIP`/`FinalPIPReview.humantask` and `SendPIPNotice.process` files exist in `casefabric/getting-started/src/definitions/`, but they belong to the **legacy** `emp.case` (R-CM-1: read, do not extend) and put free text and e-mail addresses (`goals`, `employeeEmail`, `noticeText`) into the case file, which R-ARC-3 forbids. Not reusable as-is (§8).

Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Where the sources leave a gap, it is marked **[OPEN]** or **[ASSUMPTION]** and listed in §12.

## 1. Scope

**In scope (FRD-06's own "IN-SCOPE" list, exact wording)**
- Manager drafts a PIP
- HR approves, amends or rejects it
- PIP notice to the employee; acknowledge or dispute
- Check-ins at a configurable interval (default 14 days)
- Final review: passed, one extension, or not met

Plus what FRD-06's endpoints and invariants make this module responsible for: pausing and resuming the employee's development plan (PIP-07 / DEV-05), moving the case into and out of the PIP stage, and creating the `employee_exit` row when the outcome is "Not met".

**Out of scope**
- FRD-06's own "OUT-OF-SCOPE" line: "none specific to this module in v1."
- Offboarding itself (FRD-07, F08) — "PIP not met" only *starts* it. The reverse direction, PIP-10 (offboarding starting during a PIP closes the PIP with "Closed – employee leaving"), is executed by FRD-07's `POST /exits/{id}/confirm` ("pip (CLOSED_EMPLOYEE_LEAVING)", FRD-07 §3), i.e. built by F08; this spec only defines the PIP side of that contract (§7).
- The source screens that offer "Start PIP" — project review "Performance concern" (FRD-03, F04), progress check "Skill gap" (FRD-04, F05), appraisal "Under-performing" (FRD-05, F06). This feature provides the destination (S1) and accepts the source id; it does not build those screens.
- The PIPs report (FRD-08 Screen 6, `p-hr-rep-pip`) — **already built** by F09.
- Editing the PIP timers and the PIP e-mail templates — FRD-08 Settings, **already built** by F09.

## 2. Roles & permissions

From FRD-00 §2 ("Draft PIP / approve PIP | HR: Approve | Manager: Draft | Employee: Acknowledge or dispute | IT admin: No") and FRD-06 §3 "Access gate" (quoted: "Screens 1–2 are for the employee's current manager, for active employees not already on a PIP. Screen 3 is HR-only and never for an HR user's own PIP (G-13). Screens 4–5 open for the employee only after HR approves and the notice is sent (PIP-01). Screen 6: the manager writes; the employee and HR read the notes and the employee can comment (PIP-08). Screen 7: the manager proposes, HR signs off (PIP-06).").

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| Start / edit a draft PIP, add goals and evidence, send to HR (S1, S2) | No | Yes — current manager of an **active** employee **not already on a PIP** | No | No |
| Approve & send notice / Amend / Reject (S3) | Yes — never for their own PIP (G-13) | No | No | No |
| Edit goals during Amend (S2 on S3) | Yes ("Amend — HR edits the goals, then approves.") | No | No | No |
| See the PIP at all | Yes (all) | Own team (WS-02) | **Own PIP only, and only from the moment the notice is sent (PIP-01)** | No |
| Acknowledge / dispute (S4, S5) | No | No | Own PIP; dispute only once (PIP-09) | No |
| Write a check-in (S6) | Read | Write (current manager) | Read + add a comment (PIP-08) | No |
| Final review (S7) | Sign off (PIP-06) — never their own PIP (G-13) | Propose result per goal + outcome | **[ASSUMPTION]** read-only after HR sign-off, via S4 — FRD silent | No |

G-13 applies throughout: "A user never reviews, approves or finalises anything about themselves." A manager who is themselves on a PIP is handled by their own manager and HR like any other employee. Server-side checks are mandatory (G-02, R-API-2); hiding a button is never the only protection.

"Current manager" vs. `pip.manager_id`: `pip.manager_id` is a **snapshot** ("→ employee.id snapshot", FRD-06 §5). **[ASSUMPTION]** access checks use the employee's *current* manager (WS-02, matching the access-gate wording "the employee's current manager"), so a PIP survives a manager transfer (FRD-01 Transfer manager moves the ticked open tasks); `manager_id` stays the historical author. See D-06-17.

## 3. Business rules

Copied from FRD-06 §4 "Hard Business Invariants" by ID (R-SRC-3 — wording kept exact; all priority MUST):

| ID | Rule |
|---|---|
| PIP-01 | The employee cannot see the PIP until HR approves it and the notice is sent. |
| PIP-02 | Every PIP has at least one goal with a measure and the support offered. |
| PIP-03 | The PIP notice is always sent by e-mail as well as in the application. |
| PIP-04 | Acknowledging means the employee has received the plan, not that they agree. |
| PIP-05 | A PIP can be extended once, by 30 days. After an extension the only outcomes are Passed or Not met. |
| PIP-06 | The final outcome needs both the manager and HR. |
| PIP-07 | During a PIP the development plan is paused (DEV-05). |
| PIP-08 | Check-in notes are visible to the employee and HR. |
| PIP-09 | A PIP can be disputed only once. |
| PIP-10 | If offboarding starts during a PIP, the PIP closes with the outcome "Closed – employee leaving" and its open tasks are cancelled (OFF-01). |

"Global rules G-01 … G-13 (FRD-00, section 4) also apply to this module." Most relevant here: G-03 (task owner/due date/undo window), G-04 (in-app + e-mail for every new task), G-05 (overdue colours and reminder), G-06 (history), G-08 ("All time periods (check-ins, reminders, PIP length) come from Settings › Timers & rules. They are never fixed in the application."), G-11 (save toast, Cancel never saves), G-12 (dd-mm-yyyy), G-13.

Cross-module rules this flow executes or depends on (quoted from their own FRDs):
- **DEV-05** (FRD-04): "An active plan is paused while the employee is on a PIP and resumes when the PIP is passed."
- **OFF-01** (FRD-07): "When offboarding starts, open development, appraisal, project review and PIP tasks for the employee are cancelled."
- **APR-02** (FRD-05): "Employees who joined after the eligibility date, are on probation, are on a PIP or are in offboarding are excluded." (F06 reads `pip` for this; no work here.)
- **ADM-02** (FRD-08): "Changes to timers apply to new timers only; running cases keep their dates." — hence the `pip.checkin_days/duration_days/extension_days` snapshots.

Process rules from FRD-06 §1–§2 (quoted, not paraphrased):
1. Pre-conditions: "The employee is active and not already on a PIP." / "Only the manager drafts a PIP. It can follow a project review, a progress check (skill gap) or an appraisal (under-performing), or be started directly."
2. "The manager selects "Start PIP" and completes the draft: employee, source, start date, reason, at least one goal, check-in frequency. The manager selects "Send to HR"."
3. "HR receives an "Approve PIP" task, reviews the draft and the evidence, sets a new start date if the proposed one has passed, confirms the checklist (goals measurable, support documented, informal conversation held) and chooses: Approve & send notice — the employee receives the PIP notice by e-mail and a task to respond. / Amend — HR edits the goals, then approves. / Reject — the draft returns to the manager with HR's comments."
4. "The employee reads the plan and chooses: Acknowledge — the milestone "PIP active" is recorded and the PIP starts on the start date. / Dispute — the employee explains which points they disagree with; HR and the manager are notified (template "PIP disputed") and HR reviews the plan once more (back to step 2). A PIP can be disputed only once; the plan HR sends after that is final."
5. "Every 14 days (configurable) the manager gets a "PIP check-in" task, records progress and status per goal and writes notes. The employee can add comments."
6. "After 90 days (configurable) the manager gets a "Final PIP review" task, records the result per goal and proposes an outcome. HR signs off: PIP passed — the PIP stage closes and the employee returns to normal active employment. / Extend 30 days — a new final review is set 30 days later. Allowed only once. / PIP not met — offboarding starts (FRD-07)."
7. Constraints: "Timer values for check-ins, PIP length and extension from FRD-08. The maximum of one extension is fixed and not configurable." / "The PIP notice and outcome letters use an editable template that HR/legal can adjust." / "If the employee does not respond before the due date, the overdue reminder is sent (G-05); if there is still no response, HR follows up with the employee personally."

## 4. Screens

FRD-06 §3: "FRD-06 has 7 screens across 5 features". Numbering below follows the FRD's own Screen 1…7.

| # | Screen | Who | Route / trigger (FRD-06 §3, exact) | Wireframe page | FRD |
|---|---|---|---|---|---|
| S1 | New improvement plan | Manager | `/team/:code/pip/new` · "Start PIP" on employee record; Performance concern, Skill gap or Under-performing outcome | `p-mg-pip-define` | §3 Screen 1, §4.1 |
| S2 | PIP goal (pop-up) | Manager (S1); HR (S3, Amend) | Modal on Screen 1 and Screen 3 (Amend) · "+ Add goal" | `m-pip-goal` | §3 Screen 2, §4.1 |
| S3 | Review & approve PIP | HR | `/pips/:pipId/approve` · HR task "Approve PIP"; PIPs report | `p-hr-pip-approve` | §3 Screen 3, §4.2 |
| S4 | PIP notice – acknowledge or dispute | Employee | `/me/pip` · Employee task; notification; link in PIP notice e-mail | `p-yt-pip` | §3 Screen 4, §4.3 |
| S5 | Dispute improvement plan (pop-up) | Employee | Modal on Screen 4 · "Dispute" | `m-dispute` | §3 Screen 5, §4.3 |
| S6 | PIP check-in | Manager (write); Employee, HR (read; employee comments) | `/pips/:pipId/checkins/:checkinId` · Manager task "PIP check-in" | `p-mg-pip-checkin` | §3 Screen 6, §4.4 |
| S7 | Final PIP review (manager proposal, HR sign-off) | Manager (propose); HR (sign off) | `/pips/:pipId/final-review` · Manager task "Final PIP review"; HR task "Confirm PIP outcome" | `p-mg-pip-final` | §3 Screen 7, §4.5 |

**S7 and FRD-00 Appendix A #5 — applies to this FRD.** FRD-06's own "Open Items (TBC)" repeats it verbatim: "TBC (Appendix A #5 · FRD-02 Screen 13, FRD-07 Screen 4, FRD-06 Screen 7): HR approval of "End employment", HR "Confirm exit" and HR PIP sign-off have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." WF-06 draws only the manager's view of `p-mg-pip-final` (its "HR sign-off *" is a plain text input and its submit toast reads "Outcome sent to HR for sign-off"). Unlike FRD-02 Screen 13 (no route at all), FRD-06 already gives **one** route for both the manager task and the HR task, which points to the "same page with an HR action bar" option. This spec draws S7 that way (`wireframes.md` S7, HR variant) — **[OPEN] D-06-1, confirm before building.**

Entry points owned elsewhere: "Start PIP" header button on the employee record (visibility rule specified by FRD-01 spec §4 / D-W8, wired by this feature); "Start PIP" offered after a project review "Performance concern" (FRD-03), a progress-check "Skill gap" (FRD-04), an appraisal "Under-performing" (FRD-05); the PIPs report row link (FRD-08, built) opens S3.

## 5. Fields & validation

Messages are copied exactly from FRD-06 §4 "Validation Table" (R-SRC-3). Field lists come from the FRD inputs table and WF-06; anything WF-06 draws that has no FRD/data-model backing is flagged.

### S1 — New improvement plan (Manager)

| Field | Type / source | Rule | Message |
|---|---|---|---|
| Employee | select, own team (WS-02), active, not on a PIP; pre-filled and locked when opened from a record/source | Required | "This field is required." |
| Raised from (source) | select — WF-06 options "Project review", "Development plan · skill gap", "Appraisal" → `pip.source_review_id` / `pip.source_checkin_id` | Required | "This field is required." — **[OPEN] D-06-5**: no "Direct" option is drawn although the FRD allows a PIP "started directly" (`v_pip.source = 'DIRECT'` when both FKs are null), and the screen gives no way to pick *which* review/check-in when not opened from the source. |
| Proposed start | date, dd-mm-yyyy (G-12) → `pip.proposed_start_date` | Required; "After today" | "This field is required." / "The start date must be in the future." |
| Duration | **read-only** (WF-06 `field-readonly`, "90 days") = `PIP_DURATION` snapshot → `pip.duration_days` | – | – |
| Reason | text area, placeholder "Describe the performance concern with examples" → `pip.reason` | Required | "This field is required." |
| Improvement goals | table #/Goal/Measure/Support, "+ Add goal" (S2), "×" to remove (R-UI-1) → `goal` rows | "At least one, each with goal and measure" (PIP-02) | "Add at least one goal with a measure." |
| Check-in frequency | select, WF-06 shows the single option "Every 14 days" = `PIP_CHECKIN` snapshot → `pip.checkin_days` | Required | "This field is required." — **[OPEN] D-06-6** (manager-chosen vs settings-only, G-08) |
| Evidence | file upload → `document` (`PIP_ATTACHMENT`) | optional | – — endpoint exists (§6 PIP06), **no upload control is drawn on `p-mg-pip-define`** — D-06-8 |
| `pip.extension_days` | not shown; `PIP_EXTENSION` snapshot at draft (ADM-02) | – | – |

Validation timing: "Save draft" saves without the Send-to-HR checks **[ASSUMPTION]** (only `pip`'s own NOT NULL columns — employee, reason, proposed start date — are needed to insert the row; same pattern as F01's onboarding draft). "Send to HR" runs every row above.

### S2 — PIP goal (pop-up)

| Field | Column | Rule | Message |
|---|---|---|---|
| Goal * | `goal.title` (VARCHAR 200) | Required | "Add at least one goal with a measure." |
| How it is measured * | `goal.how_measured` | Required | "Add at least one goal with a measure." |
| Support offered | `goal.support` | **Required by PIP-02 and by the DB** (`check (pip_id is null or (support is not null …))`) although WF-06 draws it **without** an asterisk and the FRD validation message only names "goal and measure" | **[OPEN] D-06-25**: no FRD message exists for a missing support text. **[ASSUMPTION]**: mark it required in the UI (FRD > wireframe, R-SRC-1) and reuse "This field is required." (the FRD's generic required message) until the user confirms. |

### S3 — Review & approve PIP (HR)

Read-only header: drafted by / date, reason, period (start – start + duration · N days), check-in interval, goals table, and the source evidence (FRD §4.2 "Draft, goals and source evidence" — `review`, `checkin`, `document`).

| Field | Column | Rule | Message |
|---|---|---|---|
| HR comments | `decision.comment` | Required for Amend and Reject (DB: `check (comment is not null or decision not in ('REQUEST_CHANGES','AMEND','REJECT'))`) | "Add a comment for the manager." |
| ☐ Goals are specific and measurable | `pip.hr_goals_measurable` | All three ticked to approve | "Confirm all checks before approving." |
| ☐ Support is documented | `pip.hr_support_documented` | same | same |
| ☐ Informal conversation held | `pip.hr_informal_conversation` | same | same |
| New start date | `pip.start_date` / `decision.new_start_date` — **not drawn in WF-06**, required by the FRD flow ("sets a new start date if the proposed one has passed") | "Today or later" | "The start date has passed. Choose a new start date." |
| Decision | segmented: Approve & send notice (green) / Amend / Reject (red) → `decision.decision` APPROVE / AMEND / REJECT | Required | "This field is required." **[ASSUMPTION]** — no FRD message for "no decision chosen"; FRD's generic required text used. |

Whether the checklist also gates **Amend** (which "then approves") — **[ASSUMPTION]** yes; see D-06-9.

### S4 — PIP notice (Employee)

Read-only: sent date and sender, respond-by date, manager, period, check-in interval, reason, goals table (#/Goal/Measure/Support), and check-in notes once they exist (§4.3 "Plan, goals, check-in notes").

| Field | Rule | Message |
|---|---|---|
| ☐ "I have read this plan and understand the goals and support offered" | Must be ticked to Acknowledge | "Confirm that you have read the plan." |
| Your comments (text area, WF-06) | – | – — **[OPEN] D-06-7**: `pip` has no column for acknowledgement comments; FRD inputs list "Comments" under "Acknowledgement or dispute". |
| Dispute button | Hidden once used (§4.3 "Dispute hidden once used", PIP-09) | – (if called anyway: 409, §6) |

### S5 — Dispute improvement plan (pop-up)

| Field | Column | Rule | Message |
|---|---|---|---|
| Which points do you disagree with and why? * | `pip.dispute_reason` (+ `disputed_at`) | Required | "Explain which points you disagree with." |
| Attachments ("Attach supporting documents, or browse file") | `document` (`DISPUTE_ATTACHMENT`) | optional | – |

### S6 — PIP check-in

| Field | Column | Rule | Message |
|---|---|---|---|
| Progress per goal | `checkin_goal.progress_pct` (0–100), copied to `goal.progress_pct` | **[ASSUMPTION]** editable 0–100 — WF-06 draws the % as static text only | – |
| Status per goal | `checkin_goal.progress_status`, copied to `goal.progress_status` | One of ON_TRACK / AT_RISK / BEHIND / ACHIEVED (DB) — WF-06's selects show only On track / At risk / Achieved (D-06-15) | – |
| Check-in notes * | `checkin.notes` | Required | "Add check-in notes." |
| Employee comments | `checkin.employee_comment` — written by the **employee** via PIP16, read-only for manager/HR | – | – |
| History | list of earlier check-ins (#, date, summary) | read-only | – |

### S7 — Final PIP review

| Field | Column | Rule | Message |
|---|---|---|---|
| Result per goal | `checkin_goal.result` MET / PARTLY_MET / NOT_MET, copied to `goal.result` | Required **[ASSUMPTION]** (FRD: "records the result per goal") | covered by the row below |
| Final assessment * | `checkin.notes` (PIP_FINAL) | Required | "Add an assessment and choose an outcome." |
| Outcome | `checkin.outcome` PASSED / EXTEND / NOT_MET | Required | "Add an assessment and choose an outcome." |
| Extend 30 days | disabled/hidden after one extension | "Not available after one extension" | "This PIP has already been extended once." |
| Manager sign-off | read-only, the submitting manager (`checkin.done_by`) | – | – |
| HR sign-off * | `checkin.hr_signoff_by` / `hr_signoff_at` — the signing HR user, **not** free text (D-06-24) | "Required for the final outcome" | "HR must confirm the outcome." |
| Summary panel | Started, Deadline, Check-ins n of m, Extensions n of 1, Disputes — derived from `v_pip`/`checkin` | read-only | – |

The "Extend 30 days" label is FRD/wireframe text; the actual extension length is `pip.extension_days` (snapshot of `PIP_EXTENSION`). **[ASSUMPTION]** the label shows the snapshot value ("Extend {n} days") so G-08 holds if HR changes the setting — D-06-14.

## 6. API

Taken from FRD-06 §3 "Features, Endpoints & Database" 4.1–4.5 (method, path, purpose and table list quoted from the FRD). Paths get this project's `/api` prefix (R-API-1), same adaptation every other spec makes. Errors use R-API-3's shapes; every state change writes `audit_log` (R-API-5) and is optimistic-locked (R-DB-5, `409 STALE_VERSION`).

| # | Method & path | Role | FRD purpose · tables | Responses |
|---|---|---|---|---|
| **4.1 Draft the PIP (S1, S2)** |||||
| PIP01 | `POST /api/pips` | Manager (current manager of an active employee) | "Create draft with source, start date, reason; timers copied from settings (ADM-02); one open PIP (ux_pip_open)" · `pip (DRAFT, manager_id, source_review_id / source_checkin_id, checkin_days, duration_days, extension_days), setting` | `201 {id}` · `400` field messages · `403` not own team / not a manager · `409 PIP_ALREADY_OPEN` (ux_pip_open) · `409` employee not active **[ASSUMPTION codes]** |
| PIP02 | `PUT /api/pips/{id}` | Manager (author/current manager), status DRAFT | "Edit draft" · `pip` | `200` · `400` · `403` · `409` not DRAFT / stale |
| PIP03 | `POST /api/pips/{id}/goals` | Manager (DRAFT); HR (IN_HR_REVIEW/DISPUTED, Amend) **[ASSUMPTION]** | "Goals with measure and support (PIP-02)" · `goal (pip_id)` | `201` · `400` |
| PIP04 | `PUT /api/pips/{id}/goals/{goalId}` | same as PIP03 | same | `200` · `400` · `404` |
| PIP05 | `DELETE /api/pips/{id}/goals/{goalId}` | same as PIP03 | same | `204` · `404` |
| PIP06 | `POST /api/pips/{id}/documents` | Manager (DRAFT) | "Attach evidence" · `document (PIP_ATTACHMENT)` (multipart, reusing F01's document storage, A07) | `201` · `400` |
| PIP07 | `POST /api/pips/{id}/send-to-hr` | Manager | "Send to HR" · `pip (IN_HR_REVIEW)` · case file `PIP (pipId, status)` | `200` · `400` "Add at least one goal with a measure." / required fields / "The start date must be in the future." · `409` not DRAFT |
| **4.2 HR review and approval (S3)** |||||
| PIP08 | `GET /api/pips/{id}` | HR (all, not own); Manager (own team) | "Draft, goals and source evidence" · `pip, v_pip, goal, review, checkin, document` | `200` · `403` · `404` |
| PIP09 | `POST /api/pips/{id}/hr-decision` | HR, never own PIP (G-13) | "Approve & send notice (checklist ticked, new start date if passed), Amend, or Reject with comment; approval moves the case to stage PIP and pauses the plan (DEV-05)" · `decision (PIP_HR), pip (start_date, hr_*, NOTICE_SENT / REJECTED, notice_sent_at), goal, employee (stage PIP), development_plan (PAUSED), email_outbox (PIP_NOTICE)` · case file `PIP.hrDecision, PIP.startDate` | `202 {undoUntil}` (completes "Approve PIP", R-API-6) · `400` "Confirm all checks before approving." / "Add a comment for the manager." / "The start date has passed. Choose a new start date." · `403` · `409` not IN_HR_REVIEW/DISPUTED |
| **4.3 Employee response (S4, S5)** |||||
| PIP10 | `GET /api/me/pip` | Employee (self) | "Plan, goals, check-in notes; Dispute hidden once used" · `pip, goal, checkin` | `200` · `404` when no PIP **or** status DRAFT/IN_HR_REVIEW/REJECTED (PIP-01 — indistinguishable from "no PIP") |
| PIP11 | `POST /api/me/pip/acknowledge` | Employee (self) | ""I have read this plan"; milestone "PIP active" (PIP-04)" · `pip (acknowledged_at, ACTIVE)` · case file `PIP.acknowledged` | `202 {undoUntil}` · `400` "Confirm that you have read the plan." · `409` not NOTICE_SENT |
| PIP12 | `POST /api/me/pip/dispute` | Employee (self) | "Dispute once with reason and attachments (multipart); back to HR (PIP-09)" · `pip (dispute_reason, disputed_at, DISPUTED), document (DISPUTE_ATTACHMENT), email_outbox (PIP_DISPUTED)` · case file `PIP.disputed` | `202 {undoUntil}` · `400` "Explain which points you disagree with." · `409 PIP_ALREADY_DISPUTED` **[ASSUMPTION code]** |
| **4.4 Check-ins (S6)** |||||
| PIP13 | `GET /api/pips/{id}/checkins` | Manager (own team), HR, Employee (own, after notice) | "Check-in history and the current one" · `checkin (PIP_CHECKIN), checkin_goal, goal` | `200` · `403` |
| PIP14 | `GET /api/checkins/{id}` | same as PIP13 | same | `200` · `403` · `404` |
| PIP15 | `POST /api/checkins/{id}/submit` | Manager (current manager) | "Progress and status per goal, notes" · `checkin, checkin_goal, goal (progress_*)` · case file `PIP.checkinDays` drives the next timer | `202 {undoUntil}` · `400` "Add check-in notes." · `409` already done |
| PIP16 | `POST /api/checkins/{id}/employee-comment` | Employee (self) | "Employee comment on a check-in" · `checkin (employee_comment)` | `200` · `403` · `409` **[ASSUMPTION]** check-in not yet submitted |
| **4.5 Final review and outcome (S7)** |||||
| PIP17 | `POST /api/pips/{id}/final-review` | Manager (current manager) | "Manager result per goal and proposed outcome; Extend only once (PIP-05)" · `checkin (PIP_FINAL, seq_no 1 or 2, outcome), checkin_goal (result)` · case file `PIP.proposedOutcome` | `202 {undoUntil}` · `400` "Add an assessment and choose an outcome." / "This PIP has already been extended once." |
| PIP18 | `POST /api/checkins/{id}/hr-signoff` | HR, never own PIP (G-13) | "HR confirms: Passed (stage Active, plan resumes), Extend 30 days, or Not met (starts offboarding)" · `checkin (hr_signoff_*), pip (outcome, CLOSED / EXTENDED), employee (stage), development_plan (ACTIVE), employee_exit (PIP_NOT_MET), email_outbox (PIP_OUTCOME)` · case file `PIP.hrConfirmed, PIP.extended, PIP.result` | `202 {undoUntil}` · `400` "HR must confirm the outcome." · `403` · `409` not a PIP_FINAL awaiting sign-off |

Notes:
- **`/api/checkins/{id}/submit` is shared with FRD-04** (its §4 lists the same path for `PLAN_PROGRESS` check-ins: "Confirm progress per goal; Continue, Plan performed or Skill gap"). One endpoint, dispatched on `checkin.checkin_type`; whichever of F05/F07 is built first creates it, the other extends it (D-06-21).
- `/api/me/pip` is singular because there is at most one open PIP (`ux_pip_open`); this follows the FRD's own path and the existing `/api/me/onboarding` precedent (F01), not R-API-1's plural-noun default — noted, not changed.
- Error codes marked **[ASSUMPTION]** are this spec's proposal for R-API-3's `<SPECIFIC_CODE>`; the FRD names none.
- No list endpoint for PIPs is specified: HR reaches S3 from the task or from the existing PIPs report (C40); the manager reaches S1 from the employee record. Nothing added (R-API-4).

## 7. Backend behaviour

General pattern per `01-overview.md` §4 (its own worked example is "Approve PIP"): authorise (role, ownership, G-13) → validate (exact messages) → one transaction (business rows + `audit_log` + `email_outbox`) → commit → engine call(s) after commit, retried, never silently rolled back (R-ARC-5).

- **Draft (PIP01/02).** Checks: actor is the employee's current manager; `employee.stage = 'ACTIVE'` ("The employee is active"); no open PIP (`ux_pip_open` — the DB backs this; map the unique violation to 409). Copies `PIP_CHECKIN`, `PIP_DURATION`, `PIP_EXTENSION` `value_int` into `pip.checkin_days/duration_days/extension_days` at creation (ADM-02, R-ARC-7). `manager_id` = actor. Source: exactly one or none of `source_review_id` / `source_checkin_id` (DB `num_nonnulls ≤ 1`). **[ASSUMPTION]** the PIP case-file item (`pipId`, `status=DRAFT`, `checkinDays/durationDays/extensionDays`) is written at draft creation — `03-case-file.md` §1: "`PIP.checkinDays` is copied when the PIP is drafted".
- **Goals (PIP03–05).** Enforce the DB rule for PIP goals (`support` not null, `category`/`target_date` null). "At least one goal" is a Spring check at Send to HR and at HR approval — FRD cross-check: "Note: "At least one goal" (PIP-02) and "PIP not visible before notice" (PIP-01) are enforced in Spring."
- **Send to HR (PIP07).** Full S1 validation; `pip.status = IN_HR_REVIEW`; after commit, case file `PIP.status` → engine raises "Approve PIP" (HR). Nothing reaches the employee (PIP-01): no task, notification or e-mail is addressed to them (FRD AC "Employee cannot see a draft PIP").
- **HR decision (PIP09).** Allowed when status is `IN_HR_REVIEW` or `DISPUTED` (second review after a dispute). Inserts `decision (decision_type='PIP_HR', pip_id, decision, comment, new_start_date, decided_by)`; stores the three checklist booleans on `pip`.
  - **APPROVE**: requires all three checks and ≥1 goal; `start_date` = proposed date, or HR's new date if the proposed one has passed (new date must be today or later); `status = NOTICE_SENT`, `notice_sent_at = now`; `employee.stage = 'PIP'` (stage cache; the engine is the source of truth, `01-overview.md` §2 rule 4); the employee's `development_plan` in status `ACTIVE` → `PAUSED` (PIP-07/DEV-05; no-op when there is none); `email_outbox` row(s) for `PIP_NOTICE` (PIP-03 — the in-app half is the employee's task + notification, G-04). After the undo window: complete "Approve PIP" with `{PIP:{hrDecision:'APPROVE', startDate}}` → engine raises the employee's response task.
  - **AMEND**: HR has already edited the goals via PIP03–05; comment required. **[ASSUMPTION, D-06-9]** then behaves exactly like APPROVE (FRD: "HR edits the goals, then approves"; FRD §4.2 lists only NOTICE_SENT / REJECTED as resulting statuses).
  - **REJECT**: comment required; `status = REJECTED`. Case output `hrDecision='REJECT'`. What the manager does next is **[OPEN] D-06-10**.
  - After a dispute (`status = DISPUTED`) the same endpoint is used; the second notice is final (PIP-09): the employee's next S4 shows Acknowledge only.
- **Acknowledge (PIP11).** Requires the tick; `acknowledged_at = now`, `status = ACTIVE`; case `PIP.acknowledged = true` → milestone "PIP active". FRD: "the PIP starts on the start date" — the status flips on acknowledgement, the check-in schedule counts from `start_date`.
- **Dispute (PIP12).** Only when `disputed_at is null` (PIP-09; DB keeps a single `dispute_reason`/`disputed_at` pair) and status `NOTICE_SENT`. Writes reason, `disputed_at`, `status = DISPUTED`, `DISPUTE_ATTACHMENT` documents, `email_outbox` `PIP_DISPUTED` (to HR and the manager). Case `PIP.disputed = true` → engine raises "Approve PIP" again for HR.
- **Check-ins (PIP15).** Scheduler-driven (§8): a `checkin (PIP_CHECKIN, pip_id, seq_no = n)` row is created when the n-th check-in falls due (`start_date + n × checkin_days`, while before the final review date) and the "PIP check-in" task is raised with `engine_task.related_entity = 'CHECKIN'`. Submit writes `notes`, `done_by`, `done_at`, one `checkin_goal` per goal, and copies `progress_pct`/`progress_status` onto `goal` (current value vs value at the check — `02-data-model.md` §5). Employee comment (PIP16) writes `checkin.employee_comment` only.
- **Final review (PIP17).** Scheduler creates `checkin (PIP_FINAL, seq_no = 1)` at `start_date + duration_days` and raises "Final PIP review"; `pip.status = FINAL_REVIEW` **[ASSUMPTION]** (status value exists, FRD does not say when it is set). Manager submit writes result per goal, assessment, proposed outcome; `EXTEND` is rejected with "This PIP has already been extended once." on `seq_no = 2` (DB CHECK backs it) and **[ASSUMPTION]** also when `PIP_EXTENSION` is disabled (D-06-14). Case `PIP.proposedOutcome` → engine raises "Confirm PIP outcome" (HR).
- **HR sign-off (PIP18).** Sets `hr_signoff_by/at` (PIP-06). Then by the confirmed outcome:
  - **PASSED** → `pip.outcome = PASSED`, `status = CLOSED`, `closed_at`; `employee.stage = 'ACTIVE'`; `development_plan PAUSED → ACTIVE` (DEV-05 "resumes when the PIP is passed"); `PIP_OUTCOME` e-mail; case `result = PASSED`, `hrConfirmed = true`.
  - **EXTEND** → `pip.status = EXTENDED`; case `extended = true`; the scheduler creates the second final review (`PIP_FINAL`, `seq_no = 2`) at first final date + `extension_days` ("a new final review is set 30 days later"). **[ASSUMPTION]** check-ins continue at `checkin_days` during the extension — FRD silent. No `PIP_OUTCOME` e-mail until the final outcome **[ASSUMPTION]**.
  - **NOT_MET** → `pip.outcome = NOT_MET`, `status = CLOSED`; inserts `employee_exit (exit_type='PIP_NOT_MET', initiated_by_role='SYSTEM', initiated_by = null)`; `PIP_OUTCOME` e-mail; case `result = NOT_MET` → Exit (FRD-07). `employee_exit.notice_date` and `last_working_day` are NOT NULL and FRD-06 does not say how they are set — **[OPEN] D-06-16**. `employee.stage` and the development plan are left for FRD-07's exit confirmation (OFF-01).
- **PIP-10 (built by F08).** When FRD-07's exit confirmation runs for an employee with an open PIP: `pip.outcome = CLOSED_EMPLOYEE_LEAVING`, `status = CLOSED`, open PIP `engine_task`s cancelled (OFF-01). This spec defines the expected end state only.
- **Overdue.** The employee's response task, like every task, gets G-05's reminder 1 day after its due date (existing `OverdueReminderScheduler`); "if there is still no response, HR follows up with the employee personally" — an offline action, nothing to build.

## 8. Case model needs

`03-case-file.md` already lists the **PIP** item with exactly FRD-06 §5's 13 fields (FRD-06: "PIP | 0..1 | 13 | pipId, status, hrDecision, disputed, acknowledged, startDate, checkinDays, durationDays, extensionDays, proposedOutcome, hrConfirmed, extended, result") — **no field change needed**, unlike FRD-02's Probation item.

| Field | Type | Written by | Used by (per `03-case-file.md`) | DB source |
|---|---|---|---|---|
| pipId | long | Spring | link | pip.id |
| status | pip status enum | Spring / task output | guards | pip.status |
| hrDecision | APPROVE / AMEND / REJECT | task output ("Approve PIP") | notice or back to manager | decision (PIP_HR) |
| disputed | boolean | task output | second notice: acknowledge only (PIP-09) | pip.disputed_at |
| acknowledged | boolean | task output | milestone "PIP active" | pip.acknowledged_at |
| startDate | date-time | Spring (HR approval) | base for the check-in and final timers | pip.start_date |
| checkinDays, durationDays, extensionDays | integer | Spring (draft) | timers | pip.* snapshot |
| proposedOutcome | PASSED / EXTEND / NOT_MET | task output ("Final PIP review") | → HR sign-off | checkin (PIP_FINAL) |
| hrConfirmed | boolean | task output ("Confirm PIP outcome") | outcome milestones (PIP-06) | checkin.hr_signoff_at |
| extended | boolean | Spring | EXTEND only once (PIP-05) | v_pip |
| result | PASSED / NOT_MET | Spring | PASSED → "PIP passed"; NOT_MET → Exit | pip.outcome |

**Model elements to add to `employeecase.case`** (none exist; `cmmn-modeler` gate 3 work):
- Stage **"PIP"** (FRD-00 §3: "PIP (only when needed) | HR approves a PIP | Final PIP review is confirmed"), entered on `hrDecision ∈ {APPROVE, AMEND}`, left on `result` set or on Exit.
- Human tasks: **"Approve PIP"** (HR; raised on `status = IN_HR_REVIEW`, and again on `disputed = true`); the employee's response task (Employee; raised on approval — **name not given by the FRD, [OPEN] D-06-12**); **"PIP check-in"** (Manager, repeating); **"Final PIP review"** (Manager); **"Confirm PIP outcome"** (HR). Task names must match these exact strings — they are the `setting` TASK_DUE keys (R-CM-3).
- Milestones: **"PIP active"** (on `acknowledged`); **"PIP passed"** (on `result = PASSED`, named in `03-case-file.md`). NOT_MET hands over to the Exit item/stage (FRD-07).
- Case roles: Manager, HR, Employee — all already defined in `03-case-file.md` §3; nothing new.

**Timers — Spring scheduler, not native CMMN timers.** FRD-06 §5 says "CaseFabric timers for check-ins and the final review, read from the case file", but the E1 spike (`01-overview.md` §5) proved CaseFabric 1.1.35 cannot read a case-file value in `timerExpression`; `03-case-file.md` §1 already applies the fallback to "the PIP check-in/final timers" by name. So: Spring computes due dates from `PIP.startDate` + `checkinDays` / `durationDays` / `extensionDays` (snapshots, ADM-02) and a scheduler (same shape as `OverdueReminderScheduler` / `ManagerTransferScheduler`) creates the `checkin` row and raises the task through the engine REST API. FRD-08's AC "Timer change … running PIPs keep their existing check-in dates" holds automatically because only the snapshot is read.

**Legacy model.** `emp.case` has a "Performance improvement plan (PIP)" stage with tasks "Define PIP goals" etc. and case-file fields like `PIP.goals`, `PIP.employeeEmail` — free text and e-mail addresses in the case file, against R-ARC-3, and a different flow (no HR approval before notice, no check-ins). R-CM-1: reference only; model the stage fresh in `employeecase.*`.

## 9. Data touched

**Tables (all designed in `02-data-model.md` §2.5/§2.7, no new table or column):**

| Table | Applied? | Use |
|---|---|---|
| `pip` | **Yes** (V13) | the plan; status machine DRAFT → IN_HR_REVIEW → (REJECTED \| NOTICE_SENT) → (DISPUTED → HR reviews again → NOTICE_SENT) → ACTIVE → FINAL_REVIEW → (EXTENDED → FINAL_REVIEW) → CLOSED |
| `goal` | **No — must be created** | PIP goals (`pip_id` typed row) |
| `checkin` | **Yes** (V13) | PIP_CHECKIN and PIP_FINAL rows |
| `checkin_goal` | **No — must be created** | per-goal progress/status/result per check-in |
| `decision` | **No — must be created** (not listed as unbuilt in `features/README.md`; verified absent) | PIP_HR decisions (APPROVE/AMEND/REJECT + comment) |
| `document` | Yes (V1) | `PIP_ATTACHMENT`, `DISPUTE_ATTACHMENT` |
| `development_plan` | Yes (V13) | PAUSED / ACTIVE (DEV-05) |
| `employee_exit` | Yes (V13) | `PIP_NOT_MET`, `SYSTEM` |
| `employee` | Yes | `stage` PIP / ACTIVE; current manager |
| `review` | Yes (V13) | read: source review (PROJECT or MANAGER) |
| `setting`, `email_template`, `email_outbox`, `engine_task`, `notification`, `audit_log` | Yes | as everywhere |

**Migration needed when F07 reaches gate 1:** `goal`, `checkin_goal`, `decision` with their indexes, DDL transcribed verbatim from `02-data-model.md` (same approach as V11/V13 — design already approved, nothing invented), plus whatever §12 decisions add. `decision` is shared with F04 (BENCH_CHECK, CONTRACT_RENEWAL) and F05 (PLAN_APPROVAL); `goal`/`checkin_goal` with F05 — whichever feature reaches gate 1 first creates them, the others must check first (`features/README.md` note).

**FRD's own cross-check (FRD-06 §5, quoted):** "OK: goal CHECK: a PIP goal must have support and no category / target date (PIP-02)." · "OK: checkin CHECK: PIP_FINAL seq_no is 1 or 2 and EXTEND is allowed only on the first final review, enforcing "extended once" (PIP-05)." · "OK: pip.dispute_reason / disputed_at are single columns, so a PIP can be disputed only once (PIP-09)." · "OK: pip.outcome includes CLOSED_EMPLOYEE_LEAVING (PIP-10); decision PIP_HR allows APPROVE / AMEND / REJECT with a comment required for AMEND / REJECT." · "OK: checkin.hr_signoff_by / hr_signoff_at record the HR sign-off of the final review (PIP-06)." · "Note: "At least one goal" (PIP-02) and "PIP not visible before notice" (PIP-01) are enforced in Spring." — FRD-06 records **no "Fix" item** for its own tables.

**Gaps this spec found against the applied schema (not in the FRD's cross-check):**
- `v_pip.source` (V13) returns only `PROJECT_REVIEW` / `SKILL_GAP` / `DIRECT`; `02-data-model.md` §2.9 documents `PROJECT_REVIEW / APPRAISAL / SKILL_GAP / DIRECT`. An appraisal-sourced PIP (`source_review_id` → a MANAGER review) is reported as `PROJECT_REVIEW` today — D-06-3.
- `v_pip.final_review_date` = `start_date + duration_days` and `days_left` is computed only for `status = 'ACTIVE'` — neither reflects an extension or the `FINAL_REVIEW`/`EXTENDED` statuses (FRD-08's PIPs report shows "final review date, days left, extensions") — D-06-4. (`v_upcoming_dates`' PIP branch has the same `start_date + duration_days` formula.)
- `document` has no `pip_id`; attachments can be linked to a PIP only through `employee_id` + `doc_type` — D-06-8.
- `pip` has no column for the acknowledgement comment drawn on S4 — D-06-7.

**Settings read (all present, verified):** `PIP_CHECKIN` 14 / `PIP_DURATION` 90 / `PIP_EXTENSION` 30 (DAYS), `UNDO_WINDOW_SECONDS`, `OVERDUE_REMINDER_DAYS`. **Missing:** `TASK_DUE` rows for the five PIP tasks (D-06-12; also `02-data-model.md` §6 open question 4).

**Case file:** the `PIP` item (§8), already documented in `03-case-file.md`; it must be added to `employeecase.type` (gate 3).

## 10. E-mails & timers

| Trigger | Template (code, seeded in V9) | To |
|---|---|---|
| HR approves (Approve or Amend) | **"PIP notice"** (`PIP_NOTICE`) — FRD-08: "HR approves a PIP" | Employee, Manager, HR — confirmed by FRD-08 AC "E-mail goes to the right manager": "the e-mail is sent to Kees, to his manager Anouk Peters and to HR" (V9 matches) |
| Employee disputes | **"PIP disputed"** (`PIP_DISPUTED`) — FRD-08: "An employee disputes a PIP (to HR and the manager)" | HR, Manager (V9 matches) |
| HR confirms the final outcome | **"PIP outcome"** (`PIP_OUTCOME`) — FRD-08: "The final PIP review is confirmed" | Employee, Manager — V9 seeded this as `[ASSUMPTION]`; WF-06's own template data independently shows "Employee, Manager", so it is now corroborated (D-06-19) |
| Every new PIP task (Approve PIP, response, check-in, final review, confirm outcome) | `TASK_ASSIGNED` (G-04, existing) | task owner |
| Task 1 day overdue | `TASK_OVERDUE` (G-05, existing) | task owner |

WF-06's template data also shows a richer "PIP notice" body ("Following our conversation, a Performance Improvement Plan has been set up from {pip_start} to {pip_end}. Please read and acknowledge it by {ack_due}: {task_link}") than V9's placeholder; bodies are HR-editable (FRD-06: "an editable template that HR/legal can adjust"), so this is content, not code — but the renderer must support `{pip_start}`, `{pip_end}`, `{ack_due}`, `{pip_outcome}` if HR uses them **[ASSUMPTION]** (D-06-22).

| Timer (setting) | Default | Goes to | Can be switched off | Mechanism |
|---|---|---|---|---|
| PIP check-in (`PIP_CHECKIN`) | 14 days | Manager | No | Spring scheduler from `PIP.startDate` + `checkinDays` snapshot |
| PIP duration (`PIP_DURATION`) | 90 days | Manager + HR | No | Spring scheduler: first final review at `startDate + durationDays` |
| PIP extension (maximum once) (`PIP_EXTENSION`) | 30 days | Manager + HR | Yes | Second final review at first final date + `extensionDays`; disabled → no Extend option **[ASSUMPTION]** (D-06-14) |
| Overdue reminder e-mail | 1 day after due | Task owner | Yes | existing (G-05) |
| Undo window | 10 seconds | Task owner | No | existing (G-03) |

## 11. Acceptance criteria

From FRD-06 §6 "Acceptance Criteria" (exact wording):

**AC-06-1 (FRD "Employee cannot see a draft PIP")**
```
Given Mark Smit sent a PIP for Kees Willems to HR
When Kees logs in
Then he sees no PIP, task or notification about it
```

**AC-06-2 (FRD "HR approves")**
```
Given HR has the task "Approve PIP · Kees Willems"
When HR chooses Approve & send notice and submits
Then Kees receives the PIP notice by e-mail and a task to respond
And the HR task disappears
```

**AC-06-3 (FRD "Employee acknowledges")**
```
Given Youssef Tahiri has a PIP notice
When he ticks "I have read this plan" and selects Acknowledge
Then the milestone "PIP active" is recorded
And his task disappears
```

**AC-06-4 (FRD "Only one extension")**
```
Given Daan Visser's PIP was extended once
When the manager opens the final review
Then the option "Extend 30 days" is not available
```

**AC-06-5 (FRD "PIP not met")**
```
Given the manager proposes "PIP not met" and HR confirms
Then offboarding starts for the employee
```
(Testable in F07 as: an `employee_exit` row `PIP_NOT_MET`/`SYSTEM` exists and case `PIP.result = NOT_MET`; the rest of offboarding is F08.)

**AC-06-6 (FRD "Dispute only once")**
```
Given Kees Willems disputed his PIP and HR sent the reviewed plan
When Kees opens the PIP notice
Then only Acknowledge is available
```

Cross-module scenarios this feature must also satisfy (quoted from their FRDs):

**AC-06-7 (FRD-08 "E-mail goes to the right manager")**
```
Given the "PIP notice" template has recipients Employee, Manager and HR
When a PIP for Kees Willems is approved
Then the e-mail is sent to Kees, to his manager Anouk Peters and to HR
```

**AC-06-8 (FRD-08 "Timer change")**
```
Given HR changes "PIP check-in" from 14 to 10 days
Then new PIPs get check-ins every 10 days
And running PIPs keep their existing check-in dates
```

**AC-06-9 (FRD-04 "Plan paused during PIP")**
```
Given an employee with an active plan starts a PIP
When the employee opens My Development
Then the plan shows as paused and no progress check tasks are created
```
(F07 asserts `development_plan.status = 'PAUSED'` after HR approval; the My Development screen is F05.)

Derived — **[ASSUMPTION]**, no literal FRD scenario, flag before building:

**AC-06-10 (one open PIP, ux_pip_open)**
```
Given Kees Willems already has a PIP that is not Rejected or Closed
When his manager tries to start another PIP
Then the PIP is not created
```

**AC-06-11 (G-13 / Access gate "never for an HR user's own PIP")**
```
Given an HR user is the subject of a PIP
When that HR user calls the HR decision or HR sign-off for it
Then the request is refused (403)
```

**AC-06-12 (validation, PIP-02)**
```
Given a manager has filled in a draft PIP without any goal
When they select Send to HR
Then the message "Add at least one goal with a measure." is shown
```

**AC-06-13 (validation, HR checklist)**
```
Given HR has ticked two of the three checks
When HR chooses Approve & send notice and submits
Then the message "Confirm all checks before approving." is shown
```

**AC-06-14 (PIP passed resumes the plan)**
```
Given Daan Visser is on a PIP and his development plan is paused
When the manager proposes "PIP passed" and HR confirms
Then his stage is Active again and his development plan is active
And he receives the "PIP outcome" e-mail
```

Every validation message in §5 additionally gets an exact-text test (CLAUDE.md §2.8 item 2).

## 12. Decisions & open items

| ID | Topic | Status |
|---|---|---|
| D-06-1 | HR PIP sign-off page (FRD-00 Appendix A #5, repeated in FRD-06's own TBC) | **[OPEN]** — FRD suggests "Draw them, or state that HR uses the same page with an HR action bar." FRD-06's route table already gives the manager task and the HR task the same route `/pips/:pipId/final-review`, so this spec drafts the **same page with an HR action bar** (`wireframes.md` S7 HR variant: manager's proposal read-only, HR "Confirm outcome"). Confirm before building. Same decision is pending for FRD-02 Screen 13 (D-P4) and FRD-07 Screen 4 — ideally answered once for all three. |
| D-06-2 | `goal`, `checkin_goal`, `decision` are not in the applied schema | **Finding, needs approval at gate 1** — verified absent from every migration. `features/README.md` names `goal`/`checkin_goal` but omits `decision`. Create them verbatim from `02-data-model.md` (shared with F04/F05). |
| D-06-3 | `v_pip.source` lacks `APPRAISAL` | **[OPEN]** — applied V13 view vs `02-data-model.md` §2.9 disagree; the FRD just says "v_pip (source, disputed, extended)". Recommend a view migration that distinguishes `review.review_type` PROJECT vs MANAGER, matching the data-model doc (and the wireframe's "Appraisal" source option). |
| D-06-4 | `v_pip.final_review_date` / `days_left` ignore extensions and non-ACTIVE statuses | **[OPEN]** — affects the already-built PIPs report and `v_upcoming_dates`. Fix in the same view migration as D-06-3, if approved. |
| D-06-5 | PIP source selection on S1 ("Raised from") | **[OPEN]** — WF-06 offers Project review / Development plan · skill gap / Appraisal, no "Direct", although the FRD allows "started directly" and the validation table makes "source" required. Also unspecified: how a specific review/check-in is chosen when S1 is opened from the employee record rather than from the source screen. **[ASSUMPTION]**: when opened from a source screen the source id is passed and locked; from the employee record the source is "Direct" (both FKs null). |
| D-06-25 | "Support offered" required? | **[OPEN]** — PIP-02 and the DB require it; WF-06 draws it optional; the FRD's message names only "goal and measure". Spec makes it required (FRD > wireframe) with the generic "This field is required." |
| D-06-6 | Check-in frequency — manager-chosen or settings only | **[OPEN]** — FRD inputs list it as a Manager input ("Every 14 days (configurable)") and WF-06 draws a required select; G-08 says periods come only from settings and `pip.checkin_days` is described as a settings snapshot. **[ASSUMPTION]**: the select shows only the current `PIP_CHECKIN` value (as WF-06 does), i.e. effectively read-only. |
| D-06-7 | Employee "Your comments" on S4 | **[OPEN]** — drawn in WF-06 and listed in FRD inputs ("Comments"), but no `pip` column exists. Options: drop the field, store it on the dispute only, or add a column (R-DB-1 change). Not built until answered. |
| D-06-8 | Linking attachments to a PIP; evidence upload on S1 | **[OPEN]** — `document` has no `pip_id`; S1 draws no upload control though PIP06 exists. **[ASSUMPTION]**: link by `employee_id` + `doc_type` + upload time within the PIP's life (one open PIP per employee makes this unambiguous for the current PIP only). |
| D-06-9 | What "Amend" does | **[OPEN]** — FRD: "HR edits the goals, then approves"; endpoint result statuses are only NOTICE_SENT / REJECTED; comment "for the manager" is required. **[ASSUMPTION]**: Amend = HR edits goals (S2 on S3), checklist required, notice sent like Approve, comment stored on `decision` and shown to the manager. |
| D-06-10 | What happens after "Reject" | **[OPEN]** — "the draft returns to the manager with HR's comments", but `REJECTED` is excluded from `ux_pip_open` (i.e. treated as finished) and no manager rework task is named. Either the same row returns to DRAFT (needs a status transition the FRD does not list) or the manager starts a new PIP (new row, old one stays REJECTED with HR's comment). |
| D-06-11 | Employee never acknowledges | **[OPEN]** — G-05 reminder, then "HR follows up with the employee personally". Not stated whether check-ins start on `start_date` without an acknowledgement. **[ASSUMPTION]**: timers run from `PIP.startDate` (set at HR approval, `03-case-file.md`), status stays NOTICE_SENT until acknowledged. |
| D-06-12 | Employee response task name; due days for all five PIP tasks | **[OPEN]** — the FRD names "Approve PIP", "PIP check-in", "Final PIP review", "Confirm PIP outcome" but only calls the employee's one "a task to respond" (WF-06 notification: "Improvement plan sent by HR · please respond by 14 Oct"). No TASK_DUE `setting` row exists for any of them (R-CM-3 needs one per task name; `02-data-model.md` §6 Q4). |
| D-06-13 | Can HR disagree with the manager's proposed outcome? | **[OPEN]** — FRD only says HR "signs off"/"confirms"; no reject/override path or message exists. Spec allows confirm only. |
| D-06-14 | `PIP_EXTENSION` switched off / label "Extend 30 days" | **[ASSUMPTION]** — when the setting is disabled, Extend is not offered; the label shows the snapshot `extension_days`. FRD-06: "The maximum of one extension is fixed and not configurable" (the count), while the length is a setting. Confirm. |
| D-06-15 | Check-in progress % input and "Behind" status | **[ASSUMPTION]** — WF-06 shows % as text and omits BEHIND; the FRD ("records progress and status per goal") and the DB support both. Spec: editable % and all four statuses. |
| D-06-16 | `employee_exit` dates on "Not met" | **[OPEN]** — `notice_date` and `last_working_day` are NOT NULL; FRD-06 does not say how they are set. Related: `02-data-model.md` §6 Q7 "Automatic exits: do they skip HR "Confirm exit"?" and `03-case-file.md` Exit.type "[OPEN]". Must be agreed with F08's spec. |
| D-06-17 | Manager rights after a manager transfer | **[ASSUMPTION]** — current manager (WS-02) acts; `pip.manager_id` stays the snapshot author. |
| D-06-18 | Employee view while status = DISPUTED | **[ASSUMPTION]** — PIP-01 is satisfied (notice was sent), so `/me/pip` still shows the plan read-only with neither action until HR's second notice. |
| D-06-19 | `PIP_OUTCOME` recipients | **Resolved** — V9's `[ASSUMPTION]` (Employee, Manager) is corroborated by WF-06's own template data ("PIP outcome", "Final PIP review confirmed", "Employee, Manager"). `PIP_NOTICE` and `PIP_DISPUTED` recipients are confirmed by FRD-08 text/AC. |
| D-06-20 | Legacy `emp.case` PIP stage and humantask files | **Resolved** — reference only (R-CM-1); not reusable because they put free text/e-mail in the case file (R-ARC-3) and model a different flow. |
| D-06-21 | `POST /api/checkins/{id}/submit` shared with FRD-04 | **Resolved (design)** — one endpoint dispatching on `checkin_type`; first of F05/F07 to build creates it. |
| D-06-22 | E-mail placeholders `{pip_start}`, `{pip_end}`, `{ack_due}`, `{pip_outcome}` | **[ASSUMPTION]** — from WF-06's template data, not the FRD; supported by the renderer so HR can use them. |
| D-06-23 | FRD says "CaseFabric timers … read from the case file" | **Resolved** — superseded by the E1 spike result (`01-overview.md` §5): Spring scheduler fallback, as `03-case-file.md` §1 already states for the PIP timers. |
| D-06-24 | "Manager sign-off" / "HR sign-off" drawn as text inputs | **Resolved (judgment)** — recorded as `checkin.done_by` / `hr_signoff_by` (employee ids, the logged-in user), shown read-only; no free-text signature column exists. |

## 13. Definition of done

- [ ] D-06-1, D-06-3, D-06-4, D-06-5, D-06-6, D-06-7, D-06-8, D-06-9, D-06-10, D-06-11, D-06-12, D-06-13, D-06-16, D-06-25 answered (and the [ASSUMPTION] rows confirmed) by the user (CLAUDE.md §2.9.5 "ask when blocked"); spec status → **Spec ready**.
- [ ] Dependencies in place or explicitly stubbed: F04 (project review source), F05 (development plan pause/resume, shared `goal`/`checkin_goal`/`checkin` submit endpoint), F06 (appraisal source). F08 implements PIP-10.
- [ ] Gate 1: Flyway migration for `goal`, `checkin_goal`, `decision` (verbatim from `02-data-model.md`, if not already created by F04/F05), the TASK_DUE `setting` rows (D-06-12), and any view fix approved under D-06-3/D-06-4; each new constraint has a negative test.
- [ ] Gate 2: PIP01–PIP18 with server-side role/ownership/G-13 checks (allowed role succeeds, forbidden role gets 403), undo window on task-completing calls, outbox e-mails, audit log, scheduler for check-ins and final reviews.
- [ ] Gate 3: `employeecase.case`/`.type` extended with the PIP item, stage, five tasks and two milestones (§8).
- [ ] Gate 4: S1–S7 per `wireframes.md`, R-UI-1…R-UI-9; Start PIP button on the employee record (FRD-01 D-W8) and the My Team "PIP" filter (`TeamService`) wired.
- [ ] Every acceptance criterion in §11 has an automated test; every §5 message has an exact-text test.
- [ ] Gate 5 review passed; status set to **Done** in `docs/features/README.md`.
