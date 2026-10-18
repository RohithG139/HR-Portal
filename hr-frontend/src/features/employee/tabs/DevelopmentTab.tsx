/**
 * @component DevelopmentTab
 * @description W8 — Employee record Development & reviews tab placeholder (D-W1, FRD-01 Wave 3).
 * Renders a "Not available yet" placeholder card. Full content will be built by F05 (Development)
 * and F06 (Appraisals) once kpi/development_plan/review tables and the
 * GET /api/employees/{id}/development endpoint exist.
 * @props employeeId - number - the employee being viewed (unused until real content lands)
 * @example
 * <DevelopmentTab employeeId={42} />
 */

import type { ReactElement } from 'react'
import { Card } from '../../../components/index'

interface DevelopmentTabProps {
  employeeId: number
}

/**
 * DevelopmentTab renders a visible, reachable placeholder per D-W1's resolution.
 * F05+F06 jointly replace this placeholder with real KPIs, development goals, and
 * review history when they ship.
 *
 * No API call is made here because GET /api/employees/{id}/development (W08) does not exist
 * until F05/F06 create the required tables.
 */
export function DevelopmentTab({ employeeId: _ }: DevelopmentTabProps): ReactElement {
  return (
    <Card title="Development & reviews">
      <div className="text-center py-8 space-y-2">
        <p className="text-sm font-medium text-gray-500">Not available yet</p>
        <p className="text-xs text-gray-400">
          KPIs, development goals and review history will be shown here once the
          Development (F05) and Appraisals (F06) modules are delivered.
        </p>
      </div>
    </Card>
  )
}
