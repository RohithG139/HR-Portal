/**
 * Where a task's OPEN button goes, by the caller's role. Each role's task queue holds a few
 * kinds of work (see TaskService.getMyTasks), so role + title is enough to pick the screen;
 * probation tasks also need the employee code and the review id (`ref`).
 * Shared by My Tasks and the dashboards' task cards.
 */

import type { AppRole } from '../../app/auth/AuthContext'

/** Extra identifiers some routes need (TaskItemDto.employeeCode / relatedId). */
export interface TaskRef {
  employeeCode?: string | null
  relatedId?: number | null
}

/**
 * @param role       the signed-in user's role
 * @param title      the task title (IT: "Equipment request" vs setup items)
 * @param employeeId the employee the task is about
 * @param equipmentOpenFor IT only: employees that still have an open "Equipment request"
 *   task. Their checklist items are finished from the Equipment request page ("Mark setup
 *   done"); the Remaining setup page can only complete a "Remaining setup" task, which the
 *   engine creates after the equipment request is done.
 * @param ref        employee code + related id, for the probation routes (FRD-02 Screen 12/13)
 * @returns the route to open, or undefined when the task has no employee to open
 */
export function taskRoute(
  role: AppRole | undefined,
  title: string,
  employeeId: number | null,
  equipmentOpenFor?: ReadonlySet<number>,
  ref: TaskRef = {},
): string | undefined {
  if (role === 'EMPLOYEE') {
    return title === 'Create development plan' ? '/me/development/plan' : '/dashboard/my-onboarding'
  }
  if (employeeId === null) return undefined
  const probation = (path: string): string | undefined =>
    ref.employeeCode != null && ref.relatedId != null
      ? `${path.replace(':code', ref.employeeCode)}?reviewId=${ref.relatedId}`
      : undefined
  switch (role) {
    case 'HR':
      if (title === 'Approve end of employment') return probation('/employees/:code/probation-review/decision')
      // "Onboarding overdue" opens step 3 (Checklist); the wizard's ?step is 0-based
      if (title === 'Onboarding overdue') return `/employees/${employeeId}/onboarding?step=2`
      return `/employees/${employeeId}/onboarding`
    case 'IT_ADMIN':
      return title === 'Equipment request' || equipmentOpenFor?.has(employeeId) === true
        ? `/it/equipment-requests/${employeeId}`
        : `/it/setup/${employeeId}`
    case 'MANAGER':
      if (title === 'Probation review') return probation('/team/:code/probation-review')
      // OPEN-01 (S7M): a manager's other items are orientation items (D-08)
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
