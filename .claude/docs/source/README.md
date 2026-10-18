# Source documents (read-only input)

Put the original input documents here. Claude reads them but never edits them (see the deny rules in `.claude/settings.json`).

## Canonical source (v1.2, decided 2026-10-16)

One FRD folder per module, each with its own FRD document and its own matching wireframe file:

| FRD | Module | FRD file | Wireframe file |
|---|---|---|---|
| FRD-00 | Application overview (roles, G-01…G-13, screen layout rules — applies to every module) | `FRD-00/FRD-00_Application_Overview.docx` | *(none — see FRD-00's own §4 "Screens" for the shared chrome; every module's own WF file draws its own screens)* |
| FRD-01 | Shared workspace: login, dashboards, tasks, notifications, employee record | `FRD-01/FRD-01_Shared_Workspace.docx` | `FRD-01/WF-01_Shared_Workspace.html` |
| FRD-02 | Onboarding and probation | `FRD-02/FRD-02_Onboarding_and_Probation.docx` | `FRD-02/WF-02_Onboarding_and_Probation.html` |
| FRD-03 | Projects and work cycle | `FRD-03/FRD-03_Projects_and_Work_Cycle.docx` | `FRD-03/WF-03_Projects_and_Work_Cycle.html` |
| FRD-04 | Development plan and KPIs | `FRD-04/FRD-04_Development_Plan_and_KPIs.docx` | `FRD-04/WF-04_Development_Plan_and_KPIs.html` |
| FRD-05 | Appraisal and promotion | `FRD-05/FRD-05_Appraisal_and_Promotion.docx` | `FRD-05/WF-05_Appraisal_and_Promotion.html` |
| FRD-06 | Performance Improvement Plan | `FRD-06/FRD-06_Performance_Improvement_Plan_PIP.docx` | `FRD-06/WF-06_Performance_Improvement_Plan_PIP.html` |
| FRD-07 | Contract end, offboarding and assets | `FRD-07/FRD-07_Contract_End_Offboarding_and_Assets.docx` | `FRD-07/WF-07_Contract_End_Offboarding_and_Assets.html` |
| FRD-08 | Reports and settings | `FRD-08/FRD-08_Reports_and_Settings.docx` | `FRD-08/WF-08_Reports_and_Settings.html` |

Each module docx is split from **FRD v1.2** ("Split from v1.2 (Oct 15, 2026 · @Rohith) · Oct 16, 2026", per FRD-00's own cover text) and is meaningfully more precise than v1.1: exact screen routes, a full method/endpoint/table-per-feature API list, a field-by-field database section cross-checked against the real `V1__init.sql` (with "OK"/"Fix"/"Note" verdicts per claim), and an enumerated Appendix A of FRD/wireframe/data-model gaps with suggested fixes. FRD-00's own document additionally covers what used to require cross-referencing the monolithic PDF for every module: the full cross-module permissions matrix, all 13 global rules G-01…G-13 verbatim, the complete case-file item inventory across every module (56 fields total), and the full `hr` schema's table/view inventory (39 tables, 11 views) with its own `V1__init.sql` cross-check.

**Referenced but not delivered (2026-10-16):** FRD-00's cover text names two companion documents — `CubeWorks_HR_CaseFabric_Integration_Guide.docx` and `CubeWorks_HR_DB_CrossCheck.docx` — that do not exist anywhere in this repository. If they exist elsewhere, add them here; until then, treat anything that would depend on them (e.g. the exact CaseFabric/Dex/Keycloak integration contract) as undocumented, not merely unread.

## Legacy source (v1.1, superseded)

| File | Content |
|---|---|
| `CubeWorks_HR_FRD.pdf` | Functional Requirements Document v1.1 (FRD-00 … FRD-08), monolithic, one file for every module |
| `CubeWorks_HR_interactive_wireframes_v6_1.html` | Interactive wireframes v6.1, one file for every module's screens |

Kept for history and for any v1.1-era spec that still cites it (e.g. `.claude/docs/features/F01-onboarding/`, built before v1.2 existed). Do not cite v1.1 in new specs — see "Canonical source" above. Where the two versions disagree, v1.2 wins (it is newer, written with knowledge of the already-built architecture, and has already been reconciled against the real schema); where v1.2 is silent on something v1.1 covered, v1.1 may still apply but note the gap rather than assuming carryover.

## Order of truth

**FRD v1.2 (per-module) > feature spec > wireframes.md > the module's own WF-xx HTML > legacy v1.1 PDF/HTML** (CLAUDE.md R-SRC-1, updated 2026-10-16 for the v1.2 source).
