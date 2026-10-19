# FRD-05 — Text wireframes

Source: `.claude/docs/source/FRD-05/WF-05_Appraisal_and_Promotion.html` (v1.2, canonical — read-only, R-SRC-1). All 10 page ids cited below were confirmed present in that file by direct read (`p-hr-apr`, `p-mg-apr`, `p-hr-apr-open`, `p-em-apr-self`, `p-em-peer`, `m-decline-peer`, `p-mg-peer-approve`, `p-mg-apr-review`, `p-hr-apr-final`, `p-em-reviews`). Routes are FRD-05 v1.2 §3 "Screens" verbatim. Names and numbers in `{…}` are data; sample values from the HTML are kept where they help read the layout.

Common to every screen: top bar per R-UI-3; button placement per R-UI-1 (Cancel red outline left, positive actions green right); dates dd-mm-yyyy (G-12; the HTML often shows "20 Oct" — rendered as dd-mm-yyyy in the build); every table sortable / filterable / searchable with an export icon (R-UI-2); every save shows a toast (G-11).

Rating questions on S4, S5, S8 and S9 are **rendered from the round's appraisal form** (`form_section` by `by_self` / `by_peer` / `by_manager`, then `form_question`), not fixed — the questions drawn below are the HTML's example content (spec §5, D-05-8).

---

## S1 · Appraisal round tracking (HR, all)

**Wireframe page:** `p-hr-apr` · **Route:** `/appraisals` (menu "Appraisals")

```
{Appraisal 2026}                                    [Close round]  [+ New Round]
Open · {01-10-2026 – 31-10-2026} · {44} employees
┌ Self reviews ┐ ┌ Peer reviews ┐ ┌ Manager reviews ┐ ┌ Finalised ──┐
│ 40 / 44  91% │ │ 71 / 120 59% │ │ 22 / 44   50%   │ │ 9 / 44      │
└──────────────┘ └──────────────┘ └─────────────────┘ │ 2 promotions│
                                                      └─────────────┘
Result [All ▾ | To finalise | In progress | Promoted]   [Search employees… ] [⤓]
┌──────────────────┬──────┬───────┬─────────┬─────────────────────┬─────────────┬──────────┐
│ Employee         │ Self │ Peers │ Manager │ Client feedback     │ Result      │ Action   │
├──────────────────┼──────┼───────┼─────────┼─────────────────────┼─────────────┼──────────┤
│ LJ Lotte Jansen  │ Open │ 0 / 2 │ Waiting │ ★★★★★ 4.7 · 3 rev.  │ In progress │ [Remind] │
│    Mark Smit     │      │       │         │                     │             │          │
│ RB Ruben Bakker  │ Done │ 3 / 3 │ Done    │ ★★★★★ 4.8 · 2 rev.  │ To finalise │[Finalise]│
│ KW Kees Willems  │ Open │ 1 / 3 │ Waiting │ —                   │ In progress │ [Remind] │
└──────────────────┴──────┴───────┴─────────┴─────────────────────┴─────────────┴──────────┘
Showing 1 to 5 of 44                                         ‹ 1 2 3 … 9 ›
```

**Changes from the HTML / notes:**
- Close round with unfinalised appraisals shows **"All appraisals must be finalised before closing the round."** (FRD validation table), not the HTML toast "All appraisals must be finalised first" (spec D-05-22). Button kept orange outline as drawn.
- "Finalise" opens S9 (`/appraisals/:appraisalId/finalise`); "Remind" calls AP06 and toasts "Reminder sent to {name}" / "Reminder sent to peers" (recipient = owner of the pending step, spec §7 [ASSUMPTION]).
- A "Pending / Overdue" marker is required by FRD-05 §1 and §4.1 but not drawn — shown in the Result column for appraisals not finalised by the round end date (date column: spec D-05-17).
- The header date range is the round window, which the HTML does not tie to a column — **[ASSUMPTION]** `started_at` → `hr_due`.
- Client feedback column: average · count, per FRD flow step 5 wording ("stars, average score and number of reviews").
- When no round is OPEN, the page shows only "+ New Round" (AP04 returns 404) **[ASSUMPTION]** — not drawn.

---

## S2 · Appraisal tracking (Manager, own team)

**Wireframe page:** `p-mg-apr` · **Route:** `/appraisals` (menu "Appraisals")

```
{Appraisal 2026}
My team · closes {31-10-2026}
┌ Self reviews ┐ ┌ Peer reviews ┐ ┌ My reviews ──┐
│ 6 / 8        │ │ 14 / 22      │ │ 3 / 8        │
│ Due 20-10    │ │ Due 20-10    │ │ Due 25-10    │
└──────────────┘ └──────────────┘ └──────────────┘
My review [All ▾ | To write | Waiting | Submitted]     [Search team… ] [⤓]
┌──────────────────┬──────┬───────────────────┬───────────┬────────────────┐
│ Employee         │ Self │ Peers             │ My review │ Action         │
├──────────────────┼──────┼───────────────────┼───────────┼────────────────┤
│ LJ Lotte Jansen  │ Open │ 0 / 2 · requested │ Waiting   │ [Change peers] │
│ PS Priya Sharma  │ Done │ 2 / 2             │ To write  │ [Write review]  │
│ RB Ruben Bakker  │ Done │ 3 / 3             │ Submitted │ [View]         │
│ DV Daan Visser   │ Done │ 2 / 3             │ Waiting   │ [Remind]       │
└──────────────────┴──────┴───────────────────┴───────────┴────────────────┘
```

**Changes from the HTML / notes:**
- Export icon added (R-UI-2; not drawn) — served by AP05, own team only (spec §5 [ASSUMPTION]).
- Rows are WS-02 scoped server-side (AP04), never filtered only in the UI (R-UI-7).
- "Write review" / "View" → S8; "Change peers" → S7; "Remind" → AP06 for own-team rows only.
- "closes {date}" — **[ASSUMPTION]** `hr_due`, same as FRD-01's W1 tile.

---

## S3 · New appraisal round (HR)

**Wireframe page:** `p-hr-apr-open` · **Route:** `/appraisals/rounds/new` ("+ New Round" on S1)

```
← Back to Appraisals
New appraisal round
┌ Round ─────────────────────────────────────────────────────┐
│ Round name *              [Appraisal 2027               ]  │
│ Review period *           [01-11-2026] → [31-10-2027]      │
│ Review form *             [Standard appraisal form    ▾]   │
│ Self & peer reviews due * [20-10-2027]                     │
│ Manager reviews due *     [25-10-2027]                     │
│ HR finalise by *          [31-10-2027]                     │
└────────────────────────────────────────────────────────────┘
┌ Included employees ────────────────────────────────────────┐
│ ☑ All active employees ({48})                              │
│ ☑ Exclude joiners after [01-08-2027]                       │
│ ☑ Exclude employees in offboarding                         │
└────────────────────────────────────────────────────────────┘
┌ Peer reviews ──────────────────────────────────────────────┐
│ Peer reviews *    [Required · 2–3 peers ▾ | Optional | Off]│
│ Peer anonymity *  [Anonymous to employee ▾ | Visible]      │
└────────────────────────────────────────────────────────────┘
[Cancel]                                [Save draft] [Start round]
```

**Changes from the HTML / notes:**
- Review period drawn as one text box "01-11-2026 → 31-10-2027"; built as two date inputs (two columns `review_period_start` / `review_period_end`).
- "Review form" is one select; the schema has `standard_form_id` + `leadership_form_id` — unresolved (spec D-05-7).
- Included employees: APR-02 makes every exclusion mandatory (joiners after the eligibility date, probation, PIP, offboarding); the HTML draws optional checkboxes and omits probation and PIP. Proposed: read-only information lines, date field = `eligibility_date` (spec D-05-6).
- "Visible" anonymity and "Optional"/"Off" peer modes need business rules before building (spec D-05-5).
- Due-date order error under the offending field: "Due dates must follow the order: self and peer, manager, HR."
- "Save draft" has no FRD endpoint to reopen/edit a draft round (spec D-05-19).
- "Start round" → AP02 then AP03; toast "Round started · {n} employees notified"; returns to S1.

---

## S4 · Self review with peer choice (Employee)

**Wireframe page:** `p-em-apr-self` · **Route:** `/me/appraisals/:appraisalId/self-review` (task "Self review"; My Reviews)

```
← Back to My Tasks
Self review
{Appraisal 2026} · {Nov 2025 – Oct 2026} · due {20-10-2026}
┌ Self review ─────────────────────────────────┐  ┌ Ask peers for feedback ────────┐
│ Job knowledge                                 │  │ [Priya Sharma ×] [Youssef T. ×]│
│ Has the skills and knowledge required…        │  │                                │
│   1   2   3  [4]  5                           │  │ Add peer                       │
│   Below     Meets     Exceeds                 │  │ [Search colleague…        ▾]   │
│ [Comment________________________________]     │  └────────────────────────────────┘
│                                               │
│ Quality of work …  (same pattern per question)│
│ Initiative …                                  │
│                                               │
│ Key achievements *                            │
│ [_____________________________________]       │
│ Areas to develop                              │
│ [_____________________________________]       │
└───────────────────────────────────────────────┘
[Cancel]                       [Save draft] [Submit self review]
```

**Changes from the HTML / notes:**
- Peer candidates come from AP10 ("Active colleagues, not self, not own manager"); chips removed with "×" (R-UI-1). Validation: "Choose 2 or 3 colleagues as peers."
- Peers are sent at once on choosing (AP11) — no manager approval (FRD AC "Peers are asked directly", spec D-05-4). Card hidden/optional depending on `peer_mode` (spec D-05-5).
- "Rate every question before submitting." when any rating is missing; "This field is required." under Key achievements.
- Storage of Key achievements / Areas to develop is unresolved (spec D-05-8).
- Read-only once submitted; behaviour after the due date unresolved (spec D-05-15).
- "Submit self review" → AP12 (`202`, undo window); toast "Self review submitted"; goes to S10.

---

## S5 · Peer review (Peer)

**Wireframe page:** `p-em-peer` · **Route:** `/peer-reviews/:reviewId` (task "Peer review")

```
← Back to My Tasks
Peer review · {Priya Sharma}
{Appraisal 2026} · due {20-10-2026}
┌───────────────────────────────────────────────┐
│ Teamwork                                       │
│ How is it to work with {Priya}?                │
│   1   2   3   4  [5]                           │
│   Below     Meets     Exceeds                  │
│ [Comment________________________________]      │
│                                                │
│ Communication …                                │
│                                                │
│ What should {Priya} keep doing? *              │
│ [_____________________________________]        │
│ What could {Priya} do better?                  │
│ [_____________________________________]        │
└────────────────────────────────────────────────┘
[Decline]                             [Submit peer review]
```

**Changes from the HTML / notes:**
- Opens only for the nominated peer (`review.reviewer_id`, FRD access gate); anyone else gets 403.
- "Decline" (red outline, left) opens S6.
- No "Save draft" drawn; AP09 (PUT) exists for peer reviews per FRD §4.3 ("Open and save the peer review") — **[ASSUMPTION]** add a "Save draft" button between the two, matching S4/S8.
- "keep doing" required message: "This field is required." [ASSUMPTION, spec §5]; storage spec D-05-8.
- Submit → AP12; toast "Peer review submitted"; back to My Tasks.

---

## S6 · Decline peer review (pop-up)

**Wireframe page:** `m-decline-peer` · **Trigger:** "Decline" on S5

```
┌ Decline peer review ─────────────────────── × ┐
│ Reason *                                       │
│ [We have not worked together this year.     ]  │
│                                                │
│ [Cancel]                            [Decline]  │
└────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- HTML order is [Decline] [Cancel]; swapped so Cancel is left (R-UI-1). Decline stays red (destructive), on the right.
- Empty reason → "Add a reason for declining."
- Decline → AP13; toast "Declined · {employee} and her manager are notified"; back to My Tasks.

---

## S7 · Change peers (Manager)

**Wireframe page:** `p-mg-peer-approve` (drawn as an approval — FRD Appendix A #6) · **Route:** `/appraisals/:appraisalId/peers` (row action "Change peers" on S2)

```
← Back to Appraisals
Change peers · {Lotte Jansen}
{Appraisal 2026}
┌──────────────────┬───────────┬──────────────────────────┐
│ Peer             │ Status    │ Action                   │
├──────────────────┼───────────┼──────────────────────────┤
│ PS Priya Sharma  │ Requested │ [Replace with… ▾]        │
│ YT Youssef Tahiri│ Declined  │ [Replace with… ▾]        │
└──────────────────┴───────────┴──────────────────────────┘
[Cancel]                                          [Save]
```

**Changes from the HTML / notes:**
- **Redrawn as "Change peers"** per FRD Appendix A #6's suggested fix ("Rename and redraw as Change peers"): title "Peers · Lotte Jansen", subtitle "requested by Lotte on 08 Oct" and the Keep/Replace "Decision" column (approval wording) are replaced by a status column (AP14) and a replace action (AP15).
- Dropped pending a decision (spec D-05-3): the "Worked together on" column (no data source in AP14's tables) and "Add a peer" (no FRD endpoint).
- Replacement candidates from AP10 [ASSUMPTION]; submitted peer reviews cannot be replaced [ASSUMPTION].
- Save → AP15 per changed row; toast "Peers updated · new peer notified"; back to S2.

---

## S8 · Manager review with evidence (Manager)

**Wireframe page:** `p-mg-apr-review` · **Route:** `/appraisals/:appraisalId/manager-review` (task "Manager review"; S2)

```
← Back to Appraisals
Manager review · {Priya Sharma}
{Appraisal 2026} · self review ✔ · peer reviews 2 / 2 ✔        (or: Self review not submitted)
┌ Review ─────────────────────────────────────┐  ┌ Evidence ─────────────────────────────┐
│ Job knowledge            Self 4 · Peers 4.5  │  │ Project review · MedTrack Dispense ★★★★★│
│   1   2   3   4   5                          │  │ Mark Smit · 30-06-2026                 │
│   Below     Meets     Exceeds                │  │ Release 2.1 delivered on time.         │
│ [Comment______________________________]      │  │ Client · MedTrack Pharma ★★★★★         │
│ Quality of work          Self 4 · Peers 5.0  │  │ R. de Graaf · call · 30-09-2026        │
│ Communication            Self 4 · Peers 4.0  │  │ "Excellent release support."           │
│                                              │  │ Client · Harbor Logistics BV ★★★★☆     │
│ Overall summary *                            │  │ Peer review · Lotte Jansen ★★★★★       │
│ [____________________________________]       │  │ 09-10-2026 · Always makes time to help.│
│                                              │  │ Self review ★★★★☆ · 08-10-2026         │
│ Recommendation                               │  │ Development plan 2025–26               │
│ [Promote] [Meets expectations] [Below exp.]  │  │ 2 of 3 goals met                       │
│  Propose    No change           HR may       │  │ KPI score {n}                          │
│  new title                      start PIP    │  └────────────────────────────────────────┘
└──────────────────────────────────────────────┘
[Cancel]                          [Save draft] [Submit to HR]
```

**Changes from the HTML / notes:**
- "Self review not submitted" replaces the ✔ line when the task was raised by the due-date fallback (APR-04, exact wording).
- Evidence panel: all items from the review period only (APR-05): project reviews, client feedback (stars, average, count), **KPI score** (in the FRD list, not drawn in the HTML — added), peer reviews **with names** (manager only, APR-03), self review, development plan result. Needs F04/F05 data (spec §9).
- "Propose new title" under Promote has no stored field (spec D-05-20) — label kept as helper text only, no input.
- "Add an overall summary." under the summary; "Rate every question before submitting." on missing ratings.
- Submit to HR → AP12 (`202`); toast "Review submitted to HR"; back to S2.

---

## S9 · Consolidate & finalise (HR)

**Wireframe page:** `p-hr-apr-final` · **Route:** `/appraisals/:appraisalId/finalise` (HR task; S1 "Finalise")

```
← Back to Appraisals
{Ruben Bakker}
{Appraisal 2026} · Consolidate & finalise
┌ Scores ──────────────────────────────────────┐  ┌ Inputs ─────────────────────────────┐
│ Competency      │ Self │ Peers (avg) │ Mgr   │  │ Manager review · Mark Smit ★★★★☆    │
│ Job knowledge   │  4   │    4.3      │  4    │  │ 09-10-2026 · Reliable, grew into    │
│ Quality of work │  4   │    4.7      │  5    │  │ mentoring role.                     │
│ Communication   │  3   │    4.0      │  4    │  │ Client · MedTrack Pharma ★★★★★      │
│ Initiative      │  3   │    3.7      │  4    │  │ R. de Graaf · call · 30-09-2026     │
│ Manager recommendation: Promote               │  │ Peer reviews (3) ★★★★☆              │
│ KPIs 2 of 2 on target · Client feedback 3 ·   │  │ Anonymous summary                   │
│ avg 4.7                                       │  │ Self review ★★★★☆ · 06-10-2026      │
└───────────────────────────────────────────────┘  └─────────────────────────────────────┘
┌ Final outcome ────────────────────────────────────────────────┐
│ (●) Promoted          ( ) No change          ( ) Under-performing│
│     Process promotion     Meets expectations     Start PIP       │
└──────────────────────────────────────────────────────────────────┘
┌ Promotion (only when Promoted) ───────────────────────────────┐
│ New job title *   [Senior Developer ▾]   New grade * [Senior I ▾]│
│ Effective date *  [01-01-2027]           Notify employee *       │
│                                          [After meeting ▾ | Immediately] │
└──────────────────────────────────────────────────────────────────┘
Final summary for employee *
[Congratulations Ruben…_____________________________________]
[Cancel]                                     [Finalise appraisal]
```

**Changes from the HTML / notes:**
- HTML's "Manager recommendation Promote → Senior Developer" shows a proposed title that has no column — rendered as "Promote" only (spec D-05-20).
- Peer reviews in Inputs: HTML says "Anonymous summary", but HR may see peer names (APR-03) — **[OPEN]** (spec D-05-27), kept as drawn until confirmed; names are available from AP17.
- Promotion block shown only when "Promoted" is selected; any missing field → "Complete the promotion details."; missing outcome or summary → "Choose an outcome and add a summary."
- Outcome pre-selected from the manager recommendation (spec D-05-1, proposal); HR can change it.
- HR opening their own appraisal: 403, page not rendered (APR-09).
- Cancel leaves the appraisal unchanged (FRD "Other paths"). Finalise → AP18 (`202`); toast "Appraisal finalised · {name} promoted" (or without "promoted" for other outcomes [ASSUMPTION]); back to S1.

---

## S10 · My Reviews (Employee)

**Wireframe page:** `p-em-reviews` · **Route:** `/me/reviews` (menu "My Reviews")

```
My Reviews
┌ Received reviews ──────────────────────────────┐  ┌ {Appraisal 2026} ──────────────────┐
│ Project review · Atlas CRM phase 1  ★★★★☆      │  │ Self review  [Open]                 │
│ Mark Smit · 31-03-2026                          │  │ Due          20-10-2026             │
│ Strong ownership of the data migration.         │  │ Peers        Priya Sharma,          │
│                                                 │  │              Youssef Tahiri ·       │
│ Appraisal 2025 · Meets expectations ★★★★☆       │  │              requested              │
│ Finalised 14-11-2025                            │  │                                     │
│                                                 │  │ [Start self review]                 │
│ Client feedback · Harbor Logistics BV ★★★★★     │  └─────────────────────────────────────┘
│ 02-09-2026 · "Delivered ahead of peak season."  │
└─────────────────────────────────────────────────┘
```

**Changes from the HTML / notes:**
- A finalised appraisal opens its final summary and the received feedback **without peer names** (APR-03, FRD AC "Peer feedback is anonymous").
- Client-feedback entries are drawn but not in AP19's FRD table list (spec D-05-21).
- The right card shows the open round only; after submission the badge becomes "Submitted" and the button disappears (HTML `data-after` state).
- With `promo_notify_mode = AFTER_MEETING`, whether the result is hidden here until the meeting is unresolved (spec D-05-10).
