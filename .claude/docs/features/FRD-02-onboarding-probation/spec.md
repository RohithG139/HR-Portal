# FRD-02 — Onboarding and probation

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-02/FRD-02_Onboarding_and_Probation.docx` (primary), `FRD-00/FRD-00_Application_Overview.docx` (roles, G-rules), `FRD-08/FRD-08_Reports_and_Settings.docx` (settings/timers this flow reads). The legacy v1.1 monolithic PDF is superseded — see `.claude/docs/source/README.md`. |
| Wireframes | `.claude/docs/source/FRD-02/WF-02_Onboarding_and_Probation.html` (v1.2, canonical) — onboarding pages (see `F01-onboarding/wireframes.md`, already built against the legacy v1.1 wireframe file; page ids are unchanged between the two files); probation pages `p-mg-probation` (manager review form, confirmed present in WF-02), `p-hr-form-probation` (the probation appraisal-form *template* editor, FRD-08 scope, not redrawn here) |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-02 wireframe HTML > legacy v1.1 (R-SRC-1). |
| Relationship to F01 | **The onboarding half of FRD-02 is already built**, as `.claude/docs/features/F01-onboarding/`. This document is the full FRD-02 contract (both halves the FRD itself describes under one heading, "FRD-02 — Onboarding and probation"); §1–§7 summarise the onboarding half and point to F01's spec for field-level detail rather than duplicating it, so the two documents cannot drift apart. **The probation review half has not been built yet** (F01's own scope section says so explicitly: "probation review is F02") — §3 onward for probation is written in full detail, as a real spec for that future feature, not a summary. |

## 0. Status & sources

FRD-02 v1.2 (`FRD-02/FRD-02_Onboarding_and_Probation.docx`, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026") describes one continuous flow — "Make sure every new employee has a complete record, working equipment and accounts, and a structured first week, and that probation is reviewed on time" — split into a "Happy path — Main flow" (onboarding, §2) and a "Probation review" sub-flow (same §2, immediately after). Both share one scope statement, one business-rules table (ONB-01…ONB-09), one validation table, and one acceptance-criteria list in the FRD. This spec keeps that FRD structure rather than silently treating "probation" as a disconnected afterthought.

v1.2 is a complete rewrite of the old v1.1 monolithic PDF's FRD-02 section into its own document, written with knowledge of the already-built architecture: it gives exact screen routes, a full method/endpoint/table list per feature (§4 "Features, Endpoints & Database"), the complete column-by-column schema for every table this flow touches, and a line-by-line cross-check of every FRD claim against the real `V1__init.sql` ("OK"/"Fix"/"Note" verdicts). Several things this spec marked `[OPEN]` against the old source are now resolved directly from v1.2 — see §12's updated status column.

Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Where the architecture docs already designed something for probation (they do — see §8), this spec cites it rather than redesigning it.

## 1. Scope

**In scope (FRD-02's own "In scope" list)**
- Creating a new employee in three steps: Employee details, IT & equipment request, Checklist — **built, see F01-onboarding**.
- Onboarding templates that create the checklist — **built**.
- IT setup of equipment and accounts — **built**.
- Employee onboarding tasks: policies, profile, confirming equipment — **built**.
- Completing onboarding — **built**.
- **Probation review — not built.** The manager rates the employee 14 days before probation ends, and the outcome either confirms employment or starts an HR-approved offboarding path.

**Out of scope (FRD-02's own "Out of scope" line, plus what F01 already deferred)**
- Recruitment — not in v1 (FRD-02 itself says so).
- Offboarding itself (FRD-07) — probation's "End employment" outcome only *starts* offboarding; it does not implement it.
- Settings screens for the probation appraisal form (FRD-08) — this spec consumes a `PROBATION`-type `appraisal_form`, it does not build the form editor (wireframe page `p-hr-form-probation` belongs to FRD-08's spec).
- Everything else F01 already listed as deferred (Projects/Development tabs, dashboards' KPI cards, Transfer manager, Initiate exit) — unchanged here.

## 2. Roles & permissions

Onboarding's permissions table is unchanged — see `F01-onboarding/spec.md` §2. For probation review specifically:

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| Receive the "Probation review" task | No | Yes (own direct report) | No | No |
| Submit the probation review (ratings, summary, outcome) | No | Yes (own direct report only, WS-02) | No | No |
| Approve/reject an "End employment" outcome | Yes | No | No | No |
| View a probation review result | Yes (all) | Own team only (WS-02) | **[OPEN]** — the FRD does not say whether the reviewed employee ever sees their own probation review result; F01's precedent for appraisals-adjacent data is to show employees only what's explicitly listed as theirs. Not resolved here — ask before building. | No |

G-13 applies: a manager is never the subject of their own probation review's manager-rating step (self-action restriction), though a manager-who-is-also-reviewed would still have their *own* review completed by *their* manager — ordinary case-team routing, not a special case.

## 3. Business rules

Copied from the FRD by ID (R-SRC-3 — wording kept exact):

| ID | Rule |
|---|---|
| ONB-01 | Probation end date = start date + probation period, calculated by the system. **(Built: `v_contract.probation_end_date`, `02-data-model.md` §2.9.)** |
| ONB-02 | The template creates the checklist items with owner and due date relative to the start date. **(Built.)** |
| ONB-03 | Each checklist item is shown in its owner's My Tasks until it is done. **(Built.)** |
| ONB-04 | Ticking a checklist item marks it Done immediately and updates the progress bar. **(Built.)** |
| ONB-05 | "Complete onboarding" is available only when all mandatory items are done. **(Built.)** |
| ONB-06 | Every item IT records under "Equipment issued" is added to the employee's assets. **(Built.)** |
| ONB-07 | The employee sees their own onboarding items only; they never see HR-only items. **(Built.)** |
| ONB-08 | If probation ends with "End employment", HR must approve before offboarding starts. **(Not built — this is the core new rule this spec covers. Data model already supports it: `review.hr_decision` is only settable when `review_type='PROBATION' and outcome='END'`, `02-data-model.md` line 599.)** |
| ONB-09 | An employee whose private e-mail already exists cannot be created a second time; the system shows the existing employee instead (G-01). **(Built.)** |

Probation-specific process rules, from FRD-02 §3 "Probation review" (quoted, not paraphrased):
1. "14 days before probation ends (configurable), the manager receives a 'Probation review' task."
2. "The manager rates quality of work, collaboration and learning, writes a summary and chooses an outcome."
3. "'Confirm employment' records the milestone 'Probation passed'."
4. "'End employment' sends the case to HR for approval and then starts offboarding (FRD-07)."

Decisions for this feature — see §12 for the full table with current status (as of 2026-10-16, FRD v1.2 has resolved D-P1, D-P6 and D-P7; D-P2, D-P4 and D-P5 remain genuinely open, unlike F01's D-01…D-23 which are all resolved):
- **D-P1** (resolved) — the Probation review timer cannot be switched off (`can_disable=false`), confirmed by FRD-08 v1.2's clean Timers & rules table: "Probation review | 14 days before end | Manager | No".
- **[OPEN] D-P2** — Whether the reviewed employee can see their own probation review result (see §2 table).
- **D-P3** (resolved, a judgment call not a gap) — The FRD's own validation table gives one combined message for the row "Probation summary and outcome | Required | **'Add a summary and choose an outcome.'**" — read as one message covering two independent required fields (summary text, outcome choice), same pattern as F01's D-14 messages that aren't tied to a single field. Kept as one message per R-SRC-3 (exact FRD wording), used for either missing-summary or missing-outcome.

## 4. Screens

Onboarding screens S1–S13 are unchanged — see `F01-onboarding/spec.md` §4 (all already built).

| # | Screen | Who | Route | Wireframe page / element | FRD |
|---|---|---|---|---|---|
| S14 | Probation review | Manager | `/team/:code/probation-review` (FRD-02 v1.2 §3, Screen 12) | `p-mg-probation` | FRD-02 §2 "Probation review" |
| S15 | Probation decision (HR approval of "End employment") | HR | `/employees/:code/probation-review/decision` (FRD-02 v1.2 §3, Screen 13) | **not drawn in the wireframe** — FRD-02 v1.2 itself lists this screen as "not drawn" (its own Screen 13 entry) and separately tracks it as an open gap: "TBC (Appendix A #5 · FRD-02 Screen 13, FRD-07 Screen 4, FRD-06 Screen 7): HR approval of 'End employment', HR 'Confirm exit' and HR PIP sign-off have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." This is the FRD's own words, not a gap this spec discovered independently — confirms D-P4 below is a real, acknowledged gap, not a miss. | ONB-08 |

## 5. Fields & validation

Onboarding fields/messages are unchanged — see `F01-onboarding/spec.md` §5.

### S14 — Probation review (Manager)

Per the wireframe (`p-mg-probation`), the review form is driven by a `PROBATION`-type `appraisal_form` (FRD-08 scope) whose seeded default has three 1–5 scale questions, matching the FRD-02 process text exactly:

| Field | Type | Rule | Message |
|---|---|---|---|
| Quality of work | 1–5 scale | Required | "This field is required." (generic required-field pattern, consistent with other scale questions elsewhere in the app) |
| Collaboration & communication | 1–5 scale | Required | same |
| Learning & independence | 1–5 scale | Required | same |
| Summary | text area | Required | **"Add a summary and choose an outcome."** (FRD-02 §5, exact wording — D-P3) |
| Outcome | choice: Confirm employment / End employment | Required | same combined message as Summary |

Context panel (read-only, per the wireframe): Started date, current project, onboarding completion %, buddy. All of these are already-existing F01 data (`contract.start_date`, `project_assignment` — **[OPEN]**: F03 isn't built yet either, so "current project" has no data source until then; render blank/omitted until F03 exists), onboarding checklist completion percentage (`checklist_item`, already computed by F01's `ChecklistDto.progressPercent`), `employee.buddy_id`.

### S15 — Probation decision (HR)

Not drawn in any wireframe. Built from ONB-08 and `review.hr_decision`'s two allowed values:

| Field | Rule | Message |
|---|---|---|
| Decision: Approve / Reject | Required | **[ASSUMPTION]** — no FRD wording exists for this screen at all (it is not in FRD-02's §4 screens list, §5 validation table, or §7 acceptance criteria). A minimal "Approve" / "Reject" choice is proposed here, matching `review.hr_decision`'s two allowed values (`APPROVED`/`REJECTED`) exactly — nothing richer than the schema already allows. **[OPEN]**: confirm with the user before building; do not build from this assumption alone (R-SRC-4: spec before code, and this part of the spec is itself unconfirmed). |

## 6. API

Onboarding's endpoints A01–A38 are unchanged — see `F01-onboarding/spec.md` §6 (F01 used an `/api/onboardings` (plural)-prefixed convention; FRD-02 v1.2 §4.1 independently gives `POST /onboarding` (singular) — a naming-style difference only, not a functional gap; F01's already-shipped, tested paths stand, R-API-1's "paths and formats" convention is this project's own, the FRD never mandates exact path spelling).

New endpoints needed for probation, now taken directly from FRD-02 v1.2 §4.7 "Probation review (Screens 12, 13)" rather than proposed from scratch:

| # | Method & path | Role | Notes |
|---|---|---|---|
| P01 | `GET /api/probation-reviews/{reviewId}` | Manager (own report), HR | "Form with ratings, summary, outcome" — `review, review_answer, appraisal_form (PROBATION), form_section, form_question, v_contract` (FRD-02 v1.2 §4.7, exact table list) |
| P02 | `PUT /api/probation-reviews/{reviewId}` | Manager (own report only, WS-02 + G-13) | "Save draft" — `review, review_answer` (FRD-02 v1.2 §4.7; not in this spec's first draft — added here) |
| P03 | `POST /api/probation-reviews/{reviewId}/submit` | Manager (own report only, WS-02 + G-13) | "Confirm employment (milestone 'Probation passed') or End employment (HR task)" — `review (outcome CONFIRM / END), review_answer` · case file `Probation.outcome` → `202 {undoUntil}` (R-API-6 undo window, same pattern as F01's task-completion endpoints) / `400` "Add a summary and choose an outcome." if summary or outcome missing |
| P04 | `POST /api/probation-reviews/{reviewId}/hr-decision` | HR | "HR approves or rejects End employment (ONB-08); approval starts offboarding" — `review (hr_decision), employee_exit (PROBATION_NOT_PASSED, SYSTEM)` · case file `Probation.hrDecision` → `200` / `409` if not in the awaiting-decision state |

(This spec's first draft also proposed a `GET .../hr-decision` list endpoint not present in FRD-02 v1.2 — dropped; the review detail endpoint P01 already serves HR once `outcome='END'`, no separate listing endpoint is specified.)

Path spelling (`/api/probation-reviews/...` vs. the FRD's own `/probation-reviews/...`) follows this project's established `/api` prefix convention (R-API-1), same adaptation F01 already makes for every one of its endpoints.

## 7. Backend behaviour

Onboarding's transaction/outbox/engine-call behaviour is unchanged — see `F01-onboarding/spec.md` §7. For probation (proposed, not yet implemented):

- **Review creation.** A scheduler (same `OverdueReminderScheduler`-style polling pattern F01 already built for G-05, not a native CMMN timer — see §8) finds contracts where `v_contract.probation_end_date − PROBATION_REVIEW days <= today` and no `review` row of type `PROBATION` exists yet for that contract, creates one (`status='REQUESTED'` or `'DRAFT'`, `form_id` = the active `PROBATION`-type form, `reviewer_id` = the employee's manager, `due_date` = probation end date), and raises G-04's task-assignment notification to the manager (reusing `TaskNotificationService`, already built).
- **Save draft (P02).** Writes `review_answer` rows (one per question, `rating`/`yes_no`/`text_answer` per the question's `answer_type`) without changing `review.status` from `DRAFT`. No engine call.
- **Submission (P03).** Writes `review.status='SUBMITTED'`, `review.outcome`, `review.submitted_at`, and upserts the final `review_answer` rows (table confirmed to exist — FRD-02 v1.2 §5, D-P6 resolved, no schema change needed). Inside the same transaction: if `outcome='CONFIRM'`, nothing further is needed from HR. After commit: completes the "Probation review" engine task (R-ARC-5, undo window) with output `{Probation:{outcome:'CONFIRM'}}` or `{Probation:{outcome:'END'}}` (see §8 for how the case model should route on this).
- **HR decision (P04).** Only reachable when `outcome='END'` (matches the DB CHECK at `02-data-model.md` line 599). Writes `review.hr_decision`, `hr_decided_by`, `hr_decided_at`. `APPROVED` triggers the start of offboarding (FRD-07, not built — F01 only needs to raise the fact, per R-ARC-1, same pattern as onboarding's "Create development plan" task for F04). `REJECTED` — **[OPEN]**: the FRD does not say what happens next if HR rejects ending employment (does the employee stay active? does a new probation period start? is the manager asked to reconsider?). Not resolved; ask before building this branch.

## 8. Case model needs

`03-case-file.md` designs this with 3 fields (quoting directly):

```
"Probation":  { "reviewId": null, "reviewDate": "2026-12-07T09:00:00Z", "outcome": null }
```

**Correction (2026-10-16, FRD v1.2):** FRD-02 v1.2 §5 "Case File Items (CaseFabric)" independently lists the `Probation` item with **4 fields**, not 3 — `probationReviewId, reviewDate, outcome, hrDecision`. The 4th field, `hrDecision`, is exactly what ONB-08's HR-approval routing needs (P04 writes it) and was missing from `03-case-file.md`'s existing design. R-SRC-1 (FRD wins): this spec adopts the 4-field version; `03-case-file.md` needs updating to match before this feature is built (not done in this pass — that file belongs to the architecture docs, update it alongside the CMMN model work in §8 below, in the same change, per R-DB-1's "update the doc in the same change" principle applied to case-file docs).

**Naming inconsistency, also flagged by the FRD itself:** FRD-02 v1.2's own database cross-check admits: "Fix: Section 8 names the case-file field Probation.reviewId; the case-file item list uses probationReviewId. Use one name." `03-case-file.md` already uses `reviewId` (shorter, consistent with how `Onboarding`'s fields aren't prefixed with the item name either, e.g. `itRequestSent` not `onboardingItRequestSent`) — **recommend keeping `reviewId`** for consistency with the existing architecture doc and the project's own naming pattern elsewhere in the case file, overriding the FRD docx's `probationReviewId` spelling. Flag this choice when updating `03-case-file.md`; it's a naming call, not a content disagreement.

| Item | Field | Type | Written by | Read by (sentry/routing) | DB source |
|---|---|---|---|---|---|
| **Probation** (0..1; absent when probation = None) | reviewId | long | Spring, when the review row is created | link | `review` (type PROBATION) |
| | reviewDate | date-time | Spring = probation end − `PROBATION_REVIEW` setting | timer → "Probation review" task (Manager) | `v_contract` |
| | outcome | CONFIRM / END | task output | CONFIRM → milestone "Probation passed"; END → HR approval task | `review.outcome` |
| | hrDecision | APPROVED / REJECTED | HR decision output (P04) | APPROVED → starts offboarding (FRD-07); REJECTED → see D-P5 (FRD silent) | `review.hr_decision` |

**Timer:** per the already-established pattern (E1 spike result, `01-overview.md` §5 — CaseFabric 1.1.35 cannot read a case-file value in a native `timerExpression`), this must **not** be a native CMMN timer. Spring computes `Probation.reviewDate` as an absolute date-time and a scheduler (same shape as F01's `OverdueReminderScheduler`) polls for `reviewDate <= today` and raises the "Probation review" task directly via the engine REST API — exactly the fallback already proven to work for `Onboarding.overdueDate`.

**Case roles:** Manager (already a case-team member from onboarding, per `03-case-file.md` §3) performs "Probation review"; HR (already a tenant-role case-team member) performs the approval step when `outcome='END'`.

**Not yet in the deployed model.** The currently-deployed `employeecase.xml` (confirmed live-deployed via `GET /repository/list`, 2026-10-16) is explicitly scoped "F01 (Create employee -> Onboarding) scope only" per its own description string. It declares only the `Employee` and `Onboarding` case-file items and three onboarding tasks — **no `Probation` item, no "Probation review" task, no "Probation passed" milestone exist in the model yet.** Building this feature requires extending `employeecase.case`/`.type` (new case-file item, new human task, new milestone, new sentry gating the HR-approval task on `Probation.outcome == 'END'`) — a `cmmn-modeler` task, not yet done. This is a prerequisite for F02, not optional polish.

## 9. Data touched

**Tables (all already in `02-data-model.md`, no new table):** `contract` (read, via `v_contract`), `review` (new rows, type `PROBATION`), **`review_answer`** (new rows — confirmed to exist, FRD-02 v1.2 §5: composite PK `(review_id, question_id)`, columns `rating` (1–5, nullable), `yes_no`, `text_answer`, `comment`, with `check (num_nonnulls(rating, yes_no, text_answer) <= 1)` — resolves D-P6, no schema change needed), `appraisal_form` + `form_section` + `form_question` (read, `used_for='PROBATION'` — seeded by FRD-08, not this feature), `employee` (read, for manager/buddy), `employee_exit` (new row on HR approval, `exit_type='PROBATION_NOT_PASSED'`, `initiated_by_role='SYSTEM'` — FRD-02 v1.2 §4.7), `engine_task`, `notification`, `email_outbox`, `audit_log`, `setting` (new row needed, see below).

**Two small FRD-vs-schema gaps, flagged by FRD-02 v1.2's own database cross-check (not this spec's own finding — citing it directly):**
- "Fix: `contract.probation_months` only allows 0, 1 or 2. The FRD does not state the allowed values; add them to the Inputs/validation table (and to the step-1 dropdown)." — informational; the schema (and F01's already-built dropdown) already correctly restrict to these 3 values, the FRD text alone just never said so explicitly.
- "Fix: `onboarding.right_to_work_verified` exists in the DB but is not mentioned in the FRD inputs, validation or wireframe. Either add it to step 1 or drop the column." — already resolved by precedent: F01 already built this field as optional (`F01-onboarding/wireframes.md` S2 lists "Right-to-work verified (optional)"), matching the column's nullable, non-mandatory nature. No action needed.

**Settings this feature needs (currently missing — must be seeded before it can run, same precedent as F01's `V2__seed_settings.sql`):**
- `PROBATION_REVIEW` setting: category `TIMER`, `value_int=14`, `value_unit='DAYS'`, `recipient_role='MANAGER'`, **`can_disable=false`** (FRD-08 v1.2's clean Timers & rules table: "Probation review | 14 days before end | Manager | No" — resolves D-P1, no longer open).
- A `PROBATION`-type `appraisal_form` + its 3 sections/questions (Quality of work, Collaboration & communication, Learning & independence — exact question text from the wireframe `p-mg-probation`, matching FRD-02's process-text rating categories) — this is FRD-08's seed-data responsibility, same split F01 used for its own templates/e-mail templates.
- **Correction (2026-10-16, FRD v1.2):** a dedicated e-mail template IS needed — **`PROBATION_REVIEW_DUE`** ("Probation review due", FRD-08 v1.2's 21-template list, trigger "14 days before probation ends") — this spec's first draft assumed the generic `TASK_ASSIGNED` (G-04) covered this and no new template was needed; that assumption was wrong. FRD-08 v1.2 lists "Probation review due" as its own named template, distinct from the generic "Task assigned" template, and its own database cross-check independently confirms the gap: "Fix: FRD-08 lists 21 e-mail templates, but only 17 template codes are referenced by endpoints. Task assigned, Task overdue, Probation review due and Contract ending have no code in any endpoint or timer row; add them." `PROBATION_REVIEW_DUE` does not exist in the real `V2__seed_settings.sql` today and must be added when this feature is built (resolves D-P7).

**Case file:** `Probation` item (§8, now 4 fields — `reviewId`, `reviewDate`, `outcome`, `hrDecision`), added to `03-case-file.md` only once approved for this feature (it is currently documented there as a 3-field *design*, not yet wired into the live model, and needs the 4th field added per §8's correction).

## 10. E-mails & timers

| Trigger | Template | To |
|---|---|---|
| Probation review task created (14 days before probation ends) | **`PROBATION_REVIEW_DUE`** (FRD-08 v1.2's own named template — corrected from this spec's first draft, which wrongly assumed the generic `TASK_ASSIGNED` covered it; see §9) | the employee's manager |
| "End employment" outcome submitted | **[OPEN]** — FRD-08 v1.2's 21-template list has no entry obviously matching "HR, a decision is awaiting you" for probation specifically (unlike the dedicated `PROBATION_REVIEW_DUE` above). G-04's generic mechanism covers the *task* notification once the HR-approval `engine_task` exists (§8) via whichever template that task-creation path already uses — check before assuming a new one is needed. |
| HR approves/rejects | **[OPEN]** — same reasoning; FRD-08 v1.2's list has no obvious match. D-P5 (what happens on reject) needs answering first regardless — there may be nothing to notify about on rejection if the FRD intends no further action. |

| Timer | Default | Guard |
|---|---|---|
| Probation review | 14 days before probation ends, cannot be disabled (`setting.PROBATION_REVIEW`, `can_disable=false`) | always on (resolved, D-P1) |

## 11. Acceptance criteria

Onboarding's AC-01…AC-24 are unchanged — see `F01-onboarding/spec.md` §11 (all already have passing tests). New, from FRD-02 §7:

**AC-P1 (FRD-02 §7 "Probation review is created on time")**
```
Given Sanne de Boer's probation ends on 05 Nov 2026
When the date is 22 Oct 2026
Then her manager receives a "Probation review" task
```
(22 Oct is exactly 14 days before 05 Nov — the FRD's own example confirms the 14-day default.)

**AC-P2 (derived from ONB-08, no literal FRD scenario given — [ASSUMPTION], flag before building)**
```
Given a manager submits a probation review with outcome "End employment"
When HR has not yet approved it
Then offboarding does not start
And HR sees a pending decision
```

**AC-P3 (derived from the §5 validation row, no literal FRD scenario given — [ASSUMPTION])**
```
Given a manager has rated all three questions but left the summary blank
When they try to submit
Then the message "Add a summary and choose an outcome." is shown
```

## 12. Decisions & open items

All onboarding decisions D-01…D-23 are unchanged — see `F01-onboarding/spec.md` §12 (resolved). For probation, nothing is resolved yet:

| ID | Topic | Status |
|---|---|---|
| D-P1 | Can the "Probation review" timer be switched off? | **Resolved (2026-10-16, FRD v1.2):** No. FRD-08 v1.2's clean Timers & rules table: "Probation review \| 14 days before end \| Manager \| No". (The old v1.1 PDF extraction was genuinely ambiguous here — confirmed, not a reading error on this spec's part; v1.2 settles it.) |
| D-P2 | Does the reviewed employee see their own probation review result? | **[OPEN]** — v1.2 does not add anything new here either. |
| D-P3 | Exact validation message scope (one combined message for summary+outcome) | Resolved by using the FRD's exact text as-is for both cases (R-SRC-3) — no [OPEN] here, but noted since it's a judgment call about scope, not literal FRD instruction. |
| D-P4 | S15 (HR decision screen) existence and fields | **Confirmed as a real, FRD-acknowledged gap (2026-10-16, FRD v1.2)** — FRD-02 v1.2 itself lists Screen 13 as "not drawn" and tracks it in its own Appendix A #5: "HR approval of 'End employment'... have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." Still **[OPEN]** which of those two resolutions the user wants; this spec's S15 placeholder (§4/wireframes.md) assumes the "draw a dedicated page" option — confirm before implementing. |
| D-P5 | What happens when HR rejects an "End employment" outcome | **[OPEN]** — FRD silent, v1.2 doesn't add anything new here either. |
| D-P6 | Answer-storage schema for probation review's 3 rating questions | **Resolved (2026-10-16, FRD v1.2):** `review_answer` table already exists in the data model (composite PK `review_id, question_id`; `rating`/`yes_no`/`text_answer`/`comment` columns) — confirmed via FRD-02 v1.2 §5's full schema listing. No migration needed, §9 updated. |
| D-P7 | Does probation review need its own e-mail template beyond the generic `TASK_ASSIGNED` (G-04)? | **Resolved (2026-10-16, FRD v1.2) — yes:** `PROBATION_REVIEW_DUE` is a distinct, named template in FRD-08 v1.2's 21-template list, confirmed missing from the real seed data by FRD-08 v1.2's own database cross-check. This spec's first draft was wrong to assume `TASK_ASSIGNED` alone was sufficient — corrected in §9/§10. |

## 13. Definition of done

- [x] Onboarding half: **Done** (built as F01-onboarding; see that spec's own §13 for its gate history — gate 5 review found some non-blocking follow-ups still open there, unrelated to probation).
- [ ] Probation half: **Not started.** Before implementation can begin: D-P2, D-P4 and D-P5 still need the user's answers (CLAUDE.md §2.9.5 "ask when blocked"); D-P1, D-P6 and D-P7 are now resolved (FRD v1.2, 2026-10-16). The CMMN model needs extending (§8) before any backend engine-call code is written against it; the `PROBATION`-type `appraisal_form` needs seeding (FRD-08 dependency, §9); `03-case-file.md` needs updating for the `Probation.hrDecision` 4th field (§8).
- [ ] Every acceptance criterion in §11 has an automated test (none exist yet for probation).
- [ ] Flyway migration for the new `setting` row (`PROBATION_REVIEW`) and the new `email_template` row (`PROBATION_REVIEW_DUE`) — no new table/column needed (D-P6 resolved: `review_answer` already exists).
