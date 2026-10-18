/**
 * @component HistoryTab
 * @description Employee record — History tab (S11, A35).
 * Paginated audit log (HR only). Shows who changed what and when.
 * Milestones are merged into the stream by the backend.
 * @props employeeId - number - the employee whose history to show
 * @example
 * <HistoryTab employeeId={42} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getEmployeeHistory } from '../../../api/employees'
import { formatDate } from '../../../lib/date'

interface HistoryTabProps {
  employeeId: number
}

/**
 * HistoryTab renders a paginated table of audit events from A35.
 *
 * Pagination uses client-controlled `page` state. Each page is fetched
 * independently so previous pages stay in React Query's cache.
 */
export function HistoryTab({ employeeId }: HistoryTabProps): ReactElement {
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['employee-history', employeeId, page],
    queryFn: () => getEmployeeHistory(employeeId, page),
  })

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-3">
        {Array.from({ length: 5 }, (_, i) => (
          <div key={i} className="h-10 bg-gray-200 rounded" />
        ))}
      </div>
    )
  }

  if (data === undefined || data.content.length === 0) {
    return (
      <p className="text-sm text-gray-400 py-4">No history entries yet.</p>
    )
  }

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto rounded border border-gray-200">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs uppercase text-gray-500">
            <tr>
              <th className="text-left px-4 py-3">Date</th>
              <th className="text-left px-4 py-3">Actor</th>
              <th className="text-left px-4 py-3">Action</th>
              <th className="text-left px-4 py-3">Before</th>
              <th className="text-left px-4 py-3">After</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data.content.map((entry) => (
              <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 whitespace-nowrap text-gray-500">
                  {/* createdAt is ISO-8601 UTC; show date only. Some rows (e.g. system/
                      engine-raised entries) have no createdAt — render a placeholder
                      instead of crashing the whole page (found live, 2026-10-18). */}
                  {entry.createdAt ? formatDate(entry.createdAt.slice(0, 10)) : '—'}
                </td>
                <td className="px-4 py-3 text-gray-900">{entry.actorName}</td>
                <td className="px-4 py-3 text-gray-900">{entry.action}</td>
                <td className="px-4 py-3 text-gray-500 max-w-[200px] truncate">
                  {entry.before ?? '—'}
                </td>
                <td className="px-4 py-3 text-gray-500 max-w-[200px] truncate">
                  {entry.after ?? '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination controls */}
      {data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-2 rounded border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-40 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors"
          >
            ← Previous
          </button>
          <span className="text-gray-500">
            Page {page + 1} of {data.totalPages} ({data.totalElements} entries)
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
            className="px-3 py-2 rounded border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-40 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors"
          >
            Next →
          </button>
        </div>
      )}
    </div>
  )
}
