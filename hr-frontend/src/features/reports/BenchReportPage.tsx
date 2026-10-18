/**
 * @component BenchReportPage
 * @description S9 — Bench report. Shows employees with no active project assignment.
 * Filters: Manager dropdown (server-side). Search handled client-side by Table.
 * Rows link to the employee record. HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/bench', element: <BenchReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getBench, exportBench } from '../../api/reports'
import type { BenchReportRow } from '../../api/reports'
import { getOnboardingLookups } from '../../api/lookups'
import { Table, useToast } from '../../components/index'
import type { Column } from '../../components/index'
import { formatDate } from '../../lib/date'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Column definitions — Employee, Job title, Manager, On bench since, Days, Last project
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S9.
 * The Employee column links to the employee record (spec §5 "Rows link to the employee").
 * formatDate converts ISO dates to dd-mm-yyyy (R-UI-5).
 */
const COLUMNS: Column<BenchReportRow>[] = [
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
    key: 'jobTitle',
    header: 'Job title',
    accessor: (r) => r.jobTitle,
  },
  {
    key: 'manager',
    header: 'Manager',
    accessor: (r) => r.managerName ?? '—',
  },
  {
    key: 'benchSince',
    header: 'On bench since',
    accessor: (r) => (r.benchSince !== null ? formatDate(r.benchSince) : null),
  },
  {
    key: 'days',
    header: 'Days',
    accessor: (r) => r.daysOnBench ?? '—',
  },
  {
    key: 'lastProject',
    header: 'Last project',
    accessor: (r) => r.lastProjectName ?? '—',
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * BenchReportPage:
 * - Manager filter is server-side: changing managerId updates the queryKey and refetches.
 * - Free-text search is client-side via the Table component's built-in search input.
 * - Export passes the current managerId filter so the Excel matches the screen (AC-05).
 */
export function BenchReportPage(): ReactElement {
  const { showToast } = useToast()
  const [managerId, setManagerId] = useState('')

  // Fetch manager list from the onboarding lookups endpoint (same source as the
  // onboarding form manager dropdown — no new endpoint needed).
  const { data: lookups } = useQuery({
    queryKey: ['lookups', 'onboarding'],
    queryFn: getOnboardingLookups,
  })

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'bench', { managerId }],
    queryFn: () =>
      getBench(managerId !== '' ? { managerId: Number(managerId) } : {}),
  })

  const handleExport = (): void => {
    exportBench(managerId !== '' ? { managerId: Number(managerId) } : {}).catch(
      (err: unknown) => {
        const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
        showToast(msg, 'error')
      },
    )
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="mgr-filter" className="sr-only">
        Filter by manager
      </label>
      <select
        id="mgr-filter"
        value={managerId}
        onChange={(e) => setManagerId(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Manager [All]</option>
        {lookups?.managers.map((m) => (
          <option key={m.id} value={String(m.id)}>
            {m.name}
          </option>
        ))}
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Bench
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No employees on bench."
      />
    </div>
  )
}
