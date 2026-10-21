/**
 * @component EmployeesListPage
 * @description S1 / Screen 8 — Employees list. Serves two role variants from the same
 * route (`/employees`):
 * - HR (all fields): sortable/filterable/searchable/exportable table, "+ New Employee" button,
 *   draft rows with "Continue" action, employee rows with "View" action.
 * - IT admin (D-W9, FRD-01 v1.2): read-only variant — Employee, Job title, Status, Assets
 *   columns only; no action column, no export icon, no "+ New Employee" button.
 *   The server-side role check enforces field reduction; column omission here is a UX concern
 *   only (R-UI-7: hiding buttons is never the only protection — R-API-2 enforces it server-side).
 * @props (none — reads current user from AuthContext)
 * @example
 * Route: { path: '/employees', element: <EmployeesListPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listEmployees, exportEmployees } from '../../api/employees'
import { getOnboardingLookups } from '../../api/lookups'
import type { EmployeeListItemDto } from '../../api/employees'
import { Badge, stageLabel, stageVariant, Table, useToast } from '../../components/index'
import type { Column } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import { useAuth } from '../../app/auth/AuthContext'

/**
 * EmployeesListPage fetches the employee list from the backend with React Query.
 *
 * Role branching:
 * - `isItAdmin`: true when the caller has the IT_ADMIN role. Renders a reduced read-only
 *   table (no export, no action column, no "+ New Employee" button). The server already
 *   returns only the IT-admin-allowed fields in the response body (D-W9); the frontend
 *   additionally omits irrelevant columns for clarity (R-UI-7).
 * - `isHR`: full-featured table with all columns and actions.
 *
 * staleTime is inherited from the QueryClient default (30 s). A very-fresh refetch
 * is triggered when the user returns to this tab after creating an employee, because
 * React Router's navigation invalidates the query on focus by default.
 */
export function EmployeesListPage(): ReactElement {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { user } = useAuth()

  const isItAdmin = user?.appRole === 'IT_ADMIN'
  const isHR = user?.appRole === 'HR'

  // stageFilter is a client-controlled query param for server-side stage filtering.
  // The search box is handled by the Table component's built-in client-side filter.
  const [stageFilter, setStageFilter] = useState('')
  // Department / manager filters (HR only) — server-side, by id ('' = all)
  const [departmentFilter, setDepartmentFilter] = useState('')
  const [managerFilter, setManagerFilter] = useState('')
  // A04 is paged server-side (20 per page); 0-based, reset to 0 whenever a filter changes
  const [page, setPage] = useState(0)

  const listParams = {
    stage: stageFilter || undefined,
    departmentId: departmentFilter !== '' ? Number(departmentFilter) : undefined,
    managerId: managerFilter !== '' ? Number(managerFilter) : undefined,
  }

  const { data, isLoading } = useQuery({
    queryKey: ['employees', listParams, page],
    queryFn: () => listEmployees({ ...listParams, page }),
  })

  // Options for the department/manager dropdowns (A02 is HR-only)
  const { data: lookups } = useQuery({
    queryKey: ['lookups', 'onboarding'],
    queryFn: getOnboardingLookups,
    enabled: isHR,
  })

  const rows: EmployeeListItemDto[] = data?.items ?? []

  const handleExport = (): void => {
    exportEmployees(listParams)
      .catch((err: unknown) => {
        const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
        showToast(msg, 'error')
      })
  }

  // ---------------------------------------------------------------------------
  // IT admin column set (D-W9): Employee, Job title, Status, Assets — no action
  // ---------------------------------------------------------------------------
  const itAdminColumns: Column<EmployeeListItemDto>[] = [
    {
      key: 'name',
      header: 'Employee',
      accessor: (r) => `${r.firstName} ${r.lastName}`,
    },
    {
      key: 'jobTitle',
      header: 'Job title',
      accessor: (r) => r.jobTitle ?? '—',
    },
    {
      key: 'stage',
      header: 'Status',
      accessor: (r) => r.stage,
      render: (r) => (
        <Badge
          label={stageLabel(r.stage)}
          variant={stageVariant(r.stage)}
        />
      ),
    },
    {
      key: 'assets',
      header: 'Assets',
      accessor: (r) => r.assetCount ?? 0,
      render: (r) => (
        <span className="text-sm text-gray-700">
          {r.assetCount ?? 0}
        </span>
      ),
    },
  ]

  // ---------------------------------------------------------------------------
  // HR column set: full, with Draft/Continue/View actions
  // ---------------------------------------------------------------------------
  const hrColumns: Column<EmployeeListItemDto>[] = [
    {
      key: 'id',
      header: 'ID',
      accessor: (r) => r.employeeCode ?? '',
    },
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
            {r.email !== null && <p className="text-xs text-gray-500 truncate">{r.email}</p>}
          </div>
        </div>
      ),
    },
    {
      key: 'jobTitle',
      header: 'Job title',
      accessor: (r) => r.jobTitle ?? '—',
    },
    {
      key: 'department',
      header: 'Department',
      accessor: (r) => r.department ?? '—',
    },
    {
      key: 'manager',
      header: 'Manager',
      accessor: (r) => r.managerName ?? '—',
    },
    {
      key: 'stage',
      header: 'Stage',
      accessor: (r) => r.stage,
      render: (r) => (
        <Badge
          label={stageLabel(r.stage)}
          variant={r.draft ? 'draft' : stageVariant(r.stage)}
        />
      ),
    },
    {
      key: 'action',
      header: 'Action',
      accessor: () => '',
      sortable: false,
      render: (r) =>
        r.draft ? (
          <Link
            to={`/onboarding/draft/${r.draftId ?? ''}`}
            // firstName/lastName travel via router state, not the URL, so this
            // personal data doesn't end up in browser history or server access
            // logs (it's lost on a hard refresh, which is an acceptable
            // trade-off for a "continue where you left off" convenience link).
            state={{ firstName: r.firstName, lastName: r.lastName }}
            className={actionButtonClass}
          >
            Continue
          </Link>
        ) : (
          <Link
            to={`/employees/${r.id ?? ''}`}
            className={actionButtonClass}
          >
            View
          </Link>
        ),
    },
  ]

  const columns = isItAdmin ? itAdminColumns : hrColumns

  // Stage filter shown for both HR and IT admin (IT admin can filter by status too);
  // department and manager filters are HR only (their option lists come from A02).
  const filters = (
    <div className="flex gap-4 flex-wrap items-center">
      <div className="flex items-center gap-2">
        <label htmlFor="stage-filter" className={filterLabelClass}>Stage</label>
        <select
          id="stage-filter"
          value={stageFilter}
          onChange={(e) => { setStageFilter(e.target.value); setPage(0) }}
          className={filterSelectClass}
        >
          <option value="">All</option>
          <option value="ONBOARDING">Onboarding</option>
          <option value="ACTIVE">Active</option>
          <option value="OFFBOARDING">Offboarding</option>
        </select>
      </div>
      {isHR && (
        <>
          <div className="flex items-center gap-2">
            <label htmlFor="department-filter" className={filterLabelClass}>Department</label>
            <select
              id="department-filter"
              value={departmentFilter}
              onChange={(e) => { setDepartmentFilter(e.target.value); setPage(0) }}
              className={filterSelectClass}
            >
              <option value="">All</option>
              {(lookups?.departments ?? []).map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <label htmlFor="manager-filter" className={filterLabelClass}>Manager</label>
            <select
              id="manager-filter"
              value={managerFilter}
              onChange={(e) => { setManagerFilter(e.target.value); setPage(0) }}
              className={filterSelectClass}
            >
              <option value="">All</option>
              {(lookups?.managers ?? []).map((m) => (
                <option key={m.id} value={m.id}>{m.name}</option>
              ))}
            </select>
          </div>
        </>
      )}
    </div>
  )

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
            Employees
          </h1>
          {/* Employee count (wireframe "48 employees"); IT admin also sees "Read-only" */}
          <p className="text-sm text-gray-500 mt-0.5">
            {data !== undefined && `${data.totalElements} employee${data.totalElements === 1 ? '' : 's'}`}
            {data !== undefined && isItAdmin && ' · '}
            {isItAdmin && <span>Read-only</span>}
          </p>
        </div>

        {/* + New Employee — HR only; positive action, top right (R-UI-1) */}
        {isHR && (
          <button
            type="button"
            onClick={() => navigate('/employees/new')}
            className="
              px-4 py-2 text-sm font-medium rounded-md
              bg-green-600 hover:bg-green-700 text-white
              min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500
              transition-colors
            "
          >
            + New Employee
          </button>
        )}
      </div>

      {/* Table with toolbar.
          IT admin: no export icon (no onExport prop → Table hides the icon).
          HR: export enabled. */}
      <Table
        columns={columns}
        rows={rows}
        onExport={isHR ? handleExport : undefined}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No employees found."
      />

      {/* Pager — the search box only filters the rows of the current page */}
      {data !== undefined && data.totalPages > 1 && (
        <nav aria-label="Employee list pages" className="flex items-center justify-center gap-3">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(p - 1, 0))}
            disabled={data.page === 0}
            className={pagerButtonClass}
          >
            ← Previous
          </button>
          <span className="text-sm text-gray-500">
            Page {data.page + 1} of {data.totalPages} ({data.totalElements} total)
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(p + 1, data.totalPages - 1))}
            disabled={data.page >= data.totalPages - 1}
            className={pagerButtonClass}
          >
            Next →
          </button>
        </nav>
      )}
    </div>
  )
}

// Filter dropdowns, labels and the outlined row action button (wireframe #hr-employees)
const filterLabelClass = 'text-sm font-medium text-gray-700'
const filterSelectClass = `
  px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900
  focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px] w-44
`
const pagerButtonClass = `
  px-3 min-h-[44px] rounded-md border border-gray-300 bg-white hover:bg-gray-50
  text-sm font-medium text-gray-700
  focus-visible:ring-2 focus-visible:ring-blue-500
  disabled:opacity-50 disabled:cursor-not-allowed
`
const actionButtonClass = `
  inline-flex items-center justify-center px-3 min-h-[32px] rounded-md
  border border-gray-300 bg-white hover:bg-gray-50
  text-xs font-semibold uppercase tracking-wide text-gray-700
  focus-visible:ring-2 focus-visible:ring-blue-500
`
