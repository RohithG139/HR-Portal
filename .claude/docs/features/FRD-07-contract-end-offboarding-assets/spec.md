# FRD-07 — Contract end, offboarding and assets

| | |
|---|---|
| Status | **Draft** |
| Sources | **FRD v1.2 (canonical, 2026-10-16):** `.claude/docs/source/FRD-07/FRD-07_Contract_End_Offboarding_and_Assets.docx` (primary, "Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026"), `FRD-00/FRD-00_Application_Overview.docx` (roles, permissions matrix, G-01…G-13, Appendix A), `FRD-08/FRD-08_Reports_and_Settings.docx` (timers, notice periods, e-mail templates, checklist templates this flow reads), `FRD-01` §4.7/§4.8 and `FRD-03` §4.7 (reused profile / Transfer manager / Change project manager), `FRD-02` §4.7 and `FRD-06` §4 (the two automatic exit triggers). The legacy v1.1 monolithic PDF is superseded — see `.claude/docs/source/README.md`. |
| Wireframes | `.claude/docs/source/FRD-07/WF-07_Contract_End_Offboarding_and_Assets.html` (v1.2, canonical) — pages `p-hr-contract`, `p-em-profile` (host of `m-resign`), `p-hr-case-lotte` (host of `m-exit`), `p-hr-off` (host of `m-exit-interview`), `p-it-revoke`, `p-it-assets` (host of `m-add-asset`, `m-asset-view`); Screen 4 "Confirm exit (HR)" is **not drawn** (the file's own screen bar labels it "S4 · Confirm exit (HR) (not drawn)"). Text versions in `wireframes.md` (this folder). |
| Order of truth | FRD v1.2 > this spec > `wireframes.md` > WF-07 wireframe HTML > legacy v1.1 (R-SRC-1). |
| Relationship to feature builds | This is the full contract for **F08 "Contract end, offboarding & assets"** (`features/README.md`, depends on F01–F07). **Nothing of FRD-07's own flow is built yet.** Already in place and reused, not rebuilt: the `asset`, `checklist_item`, `checklist_template`, `document`, `contract` tables and F01's asset-issue endpoints A21/A22 and checklist tick A17 (F01); `employee_exit` + `v_unreturned_assets` (applied early by F09's `V13__create_report_tables_and_views.sql`, user-approved 2026-10-17); `manager_change`/`manager_change_task` + Transfer manager W09/W10 (F03, `V14`); the Contracts & probation and Unreturned assets reports (F09, C44–C47); the e-mail templates `CONTRACT_ENDING`, `RESIGNATION_RECEIVED`, `OFFBOARDING_STARTED`, `ASSET_RETURN_REMINDER` (`V9`) and the timers `CONTRACT_RENEWAL_REMINDER`, `ASSET_RETURN_REMINDER`, `NOTICE_PERIOD_PERMANENT`, `NOTICE_PERIOD_FIXED_TERM` (`V10`). |

## 0. Status & sources

FRD-07 v1.2 states its objective as: "Make every exit complete and traceable: equipment returned, access removed, handover done and the record archived. Make sure fixed-term contracts are decided on in time." It describes four sub-flows in its §2 — Contract renewal, Starting an exit, Offboarding checklist, Asset register (IT admin) — over **10 screens across 7 features** (its own §3 wording), with a per-feature method/endpoint/table list (§3 "Features, Endpoints & Database", 4.1–4.7), one validation table, eight hard invariants OFF-01…OFF-08, the full column schema of every table it touches, a line-by-line cross-check against `V1__init.sql`, six acceptance scenarios and two Appendix A items (#5 and #7).

This spec keeps that structure. Nothing here invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). Where they are silent or disagree, the item is marked **[OPEN]** or **[ASSUMPTION]** and listed in §12.

**What a check of the real repository found (2026-10-19), relevant before any gate starts:**
- `employee_exit` and `v_unreturned_assets` **exist** (`V13`). `asset`, `checklist_item`, `checklist_template(_item)`, `document`, `contract`, `employee.account_status/stage/archived_at` exist (`V1`). `manager_change` + `manager_change_task` exist (`V14`). `project`, `project_assignment` (with `end_reason 'EMPLOYEE_LEAVING'`), `pip` (`outcome 'CLOSED_EMPLOYEE_LEAVING'`), `review`, `development_plan` exist (`V13`).
- **`decision` table does not exist** in any migration (designed in `02-data-model.md` §2.7 with full DDL, never applied). FRD 4.1 writes `decision (CONTRACT_RENEWAL)`.
- **`v_contract` and `v_account_access` do not exist** in any migration. `02-data-model.md` §2.9 describes them in one line each but gives **no DDL**; FRD 4.3/4.4/4.6 read both. (FRD-01's spec already recorded `v_account_access` as "deferred to F08"; `ItAdminDashboardDto`'s Javadoc says the same.)
- **No `OFFBOARDING` checklist template is seeded** (`V2`/`V9`/`V10`/`V12` seed none), yet FRD 4.3 creates `checklist_item (OFFBOARDING)` rows at confirm.
- Backend: `com.cubeworks.hr.asset` holds only F01's issue/unissue (A21 `POST /api/employees/{employeeId}/assets`, A22 `POST /api/assets/{assetId}/unissue`); no exit, contract-renewal or offboarding code exists. `KeycloakAdminClient` has no "disable user" operation (needed for OFF-07).
- Engine: the deployed `employeecase.case`/`.type` declares only the `Employee` and `Onboarding` items and three onboarding tasks — no `Contract`/`Exit` item, no offboarding stage, task or "Case completed" milestone. The legacy `emp.case` has `InitiateExit`/`ReturnAssets`/`RevokeAccess`/`ArchiveRecords` plan items, but R-CM-1 forbids extending it.

## 1. Scope

**In scope (FRD-07's own "IN-SCOPE" list)**
- Contract renewal decision for fixed-term contracts.
- Resignation by the employee.
- Starting an exit (HR or manager).
- Offboarding checklist: assets, access, handover, exit interview, documents.
- Asset register (all assets issued to employees).
- Archiving and closing the case.

**Out of scope (FRD-07's own "OUT-OF-SCOPE" list)**
- "Final pay and holiday settlement — outside this application".
- "Rehiring — not in v1".

**Also out of scope here (owned by other FRDs; this spec only consumes or triggers them)**
- The probation "End employment" approval (FRD-02 P04) and the PIP "Not met" HR sign-off (FRD-06 `POST /checkins/{id}/hr-signoff`) — each *creates* the `employee_exit` row (`PROBATION_NOT_PASSED` / `PIP_NOT_MET`); FRD-07 takes over from there.
- Transfer manager (FRD-01 §4.8, built as W09/W10 by F03) and Change project manager (FRD-03 §4.7, F04, not built) — reused for "Reassign team and projects" (OFF-08), not rebuilt.
- Settings screens for timers, notice periods, e-mail templates and offboarding checklist templates (FRD-08, built by F09).
- The Contracts & probation and Unreturned assets reports (FRD-08, built by F09) — FRD-07 only produces their data.
- Issuing equipment during onboarding (FRD-02, built by F01 — A21/A22).

## 2. Roles & permissions

From FRD-07 §3 "Access gate" (verbatim): "Screens 1, 4, 5 and 6 are HR-only. Screen 2 is the employee on their own record. Screen 3 is HR, or the manager for their own team; a manager-started exit waits for HR (Screen 4). Screens 7–10 are IT admin-only; HR can also mark assets returned or lost from Screen 5. Archived cases are read-only for everyone (G-10)."

| Capability | HR | Manager | Employee | IT admin |
|---|---|---|---|---|
| Contract renewal decision (S1) | Yes | No | No | No |
| Submit resignation (S2) | Own record only (G-13) | Own record only | Own record only | Own record only |
| Initiate exit (S3) | Yes → exit `APPROVED` | Own team only (WS-02) → exit `REQUESTED`, waits for HR | No | No |
| Confirm / reject an exit (S4) | Yes, never their own exit (G-13) | No | No | No |
| Offboarding checklist, exit interview (S5, S6) | Yes | Ticks own items (e.g. "Handover plan agreed", owner Manager) via My Tasks (ONB-03 pattern, A17) — **[ASSUMPTION]**, FRD-07 does not say whether the manager can open S5 | No | No |
| Mark asset returned / lost | Yes, from S5 only | No | No | Yes (S7, S10) |
| Remove access & collect assets (S7) | No | No | No | Yes |
| Asset register, add asset, asset history (S8–S10) | **[OPEN] D-07-10** — FRD-00's matrix row "Equipment, accounts and asset register" gives HR "View"; FRD-07's access gate says "Screens 7–10 are IT admin-only" | No | Own equipment only (FRD-01 My Profile "My equipment", already built) | Yes |
| Archive & close case | Yes (only when OFF-03 is met) | No | No | No |

FRD-00 matrix row "Start onboarding, transfer manager, start exit": HR "Yes", Manager "Start exit (HR approves)", Employee "Resign", IT admin "No" — consistent with the table above.

G-13: nobody confirms, approves or archives their own exit. G-10/OFF-06: once archived, every write endpoint below refuses changes for that employee.

## 3. Business rules

Copied from the FRD by ID (R-SRC-3 — wording kept exact; all priority MUST):

| ID | Rule |
|---|---|
| OFF-01 | When offboarding starts, open development, appraisal, project review and PIP tasks for the employee are cancelled. |
| OFF-02 | Project assignments end on the last working day. |
| OFF-03 | "Archive & close case" is available only when all assets are returned or marked lost, all access is removed, all HR items are done and, for a manager, no direct reports or open projects remain. |
| OFF-04 | Assets not returned stay visible in the Unreturned assets report after the case is closed. |
| OFF-05 | An asset reminder e-mail is sent 3 days before the last working day (configurable). |
| OFF-06 | An archived case is read-only (G-10). |
| OFF-07 | The employee can no longer log in after the end of their last working day. |
| OFF-08 | A leaving manager's direct reports and open projects must be moved to another manager before the last working day. |

"Global rules G-01 … G-13 (FRD-00, section 4) also apply to this module." Most relevant: G-03/G-04 (every task has an owner, due date, notification + e-mail), G-06 (history), G-08 (all periods from Settings), G-10 (archived = read-only), G-11 (save toast; Cancel never saves), G-12 (dd-mm-yyyy), G-13 (no self-approval).

Process rules from FRD-07 §2 (quoted, not paraphrased):
1. Pre-condition: "the employee case is active (Onboarding, Active employment or PIP)."
2. "60 days before a fixed-term contract ends (configurable), HR gets a "Contract renewal decision" task."
3. "HR chooses Renew fixed-term (new dates), Convert to permanent, or Do not renew (starts offboarding with type "End of contract")."
4. "The employee submits a resignation from My Profile, or HR or the manager selects "Initiate exit" on the employee record. Exits also start automatically after "PIP not met" or "End employment" at probation."
5. "HR confirms the type, notice date, last working day and exit interview date. An exit started by a manager needs HR approval."
6. "The case moves to Offboarding. Everyone involved is notified (template "Offboarding started")."
7. "Return assets — the list comes from the asset register. IT admin or HR marks each item returned with its condition, or marks it lost."
8. "Remove access — IT admin gets a "Remove access" task with all accounts, scheduled for the last working day. Login to this application ends automatically at the end of the last working day (OFF-07)."
9. "HR & handover — resignation confirmed, handover plan agreed (manager), exit interview (HR), project assignments ended (system, on the last day), final documents sent."
10. "Reassign team and projects — only when the leaving employee is a manager. HR transfers every direct report to a new manager (FRD-01) and changes the manager of every open project (FRD-03), effective on or before the last working day."
11. "When everything is done, HR selects "Archive & close case". The milestone "Case completed" is recorded and the record becomes read-only."
12. Asset register: "Shows every asset with number, type and model, who it is issued to, issue date and status (Issued, Return due, Returned, Not returned). IT admin can add an asset, view its history, mark it returned or mark it lost."
13. Constraint: "Notice periods are set per contract type in Settings (FRD-08, default 30 days) and are used to suggest the last working day."
14. Constraint: "Archived records are kept indefinitely (FRD-00 section 9)."

## 4. Screens

Routes and wireframe refs are FRD-07 v1.2 §3's own table.

| # | Screen | Who | Route / trigger | Wireframe page / element | FRD |
|---|---|---|---|---|---|
| S1 | Contract renewal decision | HR | `/contracts/:contractId/renewal` · HR task "Contract renewal decision" | `p-hr-contract` | §2 "Contract renewal", 4.1 |
| S2 | Submit resignation (pop-up) | Employee (own) | Modal on `/me/profile` · "Submit resignation" | `m-resign` on `p-em-profile` | §2 "Starting an exit", 4.2 |
| S3 | Initiate exit (pop-up) | HR; Manager (own team) | Modal on `/employees/:code` (HR) and `/team/:code` (Manager) · "Initiate exit" | `m-exit` on `p-hr-case-lotte` (HR record only — **Appendix A #7**, see D-07-2) | §2, 4.3 |
| S4 | Confirm exit (HR) | HR | `/exits/:exitId/confirm` · HR task "Confirm exit" after a resignation or a manager-started exit | **not drawn** — FRD-07 itself says "Screen 4 · not drawn" and tracks it as **Appendix A #5**: "HR approval of "End employment", HR "Confirm exit" and HR PIP sign-off have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." (D-07-1) | §2, 4.3 |
| S5 | Offboarding checklist | HR | `/employees/:code/offboarding` · HR task; employee record header | `p-hr-off` | §2 "Offboarding checklist", 4.4, 4.5 |
| S6 | Exit interview (pop-up) | HR | Modal on S5 · "Open form" | `m-exit-interview` | 4.4 |
| S7 | Remove access & collect assets | IT admin | `/it/offboarding/:code` · IT task "Remove access" | `p-it-revoke` | 4.6 |
| S8 | Assets (asset register) | IT admin | `/assets` · menu (IT admin) | `p-it-assets` | §2 "Asset register", 4.7 |
| S9 | Add equipment (pop-up) | IT admin | Modal on S8 · "+ Add asset" | `m-add-asset` | 4.7 |
| S10 | Asset detail and history (pop-up) | IT admin | Modal on S8 · "View" | `m-asset-view` | 4.7 |

Notes:
- **S2 host route:** FRD says `/me/profile`; F01 built My Profile at `/my-profile` (`hr-frontend/src/app/router.tsx`). The modal attaches to the already-built page; the route spelling is not changed by this feature (D-07-14).
- **S3 on the manager's record:** FRD-01 v1.2 §2 lists "Initiate exit" as an **HR-only** header action ("HR: Initiate exit and Transfer manager (both hidden when the employee is already offboarding)"), and FRD-01's spec D-W8 built only that visibility rule. FRD-07 (and FRD-00's matrix) let a manager start an exit; Appendix A #7 confirms the button is missing from `p-mg-case-*`. Resolution needed (D-07-2).
- **S5's "Reassign team and projects" section** (FRD §2) is not drawn in `p-hr-off` (the wireframe's example leaver, Thomas Mulder, is not a manager). It is drawn here from the FRD text (`wireframes.md` S5).

## 5. Fields & validation

FRD-07 §4 "Validation Table" (exact messages, R-SRC-3):

| Field | Rule | Exact message |
|---|---|---|
| Renewal decision | Required | "Choose a decision." |
| New end date (renewal) | After the current end date | "The new end date must be after the current end date." |
| Requested last working day (resignation) | After the notice date | "The last working day must be after the notice date." |
| Exit type, notice date, last working day | Required | "This field is required." |
| Asset condition | Required when returned | "Choose the condition of the returned asset." |
| Exit interview reason | Required | "Choose the main reason for leaving." |
| Asset number (new asset) | Required; unique | "This asset number is already in use." |
| Archive & close case | OFF-03 met | "Return or mark all assets, remove all access, reassign team and projects and complete all items first." |

### S1 — Contract renewal decision (`p-hr-contract`)

Read-only context: employee name, "Contract renewal decision · fixed-term ends {date}", Current contract (type · start – end), Manager. The wireframe also shows **"Manager advice: Renew — strong performer"** — **[OPEN] D-07-7**: no FRD input, table or column holds a manager's renewal advice; not built until resolved.

| Field | Type | Rule | Message | Column |
|---|---|---|---|---|
| Decision | choice: Renew fixed-term ("New end date") / Convert to permanent ("No end date") / Do not renew ("Starts offboarding") | Required | "Choose a decision." | `decision.decision` = `RENEW` / `CONVERT` / `DO_NOT_RENEW` |
| New start date | date, shown for Renew only | Required for Renew | "This field is required." **[ASSUMPTION]** — the FRD gives no message for this field; the generic required message is reused (DB CHECK `(decision = 'RENEW') = (new_start_date is not null and new_end_date is not null)`) | `decision.new_start_date` |
| New end date | date, shown for Renew only | Required for Renew; after the current end date | "The new end date must be after the current end date." | `decision.new_end_date` |
| Notes | text area | Optional | — | `decision.comment` **[ASSUMPTION]** (the only free-text column on `decision`) |

Buttons: Cancel (red outline, left) · Confirm (green, right).

### S2 — Submit resignation (`m-resign`)

| Field | Rule | Message | Column |
|---|---|---|---|
| Notice date* | Required | "This field is required." | `employee_exit.notice_date` |
| Requested last working day* | Required; after the notice date | "This field is required." / "The last working day must be after the notice date." | `employee_exit.requested_last_day` |
| Message to HR | Optional | — | `employee_exit.message` |
| Signed letter ("Attach signed letter, or browse file") | Optional file | — (size/type limits: `02-data-model.md` open question 5, still open) | `document` (`RESIGNATION_LETTER`) → `employee_exit.letter_document_id` |

Buttons: Cancel · Submit resignation. Wireframe toast: "Resignation sent to HR and your manager".

### S3 — Initiate exit (`m-exit`)

| Field | Rule | Message | Column |
|---|---|---|---|
| Type: Resignation ("Employee resigned") / End of contract ("Not renewed") / Termination ("Incl. PIP / probation") | Required | "This field is required." | `employee_exit.exit_type` = `RESIGNATION` / `END_OF_CONTRACT` / `TERMINATION` |
| Notice received* | Required | "This field is required." | `employee_exit.notice_date` |
| Last working day* | Required; prefilled from the notice period (O04) | "This field is required." | `employee_exit.last_working_day` (DB: `>= notice_date`) |
| Exit interview*: Schedule with HR / Not needed | Required | "This field is required." | `employee_exit.interview_needed` |
| Interview date | Optional | — | `employee_exit.interview_planned_on` |
| Notes | Optional | — | `employee_exit.notes` |

Buttons: Cancel · Start offboarding. Wireframe toast: "Offboarding started · employee, manager and IT notified".

Two schema notes: (1) the DB CHECK `exit_type = 'RESIGNATION' or (requested_last_day is null and message is null and letter_document_id is null)` means an HR/manager-entered "Resignation" carries no message/letter — those come only from S2. (2) FRD's "after the notice date" message is stated for the **resignation's requested** last day (DB `requested_last_day > notice_date`); for the agreed `last_working_day` the DB allows equality (`>=`). Whether S3/S4 should show the same message on `last_working_day < notice_date` is **[ASSUMPTION]** — yes, reusing the FRD's exact text (D-07-12).

### S4 — Confirm exit (HR) — not drawn

Fields from the FRD text only ("HR confirms the type, notice date, last working day and exit interview date"): Type, Notice date, Last working day, Exit interview date (all prefilled from the `employee_exit` row; requested last day, message and letter shown read-only for a resignation). Messages: "This field is required." for type/notice date/last working day. Actions: Confirm (→ O06) and, for a manager-started exit, Reject (→ O07). **[OPEN] D-07-1** — layout and whether Reject also applies to resignations (D-07-4) must be decided before building.

### S5 — Offboarding checklist (`p-hr-off`)

Header: "← Back to case", employee name, "Offboarding · last working day {date}", stage track (Onboarding / Active employment / PIP / Offboarding). Four groups (FRD §2):

| Group | Rows | Source |
|---|---|---|
| Return assets | Asset, Asset no., Issued, Returned (checkbox), Condition, Status ("Due {date}") | `asset` where `employee_id` = leaver (status `RETURN_DUE` after confirm), plus their `RETURN_ASSETS` `checklist_item` rows |
| Remove access (wireframe heading: "Revoke access", "IT admin · scheduled {date} 18:00") | one item per account, owner IT admin, due = last working day | `checklist_item` (`REMOVE_ACCESS`, `removes_item_id` → onboarding `ACCOUNT` item), `v_account_access` |
| HR & handover | "Resignation confirmed in writing" (HR), "Handover plan agreed" (Manager), "Exit interview" (HR, "Open form" → S6), "Project assignment ended" (System), "Final documents sent" (HR) — all mandatory in the wireframe | `checklist_item` (`HR_HANDOVER`) from the offboarding template |
| Reassign team and projects | only when the leaver is a manager: direct reports and open projects still to move, with links to Transfer manager (FRD-01) / Change project manager (FRD-03) | `employee` (direct reports), `project` (open, as PM), `manager_change`, `checklist_item` (`REASSIGN_TEAM`) |

Asset condition: "Choose the condition of the returned asset." when Returned is ticked without a condition (the wireframe's Condition column shows "—" with no selector on this page — `wireframes.md` S5 adds one, matching S7).
Buttons: Mark asset as lost (orange outline, left) · Save · Archive & close case (green, right; **disabled** until OFF-03 is met — on a server-side refusal: "Return or mark all assets, remove all access, reassign team and projects and complete all items first.").

### S6 — Exit interview (`m-exit-interview`)

| Field | Rule | Message | Column |
|---|---|---|---|
| Date* | Required | "This field is required." **[ASSUMPTION]** (no FRD message for this field) | `employee_exit.interview_held_on` |
| Held by* | Required (HR users) | same **[ASSUMPTION]** | `employee_exit.interview_held_by` |
| Main reason for leaving*: Relocation / Career growth / Compensation / Other | Required | "Choose the main reason for leaving." | `employee_exit.interview_main_reason` |
| Overall experience at CubeWorks | 1–5 (Below / Meets / Exceeds) | Optional | `employee_exit.interview_experience_rating` |
| What could we improve? | Optional | — | `employee_exit.interview_improvements` |
| Would you recommend CubeWorks? | Optional | — | `employee_exit.interview_would_recommend` |

DB CHECK `interview_held_on is null or (interview_held_by is not null and interview_main_reason is not null)` matches the three required fields. Buttons: Cancel · Save.

### S7 — Remove access & collect assets (`p-it-revoke`)

Header "Revoke access · {name}", "{job title} · last working day {date}". **Accounts** ("Scheduled {date} 18:00"): one checkbox per account still in `v_account_access`, owner IT admin, due = last working day. **Collect assets**: Asset, Asset no., Returned (checkbox), Condition (— / Good / Damaged → `return_condition` `GOOD`/`DAMAGED`); "Choose the condition of the returned asset." when returned without condition. Buttons: Cancel (left) · Save · Mark done (right). "Mark done" completes the IT task "Remove access" — **[OPEN] D-07-9** (no FRD endpoint for it).

### S8 — Assets (`p-it-assets`)

Title "Assets", count ("{n} assets"), "+ Add asset" (top right). Filters: Status (All / Issued / Return due / Not returned / Returned), Type (All / Laptop / Phone — wireframe shows two; the DB has 8 types, see D-07-11), search "Search asset no., employee…", export icon (Excel, R-UI-2). Columns: Asset no., Type / model, Issued to, Issued, Status, Action ("View"). Paged. Status labels map to `asset.status`: `ISSUED` "Issued", `RETURN_DUE` "Return due {date}", `RETURNED` "Returned", `NOT_RETURNED` "Not returned"; `IN_STORAGE` is shown as "In storage" in the Issued-to column (wireframe row CW-LT-0099) and has no status filter option (D-07-11).

### S9 — Add equipment (`m-add-asset`)

| Field | Rule | Message | Column |
|---|---|---|---|
| Type* | Required | "This field is required." (already used by F01's A21) | `asset.asset_type` |
| Model* | Required | same | `asset.model` |
| Asset no.* (prefilled "CW-") | Required; unique | "This asset number is already in use." (`409 ASSET_NUMBER_IN_USE`, already in `ErrorCodes`) | `asset.asset_number` |
| Serial number | Optional | — | `asset.serial_number` |
| Issued to* (employees, or "In storage") | Required | "This field is required." | `asset.employee_id` (null for In storage → `status IN_STORAGE`, else `ISSUED`) |
| Issue date* | Required | "This field is required." | `asset.issued_on` — **[OPEN] D-07-11**: the DB comment says `issued_on` is NULL while `IN_STORAGE`, but the wireframe makes Issue date required even for "In storage" |

Buttons: Cancel · Save. Toast "Asset saved".

### S10 — Asset detail and history (`m-asset-view`)

Title "{asset no.} · {model}". Read-only: Type, Serial, Issued to, Issued, Status. **History** timeline from `audit_log` (`entity_type = 'ASSET'`), e.g. "Issued to {name} · {date} · {actor}", "Return requested · {date} · offboarding". Buttons: Mark as lost (orange outline, left) · Close · Mark returned (right). "Mark returned" needs a condition (OFF rule + DB CHECK) but the modal has no condition field — **[OPEN] D-07-11**.

## 6. API

Endpoints taken directly from FRD-07 v1.2 §3 "Features, Endpoints & Database" (4.1–4.7), with this project's `/api` prefix (R-API-1). FRD paths use `{code}` for employees; built endpoints (F01 A21, F03 W10) use the numeric `{id}` — this spec writes `{employeeId}` to match the built convention (naming only, not a functional change). Every write checks role + ownership server-side (R-API-2), writes `audit_log` (R-API-5), checks `version` (R-DB-5, `409 STALE_VERSION`) and refuses archived employees (G-10).

| # | Method & path | Role | Tables (FRD's own list) · case file | Responses |
|---|---|---|---|---|
| **4.1 Contract renewal (S1)** | | | | |
| O01 | `GET /api/contracts/{contractId}/renewal` | HR | `contract`, `employee` | `200` current contract details (+ manager) / `404` |
| O02 | `POST /api/contracts/{contractId}/renewal-decision` | HR | `decision (CONTRACT_RENEWAL)`, `contract` (old `is_current` false, new row), `employee_exit (END_OF_CONTRACT)` · case file `Contract.decision` | `202 {undoUntil}` (completes "Contract renewal decision", R-API-6) / `400` "Choose a decision." · "The new end date must be after the current end date." / `409` if the contract is not fixed-term or not current **[ASSUMPTION]** |
| **4.2 Resignation (S2)** | | | | |
| O03 | `POST /api/me/resignation` (multipart: notice date, requested last day, message, signed letter) | Employee (own record) | `employee_exit (RESIGNATION, initiated_by_role EMPLOYEE, REQUESTED)`, `document (RESIGNATION_LETTER)`, `email_outbox (RESIGNATION_RECEIVED)` · case file `Exit` | `201 {exitId}` / `400` / `409` when a live exit already exists (`ux_exit_active`) — no FRD message, **[ASSUMPTION]** generic `CONFLICT` |
| **4.3 Initiate and confirm an exit (S3, S4)** | | | | |
| O04 | `GET /api/employees/{employeeId}/exits/suggested-last-day?noticeDate=` | HR; Manager (own team) | `v_contract`, `setting (NOTICE_PERIOD_*)` | `200 {suggestedLastDay}` = notice date + `NOTICE_PERIOD_PERMANENT`/`_FIXED_TERM` days |
| O05 | `POST /api/employees/{employeeId}/exits` | HR (→ `APPROVED`); Manager, own team (→ `REQUESTED`) | `employee_exit (APPROVED / REQUESTED)` — "one live exit (ux_exit_active)" · case file `Exit.requestedByRole` | `201 {exitId}` / `400` "This field is required." / `409` live exit exists / `403` (manager, not own team; anyone on self — G-13) |
| O06 | `POST /api/exits/{exitId}/confirm` | HR (not own exit) | `employee_exit (APPROVED, approved_by)`, `employee (stage OFFBOARDING)`, `checklist_item (OFFBOARDING; access items via removes_item_id)`, `asset (RETURN_DUE)`, `review`, `development_plan`, `pip (CLOSED_EMPLOYEE_LEAVING)`, `engine_task`, `email_outbox (OFFBOARDING_STARTED)` · case file `Exit.approved`, `Exit.lastWorkingDay` | `202 {undoUntil}` (completes "Confirm exit") / `400` / `409` if not `REQUESTED` |
| O07 | `POST /api/exits/{exitId}/reject` | HR | `employee_exit (REJECTED)` | `200` / `409` if not a pending manager-started exit (D-07-4) |
| **4.4 Offboarding checklist (S5, S6)** | | | | |
| O08 | `GET /api/employees/{employeeId}/offboarding` | HR | `checklist_item`, `asset`, `v_account_access`, `employee_exit`, `employee (direct reports)`, `project (open, as PM)` | `200 {exit, returnAssets[], removeAccess[], hrHandover[], reassign{directReports[], openProjects[]}, closeAllowed, version}` |
| O09 | `POST /api/checklist-items/{id}/done` | item owner, or HR | `checklist_item` | **Reuses F01's A17** — extended to `phase = 'OFFBOARDING'` items (FRD lists it under both 4.4 and 4.6). `200 {item, progress}` |
| O10 | `POST /api/exits/{exitId}/interview` | HR | `employee_exit (interview_*)` | `200` / `400` "Choose the main reason for leaving." |
| O11 | `POST /api/employees/{employeeId}/documents` (multipart, `FINAL_DOCUMENT`) | HR | `document (FINAL_DOCUMENT)` | `201 {documentId}` — F01 already has `POST /api/documents` (A07, `CONTRACT` only); whether to extend A07 or add this path is D-07-14 |
| — | "Reassign team and projects" | HR | `manager_change` — "Reuses FRD-01 4.8 and FRD-03 4.7 (OFF-08)" | No new endpoint: F03's W09/W10 (`/api/employees/{id}/manager-tasks`, `/manager-transfers`, built) and FRD-03's `/projects/{id}/manager-change-preview` + `/manager-changes` (F04, **not built**) |
| **4.5 Archive and close the case (S5)** | | | | |
| O12 | `POST /api/exits/{exitId}/archive` | HR (not own) | `employee_exit (CLOSED)`, `employee (stage ARCHIVED, archived_at)` · case file `Exit.closeAllowed` | `200` / `409` "Return or mark all assets, remove all access, reassign team and projects and complete all items first." when OFF-03 is not met |
| **4.6 IT access removal and asset collection (S7)** | | | | |
| O13 | `GET /api/it/offboarding/{employeeId}` | IT admin | `checklist_item (REMOVE_ACCESS, RETURN_ASSETS)`, `v_account_access`, `asset` | `200 {employee, lastWorkingDay, accounts[], assets[]}` |
| (O09) | `POST /api/checklist-items/{id}/done` | IT admin (own items) | `checklist_item` | "Account removed" — same endpoint as O09 |
| O14 | `POST /api/assets/{assetId}/return` `{condition}` | IT admin; HR (from S5) | `asset (RETURNED + return_condition)` | `200` / `400` "Choose the condition of the returned asset." |
| O15 | `POST /api/assets/{assetId}/lost` | IT admin; HR (from S5) | `asset (NOT_RETURNED)` — "shows as Not returned, OFF-04" | `200` |
| **4.7 Asset register (S8–S10)** | | | | |
| O16 | `GET /api/assets?status&type&q&sort&page&size` | IT admin (HR: D-07-10) | `asset`, `employee` | `200` page |
| O17 | `GET /api/assets/export?...` | same | `asset`, `employee` | `.xlsx` (R-UI-2) |
| O18 | `POST /api/assets` | IT admin | `asset` — "number unique" | `201 asset` / `400` / `409 ASSET_NUMBER_IN_USE` "This asset number is already in use." |
| O19 | `GET /api/assets/{assetId}` | IT admin | `asset`, `employee` | `200` / `404` |
| O20 | `GET /api/assets/{assetId}/history` | IT admin | `audit_log (entity_type ASSET)` | `200 [{action, at, by, detail}]` |

**Scheduled jobs (FRD rows with method "—")** — same polling pattern as F01's `OverdueReminderScheduler` (E1 fallback, `01-overview.md` §5), not native CMMN timers:

| # | Job | FRD purpose | Tables |
|---|---|---|---|
| J1 | Timer "Contract renewal reminder" | "HR task 60 days before a fixed-term end" | `contract`, `setting (CONTRACT_RENEWAL)` — real code `CONTRACT_RENEWAL_REMINDER` (D-07-13) · case file `Contract.renewalDecisionDate`, `renewalEnabled` |
| J2 | Timer "Asset return reminder" | "E-mail 3 days before last day (OFF-05)" | `setting (ASSET_RETURN_REMINDER)`, `email_outbox (ASSET_RETURN_REMINDER)` |
| J3 | Job at end of last working day | "Assignments end (OFF-02); login disabled (OFF-07)" | `project_assignment (EMPLOYEE_LEAVING)`, `employee (account_status DISABLED)` |

**Not in the FRD, needed by the screens — [OPEN], not to be added without approval (R-API-4):** a read endpoint for S4 (`GET` of one exit — D-07-1), the S7 "Mark done" task completion (D-07-9), and the reduced IT admin dashboard counts F03 deferred to F08 (`accessToRevokeCount`, `notReturnedCount` on `GET /api/dashboard`, FRD-01 spec D-W2 — an additive DTO change to an existing endpoint, not a new one).

## 7. Backend behaviour

All per R-ARC-1…7: Spring writes facts in one transaction, e-mails go to `email_outbox` in that transaction, engine calls happen after commit and are retried.

- **J1 Contract renewal reminder.** For each current fixed-term contract, Spring stores `Contract.renewalDecisionDate = end_date − CONTRACT_RENEWAL_REMINDER days` and `renewalEnabled = setting.enabled` (snapshot at cycle start, ADM-02). The scheduler raises "Contract renewal decision" (HR) when `renewalEnabled` and the date is reached, with G-04 notification and the `CONTRACT_ENDING` e-mail ("Contract ending | 60 days before a fixed-term contract ends", FRD-08).
- **O02 Renewal decision.** Inserts `decision` (`CONTRACT_RENEWAL`, `decided_by`, `engine_task_id`). `RENEW`: old `contract.is_current = false`, new `FIXED_TERM` row with `new_start_date`/`new_end_date`; case file `Contract` refreshed (`contractId`, `endDate`, new `renewalDecisionDate`) so the cycle repeats. `CONVERT`: old row not current, new `PERMANENT` row (end date null) — **[OPEN] D-07-6**: start date of the new row is not stated (the DB forbids `new_start_date` for non-RENEW decisions). `DO_NOT_RENEW`: creates `employee_exit` `END_OF_CONTRACT` — **[OPEN] D-07-5**: its `notice_date`, `last_working_day`, `initiated_by_role` and whether it still passes through "Confirm exit" are not stated.
- **O03 Resignation.** Stores the letter as `document` (`RESIGNATION_LETTER`), inserts `employee_exit` (`RESIGNATION`, `EMPLOYEE`, `REQUESTED`, `initiated_by` = self). `last_working_day` is NOT NULL in the schema, so it is initialised to `requested_last_day` **[ASSUMPTION]** and finalised by HR in O06. Writes `RESIGNATION_RECEIVED` to the outbox. After commit, sets case file `Exit` (`exitId`, `type`, `requestedByRole = EMPLOYEE`, `approved = false`) → engine raises "Confirm exit" (HR).
- **O05 Initiate exit.** Manager: `REQUESTED`, `initiated_by_role MANAGER` → "Confirm exit" (HR). HR: `APPROVED`, `approved_by/at` set. FRD 4.3 attaches the offboarding effects (checklist, stage, notifications) to O06 only, while the HR modal's button is "Start offboarding" — **[OPEN] D-07-3**: whether an HR-started exit runs the O06 effects immediately in the same transaction (recommended reading) or still raises "Confirm exit".
- **O06 Confirm exit** (one transaction): updates type/dates/interview date; `status APPROVED`, `approved_by/at`; `employee.stage = OFFBOARDING`; creates `checklist_item` rows (`phase OFFBOARDING`) from the offboarding template (D-07-8) — one `REMOVE_ACCESS` item per row in `v_account_access` with `removes_item_id` → the onboarding `ACCOUNT` item, owner IT admin, due = last working day; one `RETURN_ASSETS` item per issued asset with `asset_id` **[ASSUMPTION]**; `HR_HANDOVER` items; `REASSIGN_TEAM` item only when `Employee.isManager`. Sets every `ISSUED` asset of the employee to `RETURN_DUE` with `return_due_date = last_working_day`. **OFF-01:** cancels open `review` (→ `CANCELLED`), `development_plan` (→ `CANCELLED`) **[ASSUMPTION — FRD names the tables, not the target statuses; `CANCELLED` is the only cancel value in each CHECK]**, and closes an open `pip` with `outcome CLOSED_EMPLOYEE_LEAVING` (PIP-10), plus their open `engine_task` rows. Writes `OFFBOARDING_STARTED`. After commit: case file `Exit.approved = true`, `Exit.lastWorkingDay`; the engine enters the Offboarding stage and raises "Remove access" (IT admin, due = last working day) and the HR offboarding task.
- **O07 Reject.** `status REJECTED` (frees `ux_exit_active`); case file `Exit.approved = false`; who is notified is not stated (D-07-4).
- **Automatic exits.** FRD-02 P04 (probation `APPROVED`) and FRD-06 HR sign-off "Not met" insert `employee_exit` (`PROBATION_NOT_PASSED` / `PIP_NOT_MET`, `initiated_by_role SYSTEM`). **[OPEN] D-07-5** (same as `02-data-model.md` open question 7 and `03-case-file.md` "automatic types skip 'Confirm exit' [OPEN]"): do they skip "Confirm exit", and who sets their NOT NULL `notice_date` / `last_working_day`?
- **O09 Ticks.** A17's logic extended to offboarding items; each tick recomputes `Exit.closeAllowed` (below). The "Project assignment ended" item (owner System) is ticked by J3 — **[OPEN] D-07-15**: `checklist_item` requires `done_by` when `DONE`, and the system has no employee id.
- **O14/O15 Assets.** Return: `RETURNED`, `returned_on = today`, `return_condition`; the matching `RETURN_ASSETS` item → `DONE`. Lost: `NOT_RETURNED` (`employee_id` stays — the DB requires it, and OFF-04/`v_unreturned_assets` reads it after close); the item → `DONE` **[ASSUMPTION]** (OFF-03 counts "marked lost" as resolved). Both write `audit_log` (`entity_type 'ASSET'`), which is S10's history.
- **`Exit.closeAllowed` (OFF-03).** Recomputed by Spring after every relevant change: no asset of the employee in `ISSUED`/`RETURN_DUE`; no row left in `v_account_access`; every mandatory `HR_HANDOVER` item `DONE`; if the leaver is a manager, no `employee.manager_id` = leaver (after applied `manager_change`) and no open `project.project_manager_id` = leaver.
- **J2.** For each live exit, at `last_working_day − ASSET_RETURN_REMINDER days`, if the setting is enabled, writes `ASSET_RETURN_REMINDER` to the outbox (Employee).
- **J3 (end of last working day).** Ends the employee's open `project_assignment` rows (`end_date = last_working_day`, `end_reason EMPLOYEE_LEAVING`); sets `employee.account_status = DISABLED` and disables the identity-provider user (FRD §5 "Integrations: identity provider account disabled at the end of the last working day") — requires a new `KeycloakAdminClient` operation. Login after that fails with FRD-01's generic "Invalid e-mail or password." (WS-04).
- **O12 Archive.** Re-checks OFF-03 server-side (409 otherwise). `employee_exit.status CLOSED`, `closed_at`; `employee.stage ARCHIVED`, `archived_at`. After commit: milestone "Case completed". From then on every write for the employee is refused (G-10/OFF-06); `NOT_RETURNED` assets stay in `v_unreturned_assets` (OFF-04).
- `employee_exit.status` value **`IN_PROGRESS`** is in the schema but no FRD endpoint writes it (D-07-16).

## 8. Case model needs

`03-case-file.md` already designs both items, and FRD-07 v1.2 §5 "Case File Items" lists exactly the same fields — no new field is needed:

| Item | Field | Type | Written by | Read by (sentry/routing) | DB source |
|---|---|---|---|---|---|
| **Contract** (0..1, fixed-term only) | contractId | long | Spring | link | `contract.id` |
| | endDate | date | Spring | – | `contract.end_date` |
| | renewalDecisionDate | date-time | Spring = end − renewal setting | J1 → "Contract renewal decision" (HR) | `setting` |
| | renewalEnabled | boolean | Spring | guard on J1 | `setting.enabled` |
| | decision | RENEW / CONVERT / DO_NOT_RENEW | task output (O02) | RENEW → repeat; DO_NOT_RENEW → Exit | `decision (CONTRACT_RENEWAL)` |
| **Exit** (0..1) | exitId | long | Spring | link | `employee_exit.id` |
| | type | 5 exit types | Spring | automatic types skip "Confirm exit" **[OPEN]** (D-07-5) | `employee_exit.exit_type` |
| | requestedByRole | EMPLOYEE / MANAGER / HR / SYSTEM | Spring | MANAGER / EMPLOYEE → "Confirm exit" | `employee_exit.initiated_by_role` |
| | approved | boolean | task output or Spring | → Offboarding | `employee_exit.status` |
| | lastWorkingDay | date-time | Spring | access-removal due date | `employee_exit.last_working_day` |
| | closeAllowed | boolean | Spring (OFF-03) | enables "Archive & close case" | assets, checklist, manager_change |

Also read: `Employee.isManager` ("Reassign team & projects" applicability), `Employee.contractType` (entry of the Contract item).

**Plan items needed (none exist in the deployed `employeecase` model):** stage **Offboarding** (entry: `Exit.approved`); human tasks **"Contract renewal decision"** (HR), **"Confirm exit"** (HR), **"Remove access"** (ITAdmin, due `Exit.lastWorkingDay`), and the HR task that opens S5 (**[OPEN] D-07-9** — the FRD calls it only "HR task"); milestone **"Case completed"** (on O12). Task names are the R-CM-3 keys for `setting` `TASK_DUE`; due dates for "Contract renewal decision", "Confirm exit" and the HR offboarding task are not stated anywhere (D-07-9). The wireframe's "Revoke access" heading does not override the FRD's task name "Remove access" (R-SRC-1).

**Exit triggers into this model** from other features: FRD-02 `Probation.hrDecision = APPROVED` and FRD-06 `PIP.result = NOT_MET` (both "→ Exit" in `03-case-file.md`) and `Contract.decision = DO_NOT_RENEW`. OFF-01 also requires the model to terminate the open development/appraisal/project-review/PIP plan items when Offboarding starts.

**Naming drift to record when the model is built:** `03-case-file.md` writes `renewalDecisionDate = end − CONTRACT_RENEWAL` and FRD 4.1 says `setting (CONTRACT_RENEWAL)`, but the seeded code is **`CONTRACT_RENEWAL_REMINDER`** (`V10`) (D-07-13).

Timers use the E1 fallback (Spring computes absolute dates, a scheduler polls), never native `timerExpression` (R-CM-4, `03-case-file.md` §1). The model work is a `cmmn-modeler` prerequisite for gate 3, like FRD-02's probation half.

## 9. Data touched

**Tables (FRD §5 summary):** `contract`, `decision (CONTRACT_RENEWAL)`, `employee_exit` (incl. resignation and exit interview columns), `checklist_item (OFFBOARDING)`, `checklist_template`, `asset`, `document`, `manager_change`, `project_assignment`, `audit_log (asset history)`; plus `employee`, `review`, `development_plan`, `pip`, `project`, `engine_task`, `notification`, `email_outbox`, `setting`.
**Views:** `v_contract`, `v_account_access`, `v_unreturned_assets`. **Keys:** `ux_exit_active` (one live exit per case); `checklist_item.removes_item_id`.

| Object | State in the real schema | Needed by F08 |
|---|---|---|
| `employee_exit`, `v_unreturned_assets` | Exist (`V13`) | Use as is |
| `asset`, `checklist_item`, `checklist_template(_item)`, `document`, `contract` | Exist (`V1`); section/kind enums already include `RETURN_ASSETS`, `REMOVE_ACCESS`, `HR_HANDOVER`, `REASSIGN_TEAM`, `EXIT_INTERVIEW`, `ASSIGNMENTS_END`, owner `SYSTEM` | Use as is |
| `decision` | **Missing** — DDL fully designed in `02-data-model.md` §2.7 | New migration, DDL transcribed verbatim (same precedent as `V11`/`V13`) |
| `v_contract` | **Missing** — described only ("contract + `probation_end_date`") | New migration; view SQL must be written from that description — **[NEEDS APPROVAL]** (also blocks FRD-02's probation half) |
| `v_account_access` | **Missing** — described only ("onboarding ACCOUNT items done without a done `removes_item_id` item") | Same, **[NEEDS APPROVAL]** |
| Offboarding `checklist_template` + items | **None seeded** | Seed migration with the wireframe's items (D-07-8) |
| `setting` rows `CONTRACT_RENEWAL_REMINDER`, `ASSET_RETURN_REMINDER`, `NOTICE_PERIOD_*` | Exist (`V10`) | Use as is |
| `email_template` rows `CONTRACT_ENDING`, `RESIGNATION_RECEIVED`, `OFFBOARDING_STARTED`, `ASSET_RETURN_REMINDER` | Exist (`V9`, recipients partly `[ASSUMPTION]` there) | See §10 |

FRD-07's own cross-check against `V1__init.sql` (cited, not re-derived): "OK: Unique partial index ux_exit_active allows one live exit per employee." · "OK: employee_exit.exit_type covers RESIGNATION, END_OF_CONTRACT, TERMINATION, PIP_NOT_MET and PROBATION_NOT_PASSED, matching every exit path." · "OK: decision CONTRACT_RENEWAL allows RENEW / CONVERT / DO_NOT_RENEW; RENEW requires new_start_date and new_end_date." · "OK: asset CHECK: RETURNED requires return_condition; NOT_RETURNED covers "Lost asset"; v_unreturned_assets feeds OFF-04 and the report." · "OK: checklist_item.removes_item_id links each "remove access" item to the onboarding ACCOUNT item (v_account_access)." · "Note: Feature 4.7 reads audit_log (entity_type ASSET); entity_type is free text, so ASSET is a naming convention, not a constraint." (The cross-check was against `V1__init.sql` as designed; it does not notice that `decision`/`v_contract`/`v_account_access` were never actually applied — this spec's own finding.)

**Case file:** `Contract` and `Exit` items (§8) — already in `03-case-file.md`, not yet in `employeecase.type`.

## 10. E-mails & timers

| Trigger | Template (FRD-08) | To (seeded in `V9`) | Note |
|---|---|---|---|
| "Contract renewal decision" task raised (J1) | `CONTRACT_ENDING` — "60 days before a fixed-term contract ends" | HR | V9 recipient marked `[ASSUMPTION]`; consistent with the FRD timer row "Goes to HR" |
| Resignation submitted (O03) | `RESIGNATION_RECEIVED` — "An employee submits a resignation" | Manager, HR | Matches the wireframe toast "Resignation sent to HR and your manager" |
| Offboarding starts (O06, or HR-started O05) | `OFFBOARDING_STARTED` — "Offboarding starts" | HR, IT admin | **[OPEN] D-07-17**: FRD says "Everyone involved is notified" and the wireframe toast says "employee, manager and IT notified"; the seeded row omits Employee and Manager |
| 3 days before last working day (J2, OFF-05) | `ASSET_RETURN_REMINDER` — "3 days before the last working day" | Employee | Matches the FRD timer row |
| Every new task (Contract renewal decision, Confirm exit, Remove access, HR offboarding task) | `TASK_ASSIGNED` (G-04, built by F01) | task owner | — |
| Exit rejected (O07) | none listed in FRD-08's 21 templates | — | **[OPEN]** D-07-4 |

| Timer (FRD-08 Timers & rules) | Default | Goes to | Can be switched off | Setting code |
|---|---|---|---|---|
| Contract renewal reminder | 60 days before end | HR | Yes | `CONTRACT_RENEWAL_REMINDER` → `Contract.renewalEnabled` |
| Asset return reminder | 3 days before last day | Employee | Yes | `ASSET_RETURN_REMINDER` |
| Notice period – permanent contract | 30 days | HR | No | `NOTICE_PERIOD_PERMANENT` (O04) |
| Notice period – fixed-term contract | 30 days | HR | No | `NOTICE_PERIOD_FIXED_TERM` (O04) |
| Overdue reminder e-mail | 1 day after due | Task owner | Yes | `OVERDUE_REMINDER_DAYS` (G-05, built) |

Values are copied when a cycle starts (ADM-02, R-ARC-7).

## 11. Acceptance criteria

From FRD-07 §6 (wording kept):

**AC-07-1 (FRD "Resignation starts offboarding")**
```
Given Thomas Mulder submits a resignation with last working day 24-10-2026
When HR confirms the exit
Then his case moves to Offboarding
And IT admin receives a "Remove access" task for 24-10-2026
```

**AC-07-2 (FRD "Assets come from the register")**
```
Given Thomas has a laptop, a phone and an access badge in the asset register
When HR opens his offboarding checklist
Then all 3 assets are listed under Return assets
```

**AC-07-3 (FRD "Case cannot close with a missing asset")**
```
Given the phone CW-PH-0087 is not returned or marked lost
Then "Archive & close case" is disabled
```

**AC-07-4 (FRD "Lost asset")**
```
Given the phone is not returned
When HR marks it as lost
Then the asset shows "Not returned" in the Unreturned assets report
```

**AC-07-5 (FRD "Contract renewal reminder")**
```
Given Priya Sharma's fixed-term contract ends on 30-11-2026
When the date is 01-10-2026
Then HR receives a "Contract renewal decision" task
```
(01-10 is exactly 60 days before 30-11 — the FRD's example confirms the default.)

**AC-07-6 (FRD "Leaving manager still has a team")**
```
Given Mark Smit is offboarding and 2 employees still report to him
When HR opens his offboarding checklist
Then "Reassign team and projects" is open
And "Archive & close case" is disabled
```

Derived — **[ASSUMPTION]**, no literal FRD scenario; confirm before building:

**AC-07-7 (from the validation table and OFF-03)**
```
Given an offboarding case with one access item still open
When HR calls "Archive & close case"
Then the request is refused with "Return or mark all assets, remove all access, reassign team and projects and complete all items first."
```

**AC-07-8 (from §2 "An exit started by a manager needs HR approval")**
```
Given Mark Smit initiates an exit for Lotte Jansen
When HR has not yet confirmed it
Then Lotte's case does not move to Offboarding
And HR has a "Confirm exit" task
```

**AC-07-9 (from OFF-07 and FRD-01's "Leaver cannot log in")**
```
Given Thomas Mulder's last working day is 24-10-2026
When he tries to log in on 25-10-2026
Then the login fails with "Invalid e-mail or password."
```

**AC-07-10 (from the validation table)**
```
Given IT admin adds an asset with number CW-PH-0087, which already exists
When they save
Then the message "This asset number is already in use." is shown
```

**AC-07-11 (from OFF-01 / PIP-10)**
```
Given Daan Visser is on an active PIP
When HR confirms his exit
Then the PIP closes with outcome "Closed – employee leaving"
And his open PIP, development, appraisal and project review tasks are cancelled
```

## 12. Decisions & open items

| ID | Topic | Status |
|---|---|---|
| D-07-1 | S4 "Confirm exit (HR)" has no wireframe page | **[OPEN]** — FRD's own Appendix A #5 ("Draw them, or state that HR uses the same page with an HR action bar"), shared with FRD-02 Screen 13 (FRD-02 spec D-P4) and FRD-06 Screen 7. `wireframes.md` S4 drafts the "dedicated page" option. A read endpoint for it (`GET` one exit) is not in FRD 4.3 — needs adding to §6 if the page is built (R-API-4). |
| D-07-2 | Manager "Initiate exit" button | **[OPEN]** — FRD's own Appendix A #7: "FRD lets a manager start an exit; the button appears only on HR records (p-hr-case-*). Suggested fix: Add "Initiate exit" to p-mg-case-*." Also conflicts with FRD-01 §2's HR-only header-action list (FRD-01 spec D-W8). FRD-07 + FRD-00 matrix both grant it; recommend following them. |
| D-07-3 | HR-started exit: does it still raise "Confirm exit"? | **[OPEN]** — O05 says HR starts "(approved)"; the checklist/stage/e-mail effects are listed only under O06; the modal's button is "Start offboarding". Recommended reading: HR-started = confirm in one step. |
| D-07-4 | Reject scope and follow-up | **[OPEN]** — FRD: "HR rejects a manager-started exit". Can HR reject a resignation? Who is told, with which template (none in FRD-08's list)? No reason field exists in `employee_exit`. |
| D-07-5 | Automatic exits (PROBATION_NOT_PASSED, PIP_NOT_MET, DO_NOT_RENEW) | **[OPEN]** — skip "Confirm exit"? (`02-data-model.md` open question 7, `03-case-file.md` "[OPEN]"). Who sets their NOT NULL `notice_date`/`last_working_day`? `initiated_by_role` for DO_NOT_RENEW (HR or SYSTEM)? |
| D-07-6 | Convert to permanent: new contract start date | **[OPEN]** — FRD says "new row"; the DB forbids `new_start_date` on CONVERT. |
| D-07-7 | "Manager advice" on S1 | **[OPEN]** — drawn in `p-hr-contract`, no FRD input/column. Omit until resolved. |
| D-07-8 | Offboarding checklist template | **[OPEN]** — none is seeded; which template is used at confirm (no template field anywhere in the exit flow; `checklist_template.job_title_id` exists); item due dates relative to what (onboarding offsets are from the start date)? Proposed seed = the wireframe's HR & handover items. |
| D-07-9 | Task names/due dates and S7 "Mark done" | **[OPEN]** — the HR task behind S5 is unnamed ("HR task"); due dates for "Contract renewal decision", "Confirm exit" and the HR task are not given; S7's "Mark done" has no FRD endpoint (auto-complete "Remove access" when all its items are done, or a completion endpoint?). Also the wireframe's "18:00" schedule time has no source. |
| D-07-10 | HR access to the asset register | **[OPEN]** — FRD-00 matrix "View" vs FRD-07 "Screens 7–10 are IT admin-only". |
| D-07-11 | Asset register wireframe vs schema | **[OPEN]** — Add equipment offers 5 types, the DB CHECK has 8 (`02-data-model.md` open question 6 says "Exit reasons and asset types are taken from the wireframe", yet WF-07 shows fewer — the DB list is the applied contract and F01's A21 already validates against it); Status filter has no "In storage"; Issue date required even for "In storage" (DB: null while IN_STORAGE); S10 "Mark returned" has no condition field although condition is required. |
| D-07-12 | "The last working day must be after the notice date." on S3/S4 | **[ASSUMPTION]** — reused for `last_working_day < notice_date` (FRD states it for the resignation's requested day only; DB allows equality). |
| D-07-13 | Setting code naming | Recorded: FRD/`03-case-file.md` say `CONTRACT_RENEWAL`; the real seeded code is `CONTRACT_RENEWAL_REMINDER`. Use the real code; fix the docs when `03-case-file.md` is next touched. |
| D-07-14 | Built-path differences | Recorded: My Profile is at `/my-profile` (FRD `/me/profile`); document upload exists as A07 `POST /api/documents` (FRD `POST /employees/{code}/documents` for FINAL_DOCUMENT) — decide extend vs add before gate 2. |
| D-07-15 | System-ticked "Project assignment ended" item | **[OPEN]** — `checklist_item` CHECK requires `done_by` when `DONE`; J3 has no employee. |
| D-07-16 | `employee_exit.status IN_PROGRESS` | **[OPEN]** — in the schema, written by no FRD endpoint (confirm → `APPROVED`, archive → `CLOSED`). |
| D-07-17 | `OFFBOARDING_STARTED` recipients | **[OPEN]** — seeded HR + IT admin (`V9`, `[ASSUMPTION]` there); FRD "Everyone involved" and wireframe "employee, manager and IT". |
| D-07-18 | Missing schema objects | **[NEEDS APPROVAL]** — `decision` (verbatim DDL available), `v_contract`, `v_account_access` (SQL to be written from one-line descriptions) must be migrated by F08 Gate 1. `v_contract` is also needed by FRD-02's probation half. |

## 13. Definition of done

- [ ] Spec approved by the user (status → Spec ready); D-07-1…D-07-5, D-07-8, D-07-9 and D-07-18 answered at minimum (CLAUDE.md §2.9 "ask when blocked").
- [ ] Gate 1: migration for `decision`, `v_contract`, `v_account_access`, the offboarding checklist template seed, and any e-mail recipient correction (D-07-17); `02-data-model.md` updated in the same change (R-DB-1).
- [ ] Gate 2: O01–O20 and J1–J3 implemented with server-side role/ownership checks, G-10 refusal on archived cases, audit, optimistic locking; A17 extended to offboarding items; `KeycloakAdminClient` disable operation; IT admin dashboard counts added (FRD-01 D-W2).
- [ ] Gate 3: `employeecase` model extended — `Contract`/`Exit` items, Offboarding stage, the four tasks, "Case completed" milestone, exit-trigger sentries from Probation/PIP/Contract; `03-case-file.md` naming fix (D-07-13).
- [ ] Gate 4: S1–S10 at the routes in §4, R-UI-1…R-UI-9 (button colours/placement, dd-mm-yyyy, tables sortable/filterable/searchable/exportable, archived read-only).
- [ ] Every acceptance criterion in §11 has an automated test.
- [ ] Dependencies in place: F01 (assets, checklist), F03 (Transfer manager — built), F04 (Change project manager, `project_assignment` writes — not built), F05/F06/F07 (development plan, appraisal, PIP tasks that OFF-01 cancels; FRD-06 PIP-not-met trigger), F02 (probation "End employment" trigger).
