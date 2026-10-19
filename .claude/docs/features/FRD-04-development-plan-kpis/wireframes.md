# FRD-04 — Text wireframes

Source: `.claude/docs/source/FRD-04/WF-04_Development_Plan_and_KPIs.html` (v1.2, canonical — read-only, R-SRC-1). Every page id below was confirmed present by direct read: `p-em-dev`, `p-sb-dev`, `p-yt-dev`, `p-em-dev-define`, `p-sb-dev-define`, `p-mg-dev-approve`, `p-mg-dev-progress`, `p-mg-kpi-update`, `p-mg-dev-eval`, `p-mg-case-*` (Development & reviews tab), modals `m-add-kpi`, `m-add-activity`. Example names, values and labels are copied from the HTML. FRD v1.2 and `spec.md` win on conflict; differences are listed under each screen and in `spec.md` §12. Layout rules R-UI-1…R-UI-9 apply to every screen and are not repeated per screen.

**Drawn elsewhere — not redrawn here:** employee record header, Overview/Profile tabs and tab bar → `FRD-01-shared-workspace/wireframes.md` / `F01-onboarding/wireframes.md` §S11; My Tasks → `F01-onboarding/wireframes.md` §S12; the HR read-only Development & reviews tab → FRD-01 W8 (`FRD-01-shared-workspace/spec.md`).

---

## S1 · My Development (Employee)

**Wireframe page:** `p-em-dev` (variants `p-sb-dev`, `p-yt-dev`) · **Route:** `/me/development` (FRD-04 §3 Screen 1)

```
My Development
Plan 2026–27 · in approval                                        [Edit plan]
┌ KPIs from your manager ──────────────────────────────────────────────────┐
│ KPI                     Target               Weight  Measured  % met     │
│ Billable utilisation    ≥ 85% per quarter     30%    Quarterly  ███ 96%  │
│ Client satisfaction     Average ≥ 4.0 / 5     40%    Per project ███ 100%│
│ Code review turnaround  ≤ 1 working day       30%    Monthly    ██  77%  │
│                                          Weighted KPI score   92%        │
└──────────────────────────────────────────────────────────────────────────┘
┌ My goals ─────────────────────────────────────────────────────────────────┐
│ 1 · Plan  →  2 · Perform plan  →  3 · Evaluate                             │
│ Goal                                   Type      Target date  Status       │
│ AWS Solutions Architect certification  Training  30 Jun 2027  In approval  │
│ Mentor a junior developer              Coaching  31 Mar 2027  In approval  │
│ Lead a client discovery workshop       Project   31 Dec 2026  In approval  │
└───────────────────────────────────────────────────────────────────────────┘
┌ Previous plans ─────────────────────────┐
│ Plan     Goals  KPIs  Result             │
│ 2025–26  3      2     Closed             │
│ 2024–25  2      —     Closed             │
└──────────────────────────────────────────┘
```

State `p-sb-dev` (no open plan):
```
My Development
┌───────────────────────────────────────────────────────────┐
│ You do not have a development plan yet       [Start a plan] │
└───────────────────────────────────────────────────────────┘
```

State `p-yt-dev` (plan `PAUSED`, DEV-05):
```
My Development
┌──────────────────────────────────────────────────────────────────────────┐
│ Your development plan is paused while the improvement plan is active      │
└──────────────────────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- "Measured" values "Per project" and "Monthly" cannot occur: `kpi.frequency` is Quarterly / Half-yearly / Yearly only (Appendix A #9, spec D-04-1). Render the frequency label of the stored value.
- Weighted score 92 % is consistent with the drawn rows ((96×30 + 100×40 + 77×30) ÷ 100 = 91.9 → 92), confirming the FRD formula and the half-up rounding assumption (D-04-28).
- Goal "Status" while the plan is DRAFT/SUBMITTED shows the plan state ("In approval"); once ACTIVE it shows `goal.progress_status`. The FRD's "last manager comment" (DV01) is not drawn — show it under the header when present (`decision.comment`).
- "Edit plan" visibility depends on D-04-6 (editability after submit is open); "Previous plans" "KPIs" column has no data source (D-04-18).
- Where the employee updates their own goal progress (DV06) is not drawn (D-04-7); the employee's full KPI history (FRD §1) is not drawn either (D-04-21).
- `p-sb-dev` "Start a plan": green, right of its message (R-UI-1).

---

## S2 · Development plan – define goals (Employee)

**Wireframe page:** `p-em-dev-define` (3 goals) / `p-sb-dev-define` (1 goal) · **Route:** `/me/development/plan` (FRD-04 §3 Screen 2)

```
← Back to My Development
Development plan                                  Nov 2026 – Oct 2027 · Draft
1 · Plan  →  2 · Perform plan  →  3 · Evaluate
┌ Goal 1 ───────────────────────────────────────────────────────────── × ┐
│ Goal*                     [What do you want to achieve?_______________] │
│ Type*                     (Training / learning) (Project / assignment)  │
│                           (Coaching / mentoring)                        │
│ Target date*              [dd-mm-yyyy]                                  │
│ How will you get there?*  [_____________________________________]       │
│ Support needed from manager [___________________________________]       │
└──────────────────────────────────────────────────────────────────────────┘
┌ Goal 2 … ─────────────────────────────────────────────────────────── × ┐
└──────────────────────────────────────────────────────────────────────────┘
                                                              [+ Add goal]
[Cancel]                                   [Save draft] [Submit for approval]
```

**Changes from the HTML / notes:**
- Messages (FRD §4): "This field is required." · "The target date must be in the future." · "Add at least one goal before submitting." (on Submit).
- HTML draws "Save draft" as primary (blue); per R-UI-1 positive actions are green on the right — Save draft and Submit for approval both green, right; Cancel red outline, left (never saves, G-11).
- Toast on Submit (HTML): "Sent to Mark Smit for approval" — the manager's name, not hard-coded.
- "Nov 2026 – Oct 2027" is `period_label`; its source is open (D-04-19).
- Same page serves "Create development plan" and the revise task after Request changes (D-04-9); in that case show the manager's comment above the goals [ASSUMPTION].

---

## S3 · Approve development plan (Manager)

**Wireframe page:** `p-mg-dev-approve` · **Route:** `/team/:code/development/approve` (FRD-04 §3 Screen 3)

```
← Back to My Tasks
Development plan · Lotte Jansen                Nov 2026 – Oct 2027 · submitted 10 Oct
1 · Plan  →  2 · Perform plan  →  3 · Evaluate
┌ KPIs set by you ────────────────────────────────────────────── [+ Add KPI] ┐
│ KPI                     Target              Weight  Measured               │
│ Billable utilisation    ≥ 85% per quarter    30%    Quarterly              │
│ Client satisfaction     Average ≥ 4.0 / 5    40%    Per project            │
│ Code review turnaround  ≤ 1 working day      30%    Monthly                │
└─────────────────────────────────────────────────────────────────────────────┘
┌ Employee's goals ──────────────────────────────────────────────────────────┐
│ Goal                                   Type      Target date  Support requested     │
│ AWS Solutions Architect certification  Training  30 Jun 2027  Study time 4 h / month │
│ Mentor a junior developer              Coaching  31 Mar 2027  —                      │
│ Lead a client discovery workshop       Project   31 Dec 2026  Workshop slot          │
└─────────────────────────────────────────────────────────────────────────────┘
┌ Decision ───────────────────────────────────────────────────────────────────┐
│ Comments to employee                                                          │
│ [Good plan. Pair with Sanne for goal 2.______________________________]        │
│ Decision                                                                      │
│ [● Approve plan        ]   [○ Request changes   ]                             │
│    Plan becomes active        Back to employee                                │
└───────────────────────────────────────────────────────────────────────────────┘
[Cancel]                                                        [Submit decision]
```

**Changes from the HTML / notes:**
- Validation on Submit decision: Request changes with empty comment → "Add a comment for the employee." (DEV-06); Approve while active KPI weights ≠ 100 % → "KPI weights must add up to 100%." (DEV-02). Approving with no KPIs is open (D-04-8).
- Show the running weight total under the KPI table (it is what DEV-02 checks) [ASSUMPTION].
- "Per project"/"Monthly" in the example rows are not valid frequencies (D-04-1).
- Toast (HTML): "Plan approved · Lotte notified"; task disappears after the undo window (AC-01, G-03).
- Decision is a selection control, not a button — R-UI-1 placement applies only to Cancel (red outline, left) and Submit decision (green, right).

---

## S4 · Add KPI (pop-up, Manager)

**Wireframe element:** `m-add-kpi` · **Trigger:** "+ Add KPI" on S3 and S9 (FRD-04 §3 Screen 4)

```
┌ Add KPI ─────────────────────────────────────────────── × ┐
│ KPI*               [e.g. Billable utilisation__________]   │
│ Target value*      [e.g. 85]                               │
│ Unit*              [% | Score / 5 | Days | Hours | Count | € ▾] │
│ Direction*         (Higher is better) (Lower is better)    │
│ Measured*          [Quarterly | Half-yearly | Yearly ▾]    │
│                     default = KPI cycle (Settings)         │
│ Weight %*          [30]                                    │
│ Due date*          [31-10-2027]                            │
│ How it is measured [________________________________]      │
│ [Cancel]                                      [Add KPI]    │
└────────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- **Measured:** HTML offers Monthly | Quarterly | Per project | Yearly. Replaced with the FRD/DB list Quarterly | Half-yearly | Yearly, pre-selected from `setting.KPI_CYCLE` (Appendix A #9, D-04-1).
- Unit options are the HTML's; `kpi.unit` is free text ≤ 20, so the list is a UI convenience only.
- Messages: "This field is required." · "Enter a number greater than 0." (target). Weight outside 1–100: no FRD message (D-04-24).
- Saving while the total ≠ 100 % is allowed (DEV-02); the warning shows on S9/S3, not here.
- Toast (HTML): "KPI added · employee notified" — notification is not an FRD rule (D-04-20); toast text to be "KPI added" unless confirmed.
- Same form, pre-filled, for Edit (DV12); Target value read-only once results exist.

---

## S5 · Progress check (Manager)

**Wireframe page:** `p-mg-dev-progress` · **Route:** `/team/:code/development/progress-check` (FRD-04 §3 Screen 5)

```
← Back to case
Progress check · Lotte Jansen                 Development plan 2026–27 · every 14 days
1 · Plan  →  2 · Perform plan  →  3 · Evaluate
┌ KPIs ─────────────────────────────────────────────── [Enter KPI results] ┐
│ KPI                     Target   Current   Progress   Status             │
│ Billable utilisation    ≥ 85%    82%       ███ 82%     …                  │
│ Client satisfaction     ≥ 4.0    4.5       ███ 100%    …                  │
│ Code review turnaround  < 1 day  0.8 d     ███ 100%    …                  │
└───────────────────────────────────────────────────────────────────────────┘
┌ Goals ──────────────────────────────────────────────────── [+ Add activity] ┐
│ Goal                Type      Progress   Status                              │
│ AWS certification   Training  [45 %]     (On track)(At risk)(Behind)(Done)   │
│ Mentor junior       Coaching  [30 %]     (On track)(At risk)(Behind)(Done)   │
│ Discovery workshop  Project   [10 %]     (On track)(At risk)(Behind)(Done)   │
└──────────────────────────────────────────────────────────────────────────────┘
┌ Check-in ────────────────────────────────────────────────────────────────────┐
│ Check-in summary*                                                             │
│ [Good pace on certification. Workshop moved to January.______________]        │
│ Plan status                                                                   │
│ [● Continue        ] [○ Plan performed  ] [○ Skill gap   ]                    │
│   Next check in 14 days  Move to evaluation   Flag to HR                       │
└───────────────────────────────────────────────────────────────────────────────┘
[Cancel]                                                        [Save check-in]
```

**Changes from the HTML / notes:**
- **KPI rows:** the HTML draws "Current" as inputs and a per-KPI status picker (On track/At risk/Behind/Done). Neither has a column or FRD rule (D-04-13). Drawn here read-only (latest `v_kpi_result`, "Progress" = % met); results are entered via "Enter KPI results" → S7. The HTML's "Progress" bar shows 82 % for Billable utilisation (actual 82 vs target 85); if that bar is meant as % met, the FRD formula gives 96 % — the FRD wins, so the redraw shows % met.
- Goal "Done" maps to `progress_status = 'ACHIEVED'`; progress is 0–100 (`checkin_goal.progress_pct`). Per-goal notes (FRD "adds notes") are not drawn — optional `checkin_goal.note` [ASSUMPTION].
- "every 14 days" / "Next check in 14 days" come from `DevelopmentPlan.progressCheckDays` (snapshot of `DEV_PLAN_PROGRESS_CHECK`), never hard-coded (G-08).
- Check-in summary and Plan status required — no FRD message (D-04-15).
- Skill gap: after save, offer "Start PIP" (FRD-06 Screen 1, `/team/:code/pip/new`) and notify HR in-app; what happens to the plan next is open (D-04-16).
- Toast (HTML): "Check-in saved · next 26 Oct" — date computed from save date + interval. Cancel returns to the employee record ("Back to case"), never saves.
- The HTML's Save button label "Save check-in" is kept; it completes the "Progress check" task (undo window).

---

## S6 · Add activity (pop-up, Manager)

**Wireframe element:** `m-add-activity` · **Trigger:** "+ Add activity" on S5 (FRD-04 §3 Screen 6)

```
┌ Add activity ────────────────────────────────────── × ┐
│ Type*         (Training / learning) (Project / assignment) │
│               (Coaching / mentoring)                    │
│ Activity*     [AWS practice exam_______________]         │
│ Date*         [01-12-2026]                               │
│ Linked goal*  [AWS certification ▾]                      │
│ [Cancel]                                        [Add]    │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- Linked goal lists this plan's goals only (development goals, never PIP goals).
- All four fields required per the HTML; FRD gives no message (D-04-15). Toast (HTML): "Activity added".

---

## S7 · KPI results (Manager)

**Wireframe page:** `p-mg-kpi-update` · **Route:** `/team/:code/kpis/results` (FRD-04 §3 Screen 7)

```
← Back to case
KPI results · Lotte Jansen
Enter the actual result for each KPI and period · % met is calculated automatically
┌ Enter results ────────────────────────────────────────────────────────────────────┐
│ KPI                     Target     Direction         Weight  Period              Actual  % met │
│ Billable utilisation    ≥ 85 %     Higher is better  30%     [Q3 2026 (Jul–Sep)▾] [82]    96%   │
│ Client satisfaction     ≥ 4.0 / 5  Higher is better  40%     [Q4 2026 ▾]          [4.5]   100%  │
│ Code review turnaround  ≤ 1.0 day  Lower is better   30%     [Q3 2026 ▾]          [0.8]   100%  │
│                                                       Weighted KPI score   {computed}         │
└────────────────────────────────────────────────────────────────────────────────────┘
Comment for employee
[Utilisation slightly below target because of the Harbor handover week.______]
┌ Previous results ─────────────────────────────────────────────────────┐
│ Period             KPI                     Actual     % met  Entered by          │
│ Q2 2026            Billable utilisation    88 %       100%   Mark Smit · 03 Jul  │
│ Atlas CRM phase 1  Client satisfaction     4.2 / 5    100%   Mark Smit · 02 Apr  │
│ August 2026        Code review turnaround  1.3 days   77%    Mark Smit · 02 Sep  │
└───────────────────────────────────────────────────────────────────────┘
[Cancel]                                                          [Save results]
```

**Changes from the HTML / notes:**
- **Period picker:** the HTML offers project-based ("Harbor Logistics · Oct 2026", "GreenLeaf · Q4 2026") and monthly ("September 2026", "October 2026") periods. Only quarter / half-year / year periods exist for the FRD's frequencies (D-04-1, D-04-12); the redraw shows quarters. Past project/monthly rows in "Previous results" are HTML example data only.
- % met is a computed, read-only cell updated as the actual is typed (DEV-03); the HTML leaves it and the weighted score blank ("—").
- Validation on Save results: "Enter a number." (actual, ≥ 0); "KPI weights must add up to 100%." when active weights ≠ 100 % (DEV-02, AC-04).
- Single "Comment for employee" field → stored on each `kpi_result` saved in this submission [ASSUMPTION, D-04-12].
- A period that already has a result cannot be re-entered; it is corrected (DEV-04). No correction control is drawn — proposal: a "Correct" action per Previous-results row (D-04-22).
- Toast (HTML): "KPI results saved · Lotte can see them in My Development".

---

## S8 · Evaluate development plan (Manager)

**Wireframe page:** `p-mg-dev-eval` · **Route:** `/team/:code/development/evaluate` (FRD-04 §3 Screen 8)

```
← Back to My Tasks
Evaluate development plan · Priya Sharma                              Plan 2025–26
1 · Plan  →  2 · Perform plan  →  3 · Evaluate
┌ Goals ─────────────────────────────────────────────────────────────────┐
│ Goal                 Type      Result                         Evidence        │
│ React Native course  Training  [Met ▾]                        Certificate     │
│ Own MedTrack release Project   [Met ▾]                        Release 2.1     │
│ Tech talk            Coaching  [Partly met ▾]                 Postponed once  │
└──────────────────────────────────────────────────────────────────────────┘
Evaluation*
[Priya grew into release ownership. Next plan: architecture.______________]
Outcome
[● Plan closed      ]   [○ Further needs   ]
   No further needs        Start a new plan
[Cancel]                                                [Complete evaluation]
```

**Changes from the HTML / notes:**
- Result options: Met / Partly met / Not met (`goal.result`). Missing any → "Choose a result for every goal."
- **Evidence** column has no data source (D-04-14) — kept in the drawing only as the HTML shows it; not to be built until decided.
- Evaluation text and Outcome required per the HTML; no FRD message (D-04-15).
- Toast (HTML): "Evaluation completed · plan closed". Plan closed → milestone "Plan closed"; Further needs → employee gets a new "Create development plan" task.

---

## S9 · Employee record – Development & reviews (Manager)

**Wireframe page:** `p-mg-case-lotte` (filled), `p-mg-case-jesse` (empty) — tab "Development & reviews" · **Route:** `/team/:code?tab=development` (FRD-04 §3 Screen 9)

```
← Back to My Team
LJ  Lotte Jansen                                        [Start PIP] [Assign project]
    E-0031 · Senior Developer · Development · Manager: Mark Smit
Overview | Profile | Projects | [Development & reviews]
┌ KPIs set by manager ─────────────────────────── [Enter results] [+ Add KPI] ┐
│ ⚠ KPI weights must add up to 100%.          (only when active total ≠ 100%) │
│ KPI                     Target              Weight  Measured   % met (latest) │
│ Billable utilisation    ≥ 85% per quarter    30%    Quarterly   ███ 96%       │
│ Client satisfaction     Average ≥ 4.0 / 5    40%    Per project ███ 100%      │
│ Code review turnaround  ≤ 1 working day      30%    Monthly     ██  77%       │
└──────────────────────────────────────────────────────────────────────────────┘
┌ Development goals ─────────────────────────────────────────── [Progress check] ┐
│ Goal                                   Type      Target date  Progress          │
│ AWS Solutions Architect certification  Training  30 Jun 2027  ░ 0%              │
│ Mentor a junior developer              Coaching  31 Mar 2027  ░ 0%              │
│ Lead a client discovery workshop       Project   31 Dec 2026  ░ 0%              │
└────────────────────────────────────────────────────────────────────────────────┘
┌ Reviews ──────────────────────────────── (FRD-03 / FRD-05 — not this module) ┐
└────────────────────────────────────────────────────────────────────────────────┘
```

Empty state (`p-mg-case-jesse`):
```
┌ KPIs set by manager ────────────────────────────────────────── [+ Add KPI] ┐
│ No KPIs set                                                                  │
└──────────────────────────────────────────────────────────────────────────────┘
┌ Development goals ──────────────────────────────────────────────────────────┐
│ No development plan                                                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- **Weight warning** is added (FRD §2: "while the weights do not add up to 100% the Development tab shows a warning"); not drawn in the HTML; text reuses the validation message [ASSUMPTION, D-04-27].
- "Enter results" hidden when there are no KPIs (as in `p-mg-case-jesse`). "Progress check" opens S5 only when an open `PLAN_PROGRESS` check exists [ASSUMPTION] — otherwise disabled.
- No Retire control is drawn; proposal: a "×"-style retire action per KPI row (R-UI-1 list-item convention) — D-04-22.
- Overview tab card "Development: Status In approval · 3 goals · 3 KPIs" (HTML) is fed by the same data.
- "Start PIP" / "Assign project" header buttons belong to FRD-06 / FRD-03.
- Replaces FRD-01's D-W1 "Not available yet" placeholder for the KPI and goal sections; HR's version of this tab is read-only with no action buttons (FRD-01 W8).
