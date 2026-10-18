/**
 * @component AppraisalStatusReportPage
 * @description S11 — Appraisal Status report. Shows per-employee appraisal progress.
 * Filters: Manager dropdown, Result dropdown (both server-side). Search client-side.
 * HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/appraisal-status', element: <AppraisalStatusReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getAppraisalStatus, exportAppraisalStatus } from '../../api/reports'
import type { AppraisalStatusReportRow } from '../../api/reports'
import { getOnboardingLookups } from '../../api/lookups'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Status/result display helpers
// ---------------------------------------------------------------------------

/** Maps appraisal result codes to display labels. */
const RESULT_LABELS: Record<string, string> = {
  PROMOTED: 'Promoted',
  NO_CHANGE: 'No change',
  UNDER_PERFORMING: 'Under performing',
}

/** Maps appraisal result codes to badge variants. */
function resultVariant(result: string | null): BadgeVariant {
  if (result === null) return 'default'
  switch (result) {
    case 'PROMOTED': return 'success'
    case 'UNDER_PERFORMING': return 'danger'
    default: return 'info'
  }
}

/** Maps peer submission counts to a "x / y" display string. */
function peersDisplay(submitted: number, total: number): string {
  return `${submitted} / ${total}`
}

// ---------------------------------------------------------------------------
// Column definitions
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S11:
 * Employee, Manager, Self, Peers, Manager review, Client feedback (avg · reviews), Result.
 * Employee column links to the employee record.
 */
const COLUMNS: Column<AppraisalStatusReportRow>[] = [
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
    key: 'self',
    header: 'Self',
    accessor: (r) => r.selfStatus ?? '—',
  },
  {
    key: 'peers',
    header: 'Peers',
    accessor: (r) => peersDisplay(r.peersSubmitted, r.peersTotal),
  },
  {
    key: 'managerReview',
    header: 'Manager review',
    accessor: (r) => r.managerReviewStatus ?? '—',
  },
  {
    key: 'clientFeedback',
    header: 'Client feedback',
    accessor: (r) =>
      r.clientFeedbackAvg !== null
        ? `${r.clientFeedbackAvg.toFixed(1)} avg · ${r.clientFeedbackCount} reviews`
        : '—',
  },
  {
    key: 'result',
    header: 'Result',
    accessor: (r) => (r.result !== null ? (RESULT_LABELS[r.result] ?? r.result) : '—'),
    render: (r) =>
      r.result !== null ? (
        <Badge
          label={RESULT_LABELS[r.result] ?? r.result}
          variant={resultVariant(r.result)}
        />
      ) : (
        <span className="text-gray-400">—</span>
      ),
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * AppraisalStatusReportPage:
 * - Manager and Result filters are server-side (queryKey includes both, refetches on change).
 * - Free-text search is client-side via the Table component.
 * - Summary tiles (Completion %, Finalised, Promotions, Avg client score) are derived
 * from the currently-loaded rows — they reflect the active filter state.
 * - Export passes current filters so the Excel matches the screen (AC-05).
 */
export function AppraisalStatusReportPage(): ReactElement {
  const { showToast } = useToast()
  const [managerId, setManagerId] = useState('')
  const [result, setResult] = useState('')

  const { data: lookups } = useQuery({
    queryKey: ['lookups', 'onboarding'],
    queryFn: getOnboardingLookups,
  })

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'appraisal-status', { managerId, result }],
    queryFn: () =>
      getAppraisalStatus({
        ...(managerId !== '' ? { managerId: Number(managerId) } : {}),
        ...(result !== '' ? { result } : {}),
      }),
  })

  // Derive summary stats from current rows
  const finalisedCount = rows.filter((r) => r.appraisalStatus === 'FINALISED').length
  const promotionsCount = rows.filter((r) => r.result === 'PROMOTED').length
  const completionPct =
    rows.length > 0 ? Math.round((finalisedCount / rows.length) * 100) : 0
  const avgClientScores = rows
    .map((r) => r.clientFeedbackAvg)
    .filter((v): v is number => v !== null)
  const avgClientScore =
    avgClientScores.length > 0
      ? (avgClientScores.reduce((s, v) => s + v, 0) / avgClientScores.length).toFixed(1)
      : null

  const handleExport = (): void => {
    exportAppraisalStatus({
      ...(managerId !== '' ? { managerId: Number(managerId) } : {}),
      ...(result !== '' ? { result } : {}),
    }).catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  const filters: ReactNode = (
    <div className="flex gap-2 flex-wrap">
      <div>
        <label htmlFor="apr-mgr-filter" className="sr-only">Filter by manager</label>
        <select
          id="apr-mgr-filter"
          value={managerId}
          onChange={(e) => setManagerId(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
        >
          <option value="">Manager [All]</option>
          {lookups?.managers.map((m) => (
            <option key={m.id} value={String(m.id)}>{m.name}</option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="apr-result-filter" className="sr-only">Filter by result</label>
        <select
          id="apr-result-filter"
          value={result}
          onChange={(e) => setResult(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
        >
          <option value="">Result [All]</option>
          <option value="PROMOTED">Promoted</option>
          <option value="NO_CHANGE">No change</option>
          <option value="UNDER_PERFORMING">Under performing</option>
        </select>
      </div>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Appraisal status
      </h1>

      {/* Summary tiles derived from current rows (spec wireframe S11) */}
      {!isLoading && rows.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <AppraisalSummaryTile label="Completion" value={`${completionPct}%`} />
          <AppraisalSummaryTile label="Finalised" value={`${finalisedCount} / ${rows.length}`} />
          <AppraisalSummaryTile label="Promotions" value={String(promotionsCount)} />
          <AppraisalSummaryTile
            label="Avg client score"
            value={avgClientScore !== null ? `${avgClientScore} / 5` : '—'}
          />
        </div>
      )}

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No appraisal data found."
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Internal summary tile
// ---------------------------------------------------------------------------

interface AppraisalSummaryTileProps {
  label: string
  value: string
}

function AppraisalSummaryTile({ label, value }: AppraisalSummaryTileProps): ReactElement {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-bold text-gray-900">{value}</p>
    </div>
  )
}
