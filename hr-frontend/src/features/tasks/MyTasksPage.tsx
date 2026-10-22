/**
 * @component MyTasksPage
 * @description S12 — My Tasks page (all roles, wireframe p-hr-tasks).
 * Lists open tasks from the backend (engine tasks + own checklist items, A36) in the shared
 * Table: Module badge, Task, Employee, Raised by, Due, and an OPEN button per row.
 * Module / Raised by filters and the search box are client-side over the fetched list.
 * Notifications live in the top bar's bell dropdown, not here.
 * Due-state colours: OVERDUE=red, DUE_SOON=orange, UPCOMING=gray (R-UI-6).
 * @props (none — reads role from AuthContext)
 * @example
 * Route: { path: '/my-tasks', element: <MyTasksPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getMyTasks } from '../../api/tasks'
import type { TaskItemDto } from '../../api/tasks'
import { Badge, stageLabel, stageVariant, Table } from '../../components/index'
import type { Column } from '../../components/index'
import { useAuth } from '../../app/auth/AuthContext'
import { formatDate } from '../../lib/date'
import { equipmentRequestEmployees, taskRoute } from './taskRoute'

/** Shown in "Raised by" when the task was created by the system (no created_by). */
const SYSTEM = 'System'

/** R-UI-6: due-state colour. */
const dueStateClass: Record<string, string> = {
  OVERDUE: 'text-red-600 font-medium',
  DUE_SOON: 'text-orange-500 font-medium',
  UPCOMING: 'text-gray-900',
}

/** "Sanne de Boer" -> "SB". */
function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.charAt(0) ?? ''
  const last = parts.length > 1 ? (parts[parts.length - 1] ?? '').charAt(0) : ''
  return `${first}${last}`.toUpperCase()
}

export function MyTasksPage(): ReactElement {
  const { user } = useAuth()
  const [moduleFilter, setModuleFilter] = useState('')
  const [raisedByFilter, setRaisedByFilter] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['my-tasks'],
    queryFn: () => getMyTasks(),
  })

  const allTasks: TaskItemDto[] = data?.content ?? []
  const equipmentOpenFor = equipmentRequestEmployees(allTasks)
  const raisedByOf = (t: TaskItemDto): string => t.raisedBy ?? SYSTEM

  // Filter options come from the tasks actually in the list
  const moduleOptions = [...new Set(allTasks.map((t) => t.module))].sort()
  const raisedByOptions = [...new Set(allTasks.map(raisedByOf))].sort()

  const rows = allTasks.filter((t) =>
    (moduleFilter === '' || t.module === moduleFilter) &&
    (raisedByFilter === '' || raisedByOf(t) === raisedByFilter))

  const columns: Column<TaskItemDto>[] = [
    {
      key: 'module',
      header: 'Module',
      accessor: (t) => t.module,
      render: (t) => <Badge label={stageLabel(t.module)} variant={stageVariant(t.module)} />,
    },
    {
      key: 'title',
      header: 'Task',
      accessor: (t) => t.title,
      render: (t) => <span className="font-medium text-gray-900">{t.title}</span>,
    },
    {
      key: 'employee',
      header: 'Employee',
      accessor: (t) => t.employeeName ?? '',
      render: (t) =>
        t.employeeName !== null ? (
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="flex items-center justify-center shrink-0 w-8 h-8 rounded-full bg-blue-100 text-blue-700 text-xs font-bold"
            >
              {initials(t.employeeName)}
            </span>
            <span className="text-gray-900">{t.employeeName}</span>
          </div>
        ) : (
          '—'
        ),
    },
    {
      key: 'raisedBy',
      header: 'Raised by',
      accessor: raisedByOf,
    },
    {
      key: 'due',
      header: 'Due',
      accessor: (t) => t.dueDate ?? '',
      render: (t) => (
        <span className={dueStateClass[t.dueState] ?? dueStateClass.UPCOMING}>
          {t.dueDate !== null ? formatDate(t.dueDate) : '—'}
        </span>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      accessor: () => '',
      sortable: false,
      render: (t) => {
        const route = taskRoute(user?.appRole, t.title, t.employeeId, equipmentOpenFor, t)
        return route !== undefined ? (
          <Link to={route} className={actionButtonClass} aria-label={`Open ${t.title}`}>
            Open
          </Link>
        ) : null
      },
    },
  ]

  const filters = (
    <div className="flex gap-4 flex-wrap items-center">
      <div className="flex items-center gap-2">
        <label htmlFor="task-module" className={filterLabelClass}>Module</label>
        <select
          id="task-module"
          value={moduleFilter}
          onChange={(e) => setModuleFilter(e.target.value)}
          className={filterSelectClass}
        >
          <option value="">All</option>
          {moduleOptions.map((m) => (
            <option key={m} value={m}>{stageLabel(m)}</option>
          ))}
        </select>
      </div>
      <div className="flex items-center gap-2">
        <label htmlFor="task-raised-by" className={filterLabelClass}>Raised by</label>
        <select
          id="task-raised-by"
          value={raisedByFilter}
          onChange={(e) => setRaisedByFilter(e.target.value)}
          className={filterSelectClass}
        >
          <option value="">All</option>
          {raisedByOptions.map((r) => (
            <option key={r} value={r}>{r}</option>
          ))}
        </select>
      </div>
    </div>
  )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">My Tasks</h1>
        <p className="text-sm text-gray-500 mt-0.5">Open tasks</p>
      </div>

      <Table
        columns={columns}
        rows={rows}
        isLoading={isLoading}
        searchPlaceholder="Search tasks…"
        filters={filters}
        emptyMessage="No open tasks. Well done!"
      />
    </div>
  )
}

// Same filter and row-button styles as the Employees list (wireframe)
const filterLabelClass = 'text-sm font-medium text-gray-700'
const filterSelectClass = `
  px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900
  focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px] w-44
`
const actionButtonClass = `
  inline-flex items-center justify-center px-3 min-h-[32px] rounded-md
  border border-gray-300 bg-white hover:bg-gray-50
  text-xs font-semibold uppercase tracking-wide text-gray-700
  focus-visible:ring-2 focus-visible:ring-blue-500
`
