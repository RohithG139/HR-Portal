# 03 — Case file and case roles (`employeecase.type`, `employeecase.caseteam`)

| | |
|---|---|
| Status | Approved baseline (10 items, 57 fields) |
| Rule | A field exists only if this file lists it (R-CM-2). New fields: add them here first, marked [NEEDS APPROVAL]. |

**Change log**

| Date | Change |
|---|---|
| 2026-10-15 | F01 D-18 approved: **+`Onboarding.itSetupComplete`** (boolean), so "Remaining setup" is raised only when IT work is actually left after "Equipment request" completes. |

## 1. Principles

- **Allowed contents:** IDs, dates, booleans and enums only. No names, e-mail addresses, addresses or free text (R-ARC-3).
- **Who writes:** Spring, either as a task completion output or through the case-file API, in the same request that saves the database row.
- **Snapshots:** settings values are copied into the item **when the cycle starts** (ADM-02). For example, `PIP.checkinDays` is copied when the PIP is drafted.
- **Absolute dates:** Spring computes them, for example `Probation.reviewDate = probation end − PROBATION_REVIEW days`.
- **Timer spike (E1 in `01-overview.md`, run 2026-10-15):** **Result: does not hold.** A live test against CaseFabric 1.1.35 showed `timerExpression` cannot read a case-file value directly (only a literal ISO-8601 duration works). Every "timer" row below (`Onboarding.overdueDate`, `Probation.reviewDate`, `Contract.renewalDecisionDate`, `Appraisal.selfDueDate`, the PIP check-in/final timers, etc.) therefore uses the fallback uniformly: Spring computes and stores the absolute date as documented, a scheduler polls for `date <= today` (guarded by the matching `*Enabled` flag where one exists), and raises the business action directly through the engine REST API — no feature should attempt a native CMMN timer reading one of these fields.

## 2. Items and fields

| Item (multiplicity) | Field | Type | Written by | Used by | DB source |
|---|---|---|---|---|---|
| **Employee** (1) | employeeId | string | Spring, at case start | correlation | employee.employee_code |
| | contractType | PERMANENT / FIXED_TERM | Spring (start, renewal) | entry of the Contract stage | contract |
| | startDate | date | Spring (start) | base for the onboarding-overdue timer | contract.start_date |
| | isManager | boolean | Spring (role change) | "Reassign team & projects" applicability | employee.app_role |
| **Onboarding** (1) | itRequestSent | boolean | Spring (Send to IT) | creates "Equipment request" (IT admin) | onboarding.it_status |
| | mandatoryItemsDone | boolean | Spring (each tick / untick) | enables onboarding completion (ONB-05) | checklist_item |
| | overdueDate | date-time | Spring (start) = start + ONBOARDING_OVERDUE | timer → "Onboarding overdue" (HR) | setting |
| | overdueEnabled | boolean | Spring (start) | guard on that timer | setting.enabled |
| | completed | boolean | output of HR's onboarding task | milestone "Employee onboarded" | onboarding.status |
| | itSetupComplete | boolean | Spring, output of "Equipment request" = true when no `checklist_item` with `owner_role='IT_ADMIN'` is still OPEN for this employee, else false | sentry: "Remaining setup" (IT admin) is raised only when false (F01 D-18) | checklist_item (IT_ADMIN items) |
| **Probation** (0..1; absent when probation = None) | reviewId | long | Spring | link | review (PROBATION) |
| | reviewDate | date-time | Spring = probation end − PROBATION_REVIEW | timer → "Probation review" (Manager) | v_contract |
| | outcome | CONFIRM / END | task output | CONFIRM → "Probation passed"; END → HR approval | review.outcome |
| | hrDecision | APPROVED / REJECTED | task output (HR) | APPROVED → Exit (PROBATION_NOT_PASSED) | review.hr_decision |
| **Contract** (0..1, fixed-term only) | contractId | long | Spring | link | contract.id |
| | endDate | date | Spring | – | contract.end_date |
| | renewalDecisionDate | date-time | Spring = end − CONTRACT_RENEWAL | timer → "Contract renewal decision" (HR) | setting |
| | renewalEnabled | boolean | Spring | guard on that timer | setting.enabled |
| | decision | RENEW / CONVERT / DO_NOT_RENEW | task output | RENEW → repeat; DO_NOT_RENEW → Exit | decision (CONTRACT_RENEWAL) |
| **Bench** (1) | onBench | boolean | Spring (assignment start / end) | Bench stage entry and exit | v_bench |
| | checkIntervalDays | integer | Spring = BENCH_CHECK | repeating "Check project status" | setting |
| | lastDecision | ASSIGN_PROJECT / STAY_ON_BENCH | task output | STAY → next check | decision (BENCH_CHECK) |
| **ProjectReview** (0..1, overwritten per request) | reviewId | long | Spring, before raising "Project review requested" | task input | review (PROJECT) |
| | outcome | DELIVERED / PERFORMANCE_CONCERN | task output | shown as the PIP source | review.outcome |
| **DevelopmentPlan** (0..1) | planId | long | Spring | link | development_plan.id |
| | status | plan status enum | Spring / task output | guards | development_plan.status |
| | decision | APPROVE / REQUEST_CHANGES | task output | REQUEST_CHANGES → "Revise development plan" | decision (PLAN_APPROVAL) |
| | progressCheckDays | integer | Spring, on approve | repeating progress check | setting |
| | checkOutcome | CONTINUE / PERFORMED / SKILL_GAP | task output | PERFORMED → "Evaluate development plan" | checkin (PLAN_PROGRESS) |
| | furtherNeeds | boolean | task output | true → new "Create development plan" | development_plan.evaluation_outcome |
| **Appraisal** (0..1, overwritten per round) | roundId, appraisalId | long | Spring (Start round) | link | appraisal |
| | formId | long | Spring | task input | review (SELF).form_id |
| | selfDueDate | date-time | Spring | timer → "Manager review" (APR-04) | appraisal_round.self_peer_due |
| | selfSubmitted | boolean | task output | manager review entry | review (SELF).status |
| | pendingPeerReviewId | long | Spring, before "Peer requested" | input of the new peer task | review (PEER) |
| | recommendation | PROMOTE / MEETS / BELOW | task output | – | review (MANAGER).outcome |
| | outcome | PROMOTED / NO_CHANGE / UNDER_PERFORMING | task output | milestone "Employee promoted" | appraisal.outcome |
| **PIP** (0..1) | pipId | long | Spring | link | pip.id |
| | status | pip status enum | Spring / task output | guards | pip.status |
| | hrDecision | APPROVE / AMEND / REJECT | task output | notice or back to manager | decision (PIP_HR) |
| | disputed | boolean | task output | second notice: acknowledge only (PIP-09) | pip.disputed_at |
| | acknowledged | boolean | task output | milestone "PIP active" | pip.acknowledged_at |
| | startDate | date-time | Spring (HR approval) | base for the check-in and final timers | pip.start_date |
| | checkinDays, durationDays, extensionDays | integer | Spring (draft) | timers | pip.* snapshot |
| | proposedOutcome | PASSED / EXTEND / NOT_MET | task output | → HR sign-off | checkin (PIP_FINAL) |
| | hrConfirmed | boolean | task output | outcome milestones (PIP-06) | checkin.hr_signoff_at |
| | extended | boolean | Spring | EXTEND only once (PIP-05) | v_pip |
| | result | PASSED / NOT_MET | Spring | PASSED → "PIP passed"; NOT_MET → Exit | pip.outcome |
| **Exit** (0..1) | exitId | long | Spring | link | employee_exit.id |
| | type | 5 exit types | Spring | automatic types skip "Confirm exit" [OPEN] | employee_exit.exit_type |
| | requestedByRole | EMPLOYEE / MANAGER / HR / SYSTEM | Spring | MANAGER / EMPLOYEE → "Confirm exit" | employee_exit.initiated_by_role |
| | approved | boolean | task output or Spring | → Offboarding | employee_exit.status |
| | lastWorkingDay | date-time | Spring | access-removal due date | employee_exit.last_working_day |
| | closeAllowed | boolean | Spring (OFF-03) | enables "Archive & close case" | assets, checklist, manager_change |

## 3. Case roles (`employeecase.caseteam`)

| Case role | Members | Set by |
|---|---|---|
| Employee | the employee's user | Spring, at case start |
| Manager | the current line manager's user | Spring, at start; replaced on transfer |
| HR | tenant role `HR` | case start |
| ITAdmin | tenant role `IT_ADMIN` | case start |
| Peer | each nominated peer's user | Spring, at nomination |
| ProjectManager | the PM of a project the employee is on, if not the line manager | Spring, on assignment start / end |

The **Client** role in the legacy `emp.caseteam` is removed (G-07).

## 4. Example: the case file at case start (F01, Jesse Willems)

```json
{
  "Employee":   { "employeeId": "E-0050", "contractType": "PERMANENT", "startDate": "2026-10-21", "isManager": false },
  "Onboarding": { "itRequestSent": false, "mandatoryItemsDone": false,
                  "overdueDate": "2026-10-24T09:00:00Z", "overdueEnabled": true, "completed": false,
                  "itSetupComplete": false },
  "Probation":  { "reviewDate": "2026-12-07T09:00:00Z" },
  "Bench":      { "onBench": false }
}
```

`Probation.reviewId` is set later, when the review row is created (outside F01).
