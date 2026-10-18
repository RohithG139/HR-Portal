/**
 * @component DashboardStub
 * @description Role-dispatched dashboard page (W1–W4, FRD-01 Wave 3).
 * Fetches GET /api/dashboard and renders the appropriate role-specific dashboard component.
 * Employee role with ONBOARDING stage is redirected to /dashboard/my-onboarding (F01's
 * NewJoinerDashboard, unchanged); ACTIVE-stage employees see this wave's EmployeeDashboard.
 *
 * `stage` was added to GET /api/me's response 2026-10-17 (a Gate-4 finding: without it,
 * EmployeeDashboard — already built this wave — had no way to ever be reached, since every
 * EMPLOYEE was unconditionally redirected to the onboarding dashboard regardless of their
 * real stage). This component now branches on it directly.
 *
 * @props (none — reads from AuthContext)
 * @example
 * Route: { index: true, element: <DashboardStub /> }
 */

import type { ReactElement } from 'react'
import { Navigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/auth/AuthContext'
import { getDashboard } from '../../api/dashboard'
import type {
  HrDashboardDto,
  ManagerDashboardDto,
  EmployeeDashboardDto,
  ItAdminDashboardDto,
} from '../../api/dashboard'
import { HrDashboard } from './HrDashboard'
import { ManagerDashboard } from './ManagerDashboard'
import { EmployeeDashboard } from './EmployeeDashboard'
import { ItAdminDashboard } from './ItAdminDashboard'

/**
 * DashboardStub fetches the dashboard data and renders the role-appropriate component.
 *
 * EMPLOYEE + stage='ONBOARDING' redirects to /dashboard/my-onboarding (F01's own build,
 * unchanged). EMPLOYEE + stage='ACTIVE' renders EmployeeDashboard at this same route,
 * fetching GET /api/dashboard like every other role.
 */
export function DashboardStub(): ReactElement {
  const { user } = useAuth()
  const isOnboardingEmployee = user?.appRole === 'EMPLOYEE' && user.stage === 'ONBOARDING'

  const { data, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
    // Onboarding-stage employees never call this endpoint — they're redirected below
    // to F01's own dashboard before this query would ever run.
    enabled: user !== null && !isOnboardingEmployee,
  })

  // Onboarding-stage employees redirect to F01's new-joiner dashboard (S7), unchanged.
  // Active-stage employees fall through and render EmployeeDashboard below, same as
  // every other role.
  if (isOnboardingEmployee) {
    return <Navigate to="/dashboard/my-onboarding" replace />
  }

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="animate-pulse space-y-6">
          <div className="h-8 bg-gray-200 rounded w-32" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="h-32 bg-gray-200 rounded-lg" />
            ))}
          </div>
          <div className="h-48 bg-gray-200 rounded-lg" />
        </div>
      </div>
    )
  }

  if (isError || data === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <p className="text-sm text-red-600">Dashboard could not be loaded. Please try again.</p>
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900 dark:text-white">
        Dashboard
      </h1>

      {/* Render the appropriate dashboard based on the authenticated user's role.
          The backend already dispatched the correct DTO shape; the frontend casts
          after branching on role — same discipline as the backend's switch statement. */}
      {user?.appRole === 'HR' && (
        <HrDashboard data={data as HrDashboardDto} />
      )}
      {user?.appRole === 'MANAGER' && (
        <ManagerDashboard data={data as ManagerDashboardDto} />
      )}
      {user?.appRole === 'EMPLOYEE' && (
        <EmployeeDashboard data={data as EmployeeDashboardDto} />
      )}
      {user?.appRole === 'IT_ADMIN' && (
        <ItAdminDashboard data={data as ItAdminDashboardDto} />
      )}
    </div>
  )
}

// Re-export under the DashboardPage alias for clarity in the router import.
// Both names resolve to the same component; the router currently imports DashboardStub
// (kept for backwards compat with Wave 2's router.tsx).
export { DashboardStub as DashboardPage }
