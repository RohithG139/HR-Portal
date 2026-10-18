/**
 * @component ItAdminDashboard
 * @description W4 — IT admin dashboard. Shows equipment request count, open setup item count,
 * and the IT admin's own open task list. Deferred cards (access to revoke, not returned)
 * are omitted until F08 adds their DTO fields (additive contract, spec §7 D-W2).
 * @props data - ItAdminDashboardDto - pre-fetched dashboard data
 * @example
 * <ItAdminDashboard data={data} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '../../components/index'
import type { ItAdminDashboardDto, DashboardTaskDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'

interface ItAdminDashboardProps {
  data: ItAdminDashboardDto
}

/** R-UI-6: due-state colour for dashboard task rows. */
function dueStateClass(dueState: string): string {
  if (dueState === 'OVERDUE') return 'text-red-600'
  if (dueState === 'DUE_SOON') return 'text-orange-500'
  return 'text-gray-500'
}

/**
 * ItAdminDashboard renders the IT admin role's dashboard view.
 *
 * AC-W07: This shows Equipment requests, Open setup items, and My Tasks cards.
 * Access to revoke and Not returned cards are deferred (F08, need employee_exit).
 */
export function ItAdminDashboard({ data }: ItAdminDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      {/* KPI cards row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* Equipment requests */}
        <Card>
          <p className="text-sm text-gray-500 font-medium">Equipment requests</p>
          <p className="mt-2 text-3xl font-bold text-gray-900">{data.equipmentRequestsCount}</p>
          <p className="mt-1 text-sm text-gray-400">Open</p>
        </Card>

        {/* Open setup items */}
        <Card>
          <p className="text-sm text-gray-500 font-medium">Open setup items</p>
          <p className="mt-2 text-3xl font-bold text-gray-900">{data.openSetupItemsCount}</p>
          <p className="mt-1 text-sm text-gray-400">Pending</p>
        </Card>
      </div>

      {/* My Tasks */}
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
