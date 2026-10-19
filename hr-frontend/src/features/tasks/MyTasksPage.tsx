/**
 * @component MyTasksPage
 * @description S12 — My Tasks page (all roles).
 * Lists open tasks from the backend (engine tasks + own checklist items, A36).
 * Notifications live in the top bar's bell dropdown, not here (wireframe p-hr-tasks).
 * Due-state colours: OVERDUE=red, DUE_SOON=orange, UPCOMING=gray (R-UI-6).
 * @props (none — reads role from AuthContext)
 * @example
 * Route: { path: '/my-tasks', element: <MyTasksPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyTasks, undoTask } from '../../api/tasks'
import type { TaskItemDto } from '../../api/tasks'
import { TaskList } from '../../components/index'
import { useToast } from '../../components/index'
import { useAuth } from '../../app/auth/AuthContext'

/**
 * MyTasksPage renders the task list.
 *
 * The undo mutation (A37)
 * invalidates the task list so the undone task disappears from the list.
 */
export function MyTasksPage(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const { user } = useAuth()
  const [module, setModule] = useState<string>('')
  const [q, setQ] = useState<string>('')

  // OPEN-01 (S7M): a manager's own My Tasks items are always MANAGER-owned orientation
  // items (D-08 — no other checklist/engine task reaches a manager's queue), so the route
  // can be chosen from the caller's own role alone, without a per-task field from the API.
  const linkTo =
    user?.appRole === 'MANAGER'
      ? (task: TaskItemDto): string | undefined =>
          task.employeeId !== null ? `/manager/team/${task.employeeId}/orientation` : undefined
      : undefined

  const { data: taskData, isLoading: tasksLoading } = useQuery({
    queryKey: ['my-tasks', module, q],
    queryFn: () => getMyTasks({ module: module !== '' ? module : undefined, q: q !== '' ? q : undefined }),
  })

  const undoMutation = useMutation({
    mutationFn: (taskId: number) => undoTask(taskId),
    onSuccess: () => {
      showToast('Task completion undone.')
      void queryClient.invalidateQueries({ queryKey: ['my-tasks'] })
    },
    onError: () => showToast('Undo window has expired.', 'error'),
  })

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900 mb-6">
        My Tasks
      </h1>

      <div>
        <div className="space-y-4">
          {/* Filter / search row */}
          <div className="flex flex-col sm:flex-row gap-3">
            <label htmlFor="task-module" className="sr-only">Filter by module</label>
            <select
              id="task-module"
              value={module}
              onChange={(e) => setModule(e.target.value)}
              className="text-sm rounded-md border border-gray-300 bg-white text-gray-900 px-3 py-2 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500"
            >
              <option value="">All modules</option>
              <option value="ONBOARDING">Onboarding</option>
              <option value="IT">IT</option>
            </select>
            <label htmlFor="task-search" className="sr-only">Search tasks</label>
            <input
              id="task-search"
              type="search"
              placeholder="Search tasks…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="flex-1 text-sm rounded-md border border-gray-300 bg-white text-gray-900 px-3 py-2 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500"
            />
          </div>

          {tasksLoading ? (
            <div className="animate-pulse space-y-3">
              {Array.from({ length: 4 }, (_, i) => (
                <div key={i} className="h-14 bg-gray-200 rounded" />
              ))}
            </div>
          ) : (taskData === undefined || taskData.content.length === 0) ? (
            <p className="text-sm text-gray-400 py-8 text-center">
              No open tasks. Well done!
            </p>
          ) : (
            <TaskList
              tasks={taskData.content}
              onUndo={(task) => undoMutation.mutate(task.taskId)}
              linkTo={linkTo}
            />
          )}
        </div>
      </div>
    </div>
  )
}
