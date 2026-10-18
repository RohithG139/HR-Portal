/**
 * @component PipsReportPage
 * @description S12 — PIPs report. Shows active and pending improvement plans.
 * Filter: Status dropdown (server-side). HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/pips', element: <PipsReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getPips, exportPips } from '../../api/reports'
import type { PipReportRow } from '../../api/reports'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { formatDate } from '../../lib/date'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Status badge helper
// ---------------------------------------------------------------------------

/** Maps PIP status codes to badge variants (R-UI-6: due-date colours). */
function pipStatusVariant(status: string | null): BadgeVariant {
  if (status === null) return 'default'
  switch (status.toUpperCase()) {
    case 'ACTIVE': return 'danger'
    case 'AWAITING':
    case 'IN_HR_REVIEW':
    case 'DRAFT': return 'warning'
    case 'COMPLETED':
    case 'PASSED': return 'success'
    default: return 'default'
  }
}

// ---------------------------------------------------------------------------
// Column definitions — Employee, Manager, Status, Started, Final review, Days left, Extensions
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S12.
 * Employee links to the employee record (spec §5 "Rows link to the employee").
 * Days left is orange when ≤ 3, in line with R-UI-6 (due-date colours).
 */
const COLUMNS: Column<PipReportRow>[] = [
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
    key: 'manager',
    header: 'Manager',
    accessor: (r) => r.managerName ?? '—',
  },
  {
    key: 'status',
    header: 'Status',
    accessor: (r) => r.status ?? '—',
    render: (r) =>
      r.status !== null ? (
        <Badge label={r.status} variant={pipStatusVariant(r.status)} />
      ) : (
        <span className="text-gray-400">—</span>
      ),
  },
  {
    key: 'started',
    header: 'Started',
    accessor: (r) => (r.startDate !== null ? formatDate(r.startDate) : null),
  },
  {
    key: 'finalReview',
    header: 'Final review',
    accessor: (r) => (r.finalReviewDate !== null ? formatDate(r.finalReviewDate) : null),
  },
  {
    key: 'daysLeft',
    header: 'Days left',
    accessor: (r) => r.daysLeft ?? '—',
    // R-UI-6: orange when ≤ 3, red when overdue (negative)
    render: (r) => {
      if (r.daysLeft === null) return <span className="text-gray-400">—</span>
      const colour =
        r.daysLeft < 0
          ? 'text-red-600 font-medium'
          : r.daysLeft <= 3
            ? 'text-orange-600 font-medium'
            : 'text-gray-900'
      return <span className={colour}>{r.daysLeft}</span>
    },
  },
  {
    key: 'extensions',
    header: 'Extensions',
    accessor: (r) => r.extensionCount,
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/** PipsReportPage fetches PIPs data, filtered by status when set. */
export function PipsReportPage(): ReactElement {
  const { showToast } = useToast()
  const [status, setStatus] = useState('')

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'pips', { status }],
    queryFn: () => getPips(status !== '' ? { status } : {}),
  })

  const handleExport = (): void => {
    exportPips(status !== '' ? { status } : {}).catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="pip-status-filter" className="sr-only">Filter by status</label>
      <select
        id="pip-status-filter"
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Status [All]</option>
        <option value="ACTIVE">Active</option>
        <option value="AWAITING">Awaiting</option>
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        PIPs
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No PIPs found."
      />
    </div>
  )
}
