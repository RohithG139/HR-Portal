/**
 * @component TaskList
 * @description Renders the My Tasks list with due-date colours (R-UI-6).
 * The `dueState` field comes from the backend (R-ARC-1): React maps it to colour only.
 * @props tasks - TaskItemDto[] - task list from GET /api/tasks
 * @props onComplete - function - called when an action button is clicked
 * @props isLoading - boolean - shows skeleton rows
 * @props showEmployee - boolean - shows the Employee column (HR/Manager/IT view)
 * @props linkTo - function - optional per-task route; when it returns a route, the task's
 * title links there (e.g. a manager's orientation item links to the S7M screen, OPEN-01)
 * @example
 * <TaskList tasks={data.content} showEmployee onComplete={handleComplete} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import type { TaskItemDto } from '../../api/tasks'
import { formatDate } from '../../lib/date'

interface TaskListProps {
  tasks: TaskItemDto[]
  onComplete?: (task: TaskItemDto) => void
  onUndo?: (task: TaskItemDto) => void
  isLoading?: boolean
  showEmployee?: boolean
  linkTo?: (task: TaskItemDto) => string | undefined
}

/** Maps backend dueState to Tailwind text colour classes (R-UI-6). */
const dueStateClass: Record<string, string> = {
  OVERDUE: 'text-red-600 font-semibold',
  DUE_SOON: 'text-orange-500 font-medium',
  UPCOMING: 'text-gray-500',
}

/** One task row. */
function TaskRow({
  task,
  onComplete,
  onUndo,
  showEmployee,
  linkTo,
}: {
  task: TaskItemDto
  onComplete?: (t: TaskItemDto) => void
  onUndo?: (t: TaskItemDto) => void
  showEmployee: boolean
  linkTo?: (t: TaskItemDto) => string | undefined
}): ReactElement {
  const dueCls = dueStateClass[task.dueState] ?? dueStateClass['UPCOMING']
  const route = linkTo?.(task)

  return (
    <tr className="bg-white hover:bg-gray-50">
      <td className="px-4 py-3 text-sm text-gray-500 uppercase">{task.module}</td>
      <td className="px-4 py-3 text-sm font-medium text-gray-900">
        {route !== undefined ? (
          <Link to={route} className="text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
            {task.title}
          </Link>
        ) : (
          task.title
        )}
      </td>
      {showEmployee && (
        <td className="px-4 py-3 text-sm text-gray-600">
          {task.employeeId !== null ? `#${task.employeeId}` : '—'}
        </td>
      )}
      <td className={`px-4 py-3 text-sm ${dueCls}`}>
        {task.dueDate !== null ? formatDate(task.dueDate) : '—'}
      </td>
      <td className="px-4 py-3">
        <div className="flex gap-2">
          {onComplete !== undefined && (
            <button
              type="button"
              onClick={() => onComplete(task)}
              className="
                px-3 py-1.5 text-xs font-medium rounded
                bg-green-600 hover:bg-green-700 text-white
                min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500
                transition-colors
              "
            >
              Complete
            </button>
          )}
          {onUndo !== undefined && (
            <button
              type="button"
              onClick={() => onUndo(task)}
              className="
                px-3 py-1.5 text-xs font-medium rounded
                border border-orange-500 text-orange-500 hover:bg-orange-50
                min-h-[44px] focus-visible:ring-2 focus-visible:ring-orange-500
                transition-colors
              "
            >
              Undo
            </button>
          )}
        </div>
      </td>
    </tr>
  )
}

/** Task list table with due-state colouring. */
export function TaskList({
  tasks,
  onComplete,
  onUndo,
  isLoading = false,
  showEmployee = false,
  linkTo,
}: TaskListProps): ReactElement {
  const colCount = showEmployee ? 5 : 4

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200">
      <table className="w-full text-sm text-left">
        <thead className="bg-gray-50 text-xs uppercase text-gray-500">
          <tr>
            <th scope="col" className="px-4 py-3">Module</th>
            <th scope="col" className="px-4 py-3">Task</th>
            {showEmployee && <th scope="col" className="px-4 py-3">Employee</th>}
            <th scope="col" className="px-4 py-3">Due</th>
            <th scope="col" className="px-4 py-3">Action</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {isLoading ? (
            Array.from({ length: 3 }).map((_, i) => (
              <tr key={i} className="animate-pulse">
                {Array.from({ length: colCount }).map((__, j) => (
                  <td key={j} className="px-4 py-3">
                    <div className="h-4 bg-gray-200 rounded w-24" />
                  </td>
                ))}
              </tr>
            ))
          ) : tasks.length === 0 ? (
            <tr>
              <td colSpan={colCount} className="px-4 py-8 text-center text-gray-400">
                No open tasks.
              </td>
            </tr>
          ) : (
            tasks.map((task) => (
              <TaskRow
                key={task.taskId}
                task={task}
                onComplete={onComplete}
                onUndo={onUndo}
                showEmployee={showEmployee}
                linkTo={linkTo}
              />
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
