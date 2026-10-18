/**
 * @component UnreturnedAssetsReportPage
 * @description S15 — Unreturned Assets report. Shows assets still with leavers or due for return.
 * Filter: Status dropdown (RETURN_DUE / NOT_RETURNED). HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/unreturned-assets', element: <UnreturnedAssetsReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getUnreturnedAssets, exportUnreturnedAssets } from '../../api/reports'
import type { UnreturnedAssetsReportRow } from '../../api/reports'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { formatDate } from '../../lib/date'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Status display helpers
// ---------------------------------------------------------------------------

/** Human-readable labels for backend asset status codes. */
const ASSET_STATUS_LABELS: Record<string, string> = {
  RETURN_DUE: 'Due',
  NOT_RETURNED: 'Not returned',
}

/** Maps asset status codes to badge variants. */
function assetStatusVariant(status: string | null): BadgeVariant {
  if (status === null) return 'default'
  switch (status) {
    case 'NOT_RETURNED': return 'danger'
    case 'RETURN_DUE': return 'warning'
    default: return 'default'
  }
}

// ---------------------------------------------------------------------------
// Column definitions — Asset no., Asset, Employee, Last working day, Status
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S15.
 * Employee column links to the employee record when the employee ID is available.
 */
const COLUMNS: Column<UnreturnedAssetsReportRow>[] = [
  {
    key: 'assetNumber',
    header: 'Asset no.',
    accessor: (r) => r.assetNumber ?? '—',
  },
  {
    key: 'asset',
    header: 'Asset',
    accessor: (r) => r.assetDescription ?? '—',
  },
  {
    key: 'employee',
    header: 'Employee',
    accessor: (r) => r.employeeName ?? '—',
    render: (r) =>
      r.employeeId !== null && r.employeeName !== null ? (
        <Link
          to={`/employees/${r.employeeId}`}
          className="text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
        >
          {r.employeeName}
        </Link>
      ) : (
        <span className="text-gray-400">—</span>
      ),
  },
  {
    key: 'lastWorkingDay',
    header: 'Last working day',
    accessor: (r) => (r.lastWorkingDay !== null ? formatDate(r.lastWorkingDay) : null),
  },
  {
    key: 'status',
    header: 'Status',
    accessor: (r) =>
      r.assetStatus !== null ? (ASSET_STATUS_LABELS[r.assetStatus] ?? r.assetStatus) : '—',
    render: (r) =>
      r.assetStatus !== null ? (
        <Badge
          label={ASSET_STATUS_LABELS[r.assetStatus] ?? r.assetStatus}
          variant={assetStatusVariant(r.assetStatus)}
        />
      ) : (
        <span className="text-gray-400">—</span>
      ),
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/** UnreturnedAssetsReportPage fetches unreturned assets, filtered by status when set. */
export function UnreturnedAssetsReportPage(): ReactElement {
  const { showToast } = useToast()
  const [status, setStatus] = useState('')

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'unreturned-assets', { status }],
    queryFn: () => getUnreturnedAssets(status !== '' ? { status } : {}),
  })

  const handleExport = (): void => {
    exportUnreturnedAssets(status !== '' ? { status } : {}).catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="asset-status-filter" className="sr-only">Filter by status</label>
      <select
        id="asset-status-filter"
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Status [All]</option>
        <option value="NOT_RETURNED">Not returned</option>
        <option value="RETURN_DUE">Due</option>
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Unreturned assets
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search assets or employees…"
        filters={filters}
        emptyMessage="No unreturned assets found."
      />
    </div>
  )
}
