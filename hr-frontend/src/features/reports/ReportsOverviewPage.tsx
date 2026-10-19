/**
 * @component ReportsOverviewPage
 * @description S7 — Reports overview. Shows 4 KPI tiles from GET /api/reports/summary and
 * 8 clickable report cards linking to each detail report (S8–S15). HR-only (FRD-08 §2).
 * @props (none — reads summary from React Query)
 * @example
 * Route: { path: '/reports', element: <ReportsOverviewPage /> }
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getSummary, exportAllReports } from '../../api/reports'
import type { SummaryDto } from '../../api/reports'
import { ApiResponseError } from '../../api/types'
import { useToast } from '../../components/index'

// ---------------------------------------------------------------------------
// Report card definitions (8 cards, one per detail report)
// ---------------------------------------------------------------------------

interface ReportCard {
  title: string
  description: string
  to: string
}

const REPORT_CARDS: ReportCard[] = [
  { title: 'Headcount', description: 'By department, joiners & leavers', to: '/reports/headcount' },
  { title: 'Bench', description: 'Employees without an active project', to: '/reports/bench' },
  { title: 'Project allocation', description: 'Allocation % per employee', to: '/reports/allocation' },
  { title: 'Appraisal status', description: 'Completion, client feedback and outcomes', to: '/reports/appraisal-status' },
  { title: 'PIPs', description: 'Active and pending improvement plans', to: '/reports/pips' },
  { title: 'Onboarding', description: 'Open checklist items per joiner', to: '/reports/onboarding' },
  { title: 'Contracts & probation', description: 'End dates in the next 90 days', to: '/reports/contracts-probation' },
  { title: 'Unreturned assets', description: 'Assets still with leavers', to: '/reports/unreturned-assets' },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * ReportsOverviewPage fetches KPI summary data with React Query.
 *
 * The four KPI tiles use staleTime inherited from the QueryClient default.
 * The 8 report cards are static — they link to the corresponding route where
 * the user can drill into filtered/exportable table data.
 */
export function ReportsOverviewPage(): ReactElement {
  const { showToast } = useToast()
  const { data: summary, isLoading } = useQuery({
    queryKey: ['reports', 'summary'],
    queryFn: getSummary,
  })
  const meta = summary ? kpiMeta(summary) : null

  // C48 — one workbook with every report (wireframe "Export all to Excel")
  const handleExportAll = (): void => {
    exportAllReports().catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-4">
      {/* Page heading + export all */}
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl sm:text-2xl font-bold text-gray-900">
          Reports
        </h1>
        <button
          type="button"
          onClick={handleExportAll}
          title="Export all to Excel"
          aria-label="Export all to Excel"
          className="
            inline-flex items-center justify-center
            w-[38px] h-[38px] rounded-lg
            border-[1.5px] border-gray-300 bg-white text-gray-600
            hover:border-blue-900 hover:text-blue-900 hover:bg-gray-50
            focus-visible:ring-2 focus-visible:ring-blue-500
          "
        >
          <DownloadIcon />
        </button>
      </div>

      {/* KPI tiles — 4 tiles from GET /api/reports/summary (C31) */}
      <section aria-label="Key performance indicators">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiTile
            label="Headcount"
            value={isLoading ? '…' : String(summary?.totalHeadcount ?? '—')}
            meta={meta?.headcount}
            to="/reports/headcount"
          />
          <KpiTile
            label="Bench"
            value={isLoading ? '…' : String(summary?.benchCount ?? '—')}
            meta={meta?.bench}
            to="/reports/bench"
          />
          <KpiTile
            label="Appraisal completion"
            value={isLoading ? '…' : `${summary?.appraisalCompletionRate.toFixed(0) ?? '—'}%`}
            meta={meta?.appraisal}
            to="/reports/appraisal-status"
          />
          <KpiTile
            label="Unreturned assets"
            value={isLoading ? '…' : String(summary?.unreturnedAssetCount ?? '—')}
            meta={meta?.assets}
            to="/reports/unreturned-assets"
          />
        </div>
      </section>

      {/* 8 report cards — grid of clickable cards linking to each detail report */}
      <section aria-label="Report categories">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {REPORT_CARDS.map((card) => (
            <Link
              key={card.to}
              to={card.to}
              className="
                flex flex-col gap-1.5
                bg-white
                border border-gray-200
                rounded-xl p-5
                shadow-[0_1px_3px_rgba(15,23,42,0.06),0_1px_2px_rgba(15,23,42,0.04)]
                hover:border-blue-600
                focus-visible:ring-2 focus-visible:ring-blue-500
                transition-colors
                min-h-[44px]
              "
            >
              <h2 className="text-sm font-bold text-gray-900">
                {card.title}
              </h2>
              <p className="text-xs text-gray-500">
                {card.description}
              </p>
              <span className="mt-auto pt-2 text-xs font-semibold text-blue-600">
                Open report →
              </span>
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}

// ---------------------------------------------------------------------------
// KpiTile — internal component (not exported, not reusable outside this page)
// ---------------------------------------------------------------------------

interface KpiTileProps {
  label: string
  value: string
  /** Small caption under the number, e.g. "+2 vs last month" */
  meta?: string
  to: string
}

/**
 * KpiTile renders a single KPI number as a clickable card.
 * The card links to the corresponding detail report for drill-down.
 */
function KpiTile({ label, value, meta, to }: KpiTileProps): ReactElement {
  return (
    <Link
      to={to}
      className="
        block
        bg-white
        border border-gray-200
        rounded-lg px-[18px] py-4
        shadow-[0_1px_3px_rgba(15,23,42,0.06),0_1px_2px_rgba(15,23,42,0.04)]
        hover:border-blue-600
        focus-visible:ring-2 focus-visible:ring-blue-500
        transition-colors
        min-h-[44px]
      "
    >
      <p className="text-[11px] font-semibold uppercase tracking-[0.5px] text-gray-500 mb-1.5">{label}</p>
      <p className="text-[22px] font-bold text-gray-900 tabular-nums mb-1">{value}</p>
      {meta && <p className="text-[11px] text-gray-500">{meta}</p>}
    </Link>
  )
}

/**
 * Wireframe captions under each KPI number, derived from the C31 summary.
 * Exported for unit tests.
 */
export function kpiMeta(s: SummaryDto): {
  headcount: string
  bench: string
  appraisal: string | undefined
  assets: string
} {
  const change = s.headcountChangeVsLastMonth
  const benchPct = s.billableStaffCount > 0
    ? Math.round((s.benchCount / s.billableStaffCount) * 100)
    : 0
  const ex = s.unreturnedAssetExEmployeeCount
  return {
    headcount: `${change > 0 ? '+' : ''}${change} vs last month`,
    bench: `${benchPct}% of billable staff`,
    appraisal: s.appraisalRoundYear !== null ? `Round ${s.appraisalRoundYear}` : undefined,
    assets: `${ex} ex-employee${ex === 1 ? '' : 's'}`,
  }
}

/** Download-tray icon, same outline as the wireframe's export button. */
function DownloadIcon(): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </svg>
  )
}
