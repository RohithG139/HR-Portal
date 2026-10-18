# FRD-08 — Text wireframes

Source: `.claude/docs/source/FRD-08/WF-08_Reports_and_Settings.html` (v1.2, the dedicated FRD-08 wireframe file — read-only, R-SRC-1 rank 4: the FRD and `spec.md` win on conflict). **Revised 2026-10-16:** this file was originally written against the legacy monolithic `CubeWorks_HR_interactive_wireframes_v6_1.html` (superseded per `.claude/docs/source/README.md`). Every page id, tab id (`set-0`…`set-4`) and modal id (`m-dept-add`, `m-job-add`, `m-add-item`, `m-new-template`, `m-mail-edit`) cited below was re-checked directly against WF-08's own markup during this revision and matches — the dedicated file uses the identical ids the earlier draft already had, so the screen blocks below are otherwise unchanged except where noted (S6 below, whose values are corrected against the resolved D-07 table in `spec.md` §9/§12). Layout rules R-UI-1…R-UI-9 apply to every screen below and are not repeated per screen. Each block says what changed from the raw HTML and why.

---

## S1 · Settings — tab 1: Departments & job titles (HR)

**Wireframe page:** `p-hr-settings`, tab `set-0` · **Route:** `/settings?tab=departments` (FRD-08 v1.2 §3 "Screens")

```
Settings
[Departments & job titles] [Checklist templates] [Appraisal forms] [E-mail templates] [Timers & rules]
┌ Departments ──────────────────────── [+ Add department] ┐  ┌ Job titles ─────────────── [+ Add job title] ┐
│ Department          People   Action │                      │ Job title            Action                   │
│ Development          28       ×     │                      │ Developer             ×                        │
│ Design                8       ×     │                      │ Senior Developer      ×                        │
│ QA                    7       ×     │                      │ Lead Developer        ×                        │
│ Management & HR       5       ×     │                      │ UX Designer           ×                        │
└──────────────────────────────────────┘                      │ QA Engineer           ×                        │
                                                                └──────────────────────────────────────────────┘
```

**Changes from the HTML:** the Job titles table drops the **Grade** and **Department** columns the HTML shows (spec §5 D-01 — `job_title` has no such columns; those are per-employee attributes, not per-title). "People" column on Departments comes from a live count (`COUNT(employee) WHERE department_id = ...`), not a static number.

### Pop-up: "Add department" (modal `m-dept-add`)

```
Add department                                    [×]
Department name*
[Cancel]                                [Add department]
```

### Pop-up: "Add job title" (modal `m-job-add`)

```
Add job title                                     [×]
Job title*
[Cancel]                                [Add job title]
```

**Changes from the HTML:** the HTML's modal also shows Grade* and Department* selects — dropped, same reason as above (D-01).

---

## S2 · Settings — tab 2: Checklist templates (HR)

**Wireframe page:** `p-hr-settings`, tab `set-1` · **Route:** `/settings?tab=checklists` (FRD-08 v1.2 §3 "Screens")

```
Checklist templates                       [+ Add item] [+ New template]
[Development] [Design] [QA] [General] [Standard exit]
┌ Item ──────────────── Section ──────────── Owner ──── Mandatory ── Due ──── Action ┐
│ Contract signed        Documents & admin    HR          ✔          −5 days  Edit × │
│ ID checked             Documents & admin    HR          ✔          −5 days  Edit × │
│ Policies acknowledged  Documents & admin    HR          ✔          −5 days  Edit × │
│ Profile complete       Documents & admin    HR          —          −5 days  Edit × │
│ Laptop (MacBook Pro 14")  Equipment issued  IT admin     ✔          Day 1    Edit × │
│ Monitor                Equipment issued     IT admin     ✔          Day 1    Edit × │
│ ...                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

- One tab per existing template (matches the wireframe exactly — tabs shown for Development/Design/QA/General, all 4 already seeded by F01, plus "Standard exit," an **offboarding** template not yet seeded by any feature — its tab appears only once a template of `kind = 'OFFBOARDING'` exists; empty today).
- "+ Add item" and "Edit" both open the same pop-up (`m-add-item`) — prefilled when editing.
- "+ New template" opens the pop-up below.
- Each template tab also needs an **Archive** button (orange outline, left, R-UI-1) — **not drawn in this wireframe page** (FRD-08 v1.2 §2 "Settings" item 2 says "archive a template," but no archive control is visible in the captured mock); built from the FRD text per R-SRC-1 (FRD wins over an incomplete wireframe). Placed per R-UI-1's layout rule: orange outline, bottom-left of the per-template tab panel.

### Pop-up: "+ Add item" / "Edit" (modal `m-add-item`)

```
Checklist item                                    [×]
Item*
Section* [Documents & admin ▾]   Owner* [HR ▾]
Due (days from start)*           Mandatory* [Yes ▾]
[Cancel]                                        [Save]
```

Section options shown depend on the template's `kind`: onboarding sections (Documents & admin / Equipment issued / Accounts & installations / Orientation) for an `ONBOARDING` template; offboarding sections (Return assets / Remove access / HR handover / Reassign team) for an `OFFBOARDING` template — matches `checklist_template_item.section`'s CHECK.

### Pop-up: "+ New template" (modal `m-new-template`)

```
New checklist template                            [×]
Template name*
Type* [Onboarding ▾]
Copy items from [Empty template ▾]
Used for job title [— optional, any job title ▾]
[Cancel]                                [Create template]
```

**Change from the HTML:** "Used for job title" is optional (`checklist_template.job_title_id` is nullable — matches F01's 4 seeded templates, all `job_title_id = NULL`, "apply to all titles").

---

## S3 · Settings — tab 3: Appraisal forms, list (HR)

**Wireframe page:** `p-hr-settings`, tab `set-2` · **Route:** `/settings?tab=forms` (FRD-08 v1.2 §3 "Screens")

```
Appraisal forms                                        [+ New form]
┌ Form ────────────────────── Used for ───────────── Sections ── Last edited ── Status ── Action ┐
│ Standard appraisal form      All employees            5          02 Sep 2026   Active    Open   │
│ Leadership appraisal form    Tech leads & managers     4          02 Sep 2026   Active    Open   │
│ Probation review form        Probation reviews         3          15 Jan 2026   Active    Open   │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Row click or "Open" goes to S4 (the form editor) for that form; FRD-08 v1.2's own §3 "Screens" table names this route `/settings/forms/:formId`. "+ New form" also goes to S4, at `/settings/forms/new`, with an empty form named by HR (FRD-08 v1.2 §2: '"+ New form" opens an empty form for HR to name').

---

## S4 · Appraisal form editor (HR)

**Wireframe pages:** `p-hr-form-standard`, `p-hr-form-leadership`, `p-hr-form-probation`, `p-hr-form-new` · **Route:** `/settings/forms/:formId` (existing form), `/settings/forms/new` ("+ New form") (FRD-08 v1.2 §3 "Screens")

```
← Back to Settings
{Form name}                                  Used for {used-for label}
┌ Form details ───────────────────────────────────────────────┐
│ Form name*            Used for* [All employees ▾]            │
│ Status* [Active ▾]     Overall score* [Weighted average ▾]   │
└────────────────────────────────────────────────────────────┘
┌ Rating scale ──────────────────────────────────────────────┐
│ Score  Label                     Description                 │
│ 5      [Outstanding        ]     [Consistently exceeds...  ]  │
│ 4      [Exceeds expectations]    [Often goes beyond...      ] │
│ 3      [Meets expectations ]     [Fully meets the...        ] │
│ 2      [Partly meets       ]     [Meets some requirements...] │
│ 1      [Below expectations ]     [Does not meet the...      ] │
└────────────────────────────────────────────────────────────┘
┌ {Section name, editable}  ──────────── [+ Add question] [Remove section] ┐
│ Completed by: [Manager (employee comments) ▾]                             │
│ [Question text        ] [Description          ] [1–5 scale ▾]  ×          │
│ [Question text        ] [Description          ] [1–5 scale ▾]  ×          │
└────────────────────────────────────────────────────────────────────────┘
(repeat per section)
[+ Add section]
[Cancel]                                                    [Save form]
```

- "Completed by" options (from the wireframe): "Manager (employee comments)" / "Self · Peers · Manager" / "Manager" / "Manager · HR" — these are convenience presets over the real per-checkbox model (`form_section.by_self/by_peer/by_manager/by_hr`); the editor may show either the preset dropdown or the 4 checkboxes directly — **`[ASSUMPTION]`**: build as 4 checkboxes (Self/Peer/Manager/HR) matching the data model 1:1, since a closed preset list can't express every combination the schema allows, and the wireframe's own "Appraisal form" FRD description says "choose who completes each section" without naming a fixed preset list.
- Rating scale is always exactly 5 rows (score 1–5 fixed by the FRD and by `label_1`…`label_5`); only Label and Description are editable per row.
- "Remove section" / "×" on a question: both red outline per R-UI-1 (question's "×" is the small-removal pattern; section removal is a full button since it's a destructive, multi-row action).
- Cancel: red outline, left. Save form: green, right (R-UI-1).

---

## S5 · Settings — tab 4: E-mail templates (HR)

**Wireframe page:** `p-hr-settings`, tab `set-3` · **Route:** `/settings?tab=emails` (FRD-08 v1.2 §3 "Screens")

```
E-mail templates                                              🔍 search
┌ Template ──────────────── Sent when ─────────────────────── Recipients ──────── Active ── Action ┐
│ Task assigned              A task is assigned to a user       Task owner          ●on      Edit    │
│ Task overdue               1 day after the due date           Task owner          ●on      Edit    │
│ Welcome new employee       Employee record created            New employee        ●on      Edit    │
│ Set your password          Employee record created, or HR resends ... New employee ●on     Edit    │
│ Reset password              A user selects Forgot password     User                ●on      Edit    │
│ IT equipment request       HR sends the IT & equipment request IT admin           ●on      Edit    │
│ Onboarding completed       Milestone Employee onboarded        Employee, Manager  ●on      Edit    │
│ Probation review due       14 days before probation ends       Manager            ●on      Edit    │
│ Contract ending            60 days before a fixed-term contract ends HR, Manager  ●on      Edit    │
│ Appraisal round opened     HR starts an appraisal round        All included employees ●on   Edit   │
│ Peer review request        Peer nominations approved           Peer               ●on      Edit    │
│ Appraisal finalised        HR finalises an appraisal           Employee           ●on      Edit    │
│ Development plan submitted Employee submits a plan             Manager            ●on      Edit    │
│ Development plan decision  Manager approves or requests changes Employee          ●on      Edit    │
│ Project assigned           Employee assigned to a project      Employee           ●on      Edit    │
│ PIP notice                 HR approves a PIP                   Employee, Manager, HR ●on   Edit    │
│ PIP disputed                An employee disputes a PIP (to HR and the manager) HR, Manager ●on Edit│
│ PIP outcome                 Final PIP review confirmed         Employee, Manager  ●on      Edit    │
│ Resignation received       Employee submits resignation        HR, Manager        ●on      Edit    │
│ Offboarding started        Offboarding stage starts            Employee, Manager, IT admin ●on Edit│
│ Asset return reminder      3 days before the last working day  Employee           ●on      Edit    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Change from the HTML:** the captured wireframe page draws only 18 of these 21 rows (it is missing "Set your password," "Reset password," and "PIP disputed"). R-SRC-1: the FRD's own §3 table names all 21 by name — all 21 are built (D-04's "no create/delete, but all 21 exist" decision governs this). No "+ New template" button (D-04).

### Pop-up: E-mail template editor (modal `m-mail-edit`)

```
{Template name}                                   [×]
Template name*                 Status* [Active ▾]
Sent when*
Send to*  [ ]Employee [ ]Manager [ ]HR [ ]IT admin [ ]Task owner [ ]Peer
          Roles are resolved per employee when the e-mail is sent —
          e.g. "Manager" becomes the employee's current manager.
CC (optional e-mail addresses)
Subject*
Body* (large text area)
Insert placeholder: {employee_name} {employee_first_name} {manager_name}
                     {due_date} {task_name} {task_link} {start_date} {last_day}
[Cancel] [Send test e-mail]                          [Save template]
```

Matches the HTML exactly. "Send test e-mail" stays enabled even on an unsaved draft (sends the in-progress values — spec §12 D-03); it does not close the modal (no `data-close` on that button in the source, consistent with staying in the editor to keep adjusting).

---

## S6 · Settings — tab 5: Timers & rules (HR)

**Wireframe page:** `p-hr-settings`, tab `set-4` · **Route:** `/settings?tab=timers` (FRD-08 v1.2 §3 "Screens")

```
Timers & rules
┌ Rule ───────────────────────── Module ─── Value ──────────────── Goes to ────── Active ┐
│ Bench check                     Work cycle  7  days                Manager        Required │
│ Development plan progress check Dev plan    14 days                Manager        Required │
│ PIP check-in                    PIP         14 days                Manager        Required │
│ PIP duration                    PIP         90 days                Manager + HR   Required │
│ PIP extension (max 1×)          PIP         30 days                Manager + HR   ●on       │
│ Probation review                Onboarding  14 days before end     Manager        Required │
│ Contract renewal reminder       Employment  60 days before end     HR             ●on       │
│ Overdue reminder e-mail         All         1 day after due        Task owner     ●on       │
│ Asset return reminder           Offboarding 3 days before last day Employee       ●on       │
│ Onboarding overdue              Onboarding  3 days after start date HR            ●on       │
│ Undo window after completing a task          10 seconds             Task owner    Required │
│ Set-password link valid for                  7 days                 New user      Required │
│ Reset-password link valid for                60 minutes             User          Required │
│ Password minimum length                      12 characters          All users     Required │
│ KPI cycle                                    [Quarterly ▾] (Half-yearly / Yearly)  Manager   Required │
│ Notice period – permanent contract           30 days                 HR            Required │
│ Notice period – fixed-term contract          30 days                 HR            Required │
└──────────────────────────────────────────────────────────────────────────────────────────┘
[Cancel]                                                                      [Save]
```

**Changes from the HTML:** the captured wireframe page draws only the first 9 rows; the remaining 8 (Onboarding overdue through Notice period – fixed-term) are named in FRD-08 v1.2 §2's own prose ("This tab also holds the KPI cycle, notice periods per contract type, password rules, link validity and the undo window") and in its clean "Timers & rules" table, so they are added here per R-SRC-1. "Rule," "Module" and the row's identity (`code`) are always read-only text (ADM-01) — only "Value," "Goes to" (where the row has a recipient) and the on/off switch (only on rows marked "can disable," not "Required") are editable.

**Revised 2026-10-16 (D-07 resolved, spec.md §9/§12):** every "Goes to"/"Active" value above now matches FRD-08 v1.2's clean, unwrapped "Timers & rules" table directly — no row is reconstructed or `[ASSUMPTION]`-flagged any longer. Four values changed from the pre-v1.2 draft: **Probation review** is now `Required` (cannot be switched off — was shown as `●on` before); **Contract renewal reminder**'s "Goes to" is now `HR` only (was `Manager + HR`); **KPI cycle**'s "Goes to" is now `Manager` (was `All users`); **Notice period – permanent/fixed-term contract** now both show `HR` in "Goes to" (was `—`, on the earlier assumption these had no recipient). One value is flagged as a known, separate drift against the real seeded data, not fixed on this page: **Onboarding overdue** is drawn above as `●on` (switchable) per the clean v1.2 table, but the already-applied `V2__seed_settings.sql` seeded it non-switchable (`can_disable = false`) — see spec.md §12 D-09; until a new migration resolves that drift, the live screen will actually render this row as `Required`, not `●on`, which is a known, tracked mismatch between this wireframe and the current database state, not an error in this page. **Password minimum length**'s "Goes to" is filled in as `All users` per the clean table (was `—`), though the real seeded row has `recipient_role = NULL` — also noted in D-09, lower priority. One **Save** commits every changed row in a single request per row (C30 is now a per-`code` `PUT /api/settings/timers/{code}`, not a single bulk endpoint — spec.md §6); the single **Save** button fires one request per changed row when pressed. Cancel discards all unsaved edits in the tab (R-UI-4).

---

## S7 · Reports overview (HR)

**Wireframe page:** `p-hr-reports` · **Route:** `/reports` (FRD-08 v1.2 §3 "Screens", Screen 1)

```
Reports                                                           [⬇ export all]
┌ Headcount: 48 (+2 vs last month) ┐ ┌ Bench: 4 (8% of billable staff) ┐
┌ Appraisal completion: 62% (Round 2026) ┐ ┌ Unreturned assets: 2 (1 ex-employee) ┐
┌ Headcount ─────────┐ ┌ Bench ─────────────┐ ┌ Project allocation ───┐ ┌ Appraisal status ──┐
│ By department,      │ │ Employees without   │ │ Allocation % per       │ │ Completion, client  │
│ joiners & leavers   │ │ an active project   │ │ employee               │ │ feedback & outcomes │
│ Open report →       │ │ Open report →       │ │ Open report →          │ │ Open report →       │
└─────────────────────┘ └─────────────────────┘ └────────────────────────┘ └─────────────────────┘
┌ PIPs ──────────────┐ ┌ Onboarding ────────┐ ┌ Contracts & probation ┐ ┌ Unreturned assets ──┐
│ ...                 │ │ ...                 │ │ ...                    │ │ ...                 │
└─────────────────────┘ └─────────────────────┘ └────────────────────────┘ └─────────────────────┘
```

Each KPI tile and report card is clickable and routes to its own report screen (S8–S15).

---

## S8 · Report — Headcount (HR)

**Wireframe page:** `p-hr-rep-headcount` · **Route:** `/reports/headcount` (FRD-08 v1.2 §3 "Screens", Screen 2)

```
Headcount                                                              [⬇]
┌ Total: 48 ┐ ┌ Joiners 2026: 7 ┐ ┌ Leavers 2026: 3 ┐ ┌ Attrition: 6.3% (rolling 12 months) ┐
Department [All ▾]
┌ Department ── Employees ── Joiners 2026 ── Leavers 2026 ── Permanent ── Fixed-term ┐
│ Development     28             4               1              22           6       │
│ ...                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## S9 · Report — Bench (HR)

**Wireframe page:** `p-hr-rep-bench` · **Route:** `/reports/bench` (FRD-08 v1.2 §3 "Screens", Screen 3)

```
Bench                                                                  [⬇]
Manager [All ▾]                                            🔍 search
┌ Employee ──────── Job title ──────── Manager ──────── On bench since ── Days ── Last project ┐
│ Kees Willems       Developer           Anouk Peters      24 Sep 2026       18     Atlas CRM Migration │
│ Noor Bakker        ...                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Row click opens the employee record (S11 of F01, Overview tab).

---

## S10 · Report — Project allocation (HR)

**Wireframe page:** `p-hr-rep-alloc` · **Route:** `/reports/allocation` (FRD-08 v1.2 §3 "Screens"; `reportId` = `allocation` per §4.1 — corrected from a pre-v1.2 guess of `/reports/project-allocation`, see spec.md §4/§6).

```
Project allocation                                                     [⬇]
Status [All ▾]                                              🔍 search
┌ Employee ────────── Projects ───────────── Allocation ──────── Status ┐
│ Sanne de Boer         GreenLeaf Webshop      ▓▓▓▓▓▓░░░░ 60%     Partly available · 40% │
│ Lotte Jansen          ...                                                               │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

Status filter options: Fully allocated / Partly available / On bench / Ending.

---

## S11 · Report — Appraisal status (HR)

**Wireframe page:** `p-hr-rep-apr` · **Route:** `/reports/appraisal-status` (FRD-08 v1.2 §3 "Screens", Screen 5)

```
Appraisal status                                                       [⬇]
┌ Completion: 62% ┐ ┌ Finalised: 9 / 44 ┐ ┌ Promotions: 2 ┐ ┌ Avg client score: 4.6 / 5 ┐
Manager [All ▾]  Result [All ▾]                              🔍 search
┌ Employee ── Manager ── Self ── Peers ── Manager review ── Client feedback (avg · reviews) ── Result ┐
│ Ruben Bakker  Mark Smit  Done   3 / 3     Done              4.8 avg · 2 reviews              To finalise │
│ Priya Sharma  Mark Smit  Done   ...                                                                       │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Result filter options: In progress / To finalise / Promoted. Row click opens the appraisal finalisation screen (F06, out of scope here).

---

## S12 · Report — PIPs (HR)

**Wireframe page:** `p-hr-rep-pip` · **Route:** `/reports/pips` (FRD-08 v1.2 §3 "Screens", Screen 6)

```
PIPs                                                                   [⬇]
Status [All ▾]
┌ Employee ── Manager ── Status ── Started ── Final review ── Days left ── Extensions ┐
│ Daan Visser  Mark Smit  Active    20 Jul 2026  18 Oct 2026    6            0 / 1     │
│ ...                                                                                   │
└────────────────────────────────────────────────────────────────────────────────────┘
```

Status filter options: Active / Awaiting. Row click opens the employee record.

---

## S13 · Report — Onboarding (HR)

**Wireframe page:** `p-hr-rep-onb` · **Route:** `/reports/onboarding` (FRD-08 v1.2 §3 "Screens", Screen 7)

```
Onboarding                                                             [⬇]
┌ Employee ──────── Start date ── Template ──── Checklist ──────────── IT request ── Open items ┐
│ Sanne de Boer       05 Oct 2026   Design         ▓▓▓▓▓▓▓▓░░ 82%        Completed       3        │
│ Jesse Willems       21 Oct 2026   Development     ▓░░░░░░░░░ 9%        ...             ...       │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Row click opens that employee's onboarding wizard (F01 S2/S3/S4).

---

## S14 · Report — Contracts & probation (HR)

**Wireframe page:** `p-hr-rep-dates` · **Route:** `/reports/contracts-probation` (FRD-08 v1.2 §3 "Screens", Screen 8)

```
Contracts & probation                                                  [⬇]
Type [All ▾]
┌ Employee ── Type ──────────── Date ──────── Days ── Owner ────── Status ┐
│ Daan Visser  Final PIP review   18 Oct 2026    6      Mark Smit    Scheduled │
│ Sanne de Boer ...                                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

Type filter options: Probation / Contract / PIP — covers probation ends, contract ends and final PIP reviews in the next 90 days (FRD-08 v1.2 §2 "Reports" table, "Contracts & probation" row), i.e. reads `v_upcoming_dates`. Route: `/reports/contracts-probation` (FRD-08 v1.2 §3 "Screens").

---

## S15 · Report — Unreturned assets (HR)

**Wireframe page:** `p-hr-rep-assets` · **Route:** `/reports/unreturned-assets` (FRD-08 v1.2 §3 "Screens", Screen 9)

```
Unreturned assets                                                      [⬇]
Status [All ▾]
┌ Asset no. ── Asset ──────────────── Employee ──────── Last working day ── Status ┐
│ CW-PH-0061    Phone · Pixel 7         Kim Verhoeven      30 Jun 2026         Not returned │
│ CW-LT-0102    Laptop · MacBook Pro 16" Thomas Mulder      24 Oct 2026         Due 24 Oct   │
│ CW-PH-0087    Phone · iPhone 13        Thomas Mulder      24 Oct 2026         Due 24 Oct   │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

Status filter options: All / Not returned / Due. Matches the HTML exactly.
