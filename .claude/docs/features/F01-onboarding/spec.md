# F01 — Create employee → Onboarding

| | |
|---|---|
| Status | **Draft (awaiting review)** |
| Sources | FRD v1.1: FRD-00 (roles, G-rules, layout rules), FRD-01 (shared workspace — parts), FRD-02 (onboarding — the onboarding part; probation review is F02) |
| Wireframes | `docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html` — pages `p-hr-employees`, `p-hr-onb-new`, `p-hr-onb-jesse`, `p-hr-onb-sanne`, `p-it-provision`, `p-it-sanne`, `p-sb-dash`, `p-sb-tasks` / `p-hr-tasks` / `p-mg-tasks` / `p-it-tasks`, `p-sb-profile`, `p-hr-case-*`, `p-mg-case-*`, `#login-view`, modals `m-add-item`, `m-add-asset`, `m-equip` |
| Order of truth | FRD > this spec > `wireframes.md` > wireframe HTML (R-SRC-1). Conflicts are listed in §12. |

## 0. Status & sources

This is the first feature built (CLAUDE.md §2.9). It is written top-down: screens → API → backend → case model. The engine spike (E1–E5, `01-overview.md` §5) ran 2026-10-15: E2–E5 hold as assumed; E1 (timer reading a case-file date directly) does not hold on this engine version, so the pre-agreed fallback is used instead (Spring scheduler raises the overdue action — see §8 below and `01-overview.md` §5). Gate 3 can now proceed.

Nothing in this spec invents a field, message, table or endpoint beyond what the FRD, `02-data-model.md` and `03-case-file.md` already allow (R-SRC-2). All decisions D-01…D-21 are now resolved by the user (2026-10-15, §12); none are open.

## 1. Scope

**In scope**
- Creating a new employee in three steps on one page: Employee details, IT & equipment request, Checklist (FRD-02 §3 "Main flow").
- Draft employee details, saved without validation (FRD-02 "Save as draft").
- Onboarding checklist generated from a template, with HR/IT/Manager/Employee ticking their own items.
- IT equipment & account request, the IT admin's "Equipment request" and "Remaining setup" screens, and the asset register writes that result from them.
- The employee's own onboarding tasks: policy acknowledgement, completing the profile, confirming equipment received.
- Completing onboarding (milestone "Employee onboarded", stage → Active, "Create development plan" task raised for F04 to pick up — F01 only raises the fact, it does not build that screen).
- The shared basics this flow needs, and no more: Login (FRD-01), My Tasks + Notifications (FRD-01), the Employees list (FRD-01/FRD-02), the Employee record's **Overview**, **Profile** and **History** tabs (FRD-01) for HR and the manager.
- Settings seed data F01 needs before it can run: departments, job titles, the four onboarding checklist templates, 4 e-mail templates (`Welcome new employee`, `IT equipment request`, `Onboarding completed`, `Set your password`), and the TIMER/TASK_DUE `setting` rows this flow reads.

**Out of scope (left to later features, per CLAUDE.md §2.9.4 "never mix features")**
- Probation review (F02) — the FRD-02 "Probation review" process, its screen and its e-mail template.
- The employee record's **Projects** and **Development & reviews** tabs — they read tables (`project_assignment`, `kpi`, `development_plan`, `review`) that do not exist until F03/F04/F05 migrate. F01 does not render them.
- The HR/Manager dashboards' KPI cards and the full role menus (Appraisals, Reports, Settings, Projects, My Team analytics) — only the menu items F01 actually builds are shown (§4).
- `Initiate exit` and `Transfer manager` header actions on the employee record (F07/F01-later), and `Start PIP` / `Assign project` for managers (F06/F03).
- Settings **screens** (FRD-08) — F01 seeds what it needs with a Flyway seed migration, per `docs/features/README.md`.

## 2. Roles & permissions

Per FRD-00 §2 and the permissions table, scoped to this flow:

| Capability | HR | Manager | Employee (new joiner) | IT admin |
|---|---|---|---|---|
| Create / save draft / edit a new employee | Yes | No | No | No |
| See employees | All (incl. drafts) | Own team only (WS-02) | Self only | Name, status, assets only |
| Edit employee profile | Yes | No (read-only) | Own contact + emergency only (WS-03) | No |
| Send IT & equipment request | Yes | No | No | No |
| Record issued equipment / tick accounts | No | No | No | Yes |
| Tick onboarding checklist items | Any owner's item (D-04) | Own orientation items | Own items only (ONB-07) | Own items only |
| Complete onboarding | Yes | No | No | No |
| (Re)send set-password e-mail | Yes | No | No | No |

**G-13 (2026-10-16 correction):** the "Edit employee profile" row above is about editing *another* employee's profile (A32/A33, HR-only). Separately, FRD-00's G-13 ("Every role includes the Employee functions for the user's own record") means every role — HR, Manager, Employee, IT admin — can view and update *their own* contact and emergency details via A29/A30 (`GET`/`PUT /api/me/profile`), the same WS-03-scoped fields an Employee can change about themself. This was previously built as EMPLOYEE-role-only, which left HR/Manager/IT-admin with no way to maintain their own contact details anywhere in the app — corrected.

Every endpoint checks the role **and** ownership on the server (R-API-2/G-02); hiding a button is never the only protection (R-UI-7).

## 3. Business rules

Rules copied from the FRD, by ID (R-SRC-3: wording kept exact where the FRD gives wording).

| ID | Rule |
|---|---|
| ONB-01 | Probation end date = start date + probation period, calculated by the system. |
| ONB-02 | The template creates the checklist items with owner and due date relative to the start date. |
| ONB-03 | Each checklist item is shown in its owner's My Tasks until it is done. |
| ONB-04 | Ticking a checklist item marks it Done immediately and updates the progress bar. |
| ONB-05 | "Complete onboarding" is available only when all mandatory items are done. |
| ONB-06 | Every item IT records under "Equipment issued" is added to the employee's assets. |
| ONB-07 | The employee sees their own onboarding items only; they never see HR-only items. |
| ONB-08 | If probation ends with "End employment", HR must approve before offboarding starts. **Deferred to F02/F08** (probation review and offboarding are not part of F01's scope — see §1 Scope); listed here for traceability only. |
| ONB-09 | An employee whose private e-mail already exists cannot be created a second time; the system shows the existing employee instead (G-01). |
| WS-01 | The dashboard and menu shown depend only on the user's role. |
| WS-02 | A manager sees only employees whose current manager they are. |
| WS-03 | An employee can change only their own contact and emergency details. Employment details (including name and date of birth) are read-only for them. |
| WS-04 | Login errors never reveal whether the e-mail address exists. |
| WS-05 | Profile changes made by HR are recorded in History (G-06). |
| G-04 | **(2026-10-16, FRD-00)** Every new task creates an in-app notification and an e-mail to its owner. Within F01 this fires for every checklist item created (template instantiation, A14's "Send to IT" additions, A16's "Add item") and every `engine_task` row created ("Onboarding checklist" → HR, "Equipment request"/"Remaining setup" → IT admin). See §9/§10 for the `TASK_ASSIGNED` e-mail template and `TaskNotificationService`. |
| G-05 | **(2026-10-16, FRD-00)** An overdue reminder e-mail is sent 1 day after the due date (configurable, FRD-08 — `setting.OVERDUE_REMINDER_DAYS`, can be switched off unlike `ONBOARDING_OVERDUE`). Covers both `checklist_item` and `engine_task` rows, reminded at most once each (`overdue_reminder_at`). See §9/§10 for the `TASK_OVERDUE` e-mail template and `OverdueReminderScheduler`. |

Decided for F01 (full list of discussion in §12):
- **D-04** Ticking saves immediately; HR may tick any owner's item on their behalf. `checklist_item.done_by` records who, and History shows "done by HR on behalf of {owner}".
- **D-08** Only HR's and IT admin's checklist-driven work is an engine task (`Onboarding checklist`, `Equipment request`, `Remaining setup`). The employee's and manager's checklist items are plain `checklist_item` rows that appear in My Tasks from the database, not from the engine (this avoids one engine task per checklist row).
- **D-02** Login uses the private e-mail first (`employee.private_email`). HR can switch the login to the work e-mail later from the Profile tab once IT has created it.
- **D-05** "Send to IT" adds a `checklist_item` (section `ACCOUNTS_INSTALLATIONS` or `EQUIPMENT_ISSUED`, owner `IT_ADMIN`) for every selected accessory or account that is not already in the checklist from the template.
- **D-06** The IT option lists (laptops, keyboard layouts, delivery methods, accessories, accounts) are a fixed, seeded list served by `GET /api/lookups/it` (A03), not a settings screen (FRD-08 does not list them).
- **D-07** The policy shown in the policy pop-up (S8) is an uploaded PDF per template item (`checklist_template_item.policy_document_id`, `document.doc_type = 'POLICY'`).
- **D-13** "Save draft" writes to the separate `onboarding_draft` table (unvalidated except first/last name), not to `employee`. It is deleted the moment "Save & continue" succeeds.
- **D-15** A checklist item can be unticked until onboarding is completed (`DELETE /api/checklist-items/{id}/done`, A18); both the tick and the untick are logged.

## 4. Screens

| # | Screen | Who | Wireframe page / element | FRD |
|---|---|---|---|---|
| S1 | Employees list | HR | `p-hr-employees` | FRD-01 §4, FRD-02 |
| S2 | Onboarding — step 1 Employee details | HR | `p-hr-onb-new` tab `onbnew-0` (same page as S3/S4, reused for in-progress employees as `p-hr-onb-jesse` / `p-hr-onb-sanne`) | FRD-02 §3 "Main flow" |
| S3 | Onboarding — step 2 IT & equipment request | HR | tab `onbnew-1` | FRD-02 §3 |
| S4 | Onboarding — step 3 Checklist (+ "Add item" pop-up) | HR | tab `onbnew-2`; modal `m-add-item` | FRD-02 §3, §4 |
| S5 | Equipment request (+ "Add equipment" pop-up) | IT admin | `p-it-provision`; modal `m-add-asset` | FRD-02 §3, §4 |
| S6 | Remaining setup | IT admin | `p-it-sanne` | FRD-02 §3 "Other paths" |
| S7 | New-joiner dashboard | Employee (new) | `p-sb-dash` | FRD-02 §4, ONB-07 |
| S7M | Orientation items (manager) | Manager | reuses the S4 checklist component, filtered to the manager's own items | FRD-02 §3 step 7 "HR and the manager complete orientation items" |
| S8 | Policy pop-up | Employee | **not drawn in the wireframe** — built from FRD-02 §4 "Policy (pop-up)" | FRD-02 §4 |
| S9 | Confirm equipment pop-up | Employee | modal `m-equip` | FRD-02 §4 |
| S10 | My Profile (personal + emergency) | Employee | `p-sb-profile` | FRD-01, WS-03 |
| S11 | Employee record — **Overview**, **Profile**, **History** tabs only | HR (all), Manager (own team, Overview + read-only Profile) | `p-hr-case-jesse`, `p-mg-case-jesse` | FRD-01 §3 "Employee record" |
| S12 | My Tasks + notifications panel | All | `p-hr-tasks` / `p-mg-tasks` / `p-sb-tasks` / `p-it-tasks`; bell + dropdowns in the top bar | FRD-01, G-03/04/05 |
| S13 | Login | All | `#login-view` | FRD-01 |

`Set password` / `Forgot password` are Keycloak-hosted pages themed per FRD-01 §5, not Spring screens (R-API-1 does not apply there).

## 5. Fields & validation

### S2 — Step 1: Employee details

| Card | Fields (`*` = required) | Message |
|---|---|---|
| Personal details | First name*, Last name*, Private e-mail*, Mobile phone*, Date of birth* (dd-mm-yyyy), Nationality, Street + number*, Postcode*, City* | required → "This field is required." · e-mail → "Enter a valid e-mail address." · phone → "Enter a valid phone number, e.g. +31 6 12345678." · postcode → "Enter a valid postcode, e.g. 3511 AV." · DOB → "The employee must be at least 16 years old." · duplicate e-mail → "An employee with this e-mail address already exists." (409, see §6) |
| Contract | Contract type* (Permanent / Fixed-term), Start date*, End date* (hidden/disabled for Permanent), Hours per week* (default 40), Probation period* (1 month / 2 months / None), Probation ends (read-only, ONB-01) | required → "This field is required." · fixed-term w/o end date → "This field is required." · end before start → "The end date must be after the start date." · hours → "Hours must be between 1 and 40." |
| Verification | ID document checked in person*, Signed contract received*, Right-to-work verified (optional), contract file upload | both boxes unticked on Save & continue → "Confirm that the ID was checked and the contract is signed." No ID-number field exists (G-09). |
| Role & reporting | Job title*, Department*, Manager*, Grade* (JUNIOR/MEDIOR/SENIOR/SENIOR_I/SENIOR_II), Application role* (Employee/Manager/HR/IT admin), Buddy (optional), Onboarding template* | required → "This field is required." |
| Template preview | Total item count, then each section with its item count and item names, for the selected template | read-only, reacts to the template dropdown |

Steps can be opened in any order once the employee exists (FRD-02's "Other paths" section: "Steps can be opened in any order. HR can click any step to view or change it." — corrected 2026-10-16; previously mis-cited as a "Main flow" step 7 note, which is actually about HR/manager orientation items, not step ordering). **Save draft** checks only first and last name and writes to `onboarding_draft` (D-13); no case starts, no e-mail or task is sent. **Save & continue** runs every check above, then: creates `employee` + `contract` + `onboarding` (IN_PROGRESS) + the checklist from the template (ONB-02), deletes the matching draft if any, starts the case, and sends the welcome e-mails.

### S3 — Step 2: IT & equipment request

| Field | Rule | Message |
|---|---|---|
| Laptop*, Ready by*, Delivery* | Required to send | "Complete the equipment request before sending it to IT." |
| Ready by | On or before the start date | "The ready-by date must be on or before the start date." |
| Keyboard layout | **Optional** — the wireframe marks it required but the FRD does not (R-SRC-1: FRD wins; D-16) | — |
| Accessories (checkboxes) | Monitor, Keyboard & mouse, Headset, Docking station, Phone, Access badge | — |
| Accounts & software (checkboxes) | Microsoft 365 / e-mail, Slack, VPN, Jira/Confluence, GitHub, Figma, AWS console, Docker Desktop | — |
| Notes for IT | Optional free text | — |

Sending creates the IT "Equipment request" task and the "IT equipment request" e-mail (template), adds a checklist item per D-05, and sets the case-file `Onboarding.itRequestSent = true`. Already-sent requests show the status badge ("Sent to IT · {date} · in progress" / "Completed by IT · {date}") and the button becomes "Save & continue".

### S4 — Step 3: Checklist

| Item | Rule |
|---|---|
| Ticking / unticking | Immediate (ONB-04); untick allowed until onboarding is completed (D-15); both logged. There is no separate "Save" for the checklist — the wireframe's "Save" button is **not built**, since ticking already persists (ONB-04 overrides the wireframe, R-SRC-1). |
| "+ Add item" (modal `m-add-item`) | Item\*, Section\* (Documents & admin / Equipment issued / Accounts & installations / Orientation), Owner\* (HR/IT admin/Manager/Employee), Due (days from start)\*, Mandatory\*. All required → "This field is required." |
| "Complete onboarding" | Disabled until all mandatory items are done (ONB-05); attempting anyway → `409 MANDATORY_ITEMS_OPEN`. |

### S5 — Equipment request (IT admin)

| Field | Rule | Message |
|---|---|---|
| Type\* (all 8 values of `asset.asset_type`: Laptop, Monitor, Phone, Access badge, Docking station, Headset, Keyboard & mouse, Other — D-17 decided, the wireframe's 5-option list is extended to match the schema), Model\*, Asset no.\* (prefilled "CW-"), Issued to\*, Issue date\* in "+ Add equipment" (modal `m-add-asset`) | Required | "This field is required." |
| Asset number | Unique | "This asset number is already in use." (409) |

Saving adds the asset as `ISSUED` (ONB-06) and ticks the matching `EQUIPMENT` checklist item. "Mark setup done" completes the "Equipment request" task (202 undo window).

### S6 — Remaining setup (IT admin)

"Mark done" completes the "Remaining setup" task only when every mandatory item in it is ticked: **"Tick all mandatory items first."** (new message, no FRD wording exists — D-14.)

### S7 / S8 / S9 — Employee's own tasks

| Screen | Action | Message |
|---|---|---|
| S8 Policy pop-up | "Confirm" requires the "I have read and accept this policy" box ticked | **"Confirm that you have read the policy."** (new message — D-14.) |
| S9 Confirm equipment (modal `m-equip`) | A table of items with a "Received" checkbox, plus "Anything missing or damaged?" | No required-field message defined by the FRD; the field is optional. |

### S10 — My Profile (Employee)

Editable: private e-mail, mobile phone, street, postcode, city, emergency contact (name\*, relationship, phone\*). **Read-only** (WS-03, overrides the wireframe which wrongly shows name as editable — R-SRC-1): first name, last name, work e-mail, date of birth, the whole Employment card.

| Field | Message |
|---|---|
| Mobile phone | "Enter a valid phone number, e.g. +31 6 12345678." |
| Postcode | "Enter a valid postcode, e.g. 3511 AV." |
| Emergency contact name/phone | "Add an emergency contact name and phone number." |

Save shows the G-11 confirmation toast and ticks the `PROFILE` checklist item once the emergency contact is complete. A changed private e-mail updates the Keycloak username while it is still the login e-mail (D-02).

### S11 — Employee record (within F01's scope: Overview, Profile, History)

HR's Profile tab adds two FRD-01 items missing from the wireframe (approved earlier, kept here):
- the **Application role** field (sets `employee.app_role`, G-02);
- the **(Re)send set-password e-mail** button, shown until `employee.account_status = 'ACTIVE'` (i.e. until first login).

### S13 — Login

| Field | Message |
|---|---|
| E-mail (empty) | "Enter your e-mail address." |
| Password (empty) | "Enter your password." |
| Wrong credentials | "Invalid e-mail or password." (WS-04: never reveals which part was wrong, or whether the account exists) |

Set-password / reset-password / forgot-password are Keycloak theme screens; their messages are configured in the realm theme, not in Spring.

## 6. API

Conventions: base path `/api`, camelCase JSON, dates `yyyy-MM-dd` (R-API-1). Errors follow R-API-3 exactly:
```
400 {"code":"VALIDATION_FAILED","errors":[{"field":"privateEmail","message":"Enter a valid e-mail address."}]}
409 {"code":"DUPLICATE_PRIVATE_EMAIL","message":"An employee with this e-mail address already exists.","existingEmployeeId":50}
409 {"code":"ASSET_NUMBER_IN_USE","message":"This asset number is already in use."}
409 {"code":"STALE_VERSION"}   403 {"code":"FORBIDDEN"}   404 {"code":"NOT_FOUND"}
```

| # | Method & path | Role | Notes |
|---|---|---|---|
| A01 | `GET /api/me` | all | `{employeeId, name, initials, appRole, menu[]}` — menu lists only the items §4 builds |
| A02 | `GET /api/lookups/onboarding` | HR | departments, job titles, managers, buddies, grades, application roles, active templates |
| A03 | `GET /api/lookups/it` | HR, IT | laptops, keyboard layouts, delivery methods, accessories, accounts (seeded, D-06) |
| A04 | `GET /api/employees?stage&departmentId&managerId&q&sort&page&size` | HR | page merges `employee` rows and `onboarding_draft` rows (shown with a "Draft" badge, no job title/department/manager, action "Continue" instead of "View") |
| A05 | `GET /api/employees/export?...` | HR | same filters, `.xlsx` |
| A06 | `GET /api/checklist-templates/{id}/preview` | HR | `{totalItems, sections:[{section,count,items[]}]}` |
| A07 | `POST /api/documents` (multipart, `docType=CONTRACT`) | HR | `{documentId}`; linked to the employee on create |
| A08 | `POST /api/onboarding-drafts` | HR | first/last name only validated → `{draftId}` |
| A09 | `PUT /api/onboarding-drafts/{id}` | HR | update a draft |
| A10 | `POST /api/onboardings` | HR | full step-1 form + optional `draftId` + `contractDocumentId` → `201 {employeeId, employeeCode}` / `400` / `409 DUPLICATE_PRIVATE_EMAIL` |
| A11 | `GET /api/onboardings/{employeeId}` | HR | `{details, itRequest, checklistSummary, version}` |
| A12 | `PUT /api/onboardings/{employeeId}/details` | HR | edit after creation; writes `audit_log`; a changed start date/probation updates the case-file dates |
| A13 | `PUT /api/onboardings/{employeeId}/it-request` | HR | save without sending |
| A14 | `POST /api/onboardings/{employeeId}/it-request/send` | HR | `200 {status:"SENT",sentAt}` / `400` / `409 ALREADY_SENT` |
| A15 | `GET /api/onboardings/{employeeId}/checklist?owner&status` | HR, IT (own items) | `{progress, sections[], canComplete}` |
| A16 | `POST /api/onboardings/{employeeId}/checklist-items` | HR | "+ Add item" → `201 item` |
| A17 | `POST /api/checklist-items/{id}/done` | the owner, or HR (D-04) | `200 {item, progress}` |
| A18 | `DELETE /api/checklist-items/{id}/done` | the owner, or HR | untick (D-15), blocked once onboarding is `COMPLETED` |
| A19 | `POST /api/onboardings/{employeeId}/complete` | HR | `202 {undoUntil}` / `409 MANDATORY_ITEMS_OPEN` |
| A20 | `GET /api/it/equipment-requests/{employeeId}` | IT, HR | `{employee, request, issuedAssets[], accountItems[]}` |
| A21 | `POST /api/employees/{employeeId}/assets` | IT | `201 asset` / `409 ASSET_NUMBER_IN_USE` |
| A22 | `POST /api/assets/{assetId}/unissue` | IT | the "×" before the employee confirms receipt |
| A23 | `POST /api/onboardings/{employeeId}/it-request/complete` | IT | `202 {undoUntil}`; completes "Equipment request" |
| A24 | `POST /api/onboardings/{employeeId}/it-setup/complete` | IT | `202` / `409 MANDATORY_ITEMS_OPEN` ("Tick all mandatory items first."); completes "Remaining setup" |
| A25 | `GET /api/me/onboarding` | Employee | own items only (ONB-07), team card, equipment |
| A26 | `GET /api/checklist-items/{id}/policy` | Employee | the policy PDF (D-07) |
| A27 | `POST /api/checklist-items/{id}/acknowledge` `{accepted:true}` | Employee | `400` "Confirm that you have read the policy." if false |
| A28 | `POST /api/checklist-items/{id}/confirm-equipment` | Employee | `{receivedAssetIds[], remarks}`; notifies IT admin if remarks given |
| A29 | `GET /api/me/profile` | Employee | own profile |
| A30 | `PUT /api/me/profile` | Employee | WS-03 fields only; `409 DUPLICATE_PRIVATE_EMAIL` if changed to one in use |
| A31 | `GET /api/employees/{id}` | HR; Manager (own team) | Overview tab data only (§1 scope) |
| A32 | `GET /api/employees/{id}/profile` | HR, Manager (read) | `+appRole +loginEmail +version` |
| A33 | `PUT /api/employees/{id}/profile` | HR | writes `audit_log` (WS-05); role/login-email change updates Keycloak |
| A34 | `POST /api/employees/{id}/account/resend-invite` | HR | `202` / `409 ALREADY_LOGGED_IN` |
| A35 | `GET /api/employees/{id}/history?page` | HR | `audit_log` merged with milestones |
| A36 | `GET /api/tasks?module&q&page`, `GET /api/tasks/count` | all | merges `engine_task` with the user's own open `checklist_item` rows (D-08) |
| A37 | `POST /api/tasks/{taskId}/undo` | task owner | `200` / `409 UNDO_EXPIRED` |
| A38 | `GET /api/notifications?unread`, `GET /api/notifications/count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` | all | — |

## 7. Backend behaviour

- **Transactions.** Each write endpoint runs one transaction covering its business rows + `audit_log` + any `email_outbox` row (R-ARC-6). The engine is called **after** commit (R-ARC-5), retried on failure, never rolled back silently.
- **G-04, 2026-10-16: task-assignment notifications.** `com.cubeworks.hr.notification.TaskNotificationService.notifyTaskAssigned(...)` is called, inside the same transaction, every time `OnboardingService` creates a `checklist_item` (template instantiation in A10, D-05 additions in A14, A16's "Add item") or an `engine_task` row (A10's "Onboarding checklist", A14's "Equipment request"). It resolves the task's owner role to its recipient(s) — HR/IT admin: every employee currently holding that app role; manager: the subject employee's own manager; employee: the subject themself — and writes one `notification` row plus one `TASK_ASSIGNED` `email_outbox` row per recipient.
- **G-05, 2026-10-16: overdue reminders.** `com.cubeworks.hr.task.OverdueReminderScheduler` polls hourly (day-granularity rule; `setting.OVERDUE_REMINDER_DAYS`, default 1, switchable off) for `checklist_item`/`engine_task` rows that are still open/incomplete, past their due date by the configured window, and not yet reminded (`overdue_reminder_at IS NULL`). Each remindable row is reminded exactly once, in its own transaction (one failure does not block the others): `TaskNotificationService.notifyOverdue(...)` (same owner-role resolution as G-04) writes one `notification` row plus one `TASK_OVERDUE` `email_outbox` row per recipient, then `overdue_reminder_at` is stamped so the next poll does not remind again.
- **A10 `POST /api/onboardings`:** insert `employee`, `contract`, `onboarding`, the checklist rows from the template, delete the matching `onboarding_draft`; write `audit_log`; outbox `Welcome new employee` + `Set your password`. After commit: create the Keycloak user (username = `private_email`, D-02) and start the case with the §8 initial case file.
- **A14 `.../it-request/send`:** add checklist items per D-05; outbox `IT equipment request`. After commit: set case-file `Onboarding.itRequestSent = true` → the engine raises "Equipment request" for IT admin.
- **A17/A18 tick/untick:** update `checklist_item`; when the last mandatory item becomes done (or stops being so), update case-file `Onboarding.mandatoryItemsDone`.
- **A19 `.../complete`:** `409 MANDATORY_ITEMS_OPEN` unless all mandatory items are done; otherwise return `202 {undoUntil}` (R-API-6, G-03's default 10 s from `setting`). After the undo window, complete the HR engine task with output `{Onboarding:{completed:true}}`. The engine then raises the milestone "Employee onboarded", which the task watcher turns into: `employee.stage = 'ACTIVE'`, `employee.stage_since = today`, outbox `Onboarding completed`, and a `Create development plan` row (F04's concern — F01 only writes the fact, per R-ARC-1).
- **A33 HR profile edit:** `audit_log` entry per changed field (WS-05); changing `appRole` or the login e-mail also calls Keycloak.
- All dates shown in React are `dd-mm-yyyy` (R-UI-5); Spring stores and returns ISO.

## 8. Case model needs

Per `03-case-file.md` (already approved, now including the D-18 field below).

**Items used:** `Employee` (all 4 fields), `Onboarding` (all 6 fields, including `itSetupComplete`).

**Tasks (D-08: engine tasks only for HR and IT):**

| Task | Performer | Raised when | Due date | Completed with output |
|---|---|---|---|---|
| Onboarding checklist | HR | case start | the employee's start date (D-19) | `{Onboarding:{completed:true}}` |
| Equipment request | IT admin | `Onboarding.itRequestSent = true` | the HR-entered "ready by" date | `{Onboarding:{itSetupComplete}}` — Spring computes this from `checklist_item` (see D-18 below) and sends it as the output when completing this task |
| Remaining setup | IT admin | sentry: `Onboarding.itSetupComplete = false` when "Equipment request" completes | same ready-by date (D-19's proposal, applied here too) | none |

**Milestone:** "Employee onboarded", raised when the "Onboarding checklist" task completes.

**Timer:** "Onboarding overdue" → HR, guarded by `Onboarding.overdueEnabled`, firing at `Onboarding.overdueDate`. The E1 spike (`01-overview.md` §5, run 2026-10-15) found that CaseFabric 1.1.35's `timerExpression` cannot read a case-file value directly (confirmed via a live throwaway test case: literal ISO-8601 durations fire correctly, but any case-file-driven expression fails to resolve to a parseable `Duration`/`Instant`). The model therefore does **not** declare a native CMMN timer for this; the Spring scheduler polls `Onboarding` rows where `overdueEnabled = true` and `overdueDate <= today` and raises the overdue action directly via the engine REST API (proven workable by E2/E3 in the same spike).

**Case roles set at case start:** Employee (the new hire's user), Manager (`employee.manager_id`'s user), HR (tenant role), ITAdmin (tenant role) — per `03-case-file.md` §3.

**D-18 (approved):** the case-file field `Onboarding.itSetupComplete` (boolean). Spring computes it in the same request that completes the "Equipment request" task: `true` when no `checklist_item` rows with `owner_role = 'IT_ADMIN'` are still `OPEN` for this employee, `false` otherwise. The engine model guards "Remaining setup" with a sentry on this field being `false`, so the task is raised only when IT work is actually left — never both tasks unconditionally. Added to `03-case-file.md` §2 (Onboarding item) and §4 (example).

**Gate 3 status (2026-10-15): built and live-verified**, not just deployed. `employeecase.case/.type/.caseteam` plus three `.humantask` files exist in `casefabric/getting-started/src/definitions/`; the compiled model is deployed at `casefabric/getting-started/target/definitions/employeecase.xml`. Verified directly against a running CaseFabric 1.1.35 engine (not just "it deploys"): a stub case starts with the full Employee/Onboarding case file; "Onboarding checklist" is active from case start and its `{completed:true}` output correctly **merges** into the `Onboarding` case-file item (siblings untouched — confirmed, so R-ARC-2's "case file holds only what's needed" isn't undermined by a task wiping fields it doesn't own); the milestone fires on that completion; "Equipment request" activates when `Onboarding.itRequestSent` is set via a direct case-file update (matching A14's real usage) and its `{itSetupComplete}` output likewise merges cleanly; "Remaining setup" activates only when `itSetupComplete=false` (both the positive and negative case were tested live).

Two real defects were found and fixed during this verification, neither visible from reading the files alone:
1. `casePlanModel` was `autoComplete="true"` with no `requiredRule` on any task. Since this case represents the whole employee lifecycle (like the legacy `emp.case`, hire to exit) and only F01's three tasks exist so far, the case plan auto-completed — permanently — the moment "Onboarding checklist" finished, before IT's flow ever got a chance to run. Fixed: `autoComplete="false"`, so the case stays open until a later feature (F08 offboarding) adds the real `exitCriterion`.
2. The "Equipment request" sentry had only an `ifPart` (`Onboarding?.itRequestSent == true`) and no OnPart. A sentry with no OnPart is never evaluated at all in this engine — it is not polled — so the task never actually activated even after `itRequestSent` became true. Fixed by adding a `caseFileItemOnPart` (`sourceRef="Onboarding"`, `standardEvent=update`) alongside the `ifPart`.

**Known follow-up, not yet built (needed before this flow works through the real engine end-to-end, tracked for the next piece of work, not blocking gate 3 itself):** `hr-backend`'s `engine.CaseEngineClient` is still the `NoOpCaseEngineClient` stub from gate 2 (built before this model existed). Swapping in a real HTTP client, and adding the task-watcher component CLAUDE.md's repo layout already names (`engine/` package: "task watcher") to mirror engine-raised tasks — specifically "Remaining setup", which nothing in `OnboardingService` currently creates an `engine_task` row for, since it's raised by the sentry above rather than by a direct HR/IT action the way "Onboarding checklist"/"Equipment request" are — is separate work from the CMMN model itself. It also needs an authentication decision for how Spring calls the engine as a technical/service user (see D-22 and the E2 spike note on `01-overview.md` §5), which touches `casefabric/getting-started`'s own Dex IDP configuration. **Decided (user, 2026-10-15): deferred.** Gate 4 (frontend) proceeds first against the current Spring-only behavior; the real engine client, task watcher, and this auth decision are picked up afterward. Until then, "Remaining setup" does not appear anywhere in the running system (it is only proven correct inside the CMMN model itself, per the live verification above) and the engine never actually starts/updates a real case — `NoOpCaseEngineClient` only logs what it would have done.

## 9. Data touched

**Tables (all already in `02-data-model.md`, no new table):** `department`, `job_title`, `employee`, `contract`, `document`, `checklist_template`, `checklist_template_item`, `onboarding_draft`, `onboarding`, `checklist_item`, `asset`, `email_template`, `setting`, `email_outbox`, `audit_log`, `notification`, `engine_task`.

**Seed data (Flyway `V2__seed_settings.sql`):**
- Departments and job titles (D-12, decided): placeholder rows matching the wireframe, to be edited by HR later through settings (F09).
- 4 `checklist_template` rows (Development, Design, QA, General) with their items, exactly as counted in the wireframe's template preview (18/17/17/10 items) (D-12, decided): placeholder contents, owners and mandatory flags, editable by HR later.
- `email_template` rows: `WELCOME_NEW_EMPLOYEE`, `SET_PASSWORD`, `IT_EQUIPMENT_REQUEST`, `ONBOARDING_COMPLETED`, recipients per FRD-08's table, except `ONBOARDING_COMPLETED` whose recipients are Employee + Manager (D-21, decided).
- `email_template` row `TASK_ASSIGNED` (Flyway `V6__seed_task_assigned_template.sql`, G-04, 2026-10-16): `to_task_owner=true`; FRD-08 names the template ("Task assigned", trigger "A task is assigned to a user") but gives no exact body wording, so the body is a placeholder — same treatment as `SET_PASSWORD` — editable by HR later (F09).
- `email_template` row `TASK_OVERDUE` (Flyway `V7__overdue_reminder.sql`, G-05, 2026-10-16): `to_task_owner=true`; FRD-08 names the template ("Task overdue", trigger "1 day after the due date") but gives no exact body wording — placeholder body, same treatment as `TASK_ASSIGNED`.
- `setting` rows: `ONBOARDING_OVERDUE` (TIMER, 3 days after the start date, cannot be disabled — D-20 corrected), `PASSWORD_MIN_LENGTH` (12), `SET_PASSWORD_LINK_DAYS` (7), `UNDO_WINDOW_SECONDS` (10).
- `setting` row `OVERDUE_REMINDER_DAYS` (Flyway `V7__overdue_reminder.sql`, G-05, 2026-10-16): TIMER, 1 day after the due date, `recipient_role='TASK_OWNER'`, `can_disable=true` — per FRD-08's Timers & rules table ("Task overdue \| 1 day after due \| Task owner \| Yes"), distinct from `ONBOARDING_OVERDUE` which cannot be disabled.

**Case file:** `Employee` and `Onboarding` items (§8).

## 10. E-mails & timers

| Trigger | Template | To |
|---|---|---|
| Case engine confirms the employee's case has started (2026-10-21, "mail triggered by the case engine" — see below) | Welcome new employee | the new employee's private e-mail |
| same | Set your password | same |
| IT & equipment request sent | IT equipment request | IT admin |
| Milestone "Employee onboarded" | Onboarding completed | Employee + Manager (D-21, decided) |
| HR resends the invite (A34) | Set your password | the employee |
| Any new `checklist_item` or `engine_task` is created (G-04, 2026-10-16) | Task assigned (`TASK_ASSIGNED`) | the task's owner role: HR (`checklist_item.ownerRole=HR` / "Onboarding checklist"), IT admin (`ownerRole=IT_ADMIN` / "Equipment request", "Remaining setup"), the employee themself (`ownerRole=EMPLOYEE`), or their manager (`ownerRole=MANAGER`) — resolved by `com.cubeworks.hr.notification.TaskNotificationService`, also raising one in-app `notification` row per recipient |
| A `checklist_item`/`engine_task` is still open `OVERDUE_REMINDER_DAYS` after its due date (G-05, 2026-10-16) | Task overdue (`TASK_OVERDUE`) | same owner-role resolution as `TASK_ASSIGNED` above, via `com.cubeworks.hr.task.OverdueReminderScheduler`; fires at most once per task (`overdue_reminder_at`) |

**"Mail triggered by the case engine" (2026-10-21, user-directed change — out of FRD scope, logged here per R-SRC-2):** "Welcome new employee" and "Set your password" are no longer sent directly from `POST /api/onboardings`. Both are written/withheld at that point (the "Welcome new employee" `email_outbox` row as `HELD`, not `PENDING`; the Keycloak set-password e-mail not triggered at all yet) so R-ARC-6's atomic-with-the-business-transaction guarantee is unaffected, but neither is actually released for sending until the case engine's own "Release onboarding emails" process task (`employeecase.case`, no entry criterion — active from case start) calls back into `EngineCallbackController` to confirm the employee's case genuinely exists. `OnboardingEmailReleaseService` does the actual release (idempotent — see its class doc); `OnboardingEmailReleaseScheduler` is the fallback if that callback is ever lost (HELD rows older than 10 minutes are released anyway, logged as a WARN). This was a deliberate architecture change, not a bug fix: the FRD's own trigger wording ("Employee record created") is unchanged and still accurate in spirit — the e-mails still go out because the employee record was created, just via the case genuinely starting as the confirming signal, rather than Spring assuming case-start will succeed.

Deploying this also surfaced and fixed an unrelated, pre-existing staleness bug: the engine's actually-deployed `employeecase.xml` bundle had not been regenerated since 2026-10-16, missing the 2026-10-19 "Equipment request" `manualActivationRule` fix (confirmed live: IT could see "Equipment request" from case start regardless of whether HR had sent it) and the `Onboarding.itReadyBy` due-date wiring on "Equipment request"/"Remaining setup". The redeploy that added the new process task also brought the live model back in sync with `src/definitions/employeecase.case`.

| Timer | Default | Guard |
|---|---|---|
| Onboarding overdue | 3 days after the start date, cannot be disabled (D-20, corrected) | `Onboarding.overdueEnabled` |
| Undo window | 10 seconds (G-03 default, `setting.UNDO_WINDOW_SECONDS`) | always on |
| Task overdue reminder (G-05, 2026-10-16) | 1 day after the due date, can be disabled (`setting.OVERDUE_REMINDER_DAYS`) | `setting.OVERDUE_REMINDER_DAYS.enabled` |

## 11. Acceptance criteria

Numbered AC-01…AC-24, taken from FRD-01 §7 and FRD-02 §7, limited to what this feature builds.

```
AC-01 (FRD-02): HR onboards a new developer
  Given HR is on step 1 for Jesse Willems with a Permanent contract
  When HR completes all required fields, chooses the Development template and selects Save & continue
  Then the employee record is created with stage Onboarding
  And the end date field is not required
  And step 2 IT & equipment request opens

AC-02 (FRD-02): IT is notified
  Given step 2 is complete
  When HR selects Send to IT
  Then IT admin receives an "Equipment request" task and an e-mail
  And step 2 shows "Sent to IT"

AC-03 (FRD-02): Onboarding cannot be completed early
  Given 2 mandatory checklist items are still open
  When HR views the checklist
  Then "Complete onboarding" is disabled

AC-04 (FRD-02): Onboarding completed
  Given all mandatory items are ticked
  When HR selects Complete onboarding
  Then the milestone "Employee onboarded" is recorded
  And the employee's stage shows "Active" in the Employees list

AC-05 (FRD-02): Fixed-term without end date
  Given HR selects contract type Fixed-term
  When HR saves without an end date
  Then the message "This field is required." is shown

AC-06 (FRD-02, ONB-09): Duplicate private e-mail
  Given an employee with private e-mail jesse.willems@outlook.com already exists
  When HR tries to create another employee with the same private e-mail
  Then 409 DUPLICATE_PRIVATE_EMAIL is returned with the existing employee's id
  And the message "An employee with this e-mail address already exists." is shown with a link to that employee

AC-07 (ONB-01): Probation end date is calculated
  Given a contract starts 21-10-2026 with 2 months probation
  Then probation ends 21-12-2026, shown read-only

AC-08 (ONB-02 / ONB-03): Checklist items appear in My Tasks
  Given the Development template creates an IT-owned "Laptop" item
  Then the item appears in the IT admin's My Tasks until it is done

AC-09 (ONB-04 / D-15): Ticking and unticking
  Given a mandatory item is open
  When its owner ticks it
  Then it is marked Done immediately and the progress bar updates
  When the same owner unticks it before onboarding is complete
  Then it returns to Open and both actions are in History

AC-10 (ONB-06): Equipment issued becomes an asset
  Given IT records a MacBook Pro 14" with asset number CW-LT-0251
  Then the asset appears in the asset register as Issued
  And the matching "Laptop" checklist item is ticked

AC-11 (ONB-07): Employee sees only their own items
  Given Sanne de Boer's checklist has 4 HR-owned and 4 employee-owned items
  When Sanne opens her dashboard
  Then she sees only the 4 employee-owned items

AC-12 (D-05): Send to IT adds extra items
  Given the Development template does not include "Docking station"
  When HR sends the request with Docking station ticked
  Then a new checklist item "Docking station" (owner IT admin) is created

AC-13 (S9 / confirm equipment): Missing item is flagged
  Given Sanne confirms equipment and notes "Monitor missing"
  When she submits the confirm-equipment form
  Then the item is marked Done
  And IT admin receives a notification about the remark

AC-14 (S8 / policy): Policy must be accepted
  Given the policy checkbox is unticked
  When the employee selects Confirm
  Then the message "Confirm that you have read the policy." is shown

AC-15 (S6 / D-14): Remaining setup needs all mandatory items
  Given 1 mandatory IT item is still open
  When IT admin selects Mark done
  Then the message "Tick all mandatory items first." is shown

AC-16 (WS-03): Employee cannot change their name
  Given Lotte Jansen opens My Profile
  Then First name, Last name, Date of birth and Work e-mail are read-only

AC-17 (FRD-01 WS-01): Role-based menu
  Given Kevin Hendriks has the role IT admin
  When he logs in
  Then the menu shows only Dashboard, My Tasks, Assets and Employees (read-only)

AC-18 (FRD-01): Wrong password
  Given a user enters a wrong password
  Then the message "Invalid e-mail or password." is shown and the user stays on the login screen

AC-19 (FRD-01 G-03): Completing a task updates the lists
  Given HR has an open "Onboarding checklist · Jesse Willems" task
  When HR completes onboarding
  Then, after the undo window, the task is removed from My Tasks and the count decreases by 1

AC-20 (FRD-01 WS-02): Manager cannot see other teams
  Given Jesse Willems reports to Mark Smit
  When Anouk Peters opens My Team
  Then Jesse Willems is not listed

AC-21 (ONB-01 / draft): Draft stays out of the case
  Given HR saves step 1 for a new employee as a draft
  Then no case is started and the Employees list shows the entry with a "Draft" badge
  And no e-mail or task is sent

AC-22 (409 ASSET_NUMBER_IN_USE): Duplicate asset number
  Given asset number CW-LT-0244 is already in use
  When IT admin tries to add another asset with that number
  Then 409 ASSET_NUMBER_IN_USE is returned and the message is shown

AC-23 (R-API-6 undo): Undo a completed task
  Given HR just completed onboarding, inside the undo window
  When HR selects Undo
  Then the pending completion is cancelled and the task reappears

AC-24 (D-04): HR ticks on behalf of another owner
  Given an IT-owned checklist item is open
  When HR ticks it
  Then it is marked Done, done_by records HR, and History shows "done by HR on behalf of IT admin"
```

## 12. Decisions & open items

| # | Topic | Decision / status |
|---|---|---|
| D-01 | Draft wording | No case while in draft; the Employees list shows the badge "Onboarding · Draft". Decided. **Acknowledged deviation (2026-10-16, R-SRC-1):** FRD-02's "Other paths" section says literally "Save as draft... **the case stays in onboarding** and no tasks or e-mails are sent" — implying a case already exists and simply stays in its current stage. This spec's design (D-13) creates no `employee` row and therefore no case at all until "Save & continue" succeeds, so there is no case to "stay" anywhere during a draft. Kept as-is: a draft is unvalidated free text (not even a confirmed `firstName`/`lastName` pair in all cases) with no department/job title/manager/template chosen yet, so there is no well-formed `Employee`/`Onboarding` case-file payload (§8) to start a case with — creating one early would mean starting a case with placeholder/incomplete data and migrating it later, which is more complex than the chosen design and buys nothing the FRD's acceptance criteria (ONB-* / AC-21) actually require. The externally-visible behaviour the FRD cares about — no tasks, no e-mails, during a draft — holds either way. |
| D-02 | Login e-mail | Private e-mail first; HR can switch to the work e-mail later. Decided (user, 2026-10-15). |
| D-04 | Who may tick | Ticking saves immediately; HR may tick any owner's item. Decided (user, 2026-10-15). |
| D-05 | IT request vs template items | Send to IT adds items for selections not already in the checklist. Decided. |
| D-06 | IT option lists | Fixed, seeded lists served by the API (A03), not a settings screen. Decided (user, 2026-10-15). |
| D-07 | Policy text | An uploaded PDF per template item. Decided (user, 2026-10-15). |
| D-08 | Engine tasks vs DB rows | Only HR's and IT's checklist work is an engine task; employee/manager items are DB rows in My Tasks. Decided. |
| D-10 | Manager dropdown | Employees with `app_role = MANAGER`. HR and MANAGER are separate, non-overlapping application roles (a person who manages people is given the MANAGER role; HR is its own role) — so the dropdown is simply "every employee whose `app_role = MANAGER`". Decided (user, 2026-10-15). |
| D-11 | HR profile | Add the Application role field and the "Resend set-password e-mail" button (missing from the wireframe). Decided. |
| D-12 | Template & org seed data | The wireframe's departments, job titles and template item counts are placeholder data, seeded as-is and edited by HR later through settings (F09). Decided (user, 2026-10-15). |
| D-13 | Drafts | Separate `onboarding_draft` table (40 tables total). Decided (user, 2026-10-15). |
| D-14 | Messages not in the FRD | "Tick all mandatory items first." · "Confirm that you have read the policy." Decided (user, 2026-10-15), used as written. |
| D-15 | Untick | Allowed until onboarding is completed; both actions logged. Decided (user, 2026-10-15). |
| D-16 | Keyboard layout | Required in the wireframe, not in the FRD → optional, per R-SRC-1. Decided. |
| D-17 | Asset types in "Add equipment" | All 8 values of `asset.asset_type` are offered (the wireframe's pop-up showed only 5). Decided (user, 2026-10-15). |
| D-18 | New case-file field `Onboarding.itSetupComplete` | Approved. Added to `03-case-file.md` §2 and §4; design finalised in §8 above. Decided (user, 2026-10-15). |
| D-19 | Due date of the HR "Onboarding checklist" task and of "Remaining setup" | The employee's start date. Decided (user, 2026-10-15). |
| D-20 | `setting` value for "Onboarding overdue" | **Corrected (2026-10-16):** 3 days after the start date, cannot be disabled — per FRD-02 §6 ("If onboarding is still not complete 3 days after the start date... HR gets an 'Onboarding overdue' task") and FRD-08's Timers & rules table ("Onboarding overdue \| 3 days after start date \| Task owner \| No"). The original 2026-10-15 decision of "1 day, can disable" was a misread of the FRD and has been superseded — R-SRC-1 (FRD wins) applies. |
| D-21 | Recipients of "Onboarding completed" | Employee + Manager. Decided (user, 2026-10-15). |
| OPEN-01 | Manager's own orientation checklist items (`owner_role = MANAGER`) are not yet surfaced in A36 `GET /api/tasks` | **Resolved (2026-10-16).** `ChecklistItemRepository.findOpenManagerItemsForManager` (open MANAGER-owned items across the manager's direct reports, WS-02) backs a new `MANAGER` branch in `TaskService.getMyTasks`. `components/TaskList` gained an optional `linkTo` prop; `MyTasksPage` passes one that routes a manager's task rows to `/manager/team/{employeeId}/orientation` (S7M) when `user.appRole === 'MANAGER'` — no new DTO field needed, since a manager's own tasks are always orientation items (D-08). Covered by the OPEN-01 test in `F01OnboardingTests`. |
| OPEN-02 | `A36`'s unified task list reuses `engine_task.id` and `checklist_item.id` as a single `taskId` without a type discriminator | Two different tables' surrogate keys share one response field; `POST /api/tasks/{taskId}/undo` (A37) only ever acts on `engine_task`, so a `checklist_item` id that happens to collide with an unrelated `engine_task` id would currently return a confusing 403/409 instead of 404 (gate 2, 2026-10-15). No test currently exercises this edge case; the ownership check prevents any actual cross-row mutation. **[OPEN]** for gate 5 review — consider adding an explicit `entityType` field to `TaskItemDto` and checking it in `undoTask`. |
| D-22 | Due date for the CMMN-raised "Equipment request"/"Remaining setup" tasks (gate 3) | 03-case-file.md's approved `Onboarding` item has no `readyByDate` field, and R-ARC-2/R-ARC-3 keep the case file to IDs/dates/booleans/enums that sentries or routing actually need — the ready-by date drives neither, so it does not belong there. **Decided:** the due date R-CM-3 requires lives entirely on Spring's `engine_task` mirror (already how `OnboardingService.sendItRequest` creates the "Equipment request" row — it passes the HR-entered ready-by date directly as `dueDate`), not on the CMMN task itself. The task watcher that will mirror the engine-raised "Remaining setup" task (not yet built — see §8 "Known follow-up" below) must set that row's `dueDate` the same way, from `onboarding.it_ready_by`, when it observes the engine create it. |
| D-23 | Resuming a saved draft (S1 "Continue") cannot restore previously-entered fields | §6's API table has A08 (`POST` create) and A09 (`PUT` update) for drafts but no `GET /api/onboarding-drafts/{id}`, so the frontend (gate 4, 2026-10-15) has no way to read back a draft's saved `form` JSON. Fixed what's possible without a new endpoint: "Continue" now routes to `/onboarding/draft/:draftId`, carrying the draft id in the URL and the two A08-validated fields (firstName/lastName) via React Router `state` (not a query string, so this personal data doesn't end up in browser history or server logs — gate 5 security review, 2026-10-15), so re-saving correctly `PUT`s the existing draft instead of silently creating a duplicate via `POST`; the rest of the form is blank and the employee re-enters it. **[NEEDS APPROVAL]** to fully fix: add `GET /api/onboarding-drafts/{id}` returning `{firstName, lastName, form}` (the raw JSON already stored in `onboarding_draft.form`), per R-API-4 "a new endpoint means updating the spec first" — not added without that approval. |

All decisions needed for gates 1–3 are now resolved. The only remaining pre-condition for gate 3 is the E1–E5 engine spike result, which is **[PENDING]** and unrelated to these business decisions.

## 13. Definition of done

- [x] Gate 0 Scaffold (`hr-backend`, `hr-frontend`, Keycloak in docker-compose) (2026-10-15)
- [x] Engine spike E1–E5 recorded in `01-overview.md` §5 / `03-case-file.md` §1 (2026-10-15) — E2–E5 hold, E1 doesn't (fallback adopted)
- [x] Gate 1 Database (Flyway baseline + seed, per §9) (2026-10-15) — 17 tables, 62 seed items, 59/59 tests pass
- [x] Gate 2 Backend (2026-10-15) — A01–A38 implemented (A07/A26 built this gate; A06/A20 paths corrected to match the spec table); 153/153 tests pass, AC-01…AC-24 all referenced by name in the test suite; OPEN-01/OPEN-02 logged in §12 as non-blocking follow-ups
- [x] Gate 3 Case model (2026-10-15) — `employeecase.*` built per §8/D-18; live-verified against a running engine (not just "deploys"), 2 real defects found and fixed (autoComplete lifecycle, missing caseFileItemOnPart) — see §8 "Gate 3 status". Real engine-client wiring + task watcher is tracked as a known follow-up, not yet done.
- [x] Gate 4 Frontend (2026-10-15) — S1–S13 + S7M built; `npm run build`/`npm test` (28/28) pass. Fixed post-build: a StrictMode double-init crash in the Keycloak adapter, a broken policy-PDF fetch (A26 returns the file directly, not a JSON reference — the generated code assumed a second, nonexistent endpoint), and the draft "Continue" dead-end (D-23). Real engine wiring (deferred per the gate-3 decision above) means IT's "Remaining setup" still won't appear in the running app yet.
- [ ] **Gate 5 Review (2026-10-15): NOT ready for Done.** Ran all four checkers (springboot-security-reviewer, polyglot-security-reviewer, polyglot-code-reviewer, spec-coverage-analyst) against the whole feature for the first time. They found real, confirmed defects — most already fixed in this same pass (see list below); the rest are recorded as explicit follow-up work rather than silently deferred. Re-run `/spec-coverage` and the two security reviewers after the follow-up items are closed before setting this feature to Done (DoD §2.8.6).

  **Fixed in this gate-5 pass (verified: 153/153 backend tests, `npm run build`/`npm test` 28/28, after every change):**
  - Backend security: a manager could tick/view any employee's manager-owned checklist items, not just their own team's (IDOR, `checkTickAccess`/`getChecklist` had no manager-scoping); G-13 was unenforced across A12/A13/A14/A16/A19/A21–A24/A33/A34 and the HR/IT task lists, letting an HR or IT-admin user who is also an onboarding subject act on their own record — added `security/SelfActionGuard.java` (the "G-13 self-check helper" CLAUDE.md's repo layout already named but nothing had built) and wired it everywhere that applied; e-mail addresses and names were logged at INFO on every outbox send.
  - Backend correctness: `EmployeeProfileDto` (A29/A32) was missing `contractType`/`startDate`/`hoursPerWeek`, which crashed My Profile (S10) for every employee the moment the Employment card tried to render them; `onboarding.status` was never actually set to `COMPLETED`, so the D-15 untick-lock was dead code; the "Onboarding completed" e-mail (D-21) and the "Set your password" resend e-mail (A34) were never enqueued despite being in spec §10 (confirmed MISSED by spec-coverage-analyst); a draft's form was stored as `Map.toString()` Java syntax instead of JSON; `NotificationController` had business logic directly in the controller (R-CODE-1); CORS had `allowCredentials(true)` for a Bearer-only stateless API, which the WS-03/WS-05-style credential isn't needed for.
  - Frontend security: the policy-PDF viewer (S8) loaded whatever `Content-Type` the server claimed into an unvalidated `<iframe>` blob URL — a latent stored-XSS path if a non-PDF "policy" document is ever linked; re-wrapped the blob as `application/pdf` unconditionally so non-PDF content fails to render instead of executing. Keycloak realm/client/URL were hard-coded instead of env-configurable; the app rendered silently instead of showing an error when Keycloak init failed; draft-resume carried personal names in the URL query string (browser history/server logs) instead of router state.
  - Frontend correctness: a StrictMode double-mount crashed `keycloak.init()` on every `npm run dev` load (not caught by any test, since no test exercises the live Keycloak flow).

  **Known, NOT fixed — tracked explicitly, not silently dropped:**
  - **H3 (springboot-security-reviewer, architectural):** authorization reads roles from two places that can disagree — `@PreAuthorize` from the JWT's realm roles, service-layer checks from `employee.app_role` in the database — and A33 (role change) only ever updates the database. A demoted HR user keeps JWT-level HR access until their token expires; a promoted user fails DB-role-based checks until Keycloak catches up. Real fix needs either a Keycloak admin-API sync from A33, or moving all authorization onto one source — both are more than a bounded bug fix and need a decision, not a silent pick (CLAUDE.md "ask when blocked"). **[NEEDS APPROVAL]**
  - **M4 (JWT audience not validated):** documented in `application.yml` rather than fixed blind — adding `audiences: hr-backend` without a matching Keycloak audience mapper on the `hr-frontend` client would reject every real token immediately. Needs a coordinated realm-config change plus a Keycloak restart/re-import, verified together.
  - **M3 residual (backend hardening):** the frontend XSS mitigation above is in place, but `DocumentController` (A07) still accepts any `docType` and trusts the client-supplied `Content-Type` with no file-signature check; `OnboardingController`'s A26 `Content-Disposition` filename isn't escaped. Lower urgency now that the frontend can't be tricked into executing non-PDF content, but still worth closing.
  - **Spec coverage: 84.2% (FAILED, threshold 95%).** Two MISSED items are now fixed above (the two missing e-mails) and should push the score up on a re-run, but a large number of PARTIAL items remain — mostly endpoints with real `@PreAuthorize`/service logic but no integration test asserting the success path (A05, A06, A09, A12 success path, A16 success/role path, A20, A22, A23/A24 IT-success path, A32, A33 HR-success path, A34 HR-success + 409, A35, three of A38's four sub-endpoints), plus EC-12 (D-15 block has no test) and FR-13 (WS-05 audit entries for A33 untested). This is systematic test-writing work, not a design gap — recorded here rather than attempted in the same pass as the security fixes above.
  - **Drift (cosmetic, not a defect):** spec §6 documents A15's query params as `?owner&status` and A38's as `?unread`; the actual, already-consistent frontend+backend implementation uses `ownerFilter`/`statusFilter` and `unreadOnly`. Either rename the code to match the spec or update the spec to match the shipped contract — not done in this pass since nothing is broken, just inconsistently named.
  - A few smaller code-quality items from the reviewers (an unused `AssetRepository`-backed query in `LookupService`/`EmployeeService` loading full tables instead of paging, a couple of remaining `audit_log` JSON string-concatenations elsewhere in `OnboardingService`/`AssetService`, duplicate `displayToIso` date helpers in two frontend files that should import `lib/date.ts`'s `parseDate` instead, a UTC/local-time mismatch in two frontend due-date color calculations) — left as documented follow-up rather than fixed in this pass.
- [x] **S13 exact wording (2026-10-16, corrected):** gate 4's "S1–S13 built" claim above was misleading — no custom Keycloak theme existed, so Keycloak 26's generic default text showed instead of FRD-01 §5's required strings. Fixed: `infra/keycloak/themes/cubeworks/login/` (`parent=keycloak.v2`, confirmed live as the realm's actual active base theme — the initial plan had assumed `keycloak` without checking), `cubeworks-hr-realm.json` (`loginTheme=cubeworks`; `passwordPolicy` changed from `length(12)` to `regexPattern(^(?=.*[A-Za-z])(?=.*[0-9]).{12,}$) and notEmail`, each of the FRD-01 rule's parts — 12 chars, a letter, a digit, not the same as the e-mail — now actually enforced, not just the length); `docker-compose.yml` mounts the theme read-only and the container was recreated (not just restarted — `--import-realm` skips an already-imported realm) to pick up both changes.

  Every overridden message key was checked against the **live** container's own bundled `theme/base/login/messages/messages_en.properties` (extracted from the running `org.keycloak.keycloak-themes-26.0.8.jar`, not recalled from training data) before use, then exercised end-to-end with real HTTP requests against the running realm (login attempts, a disposable test user's forced password-update flow, the forgot-password form) — not just read back from the theme files:
  - Password-policy violation (`regexPattern` too short/no digit) → confirmed renders "Use at least 12 characters, with at least one letter and one number."
  - Password same as e-mail (`notEmail`) → confirmed renders the same message (the FRD gives only one message for this whole field's rule, so both failure paths intentionally share it — there is no separate FRD wording for the e-mail-equality case).
  - Password confirmation mismatch → confirmed renders "The passwords do not match."
  - Forgot-password form, e-mail field left empty → confirmed renders "Enter your e-mail address."
  - Login form, wrong credentials → confirmed renders "Invalid e-mail or password."

  **Found during live verification, not fixed (new, separate finding — not one of the 7 items this plan started from):** Keycloak's built-in login-form authenticator does not distinguish an empty e-mail field, an empty password field, and genuinely wrong credentials — all three render the same `invalidUserMessage`/`invalidPasswordMessage` text on the login form itself (confirmed by submitting both cases live). `missingUsernameMessage`/`missingPasswordMessage` are correctly wired and do fire, just on the forgot-password form's own empty-field case, not on the main login form the FRD-01 table's first two rows describe. Reproducing the FRD's literal 3-way split on the login screen specifically would need a custom Keycloak authenticator (Java SPI) or a hand-maintained `login.ftl` with inline JS — out of scope for a theme-only change, and arguably unnecessary: showing one unified message for all three login-form cases is strictly more WS-04-compliant (it never even hints which field was the problem) than the FRD's literal per-field breakdown would be. **[OPEN]** if the user wants the literal 3-way split built anyway.

  **Also found, not part of S13 itself:** the realm had no `smtpServer` configured at all, so "Forgot password" could not send real e-mail in this dev environment, prior to or independent of this fix. Added `smtpServer` pointing at `host.docker.internal:1025` (the engine stack's Mailcatcher, reached via its host-published port per CLAUDE.md §1.3's "no second mailcatcher") plus `extra_hosts: host.docker.internal:host-gateway` for Linux portability — but `casefabric/getting-started/docker-compose.yml`'s `mailcatcher` service (CLAUDE.md: never edit this file) only publishes port 1080 to the host, not 1025, so the connection still fails (confirmed live: `Connection refused` in the Keycloak container logs). The `emailSentMessage` override itself is correctly configured and its key verified against the live bundle, but the "email actually sent and the confirmation page rendered" end-to-end path could not be click-through verified because of this separate, pre-existing infra gap. **[OPEN]** — needs either the engine's own compose file to publish 1025 (a decision for whoever owns that repo, since CLAUDE.md reserves it) or a different SMTP target.
- [ ] All [OPEN] / [NEEDS APPROVAL] items in §12 resolved — **D-23 and the gate-5 items above are still open**, this line was wrong before this update
