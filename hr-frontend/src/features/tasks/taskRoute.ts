/**
 * Where a task's OPEN button goes, by the caller's role. Each role's task queue holds one
 * kind of work (see TaskService.getMyTasks), so role + title is enough to pick the screen.
 * Shared by My Tasks and the dashboards' task cards.
 */

import type { AppRole } from '../../app/auth/AuthContext'

/**
 * @param role       the signed-in user's role
 * @param title      the task title (IT: "Equipment request" vs setup items)
 * @param employeeId the employee the task is about
 * @param equipmentOpenFor IT only: employees that still have an open "Equipment request"
 *   task. Their checklist items are finished from the Equipment request page ("Mark setup
 *   done"); the Remaining setup page can only complete a "Remaining setup" task, which the
 *   engine creates after the equipment request is done.
 * @returns the route to open, or undefined when the task has no employee to open
 */
export function taskRoute(
  role: AppRole | undefined,
  title: string,
  employeeId: number | null,
  equipmentOpenFor?: ReadonlySet<number>,
): string | undefined {
  if (role === 'EMPLOYEE') return '/dashboard/my-onboarding'
  if (employeeId === null) return undefined
  switch (role) {
    case 'HR':
      return `/employees/${employeeId}/onboarding`
    case 'IT_ADMIN':
      return title === 'Equipment request' || equipmentOpenFor?.has(employeeId) === true
        ? `/it/equipment-requests/${employeeId}`
        : `/it/setup/${employeeId}`
    case 'MANAGER':
      // OPEN-01 (S7M): a manager's own items are always orientation items (D-08)
      return `/manager/team/${employeeId}/orientation`
    default:
      return undefined
  }
}

/** Employees with an open "Equipment request" task in this task list (see taskRoute). */
export function equipmentRequestEmployees(
  tasks: ReadonlyArray<{ title: string; employeeId: number | null }>,
): Set<number> {
  const ids = new Set<number>()
  for (const t of tasks) {
    if (t.title === 'Equipment request' && t.employeeId !== null) ids.add(t.employeeId)
  }
  return ids
}
