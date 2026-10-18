/**
 * @component OnboardingReportPage
 * @description S13 — Onboarding report. Shows employees currently in onboarding.
 * No filters per spec. Rows link to the employee's onboarding wizard.
 * HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/onboarding', element: <OnboardingReportPage /> }
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getOnboardingReport, exportOnboarding } from '../../api/reports'
import type { OnboardingReportRow } from '../../api/reports'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { formatDate } from '../../lib/date'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// IT status badge helper
// ---------------------------------------------------------------------------

/** Maps IT request status codes to badge variants. */
function itStatusVariant(status: string | null): BadgeVariant {
  if (status === null) return 'default'
  switch (status.toUpperCase()) {
    case 'COMPLETED': return 'success'
    case 'IN_PROGRESS':
    case 'SENT': return 'info'
    case 'NOT_SENT': return 'default'
    default: return 'default'
  }
}

// ---------------------------------------------------------------------------
// Column definitions — Employee, Start date, Template, Checklist (%), IT request, Open items
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S13.
 * Employee column links to the employee's onboarding wizard (spec §5 "Rows link to the employee").
 */
const COLUMNS: Column<OnboardingReportRow>[] = [
  {
    key: 'employee',
    header: 'Employee',
    accessor: (r) => r.employeeName,
    render: (r) => (
      <Link
        to={`/employees/${r.employeeId}/onboarding`}
        className="text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        {r.employeeName}
      </Link>
    ),
  },
  {
    key: 'startDate',
    header: 'Start date',
    accessor: (r) => (r.startDate !== null ? formatDate(r.startDate) : null),
  },
  {
    key: 'template',
    header: 'Template',
    accessor: (r) => r.templateName,
  },
  {
    key: 'checklist',
    header: 'Checklist (%)',
    accessor: (r) => r.checklistPct,
    render: (r) => {
      const pct = r.checklistPct
      return (
        <div className="flex items-center gap-2">
          {/* Simple progress bar */}
          <div
            role="progressbar"
            aria-valuenow={pct}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${pct}% complete`}
            className="w-24 bg-gray-200 rounded-full h-2"
          >
            <div
              className="bg-blue-500 h-2 rounded-full"
              style={{ width: `${pct}%` }}
            />
          </div>
          <span className="text-xs text-gray-600">{pct}%</span>
        </div>
      )
    },
  },
  {
    key: 'itRequest',
    header: 'IT request',
    accessor: (r) => r.itStatus ?? '—',
    render: (r) =>
      r.itStatus !== null ? (
        <Badge label={r.itStatus.replace(/_/g, ' ')} variant={itStatusVariant(r.itStatus)} />
      ) : (
        <span className="text-gray-400">—</span>
      ),
  },
  {
    key: 'openItems',
    header: 'Open items',
    accessor: (r) => r.openItems,
    render: (r) => {
      const count = r.openItems
      const colour =
        count > 5
          ? 'text-red-600 font-medium'
          : count > 0
            ? 'text-orange-600 font-medium'
            : 'text-gray-900'
      return <span className={colour}>{count}</span>
    },
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/** OnboardingReportPage fetches all IN_PROGRESS onboarding records. No filter controls. */
export function OnboardingReportPage(): ReactElement {
  const { showToast } = useToast()

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'onboarding'],
    queryFn: getOnboardingReport,
  })

  const handleExport = (): void => {
    exportOnboarding().catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Onboarding
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        emptyMessage="No employees in onboarding."
      />
    </div>
  )
}
