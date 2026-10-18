/**
 * @component MyTeamPage
 * @description W5 — My Team page (Manager only, `/team`, `p-mg-team`).
 * WS-02-scoped list of the authenticated manager's direct reports.
 * Columns: Employee (name + job title), Status. Deferred columns (Current projects,
 * Allocation, Development) are omitted — they don't exist in the server response yet
 * and will be added by F04/F05 (additive DTO contract, spec §7 D-W2).
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
import { Badge, stageVariant, Table, useToast } from '../../components/index'
import type { Column } from '../../components/index'
import { ApiResponseError } from '../../api/types'

/** Display labels for lifecycle stage codes. */
const stageLabel: Record<string, string> = {
  ONBOARDING: 'Onboarding',
  ACTIVE: 'Active',
  OFFBOARDING: 'Offboarding',
}

/**
 * MyTeamPage fetches the manager's direct reports with React Query.
 *
 * WS-02: the server already scopes the response to `manager_id = caller.id` —
 * hiding the My Team nav item is UX convenience only (R-UI-7, R-API-2).
 *
 * Status filter: All / Onboarding / On project / Bench / PIP — all 5 options are wired
 * even though "On project", "Bench" and "PIP" currently return empty server-side
 * (those statuses depend on F04/F07 tables that don't exist yet — per spec §6 W05 note).
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
        <div>
          {/* Row clicks navigate to the shared /employees/:id route (same one HR uses — F01) */}
          <Link
            to={`/employees/${r.employeeId}`}
            className="text-sm font-medium text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
          >
            {r.firstName} {r.lastName}
          </Link>
          {r.jobTitle !== null && (
            <p className="text-xs text-gray-500">{r.jobTitle}</p>
          )}
        </div>
      ),
    },
    {
      key: 'stage',
      header: 'Status',
      accessor: (r) => r.stage,
      render: (r) => (
        <Badge
          label={stageLabel[r.stage] ?? r.stage}
          variant={stageVariant(r.stage)}
        />
      ),
    },
  ]

  const filters = (
    <div>
      <label htmlFor="status-filter" className="sr-only">Filter by status</label>
      <select
        id="status-filter"
        value={statusFilter}
        onChange={(e) => setStatusFilter(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Status [All]</option>
        <option value="ONBOARDING">Onboarding</option>
        <option value="On project">On project</option>
        <option value="BENCH">Bench</option>
        <option value="PIP">PIP</option>
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
          My Team
        </h1>
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
