# FRD-03 — Text wireframes

Source: `.claude/docs/source/FRD-03/WF-03_Projects_and_Work_Cycle.html` (v1.2, canonical — read-only, R-SRC-1). All 12 page ids cited below were confirmed present by direct read; the file contains exactly these 12 (plus its own login stub), matching FRD-03 §3's screens table one-to-one. FRD v1.2 and `spec.md` win on conflict. Layout rules R-UI-1…R-UI-9 apply to every screen and are not repeated per screen. Example names/dates are the wireframe's own sample data.

**Drawn elsewhere, not redrawn here:** employee record Projects tab and the PRJ-08 project-member view (`FRD-01-shared-workspace/wireframes.md`, W7/W10 — their data comes from this module); My Team's "Assign" button and the employee record's "Assign project" header action (same file) — both open S7 below.

---

## S1 · Projects list (HR, all, read-only)

**Wireframe page:** `p-hr-projects` · **Route:** `/projects` (menu "Projects")

```
Projects
12 projects · created and maintained by managers
Status [All ▾]  Manager [All ▾]                        [Search…]  [⤓]
┌──────┬─────────────────────────┬────────────────────┬──────────────┬────────┬─────────────────────────┬───────────┬────────┐
│ ID   │ Project                 │ Client             │ Manager      │ People │ Period                  │ Status    │ Action │
├──────┼─────────────────────────┼────────────────────┼──────────────┼────────┼─────────────────────────┼───────────┼────────┤
│P-024 │ Harbor Logistics Portal │ Harbor Logistics BV│ Mark Smit    │ 3      │ 01-03-2026 – 08-10-2026 │ Completed │ [View] │
│P-027 │ GreenLeaf Webshop       │ GreenLeaf Retail   │ Mark Smit    │ 4      │ 01-09-2026 – 31-03-2027 │ Active    │ [View] │
│P-031 │ Atlas CRM phase 2       │ Atlas Insurance    │ Anouk Peters │ 0      │ 02-11-2026 – 30-04-2027 │ Planned   │ [View] │
└──────┴─────────────────────────┴────────────────────┴──────────────┴────────┴─────────────────────────┴───────────┴────────┘
Showing 1 to 6 of 12                                                     ‹ 1 2 ›
```

**Changes from the HTML / notes:**
- No "+ New Project" — HR never creates projects (PRJ-01).
- Status filter: the HTML lists All/Active/Planned/Completed only; offer all six FRD statuses (Planned, Active, On hold, Overdue, Completed, Withdrawn) — FRD wins.
- Export icon added next to search (R-UI-2; FRD 4.1 `/projects/export`) — not drawn in the HTML.
- Dates shown dd-mm-yyyy (G-12); the HTML shows "01 Mar – 08 Oct 2026".
- "People" semantics: spec D-03-21.

---

## S2 · My Projects (Manager)

**Wireframe page:** `p-mg-projects` · **Route:** `/projects` (menu "My Projects", own projects only)

```
My Projects                                                    [+ New Project]
2 active · 2 completed
Status [All ▾]                                         [Search…]  [⤓]
┌──────┬─────────────────────────┬────────────────────┬────────┬─────────────────────────┬───────────┬────────┐
│ ID   │ Project                 │ Client             │ People │ Period                  │ Status    │ Action │
├──────┼─────────────────────────┼────────────────────┼────────┼─────────────────────────┼───────────┼────────┤
│P-024 │ Harbor Logistics Portal │ Harbor Logistics BV│ 3      │ 01-03-2026 – 08-10-2026 │ Completed │ [View] │
│P-027 │ GreenLeaf Webshop       │ GreenLeaf Retail   │ 4      │ 01-09-2026 – 31-03-2027 │ Active    │ [View] │
└──────┴─────────────────────────┴────────────────────┴────────┴─────────────────────────┴───────────┴────────┘
```

**Changes from the HTML / notes:** same status-filter, export and date notes as S1. No Manager column (all rows are the user's own). "+ New Project" → S5.

---

## S3 · Project page, HR read-only

**Wireframe page:** `p-hr-proj` · **Route:** `/projects/:projectId?tab=overview|team|feedback`

```
← Back to Projects
Harbor Logistics Portal                                   [Change project manager]
P-024 · Harbor Logistics BV · Completed
┌ Period ─────────┐ ┌ Team ────┐ ┌ Client feedback ┐ ┌ Project reviews ┐
│ 01-03 – 08-10   │ │ 3        │ │ 2               │ │ 1 / 3           │
│ 2026 · completed│ │ people   │ │ Last 05-10-2026 │ │ completed       │
└─────────────────┘ └──────────┘ └─────────────────┘ └─────────────────┘
[Overview] [Team] [Client feedback (2)]

Overview
┌ Project ─────────────────────────────┐ ┌ Client ──────────────────────────────────┐
│ Project ID       P-024               │ │ Client            Harbor Logistics BV    │
│ Project manager  Mark Smit           │ │ Industry          Logistics              │
│ Type             Fixed price         │ │ Address           Waalhaven Z.z. 19, R'dam│
│ Start date       01-03-2026          │ │ Point of contact  Jan Verbeek · Product owner│
│ End date         08-10-2026          │ │ E-mail            j.verbeek@harbor…      │
│ Status           Completed           │ │ Phone             +31 10 123 4567        │
│ Work mode        Hybrid · client site│ │ Secondary contact Ilse de Groot · IT mgr │
│ Tech stack       Laravel, Vue, PgSQL │ │ Contract ref.     HL-2026-014            │
└──────────────────────────────────────┘ └──────────────────────────────────────────┘
┌ Description ─────────────────────────────────────────────────────────────────────┐
│ Rebuild of the Harbor customer portal: route planner, shipment tracking and …     │
└────────────────────────────────────────────────────────────────────────────────────┘

Team                                   (no add button)
│ Team member     │ Role           │ Allocation │ Assigned        │ Review              │
│ Lotte Jansen    │ Lead developer │ 100%       │ 01-03 – 08-10   │ Due 15-10           │
│ Youssef Tahiri  │ Developer      │ 50%        │ 01-03 – 08-10   │ Reviewed · 09-10    │
│ Priya Sharma    │ Developer      │ 20%        │ 01-03 – 30-06   │ Reviewed · 30-06    │

Client feedback                        (no add button)
│ Jan Verbeek · Product owner                    ★★★★★ │
│ 05-10-2026 · e-mail · whole team                      │
│ "Good handover documentation. Some delays in the last sprint."│
```

**Changes from the HTML / notes:**
- The only action is "Change project manager" → S11 (AC-03-2). No "+ Assign employee", no "+ Add" (AC-03-2: "the Team and Client feedback tabs have no add buttons").
- The HTML renders all three sections on one scroll; the FRD route gives them as tabs (`?tab=overview|team|feedback`) — follow the FRD.
- A feedback attachment link is shown when one exists (not drawn; download endpoint is spec D-03-12).

---

## S4 · Project page, Manager

**Wireframe page:** `p-mg-proj` · **Route:** `/projects/:projectId?tab=overview|team|feedback`

```
← Back to My Projects
Harbor Logistics Portal                         [Edit project] [+ Client feedback]
P-024 · Harbor Logistics BV · Completed
(same 4 KPI cards and Overview tab as S3)

Team                                                       [+ Assign employee]
│ Team member     │ Role           │ Allocation │ Assigned        │ Review              │
│ Lotte Jansen    │ Lead developer │ 100%       │ 01-03 – 08-10   │ [Write review]      │
│ Youssef Tahiri  │ Developer      │ 50%        │ 01-03 – 08-10   │ Reviewed · 09-10    │
│ Priya Sharma    │ Developer      │ 20%        │ 01-03 – 30-06   │ Reviewed · 30-06    │

Client feedback                                                          [+ Add]
│ Jan Verbeek · Product owner                    ★★★★★ │
│ 05-10-2026 · e-mail · whole team                      │
│ "Good handover documentation. Some delays in the last sprint."│
```

**Changes from the HTML / notes:**
- "+ Assign employee" → S7 with Project fixed; "+ Client feedback" / "+ Add" → S8; "Write review" → S10; "Edit project" → S6.
- Team member names link to the PRJ-08 member view (`/projects/:projectId/members/:code`, FRD-01 W10) when the member is from another team — FRD-00 Appendix A #3 "Add a read-only page reached from the project Team tab"; not drawn in the HTML.
- Ending an assignment early (PR12) has no control in the HTML — **[OPEN]**, needs a per-row action on the Team tab (not drawn; do not invent its layout before approval).
- Status changes (On hold / Completed / Withdrawn, PR08) have no control on this page in the HTML — spec D-03-7.

---

## S5 · New project

**Wireframe page:** `p-mg-proj-new` · **Route:** `/projects/new`

```
← Back to Projects
New project
┌ Project ───────────────────────────────────────────────────────────┐
│ Project name*      [e.g. …                  ]                       │
│ Project manager*   [Mark Smit ▾]        Type*   [Fixed price ▾]      │
│ Status*            [Planned ▾]                                       │
│ Start date*        [dd-mm-yyyy]         End date [dd-mm-yyyy]        │
│ Work mode          [Remote / Hybrid …]  Tech stack [              ]  │
└──────────────────────────────────────────────────────────────────────┘
┌ Client ────────────────────────────────────────────────────────────┐
│ Client name*       [              ]     Industry   [              ]  │
│ Address            [                                            ]  │
│ Point of contact*  [              ]     POC role   [              ]  │
│ POC e-mail*        [              ]     POC phone  [              ]  │
│ Secondary contact  [              ]     Contract reference [      ]  │
└──────────────────────────────────────────────────────────────────────┘
┌ Description ───────────────────────────────────────────────────────┐
│ [                                                                ]  │
└──────────────────────────────────────────────────────────────────────┘
[Cancel]                                                   [Create project]
```

**Changes from the HTML / notes:**
- No "Delete project" (AC-03-1).
- Status dropdown: the HTML offers Planned/Active/On hold/Completed — spec D-03-7 (recommendation: Planned/Active only on create).
- Project manager dropdown: spec D-03-8 (recommendation: default and restrict to self).
- Secondary contact is a single box in the HTML: spec D-03-22.
- Messages per spec §5 S5/S6 (combined client message "Enter the client name and a valid contact e-mail.").

---

## S6 · Edit project

**Wireframe page:** `p-mg-proj-edit` · **Route:** `/projects/:projectId/edit`

```
← Back to project
Edit project · Harbor Logistics Portal                                  P-024
(same Project / Client / Description sections as S5, filled in)
┌ Withdrawal (shown only when Status = Withdrawn) ─────────────────────┐
│ Withdrawal date*  [dd-mm-yyyy]                                       │
│ Reason*           [                                              ]   │
└────────────────────────────────────────────────────────────────────────┘
[Delete project] [Cancel]                                    [Save changes]
```

**Changes from the HTML / notes:**
- **Withdrawal block added** — not drawn in the HTML, but FRD-03 Inputs ("withdrawal date and reason when Withdrawn"), the validation row ("Add the withdrawal date and reason.") and AC-03-7 require it; the HTML's status list also lacks "Withdrawn". Placement depends on D-03-7 (S6 vs a status action on S4).
- "Delete project" red outline, left (R-UI-1); on a project with assignments/reviews it shows the PRJ-06 message instead of deleting (FRD wording; **[ASSUMPTION]** button stays visible and the server answers `409`, since the FRD defines a message for it rather than hiding it).

---

## S7 · Assign to project (pop-up)

**Wireframe page:** `m-assign` · **Trigger:** "+ Assign employee" (S4), "Assign" (My Team), "Assign project" (employee record, S9)

```
┌ Assign to project ──────────────────────────────── × ┐
│ Employee*         [Ruben Bakker — 0%        ▾]        │
│ Project*          [GreenLeaf Webshop        ▾]        │
│ Role on project*  [Developer                 ]        │
│ Allocation %*     [60 ]                               │
│ Start date*       [19-10-2026]   End date [31-03-2027]│
│ [Cancel]                                    [Assign]  │
└────────────────────────────────────────────────────────┘

(over 100%)  Total allocation will be {x}%. Do you want to continue?
             [Cancel]                                   [Continue]
```

**Changes from the HTML / notes:**
- Employee list = `/employees/assignable` ("Active employees from any team with current allocation"), shown as "{name} — {x}%" as drawn.
- Project: fixed (read-only) when opened from S4; a dropdown of the manager's own projects otherwise (as drawn).
- Employee fixed (read-only) when opened from My Team / employee record / S9.
- The over-allocation confirm step is not drawn — FRD message is exact; button labels "Cancel"/"Continue" are **[ASSUMPTION]**.

---

## S8 · Add client feedback (pop-up)

**Wireframe page:** `m-client-fb` · **Trigger:** "+ Client feedback" / "+ Add" (S4), "+ Add" (S10)

```
┌ Add client feedback ────────────────────────────── × ┐
│ Client            Harbor Logistics BV   (read-only)   │
│ Client contact*   [Jan Verbeek        ]               │
│ Contact role      [Product owner      ]               │
│ Date received*    [12-10-2026]                        │
│ Collected via*    ( Call ) ( E-mail ) ( Meeting ) ( Survey ) │
│ Applies to*       [Whole project team ▾]              │
│ Client satisfaction*   1  2  3  4  5                  │
│                        Below   Meets   Exceeds        │
│ Client comments*  [                               ]   │
│ ┌ Attach e-mail or notes, or browse file ┐            │
│ └────────────────────────────────────────┘            │
│ [Cancel]                              [Save feedback] │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- "Collected via" starred here (DB NOT NULL; HTML has no star) — spec D-03-14.
- "Applies to" options: "Whole project team" + each person assigned to the project.
- Future date → "The date cannot be in the future." (AC-03-6).

---

## S9 · Check project status (bench)

**Wireframe page:** `p-mg-bench` · **Route:** `/team/:code/bench-check/:taskId` (task "Check project status")

```
← Back to My Tasks
Check project status · Ruben Bakker               On bench since 06-10-2026 · 6 days
┌ Status ─────────────────────────────────────────────────────────┐
│ Last project     MedTrack Dispense · ended 06-10                 │
│ Previous checks  None                                            │
│ Decision*                                                         │
│ [ Assign project      ]   [ Stay on bench         ]               │
│   Pick a project now        Check again in 7 days                 │
│ Notes                                                             │
│ [Expected start on Atlas phase 2.                          ]      │
└────────────────────────────────────────────────────────────────────┘
[Cancel]                                                     [Confirm]
```

**Changes from the HTML / notes:**
- **Removed "Skills" row and the "Projects needing people" panel** (with its own "Assign project" button) — no data source in the schema or FRD (spec D-03-11, R-SRC-2).
- "Check again in 7 days" shows the current `BENCH_CHECK` setting value, not a fixed 7 (G-08).
- Choosing "Assign project" opens S7 with the employee fixed (spec §7, D-03-11).
- Confirm completes the task through the undo window (`202 {undoUntil}`).

---

## S10 · Project review

**Wireframe page:** `p-mg-proj-review` · **Route:** `/project-reviews/:reviewId` (task "Project review"; "Write review" on S4)

```
← Back to My Tasks
Project review · Lotte Jansen
Harbor Logistics Portal · ended 08-10-2026 · Lead developer · 100%
┌ Manager review ─────────────────────────────┐ ┌ Client feedback ──────── [+ Add] ┐
│ Delivery & quality                          │ │ Jan Verbeek · Harbor Logistics BV │
│   1   2   3   4   5                         │ │ ★★★★★  02-09-2026 · call          │
│   Below      Meets      Exceeds             │ │ "Delivered the planner ahead of   │
│ Collaboration with client & team            │ │  peak season."                    │
│   1   2   3   4   5                         │ │ Jan Verbeek · Harbor Logistics BV │
│ Ownership & initiative                      │ │ ★★★★★  05-10-2026 · e-mail        │
│   1   2   3   4   5                         │ │ "Good handover documentation. …"  │
│ Strengths*                                  │ └───────────────────────────────────┘
│ [Led the route-planner rebuild, …       ]   │
│ Areas to develop                            │
│ [Estimate more conservatively; …        ]   │
│ Outcome                                     │
│ [ Project delivered ]  [ Performance concern ]│
│   Next project round     Propose PIP        │
└─────────────────────────────────────────────┘
[Cancel]                                   [Save draft] [Submit review]
```

**Changes from the HTML / notes:**
- Questions and their 1–5 labels render from the active `PROJECT` appraisal form (spec D-03-6), not a fixed layout — same rule as FRD-02's probation form.
- Client feedback panel shows contact, date, method and rating (AC-03-4), including TEAM feedback.
- On submit with "Performance concern", a "Start PIP" link is offered (FRD 4.6 "offers Start PIP") once F07 exists; until then it is omitted (FRD-01 D-W8 precedent).
- Submit message: "Rate all items and add strengths before submitting."

---

## S11 · Change project manager (pop-up)

**Wireframe page:** `m-change-pm` · **Trigger:** "Change project manager" on S3

```
┌ Change project manager ─────────────────────────── × ┐
│ Project              Harbor Logistics Portal          │
│ Current manager      Mark Smit                        │
│ New project manager* [Select manager… ▾]              │
│ Effective date*      [01-11-2026]                     │
│ [✓] Move open project tasks (reviews, client feedback)│
│     to the new manager                                │
│ Line manager also moves to the new manager:           │
│   [✓] Lotte Jansen      (reports to Mark Smit)         │
│   (Priya Sharma — reports to Anouk Peters, not listed)│
│ Reason*                                                │
│ [Mark Smit is leaving CubeWorks                    ]  │
│ [Cancel]                            [Change manager]  │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- **Per-member tick list added** — not drawn in the HTML, but required by FRD-03 §2 ("This is ticked by default; HR can untick it per person"), PRJ-01 ("unless HR unticks them") and 4.7's `manager-change-preview` endpoint. Lists only members whose line manager is the current PM; others keep their manager (AC-03-8). Exact layout **[ASSUMPTION]**; spec D-03-9.
- "Choose a new project manager." when empty or equal to the current PM. Messages for Effective date / Reason: spec D-03-9.

---

## S12 · My Projects (Employee)

**Wireframe page:** `p-em-projects` · **Route:** `/me/projects` (menu "My Projects")

```
My Projects
2 project(s)
┌─────────────────────────┬────────────────────┬────────────────┬────────────┬─────────────────────────┬───────────┐
│ Project                 │ Client             │ Role           │ Allocation │ Period                  │ Status    │
├─────────────────────────┼────────────────────┼────────────────┼────────────┼─────────────────────────┼───────────┤
│ Harbor Logistics Portal │ Harbor Logistics BV│ Lead developer │ 100%       │ 01-03-2026 – 08-10-2026 │ Completed │
│ GreenLeaf Webshop       │ GreenLeaf Retail   │ Developer      │ 50%        │ 01-09-2026 – 31-03-2027 │ Active    │
└─────────────────────────┴────────────────────┴────────────────┴────────────┴─────────────────────────┴───────────┘
```

**Changes from the HTML / notes:**
- Read-only, no row action. Sort/filter/search per R-UI-2 (not drawn in the HTML). Whether the employee also sees reviews/feedback: spec D-03-16.
