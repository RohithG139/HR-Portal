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
import { getSummary } from '../../api/reports'

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
  { title: 'Appraisal status', description: 'Completion, client feedback & outcomes', to: '/reports/appraisal-status' },
  { title: 'PIPs', description: 'Active and pending improvement plans', to: '/reports/pips' },
  { title: 'Onboarding', description: 'Employees in onboarding and their progress', to: '/reports/onboarding' },
  { title: 'Contracts & probation', description: 'Upcoming probation ends, contract ends and PIP reviews', to: '/reports/contracts-probation' },
  { title: 'Unreturned assets', description: 'Assets still with leavers or due for return', to: '/reports/unreturned-assets' },
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
  const { data: summary, isLoading } = useQuery({
    queryKey: ['reports', 'summary'],
    queryFn: getSummary,
  })

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-8">
      {/* Page heading */}
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Reports
      </h1>

      {/* KPI tiles — 4 tiles from GET /api/reports/summary (C31) */}
      <section aria-label="Key performance indicators">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiTile
            label="Headcount"
            value={isLoading ? '…' : String(summary?.totalHeadcount ?? '—')}
            to="/reports/headcount"
          />
          <KpiTile
            label="Bench"
            value={isLoading ? '…' : String(summary?.benchCount ?? '—')}
            to="/reports/bench"
          />
          <KpiTile
            label="Appraisal completion"
            value={isLoading ? '…' : `${summary?.appraisalCompletionRate.toFixed(0) ?? '—'}%`}
            to="/reports/appraisal-status"
          />
          <KpiTile
            label="Unreturned assets"
            value={isLoading ? '…' : String(summary?.unreturnedAssetCount ?? '—')}
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
                block
                bg-white
                border border-gray-200
                rounded-lg p-4 sm:p-5
                hover:border-blue-400
                hover:shadow-sm
                focus-visible:ring-2 focus-visible:ring-blue-500
                transition-colors
                min-h-[44px]
              "
            >
              <h2 className="text-sm sm:text-base font-semibold text-gray-900 mb-1">
                {card.title}
              </h2>
              <p className="text-xs sm:text-sm text-gray-500 mb-3">
                {card.description}
              </p>
              <span className="text-xs font-medium text-blue-600">
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
  to: string
}

/**
 * KpiTile renders a single KPI number as a clickable card.
 * The card links to the corresponding detail report for drill-down.
 */
function KpiTile({ label, value, to }: KpiTileProps): ReactElement {
  return (
    <Link
      to={to}
      className="
        block
        bg-white
        border border-gray-200
        rounded-lg p-4 sm:p-5
        hover:border-blue-400
        hover:shadow-sm
        focus-visible:ring-2 focus-visible:ring-blue-500
        transition-colors
        min-h-[44px]
      "
    >
      <p className="text-xs sm:text-sm text-gray-500 mb-1">{label}</p>
      <p className="text-2xl sm:text-3xl font-bold text-gray-900">{value}</p>
    </Link>
  )
}
