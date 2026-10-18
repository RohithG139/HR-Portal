# F01 — Text wireframes

Source: `docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html` (read-only, R-SRC-1 rank 4 — the FRD and `spec.md` win on conflict). Layout rules R-UI-1…R-UI-9 apply to every screen below and are not repeated per screen. Each block says what changed from the raw HTML and why.

---

## S1 · Employees list (HR)

**Wireframe page:** `p-hr-employees`

```
[+ New Employee]                                              (top right)
┌ Table toolbar ───────────────────────────────────────────────┐
│ Stage [All▾]  Department [All▾]  Manager [All▾]   🔍 search  [⬇] │
└────────────────────────────────────────────────────────────────┘
┌ ID │ Employee (avatar+name+e-mail) │ Job title │ Department │ Manager │ Stage │ Action ┐
│ …  │ …                              │ …         │ …          │ …       │ badge │ View   │
└──────────────────────────────────────────────────────────────────────────────────────┘
[pagination]
```

**Changes from the HTML:**
- Rows for `onboarding_draft` entries are added at the top: `ID` blank, `Employee` = first + last name only, `Job title`/`Department`/`Manager` = "—", `Stage` = badge "Onboarding · Draft" (D-01), `Action` = "Continue" (opens S2 pre-filled from the draft) instead of "View".
- Sortable, filterable, searchable, exportable per R-UI-2 — already matches the HTML (`sortable` columns, `.search-in`, the export icon).

---

## S2 / S3 / S4 · Onboarding wizard (HR)

One page, three step tabs, built once and reused for a new employee (`p-hr-onb-new`) and for re-opening an in-progress one (`p-hr-onb-jesse`, `p-hr-onb-sanne`). Steps can be opened in any order.

```
← Back to Employees
{Employee name or "New employee"}                 Onboarding
[1 · Employee details] → [2 · IT & equipment request] → [3 · Checklist]
```

### Step 1 — Employee details (`onbnew-0` / `onbjesse-0`)

```
┌ Personal details ───────────────────────────────┐
│ First name*      Last name*                      │
│ Private e-mail*  Mobile phone*                    │
│ Date of birth* (dd-mm-yyyy)   Nationality          │
│ Street + number* (full width)                      │
│ Postcode*        City*                             │
└────────────────────────────────────────────────┘
┌ Contract ─────────────────────────────────────────┐
│ Contract type* [Permanent▾]  Start date*            │
│ End date* (hidden/disabled when Permanent)          │
│ Hours per week* (40)  Probation period* [2 months▾] │
│ Probation ends (read-only, computed)                │
└────────────────────────────────────────────────┘
┌ Verification ─────────────────────────────────────┐
│ [ ] ID document checked in person*                  │
│ [ ] Signed contract received*                       │
│ [ ] Right-to-work verified                          │
│ [Drag & drop signed contract, or browse file]       │
└────────────────────────────────────────────────┘
┌ Role & reporting ──────────┐ ┌ Template preview ──────┐
│ Job title*   Department*    │ │ "18 checklist items     │
│ Manager*     Grade*         │ │  will be created"       │
│ Application role*  Buddy    │ │ Documents & admin  (4)  │
│ Onboarding template*        │ │ Equipment issued   (3)  │
└─────────────────────────────┘ │ Accounts & install.(6)  │
                                 │ Orientation        (5)  │
                                 └─────────────────────────┘
[Cancel]                                [Save draft] [Save & continue]
```

- Cancel: red outline, left (R-UI-1). Save draft / Save & continue: green, right.
- The template preview panel re-renders from `GET /api/checklist-templates/{id}/preview` (A06) whenever the template dropdown changes; it does **not** read the 4 hard-coded preview blocks in the HTML (those were mock data for the prototype only).
- Grade options in the HTML show only Medior/Junior/Senior; the data model has 5 values (`JUNIOR,MEDIOR,SENIOR,SENIOR_I,SENIOR_II`) — all 5 are offered, per `02-data-model.md` (R-SRC-2: do not narrow what the schema allows without a decision; none exists to narrow it).

### Step 2 — IT & equipment request (`onbnew-1` / `onbjesse-1`)

```
┌ Laptop & hardware ─────────────── [badge: Not sent yet / Sent to IT · {date} · in progress / Completed by IT · {date}] ┐
│ Laptop*            Ready by*                        │
│ Delivery*          Keyboard layout (optional, D-16) │
│ Accessories: [ ]Monitor [ ]Keyboard&mouse [ ]Headset [ ]Docking station [ ]Phone [ ]Access badge │
└────────────────────────────────────────────────┘
┌ Accounts & software ───────────────────────────────┐
│ [ ]Microsoft 365  [ ]Slack  [ ]VPN  [ ]Jira/Confluence │
│ [ ]GitHub  [ ]Figma  [ ]AWS console  [ ]Docker Desktop │
│ Notes for IT (textarea)                              │
└────────────────────────────────────────────────┘
[Cancel]                          [Back]  [Send to IT  /  Save & continue once sent]
```

- Button label is "Send to IT" before `A14` has succeeded, and "Save & continue" after (the HTML's two states, kept).

### Step 3 — Checklist (`onbnew-2` / `onbjesse-2`)

```
┌ Progress ────────────────────────────── [+ Add item] ┐
│ x of y items · a of b mandatory                        │
│ ▓▓▓▓▓▓▓▓░░░░░░░░ 45%                                   │
└────────────────────────────────────────────────┘
┌ Documents & admin ──────────────────────── HR ┐
│ [x] Contract signed *      HR   09 Oct  Done   │
│ [ ] ID checked *           HR   09 Oct  Open   │
│ ...                                             │
└────────────────────────────────────────────────┘
(repeat for Equipment issued / Accounts & installations / Orientation)
[Cancel]                                    [Complete onboarding]
```

- **The HTML's "Save" button next to "Complete onboarding" is dropped.** ONB-04 says ticking saves immediately, so there is nothing left to Save; keeping a dead button would violate R-CODE-4 (no dead UI).
- "Complete onboarding" is disabled (R-UI-1 styling: `btn[disabled]`) until every mandatory item is Done (ONB-05).
- "+ Add item" opens the pop-up below.

### Pop-up: "+ Add item" (modal `m-add-item`)

```
Checklist item                                    [×]
Item*
Section* [Documents & admin ▾]   Owner* [HR ▾]
Due (days from start)*           Mandatory* [Yes ▾]
[Cancel]                                        [Save]
```

---

## S5 · Equipment request (IT admin)

**Wireframe page:** `p-it-provision`

```
← Back to My Tasks
Equipment request · {name}          {job title} · starts {date} · ready by {date}
┌ Equipment issued ──────────────────── [+ Add equipment] ┐
│ Type  Model  Asset no.  Serial  Issued   ×               │
└────────────────────────────────────────────────┘
┌ Accounts & installations ──────────────────────┐
│ [ ]Microsoft 365  [ ]Slack  [ ]VPN  [ ]Jira/Confluence │
│ [ ]GitHub  [ ]Docker Desktop  [ ]Monitor  [ ]Docking station [ ]Access badge │
└────────────────────────────────────────────────┘
┌ Requested by HR (read-only) ───────────────────┐
│ Requested by, laptop, accessories, accounts,    │
│ delivery, notes                                  │
└────────────────────────────────────────────────┘
[Cancel]                       [Save progress] [Mark setup done]
```

### Pop-up: "+ Add equipment" (modal `m-add-asset`)

```
Add equipment                                     [×]
Type* [Laptop ▾]      Model*
Asset no.* (prefilled "CW-")   Serial number
Issued to*             Issue date*
[Cancel]                                        [Save]
```

---

## S6 · Remaining setup (IT admin)

**Wireframe page:** `p-it-sanne`

```
← Back to My Tasks
Remaining setup · {name}                 {job title} · started {date}
┌ Accounts & installations ──────────────────────┐
│ [x] Microsoft 365   IT admin   02 Oct   Done     │
│ [ ] VPN *           IT admin   13 Oct   Open     │
│ [ ] Jira/Confluence * IT admin 13 Oct   Open     │
└────────────────────────────────────────────────┘
[Cancel]                                      [Mark done]
```

"Mark done" is blocked with "Tick all mandatory items first." (D-14) while a mandatory item is open.

---

## S7 · New-joiner dashboard (Employee)

**Wireframe page:** `p-sb-dash`

```
Welcome to CubeWorks, {first name}!          Week {n} · started {date}
┌ Your onboarding tasks ─────────────────────────┐
│ [x] Acknowledge employee handbook *             │
│ [x] Acknowledge IT & GDPR policy *              │
│ [ ] Complete profile & emergency contact * → Open profile │
│ [ ] Confirm equipment received * → Confirm      │
└────────────────────────────────────────────────┘
┌ Your team ──────────┐  ┌ My equipment ──────────┐
│ Manager, Buddy,       │  │ Item | Asset no. | Issued │
│ Project, Probation ends│  └─────────────────────────┘
└──────────────────────┘
```

- Only the employee's own items are shown (ONB-07) — matches the HTML.
- "Acknowledge IT & GDPR policy" opens **S8** (built fresh, not in the HTML — see below), not a plain checkbox; the HTML ticks it inline, which skips the FRD's required policy pop-up (FRD-02 §4). Spec wins (R-SRC-1).

---

## S7M · Orientation items (Manager)

Not a separate page in the HTML. It reuses the S4 checklist component, opened from the manager's "Onboarding checklist" row in My Tasks, filtered to `owner_role = 'MANAGER'` items for that employee (e.g. "Team introduction", "Design system intro" from the wireframe's Sanne example). Same tick/untick behaviour as S4.

---

## S8 · Policy pop-up (Employee)

**Not drawn in the wireframe** (`FRD-02 §4` only). Built as:

```
{Policy title}                                     [×]
┌ Policy text (scrollable) ───────────────────────┐
│ ...                                               │
└──────────────────────────────────────────────────┘
[ ] I have read and accept this policy
[Cancel]                                      [Confirm]
```

Confirm is disabled until the box is ticked; unticked + Confirm shows "Confirm that you have read the policy." (D-14). The policy text is the PDF at `checklist_template_item.policy_document_id` (D-07), shown inline or as a download link — `[ASSUMPTION]`: inline PDF viewer if the browser supports it, otherwise a "View policy (PDF)" link, since the FRD does not specify the rendering.

---

## S9 · Confirm equipment pop-up (Employee)

**Wireframe page:** modal `m-equip`

```
Confirm equipment received                         [×]
┌ Item ──────────────── Asset no. ── Received ┐
│ Laptop · MacBook Pro 14" CW-LT-0244   [x]    │
│ Monitor · Dell 27"       CW-MN-0131   [x]    │
└──────────────────────────────────────────────┘
Anything missing or damaged?
[Cancel]                                     [Confirm]
```

Matches the HTML as-is.

---

## S10 · My Profile (Employee)

**Wireframe page:** `p-sb-profile`

```
My Profile                                    {job title} · {department}
[Submit resignation]  ← out of scope for F01, not built (F07)
┌ Personal details ───────────────────────────────┐
│ First name (read-only)   Last name (read-only)   │
│ Work e-mail (read-only)  Private e-mail            │
│ Mobile phone*            Date of birth (read-only) │
│ Street + number*                                    │
│ Postcode*                City*                      │
└────────────────────────────────────────────────┘
┌ Emergency contact ──────────────────────────────┐
│ Name*            Relationship                    │
│ Phone*                                            │
└────────────────────────────────────────────────┘
[Cancel]                                      [Save]
```

**Changes from the HTML:** First name, Last name, Date of birth and Work e-mail are **read-only** (WS-03 overrides the HTML, which wrongly shows them as editable inputs). The "Submit resignation" button is **not built** in F01 (F07's "Initiate exit" flow); it is omitted from this screen rather than shown disabled, since F01 does not own that feature's data.

---

## S11 · Employee record — Overview / Profile / History (HR, Manager)

**Wireframe pages:** `p-hr-case-jesse` (HR), `p-mg-case-jesse` (Manager)

```
← Back to Employees / My Team
{Avatar} {Name}                 {code} · {job title} · {dept} · Manager: {name}
[Overview] [Profile] [History]        ← only these 3 tabs for F01 (Projects, Development & reviews excluded, §1 scope)
```

### Overview tab

```
Stage [badge]  Since {date}  | ✔/○ milestone chips          ← HR only (the stage line)
┌ Work cycle ──┐ ┌ Development ──┐ ┌ Appraisal ──┐
│ Not started   │ │ Not started    │ │ Not eligible │   ← fixed placeholder text; F03/F04/F05 fill these in later
└───────────────┘ └────────────────┘ └──────────────┘
┌ Open tasks ─────────────────────────────────────┐
│ task rows for this employee (from engine_task +   │
│ checklist_item, same shape as S12)                 │
└────────────────────────────────────────────────┘
```

- The manager's view has no stage line (FRD-01 "Manager: no stage line").
- The three cards always show their F01 placeholder text ("Not started" / "Not eligible") since the underlying tables do not exist until F03/F04/F05. They are not clickable yet.
- **Not built in F01:** the `Initiate exit` and `Transfer manager` header buttons (HR), `Start PIP` / `Assign project` (Manager) — all belong to later features.

### Profile tab

```
┌ Personal details (HR: editable; Manager: read-only) ┐
│ ... same fields as the employee record in the data model
└────────────────────────────────────────────────┘
┌ Employment ──────────────────────────────────────┐
│ Employee ID (read-only)  Job title*               │
│ Department*              Manager (read-only)       │
│ Contract (read-only summary)  Start date (read-only) │
│ Application role*  ← ADDED, missing from the wireframe (D-11) │
└────────────────────────────────────────────────┘
┌ Emergency contact ┐  ┌ Assets (table) ┐  ┌ Documents (table) ┐
└───────────────────┘  └────────────────┘  └────────────────────┘
[(Re)send set-password e-mail]  ← ADDED, shown until first login (D-11)
[Cancel]                                   [Save changes]  (HR only)
```

### History tab (HR only)

```
Timeline: milestone / task-created / field-changed entries, each
with who and when (from audit_log, newest first)
```

Matches the HTML's `.tl` timeline pattern.

---

## S12 · My Tasks + notifications (all roles)

**Wireframe pages:** `p-hr-tasks`, `p-mg-tasks`, `p-sb-tasks`, `p-it-tasks`; the bell/avatar dropdowns in the top bar on every page.

```
My Tasks                                     {n} open tasks
┌ Module [All▾]  (Raised by [All▾] — HR/Manager/IT only)   🔍 search ┐
┌ Module │ Task │ Employee (HR/Mgr/IT only) │ (Raised by) │ Due │ Action ┐
└──────────────────────────────────────────────────────────────────────┘
```

- `task-due soon` → orange (due within 3 days, G-05); `task-due overdue` → red.
- A completed row disappears after the undo window (G-03), matching the HTML's `markDone()` behaviour.

**Notifications (bell dropdown):**

```
Notifications                              Mark all read
🔔 {title}                                  {time ago}
```

**User menu (avatar dropdown):**

```
{Name}
{e-mail}
My profile
Log out
```

**Top bar / sidebar, scoped to F01:** the role pill, bell and avatar match the HTML. The sidebar shows only the nav items this feature (or an earlier one) has built:

| Role | Nav items shown in F01 |
|---|---|
| HR | Dashboard (stub), My Tasks, Employees |
| Manager | Dashboard (stub), My Tasks, My Team |
| Employee | Dashboard, My Tasks, My Profile |
| IT admin | Dashboard (stub), My Tasks, Assets, Employees (read-only) |

Appraisals, Reports, Settings, Projects, My Projects and the dashboard's KPI cards from the HTML are **not built** — they belong to F03–F09. "Dashboard (stub)" means an empty page title only, so the nav entry has somewhere to land; its content ships with the feature that owns it.

---

## S13 · Login

**Wireframe page:** `#login-view`

```
┌──────────────────────────────┐
│ Login                 CW brand │
│ E-mail address*                │
│ Password*                      │
│          Forgot password?   [LOGIN] │
└──────────────────────────────┘
```

Matches the HTML. "Forgot password?" and the set-password / reset-password screens are Keycloak-themed pages (FRD-01 §6), not built as React screens — out of scope for the frontend gate, in scope for the Keycloak realm theme (gate 0, `devops-docker-architect`).
