# FRD-07 — Text wireframes

Source: `.claude/docs/source/FRD-07/WF-07_Contract_End_Offboarding_and_Assets.html` (v1.2, canonical — read-only, R-SRC-1). Page ids confirmed by direct read: `p-hr-contract`, `p-em-profile`, `p-hr-case-lotte`, `p-hr-off`, `p-it-revoke`, `p-it-assets`, modals `m-resign`, `m-exit`, `m-exit-interview`, `m-add-asset`, `m-asset-view`. The file's own screen bar marks S4 "Confirm exit (HR) (not drawn)". Example names/dates below are the wireframe's own. R-SRC-1 rank: FRD v1.2 and `spec.md` win on conflict. Layout rules R-UI-1…R-UI-9 apply to every screen and are not repeated per screen.

**Host pages already drawn elsewhere — not redrawn here:** My Profile (`p-em-profile`) → `F01-onboarding/wireframes.md` §S10 / FRD-01; employee record (`p-hr-case-*`) → `F01-onboarding/wireframes.md` §S11 and `FRD-01-shared-workspace/wireframes.md` (tab bar and header actions). Only the buttons this feature adds to them are shown.

---

## S1 · Contract renewal decision (HR)

**Wireframe page:** `p-hr-contract` · **Route:** `/contracts/:contractId/renewal` (HR task "Contract renewal decision")

```
← Back to My Tasks
Priya Sharma
Contract renewal decision · fixed-term ends 30 Nov 2026
┌──────────────────────────────────────────────────────────────┐
│ Current contract  Fixed-term · 01 Dec 2025 – 30 Nov 2026      │
│ Manager           Mark Smit                                   │
│ Manager advice    Renew — strong performer        ← [OPEN]    │
│                                                               │
│ Decision                                                      │
│ ┌ Renew fixed-term ┐ ┌ Convert to permanent ┐ ┌ Do not renew ──┐│
│ │ New end date     │ │ No end date          │ │ Starts         ││
│ └──────────────────┘ └──────────────────────┘ │ offboarding    ││
│                                               └────────────────┘│
│ New start date*  [01-12-2026]    New end date*  [30-11-2027]   │
│ Notes                                                          │
│ [__________________________________________________________]   │
└──────────────────────────────────────────────────────────────┘
[Cancel]                                                 [Confirm]
```

**Changes from the HTML / notes:**
- "Manager advice" is drawn but has no FRD input or column (spec D-07-7) — omit until resolved.
- New start/end date fields show only when "Renew fixed-term" is selected (DB: dates only for RENEW). No decision → "Choose a decision."; end date not after the current end → "The new end date must be after the current end date."
- Confirm completes the task through the undo window (O02, `202 {undoUntil}`). Wireframe toast for Renew: "Contract renewed until 30 Nov 2027".
- "Do not renew" starts an `END_OF_CONTRACT` exit; its dates are spec D-07-5.

---

## S2 · Submit resignation (pop-up, Employee)

**Wireframe element:** `m-resign` on `p-em-profile` · **Route:** modal on `/me/profile` (built as `/my-profile`, spec D-07-14)

Host page addition (My Profile header): `[Submit resignation]` (red, `btn-danger`).

```
┌ Submit resignation ─────────────────────────────── × ┐
│ Notice date*                Requested last working day*│
│ [12-10-2026]                [30-11-2026]               │
│ Message to HR                                          │
│ [____________________________________________________] │
│ ┌ Attach signed letter, or browse file ─────────────┐  │
│ └───────────────────────────────────────────────────┘  │
│ [Cancel]                          [Submit resignation] │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- Requested last day not after notice date → "The last working day must be after the notice date."; empty required field → "This field is required.".
- Toast (wireframe): "Resignation sent to HR and your manager" (matches `RESIGNATION_RECEIVED` recipients).
- Hidden once a live exit exists (`ux_exit_active`) and on archived records **[ASSUMPTION]**.

---

## S3 · Initiate exit (pop-up, HR / Manager)

**Wireframe element:** `m-exit` on `p-hr-case-lotte` · **Route:** modal on `/employees/:code` (HR) and `/team/:code` (Manager)

Host page addition (record header): `[Initiate exit]` (red, `btn-danger`) next to `[Transfer manager]`; hidden when the employee is already offboarding (FRD-01 §2).

```
┌ Initiate exit ─────────────────────────────────────── × ┐
│ ┌ Resignation ─────┐ ┌ End of contract ┐ ┌ Termination ──────┐│
│ │ Employee resigned│ │ Not renewed     │ │ Incl. PIP /       ││
│ └──────────────────┘ └─────────────────┘ │ probation         ││
│                                          └───────────────────┘│
│ Notice received*          Last working day*                  │
│ [12-10-2026]              [30-11-2026]                       │
│ Exit interview*           Interview date                     │
│ [Schedule with HR ▾]      [26-11-2026]                       │
│ Notes                                                         │
│ [___________________________________________________________] │
│ [Cancel]                                  [Start offboarding] │
└───────────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- The HTML puts the button only on the HR record (`p-hr-case-lotte`). FRD Appendix A #7: add it to `p-mg-case-*` too (spec D-07-2). For a manager the button label stays "Start offboarding" in the HTML, but a manager's exit only becomes a **request** waiting for HR (Screen 4) — the toast should not claim offboarding started **[ASSUMPTION]**; wording TBD with D-07-2.
- Last working day is prefilled from O04 (notice date + notice period for the contract type) and stays editable.
- Exit interview options map to `interview_needed` (Schedule with HR = true, Not needed = false); Interview date → `interview_planned_on`.
- "Termination — Incl. PIP / probation" is the wireframe's caption; the schema keeps `PIP_NOT_MET` and `PROBATION_NOT_PASSED` as separate, system-created types. The modal offers only the three manual types.
- HR toast (wireframe): "Offboarding started · employee, manager and IT notified" — recipients conflict with the seeded template, spec D-07-17.

---

## S4 · Confirm exit (HR)

**Route:** `/exits/:exitId/confirm` (HR task "Confirm exit")

**Not drawn in the wireframe — and FRD-07 v1.2 itself says so** ("Screen 4 · not drawn"; WF-07's screen bar: "S4 · Confirm exit (HR) (not drawn)"), tracked as Appendix A #5: "HR approval of "End employment", HR "Confirm exit" and HR PIP sign-off have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." Built here as a minimal placeholder from the FRD text alone ("HR confirms the type, notice date, last working day and exit interview date. An exit started by a manager needs HR approval.") — **[OPEN] spec D-07-1, confirm before implementing.**

```
← Back to My Tasks
Confirm exit · {employee name}          Requested by {name} ({role}) · {date}
┌ Request (read-only) ──────────────────────────────────────┐
│ Requested last working day  {date}      (resignation only) │
│ Message                     "{text}"     (resignation only) │
│ Signed letter               {file} [Download] (if attached) │
└────────────────────────────────────────────────────────────┘
┌ Exit details ─────────────────────────────────────────────┐
│ Type*            [Resignation ▾]                            │
│ Notice date*     [dd-mm-yyyy]    Last working day* [dd-mm-yyyy]│
│ Exit interview   [Schedule with HR ▾]  Interview date [dd-mm-yyyy]│
└────────────────────────────────────────────────────────────┘
[Reject]                                              [Confirm]
```

- **[ASSUMPTION]** button placement per R-UI-1: Confirm green/right; Reject red outline/left, shown only for a manager-started exit (FRD 4.3 "HR rejects a manager-started exit"; resignations: spec D-07-4).
- A read endpoint for this page is not in the FRD's endpoint list (spec §6, D-07-1).

---

## S5 · Offboarding checklist (HR)

**Wireframe page:** `p-hr-off` · **Route:** `/employees/:code/offboarding` (HR task; employee record header)

```
← Back to case
Thomas Mulder
Offboarding · last working day 24 Oct 2026
┌──────────────────────────────────────────────────────────────────┐
│ ✔ Onboarding      ✔ Active employment    ○ PIP        ● Offboarding│
│   Completed 2021    Closed 26 Sep          Not used     Since 26 Sep│
└──────────────────────────────────────────────────────────────────┘
┌ Return assets ───────────────────────────────────────────────────┐
│ Asset                  Asset no.   Issued       Returned Condition  Status    │
│ Laptop · MacBook Pro 16" CW-LT-0102 04 Jan 2021 [ ]  [— ▾]   Due 24 Oct│
│ Phone · iPhone 13      CW-PH-0087  10 Mar 2022  [ ]   [— ▾]   Due 24 Oct│
│ Access badge           CW-BD-0201  04 Jan 2021  [ ]   [— ▾]   Due 24 Oct│
└──────────────────────────────────────────────────────────────────┘
┌ Remove access ──────────────────── IT admin · scheduled 24 Oct ┐
│ [ ] Microsoft 365 / e-mail*       IT admin   24 Oct   Open      │
│ [ ] Slack*                        IT admin   24 Oct   Open      │
│ [ ] VPN*                          IT admin   24 Oct   Open      │
│ [ ] AWS console*                  IT admin   24 Oct   Open      │
└─────────────────────────────────────────────────────────────────┘
┌ HR & handover ───────────────────────────────── HR / Manager ┐
│ [✔] Resignation confirmed in writing*   HR        26 Sep  Done │
│ [✔] Handover plan agreed*               Manager   10 Oct  Done │
│ [ ] Exit interview*  Open form          HR        22 Oct  Open │
│ [ ] Project assignment ended*           System    24 Oct  Open │
│ [ ] Final documents sent*               HR        24 Oct  Open │
└───────────────────────────────────────────────────────────────┘
┌ Reassign team and projects ─────────── (only when a manager) ┐
│ Direct reports still to move   {n}   [Transfer manager]        │
│ Open projects as PM            {n}   [Change project manager]  │
└───────────────────────────────────────────────────────────────┘
[Mark asset as lost]                    [Save] [Archive & close case]
```

**Changes from the HTML / notes:**
- **Reassign team and projects** is not drawn (the example leaver is not a manager); added from FRD §2 and OFF-08, shown only when `Employee.isManager`. The two links open the existing Transfer manager (FRD-01, built) and Change project manager (FRD-03, F04) flows; exact layout **[ASSUMPTION]**.
- Section heading: HTML says "Revoke access", FRD says "Remove access" (FRD wins, R-SRC-1). The HTML's "18:00" schedule time has no source (spec D-07-9) — show the date only.
- Condition column: the HTML shows "—" with no selector; a Good/Damaged selector is added (same as S7), because "IT admin or HR marks each item returned with its condition" and returned-without-condition → "Choose the condition of the returned asset.".
- "Mark asset as lost" (orange outline, left — the HTML's `btn-archive` class) acts on the selected asset row **[ASSUMPTION]**; the HTML gives no row selection.
- "Archive & close case" is green/right and **disabled** until OFF-03 is met (HTML: `disabled`); server refusal message "Return or mark all assets, remove all access, reassign team and projects and complete all items first.".
- "Project assignment ended" (owner System) is ticked by the end-of-day job, not by HR (spec D-07-15).
- "Final documents sent" — whether ticking requires a FINAL_DOCUMENT upload (O11) is not stated **[OPEN]**; the HTML has no upload control on this page.
- Read-only for everyone once archived (G-10).

---

## S6 · Exit interview (pop-up, HR)

**Wireframe element:** `m-exit-interview` · **Route:** modal on S5 ("Open form")

```
┌ Exit interview · Thomas Mulder ──────────────────── × ┐
│ Date*               Held by*                           │
│ [22-10-2026]        [Emma de Vries ▾]                  │
│ Main reason for leaving*                               │
│ [Relocation | Career growth | Compensation | Other]    │
│ Overall experience at CubeWorks                        │
│   1   2   3   4   5                                    │
│   Below    Meets    Exceeds                            │
│ What could we improve?                                 │
│ [____________________________________________________] │
│ Would you recommend CubeWorks?                         │
│ [____________________________________________________] │
│ [Cancel]                                        [Save] │
└────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- No reason → "Choose the main reason for leaving.". Toast (wireframe): "Exit interview saved".
- Whether saving also ticks the "Exit interview" checklist item (`item_kind EXIT_INTERVIEW`) is not stated — **[ASSUMPTION]** yes, once Date/Held by/Reason are saved.

---

## S7 · Remove access & collect assets (IT admin)

**Wireframe page:** `p-it-revoke` · **Route:** `/it/offboarding/:code` (IT task "Remove access")

```
← Back to My Tasks
Revoke access · Thomas Mulder
Tech Lead · last working day 24 Oct 2026
┌ Accounts ───────────────────────────── Scheduled 24 Oct ┐
│ [ ] Microsoft 365 / e-mail*     IT admin   24 Oct   Open  │
│ [ ] Slack*                      IT admin   24 Oct   Open  │
│ [ ] VPN*                        IT admin   24 Oct   Open  │
│ [ ] AWS console*                IT admin   24 Oct   Open  │
│ [ ] GitHub*                     IT admin   24 Oct   Open  │
└───────────────────────────────────────────────────────────┘
┌ Collect assets ──────────────────────────────────────────┐
│ Asset                     Asset no.    Returned  Condition │
│ Laptop · MacBook Pro 16"  CW-LT-0102   [ ]       [— ▾]     │
│ Phone · iPhone 13         CW-PH-0087   [ ]       [— ▾]     │
│ Access badge              CW-BD-0201   [ ]       [— ▾]     │
└───────────────────────────────────────────────────────────┘
[Cancel]                                     [Save] [Mark done]
```

**Changes from the HTML / notes:**
- Title: the FRD task is "Remove access" (R-CM-3 key); the HTML page title "Revoke access" is kept only as display text if the user prefers **[ASSUMPTION]** — default to the FRD name.
- The HTML lists 5 accounts here but 4 on `p-hr-off` for the same person (GitHub missing there) — wireframe inconsistency; both pages show the same `v_account_access` rows.
- Condition options: — / Good / Damaged for every asset (the HTML omits "Damaged" for the access badge; the DB allows both for any asset).
- "Mark done" completes the task; no FRD endpoint exists for it (spec D-07-9). Toast (wireframe): "Access revoked · assets updated".
- Lost assets are marked from S5 or S10 (the HTML has no lost action here).

---

## S8 · Assets (asset register, IT admin)

**Wireframe page:** `p-it-assets` · **Route:** `/assets` (menu, IT admin)

```
Assets
86 assets                                                    [+ Add asset]
Status [All ▾]  Type [All ▾]          [Search asset no., employee…] [⤓]
┌──────────────────────────────────────────────────────────────────────┐
│ Asset no.  │ Type / model             │ Issued to      │ Issued      │ Status             │ Action │
│ CW-LT-0251 │ Laptop · MacBook Pro 14" │ JW Jesse Willems│ 12 Oct 2026 │ Issued             │ [View] │
│ CW-LT-0244 │ Laptop · MacBook Pro 14" │ SB Sanne de Boer│ 02 Oct 2026 │ Issued             │ [View] │
│ CW-PH-0087 │ Phone · iPhone 13        │ TM Thomas Mulder│ 10 Mar 2022 │ Return due 24 Oct  │ [View] │
│ CW-LT-0231 │ Laptop · MacBook Pro 14" │ LJ Lotte Jansen │ 01 Feb 2024 │ Issued             │ [View] │
│ CW-PH-0061 │ Phone · Pixel 7          │ KV Kim Verhoeven│ 05 May 2023 │ Not returned       │ [View] │
│ CW-LT-0099 │ Laptop · Dell XPS 13     │ — In storage    │ —           │ Returned           │ [View] │
└──────────────────────────────────────────────────────────────────────┘
Showing 1 to 6 of 86                                   ‹ 1 2 3 … 9 ›
```

**Changes from the HTML / notes:**
- Status filter: All / Issued / Return due / Not returned / Returned (HTML). The DB also has `IN_STORAGE`; an "In storage" option is **[OPEN]** (spec D-07-11).
- Type filter: the HTML offers Laptop/Phone only; the real filter lists all 8 `asset_type` values **[ASSUMPTION]**, matching the DB CHECK.
- Status badge colours (HTML): Issued = info, Return due = warning, Not returned = error, Returned = success.
- Every column sortable; export downloads Excel (O17, R-UI-2).

---

## S9 · Add equipment (pop-up, IT admin)

**Wireframe element:** `m-add-asset` · **Route:** modal on S8 ("+ Add asset")

```
┌ Add equipment ─────────────────────────────────── × ┐
│ Type*                     Model*                     │
│ [Laptop ▾]                [______________________]   │
│ Asset no.*                Serial number              │
│ [CW-__________]           [______________________]   │
│ Issued to*                Issue date*                │
│ [Jesse Willems ▾ / In storage]  [12-10-2026]         │
│ [Cancel]                                      [Save] │
└──────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- Type options: HTML has Laptop / Monitor / Phone / Access badge / Other; the DB has 8 types (+ Docking station, Headset, Keyboard & mouse) — spec D-07-11.
- Duplicate number → "This asset number is already in use." (`409 ASSET_NUMBER_IN_USE`, same as F01's A21).
- "Issued to = In storage" → `IN_STORAGE`, no employee; Issue date then conflicts with the DB's "NULL while IN_STORAGE" — spec D-07-11.
- Toast (wireframe): "Asset saved".

---

## S10 · Asset detail and history (pop-up, IT admin)

**Wireframe element:** `m-asset-view` · **Route:** modal on S8 ("View")

```
┌ CW-PH-0087 · iPhone 13 ───────────────────────────── × ┐
│ Type       Phone                                        │
│ Serial     F2LX…                                        │
│ Issued to  Thomas Mulder                                │
│ Issued     10 Mar 2022                                  │
│ Status     Return due 24 Oct                            │
│ History                                                 │
│  • Issued to Thomas Mulder                              │
│    10 Mar 2022 · Kevin Hendriks                         │
│  • Return requested                                     │
│    26 Sep 2026 · offboarding                            │
│ [Mark as lost]                    [Close] [Mark returned]│
└─────────────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- History comes from `audit_log` (`entity_type 'ASSET'`, O20).
- "Mark returned" requires a condition, but the HTML has no condition field — a Good/Damaged choice is needed before it can call O14 (spec D-07-11, **[OPEN]**).
- "Mark as lost" is orange outline/left (HTML `btn-archive`); "Mark returned" green/right; both hidden for assets not in `ISSUED`/`RETURN_DUE` **[ASSUMPTION]**. Toasts (wireframe): "Marked as lost", "Marked as returned".
