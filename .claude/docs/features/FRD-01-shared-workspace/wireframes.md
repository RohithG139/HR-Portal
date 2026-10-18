# FRD-01 — Text wireframes

Source (v1.2, canonical, decided 2026-10-16): `.claude/docs/source/FRD-01/WF-01_Shared_Workspace.html` — the module's own dedicated wireframe file, distinct from the legacy monolithic `docs/source/CubeWorks_HR_interactive_wireframes_v6_1.html` (v1.1, superseded). Every page id and markup detail below has been confirmed against WF-01 by direct read in this revision (page ids `p-hr-dash`, `p-hr-tasks`, `p-hr-employees`, `p-hr-case-*`, `p-mg-dash`, `p-mg-tasks`, `p-mg-team`, `p-mg-case-*`, `p-em-dash`, `p-em-tasks`, `p-em-profile`, `p-it-dash`, `p-it-tasks`, `p-it-employees`, modal `m-transfer` all exist in WF-01 exactly as cited) — content carried over unchanged from the v1.1-era draft of this file turned out to already match WF-01 verbatim in every screen checked (same card labels, same table columns, same example names/dates, same modal fields), so no wireframe content needed correcting, only the source citation. R-SRC-1 rank: FRD v1.2 and `spec.md` win on conflict; see `spec.md`'s header table and `.claude/docs/source/README.md` "Order of truth". Layout rules R-UI-1…R-UI-9 apply to every screen below and are not repeated per screen.

**Screens already drawn elsewhere — not redrawn here:** Login (`#login-view`) → `F01-onboarding/wireframes.md` §S13. My Tasks + notifications, top bar, user menu (`p-hr-tasks`/`p-mg-tasks`/`p-em-tasks`/`p-it-tasks`) → `F01-onboarding/wireframes.md` §S12 (page id corrected: WF-01 uses `p-em-tasks` for the Employee role, not `p-sb-tasks`). Employees list (HR) → `F01-onboarding/wireframes.md` §S1. Employee record — Overview/Profile/History tabs → `F01-onboarding/wireframes.md` §S11 (this file only adds the two tabs F01 did not draw, W7/W8 below, and the full tab bar including header actions). My Profile (`p-em-profile`) → `F01-onboarding/wireframes.md` §S10.

---

## W1 · HR dashboard

**Wireframe page:** `p-hr-dash` (route `/dashboard`, FRD-01 v1.2 §3 Screen 3) — confirmed by direct read: card order, labels and example data below match WF-01's markup exactly (Active employees/In onboarding/On bench/PIPs kpi-row, My tasks list, Appraisal round card, Upcoming timeline).

```
Good morning, Emma                                    Monday 12 October 2026
                                                           [+ New Employee]
┌ Active employees ┐ ┌ In onboarding ───┐ ┌ On bench ────┐ ┌ PIPs ──────────┐
│ 48                │ │ 2                │ │ 4             │ │ 2 + 1          │
│ +2 this month      │ │ Sanne de Boer ·  │ │ Longest        │ │ 2 active ·     │
│                    │ │ Jesse Willems    │ │ 18 days        │ │ 1 to approve   │
└────────────────────┘ └──────────────────┘ └────────────────┘ └────────────────┘
┌ My tasks ──────────────────────────────── [View all] ┐
│ PIP · Approve PIP · Kees Willems     13 Oct   Open     │
│ Onboarding · Onboarding checklist · Sanne de Boer  …   │
│ ...                                                      │
└──────────────────────────────────────────────────────┘
┌ Appraisal round 2026 ────────────────────── [View] ┐  ┌ Upcoming ──────────────┐
│ Completion 62%                                       │  │ Final PIP review ·      │
│ Self reviews 40/44 · Peer reviews 71/120 ·           │  │  Daan Visser 18 Oct     │
│ Manager reviews 22/44 · Closes 31 Oct 2026           │  │ Jesse Willems starts    │
└───────────────────────────────────────────────────┘  │  21 Oct                  │
                                                           │ Last day · Thomas       │
                                                           │  Mulder 24 Oct          │
                                                           │ Probation ends · Sanne  │
                                                           │  de Boer 05 Nov         │
                                                           │ Contract ends · Priya   │
                                                           │  Sharma 30 Nov          │
                                                           └──────────────────────────┘
```

**Build now vs later (spec.md §5/§7 W1):** Active employees and In onboarding cards, and the My tasks widget, use data F01 already migrated. On bench, PIPs, Appraisal round and most of Upcoming are deferred — each card is simply omitted from the page until its owning feature (F04/F06/F07/F08) extends `GET /api/dashboard/hr` (W01) to include it. "+ New Employee" button is F01's own (S2), already built — shown here only because it sits in this page's header in the wireframe.

---

## W2 · Manager dashboard

**Wireframe page:** `p-mg-dash` (route `/dashboard`) — confirmed by direct read, including the "My team" table's exact rows (Lotte Jansen/Priya Sharma/Ruben Bakker/Daan Visser).

```
Good morning, Mark                              My team · 9 people · 3 projects
┌ Team members ─┐ ┌ On bench ────────┐ ┌ Reviews to write ┐ ┌ Plans to approve ┐
│ 9              │ │ 2                 │ │ 3                  │ │ 1                  │
│ Direct reports │ │ Ruben Bakker ·    │ │ Project + appraisal│ │ Lotte Jansen       │
│                │ │ Youssef Tahiri    │ │                    │ │                    │
└────────────────┘ └───────────────────┘ └────────────────────┘ └────────────────────┘
┌ My tasks ──────────────────────────────── [View all] ┐
│ PIP · PIP check-in #6 · Daan Visser     Today   Open    │
│ ...                                                       │
└──────────────────────────────────────────────────────┘
┌ My team ───────────────────────────────────── [View all] ┐
│ Employee │ Projects │ Allocation │ Development │ Status    │
│ LJ Lotte Jansen · Senior Developer │ GreenLeaf Webshop │ 50% │ 0%  │ On project │
│ PS Priya Sharma · Developer        │ MedTrack Dispense │ 80% │ 100%│ On project │
│ RB Ruben Bakker · Developer        │ —                 │ 0%  │ 55% │ Bench      │
│ DV Daan Visser · QA Engineer       │ Atlas CRM Migration│ 60% │ —   │ PIP        │
└──────────────────────────────────────────────────────────┘
```

**Build now vs later:** Team members/Direct reports count and My tasks widget are buildable now. On bench, Reviews to write, Plans to approve, and the My team table's Projects/Allocation/Development columns are deferred to F04/F05/F06 (same dependency chain as W1).

---

## W3 · Employee dashboard (active stage)

**Wireframe page:** `p-em-dash` (route `/dashboard`) — confirmed by direct read. F01's S7 (`p-sb-dash`, new-joiner dashboard — not part of WF-01; F01 cites it from its own onboarding-era source, `F01-onboarding/wireframes.md` §S7) is unchanged and still used while `stage='ONBOARDING'`; this is the different screen, `p-em-dash`, that FRD-01 v1.2 describes for `stage='ACTIVE'`.

```
Hi Lotte                                   Senior Developer · Manager: Mark Smit
┌ Current projects ┐ ┌ Development plan ──┐ ┌ Appraisal 2026 ──────┐
│ 1                  │ │ In approval         │ │ Self review           │
│ GreenLeaf Webshop   │ │ Submitted 10 Oct    │ │ Due 20 Oct             │
└────────────────────┘ └─────────────────────┘ └────────────────────────┘
┌ My tasks ──────────────────────────────── [View all] ┐
│ Appraisal · Self review · Appraisal 2026   20 Oct  Open  │
│ Peer · Peer review · Priya Sharma          20 Oct  Open  │
└──────────────────────────────────────────────────────┘
┌ My equipment ──────────────────────┐
│ Item                    │ Asset no.  │
│ Laptop · MacBook Pro 14"│ CW-LT-0231 │
│ Monitor · Dell 27"      │ CW-MN-0118 │
│ Access badge            │ CW-BD-0412 │
└─────────────────────────────────────┘
```

**Build now vs later:** My tasks and My equipment are buildable now (same components as F01's S7). Current projects, Development plan and Appraisal cards are deferred to F04/F05/F06.

A PIP-specific variant exists in the wireframe (`p-yt-dash`, Youssef Tahiri) that swaps the Development plan card for an "Improvement plan" card — that variant belongs to F07 (PIP) and is not built here.

---

## W4 · IT admin dashboard

**Wireframe page:** `p-it-dash` (route `/dashboard`) — confirmed by direct read.

```
Good morning, Kevin                                    Provisioning & access
┌ Equipment requests ┐ ┌ Open setup items ┐ ┌ Access to revoke ┐ ┌ Not returned ┐
│ 1                    │ │ 2                  │ │ 1                  │ │ 1               │
│ Jesse Willems ·       │ │ Sanne de Boer      │ │ Thomas Mulder ·    │ │ Ex-employee     │
│ starts 21 Oct         │ │                    │ │ 24 Oct             │ │                 │
└──────────────────────┘ └────────────────────┘ └────────────────────┘ └─────────────────┘
┌ My tasks ──────────────────────────────── [View all] ┐
│ Provision · Remaining setup · Sanne de Boer   13 Oct  Open │
│ IT request · Equipment request · Jesse Willems 20 Oct Open │
│ Revoke · Revoke access · Thomas Mulder         24 Oct Open │
└──────────────────────────────────────────────────────┘
```

**Build now vs later:** Equipment requests, Open setup items and My tasks are buildable now (F01's `onboarding`/`engine_task` data). Access to revoke and Not returned are deferred to F08.

---

## W5 · My Team (Manager)

**Wireframe page:** `p-mg-team` (route `/team`, FRD-01 v1.2 §3 Screen 9) — confirmed by direct read: the table's exact columns (Employee / Current projects / Allocation / Development / Status / Action) and all 7 visible rows match WF-01's markup, down to the per-row `data-modal="assign"` action and the "Showing 1 to 7 of 9" pagination caption.

```
My Team                                                9 direct reports
┌ Status [All ▾] [On project] [Bench] [PIP] [Onboarding]      🔍 search  [⬇] ┐
└────────────────────────────────────────────────────────────────────────┘
┌ Employee │ Current projects │ Allocation │ Development │ Status │ Action ┐
│ LJ Lotte Jansen · Senior Developer │ GreenLeaf Webshop │ 50% │ 0%   │ On project │ Assign │
│ PS Priya Sharma · Developer        │ MedTrack Dispense │ 80% │ 100% │ On project │ Assign │
│ RB Ruben Bakker · Developer        │ —                 │ 0%  │ 55%  │ Bench      │ Assign │
│ DV Daan Visser · QA Engineer       │ Atlas CRM Migration│60% │ —    │ PIP        │ Assign │
│ YT Youssef Tahiri · Developer      │ —                 │ 0%  │ —    │ Bench      │ Assign │
│ SB Sanne de Boer · UX Designer     │ GreenLeaf Webshop │ 60% │ —    │ Onboarding │ Assign │
│ JW Jesse Willems · Developer       │ —                 │ 0%  │ —    │ Onboarding │ Assign │
└──────────────────────────────────────────────────────────────────┘
Showing 1 to 7 of 9                                              [pagination]
```

- Sortable, filterable, searchable, exportable per R-UI-2, same mechanics as F01's Employees list (S1).
- WS-02 scoped: only employees whose `manager_id` is the logged-in manager.
- "Current projects"/"Allocation"/"Development" columns render as "—" until F04/F05 exist; "Assign" opens F04's assign-project flow (not built here).

---

## W6 · Transfer manager (pop-up)

**Wireframe page:** modal `m-transfer` (FRD-01 v1.2 §3 Screen 14, "Modal on Screen 10 · 'Transfer manager'"), opened from the employee record header action "Transfer manager" (HR only) — confirmed by direct read: `<dl>` Employee/Current manager pair, New manager select (options "Select manager…", "Anouk Peters", "Jeroen de Wit"), Effective date field (value `01-11-2026`), the three "Move open tasks" checkboxes (first two checked), Reason textarea (placeholder text "Team restructure"), and Cancel/Transfer footer all match WF-01's markup exactly, including the "Transfer scheduled for 01 Nov 2026" toast text.

```
Transfer manager                                                    [×]
Employee            Lotte Jansen
Current manager      Mark Smit

New manager*          [Select manager… ▾]
Effective date*        (dd-mm-yyyy)

Move open tasks to new manager
  [x] Approve development plan
  [x] Manager review · Appraisal 2026
  [ ] Project review · Harbor Logistics Portal

Reason                (free text, e.g. "Team restructure")

[Cancel]                                                    [Transfer]
```

- Cancel: red outline, left (R-UI-1). Transfer: green, right.
- "New manager" dropdown lists every `app_role='MANAGER'` employee except the current manager.
- Unticked tasks (per the mock-up, "Project review · Harbor Logistics Portal" above) stay with the current manager; this exactly matches FRD-01's AC-W02 (`spec.md` §11).
- No "Save" state — the whole pop-up is submitted at once with "Transfer" (W10).

---

## W7 · Employee record — Projects tab (contract only, build deferred — `spec.md` §12 D-W1)

**Wireframe pages:** `p-hr-case-lotte` (HR, editable actions are F04's), `p-mg-case-lotte` (Manager, read-only) — confirmed by direct read: both pages' Projects tab-pane has the identical 6-column table (Project/Client/Role/Allocation/Period/Status) and the same two example rows (Harbor Logistics Portal · Completed; GreenLeaf Webshop · Active).

```
[Overview] [Profile] [Projects] [Development & reviews] [History]  ← History: HR only

┌ Project                   │ Client             │ Role           │ Allocation │ Period               │ Status    ┐
│ Harbor Logistics Portal   │ Harbor Logistics BV│ Lead developer │ 100%       │ 01 Mar – 08 Oct 2026 │ Completed │
│ GreenLeaf Webshop         │ GreenLeaf Retail    │ Developer      │ 50%        │ 01 Sep 2026 – 31 Mar │ Active    │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

Drawn here as the contract FRD-01 promises; not built until F04 adds this tab to the employee record (see `spec.md` §12 D-W1 for why this spec does not build a placeholder version).

---

## W8 · Employee record — Development & reviews tab (contract only, build deferred — `spec.md` §12 D-W1)

**Wireframe pages:** `p-hr-case-lotte` / `p-mg-case-lotte` — confirmed by direct read (KPIs/Development goals/Reviews cards, exact example rows). The manager's own-team view additionally shows a "+ Add KPI" action (`p-mg-case-jesse`'s Development & reviews pane, `data-modal="add-kpi"` — confirmed by direct read); "Enter results" / "Progress check" are named in the FRD text but not found as distinct buttons on any `p-mg-case-*` page read — likely rendered only once a KPI/plan exists to act on (none of the manager's-team example employees read have one yet). These all belong to F05's own screens, not drawn here.

```
┌ KPIs set by manager ──────────────────────────────────────────────────┐
│ KPI                     │ Target          │ Weight │ Measured  │ % met │
│ Billable utilisation    │ ≥ 85% / quarter │ 30%    │ Quarterly │ 96%   │
│ Client satisfaction     │ Avg ≥ 4.0 / 5   │ 40%    │ Per proj. │ 100%  │
│ Code review turnaround  │ ≤ 1 working day │ 30%    │ Monthly   │ 77%   │
└────────────────────────────────────────────────────────────────────┘
┌ Development goals ──────────────────────────────────────────────────┐
│ Goal                                  │ Type     │ Target date │ Progress │
│ AWS Solutions Architect certification │ Training │ 30 Jun 2027 │ 0%       │
│ Mentor a junior developer             │ Coaching │ 31 Mar 2027 │ 0%       │
│ Lead a client discovery workshop      │ Project  │ 31 Dec 2026 │ 0%       │
└────────────────────────────────────────────────────────────────────┘
┌ Reviews ───────────────────────────────────────────────────────────┐
│ Project review · Atlas CRM phase 1 — ★★★★☆                          │
│   Mark Smit · 31 Mar 2026 — "Strong ownership of the data migration."│
│ Client feedback · Harbor Logistics BV — ★★★★★                       │
│   J. Verbeek · call · 02 Sep 2026 — "Delivered ahead of peak season."│
│ Appraisal 2025 · Meets expectations — ★★★★☆                         │
│   Finalised by Emma de Vries · 14 Nov 2025                           │
└────────────────────────────────────────────────────────────────────┘
```

---

## W9 · Employees list (IT admin, read-only) — new in v1.2, `spec.md` §12 D-W9

**Wireframe page:** `p-it-employees` (route `/employees`, FRD-01 v1.2 §3 Screen 8) — confirmed by direct read: page title "Employees", subtitle "Read-only", a single "Status" filter (All/Onboarding/Active/Offboarding), search box, **no export icon** (unlike `p-hr-employees`, which has both export and a "+ New Employee" button).

```
Employees                                                      Read-only
┌ Status [All ▾]                                               🔍 search  ┐
└────────────────────────────────────────────────────────────────────────┘
┌ Employee                     │ Job title        │ Status      │ Assets ┐
│ JW Jesse Willems · jesse@…   │ Developer        │ Onboarding  │   1    │
│ SB Sanne de Boer · sanne@…   │ UX Designer      │ Onboarding  │   2    │
│ LJ Lotte Jansen · lotte@…    │ Senior Developer │ Active      │   3    │
│ RB Ruben Bakker · ruben@…    │ Developer        │ Active      │   1    │
│ TM Thomas Mulder · thomas@…  │ Tech Lead        │ Offboarding │   3    │
└──────────────────────────────────────────────────────────────────────┘
```

No row-click action, no "View"/"Export" controls — read-only is enforced by the fields and controls simply not being rendered for this role (same column set as `spec.md` §5 W9), not by disabling a control that HR sees enabled. Same component/endpoint as `p-hr-employees` (`/employees`), different response shape for `app_role='IT_ADMIN'` (`spec.md` §6, D-W9).

---

## W10 · Project member view (PRJ-08) (contract only, build deferred — `spec.md` §12 D-W7)

**Wireframe page:** not drawn in WF-01 — confirmed by direct read (no page id matching "member"/"prj" exists anywhere in `WF-01_Shared_Workspace.html`), matching FRD-01 v1.2 §3 Screen 12's own "not drawn" note and its own Appendix A #3 ("The limited project-member view (PRJ-08) is not drawn. Suggested fix: Add a read-only page reached from the project Team tab."). Contract reconstructed from v1.2's acceptance scenario text and API table only (`spec.md` §5 W10):

```
← Back to [Project] Team                                    Read-only
{Avatar} {Name}                           {job title} · on this project

┌ Assignment ───────────────────────────────────────────────────────┐
│ Project              Role              Allocation   Period          │
│ {project name}       {role_on_project} {allocation}  {start}–{end}  │
└─────────────────────────────────────────────────────────────────────┘
┌ Project reviews ───────────────────────────────────────────────────┐
│ {review title} — ★★★★☆                                              │
│   {reviewer} · {date} — "{comment}"                                 │
└─────────────────────────────────────────────────────────────────────┘
┌ Client feedback ────────────────────────────────────────────────────┐
│ {client} — ★★★★★                                                     │
│   {contact} · {channel} · {date} — "{comment}"                      │
└─────────────────────────────────────────────────────────────────────┘
```

No Profile, Development plan or Appraisal section — explicitly excluded per FRD-01 v1.2 §6's own acceptance scenario ("he does not see her profile, development plan or appraisal"). Reached from "name on project Team tab" (v1.2 §3), i.e. a link on a screen this spec does not own (F04's Project detail screen); this page itself would be reached at `/projects/:projectId/members/:code`.

---

## Employee record — tab bar and header actions (completing F01's S11)

**Wireframe pages:** `p-hr-case-lotte` (HR), `p-mg-case-lotte` (Manager) — confirmed by direct read: HR header actions are exactly `data-modal="exit"` ("Initiate exit") and `data-modal="transfer"` ("Transfer manager"); every other `p-hr-case-*` page read (sanne/jesse/ruben/daan/youssef/priya/kees/thomas) carries the identical pair. Manager header actions are exactly `data-go="mg-pip-define"` ("Start PIP") and `data-modal="assign"` ("Assign project") on `p-mg-case-lotte`; note the Manager tab bar there has only 4 tabs (Overview/Profile/Projects/Development & reviews, no History) — matching FRD-01 v1.2 §3 Screen 11 "Employee record, Manager (4 tabs, no History)" exactly.

```
← Back to Employees / My Team
{Avatar} {Name}                 {code} · {job title} · {dept} · Manager: {name}
[Initiate exit] [Transfer manager]                                    ← HR header actions
[Start PIP] [Assign project]                                          ← Manager header actions
[Overview] [Profile] [Projects] [Development & reviews] [History]     ← History: HR only
```

F01 built the tab bar with only Overview/Profile/History and no header actions at all. This is the full five-tab bar FRD-01 describes. Per `spec.md` §4/§12 D-W8: "Transfer manager" is this spec's own action (W6, built here); "Initiate exit", "Start PIP" and "Assign project" render per the visibility rule in `spec.md` §4 but open screens owned by F08/F07/F04 respectively — until those features exist, they are omitted from the header entirely (same treatment F01 already gave "Submit resignation" on My Profile), not shown disabled.
