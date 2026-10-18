/**
 * @component ProjectsTab
 * @description W7 — Employee record Projects tab placeholder (D-W1, FRD-01 Wave 3).
 * Renders a "Not available yet" placeholder card matching the visual style used in
 * OverviewTab's own placeholder cards. Full content will be built by F04 (Projects)
 * once the project_assignment table and GET /api/employees/{id}/projects endpoint exist.
 * @props employeeId - number - the employee being viewed (unused until real content lands)
 * @example
 * <ProjectsTab employeeId={42} />
 */

import type { ReactElement } from 'react'
import { Card } from '../../../components/index'

interface ProjectsTabProps {
  employeeId: number
}

/**
 * ProjectsTab renders a visible, reachable placeholder per D-W1's resolution:
 * "build placeholder tabs now, not hidden/deferred" — deliberately not empty/dead UI.
 * F04 replaces this placeholder with real content when it ships.
 *
 * No API call is made here because GET /api/employees/{id}/projects (W07) does not exist
 * until F04 creates the project/project_assignment tables.
 */
export function ProjectsTab({ employeeId: _ }: ProjectsTabProps): ReactElement {
  return (
    <Card title="Projects">
      <div className="text-center py-8 space-y-2">
        <p className="text-sm font-medium text-gray-500">Not available yet</p>
        <p className="text-xs text-gray-400">
          Project assignments will be shown here once the Projects module (F04) is delivered.
        </p>
      </div>
    </Card>
  )
}
