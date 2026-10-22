# FRD-08 — Reports and settings (Settings foundation)

| | |
|---|---|
| Status | **Done** (2026-10-17 — all 5 gates complete, see §13) |
| Sources | FRD v1.2 (canonical, decided 2026-10-16): `.claude/docs/source/FRD-08/FRD-08_Reports_and_Settings.docx` ("Split from v1.2 (Oct 15, 2026 · @Rohith)") — full module text, §4 Features/Endpoints/Database, Appendix A cross-check against `V1__init.sql`; plus FRD-00 §2 permissions table, §4 global rules G-01…G-13; cross-references from FRD-02 §6, FRD-05, FRD-06, FRD-07 that depend on Settings existing. Legacy v1.1 FRD-08 (monolithic PDF) superseded — see `.claude/docs/source/README.md` "Order of truth" |
| Wireframes | `.claude/docs/source/FRD-08/WF-08_Reports_and_Settings.html` (v1.2, dedicated FRD-08 wireframe file) — pages `p-hr-reports`, `p-hr-rep-headcount`, `p-hr-rep-bench`, `p-hr-rep-alloc`, `p-hr-rep-apr`, `p-hr-rep-pip`, `p-hr-rep-onb`, `p-hr-rep-dates`, `p-hr-rep-assets`, `p-hr-settings` (tabs `set-0`…`set-4`), `p-hr-form-standard`, `p-hr-form-leadership`, `p-hr-form-probation`, `p-hr-form-new`; modals `m-dept-add`, `m-job-add`, `m-add-item`, `m-new-template`, `m-mail-edit` — every page/tab/modal id below was re-verified directly against this file's markup (not assumed carried over from the old monolithic wireframe HTML). Legacy v6.1 monolithic wireframe HTML superseded for this module. |
| Order of truth | FRD v1.2 (per-module) > this spec > `wireframes.md` > the module's own WF-08 HTML > legacy v1.1 PDF/HTML (CLAUDE.md R-SRC-1, `.claude/docs/source/README.md` "Order of truth", updated 2026-10-16). Conflicts are listed in §12. |

## 0. Status & sources

FRD-08 is the module `.claude/docs/features/README.md` lists as **F09** ("Reports & settings screens") with the note "settings must exist before the other modules. Until F09 builds the settings screens, F01 seeds what it needs with a Flyway seed migration." This spec is written now, ahead of F02–F08, because the FRD itself (FRD-00 §7, referenced by FRD-08 §1 "Constraints & Dependencies": "All other modules depend on these settings") and the already-built F01 feature both depend on the data this module manages. It is filed under `FRD-08-settings-foundation/` per the task that produced it; `.claude/docs/features/README.md`'s own F09 row is **not edited by this spec** (read-only for this change — the README update, if the user wants the row's spec-path filled in, is a separate, deliberate edit).

**This spec describes the real admin capability — CRUD screens, validation, test-send, 8 reports — not the one-time seed.** It does not replace or invalidate `V2__seed_settings.sql` (nor `V6__seed_task_assigned_template.sql`/`V7__overdue_reminder.sql`, which also seed rows this module manages); it describes the screens that let HR manage the *same* rows those migrations already inserted (§9), plus the rows they did not seed (the remaining 15 of 21 e-mail templates, the remaining 12 of 17 `setting` rows, and the appraisal forms, which F01 never touched at all).

**Revision note (2026-10-16):** this spec was originally written against FRD v1.1 (the monolithic PDF), whose "Timers & rules" table had a column/row-wrap artifact in PDF-to-text extraction, forcing several recipient-role values to be reconstructed from corroborating sources and flagged `[ASSUMPTION]` (old D-07). The canonical source is now FRD v1.2's dedicated `FRD-08_Reports_and_Settings.docx`, which gives that same "Timers & rules" table cleanly — 17 rows, each with Rule/Default/Goes to/Can be switched off in clean alignment, no wrap — §3/§5/§9/§12 below are revised to cite the clean table directly; D-07 is resolved, not reconstructed, below. v1.2 also gives exact screen routes (§4), a full per-feature API table (§6), and its own Appendix A/cross-check against the real `V1__init.sql` (§9/§12), each incorporated below.

Nothing in this spec invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and the wireframe already allow, except where explicitly flagged `[ASSUMPTION]`, `[OPEN]` or `[NEEDS APPROVAL]` in §12 (R-SRC-2).

## 1. Scope

**In scope**

- **Settings, 5 tabs, in the FRD's order:**
  1. Departments & job titles — add / remove.
  2. Checklist templates — one tab per template (onboarding and offboarding), add/edit/remove items, create a template (optionally copied from another), archive a template.
  3. Appraisal forms — Standard, Leadership, Probation (+ any future form via "+ New form"): rating-scale labels, sections, questions, who completes each section.
  4. E-mail templates — all 21 FRD-named templates: name, trigger description, recipients, CC, subject, body with placeholders, active switch, test-send.
  5. Timers & rules — all 17 `setting` rows: value, recipient, on/off where switchable; also KPI cycle, notice periods per contract type, password rules, link validity, undo window (all live in the same `setting` table, same tab, per the FRD's own grouping).
- **8 reports** + 1 reports-overview dashboard, each filterable/searchable/sortable/exportable to Excel (R-UI-2): Headcount, Bench, Project allocation, Appraisal status, PIPs, Onboarding, Contracts & probation, Unreturned assets.
- The Flyway migration(s) needed to reach full coverage: the 15 remaining e-mail templates beyond the 6 already seeded by F01/G-04/G-05 (§9 — corrected from an earlier miscount of "17 remaining"), the 12 remaining `setting` rows beyond the 5 already seeded (§9), and a minimum viable seed for the 3 named appraisal forms (so FRD-05/06's own pre-conditions — "at least one active appraisal form exists" — are met before those features are built).

**Explicitly supersedes/extends, does not replace, F01's seed approach:** `V2__seed_settings.sql`'s departments, job titles, 4 checklist templates (62 items), 4 e-mail templates, and 4 `setting` rows (plus G-04/G-05's `TASK_ASSIGNED`/`TASK_OVERDUE` templates and `OVERDUE_REMINDER_DAYS` setting from `V6`/`V7`) stay exactly as seeded. This feature adds the screens to manage those same rows, and the migration(s) to seed what F01 deliberately left out. No row F01 seeded is deleted, renamed, or re-keyed by this feature.

**Out of scope (left to later features, per CLAUDE.md §2.9.4 "never mix features")**

- Offboarding checklist template *content* (a "Standard exit" template is shown in the wireframe's template tabs, but no offboarding template exists yet — F07 seeds it). This spec's checklist-template screens work for `kind = 'OFFBOARDING'` generically (the schema already allows it); they just have nothing to show until F07 creates a row.
- Anything using the settings this module manages (onboarding templates are *consumed* by F01, KPI cycle by F05, notice periods by F08, etc.) — FRD-08 only manages the configuration, never the workflow that reads it (R-ARC-1, R-ARC-7).
- Keycloak realm/password-policy configuration itself — `PASSWORD_MIN_LENGTH` is a `setting` row this module edits, but wiring an edited value into the live Keycloak realm policy (as F01 gate-5 did once, by hand, for the initial value) is a separate integration concern, flagged in §12.

## 2. Roles & permissions

Per FRD-00 §2's permissions table: **"Reports and settings: HR — Yes, Manager — No, Employee — No, IT admin — No."** Every screen and endpoint in this spec is **HR-only**, with no exceptions and no per-employee self-scoping (G-13 does not apply — there is no "own record" sense for a department or an e-mail template).

| Capability | HR | Manager / Employee / IT admin |
|---|---|---|
| View/edit departments & job titles | Yes | No |
| View/edit/archive checklist templates & items | Yes | No |
| View/edit/archive appraisal forms, sections, questions | Yes | No |
| View/edit e-mail templates, send test e-mail | Yes | No |
| View/edit timers & rules | Yes | No |
| View/export all 8 reports | Yes | No |

Every endpoint checks the role on the server (R-API-2/G-02); hiding the "Settings"/"Reports" menu items for other roles is never the only protection (R-UI-7) — matches F01's existing `@PreAuthorize`/service-layer pattern.

## 3. Business rules

Rules copied from FRD-08 v1.2's "Hard Business Invariants" table, within its "4. Validation Rules & Business Invariants" section (cited as §3 "Business rules" in the superseded v1.1 draft of this spec — R-SRC-3: wording kept exact). v1.2 marks ADM-01 through ADM-04 **MUST** and ADM-05 **SHOULD**; this spec treats all five as MUST per CLAUDE.md §2.1 R-SRC-1 (the FRD's own stated priority wins, and ADM-05 is in any case already a hard requirement of R-API-5 "every change is audited"):

| ID | Rule |
|---|---|
| ADM-01 | Rule names and modules cannot be changed; only the value, the receiver and on/off. |
| ADM-02 | Changes to timers apply to new timers only; running cases keep their dates. |
| ADM-03 | A department or job title in use cannot be removed. |
| ADM-04 | An archived template or form is no longer offered but stays on existing cases. |
| ADM-05 | Changes to settings are logged with who and when. |

How each is implemented here:

- **ADM-01** — `setting.code`, `setting.category`, `setting.module`, `setting.label` are never accepted in the PUT body (§6 C30); only `value_int`/`value_text`, `recipient_role` (where the row has one) and `enabled` (where `can_disable = true`) are writable. The same applies to e-mail templates' `code` (§6 C27): never editable, never shown as an editable field.
- **ADM-02** — already the behaviour the running system has: `onboarding.it_ready_by`/due dates, `pip.checkin_days`/`duration_days`/`extension_days`, etc. are copied into the case/row at the moment a cycle starts (R-ARC-7, "deliberate copies" in `02-data-model.md` §5). This feature changes nothing about that; it only changes the `setting` row new cycles will read. No code in this feature touches an already-running case's copied values.
- **ADM-03** — enforced for free by the existing `ON DELETE RESTRICT` default on `employee.department_id`/`employee.job_title_id` (02-data-model.md §2.1/convention). `DELETE /api/settings/departments/{id}` and `DELETE /api/settings/job-titles/{id}` (§6 C03/C06) catch the resulting FK-violation and return `409 {"code":"ITEM_IN_USE","message":"This item is in use and cannot be removed."}` rather than a raw DB error.
- **ADM-04** — `checklist_template.archived_at` and `appraisal_form.status = 'ARCHIVED'` already exist in the schema for exactly this. Archiving (§6 C10/C24) only sets that column/status; it never touches `checklist_item` (already-instantiated, snapshotted per `02-data-model.md` §5 "Deliberate copies kept" — see §7 below) or any `review`/`appraisal` row that already references the form.
- **ADM-05** — every write endpoint in this spec writes `audit_log` (`entity_type` = `DEPARTMENT`/`JOB_TITLE`/`CHECKLIST_TEMPLATE`/`CHECKLIST_TEMPLATE_ITEM`/`APPRAISAL_FORM`/`FORM_SECTION`/`FORM_QUESTION`/`EMAIL_TEMPLATE`/`SETTING`, `changed_by` = the HR user, `before_value`/`after_value` snapshots), matching R-API-5.

## 4. Screens

Screen numbers/ids below are FRD-08 §3's own ("WF-08 ref" column) — this spec's S-numbers are kept for cross-reference within this document; the FRD's own "Screen N" numbers are given in the rightmost column.

| # | Screen | Who | Route | Wireframe page / element | FRD | FRD §3 screen # |
|---|---|---|---|---|---|---|
| S1 | Settings — tab 1 Departments & job titles (+ "Add department"/"Add job title" pop-ups) | HR | `/settings?tab=departments` | `p-hr-settings` tab `set-0`; modals `m-dept-add`, `m-job-add` | FRD-08 §2 "Settings" item 1, §3 | Screen 10 (+ 11, 12) |
| S2 | Settings — tab 2 Checklist templates (+ "+ Add item" / "+ New template" pop-ups) | HR | `/settings?tab=checklists` | `p-hr-settings` tab `set-1`; modals `m-add-item`, `m-new-template` | FRD-08 §2 item 2, §3 | Screen 10 (+ 13, 14) |
| S3 | Settings — tab 3 Appraisal forms, list | HR | `/settings?tab=forms` | `p-hr-settings` tab `set-2` | FRD-08 §2 item 3, §3 | Screen 10 |
| S4 | Appraisal form editor (Standard / Leadership / Probation / new) | HR | `/settings/forms/:formId`, `/settings/forms/new` | `p-hr-form-standard`, `p-hr-form-leadership`, `p-hr-form-probation`, `p-hr-form-new` | FRD-08 §2 item 3, §3 | Screen 15 |
| S5 | Settings — tab 4 E-mail templates (+ editor pop-up with test-send) | HR | `/settings?tab=emails` | `p-hr-settings` tab `set-3`; modal `m-mail-edit` | FRD-08 §2 item 4, §3 | Screen 10 (+ 16) |
| S6 | Settings — tab 5 Timers & rules | HR | `/settings?tab=timers` | `p-hr-settings` tab `set-4` | FRD-08 §2 item 5, §3 | Screen 10 |
| S7 | Reports overview (KPI tiles + 8 report cards) | HR | `/reports` | `p-hr-reports` | FRD-08 §2 "Reports", §3 | Screen 1 |
| S8 | Report — Headcount | HR | `/reports/headcount` | `p-hr-rep-headcount` | FRD-08 §2, §4.1 | Screen 2 |
| S9 | Report — Bench | HR | `/reports/bench` | `p-hr-rep-bench` | FRD-08 §2, §4.1 | Screen 3 |
| S10 | Report — Project allocation | HR | `/reports/allocation` | `p-hr-rep-alloc` | FRD-08 §2, §4.1; FRD-03 | Screen 4 |
| S11 | Report — Appraisal status | HR | `/reports/appraisal-status` | `p-hr-rep-apr` | FRD-08 §2, §4.1 | Screen 5 |
| S12 | Report — PIPs | HR | `/reports/pips` | `p-hr-rep-pip` | FRD-08 §2, §4.1 | Screen 6 |
| S13 | Report — Onboarding | HR | `/reports/onboarding` | `p-hr-rep-onb` | FRD-08 §2, §4.1 | Screen 7 |
| S14 | Report — Contracts & probation | HR | `/reports/contracts-probation` | `p-hr-rep-dates` | FRD-08 §2, §4.1 | Screen 8 |
| S15 | Report — Unreturned assets | HR | `/reports/unreturned-assets` | `p-hr-rep-assets` | FRD-08 §2, §4.1 | Screen 9 |

**Corrected from the pre-v1.2 draft:** S10's route is `/reports/allocation` (the FRD's own `reportId` for this report, §4.1), not `/reports/project-allocation` as this spec previously guessed; the frontend router and the `GET /api/reports/{reportId}` path (§6) both follow the FRD's `reportId` literally. Every other route above is new in this revision — v1.1 gave no exact routes at all.

Menu entries "Settings" and "Reports" (HR only, R-UI-7) are added to the HR sidebar, replacing the F01 placeholder note "Appraisals, Reports, Settings, Projects... not built" for these two specifically.

## 5. Fields & validation

Validation messages below are copied exactly from FRD-08 v1.2's "4. Validation Rules & Business Invariants" section ("Validation Table" — R-SRC-3; cited as "§5" in the superseded v1.1 draft of this spec, the section is numbered "4" in v1.2's own text, distinct from the unnumbered "Features, Endpoints & Database" section whose own subsections are cited as §4.1–§4.6 in §6 below — both exist in the source docx under similar numbers, so citations here spell out the heading name to avoid ambiguity). Cross-checked word-for-word against v1.2's table: every message below (including "This name already exists.", "This item is in use and cannot be removed.", "This field is required.", "Add at least one section with a question.", "Choose at least one recipient.", "Enter valid e-mail addresses, separated by commas.", "Enter a whole number greater than 0.", "Enter a number between 8 and 64.", "Choose a KPI cycle.") matches v1.2 exactly — no wording changed in this revision. Fields are exactly those the wireframe's forms/modals show, cross-checked against `02-data-model.md` (R-SRC-2) — see §12 for the one field the wireframe shows that the data model does not support (job title Grade/Department).

### S1 — Departments & job titles

| Field | Rule | Message |
|---|---|---|
| Department name* (`m-dept-add`) | Required, unique | "This field is required." / "This name already exists." |
| Job title* (`m-job-add`) | Required, unique | "This field is required." / "This name already exists." |
| Remove (either list) | Blocked if in use | "This item is in use and cannot be removed." (ADM-03) |

`m-job-add`'s wireframe also shows **Grade** and **Department** selects on the job title form — **built since 2026-10-19** (§12 D-01 reversed, V15): both are required on create, alongside **Job title name**.

### S2 — Checklist templates

| Field | Rule | Message |
|---|---|---|
| Item* / Section* / Owner* / Due (days from start)* / Mandatory* (`m-add-item`) | All required | "This field is required." |
| Template name* / Type* (Onboarding/Offboarding) / Copy items from / Used for job title (`m-new-template`) | Name required, unique | "This field is required." / "This name already exists." |
| Remove item | — | No FRD message for this case specifically; see §12 D-02 for the resolved behaviour. |
| Archive template | — | No confirmation message given by the FRD; a plain action (ADM-04). |

Section options: Documents & admin, Equipment issued, Accounts & installations, Orientation (matches `checklist_template_item.section` CHECK's onboarding values; the offboarding-only values `RETURN_ASSETS`/`REMOVE_ACCESS`/`HR_HANDOVER`/`REASSIGN_TEAM` are offered only when the template's `kind = 'OFFBOARDING'`). Owner options: HR, IT admin, Manager, Employee (matches `owner_role`, minus `SYSTEM` which no admin screen ever sets by hand).

### S3/S4 — Appraisal forms

| Field | Rule | Message |
|---|---|---|
| Form name* | Required, unique | "This field is required." / "This name already exists." |
| Used for* | Required (All employees / Tech leads & managers / Probation reviews) | "This field is required." |
| Status | Draft / Active / Archived | — |
| Overall score | Weighted average / Manager decides | — |
| Rating scale (5 rows: score 1–5, Label, Description) | Editable text per row | — (no FRD message; free text) |
| Section name* | Required | "This field is required." |
| "Completed by" (Self/Peer/Manager/HR combination)* | At least one ticked (DB CHECK: `by_self OR by_peer OR by_manager OR by_hr`) | "Choose at least one reviewer." — **[ASSUMPTION], found during Gate 5 review**: the FRD gives no message for this specific case (only the generic "This field is required." for the row as a whole); this wording was coined during Gate 2 by analogy with "Choose at least one recipient." (S5), the FRD's own wording for the structurally identical e-mail-recipient-checkboxes case. Flag for the user to confirm or replace. |
| Question: Text*, Description, Answer type* (1–5 scale / Yes-No / Text) | Text + type required | "This field is required." |
| Save (whole form) | At least one section with one question | "Add at least one section with a question." |

### S5 — E-mail templates

| Field | Rule | Message |
|---|---|---|
| Template name*, Sent when* (trigger description), Subject*, Body* | Required | "This field is required." |
| Recipients (Employee/Manager/HR/IT admin/Task owner/Peer) | At least one ticked | "Choose at least one recipient." |
| CC | Valid e-mail addresses, comma-separated | "Enter valid e-mail addresses, separated by commas." |
| Active | On/off switch | — |
| Send test e-mail | — | Toast: "Test e-mail sent to {HR user's e-mail}." (wireframe literal example: "emma@cubeworks.nl") |

Placeholder chips offered in the body editor (from the wireframe, verbatim): `{employee_name}`, `{employee_first_name}`, `{manager_name}`, `{due_date}`, `{task_name}`, `{task_link}`, `{start_date}`, `{last_day}`.

### S6 — Timers & rules

| Field | Rule | Message |
|---|---|---|
| Timer value (days) | Whole number > 0 | "Enter a whole number greater than 0." |
| Password minimum length | 8–64 | "Enter a number between 8 and 64." |
| KPI cycle | Quarterly / Half-yearly / Yearly | "Choose a KPI cycle." |
| Recipient role (where applicable) | — | — |
| Active/can-disable switch (only on rows where `can_disable = true`) | — | — |

Rule name, module and code are **read-only** on every row (ADM-01) — shown as plain text, never an editable field.

### S7–S15 — Reports

Every report: sortable/filterable/searchable table (R-UI-2), download-icon export to `.xlsx`, rows link to the employee or task (FRD-08 §3). Columns and filters per report (from the wireframe, which is the only source giving exact columns — the FRD's prose only names what each report "Shows" at a high level, matched below):

| Report | FRD "Shows" | Filters | Columns |
|---|---|---|---|
| S7 Overview | — | — | KPI tiles: Headcount, Bench, Appraisal completion, Unreturned assets, each with its wireframe caption ("+2 vs last month", "8% of billable staff", "Round 2026", "1 ex-employee"); 8 report cards; "Export all to Excel" download icon (C48) |
| S8 Headcount | Employees per department, joiners and leavers this year, permanent and fixed-term | Department | Department, Employees, Joiners (this year), Leavers (this year), Permanent, Fixed-term |
| S9 Bench | Employees without an active project, since when, days, last project | Manager, search | Employee, Job title, Manager, On bench since, Days, Last project |
| S10 Project allocation | Allocation per employee and allocation status (FRD-03) | Status, search | Employee, Projects, Allocation %, Status |
| S11 Appraisal status | Per employee: self, peers, manager review, client feedback (average · number of reviews), result | Manager, Result, search | Employee, Manager, Self, Peers, Manager review, Client feedback (avg · reviews), Result |
| S12 PIPs | Active and pending PIPs, start, final review date, days left, extensions | Status | Employee, Manager, Status, Started, Final review, Days left, Extensions |
| S13 Onboarding | Employees in onboarding, start date, template, checklist progress, IT request status | — | Employee, Start date, Template, Checklist (%), IT request, Open items |
| S14 Contracts & probation | Probation ends, contract ends and final PIP reviews in the next 90 days | Type (Probation/Contract/PIP) | Employee, Type, Date, Days, Owner, Status |
| S15 Unreturned assets | Assets still with leavers or due for return | Status | Asset no., Asset, Employee, Last working day, Status |

## 6. API

Conventions: base path `/api`, camelCase JSON, dates `yyyy-MM-dd` (R-API-1). Errors follow R-API-3 exactly:
```
400 {"code":"VALIDATION_FAILED","errors":[{"field":"name","message":"This field is required."}]}
409 {"code":"DUPLICATE_NAME","message":"This name already exists."}
409 {"code":"ITEM_IN_USE","message":"This item is in use and cannot be removed."}
409 {"code":"STALE_VERSION"}   403 {"code":"FORBIDDEN"}   404 {"code":"NOT_FOUND"}
```
All endpoints below require the HR role (§2); omitted from each row for brevity.

**Revised to match FRD-08 v1.2 §4's own endpoint tables (4.2–4.6), which nest every settings-management endpoint under `/api/settings/...` (not flat resource roots like `/api/departments`), and identify e-mail templates and timer/rule rows by their immutable `code` in the path, not a numeric `{id}`** — this reads better against ADM-01 ("rule names... cannot be changed") than this spec's original numeric-id guess, since the path itself can never need to change. The FRD's tables give method + endpoint + purpose + tables only (no request/response bodies, status codes, or query params); those are carried over unchanged from the original draft where the FRD is silent, per R-SRC-2.

### Departments & job titles (FRD-08 §4.2)

| # | Method & path | Notes |
|---|---|---|
| C01 | `GET /api/settings/departments` | `[{id,name,employeeCount}]` — count for the "People" column; `POST` on the same path creates (see C02) |
| C02 | `POST /api/settings/departments` `{name}` | `201 {id,name}` / `400` / `409 DUPLICATE_NAME` |
| C03 | `DELETE /api/settings/departments/{id}` | `204` / `409 ITEM_IN_USE` (ADM-03, FK RESTRICT) |
| C04 | `GET /api/settings/job-titles` | `[{id,name}]` (no grade/department — §5 D-01) |
| C05 | `POST /api/settings/job-titles` `{name}` | `201 {id,name}` / `400` / `409 DUPLICATE_NAME` |
| C06 | `DELETE /api/settings/job-titles/{id}` | `204` / `409 ITEM_IN_USE` |

### Checklist templates (FRD-08 §4.3)

| # | Method & path | Notes |
|---|---|---|
| C07 | `GET /api/settings/checklist-templates?kind=&includeArchived=` | templates by kind, with item count, archived flag |
| C08 | `GET /api/settings/checklist-templates/{id}` | full detail incl. items, for the admin tab (distinct from F01's read-only `GET /api/checklist-templates/{id}/preview`, A06, which stays where it is — it is not a settings-management endpoint) |
| C09 | `POST /api/settings/checklist-templates` `{name,kind,jobTitleId?,copiedFromId?}` | `201 {id}` / `400` / `409 DUPLICATE_NAME`; `copiedFromId` → `checklist_template.copied_from_id` |
| C10 | `POST /api/settings/checklist-templates/{id}/archive` | `200` (ADM-04) |
| C11 | `POST /api/settings/checklist-templates/{id}/items` `{name,section,ownerRole,mandatory,dueOffsetDays,itemKind?,policyDocumentId?}` | `201 item` / `400`. `itemKind` (optional, default GENERIC) = `checklist_template_item.item_kind`; `policyDocumentId` (a `POLICY` document uploaded via F01 A07) is kept only for `POLICY_ACK` (DB CHECK) — added 2026-10-21, see F01 spec §12 D-24(g) |
| C12 | `PUT /api/settings/checklist-templates/{id}/items/{itemId}` `{name,section,ownerRole,mandatory,dueOffsetDays,itemKind?,policyDocumentId?}` | `200` / `400`; nested under the template, per FRD §4.3 (was a flat `/api/checklist-template-items/{itemId}` in the pre-v1.2 draft); `itemKind`/`policyDocumentId` as C11 |
| C13 | `DELETE /api/settings/checklist-templates/{id}/items/{itemId}` | `204` / `409 ITEM_IN_USE` (§12 D-02) |

### Appraisal forms (FRD-08 §4.4)

| # | Method & path | Notes |
|---|---|---|
| C14 | `GET /api/settings/appraisal-forms` | list incl. section count, status, last-edited |
| C15 | `POST /api/settings/appraisal-forms` `{name,usedFor}` | `201 {id}` (status defaults `DRAFT`) / `400` / `409 DUPLICATE_NAME` |
| C16 | `GET /api/settings/appraisal-forms/{id}` | full, sections + questions nested |
| C17 | `PUT /api/settings/appraisal-forms/{id}` `{name,usedFor,status,overallScoreMethod,label1..label5,version}` | `200` / `400` / `409 STALE_VERSION` / `400` "Add at least one section with a question." (only enforced when `status` is being set to `ACTIVE`) |
| C18 | `POST /api/settings/appraisal-forms/{id}/sections` `{name,bySelf,byPeer,byManager,byHr}` | `201` / `400` |
| C19 | `PUT /api/settings/appraisal-forms/{id}/sections/{sectionId}` `{name,bySelf,byPeer,byManager,byHr}` | `200` / `400`; nested under the form, per FRD §4.4 (was a flat `/api/appraisal-form-sections/{id}` in the pre-v1.2 draft) |
| C20 | `DELETE /api/settings/appraisal-forms/{id}/sections/{sectionId}` | `204` (cascades its questions, schema `on delete cascade`) |
| C21 | `POST /api/settings/form-sections/{id}/questions` `{text,description,answerType,required}` | `201` / `400`; FRD §4.4 keeps this one flat under `form-sections`, not re-nested under the form |
| C22 | `PUT /api/settings/form-sections/{id}/questions/{questionId}` `{text,description,answerType,required}` | `200` / `400` |
| C23 | `DELETE /api/settings/form-sections/{id}/questions/{questionId}` | `204` |
| C24 | `POST /api/settings/appraisal-forms/{id}/archive` | `200` (ADM-04) |

### E-mail templates (FRD-08 §4.5)

| # | Method & path | Notes |
|---|---|---|
| C25 | `GET /api/settings/email-templates` | `[{code,name,triggerDescription,recipientsSummary,active}]` — the 21 templates |
| C26 | `GET /api/settings/email-templates/{code}` | full, incl. subject/body/cc/recipient flags; `code` is the path key and is never itself editable (ADM-01) — identified by `code`, not a numeric id, per FRD §4.5 (was `/api/email-templates/{id}` in the pre-v1.2 draft) |
| C27 | `PUT /api/settings/email-templates/{code}` `{name,triggerDescription,subject,body,cc,toEmployee,toManager,toHr,toItAdmin,toTaskOwner,toPeer,active,version}` | `200` / `400 VALIDATION_FAILED` / `400` "Choose at least one recipient." / `400` "Enter valid e-mail addresses, separated by commas." / `409 STALE_VERSION` |
| C28 | `POST /api/settings/email-templates/{code}/test` | `202`; renders the current unsaved-or-saved draft with sample data and sends it to the requesting HR user's own e-mail address — no recipient field (§12 D-03); writes to `email_outbox` per FRD §4.5 |

No `POST /api/settings/email-templates` (create) — the 21 templates are a closed, code-identified set (§12 D-04).

### Timers & rules (FRD-08 §4.6)

| # | Method & path | Notes |
|---|---|---|
| C29 | `GET /api/settings/timers` | all 17 `setting` rows (category `TIMER`): `{code,category,module,label,valueInt,valueUnit,valueText,recipientRole,canDisable,enabled}` |
| C30 | `PUT /api/settings/timers/{code}` `{valueInt?,valueText?,recipientRole?,enabled?}` | one row per request, per FRD §4.6 ("change value, receiver or on/off only... new timers only") — **corrected from this spec's original single bulk `PUT /api/settings`**: the FRD gives no bulk-save endpoint, only a per-`code` `PUT`. The wireframe's single "Save" button on S6 (one button, many edited rows) is implemented client-side as one `PUT /api/settings/timers/{code}` call per changed row, fired together when Save is pressed — this keeps R-UI-4's single save/cancel affordance without inventing an endpoint the FRD doesn't give. `code`/`category`/`module`/`label` are never in the request body (ADM-01); `400` "Enter a whole number greater than 0." / `400` "Enter a number between 8 and 64." / `400` "Choose a KPI cycle." |

### Reports (FRD-08 §4.1)

All reports share two endpoint shapes, per FRD §4.1: `GET /api/reports/{reportId}` (filters, search, sort, paging) and `GET /api/reports/{reportId}/export` (Excel). `reportId` values and the tables/views each one reads, per FRD §4.1's own mapping:

| # | Method & path | `reportId` | Tables / views (FRD §4.1) |
|---|---|---|---|
| C31 | `GET /api/reports/summary` | *(not in FRD's 8-report list — this spec's own addition for S7's KPI tiles, composed from the same tables as C32/C34/C38/C46)* | `employee`, `v_bench`, `v_appraisal_status`, `v_unreturned_assets` |
| C32 / C33 | `GET /api/reports/headcount?departmentId=`, `.../export` | `headcount` | `employee`, `department`, `contract`, `employee_exit` |
| C34 / C35 | `GET /api/reports/bench?managerId=&q=`, `.../export` | `bench` | `v_bench`, `employee` (manager filter) |
| C36 / C37 | `GET /api/reports/allocation?status=&q=`, `.../export` | `allocation` | `v_employee_allocation`, `project_assignment`, `project` |
| C38 / C39 | `GET /api/reports/appraisal-status?managerId=&result=&q=`, `.../export` | `appraisal-status` | `v_appraisal_status` |
| C40 / C41 | `GET /api/reports/pips?status=`, `.../export` | `pips` | `v_pip`, `checkin` |
| C42 / C43 | `GET /api/reports/onboarding`, `.../export` | `onboarding` | `onboarding`, `checklist_item`, `checklist_template` |
| C44 / C45 | `GET /api/reports/contracts-probation?type=`, `.../export` | `contracts-probation` | `v_upcoming_dates` |
| C46 / C47 | `GET /api/reports/unreturned-assets?status=`, `.../export` | `unreturned-assets` | `v_unreturned_assets` |
| C48 | `GET /api/reports/export` | *(not in FRD's 8-report list — added 2026-10-19 on user request for the wireframe's "Export all to Excel" icon on S7, see D-11)* — one `.xlsx` (`reports.xlsx`), one sheet per report in card order, each unfiltered and built from the same service method as its JSON endpoint (spec §7) | all eight report sources above |

**Corrected from the pre-v1.2 draft:** C36/C37's `reportId` is `allocation` (`/api/reports/allocation`), not `project-allocation` as originally guessed — this also fixes S10's route in §4. C40 now also reads `checkin` (PIP check-ins) and C42 reads `checklist_template` rather than `employee`, per the FRD's own table/view mapping; neither change affects the response shape this spec already described, only which source tables the service method joins.

Every `.../export` variant takes the same filters as its `GET` sibling and returns `.xlsx` (R-UI-2).

## 7. Backend behaviour

- **Transactions & audit.** Every write endpoint (C02/C03/C05/C06/C09–C13/C15/C17–C24/C27/C30) runs one transaction covering its business rows + `audit_log` (ADM-05/R-API-5). None of these endpoints call the CaseFabric engine — settings are read by other modules' own services when *they* act, never pushed to the engine (R-ARC-1, R-ARC-2: no settings value belongs in a case file).
- **New rows are visible immediately everywhere.** C02/C05 insert into the same `department`/`job_title` tables F01's `GET /api/lookups/onboarding` (A02) already reads; no cache, no second copy — AC-01 (new job title appears in the onboarding form) holds without extra wiring.
- **Template edits never touch running cases (ADM-04).** `checklist_item` rows already copy `name`/`section`/`owner_role`/`mandatory` at creation time (`02-data-model.md` §5, "Deliberate copies kept" — specifically called out there as "Template edits and archiving must not change running cases (ADM-04)"). C12 (edit a template item) and C10 (archive a template) therefore only ever touch `checklist_template`/`checklist_template_item`; they never write `checklist_item`. A checklist template item that has already been instantiated into at least one `checklist_item` row cannot be *deleted* (C13), because `checklist_item.template_item_id` has no `ON DELETE CASCADE`/`SET NULL` (the global default is `ON DELETE RESTRICT`, `02-data-model.md` conventions) — attempting it returns `409 ITEM_IN_USE` (§12 D-02), the same shape as ADM-03's department/job-title message, reused by analogy since the FRD gives no message of its own for this case.
- **Archived templates/forms stop appearing in pickers, not in history.** C10/C24 flip `archived_at`/`status='ARCHIVED'`; every *read* endpoint used to populate a dropdown for a *new* thing (F01's A02 template dropdown, a future "+ New review" form picker) filters `archived_at is null`/`status != 'ARCHIVED'`. Anything that already references the row (an existing `checklist_item`, an existing `review.form_id`) keeps working unchanged (ADM-04).
- **E-mail template edits apply to the next send, not to `email_outbox` rows already written.** `email_outbox.subject`/`body` are a rendered snapshot at send time (`02-data-model.md` §5); editing a template (C27) changes what the *next* trigger renders, never a row already in the outbox.
- **Timer/rule edits apply to new cycles only (ADM-02).** C30 only ever updates the `setting` table. Every place elsewhere in the application that starts a timed cycle (PIP, onboarding, bench check, etc.) already reads the *current* `setting` value and copies it into the new row/case at that moment (`02-data-model.md` §5's "Deliberate copies kept" list — `pip.checkin_days`/`duration_days`/`extension_days`, `onboarding`'s engine-task due dates, etc.); none of that logic is touched by this feature, which only ever changes the table those reads come from.
- **Test-send (C28)** renders the template's current saved values with placeholder sample data (e.g. `{employee_name}` → "Jesse Willems") and writes one `email_outbox` row addressed to the requesting HR user, same send pipeline (R-ARC-6) as every other outgoing e-mail — it is a real send through the existing outbox/Mailcatcher path, not a separate preview-only mechanism.
- **Reports are read-only** and never write `audit_log` or touch the engine. Each report's `GET` and `.../export` share one query/service method so the on-screen rows and the exported `.xlsx` are always identical (no separate "export" code path that could drift).

## 8. Case model needs

**None.** No screen or endpoint in this feature starts, updates or reads a CaseFabric case, writes a case-file item, or defines a task/timer/milestone. Settings are configuration that *other* modules' case models read indirectly (via Spring, never directly by the engine, R-ARC-1/R-ARC-7) when those modules' own features are built. `03-case-file.md` is unchanged by this feature.

## 9. Data touched

**Tables (all already in `02-data-model.md`, no new table required for the core scope):** `department`, `job_title`, `checklist_template`, `checklist_template_item`, `appraisal_form`, `form_section`, `form_question`, `email_template`, `setting`, `audit_log`. (`checklist_item` and `review`/`appraisal` are read-only from this feature's point of view — never written here, per §7.)

**Views read by the reports (already in `02-data-model.md` §2.9, no new view required):** `v_bench` (S9), `v_employee_allocation` (S10), `v_appraisal_status` (S11), `v_pip` (S12), `v_upcoming_dates` (S14), `v_unreturned_assets` (S15). S8 (Headcount) and S13 (Onboarding) are computed by aggregate queries over `employee`/`department` and `onboarding`/`checklist_item`/`employee` respectively — no view needed (a `v_headcount` convenience view is a possible implementation choice but not required; flagged as an open implementation detail, not a schema requirement, in §12).

**New Flyway migration(s) this feature adds (on top of, never replacing, `V2`/`V6`/`V7`):**

- **E-mail templates — gap analysis, verified against the real `V2__seed_settings.sql`/`V6`/`V7` (corrected from the pre-v1.2 draft, which miscounted this as "17 more"):** FRD-08 v1.2 names 21 templates (§2 "E-mail templates" table). The real seed migrations already inserted 6: `WELCOME_NEW_EMPLOYEE`, `SET_PASSWORD`, `IT_EQUIPMENT_REQUEST`, `ONBOARDING_COMPLETED` (`V2`), `TASK_ASSIGNED` (`V6`), `TASK_OVERDUE` (`V7`) — this spec can confirm that exact count directly (unlike FRD-08 v1.2's own Appendix A note, "the 17 timer settings and 21 e-mail templates are seeded by `V2__seed_settings.sql`, which was not supplied [to the FRD's author], so their codes could not be verified" — this spec *does* have `V2`/`V6`/`V7` and has verified them row by row). FRD-08 v1.2's own cross-check independently confirms two of these six by name: "Task assigned, Task overdue, Probation review due and Contract ending have no code in any endpoint or timer row; add them (e.g. `TASK_ASSIGNED`, `TASK_OVERDUE`, `PROBATION_REVIEW_DUE`, `CONTRACT_ENDING`)" — `TASK_ASSIGNED`/`TASK_OVERDUE` are confirmed as already-correct existing codes (built today for G-04/G-05); `PROBATION_REVIEW_DUE` and `CONTRACT_ENDING` are confirmed **missing** and both appear in this migration's list below. **15 more `email_template` rows are needed to reach 21** (21 − 6 = 15, not 17): `RESET_PASSWORD`, `PROBATION_REVIEW_DUE`, `CONTRACT_ENDING`, `APPRAISAL_ROUND_OPENED`, `PEER_REVIEW_REQUEST`, `APPRAISAL_FINALISED`, `DEV_PLAN_SUBMITTED`, `DEV_PLAN_DECISION`, `PROJECT_ASSIGNED`, `PIP_NOTICE`, `PIP_DISPUTED`, `PIP_OUTCOME`, `RESIGNATION_RECEIVED`, `OFFBOARDING_STARTED`, `ASSET_RETURN_REMINDER` — trigger descriptions and recipients copied from FRD-08 v1.2 §2's own tables ("E-mail templates" and the cross-reference to §4.5's recipient-flag columns); **bodies remain placeholders** (the FRD gives exact wording for none of the 21 templates' bodies, including the 3 F01 already filled in verbatim from its own earlier-established placeholder convention for `SET_PASSWORD`) — same treatment, editable by HR afterward, flagged `[ASSUMPTION]` same as F01's `SET_PASSWORD`/`TASK_ASSIGNED`/`TASK_OVERDUE` precedent.
- **Timers & rules — 12 more `setting` rows, reaching the FRD's own 17-row total (D-07 now resolved against the clean v1.2 table, not reconstructed):** the real seed migrations already inserted 5: `ONBOARDING_OVERDUE`, `PASSWORD_MIN_LENGTH`, `SET_PASSWORD_LINK_DAYS`, `UNDO_WINDOW_SECONDS` (`V2`), `OVERDUE_REMINDER_DAYS` (`V7`). FRD-08 v1.2's clean "Timers & rules" table (§2, 17 rows: Rule / Default / Goes to / Can be switched off) gives every remaining row's recipient and disable-flag directly, correcting several values this spec previously had to infer: `BENCH_CHECK` (7 days, `MANAGER`, cannot disable), `DEV_PLAN_PROGRESS_CHECK` (14 days, `MANAGER`, cannot disable), `PIP_CHECKIN` (14 days, `MANAGER`, cannot disable), `PIP_DURATION` (90 days, `MANAGER_HR`, cannot disable), `PIP_EXTENSION` (30 days, `MANAGER_HR`, can disable, default on), `PROBATION_REVIEW` (14 days before end, `MANAGER`, **cannot disable** — corrected from "can disable" in the pre-v1.2 draft; the clean table's own row reads "Probation review | 14 days before end | Manager | No"), `CONTRACT_RENEWAL_REMINDER` (60 days before end, **`HR`** — corrected from `MANAGER_HR`; the clean table reads "Contract renewal reminder | 60 days before end | HR | Yes" — can disable), `ASSET_RETURN_REMINDER` (3 days before last day, `EMPLOYEE`, can disable), `RESET_PASSWORD_LINK_MINUTES` ("Reset-password link valid for", 60 minutes, `USER`, cannot disable), `KPI_CYCLE` (`QUARTERLY`, `value_text`, **`MANAGER`** — corrected from `ALL_USERS`; the clean table reads "KPI cycle | Quarterly... | Manager | No" — cannot disable), `NOTICE_PERIOD_PERMANENT` (30 days, **`HR`** — corrected from no recipient/`NULL`; the clean table reads "Notice period – permanent contract | 30 days | HR | No" — cannot disable), `NOTICE_PERIOD_FIXED_TERM` (30 days, **`HR`** — same correction, cannot disable). The other 7 rows (`BENCH_CHECK`, `DEV_PLAN_PROGRESS_CHECK`, `PIP_CHECKIN`, `PIP_DURATION`, `PIP_EXTENSION`, `ASSET_RETURN_REMINDER`, `RESET_PASSWORD_LINK_MINUTES`) match this spec's earlier (reconstructed) values exactly, now confirmed rather than assumed. None of these 12 rows is marked `[ASSUMPTION]` any longer — see §12 D-07.
- **Drift found between the already-applied seed migrations and the v1.2 clean table (new, not previously known — see §12 D-09):** `ONBOARDING_OVERDUE` was seeded by `V2__seed_settings.sql` with `can_disable = false` ("Required"), on the pre-v1.2 draft's reading of the garbled PDF table. FRD-08 v1.2's clean table instead reads "Onboarding overdue | 3 days after start date | HR | **Yes**" (can be switched off). `V2` is an already-applied migration and **must not be edited** (CLAUDE.md: "never edit an applied migration"); fixing this, if approved, needs a new forward migration (e.g. `V8`) to `update hr.setting set can_disable = true where code = 'ONBOARDING_OVERDUE'`. Flagged `[NEEDS APPROVAL]` — not a blocker for this feature to ship, since the S6 screen can be built either way, but the toggle will currently show "Required" (no switch) where the FRD now says there should be one. Separately, minor and lower-priority: `PASSWORD_MIN_LENGTH` was seeded with `recipient_role = NULL`; the clean table lists "All users" in the "Goes to" column for this row. Since this setting has no notification target in practice (it is a platform policy, not a trigger), this is noted but not treated as actionable without user confirmation.
- **A minimum viable seed for `appraisal_form`/`form_section`/`form_question`** — Standard, Leadership and Probation forms, each `status='ACTIVE'`, each with at least one section and one question (so FRD-05/FRD-06's own stated pre-condition "at least one active appraisal form exists" is met before those features are built). Exact section/question wording is **not** given by the FRD; the wireframe's example content (sections "Results & KPIs", "Core competencies", etc.) is a reasonable starting set, flagged `[ASSUMPTION]`, editable by HR afterward — same placeholder-content pattern F01 used for its checklist templates (D-12 in F01's spec).
- **`setting.category` CHECK gap** (already flagged in `02-data-model.md` §6 item 13b, carried over from F01's gate-1 finding): `category` only allows `'TIMER'`/`'TASK_DUE'`, forcing `PASSWORD_MIN_LENGTH` (unit `CHARACTERS`) and the new `NOTICE_PERIOD_*`/`KPI_CYCLE` rows into `'TIMER'` even though they are not timers. This spec does **not** propose changing the CHECK (that is a schema change needing its own sign-off, R-DB-1) — flagged `[NEEDS APPROVAL]` in §12 as an optional follow-up.

**Case file:** none (§8).

## 10. E-mails & timers

This feature does not *trigger* any e-mail or timer itself (it has no workflow) — it is the screen that configures the 21 templates and 17 timers/rules that **other** features' workflows fire. The test-send action (C28, S5) is the one exception: it sends one real e-mail outside any business trigger, to the requesting HR user, through the existing `email_outbox`/Mailcatcher path (R-ARC-6), using the template's currently-saved subject/body/placeholders rendered with sample data.

## 11. Acceptance criteria

AC-01…AC-06 are FRD-08 §6 "Acceptance Criteria"'s own scenarios, verbatim (§6 in v1.2's own numbering; this was §7 in the superseded v1.1 PDF). AC-07 onward cover this spec's validation messages and business rules per DoD §2.8 rules 2–4, since the FRD gives no further scenarios of its own.

```
AC-01 (FRD-08 §6): New job title appears everywhere
  Given HR adds the job title "Account Manager"
  When HR opens the new checklist template form or an employee profile
  Then "Account Manager" is offered in the job title list

AC-02 (FRD-08 §6): Remove a section from a form
  Given the Standard appraisal form has 5 sections
  When HR selects Remove section on "Results & KPIs" and saves the form
  Then the form has 4 sections

AC-03 (FRD-08 §6): E-mail goes to the right manager
  Given the "PIP notice" template has recipients Employee, Manager and HR
  When a PIP for Kees Willems is approved
  Then the e-mail is sent to Kees, to his manager Anouk Peters and to HR

AC-04 (FRD-08 §6): Timer change
  Given HR changes "PIP check-in" from 14 to 10 days
  Then new PIPs get check-ins every 10 days
  And running PIPs keep their existing check-in dates

AC-05 (FRD-08 §6): Export
  Given HR filters the Bench report to manager Mark Smit
  When HR selects the download icon
  Then an Excel file with only Mark Smit's team members on the bench is downloaded

AC-06 (FRD-08 §6): KPI cycle change
  Given the KPI cycle is Quarterly
  When HR changes it to Half-yearly
  Then new KPIs get the frequency Half-yearly by default
  And existing KPIs keep their frequency

AC-07 (ADM-03): Department in use cannot be removed
  Given the Development department has employees assigned
  When HR tries to remove it
  Then 409 ITEM_IN_USE is returned and "This item is in use and cannot be removed." is shown

AC-08 (ADM-04): Archiving a template does not change a running case
  Given Jesse Willems' onboarding checklist was created from the Development template
  When HR archives the Development template
  Then Jesse's existing checklist items are unchanged
  And the Development template no longer appears in the onboarding template dropdown for new employees

AC-09 (§5 S3/S4): Form cannot be activated without content
  Given a newly created appraisal form has no sections
  When HR sets its status to Active and saves
  Then "Add at least one section with a question." is shown

AC-10 (§5 S5): E-mail template needs at least one recipient
  Given HR unticks every recipient on the "Onboarding completed" template
  When HR saves
  Then "Choose at least one recipient." is shown

AC-11 (§5 S5): CC must be valid e-mail addresses
  Given HR enters "hr@cubeworks.nl, not-an-email" in CC
  When HR saves
  Then "Enter valid e-mail addresses, separated by commas." is shown

AC-12 (§5 S5): Test e-mail
  Given HR opens the "Welcome new employee" template editor
  When HR selects "Send test e-mail"
  Then a rendered test e-mail is sent to HR's own e-mail address and a confirmation toast is shown

AC-13 (§5 S6): Timer value must be a positive whole number
  Given HR enters "0" for "Bench check"
  When HR saves
  Then "Enter a whole number greater than 0." is shown

AC-14 (§5 S6): Password minimum length range
  Given HR enters "6" for Password minimum length
  When HR saves
  Then "Enter a number between 8 and 64." is shown

AC-15 (ADM-01): Rule name is not editable
  Given HR opens the Timers & rules tab
  Then "Bench check"'s rule name and module are shown as read-only text, not input fields

AC-16 (ADM-05): Settings change is audited
  Given HR changes "Onboarding overdue" from 3 to 4 days
  Then an audit_log entry records who made the change, when, and the before/after values

AC-17 (§2 role check): Non-HR cannot reach Settings or Reports
  Given a Manager, Employee or IT admin user calls GET /api/settings/timers or GET /api/reports/summary directly
  Then 403 FORBIDDEN is returned regardless of what the UI shows
```

## 12. Decisions & open items

| # | Topic | Decision / status |
|---|---|---|
| D-01 | Job title Grade/Department fields in the wireframe's "Add job title" pop-up | **REVERSED — built, user decision 2026-10-19** ("it should look like the image"). Adopts FRD-08 v1.2 Appendix A #8's own suggested fix: `V15__job_title_grade_department.sql` adds nullable `job_title.grade` (same CHECK list as `employee.grade`) and `job_title.department_id` (FK `department`), with `02-data-model.md` updated in the same change (R-DB-1). C04 now returns `grade`, `departmentId`, `departmentName`; C05 requires `grade` and `departmentId` ("This field is required." for each, 404 if the department does not exist). The Job titles table shows Job title / Grade / Department / Action, with "—" for pre-V15 rows. Grade options come from the same lookup as onboarding (all five values, one more than the wireframe's Junior/Medior/Senior/Senior II) `[ASSUMPTION]`. A department referenced by a job title now also returns `409 ITEM_IN_USE` on delete (ADM-03). |
| D-02 | Removing a checklist template item already in use | The FRD's ADM-03 message ("This item is in use and cannot be removed.") is written only for departments/job titles. `checklist_item.template_item_id` has the same `ON DELETE RESTRICT` default as every other FK in the schema, so a template item that has already been instantiated into at least one `checklist_item` row cannot be hard-deleted regardless of what message is shown. **Decided:** reuse ADM-03's wording (`409 ITEM_IN_USE`) by analogy, since leaving the case with no defined message/behaviour would be worse than reusing the FRD's closest existing one. Flagged `[ASSUMPTION]` — confirm with the user/FRD owner if different wording is preferred. |
| D-03 | Test-send recipient | The wireframe's toast ("Test e-mail sent to emma@cubeworks.nl") implies the test e-mail goes to the logged-in HR user, with no separate "send to" input field drawn anywhere. **Decided:** send to the requesting HR user's own e-mail (`employee.work_email`, falling back to `private_email` if the HR user has no work e-mail yet). `[ASSUMPTION]` — the FRD text itself only says "HR can send a test e-mail," without naming the recipient. |
| D-04 | "+ New template"/"+ New form" for e-mail templates | The wireframe draws a "+ New template" button on the E-mail templates tab, but FRD-08 §2/§3 describes a closed set of "21 templates" each tied to a specific system trigger ("Task assigned," "Welcome new employee," etc.) — a template HR invents from scratch has no code path that would ever send it. **Decided (R-SRC-1, FRD wins):** no create/delete for e-mail templates; the wireframe's "+ New template" button is not built. All 21 are pre-seeded (`email_template.code`, unique) and only ever edited. |
| D-05 | Appraisal form "Used for: Project reviews" — **RESOLVED, user decision 2026-10-17: leave as built** | `appraisal_form.used_for` allows `'PROJECT'` in the schema (confirmed by both `02-data-model.md` and FRD-08 v1.2's own table), but the S3/S4 screen's dropdown only offers 3 options (All employees / Tech leads & managers / Probation reviews) — PROJECT forms stay unreachable from this UI, and that is the decided, final behavior for this feature. If project reviews (FRD-03/F04) later need their own form, that feature can add the 4th dropdown option here or manage it separately, decided then with real requirements in hand — not speculatively now. |
| D-06 | `setting.category` third category | **Declined by the user, 2026-10-16** — left as-is. `PASSWORD_MIN_LENGTH`, `NOTICE_PERIOD_PERMANENT`, `NOTICE_PERIOD_FIXED_TERM` and `KPI_CYCLE` stay classified as `category='TIMER'` (the CHECK constraint already accepts this, same as F01's own seed). No `02-data-model.md` or CHECK-constraint change made. Can be revisited later if desired — not required to ship this feature. |
| D-07 | Timers & rules recipient roles — **RESOLVED** (was the PDF-extraction reconstruction note) | FRD v1.1's "Timers & rules" table (source PDF) lost column alignment under extraction, forcing seven rows' recipient roles to be reconstructed from corroborating sources and flagged `[ASSUMPTION]`. **FRD-08 v1.2's dedicated docx gives this same table cleanly** — 17 rows, each a clean 4-line Rule/Default/Goes to/Can-be-switched-off block, no wrap — and is now the authoritative source, replacing the reconstruction entirely (§3/§9 above use it directly). Three of the seven previously-reconstructed values turned out to be **wrong** and are corrected in §9: `PROBATION_REVIEW` can_disable is **No** (not "can disable" as reconstructed), `CONTRACT_RENEWAL_REMINDER` recipient is **HR only** (not `MANAGER_HR`), and `KPI_CYCLE` recipient is **Manager** (not `ALL_USERS`). Two more gain a recipient they previously lacked: `NOTICE_PERIOD_PERMANENT`/`NOTICE_PERIOD_FIXED_TERM` are now **HR** (not `NULL`) per the clean table's own "Notice period – permanent/fixed-term contract | 30 days | HR | No" rows — this supersedes the earlier reasoning that these were pure calculation inputs with no notification target. The other seven previously-reconstructed rows (`PIP_CHECKIN`, `PIP_DURATION`, `PIP_EXTENSION`, `ASSET_RETURN_REMINDER`, `BENCH_CHECK`, `DEV_PLAN_PROGRESS_CHECK`, `RESET_PASSWORD_LINK_MINUTES`) match what was reconstructed, now confirmed rather than assumed. No row in §9's 12-row list is flagged `[ASSUMPTION]` any longer. See D-09 below for a related, newly-discovered drift between the *already-applied* `V2` seed migration and this same clean table. |
| D-08 | `v_headcount` view | Not required: the Headcount report (S8) can be served by an aggregate query in the report service without a new database view. Adding one is a reasonable implementation choice but not mandated by this spec (no new schema object proposed). Unaffected by the v1.2 revision. |
| D-09 | `ONBOARDING_OVERDUE.can_disable` — drift between the already-applied `V2` seed and FRD v1.2's clean table — **RESOLVED, user-approved 2026-10-16** | Fixed by `V8__fix_onboarding_overdue_can_disable.sql`: `update hr.setting set can_disable = true where code = 'ONBOARDING_OVERDUE';`. Applied to both the Testcontainers test schema and the live dev database; verified `can_disable = true` post-migration. A smaller, lower-priority note in the same vein, left unactioned: `PASSWORD_MIN_LENGTH` was seeded with `recipient_role = NULL`, while the clean table's "Goes to" column reads "All users" for that row — not treated as actionable without user confirmation (this setting has no real notification target). |

| D-10 | `PUT /api/settings/timers/{code}` (C30) has no optimistic-lock `version` field — **RESOLVED, user decision 2026-10-17: fix it** | Fixed: `TimerUpdateRequest`/`TimerDto` now carry a required `version` field, and `TimerSettingsService.update()` compares it against the stored version before saving, returning `409 STALE_VERSION` on a conflict — the same pattern already used by the e-mail template and appraisal form update endpoints (R-DB-5). The frontend's `buildTimerUpdate` helper (and `TimersTab`'s two call sites) now source `version` from the loaded `TimerDto` rather than the stripped local edit-draft shape. All existing backend tests that PUT a timer were updated to fetch the row's current version via JDBC immediately before each call (the seeded settings are shared across multiple test classes in one database, so a hardcoded version number would be fragile). Full suites re-verified: backend 287/287, frontend 65/65. |
| D-11 | S7 KPI captions + "Export all" — **added 2026-10-19, user request** ("add 1 and 2") to match `p-hr-reports` exactly | C31 `SummaryDto` gains four fields: `headcountChangeVsLastMonth` = joiners (first `contract.start_date`) minus leavers (non-REJECTED `employee_exit.last_working_day`) in `(today − 1 month, today]`; `billableStaffCount` = rows in `v_employee_allocation` `[ASSUMPTION]` — the data model has no "billable" flag, so the allocation pool (not ARCHIVED/OFFBOARDING) is used; `appraisalRoundYear` = year of the OPEN round's `review_period_end`, `null` when none (caption hidden) `[ASSUMPTION]`; `unreturnedAssetExEmployeeCount` = distinct employees in `v_unreturned_assets` whose `last_working_day` has passed. Bench % = `benchCount / billableStaffCount`, rounded, computed in the UI. New endpoint C48 (above). No schema change. |

## 13. Definition of done

- [x] Gate 1 Database (done 2026-10-16): `V8__fix_onboarding_overdue_can_disable.sql` (D-09, user-approved), `V9__seed_remaining_email_templates.sql` (15 rows), `V10__seed_remaining_timers.sql` (12 rows), `V11__create_appraisal_form_tables.sql`, `V12__seed_appraisal_forms.sql` (3 forms). D-06 (`setting.category` third value) declined by the user — left as-is, no `02-data-model.md` change needed. **New finding, not anticipated by this spec's §9:** `appraisal_form`/`form_section`/`form_question` were documented in `02-data-model.md` but never created by `V1__init.sql` — caught when V12 failed against an empty Testcontainers database; V11 adds them verbatim from the data-model doc (no doc update needed, the doc was already correct). New entities `AppraisalForm`/`FormSection`/`FormQuestion` + repositories added under `model/settings/`/`repository/settings/` (CLAUDE.md's 2026-10-16 layer-first MVC convention for all new code; F01's existing `com.cubeworks.hr.settings` package — Department/JobTitle/ChecklistTemplate*/EmailTemplate/Setting — is reused as-is, not duplicated). Full test suite (159 tests) passes; all 12 migrations apply cleanly from an empty database.
- [x] Gate 2 Backend (done 2026-10-17): C01–C47 implemented under the layer-first MVC structure (`controller/service/dto.{settings,reports}`), exact error shapes/paths per §6. Settings CRUD (C01–C30): 6 controllers/services, 22 DTOs, reusing F01's existing `com.cubeworks.hr.settings` entities/repositories unchanged and the new `model/repository.settings` appraisal-form entities from Gate 1; new `ErrorCodes.DUPLICATE_NAME`/`ITEM_IN_USE`. Reports (C31–C47): one controller/service using `NamedParameterJdbcTemplate`, 9 DTOs; **required a new migration, `V13__create_report_tables_and_views.sql`**, not anticipated by this spec's §9 — 5 of the 8 reports need Projects/Appraisal/PIP/Offboarding data that doesn't exist yet (F04/F06/F07/F08 unbuilt); user approved applying that schema early (verbatim from `02-data-model.md`, nothing invented) rather than stubbing those reports with placeholders — see `02-data-model.md`'s 2026-10-17 change-log entry and `docs/features/README.md`. 125 new tests (63 settings-CRUD + 62 reports) on top of the prior 159; full suite independently verified at 284/284 passing, `./mvnw clean test` exits 0, Flyway migrates cleanly from empty (V1–V13).
- [ ] Gate 3 Case model: **N/A — confirmed no case-model changes (§8).**
- [x] Gate 4 Frontend (done 2026-10-17): S1–S15 built per §4/§5 at the exact routes in §4. Settings half (S1–S6): `src/features/settings/*`, `src/api/settings*.ts`; appraisal form editor enforces "Add at least one section with a question." client-side too (AC-09). Reports half (S7–S15): `src/features/reports/*`, `src/api/reports.ts`; every report table wired to `Table`'s `onExport` via `apiDownload` against its `.../export` sibling (R-UI-2). Both halves share `router.tsx`/`Sidebar.tsx` additions (`/settings`, `/reports` + nav entries, both HR-only) built sequentially so neither overwrote the other. `npm run build` and `npm test` both pass: 62 tests total at the time (27 FRD-08-new settings tests + 28 pre-existing `lib/date.ts` utility tests, carried in the same suite run + 7 reports tests — the Gate 4 handback's own "55 settings" figure conflated the pre-existing date-utility tests with new settings tests, corrected here per the Gate 5 spec-coverage review), 0 failures, independently verified. Gate 5 added 2 more frontend tests (VR-10 toast-text, AC-15 read-only rendering), now 64 total. R-UI-1…R-UI-9 followed (button placement/colour, dd-mm-yyyy dates, due-date colouring on PIP/contracts-probation reports, read-only ADM-01 fields on the timers tab).
- [x] Gate 5 Review (done 2026-10-17): springboot-security-reviewer (1 HIGH + 1 MEDIUM + 1 LOW, all fixed: PII removed from a log line, e-mail-template audit snapshot completed to include subject/body/cc/triggerDescription, copied checklist items now individually audited), polyglot-security-reviewer (2 more MEDIUM + 3 more LOW, fixed: log-injection via unsanitised user-entered names removed from 6 log lines across 4 services, a missing TIMER-category scope check added to `PUT /api/settings/timers/{code}`, `encodeURIComponent` added to 4 path-interpolated `code` values in the frontend API client, and — after the user's D-10 decision below — the optimistic-lock version gap on the same endpoint), polyglot-code-reviewer (0 blockers; 4 warnings fixed: 3 N+1 query patterns replaced with single aggregate queries, `ReportController`'s ~170 lines of Apache POI/formatting logic extracted to a new `ReportExcelBuilder` class; suggestions addressed: a wildcard DTO import replaced with explicit imports, `@Transactional(readOnly = true)` added to 3 multi-query read methods), spec-coverage-analyst (PASSED, 97.6% → effectively higher after closing all 4 PARTIAL items with 5 new tests: AC-03's PIP_NOTICE recipient assertion, AC-08 strengthened with a real instantiated-item-survives-archiving test, AC-15 covered on both the backend — a crafted PUT can't change `label`/`module`/`code` — and the frontend — the timer row renders them as text, not inputs — and VR-10's exact toast text). Full suites re-verified after every fix, including the D-10 timer-locking change: backend 287/287, frontend 65/65, both 0 failures.
- [x] All `[OPEN]`/`[NEEDS APPROVAL]`/`[ASSUMPTION]` items in §12 resolved or explicitly accepted by the user (D-01 through D-10): D-01/D-04/D-06/D-07/D-09 already resolved by earlier revisions/gates; D-02/D-03 are accepted `[ASSUMPTION]`s; D-05 and D-10 resolved by explicit user decision 2026-10-17 (D-05: leave as built; D-10: fix it, done).
- [x] `.claude/docs/features/README.md`'s F09 row updated to point at this spec and reflect its status (Done).
