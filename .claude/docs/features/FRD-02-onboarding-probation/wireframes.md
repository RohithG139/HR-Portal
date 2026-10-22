# FRD-02 — Text wireframes

Source: `.claude/docs/source/FRD-02/WF-02_Onboarding_and_Probation.html` (v1.2, canonical — read-only, R-SRC-1). `p-mg-probation` confirmed present in this dedicated file, same page id as the legacy monolithic wireframe.

**Onboarding screens S1–S13 are already drawn in `F01-onboarding/wireframes.md`** (built, unchanged, against the legacy v1.1 wireframe file — page ids are unchanged in WF-02) — not repeated here. This file covers only the probation-review screens that document adds (S14, S15), which that file does not have.

---

## S14 · Probation review (Manager)

**Wireframe page:** `p-mg-probation` · **Route:** `/team/:code/probation-review` (FRD-02 v1.2 §3, Screen 12)

```
← Back to My Tasks
Probation review · {employee name}              Started {date} · probation ends {date}
┌ Review ──────────────────────────────────────────────┐
│ Quality of work                                        │
│   1   2   3   [4]   5                                  │
│   Below        Meets        Exceeds                    │
│                                                         │
│ Collaboration & communication                          │
│   1   2   3   [4]   5                                  │
│   Below        Meets        Exceeds                    │
│                                                         │
│ Learning & independence                                │
│   1   2   [3]   4   5                                  │
│   Below        Meets        Exceeds                    │
│                                                         │
│ Summary*                                                │
│ [________________________________________________]     │
│                                                         │
│ Outcome                                                 │
│ [● Confirm employment]   [○ End employment]             │
│   Probation passed         HR approval, then offboarding│
└─────────────────────────────────────────────────────────┘
[Cancel]                                          [Submit]
```

```
┌ Context ──────────┐
│ Started    {date}  │
│ Project    {name}  │
│ Onboarding {n}%     │
│ Buddy      {name}  │
└────────────────────┘
```

**Changes from the HTML / notes:**
- Rating questions ("Quality of work", "Collaboration & communication", "Learning & independence") and their 1–5 scale with Below/Meets/Exceeds legend are read directly from the active `PROBATION`-type `appraisal_form` (FRD-08), the same way F01's `GET /api/checklist-templates/{id}/preview` reads from a seeded template rather than hard-coding the three questions — if HR edits the form later (FRD-08), this screen must reflect that, not a fixed layout. The HTML draws these three specific questions as static example content; the real screen renders whatever the active form defines.
- Outcome is a segmented control exactly as drawn: "Confirm employment" (green, left per R-UI-1's positive-action-green convention — though note this is a *selection*, not a submit action, so R-UI-1's "positive actions on the right" button-placement rule does not apply to this control, only to the Submit button below it) / "End employment" (red).
- Submit is disabled until all three ratings are answered, Summary is non-empty, and an outcome is chosen (D-P3's combined message: "Add a summary and choose an outcome.").
- Context panel's "Project" field has no data source until F03 (Projects) exists — render "—" or omit the row until then (same treatment F01 gave the Employee record's Overview tab's Work cycle/Development/Appraisal cards).
- Cancel: red outline, left (R-UI-1). Submit: green, right.

---

## S15 · Probation decision (HR)

**Route:** `/employees/:code/probation-review/decision` (FRD-02 v1.2 §3, Screen 13)

**Not drawn in the wireframe — and FRD-02 v1.2 itself says so**, not just this spec's own search: its Screen 13 entry reads "not drawn", and its Appendix A #5 tracks this as an open gap across three features at once: "HR approval of 'End employment', HR 'Confirm exit' and HR PIP sign-off have no dedicated page. Suggested fix: Draw them, or state that HR uses the same page with an HR action bar." (confirmed independently by checking every `p-hr-*` page id in WF-02 for anything related to "probation" or a generic approval pattern — only `p-hr-pip-approve`, a different feature's approval screen, and `p-hr-form-probation`, the FRD-08 form *editor*, exist). Built here as a minimal placeholder from ONB-08's text alone — **[OPEN] per spec.md D-P4, confirm with the user before implementing** which of the FRD's two suggested fixes to take.

```
← Back to My Tasks
Probation decision · {employee name}             Reported by {manager name} · {date}
┌ Review summary (read-only) ─────────────────────────────┐
│ Quality of work      {score}/5                           │
│ Collaboration         {score}/5                           │
│ Learning              {score}/5                           │
│ Summary                                                   │
│ "{manager's summary text}"                                │
│ Outcome: End employment                                   │
└────────────────────────────────────────────────────────────┘
[Reject]                                          [Approve]
```

- **[ASSUMPTION]**: button placement follows R-UI-1 (positive action green/right — here "Approve" since it's the one that proceeds; "Reject" red outline/left, matching the Cancel/Delete convention rather than the Archive one, since rejecting isn't an archive action). Not FRD-specified.

**Decided 2026-10-21 (user):** D-P4 = "same page + HR action bar" — S15 is the S14 layout above (title "Approve end of employment · {name}", ratings/summary/outcome read-only, Context panel) with the `[Reject] … [Approve]` bar at the bottom, shown only while the decision is pending; after a click the undo banner (G-03) replaces it. D-P5 = Reject confirms the employee (no reason field, no extra step). Route carries `?reviewId={id}`.
