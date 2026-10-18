# Feature index

Features are built **one at a time** in this order (CLAUDE.md §2.9).

**Status flow:** Draft → Spec ready (user approved) → In progress (gates 1–4) → Done (review passed).

| ID | Feature | FRD | Depends on | Status | Folder |
|---|---|---|---|---|---|
| F01 | Create employee → Onboarding, plus the shared basics it needs (login, layout, My Tasks, notifications, employee record) | FRD-02 (onboarding part), FRD-01 (parts), FRD-00 | – | **In progress** (gates 0–4 done; gate 5 review ran and fixed real issues, not yet "Done" — spec coverage 84.2%, a few items need a user decision, see spec.md §13) | `F01-onboarding/` |
| F02 | Probation review | FRD-02 (probation part) | F01 | Not started | – |
| F03 | Shared workspace: remainder (dashboards per role, profile editing, transfer manager) | FRD-01 | F01 | **Done** (2026-10-17 — gates 1/2/4 built it (migration V14, W01/W05/W06/W09/W10 backend, D-W9 IT admin employees extension, dashboards/My Team/Transfer manager/placeholder tabs); gate 5 found 2 HIGH security findings (IDOR on Transfer manager, see D-W11) + a same-day-transfer transaction bug + code-quality issues, all fixed; gate 3 N/A, no case-file changes. Final: 339 backend tests, 94 frontend tests, spec-coverage re-verified) | `FRD-01-shared-workspace/` |
| F04 | Projects & work cycle (projects, assignments, bench, client feedback, project reviews, change PM) | FRD-03 | F01 | Not started — **schema already applied** (see note below) | – |
| F05 | Development plan & KPIs | FRD-04 | F01 | Not started | – |
| F06 | Appraisal & promotion | FRD-05 | F04, F05 | Not started — **schema already applied** (see note below) | – |
| F07 | Performance Improvement Plan | FRD-06 | F04, F05, F06 | Not started — **schema already applied** (see note below) | – |
| F08 | Contract end, offboarding & assets | FRD-07 | F01–F07 | Not started — **`employee_exit` schema already applied** (see note below) | – |
| F09 | Reports & settings screens | FRD-08 | all | **Done** (2026-10-17 — all 5 gates: migrations V8–V13, C01–C47 backend (287 tests), S1–S15 frontend (65 tests), full security/code/spec-coverage review passed, D-05/D-10 resolved by user decision; gate 3 N/A, no case-model changes) | `FRD-08-settings-foundation/` |

**Note on settings (FRD-00 §7):** settings must exist before the other modules. F01 originally seeded what it needed with Flyway seed migrations (departments, job titles, onboarding templates, e-mail templates, timers) before F09 existed; F09 (now Done) is the real admin surface for managing those same rows plus the remaining settings F01 never touched (appraisal forms, the rest of the 21 e-mail templates and 17 timers).

## FRD-level reference specs

Separate from the `Fxx-name` feature-build folders above, these are full per-FRD specs written directly against the canonical FRD v1.2 source (`.claude/docs/source/README.md`), covering each FRD's **complete** scope — not just the slice a given feature happened to build. They exist so each FRD's full contract is documented in one place, independent of how the work ends up split across features. Added 2026-10-16, status **Draft** on all four.

| FRD | Covers | Relationship to the feature builds above | Folder |
|---|---|---|---|
| FRD-00 | Application overview: roles, the 13 global rules (G-01…G-13), the full cross-module permissions matrix, screen layout rules, the complete case-file/schema inventory | Cross-cutting; referenced by every feature. Not itself a buildable feature. | `FRD-00-application-overview/` |
| FRD-01 | Shared workspace: login, dashboards, My Tasks, notifications, the full 5-tab employee record, Transfer manager | **Done** (2026-10-17) — this spec doubled as F03's actual build spec, same pattern as FRD-08/F09; all 5 gates complete. | `FRD-01-shared-workspace/` |
| FRD-02 | Onboarding **and probation review** | F01 built the onboarding half only (its own spec says so explicitly). This spec covers both halves in one place and is F02's starting point for the unbuilt probation half. | `FRD-02-onboarding-probation/` |
| FRD-08 | Reports and settings: 8 reports, 5 settings tabs (departments/job titles, checklist templates, appraisal forms, e-mail templates, timers & rules) | **Done** (2026-10-17) — this spec doubled as F09's actual build spec; all 5 gates complete. | `FRD-08-settings-foundation/` |

**Resolved 2026-10-16 (was open above):** the `ONBOARDING_OVERDUE.can_disable` drift (D-09) — `V2__seed_settings.sql` had it `false`, FRD v1.2 says `Yes` — is fixed by `V8__fix_onboarding_overdue_can_disable.sql`, user-approved. Also found and fixed during the same gate: `appraisal_form`/`form_section`/`form_question` were documented in `02-data-model.md` but never actually created by `V1__init.sql` — a genuine gap between the design doc and the applied schema, not previously known. `V11__create_appraisal_form_tables.sql` adds them (DDL transcribed verbatim from `02-data-model.md`), and `V12__seed_appraisal_forms.sql` seeds the 3 minimum-viable forms.

**F04/F06/F07/F08 schema applied early, user-approved 2026-10-17:** F09's 8 reports (Bench, Allocation, Appraisal status, PIPs, Contracts & probation, Unreturned assets) need real data from Projects/Appraisal/PIP/Offboarding, none of which are built yet. Rather than stub those 5 reports with placeholders, `V13__create_report_tables_and_views.sql` (F09 Gate 2) creates `client`/`project`/`project_assignment`/`client_feedback`/`employee_exit`/`development_plan`/`appraisal_round`/`appraisal`/`pip`/`review`/`checkin` and their 6 report views — DDL transcribed verbatim from `02-data-model.md` (already fully designed there, never previously applied), nothing invented. See `02-data-model.md`'s 2026-10-17 change-log entry for the full reasoning. **Consequence for F04/F06/F07/F08:** when each of those features is eventually spec'd and reaches its own Gate 1, these tables already exist — that gate should check first and only add what (if anything) its own spec still needs, not re-create them. `goal`, `development_activity`, `checkin_goal` and `review_answer` are still fully unbuilt (not needed by any of the 6 report views).
