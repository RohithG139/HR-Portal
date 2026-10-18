/**
 * @component HrDashboard
 * @description W1 — HR dashboard. Shows active employee count, onboarding snapshot,
 * and the HR user's own open task list. Deferred cards (bench, PIPs, appraisal, upcoming)
 * are omitted until F04/F07/F06/F08 add their DTO fields (additive contract, spec §7 D-W2).
 * @props data - HrDashboardDto - pre-fetched dashboard data
 * @example
 * <HrDashboard data={data} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '../../components/index'
import type { HrDashboardDto, DashboardTaskDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'

interface HrDashboardProps {
  data: HrDashboardDto
}

/** R-UI-6: due-state colour for dashboard task rows. */
function dueStateClass(dueState: string): string {
  if (dueState === 'OVERDUE') return 'text-red-600'
  if (dueState === 'DUE_SOON') return 'text-orange-500'
  return 'text-gray-500'
}

/**
 * HrDashboard renders the HR role's dashboard view.
 *
 * AC-W06: the "In onboarding" card links to /employees?stage=ONBOARDING so clicking the
 * number opens the Employees list filtered to onboarding stage.
 * The "Active employees" card links to /employees?stage=ACTIVE.
 */
export function HrDashboard({ data }: HrDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      {/* KPI cards row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* Active employees — links to /employees?stage=ACTIVE (AC-W06) */}
        <Link
          to="/employees?stage=ACTIVE"
          className="block focus-visible:ring-2 focus-visible:ring-blue-500 rounded-lg"
          aria-label={`Active employees: ${data.activeEmployees}, plus ${data.activeEmployeesDelta} this month`}
        >
          <Card className="hover:border-blue-300 transition-colors cursor-pointer">
            <p className="text-sm text-gray-500 font-medium">Active employees</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">{data.activeEmployees}</p>
            {data.activeEmployeesDelta > 0 && (
              <p className="mt-1 text-sm text-green-600">+{data.activeEmployeesDelta} this month</p>
            )}
          </Card>
        </Link>

        {/* In onboarding — links to /employees?stage=ONBOARDING (AC-W06) */}
        <Link
          to="/employees?stage=ONBOARDING"
          className="block focus-visible:ring-2 focus-visible:ring-blue-500 rounded-lg"
          aria-label={`In onboarding: ${data.onboardingCount}`}
        >
          <Card className="hover:border-blue-300 transition-colors cursor-pointer">
            <p className="text-sm text-gray-500 font-medium">In onboarding</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">{data.onboardingCount}</p>
            {data.onboardingNames.length > 0 && (
              <p className="mt-1 text-sm text-gray-500 truncate">
                {data.onboardingNames.join(', ')}
              </p>
            )}
          </Card>
        </Link>
      </div>

      {/* My Tasks list */}
      <Card title="My Tasks" action={
        <Link
          to="/my-tasks"
          className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
        >
          View all
        </Link>
      }>
        {data.tasks.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-4">No open tasks. Well done!</p>
        ) : (
          <ul className="divide-y divide-gray-100">
            {data.tasks.map((task: DashboardTaskDto) => (
              <li key={task.taskId} className="py-3 flex items-center justify-between gap-4">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{task.title}</p>
                </div>
                <div className="shrink-0 text-right">
                  {task.dueDate !== null && (
                    <p className={`text-xs ${dueStateClass(task.dueState)}`}>
                      {formatDate(task.dueDate)}
                    </p>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
