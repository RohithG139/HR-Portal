/**
 * @component AllocationReportPage
 * @description S10 — Project Allocation report. Shows current allocation per active employee.
 * Filter: Status dropdown (server-side). Search handled client-side by Table.
 * Rows link to the employee record. HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/allocation', element: <AllocationReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getAllocation, exportAllocation } from '../../api/reports'
import type { AllocationReportRow } from '../../api/reports'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Status display labels and badge variants
// ---------------------------------------------------------------------------

/** Human-readable labels for backend allocation status codes. */
const STATUS_LABELS: Record<string, string> = {
  ON_BENCH: 'On bench',
  PARTLY_ALLOCATED: 'Partly available',
  FULLY_ALLOCATED: 'Fully allocated',
  OVER_ALLOCATED: 'Over allocated',
}

/** Maps allocation status codes to Badge variants. */
function allocationVariant(status: string): BadgeVariant {
  switch (status) {
    case 'ON_BENCH':
      return 'default'
    case 'PARTLY_ALLOCATED':
      return 'warning'
    case 'FULLY_ALLOCATED':
      return 'success'
    case 'OVER_ALLOCATED':
      return 'danger'
    default:
      return 'default'
  }
}

// ---------------------------------------------------------------------------
// Column definitions — Employee, Projects, Allocation %, Status
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S10.
 * Employee column links to employee record (spec §5 "Rows link to the employee").
 */
const COLUMNS: Column<AllocationReportRow>[] = [
  {
    key: 'employee',
    header: 'Employee',
    accessor: (r) => r.employeeName,
    render: (r) => (
      <Link
        to={`/employees/${r.employeeId}`}
        className="text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        {r.employeeName}
      </Link>
    ),
  },
  {
    key: 'projects',
    header: 'Projects',
    accessor: (r) => r.projects ?? '—',
  },
  {
    key: 'allocation',
    header: 'Allocation %',
    accessor: (r) => r.totalAllocationPct,
    render: (r) => `${r.totalAllocationPct}%`,
  },
  {
    key: 'status',
    header: 'Status',
    accessor: (r) => STATUS_LABELS[r.allocationStatus] ?? r.allocationStatus,
    render: (r) => (
      <Badge
        label={STATUS_LABELS[r.allocationStatus] ?? r.allocationStatus}
        variant={allocationVariant(r.allocationStatus)}
      />
    ),
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * AllocationReportPage:
 * - Status filter is server-side (queryKey includes status, refetches on change).
 * - Free-text search is client-side via the Table component's built-in search.
 * - Export passes the current status filter so the Excel matches the screen (AC-05).
 */
export function AllocationReportPage(): ReactElement {
  const { showToast } = useToast()
  const [status, setStatus] = useState('')

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'allocation', { status }],
    queryFn: () => getAllocation(status !== '' ? { status } : {}),
  })

  const handleExport = (): void => {
    exportAllocation(status !== '' ? { status } : {}).catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="status-filter" className="sr-only">
        Filter by status
      </label>
      <select
        id="status-filter"
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Status [All]</option>
        <option value="ON_BENCH">On bench</option>
        <option value="PARTLY_ALLOCATED">Partly available</option>
        <option value="FULLY_ALLOCATED">Fully allocated</option>
        <option value="OVER_ALLOCATED">Over allocated</option>
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Project allocation
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No allocation data found."
      />
    </div>
  )
}
