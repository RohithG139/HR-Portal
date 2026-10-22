/**
 * Application router.
 *
 * Uses `createBrowserRouter` (React Router v7 data router). All F01 feature
 * routes are registered here. Each route is lazy-imported to avoid bundling
 * every page upfront.
 *
 * Route guard: AuthProvider ensures authentication before any child renders;
 * role-based access is enforced server-side (R-API-2). The menu in the sidebar
 * hides items by role (UX convenience only, R-UI-7).
 */

import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from './layout/AppLayout'
import { EmployeesListPage } from '../features/employees/EmployeesListPage'
import { OnboardingWizard } from '../features/onboarding/OnboardingWizard'
import { EquipmentRequestPage } from '../features/it/EquipmentRequestPage'
import { RemainingSetupPage } from '../features/it/RemainingSetupPage'
import { NewJoinerDashboard } from '../features/employee/NewJoinerDashboard'
import { ManagerOrientationPage } from '../features/employee/ManagerOrientationPage'
import { MyProfile } from '../features/employee/MyProfile'
import { EmployeeRecord } from '../features/employee/EmployeeRecord'
import { MyTasksPage } from '../features/tasks/MyTasksPage'
import { DashboardStub } from '../features/dashboard/DashboardStub'
import { SettingsPage } from '../features/settings/SettingsPage'
import { AppraisalFormEditor } from '../features/settings/AppraisalFormEditor'
import { ReportsOverviewPage } from '../features/reports/ReportsOverviewPage'
import { HeadcountReportPage } from '../features/reports/HeadcountReportPage'
import { BenchReportPage } from '../features/reports/BenchReportPage'
import { AllocationReportPage } from '../features/reports/AllocationReportPage'
import { AppraisalStatusReportPage } from '../features/reports/AppraisalStatusReportPage'
import { PipsReportPage } from '../features/reports/PipsReportPage'
import { OnboardingReportPage } from '../features/reports/OnboardingReportPage'
import { ContractsProbationReportPage } from '../features/reports/ContractsProbationReportPage'
import { UnreturnedAssetsReportPage } from '../features/reports/UnreturnedAssetsReportPage'
// FRD-01 Wave 3: My Team (Manager), PRJ-08 placeholder
import { MyTeamPage } from '../features/team/MyTeamPage'
import { ProjectMemberPage } from '../features/projects/ProjectMemberPage'
import { ProbationReviewPage } from '../features/probation/ProbationReviewPage'
import { UnderConstruction } from './UnderConstruction'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      // Default — DashboardStub redirects by role
      {
        index: true,
        element: <DashboardStub />,
      },

      // S1 — Employee list (HR)
      {
        path: 'employees',
        element: <EmployeesListPage />,
      },

      // S2/S3/S4 — Onboarding wizard (new employee, HR)
      {
        path: 'employees/new',
        element: <OnboardingWizard />,
      },

      // S2 — Resume a saved draft (HR). Only firstName/lastName carry over —
      // see Step1Details' initialDraftId doc comment (no GET-by-id endpoint exists).
      {
        path: 'onboarding/draft/:draftId',
        element: <OnboardingWizard />,
      },

      // S2/S3/S4 — Onboarding wizard for an existing in-progress employee (HR)
      {
        path: 'employees/:employeeId/onboarding',
        element: <OnboardingWizard />,
      },

      // S11 — Employee record (HR, Manager)
      {
        path: 'employees/:employeeId',
        element: <EmployeeRecord />,
      },

      // S5 — Equipment request (IT admin)
      {
        path: 'it/equipment-requests/:employeeId',
        element: <EquipmentRequestPage />,
      },

      // S6 — Remaining setup (IT admin)
      {
        path: 'it/setup/:employeeId',
        element: <RemainingSetupPage />,
      },

      // S7 — New-joiner dashboard (Employee)
      {
        path: 'dashboard/my-onboarding',
        element: <NewJoinerDashboard />,
      },

      // S7M — Manager orientation checklist
      {
        path: 'manager/team/:employeeId/orientation',
        element: <ManagerOrientationPage />,
      },

      // S10 — My Profile (Employee)
      {
        path: 'my-profile',
        element: <MyProfile />,
      },

      // S12 — My Tasks + notifications (all roles)
      {
        path: 'my-tasks',
        element: <MyTasksPage />,
      },

      // FRD-08 Settings — S1-S6 (tabs: departments/checklists/forms/emails/timers)
      // ?tab= query param controls the active tab (bookmarkable URLs per spec §4)
      {
        path: 'settings',
        element: <SettingsPage />,
      },

      // FRD-08 S4 — Appraisal form editor (new form)
      {
        path: 'settings/forms/new',
        element: <AppraisalFormEditor />,
      },

      // FRD-08 S4 — Appraisal form editor (existing form)
      {
        path: 'settings/forms/:formId',
        element: <AppraisalFormEditor />,
      },

      // FRD-08 Reports — S7 overview + S8-S15 detail reports (HR only, spec §4)
      // Route paths exactly match the FRD's own reportId values (spec §4 "Corrected from pre-v1.2").
      {
        path: 'reports',
        element: <ReportsOverviewPage />,
      },
      {
        path: 'reports/headcount',
        element: <HeadcountReportPage />,
      },
      {
        path: 'reports/bench',
        element: <BenchReportPage />,
      },
      {
        path: 'reports/allocation',
        element: <AllocationReportPage />,
      },
      {
        path: 'reports/appraisal-status',
        element: <AppraisalStatusReportPage />,
      },
      {
        path: 'reports/pips',
        element: <PipsReportPage />,
      },
      {
        path: 'reports/onboarding',
        element: <OnboardingReportPage />,
      },
      {
        path: 'reports/contracts-probation',
        element: <ContractsProbationReportPage />,
      },
      {
        path: 'reports/unreturned-assets',
        element: <UnreturnedAssetsReportPage />,
      },

      // W5 — My Team (Manager only); WS-02 scoped server-side
      {
        path: 'team',
        element: <MyTeamPage />,
      },

      // FRD-02 Screen 12 — Probation review (Manager, task "Probation review")
      {
        path: 'team/:code/probation-review',
        element: <ProbationReviewPage mode="manager" />,
      },

      // FRD-02 Screen 13 — Approve end of employment (HR, task "Approve end of employment");
      // same page with an HR action bar (spec D-P4)
      {
        path: 'employees/:code/probation-review/decision',
        element: <ProbationReviewPage mode="hr" />,
      },

      // FRD-04 Screen 2 (task "Create development plan") — placeholder until F05 builds it
      {
        path: 'me/development/plan',
        element: <UnderConstruction />,
      },

      // W10 screen — PRJ-08 project member view placeholder (D-W7).
      // Reachable by direct URL only; no nav link currently points here.
      // F04 adds both the real content and the link when it ships.
      {
        path: 'projects/:projectId/members/:code',
        element: <ProjectMemberPage />,
      },

      // Catch-all redirect to root
      {
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
])
