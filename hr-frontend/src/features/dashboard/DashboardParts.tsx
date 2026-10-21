/**
 * @component DashboardParts
 * @description Building blocks shared by the role dashboards (wireframe #hr-dash / #it-dash):
 * StatCard (KPI tile) and DashboardTasks ("My tasks" card with OPEN buttons).
 * @example
 * <StatCard label="Open setup items" value={2} sub="Sanne de Boer" />
 * <DashboardTasks tasks={data.tasks} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import type { DashboardTaskDto } from '../../api/dashboard'
import { useAuth } from '../../app/auth/AuthContext'
import { formatDate } from '../../lib/date'
import { equipmentRequestEmployees, taskRoute } from '../tasks/taskRoute'

/** Outlined small uppercase button (VIEW ALL / OPEN). */
export const outlineButtonClass = `
  inline-flex items-center justify-center px-3 min-h-[32px] rounded-md
  border border-gray-300 bg-white hover:bg-gray-50
  text-xs font-semibold uppercase tracking-wide text-gray-700
  focus-visible:ring-2 focus-visible:ring-blue-500
`

const statCardClass = 'block bg-white border border-gray-200 rounded-lg p-4 sm:p-5'
const statLinkClass = 'hover:border-blue-300 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500'

interface StatCardProps {
  label: string
  value: number
  /** Grey detail line under the number; hidden when empty. */
  sub?: string
  /** Makes the whole card a link. */
  to?: string
  ariaLabel?: string
}

/** KPI tile: uppercase label, big number, grey detail line. */
export function StatCard({ label, value, sub, to, ariaLabel }: StatCardProps): ReactElement {
  const body = (
    <>
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</p>
      <p className="mt-2 text-2xl font-bold text-gray-900">{value}</p>
      {sub !== undefined && sub !== '' && <p className="mt-1 text-xs text-gray-500 truncate">{sub}</p>}
    </>
  )
  return to !== undefined ? (
    <Link to={to} className={`${statCardClass} ${statLinkClass}`} aria-label={ariaLabel}>{body}</Link>
  ) : (
    <div className={statCardClass} aria-label={ariaLabel}>{body}</div>
  )
}

/** R-UI-6: due-state colour for dashboard task rows. */
function dueStateClass(dueState: string): string {
  if (dueState === 'OVERDUE') return 'text-red-600'
  if (dueState === 'DUE_SOON') return 'text-orange-500'
  return 'text-gray-900'
}

/** "My tasks" card: "Task · Employee", due date, OPEN button per row; VIEW ALL to /my-tasks. */
export function DashboardTasks({ tasks, className = '' }: { tasks: DashboardTaskDto[]; className?: string }): ReactElement {
  const { user } = useAuth()
  const equipmentOpenFor = equipmentRequestEmployees(tasks)
  return (
    <section className={`bg-white border border-gray-200 rounded-lg p-4 sm:p-6 ${className}`} aria-labelledby="dash-tasks">
      <div className="flex items-center justify-between pb-3 mb-4 border-b border-gray-200">
        <h2 id="dash-tasks" className="text-base sm:text-lg font-semibold text-gray-900">My tasks</h2>
        <Link to="/my-tasks" className={outlineButtonClass}>View all</Link>
      </div>
      {tasks.length === 0 ? (
        <p className="text-sm text-gray-400 text-center py-4">No open tasks. Well done!</p>
      ) : (
        <ul className="border border-gray-200 rounded-lg divide-y divide-gray-200 overflow-hidden">
          {tasks.map((task, i) => {
            const title = task.employeeName != null ? `${task.title} · ${task.employeeName}` : task.title
            const route = taskRoute(user?.appRole, task.title, task.employeeId, equipmentOpenFor) ?? '/my-tasks'
            return (
              <li
                key={task.taskId ?? `task-${i}`}
                className={`px-4 py-3 flex items-center gap-4 ${i % 2 === 1 ? 'bg-blue-50' : 'bg-white'}`}
              >
                <p className="flex-1 min-w-0 text-sm font-medium text-gray-900">{title}</p>
                {task.dueDate !== null && (
                  <p className={`shrink-0 text-xs font-medium ${dueStateClass(task.dueState)}`}>
                    {formatDate(task.dueDate)}
                  </p>
                )}
                <Link to={route} className={`shrink-0 ${outlineButtonClass}`} aria-label={`Open ${title}`}>
                  Open
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
