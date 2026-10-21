/**
 * @component MyTeamPage
 * @description W5 — My Team page (Manager only, `/team`, `p-mg-team`).
 * WS-02-scoped list of the authenticated manager's direct reports.
 * Columns: Employee (initials, name, job title), Current projects, Allocation bar, Status,
 * and a VIEW button. The wireframe's Development column and ASSIGN action are deferred
 * (F05 development progress / F04 project assignment screens are not built yet).
 * @props (none — reads from AuthContext)
 * @example
 * Route: { path: '/team', element: <MyTeamPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listTeam, exportTeam } from '../../api/team'
import type { TeamMemberDto } from '../../api/team'
import { Badge, stageLabel, stageVariant, Table, useToast } from '../../components/index'
import type { Column } from '../../components/index'
import { ApiResponseError } from '../../api/types'

/**
 * MyTeamPage fetches the manager's direct reports with React Query.
 *
 * WS-02: the server already scopes the response to `manager_id = caller.id` —
 * hiding the My Team nav item is UX convenience only (R-UI-7, R-API-2).
 *
 * Status filter: All / Onboarding / On project / Bench / PIP — server-side; On project and
 * Bench are derived from live project allocation (TeamService.teamStatus).
 */
export function MyTeamPage(): ReactElement {
  const { showToast } = useToast()
  const [statusFilter, setStatusFilter] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['team', { status: statusFilter }],
    queryFn: () => listTeam({ status: statusFilter || undefined }),
  })

  const rows: TeamMemberDto[] = data?.items ?? []

  const handleExport = (): void => {
    exportTeam({ status: statusFilter || undefined })
      .catch((err: unknown) => {
        const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
        showToast(msg, 'error')
      })
  }

  const columns: Column<TeamMemberDto>[] = [
    {
      key: 'name',
      header: 'Employee',
      accessor: (r) => `${r.firstName} ${r.lastName}`,
      render: (r) => (
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="flex items-center justify-center shrink-0 w-8 h-8 rounded-full bg-blue-100 text-blue-700 text-xs font-bold"
          >
            {`${r.firstName.charAt(0)}${r.lastName.charAt(0)}`.toUpperCase()}
          </span>
          <div className="min-w-0">
            <p className="text-sm font-medium text-gray-900">{r.firstName} {r.lastName}</p>
            {r.jobTitle !== null && <p className="text-xs text-gray-500">{r.jobTitle}</p>}
          </div>
        </div>
      ),
    },
    {
      key: 'projects',
      header: 'Current projects',
      accessor: (r) => r.projects ?? '',
      render: (r) => r.projects ?? '—',
    },
    {
      key: 'allocation',
      header: 'Allocation',
      accessor: (r) => r.allocationPct,
      render: (r) => (
        <div className="flex items-center gap-3">
          <div
            role="progressbar"
            aria-label={`Allocation ${r.allocationPct}%`}
            aria-valuenow={r.allocationPct}
            aria-valuemin={0}
            aria-valuemax={100}
            className="w-24 h-2 rounded-full bg-gray-200 overflow-hidden"
          >
            <div className="h-full bg-blue-600 rounded-full" style={{ width: `${Math.min(r.allocationPct, 100)}%` }} />
          </div>
          <span className="text-sm text-gray-900">{r.allocationPct}%</span>
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      accessor: (r) => r.teamStatus,
      render: (r) => (
        <Badge
          label={stageLabel(r.teamStatus)}
          variant={stageVariant(r.teamStatus)}
        />
      ),
    },
    {
      key: 'action',
      header: 'Action',
      accessor: () => '',
      sortable: false,
      render: (r) => (
        // Shared /employees/:id record (same route HR uses — F01)
        <Link to={`/employees/${r.employeeId}`} className={actionButtonClass}>
          View
        </Link>
      ),
    },
  ]

  const filters = (
    <div className="flex items-center gap-2">
      <label htmlFor="status-filter" className="text-sm font-medium text-gray-700">Status</label>
      <select
        id="status-filter"
        value={statusFilter}
        onChange={(e) => setStatusFilter(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px] w-44"
      >
        <option value="">All</option>
        <option value="ONBOARDING">Onboarding</option>
        <option value="ON_PROJECT">On project</option>
        <option value="BENCH">Bench</option>
        <option value="PIP">PIP</option>
      </select>
    </div>
  )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
          My Team
        </h1>
        {data !== undefined && (
          <p className="text-sm text-gray-500 mt-0.5">
            {data.totalElements} direct report{data.totalElements === 1 ? '' : 's'}
          </p>
        )}
      </div>

      <Table
        columns={columns}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search team…"
        filters={filters}
        emptyMessage="No team members found."
      />

      {/* Pagination placeholder */}
      {data !== undefined && data.totalPages > 1 && (
        <p className="text-sm text-gray-400 text-center">
          Page {data.page + 1} of {data.totalPages} ({data.totalElements} total)
        </p>
      )}
    </div>
  )
}

// Same outlined row button as the Employees list (wireframe)
const actionButtonClass = `
  inline-flex items-center justify-center px-3 min-h-[32px] rounded-md
  border border-gray-300 bg-white hover:bg-gray-50
  text-xs font-semibold uppercase tracking-wide text-gray-700
  focus-visible:ring-2 focus-visible:ring-blue-500
`
