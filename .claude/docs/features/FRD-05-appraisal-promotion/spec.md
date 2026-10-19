# FRD-05 — Appraisal and promotion

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-05/FRD-05_Appraisal_and_Promotion.docx` (primary), `FRD-00/FRD-00_Application_Overview.docx` (roles, permissions matrix, G-rules, case-file inventory, Appendix A), `FRD-08/FRD-08_Reports_and_Settings.docx` (appraisal forms, e-mail templates, timers this flow reads), `FRD-03`/`FRD-04` docx (the evidence this flow reads), `FRD-06` docx (the PIP an "Under-performing" outcome may lead to). The legacy v1.1 monolithic PDF is superseded — see `.claude/docs/source/README.md`. |
| Wireframes | `.claude/docs/source/FRD-05/WF-05_Appraisal_and_Promotion.html` (v1.2, canonical) — 9 pages + 1 modal, all confirmed present by direct read: `p-hr-apr`, `p-mg-apr`, `p-hr-apr-open`, `p-em-apr-self`, `p-em-peer`, `m-decline-peer`, `p-mg-peer-approve`, `p-mg-apr-review`, `p-hr-apr-final`, `p-em-reviews`. Redrawn in `wireframes.md`. |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-05 wireframe HTML > legacy v1.1 (R-SRC-1). Conflicts are listed in §12. |
| Relationship to feature builds | **F06 "Appraisal & promotion"** in `.claude/docs/features/README.md` — **Not started**, depends on **F04** (projects, project reviews, client feedback = evidence) and **F05** (KPIs, development plan = evidence). Nothing of the FRD-05 flow itself is built. What already exists and this spec builds on rather than redoes: the `appraisal_form`/`form_section`/`form_question` tables and 3 seeded forms (`V11`/`V12`, F09); the HR appraisal-form editor (F09, C14–C24 `/api/settings/appraisal-forms…`); the `appraisal_round`/`appraisal`/`review` tables and the `v_appraisal_status` view (`V13`, applied early for F09's Appraisal status report C38/C39); the 4 appraisal e-mail templates (`V9`). **`review_answer` is still unbuilt** (verified: no migration V1–V14 creates it). FRD-01/F03 explicitly deferred the appraisal parts of the dashboards, the "Appraisals"/"My Reviews" menu items and the reviews list of the employee record's Development & reviews tab to F06 (`FRD-01-shared-workspace/spec.md` §2, §5 W1–W3/W8). |

## 0. Status & sources

FRD-05 v1.2 (`FRD-05/FRD-05_Appraisal_and_Promotion.docx`, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026") describes one flow — "One fair, evidence-based company-wide appraisal round per year, with promotion decisions" — in 10 screens across 7 feature groups (§3 "Screens", §4.1–§4.7 "Features, Endpoints & Database"), one validation table, nine business invariants APR-01…APR-09, a full column-level schema for every table it touches with its own cross-check against `V1__init.sql` (2 "Fix" items, both carried into §12), and 8 acceptance scenarios plus one Appendix A item (#6).

This is a **full-scope** spec: unlike FRD-02, no part of FRD-05 has been built by any earlier feature, so every section below is written in full detail rather than pointing at an earlier spec.

Verified against the repository on 2026-10-19 (not assumed):
- **Schema.** `V13__create_report_tables_and_views.sql` creates `appraisal_round` (incl. `ux_one_open_round`), `appraisal` (incl. the APR-09 and promotion all-or-nothing CHECKs), `review` (incl. `ux_review_self_mgr`, `ux_review_peer`) and `v_appraisal_status`; `V11`/`V12` create and seed the forms. DDL matches FRD-05 §5 column by column. **Not applied anywhere:** `review_answer`, `kpi`, `kpi_result`, `goal`, `v_kpi_result`, `v_kpi_score`, `v_client_feedback_by_employee`, and — unexpectedly — **`v_contract`** (documented in `02-data-model.md` §2.9 and referenced by `V1__init.sql`'s own comment on `contract.probation_months`, but never created; F01 computes the probation end date in Java, `OnboardingService`). See §9.
- **Backend.** Only F09 code touches this area: `AppraisalFormSettingsController` (`/api/settings/appraisal-forms…`, HR only) and `ReportController` C38/C39 (`/api/reports/appraisal-status…`, reads `v_appraisal_status`). No appraisal-round, review or promotion code exists.
- **Case model.** The deployed `employeecase.case`/`.type` declare only `Employee` and `Onboarding` — **no `Appraisal` item, no appraisal tasks, no "Employee promoted" milestone**. `employeecase.caseteam` already declares the **`Peer`** role. The legacy `emp.case` has an "Appraisal cycle" stage with a different, superseded design (`Appraisal.selfScore/managerScore/clientScore/finalScore`) — read-only per R-CM-1, not reused.

Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Where the wireframe shows something the FRD does not back, it is flagged in §12 rather than adopted.

## 1. Scope

**In scope (FRD-05's own "IN-SCOPE" list)**
- HR opens an appraisal round (S3).
- Self review and requesting peers (S4).
- Peer review, including declining (S5, S6).
- Manager review with evidence (S8), plus the manager's Change peers action (S7).
- HR finalises the result, including promotion (S9) and the scheduled job that applies a promotion on its effective date.
- Tracking the round and closing it (S1 HR, S2 Manager), with reminders and Excel export.
- My Reviews for the employee (S10).
- **Cross-module additions FRD-01/F03 deferred to this feature** (additive only, not redesigned here): the "Appraisals" (HR, Manager) and "My Reviews" (Employee) menu items; the HR dashboard's "Appraisal round progress" tile, the Manager dashboard's "Reviews to write" count (appraisal part), the Employee dashboard's "Appraisal" status tile (`FRD-01-shared-workspace/spec.md` §5 W1–W3); the appraisal rows of the employee record's "Development & reviews" tab (W8).

**Out of scope**
- Salary changes — "not in this application" (FRD-05 "OUT-OF-SCOPE"; APR-08; G-09).
- The appraisal-form editor (FRD-08, built by F09) — this spec only reads `ACTIVE` forms.
- The Appraisal status **report** (FRD-08 Screen 5, built by F09 as C38/C39) — this spec owns the round-tracking screens S1/S2, which read the same view; any change F06 makes to `v_appraisal_status` must keep C38/C39 passing.
- Producing the evidence: project reviews and client feedback (FRD-03 → F04), KPIs/KPI results/development plan (FRD-04 → F05). This spec only reads them.
- Starting the PIP after "Under-performing" (FRD-06 → F07) — this spec only notifies the manager.
- Probation reviews (FRD-02) — same `review`/`review_answer` tables, different `review_type`; see `FRD-02-onboarding-probation/spec.md`.

## 2. Roles & permissions

From FRD-00 §2 ("Open and finalise appraisal rounds": HR only; "Write reviews": HR Finalise / Manager "Manager review" / Employee "Self and peer review") and FRD-05 §3 "Access gate" (quoted: "Screens 1, 3 and 9 are HR-only; an HR user cannot finalise their own appraisal (APR-09). Screens 2, 7 and 8 are for the manager, own team only. Screens 4 and 10 are the employee's own appraisal. Screens 5–6 open only for the nominated peer. Peer names are hidden from the employee and shown to the manager and HR (APR-03).").

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| Create, start, close a round (S3, S1) | Yes | No | No | No |
| Round tracking table (S1 all / S2 own team) + export | All | Own team (WS-02) | No | No |
| Send reminders from the tracking table | Yes (any row) | Own team only | No | No |
| Write own self review, choose peers (S4) | Own record (G-13) | Own record (G-13) | Own record | Own record (G-13) |
| Write / decline a peer review (S5, S6) | Only when nominated | Only when nominated | Only when nominated | Only when nominated |
| Change peers (S7) | No (not in FRD) | Own team only | No | No |
| Manager review (S8) | No | Own direct report only | No | No |
| See peer names | Yes | Yes (own team) | **No** — anonymous (APR-03) | No |
| Consolidate & finalise, record promotion (S9) | Yes, **never own appraisal** (APR-06, APR-09) | No | No | No |
| My Reviews (S10) | Own | Own | Own | Own |

"Every user is also an employee" (FRD-00 §2, G-13): HR users, managers and IT admins are included in a round like anyone else and see their own S4/S10. G-13 applied here: an employee never chooses themselves as a peer (validation), a peer review is never written about oneself, a manager review is written by the employee's manager (never the subject), and APR-09 is enforced in the database (`check (finalised_by is null or finalised_by <> employee_id)`) **and** on the server (R-API-2).

## 3. Business rules

Copied from FRD-05 §4 "Hard Business Invariants" by ID (R-SRC-3 — wording kept exact). All MUST.

| ID | Rule |
|---|---|
| APR-01 | There is one company-wide appraisal round at a time, opened by HR. **(DB: `ux_one_open_round`, applied in V13.)** |
| APR-02 | Employees who joined after the eligibility date, are on probation, are on a PIP or are in offboarding are excluded. |
| APR-03 | Peer feedback is anonymous for the employee. The manager and HR see peer names. |
| APR-04 | The manager review becomes available when the self review is submitted, or on the self-review due date if it was not submitted. It then shows "Self review not submitted". |
| APR-05 | Evidence is collected automatically; managers do not need to contact clients again. |
| APR-06 | Only HR can finalise an appraisal and record a promotion. |
| APR-07 | A round can only be closed when all included appraisals are finalised. |
| APR-08 | Salary is not recorded or changed in this application. |
| APR-09 | An HR user cannot finalise their own appraisal; another HR user does it (G-13). **(DB: `check (finalised_by is null or finalised_by <> employee_id)`, applied in V13.)** |

"Global rules G-01 … G-13 (FRD-00, section 4) also apply to this module." Most relevant: G-02 (server-side access), G-03 (task "Open" action + undo window), G-04 (every new task → in-app + e-mail), G-05 (due-soon orange / overdue red, overdue e-mail), G-06 (History), G-11 (save toast; Cancel never saves), G-12 (dd-mm-yyyy), G-13.

Process rules from FRD-05 §2 "Happy path — Main flow" and "Other paths" (quoted, not paraphrased):
1. "HR selects "+ New Round", enters the round settings and selects "Start round"."
2. "Every included employee gets a "Self review" task and an e-mail (template "Appraisal round opened")."
3. "The employee completes the self review and chooses 2 or 3 peers. Peers receive a "Peer review" task straight away."
4. "Each peer completes the peer review or declines with a reason. On decline the employee and manager are notified so another peer can be chosen."
5. "When the self review is submitted, or on the self-review due date if it was not submitted, the manager gets a "Manager review" task." The page shows "all evidence from the review period": "project reviews, client feedback (stars, average score and number of reviews), KPI score, peer reviews (with names, for the manager), self review, development plan result."
6. "The manager rates each question, writes a summary, chooses a recommendation (Promote, Meets expectations, Below expectations) and submits to HR."
7. "HR opens "Consolidate & finalise", sees self, peer and manager scores side by side, and chooses the outcome:" — "Promoted — HR enters new job title, grade, effective date and when to tell the employee. The milestone "Employee promoted" is recorded and the new title applies from the effective date." / "No change — the appraisal is closed as "Meets expectations"." / "Under-performing — the manager is notified and may start a PIP (FRD-06)."
8. "HR writes a final summary and selects "Finalise appraisal". The employee is notified (template "Appraisal finalised") and can read the summary in My Reviews."
9. "HR closes the round when all appraisals are finalised."
10. Other paths: "Change peers. The manager can replace a peer from the Appraisals page. The new peer is notified." · "Reminders. HR and managers can send reminders from the tracking tables." · "Cancel. Cancel on the finalise page leaves the appraisal unchanged."
11. Constraint: "Appraisals not finalised by the round end date are shown as Pending / Overdue." (FRD-05 §1 "Constraints & Dependencies"; "round end date" not pinned to a column — D-05-17.)

Pre-conditions (FRD-05 §2): "At least one active appraisal form exists (FRD-08)." — met by `V12` (3 `ACTIVE` forms). "Only active employees are included (see APR-02)."

## 4. Screens

Routes and wireframe ids are FRD-05 v1.2 §3 "Screens" verbatim.

| # | Screen | Who | Route | Wireframe page | FRD |
|---|---|---|---|---|---|
| S1 | Appraisal round tracking (HR, all) | HR | `/appraisals` · menu "Appraisals" | `p-hr-apr` | Screen 1 · §4.1 |
| S2 | Appraisal tracking (Manager, own team) | Manager | `/appraisals` · menu "Appraisals" (same route, role-dispatched) | `p-mg-apr` | Screen 2 · §4.1 |
| S3 | New appraisal round | HR | `/appraisals/rounds/new` · "+ New Round" on S1 | `p-hr-apr-open` | Screen 3 · §4.1 |
| S4 | Self review with peer choice | Employee (own) | `/me/appraisals/:appraisalId/self-review` · task "Self review"; My Reviews | `p-em-apr-self` | Screen 4 · §4.2 |
| S5 | Peer review | Nominated peer | `/peer-reviews/:reviewId` · task "Peer review" | `p-em-peer` | Screen 5 · §4.3 |
| S6 | Decline peer review (pop-up) | Nominated peer | Modal on S5 · "Decline" | `m-decline-peer` | Screen 6 · §4.3 |
| S7 | Change peers | Manager (own team) | `/appraisals/:appraisalId/peers` · row action "Change peers" on S2 | `p-mg-peer-approve` (drawn as an approval — FRD Appendix A #6, D-05-3) | Screen 7 · §4.4 |
| S8 | Manager review with evidence | Manager (own direct report) | `/appraisals/:appraisalId/manager-review` · task "Manager review"; S2 | `p-mg-apr-review` | Screen 8 · §4.5 |
| S9 | Consolidate & finalise | HR (not own) | `/appraisals/:appraisalId/finalise` · HR task; S1 row action "Finalise" | `p-hr-apr-final` | Screen 9 · §4.6 |
| S10 | My Reviews | Employee (own) | `/me/reviews` · menu "My Reviews" | `p-em-reviews` | Screen 10 · §4.7 |

Toasts drawn in WF-05 (G-11, R-UI-4), adopted as-is except where the FRD gives other wording: "Round started · 44 employees notified" (count is live), "Draft saved", "Self review submitted", "Peer review submitted", "Declined · Lotte and her manager are notified", "Peers updated · new peer notified", "Review submitted to HR", "Appraisal finalised · Ruben Bakker promoted", "Reminder sent to {name}" / "Reminder sent to peers", "Export downloaded (.xlsx)". **Not adopted:** the Close-round toast "All appraisals must be finalised first" — the FRD's validation table gives "All appraisals must be finalised before closing the round." and the FRD wins (R-SRC-1, D-05-22).

## 5. Fields & validation

Messages are FRD-05 §4 "Validation Table" verbatim (R-SRC-3). Rating questions on every review form are rendered from the round's form (`form_section` filtered by `by_self`/`by_peer`/`by_manager`, then its `form_question`s), never hard-coded — the question texts drawn in WF-05 ("Job knowledge", "Quality of work", "Initiative", "Teamwork", "Communication"…) are example content only; the seeded forms (`V12`) currently contain different, fewer questions (D-05-8).

### S3 — New appraisal round (HR)

| Field | Column | Rule | Message |
|---|---|---|---|
| Round name * | `appraisal_round.name` (unique) | Required | "This field is required." · duplicate name: **[OPEN]** no FRD-05 message; FRD-08's "This name already exists." covers form/template names only (D-05-19) |
| Review period * (from → to) | `review_period_start`, `review_period_end` | Required; end after start (DB CHECK) | "This field is required." · end ≤ start: **[OPEN]** no FRD message |
| Review form * | `standard_form_id` (+ `leadership_form_id`, nullable) | Required; only `ACTIVE` forms offered | "This field is required." · wireframe offers one select (Standard / Leadership) while the schema has two columns — D-05-7 |
| Self & peer reviews due * | `self_peer_due` | Required | "This field is required." |
| Manager reviews due * | `manager_due` | Required | "This field is required." |
| HR finalise by * | `hr_due` | Required | "This field is required." |
| Due dates (order) | CHECK `self_peer_due < manager_due and manager_due < hr_due` | Self & peer due < manager due < HR due | **"Due dates must follow the order: self and peer, manager, HR."** |
| Included employees | `eligibility_date` | Wireframe: "All active employees (n)", "Exclude joiners after {date}", "Exclude employees in offboarding" checkboxes | APR-02 makes all exclusions mandatory; see D-05-6 |
| Peer reviews * | `peer_mode` REQUIRED / OPTIONAL / OFF | Required | Wireframe labels: "Required · 2–3 peers", "Optional", "Off" (D-05-5) |
| Peer anonymity * | `peer_anonymous` (default true) | Required | Wireframe labels: "Anonymous to employee", "Visible" — "Visible" conflicts with APR-03 (D-05-5) |

Buttons: Cancel (red outline, left) · Save draft · Start round (green, right).

### S4 — Self review with peer choice (Employee)

| Field | Storage | Rule | Message |
|---|---|---|---|
| Rating per question (1–5, legend Below / Meets / Exceeds) | `review_answer.rating` | Every question rated | **"Rate every question before submitting."** |
| Comment per question (optional) | `review_answer.comment` | – | – |
| Key achievements * | **[OPEN] D-05-8** (TEXT question of the form, per `02-data-model.md` §6 #12) | Required | **"This field is required."** |
| Areas to develop | same (D-05-8) | Optional | – |
| Ask peers for feedback (chips, "×" to remove, "Add peer" search) | `review` rows `review_type='PEER'` | 2 or 3 peers; not themselves; not their manager (candidates from AP10) | **"Choose 2 or 3 colleagues as peers."** |

Buttons: Cancel (left) · Save draft · Submit self review (right).

### S5 — Peer review (Peer)

| Field | Storage | Rule | Message |
|---|---|---|---|
| Rating per question | `review_answer.rating` | Every question rated | **"Rate every question before submitting."** |
| Comment per question | `review_answer.comment` | Optional | – |
| What should {name} keep doing? * | D-05-8 | Required | "This field is required." — **[ASSUMPTION]**: the FRD validation table has no row for it; the wireframe marks it `*` and the generic required message is used, same as "Key achievements" |
| What could {name} do better? | D-05-8 | Optional | – |

Buttons: Decline (red outline, left → S6) · Submit peer review (right).

### S6 — Decline peer review (pop-up)

| Field | Column | Rule | Message |
|---|---|---|---|
| Reason * | `review.decline_reason` (DB: `status <> 'DECLINED' or decline_reason is not null`) | Required | **"Add a reason for declining."** |

### S7 — Change peers (Manager)

| Field | Storage | Rule | Message |
|---|---|---|---|
| Per current peer: Keep / Replace (wireframe) | `review.status='REPLACED'`, `replaced_by_id` → new PEER row | Replacement must be an active colleague, not the employee, not the manager [ASSUMPTION — same candidate rule as AP10] | "Choose 2 or 3 colleagues as peers." if the result would leave fewer than 2 or more than 3 active peers [ASSUMPTION] |
| Add a peer (wireframe) | – | **[OPEN] D-05-3** — FRD §4.4 has only "Replace a peer", no add | – |
| "Worked together on" column (wireframe) | – | **[OPEN] D-05-3** — not in FRD §4.4's table list (`review (PEER), employee`) | – |

### S8 — Manager review (Manager)

| Field | Storage | Rule | Message |
|---|---|---|---|
| Rating per question, with "Self n · Peers n.n" shown per question | `review_answer.rating` | Every question rated | **"Rate every question before submitting."** |
| Comment per question | `review_answer.comment` | Optional | – |
| Overall summary * | D-05-8 | Required | **"Add an overall summary."** |
| Recommendation: Promote ("Propose new title") / Meets expectations ("No change") / Below expectations ("HR may start PIP") | `review.outcome` PROMOTE / MEETS / BELOW (DB: required when MANAGER review is SUBMITTED) | Required [ASSUMPTION — DB CHECK; no FRD message] | **[OPEN]** no FRD message; "Propose new title" has no column (D-05-20) |
| Header line "self review ✔ · peer reviews x / y ✔", or "Self review not submitted" (APR-04) | derived | read-only | "Self review not submitted" (APR-04, exact) |
| Evidence panel (read-only): project reviews, client feedback (stars, average, count), KPI score, peer reviews **with names**, self review, development plan result — "from the review period" | §9 | read-only (APR-05) | – |

Buttons: Cancel (left) · Save draft · Submit to HR (right).

### S9 — Consolidate & finalise (HR)

| Field | Column | Rule | Message |
|---|---|---|---|
| Scores table: per question, Self / Peers (avg) / Manager | derived from `review_answer` | read-only | – |
| Summary line: manager recommendation, KPIs on target, client feedback count · avg | derived | read-only | – |
| Final outcome: Promoted ("Process promotion") / No change ("Meets expectations") / Under-performing ("Start PIP") | `appraisal.outcome` PROMOTED / NO_CHANGE / UNDER_PERFORMING | Required | **"Choose an outcome and add a summary."** |
| Final summary for employee * | `appraisal.final_summary` | Required | **"Choose an outcome and add a summary."** (one combined message, same treatment as FRD-02's D-P3) |
| New job title * (when Promoted) | `promo_job_title_id` → `job_title` | Required when Promoted | **"Complete the promotion details."** |
| New grade * | `promo_grade` JUNIOR / MEDIOR / SENIOR / SENIOR_I / SENIOR_II | Required when Promoted | same |
| Effective date * (dd-mm-yyyy) | `promo_effective_date` | Required when Promoted | same |
| Notify employee * — "After meeting" / "Immediately" | `promo_notify_mode` AFTER_MEETING / IMMEDIATELY | Required when Promoted (DB: all four promo columns all-or-nothing with PROMOTED) | same · behaviour of AFTER_MEETING: D-05-10 |
| Inputs panel: manager review, client feedback entries, "Peer reviews (n) · Anonymous summary", self review | derived | read-only | – |

Buttons: Cancel (left — "leaves the appraisal unchanged") · Finalise appraisal (right).

### S1 / S2 / S10 (read screens)

- **S1 (HR):** header "{round name} · Open · {dates} · {n} employees", "Close round" and "+ New Round" buttons; tiles Self reviews x / y %, Peer reviews x / y %, Manager reviews x / y %, Finalised x / y + promotions count; Result filter (All / To finalise / In progress / Promoted), search, export; columns Employee (+ manager), Self, Peers (x / y), Manager, Client feedback (avg · reviews), Result, Action (Remind / Finalise). Close round when not all finalised → **"All appraisals must be finalised before closing the round."**
- **S2 (Manager):** header "{round name} · My team · closes {date}"; tiles Self reviews, Peer reviews, My reviews (x / y, due date); "My review" filter (All / To write / Waiting / Submitted), search; columns Employee, Self, Peers, My review, Action (Change peers / Write review / View / Remind). No export icon drawn; R-UI-2 requires one — **[ASSUMPTION]** AP05 serves it, role-scoped (same reasoning as FRD-01's W06).
- **S10 (Employee):** "Received reviews" (project reviews, finalised appraisals with result, client feedback — D-05-21) and an "{round name}" card: Self review status, Due, Peers (names of the employee's *own* nominations — not anonymised, the employee chose them), "Start self review" button. Peer feedback shown **without names** (APR-03).

## 6. API

Conventions: base path `/api`, camelCase JSON, dates `yyyy-MM-dd` (R-API-1); errors per R-API-3; optimistic locking `409 STALE_VERSION` on every update (R-DB-5); every state change audited (R-API-5). Endpoints are FRD-05 v1.2 §4.1–§4.7 verbatim with the `/api` prefix added (same adaptation as FRD-02 P01–P04). Numbering prefix **AP** (unique to this FRD).

```
400 {"code":"VALIDATION_FAILED","errors":[{"field":"managerDue","message":"Due dates must follow the order: self and peer, manager, HR."}]}
400 {"code":"VALIDATION_FAILED","errors":[{"field":"peers","message":"Choose 2 or 3 colleagues as peers."}]}
403 {"code":"FORBIDDEN"}   404 {"code":"NOT_FOUND"}   409 {"code":"STALE_VERSION"}
```

| # | Method & path | Role | FRD purpose · tables | Responses |
|---|---|---|---|---|
| AP01 | `GET /api/appraisal-forms?status=ACTIVE` | HR | "Forms for the round" · `appraisal_form`. Distinct from F09's HR settings endpoint C14 (`/api/settings/appraisal-forms`); a read-only, `ACTIVE`-only list | `200 [{id,name,usedFor}]` |
| AP02 | `POST /api/appraisal-rounds` | HR | "Save round settings; due dates must be in order" · `appraisal_round (DRAFT)` | `201 {id}` / `400` (required fields, due-date order) |
| AP03 | `POST /api/appraisal-rounds/{id}/start` | HR | "Start round; one open round (APR-01); creates appraisal and self review per eligible employee (APR-02)" · `appraisal_round (OPEN), appraisal, review (SELF), employee, v_contract, pip, employee_exit, email_outbox (APPRAISAL_ROUND_OPENED)` · case file `Appraisal (roundId, appraisalId, formId, selfDueDate)` | `200 {included}` / `409` another round is OPEN (APR-01; code name [ASSUMPTION]) / `409` round not DRAFT |
| AP04 | `GET /api/appraisal-rounds/current/status?result&q&sort&page&size` | HR (all); Manager (own team, WS-02) | "Tracking table: self, peers x of y, manager, client average, result; Pending / Overdue" · `v_appraisal_status`. Serves S1 and S2 (role-scoped server-side; manager variant adds "My review" state) | `200 {round, tiles, rows[]}` / `404` no open round |
| AP05 | `GET /api/appraisal-rounds/current/status/export?...` | HR; Manager (own team) | Same filters, `.xlsx` (R-UI-2). FRD writes it as "`/current/status · /export`" — the exact path of the export sibling is **[ASSUMPTION]** | `200` xlsx |
| AP06 | `POST /api/appraisal-rounds/{id}/reminders` | HR (any row); Manager (own team rows only) | "Send reminders for selected rows" · `notification, email_outbox`. Body `{appraisalIds[]}` [ASSUMPTION]; recipient and template D-05-12 | `200 {sent}` / `403` row outside own team |
| AP07 | `POST /api/appraisal-rounds/{id}/close` | HR | "Only when all appraisals are finalised (APR-07)" · `appraisal_round (CLOSED), appraisal` | `200` / `409 {"message":"All appraisals must be finalised before closing the round."}` |
| AP08 | `GET /api/reviews/{id}` | The review's `reviewer_id` (SELF: the employee; PEER: the nominated peer; MANAGER: the manager — WS-02 re-checked) | "Form with sections and questions" · `review, review_answer, form_section, form_question` — sections filtered by `by_self`/`by_peer`/`by_manager` per `review_type` | `200` / `403` / `404` |
| AP09 | `PUT /api/reviews/{id}` | same as AP08 | "Save draft" · `review_answer` (self, peer and manager reviews all use it, FRD §4.2/§4.3/§4.5) | `200` / `409 STALE_VERSION` / `409` review already SUBMITTED, DECLINED or REPLACED [ASSUMPTION] |
| AP10 | `GET /api/employees/peer-candidates?q` | Employee (for own appraisal); Manager (S7) | "Active colleagues, not self, not own manager" · `employee` (resolves `02-data-model.md` §6 #8) | `200 [{id,name}]` |
| AP11 | `POST /api/appraisals/{id}/peers` | Employee (own appraisal only, G-13) | "Choose 2 or 3 peers; tasks created at once" · `review (PEER, REQUESTED, nominated_by), email_outbox (PEER_REVIEW_REQUEST)` · case file `Appraisal.pendingPeerReviewId` (name: D-05-2) | `201` / `400` "Choose 2 or 3 colleagues as peers." / `409` round `peer_mode='OFF'` [ASSUMPTION] |
| AP12 | `POST /api/reviews/{id}/submit` | reviewer of that review | One endpoint, three uses: self ("Submit self review; triggers manager review" · `review (SUBMITTED, submitted_at)` · case file `Appraisal.selfSubmitted`), peer ("Submit" · `review (SUBMITTED)`), manager ("Submit to HR with Promote / Meets / Below" · `review (outcome)` · case file `Appraisal.recommendation`) | `202 {undoUntil}` (R-API-6) / `400` "Rate every question before submitting." · "This field is required." · "Add an overall summary." |
| AP13 | `POST /api/reviews/{id}/decline` | the nominated peer | "Decline with reason; notify employee and manager" · `review (DECLINED, decline_reason), notification` | `202 {undoUntil}` / `400` "Add a reason for declining." / `409` not a PEER review in REQUESTED/DRAFT [ASSUMPTION] |
| AP14 | `GET /api/appraisals/{id}/peers` | Manager (own team) | "Current peers and status" · `review (PEER), employee` | `200 [{reviewId,peerId,name,status}]` |
| AP15 | `POST /api/reviews/{id}/replace` | Manager (own team) | "Replace a peer; new peer notified" · `review (REPLACED, replaced_by_id; new PEER row)`. Body `{newPeerId}` [ASSUMPTION] | `201 {newReviewId}` / `400` / `409` review already SUBMITTED [ASSUMPTION] |
| AP16 | `GET /api/appraisals/{id}/manager-review` | Manager (own direct report) | "Form plus evidence from the review period; "Self review not submitted" when due date passed (APR-04, APR-05)" · `review (MANAGER, SELF, PEER with names, PROJECT), review_answer, project_assignment, v_client_feedback_by_employee, v_kpi_score, v_kpi_result, development_plan` | `200` / `403` / `409` manager review not yet available (APR-04) [ASSUMPTION] |
| AP17 | `GET /api/appraisals/{id}/consolidation` | HR (not own, APR-09) | "Self, peer and manager scores side by side" · `appraisal, review, review_answer` | `200` / `403` own appraisal |
| AP18 | `POST /api/appraisals/{id}/finalise` | HR (not own, APR-06/APR-09) | "Promoted (title, grade, date, notify mode), No change, or Under-performing (manager notified, may start PIP)" · `appraisal (outcome, final_summary, finalised_by, promo_*), email_outbox (APPRAISAL_FINALISED), notification` · case file `Appraisal.outcome` | `202 {undoUntil}` / `400` "Choose an outcome and add a summary." · "Complete the promotion details." / `403` own appraisal / `409` already FINALISED |
| AP19 | `GET /api/me/reviews` | any user (own) | "Finalised appraisals and received reviews; peer names removed" · `appraisal, review, review_answer` | `200` |
| — | Scheduled job (not an endpoint) | system | "Scheduled job on promotion effective date — New title and grade apply; milestone "Employee promoted"" · `employee (job_title_id, grade), appraisal (promo_applied_at)` | – |

**Gaps in the FRD's own endpoint list** (not added — R-API-4 requires the spec to name them first, so each is an open item): no `GET`/`PUT` for a saved DRAFT round, although S3 has "Save draft" (D-05-19); no endpoint for the manager's "Add a peer" (D-05-3); S10's client-feedback entries are not in AP19's table list (D-05-21).

## 7. Backend behaviour

Same request flow as every feature (`01-overview.md` §4): authorise (role, ownership, G-13) → validate (exact messages) → one DB transaction (business rows + `audit_log` + `email_outbox`) → commit → engine calls (retried, never rolled back silently, R-ARC-5).

- **Save round (AP02).** Inserts `appraisal_round` with `status='DRAFT'`. Due-date order checked in the service first (exact message), backed by the DB CHECK.
- **Start round (AP03).** Rejects if another round is `OPEN` (APR-01, `ux_one_open_round` as backstop). Selects included employees: `employee.stage='ACTIVE'` ("Only active employees") and **not** (contract start after `eligibility_date`) **and not** on probation (probation end date > today — `v_contract` is not applied, see §9) **and not** with an open `pip` **and not** with a live `employee_exit` / stage `OFFBOARDING` (APR-02). In one transaction: round → `OPEN`, `started_at`, `started_by`; one `appraisal` per included employee (`OPEN`); one `review` `SELF` per appraisal (`reviewer_id` = employee, `form_id` = the round's form for that employee — D-05-7, `due_date` = `self_peer_due` [ASSUMPTION — the round's "Self & peer reviews due" date]); one `email_outbox` row `APPRAISAL_ROUND_OPENED` per employee. After commit, per employee: write case file `Appraisal {roundId, appraisalId, formId, selfDueDate, selfSubmitted:false}` (absolute date-time, `03-case-file.md` §1) — the model raises "Self review". Fan-out is per case; a failed engine call for one employee is retried without affecting the others.
- **Save draft (AP09).** Upserts `review_answer` rows (one per question; `rating` / `yes_no` / `text_answer` per `answer_type`, plus `comment`); sets `review.status` `REQUESTED → DRAFT` for a peer review on first save [ASSUMPTION]. No engine call.
- **Choose peers (AP11).** Validates 2–3 distinct active colleagues, not self, not own manager. Inserts one `review` per peer: `review_type='PEER'`, `status='REQUESTED'`, `nominated_by` = employee, `reviewer_id` = peer, `due_date` = `self_peer_due` [ASSUMPTION]; `email_outbox` `PEER_REVIEW_REQUEST` per peer. After commit, per peer: add the peer to the case team as **Peer** (`03-case-file.md` §3; E5 proven), set `Appraisal.pendingPeerReviewId`, raise the "Peer review" task. Peers are processed one at a time because the case file holds a single `pendingPeerReviewId`. No manager approval task (AC-05-02, Appendix A #6).
- **Submit (AP12).** Validates every rated question ("Rate every question before submitting.") and required text fields. Sets `status='SUBMITTED'`, `submitted_at`; for `MANAGER` also `outcome` (PROMOTE / MEETS / BELOW). Returns `202 {undoUntil}`; after the undo window the engine task is completed with output: self → `{Appraisal:{selfSubmitted:true}}` (model raises "Manager review"); manager → `{Appraisal:{recommendation}}` (model raises the HR finalise task, D-05-14); peer → no case-file output.
- **Manager review entry (APR-04).** The FRD names a "CaseFabric timer on the self-review due date"; per the E1 spike result (`01-overview.md` §5) that is replaced by the standard fallback: a Spring scheduler finds open appraisals where `Appraisal.selfDueDate <= now` and `selfSubmitted=false` and no MANAGER review exists, and raises the "Manager review" task via the engine REST API. Either path inserts the `review` `MANAGER` row (`reviewer_id` = the employee's current manager, `due_date` = `manager_due` [ASSUMPTION]). AP16 then shows "Self review not submitted" when the SELF review is not `SUBMITTED`.
- **Decline (AP13).** `status='DECLINED'`, `decline_reason`; in-app `notification` to the employee and the manager (FRD table lists `notification` only). Completes the peer's task via the undo window. The peer is removed from the case team only if no other open PEER review of theirs remains on this case [ASSUMPTION].
- **Replace (AP15).** Old row `status='REPLACED'`, `replaced_by_id` → new `PEER` row (`REQUESTED`, `nominated_by` = **[OPEN]** the employee or the manager — the DB only requires it be non-null for PEER rows via the PEER-type CHECK, D-05-16). Old peer's open "Peer review" task is terminated and the old peer removed from the case team; new peer added and notified (same path as AP11). `ux_review_peer` blocks asking the same peer twice in one appraisal.
- **Evidence (AP16, APR-05).** Read-only, "from the review period" (`review_period_start`…`review_period_end`): `PROJECT` reviews of the employee's `project_assignment`s; client feedback via `v_client_feedback_by_employee` (stars, average, count); KPI score via `v_kpi_score` / `v_kpi_result`; PEER reviews **with reviewer names**; SELF review; latest `development_plan` result. Every source except `development_plan`, `project_assignment` and `client_feedback` (tables exist, V13) is missing from the applied schema and is produced by F04/F05 (§9).
- **Finalise (AP18).** Rejects own appraisal (`403`, APR-09) and non-HR (`403`, APR-06). Validates outcome + summary and, when `PROMOTED`, all four promotion fields. Sets `status='FINALISED'`, `outcome`, `final_summary`, `finalised_by`, `finalised_at`, `promo_*`; `email_outbox` `APPRAISAL_FINALISED` (timing for `AFTER_MEETING`: D-05-10); when `UNDER_PERFORMING`, an in-app `notification` to the manager (D-05-26). `202 {undoUntil}`; then the HR task is completed with `{Appraisal:{outcome}}`. Cancel on S9 calls nothing ("leaves the appraisal unchanged").
- **Promotion job.** Daily: appraisals with `outcome='PROMOTED'`, `promo_effective_date <= today`, `promo_applied_at is null` → `employee.job_title_id = promo_job_title_id`, `employee.grade = promo_grade`, `promo_applied_at = now()`, `audit_log` on the employee (G-06). Milestone timing: D-05-9.
- **Close (AP07).** Rejects unless every included appraisal is `FINALISED` (APR-07; treatment of `CANCELLED`: D-05-18). Sets `CLOSED`, `closed_at`, `closed_by`.
- **Reminders (AP06).** For each selected row, sends to whoever owns the pending step (employee for self, open peers, manager for manager review — inferred from the wireframe toasts "Reminder sent to Lotte Jansen", "Reminder sent to peers", "Reminder sent to Mark Smit") [ASSUMPTION]; template D-05-12.
- **Anonymity (APR-03).** AP19 and any employee-facing payload never contains a PEER reviewer's id or name; AP16/AP17 (manager, HR) do. Enforced in the DTO, not the UI.

## 8. Case model needs

`03-case-file.md` already designs the item (quoting): `Appraisal (0..1, overwritten per round)` — `roundId, appraisalId` (long, Spring at Start round) · `formId` (long) · `selfDueDate` (date-time, "timer → "Manager review" (APR-04)") · `selfSubmitted` (boolean, task output) · `pendingPeerReviewId` (long, "Spring, before "Peer requested"") · `recommendation` (PROMOTE / MEETS / BELOW, task output) · `outcome` (PROMOTED / NO_CHANGE / UNDER_PERFORMING, task output → milestone "Employee promoted"). 8 fields, matching FRD-00 §5 and FRD-05 §5's count.

**Naming inconsistency, flagged by the FRD itself:** "Fix: Feature 4.2 and section 8 use the case-file field Appraisal.pendingPeerReviewId; the case-file item list uses pendingPeerNominationId. Use one name." `03-case-file.md` already uses `pendingPeerReviewId`, and so do FRD-05 §4.2 and FRD-05 §5's own summary line; there is no `peer_nomination` table any more (`02-data-model.md` §5: folded into `review`). **Recommend keeping `pendingPeerReviewId`** — same reasoning and same direction as FRD-02's `reviewId` vs `probationReviewId` call (D-05-2).

| Element | Kind | Performer / guard | Notes |
|---|---|---|---|
| "Self review" | human task | Employee | raised when the `Appraisal` item is written at Start round; output `selfSubmitted` |
| "Peer review" | human task, repeatable | **Peer** (case role already in `employeecase.caseteam`) | one per nomination, input `pendingPeerReviewId`; replaced/declined ones are terminated |
| "Manager review" | human task | Manager | entry: `selfSubmitted == true` **or** the scheduler fallback on `selfDueDate` (E1, no native timer); output `recommendation` |
| HR finalise task (name D-05-14) | human task | HR | entry: "Manager review" completed; output `outcome` |
| "Employee promoted" | milestone | `outcome == 'PROMOTED'` (timing D-05-9) | FRD-00 "Milestone" example list |

Task due dates come from the round (`self_peer_due`, `manager_due`, `hr_due`), not from `setting` TASK_DUE rows [ASSUMPTION — FRD-08's Timers & rules table has no appraisal row; no TASK_DUE rows are seeded today]. R-CM-3's "name matches the spec" applies to the task names above.

**Not yet in the deployed model.** `employeecase.case`/`.type` (F01 scope) have no `Appraisal` item, none of the tasks above and no "Employee promoted" milestone. Building this feature requires a `cmmn-modeler` change (gate 3): new case-file item (8 fields), 4 human tasks, 1 milestone, the sentries above. The legacy `emp.case` "Appraisal cycle" stage is not extended (R-CM-1).

## 9. Data touched

**Tables already applied (no new DDL needed):** `appraisal_round`, `appraisal`, `review` (V13); `appraisal_form`, `form_section`, `form_question` (V11, read); `employee`, `contract`, `job_title` (V1, read; `employee.job_title_id`/`grade` written by the promotion job); `project_assignment`, `client_feedback`, `development_plan`, `pip`, `employee_exit` (V13, read); `email_template`, `email_outbox`, `notification`, `engine_task`, `audit_log` (V1).

**Not applied — must exist before this feature can be finished:**
- **`review_answer`** — designed in `02-data-model.md` §2.6 and FRD-05 §5 (composite PK `(review_id, question_id)`, `rating` 1–5, `yes_no`, `text_answer`, `comment`, `check (num_nonnulls(rating, yes_no, text_answer) <= 1)`), never migrated. Note for consistency with `FRD-02-onboarding-probation/spec.md` §9: that spec calls it "confirmed to exist", meaning documented in FRD v1.2 — it is **not** in the applied schema. Whichever of F02 / F06 reaches gate 1 first creates it, DDL verbatim from `02-data-model.md`; the other reuses it.
- **`v_client_feedback_by_employee`** (F04), **`kpi`, `kpi_result`, `goal`, `v_kpi_result`, `v_kpi_score`** (F05) — evidence sources for S8/S9. This is why F06 depends on F04 and F05.
- **`v_contract`** — needed by AP03 (APR-02 "on probation"); documented but never created (§0). Either create it (owned by FRD-02/F01's area) or compute probation end in the service as F01 does — D-05-6.

**`v_appraisal_status` as applied (V13) vs FRD-05:** it exists and feeds F09's report, but (a) has no Pending / Overdue column (FRD §4.1, §1 constraint), (b) averages `client_feedback` with `applies_to='PERSON'` only, over all time, rather than `v_client_feedback_by_employee` (PERSON + TEAM) for the review period, (c) has no per-manager "My review" state for S2. Changing it is a new migration that must keep C38/C39 green (D-05-17).

**Seed data drift to resolve (FRD-08/F09 data, consumed here):**
- The `V12` Standard / Leadership forms have 2 / 1 SCALE questions and **no TEXT questions**, so there is nowhere to store "Key achievements", "Areas to develop", "What should … keep doing?", "What could … do better?" and the manager's "Overall summary" under `02-data-model.md` §6 #12's design (D-05-8).
- `V9`'s `APPRAISAL_FINALISED` sends to employee **and** manager ([ASSUMPTION] in that migration); FRD-05 says "The employee is notified" and WF-05's own mail list says recipient "Employee" (D-05-11).

**FRD-05's own database cross-check, cited directly:**
- "Fix: The manager review outcome is PROMOTE / MEETS / BELOW (review) but the appraisal outcome is PROMOTED / NO_CHANGE / UNDER_PERFORMING (appraisal). The FRD should state the mapping." → D-05-1.
- "Fix: Feature 4.2 and section 8 use the case-file field Appraisal.pendingPeerReviewId; the case-file item list uses pendingPeerNominationId. Use one name." → D-05-2.
- "OK" items (one appraisal per employee per round; APR-09 CHECK; `peer_anonymous` default true + due-date CHECK; promotion all-or-nothing; REQUESTED / DECLINED / REPLACED + `replaced_by_id`) — all re-verified present in V13.

**Case file:** `Appraisal` item (§8), already in `03-case-file.md`; no new field.

## 10. E-mails & timers

| Trigger | Template (code, seeded) | To |
|---|---|---|
| HR starts a round (AP03) | `APPRAISAL_ROUND_OPENED` (V9) | each included employee |
| Employee chooses peers (AP11); manager replaces a peer (AP15) | `PEER_REVIEW_REQUEST` (V9, `to_peer`) — for AP15 [ASSUMPTION], FRD §4.4 says only "new peer notified" | each new peer |
| HR finalises (AP18) | `APPRAISAL_FINALISED` (V9) | "The employee is notified" — seed also flags manager (D-05-11); `AFTER_MEETING` timing D-05-10 |
| Every new task (Self / Peer / Manager review, HR task) | G-04 `TASK_ASSIGNED` + in-app notification | task owner — whether the dedicated templates above **replace** or **add to** it for Self review / Peer review: D-05-13 |
| Peer declines (AP13) | in-app notification only (FRD table: `notification`) | employee + manager |
| Under-performing (AP18) | in-app notification ("the manager is notified"; no template among FRD-08's 21) | manager (D-05-26) |
| Reminder (AP06) | **[OPEN] D-05-12** — no "appraisal reminder" template among FRD-08's 21 | owner of the pending step |
| Overdue tasks | G-05 `TASK_OVERDUE` (setting `OVERDUE_REMINDER_DAYS`, 1 day after due, can be switched off) | task owner |

WF-05's embedded mail list gives "Peer review request" the trigger "Peer nominations approved" — contradicts FRD-08 ("An employee chooses peers") and FRD-05 (no approval, Appendix A #6); the FRD wins.

| Timer | Default | Guard |
|---|---|---|
| Manager review on self-review due date (APR-04) | `appraisal_round.self_peer_due` (per round, not a `setting` row) | Spring scheduler fallback (E1), only when `selfSubmitted=false` |
| Promotion effective date | `appraisal.promo_effective_date` | daily job, `promo_applied_at is null` |
| Undo window | 10 seconds (setting `UNDO_WINDOW_SECONDS`, cannot be switched off) | every task-completing endpoint (AP12, AP13, AP18) |

## 11. Acceptance criteria

From FRD-05 §6, verbatim.

**AC-05-01 (FRD "HR starts a round")**
```
Given HR fills in all round settings
When HR selects Start round
Then every eligible employee receives a "Self review" task and an e-mail
```

**AC-05-02 (FRD "Peers are asked directly")**
```
Given Lotte Jansen chooses Priya Sharma and Youssef Tahiri as peers
When she submits her self review
Then Priya and Youssef each receive a "Peer review" task
And no approval task is created for the manager
```

**AC-05-03 (FRD "Peer feedback is anonymous")**
```
Given Priya Sharma submitted a peer review for Lotte Jansen
When Lotte opens her finalised appraisal
Then she sees the feedback without Priya's name
```

**AC-05-04 (FRD "Evidence on the manager review")** — needs F04 data
```
Given Priya Sharma has 2 project reviews and 2 client feedback entries this period
When Mark Smit opens her manager review
Then all 4 items are shown in the Evidence panel with dates and ratings
```

**AC-05-05 (FRD "Promotion")**
```
Given Ruben Bakker's manager recommends Promote
When HR chooses Promoted, enters Senior Developer, Senior I and 01-01-2027 and selects Finalise appraisal
Then Ruben's result shows "Promoted"
And his job title changes to Senior Developer on 01-01-2027
```

**AC-05-06 (FRD "Round cannot close early")**
```
Given 35 of 44 appraisals are not finalised
When HR selects Close round
Then the message "All appraisals must be finalised before closing the round." is shown
```

**AC-05-07 (FRD "Employees on probation or PIP are excluded")**
```
Given Sanne de Boer is on probation and Daan Visser is on a PIP
When HR starts the round
Then neither receives a "Self review" task
```

**AC-05-08 (FRD "Self review not submitted")**
```
Given Ruben Bakker has not submitted his self review
When the self-review due date passes
Then his manager receives a "Manager review" task marked "Self review not submitted"
```

Derived, no literal FRD scenario — **[ASSUMPTION]**, confirm before building:

**AC-05-09 (APR-09)**
```
Given Emma de Vries is HR and is included in the round
When Emma opens Consolidate & finalise for her own appraisal
Then the request is refused with 403 and her appraisal stays unchanged
```

**AC-05-10 (APR-01)**
```
Given a round is Open
When HR selects Start round on a second round
Then the second round is not started
```

**AC-05-11 (validation row "Due dates")**
```
Given HR enters a manager due date before the self and peer due date
When HR selects Save draft
Then the message "Due dates must follow the order: self and peer, manager, HR." is shown
```

**AC-05-12 (validation row "Peers")**
```
Given Lotte Jansen chooses only Priya Sharma as a peer
When she submits her peer choice
Then the message "Choose 2 or 3 colleagues as peers." is shown
```

**AC-05-13 (validation row "Decline reason" + flow step 4)**
```
Given Priya Sharma has a "Peer review" task for Lotte Jansen
When Priya declines without a reason
Then the message "Add a reason for declining." is shown
And when she declines with a reason, Lotte and her manager receive a notification
```

**AC-05-14 (validation row "New job title, grade, effective date")**
```
Given HR chooses Promoted and leaves the effective date empty
When HR selects Finalise appraisal
Then the message "Complete the promotion details." is shown
```

## 12. Decisions & open items

| ID | Topic | Status |
|---|---|---|
| D-05-1 | Mapping manager recommendation (PROMOTE / MEETS / BELOW) → appraisal outcome (PROMOTED / NO_CHANGE / UNDER_PERFORMING) | **[OPEN]** — FRD's own "Fix". The wireframe pairs them visually (Promote ↔ Promoted "Process promotion", Meets ↔ No change "Meets expectations", Below ↔ Under-performing "Start PIP"); proposal: 1:1 in that order, used only to pre-select S9's outcome — HR still chooses freely. Confirm. |
| D-05-2 | Case-file field `pendingPeerReviewId` vs `pendingPeerNominationId` | **Proposed:** keep `pendingPeerReviewId` (already in `03-case-file.md`, FRD §4.2 and §5 summary; mirrors FRD-02's `reviewId` call). Needs user confirmation. |
| D-05-3 | S7 Change peers: wireframe `p-mg-peer-approve` reads as an approval ("requested by Lotte on 08 Oct", Keep/Replace) | **Confirmed FRD gap** — Appendix A #6: "p-mg-peer-approve reads as an approval "requested by Lotte"; FRD-05 says peers are asked directly with no manager approval. Suggested fix: Rename and redraw as Change peers." `wireframes.md` redraws it as Change peers. Still **[OPEN]**: the drawn "Add a peer" (no FRD endpoint) and "Worked together on" column (not in AP14's tables) — proposal: drop both. |
| D-05-4 | When peer tasks are created: on choosing peers (§4.2 "tasks created at once", flow "straight away") vs AC-05-02 "When she submits her self review" | **Proposed:** at AP11 (choose peers), which is also what S10's "Peers … · requested" before self-submission shows; AC-05-02 still holds if peers are chosen in the same step. Confirm. |
| D-05-5 | `peer_mode` OPTIONAL / OFF behaviour (is 0 peers valid under OPTIONAL? is the peer card hidden under OFF?) and the "Visible" anonymity option vs APR-03 (MUST, anonymous for the employee) | **[OPEN]** — FRD gives the columns and the "2 or 3" message only. |
| D-05-6 | Eligibility: wireframe offers optional checkboxes; APR-02 makes all four exclusions mandatory and does not show probation/PIP; `v_contract` (probation end) is not applied | **[OPEN]** — proposal: exclusions always applied server-side; "Exclude joiners after" date = `eligibility_date`; checkboxes become read-only information. Who creates `v_contract` (or compute in Java as F01 does) to be decided at gate 1. |
| D-05-7 | Standard vs Leadership form per employee (round has `standard_form_id` + nullable `leadership_form_id`; wireframe shows one "Review form" select) | **[OPEN]** — `02-data-model.md` §6 #9 "Leadership form: chosen by job title or by role?" still unanswered. |
| D-05-8 | Where free-text answers live: Key achievements, Areas to develop, keep doing / do better, manager Overall summary (`review` has no summary column) | **[OPEN]** — `02-data-model.md` §6 #12 says "Self and peer texts are TEXT questions of their forms"; the V12-seeded forms have no TEXT questions and different SCALE questions from WF-05. Needs a seed/form update (FRD-08 data) or another decision; same question applies to FRD-02's probation "Summary". |
| D-05-9 | Milestone "Employee promoted": recorded at finalise (§2 flow step 7) or by the scheduled job on the effective date (§4.6)? | **[OPEN]** — FRD contradicts itself. |
| D-05-10 | `promo_notify_mode = AFTER_MEETING`: what triggers the "Appraisal finalised" e-mail, and is the result hidden in My Reviews until then? | **[OPEN]** — FRD gives only the two values. |
| D-05-11 | `APPRAISAL_FINALISED` recipients: V9 seed = employee + manager ([ASSUMPTION]); FRD + WF-05 mail list = employee | **[OPEN]** — FRD wins unless the user decides otherwise; fixing the seed is an HR-editable setting, no migration strictly required. |
| D-05-12 | Reminder (AP06) template and recipients | **[OPEN]** — no matching template among FRD-08's 21; recipients inferred from wireframe toasts. |
| D-05-13 | Dedicated templates (`APPRAISAL_ROUND_OPENED`, `PEER_REVIEW_REQUEST`) vs the generic G-04 `TASK_ASSIGNED` for the same new task — one e-mail or two? | **[OPEN]** — same question FRD-02's `PROBATION_REVIEW_DUE` raises; decide once for both. |
| D-05-14 | Name of HR's finalise task | **[OPEN]** — FRD names "Self review", "Peer review", "Manager review" but only "HR task" for Screen 9. Proposal: "Consolidate & finalise" (S9's own title, the button text HR "opens"). Needed for R-CM-3. |
| D-05-15 | After the self-review due date: can the employee still submit? What happens to the open "Self review" task once "Manager review" starts? | **[OPEN]** — FRD silent. |
| D-05-16 | After a decline, who picks the new peer (employee via AP11 again, or manager via AP15)? And `nominated_by` on a manager-replaced row | **[OPEN]** — flow says "so another peer can be chosen", without a subject. |
| D-05-17 | "Pending / Overdue": which date is the "round end date" (`hr_due` or `review_period_end`)? `v_appraisal_status` lacks it, uses PERSON-only all-time client feedback, and has no manager "My review" state | **[OPEN]** — proposal `hr_due`; view changes go through a new migration that keeps F09's C38/C39 green. |
| D-05-18 | `appraisal.status='CANCELLED'`: when is an appraisal cancelled (e.g. employee enters offboarding or a PIP mid-round), and does APR-07 ignore cancelled rows? | **[OPEN]** — FRD silent; the status exists in the schema. |
| D-05-19 | Round DRAFT: S3 has "Save draft" but §4.1 has no GET/PUT for a draft round; no duplicate-name or period-order message | **[OPEN]** — needs endpoints added to this spec (R-API-4) or "Save draft" removed. |
| D-05-20 | Manager's "Promote · Propose new title" and S9's "Promote → Senior Developer" | **[OPEN]** — no column stores a proposed title; proposal: drop the proposed-title part, HR enters the title on S9. |
| D-05-21 | S10 shows client-feedback entries; AP19's FRD table list (`appraisal, review, review_answer`) does not include `client_feedback` | **[OPEN]** — add the table to AP19 or drop the entries. |
| D-05-22 | Close-round message: wireframe toast "All appraisals must be finalised first" vs FRD "All appraisals must be finalised before closing the round."; button drawn orange (Archive style) | **Resolved:** FRD wording (R-SRC-1, R-SRC-3). Button colour: keep orange outline as drawn **[ASSUMPTION]** — closing is archive-like, R-UI-1 names only Archive. |
| D-05-23 | Evidence sources not yet applied (`v_client_feedback_by_employee`, `kpi*`, `goal`, `v_kpi_*`) | **Resolved by build order:** F06 depends on F04 and F05 (`README.md`); not built here. |
| D-05-24 | FRD "Integrations: CaseFabric timer on the self-review due date" | **Resolved:** superseded by the E1 spike result — Spring scheduler fallback (`01-overview.md` §5, `03-case-file.md` §1). |
| D-05-25 | `review_answer` creation ownership (shared with FRD-02) | **Resolved:** first of F02 / F06 to reach gate 1 creates it, DDL verbatim from `02-data-model.md` §2.6. |
| D-05-26 | "Under-performing — the manager is notified": channel | **[ASSUMPTION]** in-app `notification` only (FRD §4.6 lists `notification`; no template among the 21). Confirm. |
| D-05-27 | S9's Inputs panel draws "Peer reviews (3) · Anonymous summary", but APR-03 says HR sees peer names | **[OPEN]** — kept as drawn until confirmed; AP17 returns names either way (HR is allowed to see them). |

## 13. Definition of done

- [ ] **Not started.** Blocked on F04 and F05 (evidence data, §9) and on the user's answers to D-05-1, -2, -4…-21, -26, -27 (CLAUDE.md §2.9.5 "ask when blocked"). D-05-3's rename is FRD-confirmed; its add-peer / worked-together parts are not.
- [ ] Gate 1: migration for `review_answer` (unless F02 got there first), any `v_appraisal_status` change (D-05-17) and any seed change for appraisal-form TEXT questions (D-05-8); `02-data-model.md` updated in the same change (R-DB-1). `appraisal_round` / `appraisal` / `review` are **not** recreated.
- [ ] Gate 3: `employeecase` model extended with the `Appraisal` item, 4 tasks, the "Employee promoted" milestone (§8); `03-case-file.md` updated if D-05-2 changes the field name.
- [ ] Every acceptance criterion in §11 has an automated test (Testcontainers integration; AC-05-04 needs F04 fixtures).
- [ ] Every message in §5 asserted verbatim; every DB CHECK this feature relies on (APR-01 index, APR-09 CHECK, due-date order, promotion all-or-nothing, decline reason, `ux_review_peer`) has a negative test.
- [ ] Role checks tested allowed + 403 for every endpoint, including HR finalising own appraisal (APR-09), a manager on another team's appraisal (WS-02), and a non-nominated user opening a peer review.
- [ ] APR-03 tested at the API level: no peer name or id in any employee-facing response.
- [ ] F09's C38/C39 report tests still pass after any `v_appraisal_status` change.
