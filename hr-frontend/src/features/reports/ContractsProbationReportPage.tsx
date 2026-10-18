/**
 * @component ContractsProbationReportPage
 * @description S14 — Contracts & Probation report. Shows upcoming probation ends,
 * contract ends and final PIP reviews within the next 90 days.
 * Filter: Type dropdown (server-side). HR-only (FRD-08 §2, R-UI-2).
 * @props (none — reads from React Query)
 * @example
 * Route: { path: '/reports/contracts-probation', element: <ContractsProbationReportPage /> }
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getContractsProbation, exportContractsProbation } from '../../api/reports'
import type { ContractsProbationReportRow } from '../../api/reports'
import { Table, Badge, useToast } from '../../components/index'
import type { Column, BadgeVariant } from '../../components/index'
import { formatDate } from '../../lib/date'
import { ApiResponseError } from '../../api/types'

// ---------------------------------------------------------------------------
// Type display helpers
// ---------------------------------------------------------------------------

/** Human-readable event type labels. */
const TYPE_LABELS: Record<string, string> = {
  PROBATION: 'Probation',
  CONTRACT: 'Contract',
  PIP: 'Final PIP review',
}

/** Maps event type to badge variant. */
function eventTypeVariant(eventType: string): BadgeVariant {
  switch (eventType) {
    case 'PIP': return 'danger'
    case 'PROBATION': return 'warning'
    case 'CONTRACT': return 'info'
    default: return 'default'
  }
}

// ---------------------------------------------------------------------------
// Column definitions — Employee, Type, Date, Days, Owner, Status
// ---------------------------------------------------------------------------

/**
 * Columns per spec §5 S14.
 * Employee links to the employee record.
 * Days column is coloured per R-UI-6 (≤ 3 = orange, overdue = red).
 */
const COLUMNS: Column<ContractsProbationReportRow>[] = [
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
    key: 'type',
    header: 'Type',
    accessor: (r) => TYPE_LABELS[r.eventType] ?? r.eventType,
    render: (r) => (
      <Badge
        label={TYPE_LABELS[r.eventType] ?? r.eventType}
        variant={eventTypeVariant(r.eventType)}
      />
    ),
  },
  {
    key: 'date',
    header: 'Date',
    accessor: (r) => (r.eventDate !== null ? formatDate(r.eventDate) : null),
  },
  {
    key: 'days',
    header: 'Days',
    accessor: (r) => r.daysUntil,
    render: (r) => {
      const days = r.daysUntil
      const colour =
        days < 0
          ? 'text-red-600 font-medium'
          : days <= 3
            ? 'text-orange-600 font-medium'
            : 'text-gray-900'
      return <span className={colour}>{days}</span>
    },
  },
  {
    key: 'owner',
    header: 'Owner',
    accessor: (r) => r.ownerRole ?? '—',
  },
  {
    key: 'status',
    header: 'Status',
    accessor: (r) => r.eventStatus ?? '—',
  },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/** ContractsProbationReportPage fetches upcoming date events, filtered by type when set. */
export function ContractsProbationReportPage(): ReactElement {
  const { showToast } = useToast()
  const [type, setType] = useState('')

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ['reports', 'contracts-probation', { type }],
    queryFn: () => getContractsProbation(type !== '' ? { type } : {}),
  })

  const handleExport = (): void => {
    exportContractsProbation(type !== '' ? { type } : {}).catch((err: unknown) => {
      const msg = err instanceof ApiResponseError ? err.body.code : 'Export failed.'
      showToast(msg, 'error')
    })
  }

  const filters: ReactNode = (
    <div>
      <label htmlFor="cp-type-filter" className="sr-only">Filter by type</label>
      <select
        id="cp-type-filter"
        value={type}
        onChange={(e) => setType(e.target.value)}
        className="px-3 py-2 border border-gray-300 rounded-md text-sm bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
      >
        <option value="">Type [All]</option>
        <option value="PROBATION">Probation</option>
        <option value="CONTRACT">Contract</option>
        <option value="PIP">Final PIP review</option>
      </select>
    </div>
  )

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Contracts &amp; probation
      </h1>

      <Table
        columns={COLUMNS}
        rows={rows}
        onExport={handleExport}
        isLoading={isLoading}
        searchPlaceholder="Search employees…"
        filters={filters}
        emptyMessage="No upcoming events in the next 90 days."
      />
    </div>
  )
}
