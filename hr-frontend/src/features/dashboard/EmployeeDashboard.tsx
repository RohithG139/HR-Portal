/**
 * @component EmployeeDashboard
 * @description W3 — Employee active-stage dashboard. Shows issued equipment and the
 * employee's own open task list. Deferred cards (current projects, development plan,
 * appraisal) are omitted until F04/F05/F06 add their DTO fields (additive contract).
 * @props data - EmployeeDashboardDto - pre-fetched dashboard data
 * @example
 * <EmployeeDashboard data={data} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { Card } from '../../components/index'
import type { EmployeeDashboardDto, DashboardTaskDto, EquipmentDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'

interface EmployeeDashboardProps {
  data: EmployeeDashboardDto
}

/** R-UI-6: due-state colour for dashboard task rows. */
function dueStateClass(dueState: string): string {
  if (dueState === 'OVERDUE') return 'text-red-600'
  if (dueState === 'DUE_SOON') return 'text-orange-500'
  return 'text-gray-500'
}

/**
 * EmployeeDashboard renders the active-stage Employee's dashboard view.
 */
export function EmployeeDashboard({ data }: EmployeeDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* My Equipment */}
        <Card title="My Equipment">
          {data.equipment.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">No equipment assigned.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {data.equipment.map((item: EquipmentDto) => (
                <li key={item.assetId} className="py-3">
                  <p className="text-sm font-medium text-gray-900">{item.model}</p>
                  <p className="text-xs text-gray-500">
                    {item.assetType} · {item.assetNumber}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>

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
    </div>
  )
}
