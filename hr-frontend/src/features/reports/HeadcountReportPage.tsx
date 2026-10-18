/**
 * @component HeadcountReportPage
 * @description S8 — Headcount report. Sortable, searchable, exportable table of employee
 * counts by department. Filter: Department dropdown. HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/headcount', element: <HeadcountReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getHeadcount, exportHeadcount } from '../../api/reports'
import type { HeadcountReportRow } from '../../api/reports'
import { listDepartments } from '../../api/settingsDepartments'
import { Table, useToast } from '../../components/index'
import type { Column } from '../../components/index'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Column definitions
// ---------------------------------------------------------------------------

/**
 * Headcount columns per spec §5 S8:
 * Department, Employees, Joiners (this year), Leavers (this year), Permanent, Fixed-term.
 */
const COLUMNS: Column<HeadcountReportRow>[] = [
  {
    key: 'department',
    header: 'Department',
    accessor: (r) => r.departmentName,
  },
  {
    key: 'employees',
    header: 'Employees',
    accessor: (r) => r.employeeCount,
  },
  {
    key: 'joiners',
    header: 'Joiners (this year)',
    accessor: (r) => r.joinersThisYear,
  },
  {
    key: 'leavers',
    header: 'Leavers (this year)',
    accessor: (r) => r.leaversThisYear,
  },
  {
    key: 'permanent',
    header: 'Permanent',
    accessor: (r) => r.permanentCount,
  },
  {
    key: 'fixedTerm',
    header: 'Fixed-term',
    accessor: (r) => r.fixedTermCount,
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * HeadcountReportPage:
 * - Fetches departments for the filter dropdown using the existing settingsDepartments API.
 * - Refetches the report data when the departmentId filter changes (queryKey includes it).
 * - Export button calls exportHeadcount() with the current filter state (AC-05).
 */
export function HeadcountReportPage(): ReactElement {
  const { showToast } = useToast()
  const [departmentId, setDepartmentId] = useState('')

  // Fetch department list for the filter dropdown.
  // Re-uses the same GET /api/settings/departments endpoint the Settings tab uses.
  const { data: departments } = useQuery({
    queryKey: ['settings', 'departments'],
    queryFn: listDepartments,
  })

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'headcount', { departmentId }],
    queryFn: () =>
      getHeadcount(departmentId !== '' ? { departmentId: Number(departmentId) } : {}),
  })

  // Derive summary tiles from the returned rows (computed from source, not stored — R-DB-3)
  const total = rows.reduce((sum, r) => sum + r.employeeCount, 0)
  const joinersTotal = rows.reduce((sum, r) => sum + r.joinersThisYear, 0)
  const leaversTotal = rows.reduce((sum, r) => sum + r.leaversThisYear, 0)

  // Export sends the same departmentId filter so the Excel matches the screen (AC-05 / spec §7)
  const handleExport = (): void => {
    exportHeadcount(departmentId !== '' ? { departmentId: Number(departmentId) } : {}).catch(
      (err: unknown) => {
        const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
        showToast(msg, 'error')
      },
    )
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="dept-filter" className="sr-only">
        Filter by department
      </label>
      <select
        id="dept-filter"
        value={departmentId}
        onChange={(e) => setDepartmentId(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Department [All]</option>
        {departments?.map((d) => (
          <option key={d.id} value={String(d.id)}>
            {d.name}
          </option>
        ))}
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Page heading */}
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Headcount
      </h1>

      {/* Summary tiles derived from current rows */}
      {!isLoading && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <SummaryTile label="Total" value={String(total)} />
          <SummaryTile label={`Joiners ${new Date().getFullYear()}`} value={String(joinersTotal)} />
          <SummaryTile label={`Leavers ${new Date().getFullYear()}`} value={String(leaversTotal)} />
        </div>
      )}

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search departments…"
        filters={filters}
        emptyMessage="No headcount data found."
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Internal SummaryTile
// ---------------------------------------------------------------------------

interface SummaryTileProps {
  label: string
  value: string
}

/** Small summary stat tile used at the top of the headcount report (wireframe S8). */
function SummaryTile({ label, value }: SummaryTileProps): ReactElement {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-xl font-bold text-gray-900">{value}</p>
    </div>
  )
}
