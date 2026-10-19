# FRD-06 — Text wireframes

Source: `.claude/docs/source/FRD-06/WF-06_Performance_Improvement_Plan_PIP.html` (v1.2, canonical — read-only, R-SRC-1). All 7 ids drawn below were confirmed by direct read: pages `p-mg-pip-define`, `p-hr-pip-approve`, `p-yt-pip`, `p-mg-pip-checkin`, `p-mg-pip-final`; modals `m-pip-goal`, `m-dispute`. No other page or modal id exists in WF-06. Example names, dates and goal texts are WF-06's own sample data. Button toasts quoted below are WF-06 text (G-11 requires a short confirmation; the FRD gives no wording, so these are the wireframe's, lowest in the order of truth). Layout rules R-UI-1…R-UI-9 apply to every screen and are not repeated per screen.

Every page carries the same step bar under its title: `1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review` (current step highlighted).

---

## S1 · New improvement plan (Manager)

**Wireframe page:** `p-mg-pip-define` · **Route:** `/team/:code/pip/new` (FRD-06 §3 Screen 1)

```
← Back to My Team
New improvement plan                                  Draft · goes to HR for approval
1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review
┌ Plan ─────────────────────────────────────────────────────────────┐
│ Employee*            [Ruben Bakker            ▾]                    │
│ Raised from*         [Project review          ▾]                    │
│                       (Project review / Development plan · skill gap │
│                        / Appraisal)                                  │
│ Proposed start*      [26-10-2026]          Duration  [90 days] (ro)  │
│ Reason*                                                              │
│ [Describe the performance concern with examples_______________]      │
└───────────────────────────────────────────────────────────────────┘
┌ Improvement goals ───────────────────────────────────── [+ Add goal] ┐
│ #  Goal                            Measure                 Support    │
│ 1  Test cases before sprint review 100% of stories by day 8 Weekly    │
│                                                             pairing  ×│
│ 2  Reduce escaped defects          ≤ 2 per release (was 7)  Checklist │
│                                                             + lead  × │
│ 3  Status updates on time          Daily, blockers same day Bi-weekly │
│                                                             1:1     × │
└───────────────────────────────────────────────────────────────────┘
┌ Follow-up ─────────────────────────────────────────────────────────┐
│ Check-in frequency*  [Every 14 days ▾]                               │
└───────────────────────────────────────────────────────────────────┘
[Cancel]                                         [Save draft] [Send to HR]
```

**Changes from the HTML / notes:**
- Toasts (WF-06): Save draft → "Draft saved"; Send to HR → "PIP sent to HR for approval", back to My Team.
- **Duration** is read-only (WF-06 marks it `field-readonly`) and shows the `PIP_DURATION` snapshot, not a fixed "90 days" (G-08).
- **Check-in frequency** shows the current `PIP_CHECKIN` value as its only option, as WF-06 does — whether a manager may ever choose another value is open (spec D-06-6).
- **Raised from**: pre-filled and locked when opened from a project review / progress check / appraisal (source id passed in). WF-06 has no "Direct" option although the FRD allows a PIP "started directly" — spec D-06-5, open.
- **Employee**: own team only, active, not already on a PIP; locked when opened from the employee record's "Start PIP".
- Remove goal: small "×" per row (R-UI-1, not drawn in WF-06). "+ Add goal" sits top right of its section (as drawn).
- **Evidence upload**: FRD §4.1 has `POST /pips/{id}/documents` ("Attach evidence") but WF-06 draws no upload control. Not added here until spec D-06-8 is answered.
- Cancel red outline left; Save draft and Send to HR green right (R-UI-1).

---

## S2 · PIP goal (pop-up)

**Wireframe modal:** `m-pip-goal` · **Trigger:** "+ Add goal" on S1, and on S3 during Amend (FRD-06 §3 Screen 2)

```
┌ PIP goal ───────────────────────────────── × ┐
│ Goal*                 [______________________] │
│ How it is measured*   [______________________] │
│ Support offered*      [______________________] │
│                                                 │
│ [Cancel]                            [Add goal]  │
└─────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- **Support offered is marked required** here; WF-06 draws it without "*". PIP-02 ("at least one goal with a measure and the support offered") and the DB CHECK on `goal.support` require it (FRD > wireframe). Message for a missing support text is open — spec D-06-25.
- Toast (WF-06): "Goal added". The same modal edits an existing goal (title "PIP goal", button "Save") **[ASSUMPTION]** — WF-06 draws only the add case, but PIP04 (`PUT …/goals/{goalId}`) exists and Amend means editing goals.

---

## S3 · Review & approve PIP (HR)

**Wireframe page:** `p-hr-pip-approve` · **Route:** `/pips/:pipId/approve` (FRD-06 §3 Screen 3)

```
← Back to My Tasks
Kees Willems
Performance Improvement Plan · Review & approve
1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review
┌──────────────────────────────────────────────────────────────────┐
│ Drafted by  Anouk Peters · 10 Oct 2026                             │
│ Reason      Missed test coverage on Atlas sprints 4–7; 7 escaped   │
│             defects in release 1.3                                  │
│ Period      19 Oct 2026 – 16 Jan 2027 · 90 days                     │
│ Check-ins   Every 14 days                                           │
│ Source      {Project review / Skill gap / Appraisal / Direct} →     │
│             link to the source evidence + attachments               │
├──────────────────────────────────────────────── [+ Add goal]* ────┤
│ #  Goal                            Measure                 Support  │
│ 1  Test cases before sprint review 100% of stories by day 8 Weekly  │
│                                                             pairing │
│ 2  Reduce escaped defects          ≤ 2 per release (was 7)  …       │
│ 3  Status updates on time          Daily, blockers same day …       │
├────────────────────────────────────────────────────────────────────┤
│ New start date*   [__-__-____]   (only when the proposed start has  │
│                                   passed)                            │
│ HR comments                                                         │
│ [Add baseline numbers per release.___________________________]      │
│ ☑ Goals are specific and measurable                                 │
│ ☑ Support is documented                                             │
│ ☑ Informal conversation held                                        │
│ Decision                                                            │
│ [● Approve & send notice] [○ Amend]        [○ Reject]               │
│   E-mail + task to employee  Edit goals first  Back to manager       │
└────────────────────────────────────────────────────────────────────┘
[Cancel]                                              [Submit decision]
```
`*` "+ Add goal", and edit/× on goal rows, appear only while **Amend** is selected.

**Changes from the HTML / notes:**
- Toast (WF-06): "PIP approved · notice e-mailed to Kees Willems", back to My Tasks. **[ASSUMPTION]** Amend uses the same toast. No source gives a toast for Reject — **[OPEN]**, agree the wording together with spec D-06-10 (G-11 requires one).
- **New start date** is not drawn in WF-06 but the FRD flow requires it ("sets a new start date if the proposed one has passed"; message "The start date has passed. Choose a new start date."). Shown only when `proposed_start_date` < today.
- **Source** row is not drawn in WF-06; FRD §4.2's GET returns "Draft, goals and source evidence" (`review`, `checkin`, `document`), so HR needs to see it ("reviews the draft and the evidence").
- After a dispute (second review), the page also shows the employee's dispute reason and attachments read-only above the decision **[ASSUMPTION]** — HR "reviews the plan once more", so they need the dispute text.
- Decision control: segmented as drawn — Approve green, Amend neutral, Reject red. Submit decision green right; Cancel red outline left (R-UI-1).
- Comment is required for Amend and Reject ("Add a comment for the manager."); all three ticks required for Approve/Amend ("Confirm all checks before approving.").
- HR-only; refused (403) for the HR user's own PIP (G-13).

---

## S4 · PIP notice – acknowledge or dispute (Employee)

**Wireframe page:** `p-yt-pip` · **Route:** `/me/pip` (FRD-06 §3 Screen 4)

```
← Back to My Tasks
Performance Improvement Plan
Sent 09 Oct 2026 by Emma de Vries · respond by 14 Oct
1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review
┌──────────────────────────────────────────────────────────────────┐
│ Manager     Mark Smit                                              │
│ Period      15 Oct 2026 – 13 Jan 2027                              │
│ Check-ins   Every 14 days                                          │
│ Reason      Missed release deadlines and late escalation on        │
│             Harbor Logistics Portal                                │
├────────────────────────────────────────────────────────────────────┤
│ #  Goal                            Measure                 Support  │
│ 1  Test cases before sprint review 100% of stories by day 8 Weekly  │
│                                                             pairing │
│ 2  Reduce escaped defects          ≤ 2 per release (was 7)  …       │
│ 3  Status updates on time          Daily, blockers same day …       │
├────────────────────────────────────────────────────────────────────┤
│ Your comments                                                       │
│ [____________________________________________________________]     │
│ ☐ I have read this plan and understand the goals and support offered│
└────────────────────────────────────────────────────────────────────┘
[Dispute]                                                 [Acknowledge]
```

**Changes from the HTML / notes:**
- Toast (WF-06): Acknowledge → "Acknowledged · PIP starts 15 Oct" (date = `start_date`), to the dashboard.
- **Dispute** red outline left (as drawn, class `btn-cancel`); **hidden** once the PIP has been disputed (PIP-09, AC "Dispute only once"). Acknowledge green right, enabled only when the tick is set ("Confirm that you have read the plan.").
- **"Your comments"** is drawn but has no column in `pip` — not built until spec D-06-7 is answered.
- After acknowledgement the page stays reachable read-only and lists the check-in notes (FRD §4.3 "Plan, goals, check-in notes"; PIP-08), each linking to S6 where the employee can add a comment **[ASSUMPTION]** on layout — WF-06 draws only the notice state.
- Not reachable at all (404, nothing in menus, tasks or notifications) before the notice is sent (PIP-01).
- "respond by" = the response task's due date (spec D-06-12; WF-06 example is 5 days after sending).

---

## S5 · Dispute improvement plan (pop-up)

**Wireframe modal:** `m-dispute` · **Trigger:** "Dispute" on S4 (FRD-06 §3 Screen 5)

```
┌ Dispute improvement plan ──────────────────────── × ┐
│ Which points do you disagree with and why?*           │
│ [__________________________________________________] │
│ [__________________________________________________] │
│                                                        │
│ ┌ - - - - - - - - - - - - - - - - - - - - - - - - - ┐ │
│   Attach supporting documents, or browse file          │
│ └ - - - - - - - - - - - - - - - - - - - - - - - - - ┘ │
│                                                        │
│ [Cancel]                                [Send to HR]   │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- Toast (WF-06): "Dispute sent to HR", to the dashboard.
- Reason required: "Explain which points you disagree with." Attachments optional, stored as `DISPUTE_ATTACHMENT` (multipart, same request).
- Sends the "PIP disputed" e-mail to HR and the manager; HR gets "Approve PIP" again.

---

## S6 · PIP check-in

**Wireframe page:** `p-mg-pip-checkin` · **Route:** `/pips/:pipId/checkins/:checkinId` (FRD-06 §3 Screen 6)

```
← Back to My Tasks
PIP check-in #6 · Daan Visser
Day 84 of 90 · final review 18 Oct 2026
1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review
┌ Check-in ───────────────────────────────────────┐  ┌ History ──────────┐
│ Goal                       Progress   Status      │  │ #5 · 28 Sep        │
│ Test cases before review   [ 90]%    [On track ▾] │  │   2 goals on track │
│ Escaped defects ≤ 2        [ 60]%    [At risk  ▾] │  │ #4 · 14 Sep        │
│ Status updates on time     [100]%    [Achieved ▾] │  │   Improving        │
│                                                    │  │ #3 · 31 Aug        │
│ Check-in notes*                                    │  │   At risk          │
│ [Clear improvement on test cases. Release 1.5 had  │  │ #2 · 17 Aug        │
│  3 bugs._________________________________________] │  │   Started          │
│                                                    │  │ #1 · 03 Aug        │
│ Employee comments                                  │  │   Kick-off         │
│ [Will add regression suite for payments.________]  │  └────────────────────┘
└────────────────────────────────────────────────────┘
[Cancel]                                        [Save check-in]
```

**Changes from the HTML / notes:**
- **Progress** is drawn as static % text in WF-06; shown here as a 0–100 input (spec D-06-15, [ASSUMPTION]). **Status** options: On track / At risk / Behind / Achieved (DB enum); WF-06's selects omit "Behind".
- **Employee comments** is *written by the employee*, not the manager: for the manager and HR it is read-only; for the employee (own PIP) the notes/progress are read-only and the comments box plus a "Save comment" button (green, right) are the only inputs **[ASSUMPTION]** on button label. HR sees everything read-only (PIP-08).
- History lines ("2 goals on track", "Improving", "Kick-off") are WF-06 sample summaries; no column stores them — render a derived summary (e.g. count of goals per status) **[ASSUMPTION]**, each row linking to that check-in.
- "Day 84 of 90 · final review 18 Oct 2026" = days since `start_date` / `duration_days` and the final review date (including any extension).
- Save check-in green right; Cancel red outline left. Toast not given by WF-06 — standard G-11 confirmation.

---

## S7 · Final PIP review (manager proposal, HR sign-off)

**Wireframe page:** `p-mg-pip-final` · **Route:** `/pips/:pipId/final-review` (FRD-06 §3 Screen 7)

**Manager view (as drawn in WF-06):**
```
← Back to My Tasks
Final PIP review · Daan Visser
PIP 20 Jul – 18 Oct 2026
1 · Define goals → 2 · HR approval → 3 · Notice → 4 · Check-ins → 5 · Final review
┌ Final review ─────────────────────────────────────┐  ┌ Summary ───────────┐
│ Goal                       Result                   │  │ Started  20 Jul 2026│
│ Test cases before review   [Met          ▾]         │  │ Deadline 18 Oct 2026│
│ Escaped defects ≤ 2        [Partly met   ▾]         │  │ Check-ins  6 of 6   │
│ Status updates on time     [Met          ▾]         │  │ Extensions 0 of 1   │
│                                                      │  │ Disputes   None     │
│ Final assessment*                                    │  └─────────────────────┘
│ [Two goals met; defects trending down (7 → 3 → 2).] │
│ Outcome                                              │
│ [● PIP passed] [○ Extend 30 days] [○ PIP not met]    │
│   Back to active   One extension    Start            │
│   employment       only             offboarding      │
│ Manager sign-off  Mark Smit (read-only)              │
└──────────────────────────────────────────────────────┘
[Cancel]                                       [Confirm outcome]
```

**HR view — not drawn in WF-06 (FRD-00 Appendix A #5; spec D-06-1, [OPEN]):**
```
← Back to My Tasks
Final PIP review · Daan Visser                         Confirm PIP outcome
PIP 20 Jul – 18 Oct 2026
┌ Final review (read-only) ──────────────────────────┐  ┌ Summary ───────────┐
│ Goal                       Result                    │  │ (as manager view)  │
│ Test cases before review   Met                       │  └─────────────────────┘
│ Escaped defects ≤ 2        Partly met                │
│ Status updates on time     Met                       │
│ Final assessment  "Two goals met; defects trending   │
│                    down (7 → 3 → 2)."                │
│ Proposed outcome  PIP passed                         │
│ Manager sign-off  Mark Smit · {date}                 │
│ HR sign-off*      Emma de Vries (you)                │
└──────────────────────────────────────────────────────┘
[Cancel]                                       [Confirm outcome]
```

**Changes from the HTML / notes:**
- WF-06 draws both sign-off fields as text inputs on the manager's page ("Manager sign-off" read-only "Mark Smit"; "HR sign-off *" placeholder "Emma de Vries") and the manager's toast is "Outcome sent to HR for sign-off". Signatures are recorded as the logged-in users (`checkin.done_by`, `checkin.hr_signoff_by`), not typed names (spec D-06-24). In the manager view the HR sign-off line is shown as "Awaiting HR" or omitted.
- HR view: same route, HR action bar — the FRD's own route table gives both the manager task "Final PIP review" and the HR task "Confirm PIP outcome" this route, which matches the Appendix A #5 option "HR uses the same page with an HR action bar". Not confirmed — spec D-06-1. HR can only confirm (spec D-06-13); "HR must confirm the outcome." if submitted without sign-off.
- **Extend 30 days** is not offered after one extension (PIP-05, AC "Only one extension"; message "This PIP has already been extended once.") and, **[ASSUMPTION]**, not when `PIP_EXTENSION` is switched off; the label shows the snapshot `extension_days` (spec D-06-14).
- Result options per goal: Met / Partly met / Not met (`checkin_goal.result`).
- Outcome control colours as drawn: PIP passed green, Extend neutral, PIP not met red. Confirm outcome green right; Cancel red outline left.
- Summary: "Extensions n of 1", "Disputes None/Once" from `v_pip`; "Deadline" includes an extension.
