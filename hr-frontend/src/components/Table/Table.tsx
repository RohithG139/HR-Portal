/**
 * @component Table
 * @description Sortable, filterable, searchable data table with Excel export (R-UI-2).
 * The export icon triggers a download from an optional `exportUrl` via the API client.
 * Sort, search, and column filters are handled client-side over the provided `rows`.
 * For server-side pagination, callers pass `onSearch`/`onSort` callbacks instead.
 * @props columns - Column[] - column definitions
 * @props rows - T[] - data rows (any shape; columns define how to access each field)
 * @props onExport - function - called when the export icon is clicked (caller triggers download)
 * @props searchPlaceholder - string - placeholder for the search box
 * @props isLoading - boolean - shows a loading skeleton
 * @props emptyMessage - string - shown when rows is empty and not loading
 * @example
 * <Table columns={cols} rows={employees} onExport={handleExport} />
 */

import { useState } from 'react'
import type { ReactElement, ReactNode } from 'react'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** One column definition for the Table component. */
export interface Column<T> {
  /** Column header text. */
  key: string
  /** Label shown in the column header. */
  header: string
  /** Extracts the sortable/searchable string value from a row. */
  accessor: (row: T) => string | number | null | undefined
  /** Optional custom cell renderer. */
  render?: (row: T) => ReactNode
  /** Whether this column is sortable (default: true). */
  sortable?: boolean
}

interface TableProps<T> {
  columns: Column<T>[]
  rows: T[]
  onExport?: () => void
  searchPlaceholder?: string
  isLoading?: boolean
  emptyMessage?: string
  /** Additional filter controls rendered to the left of the search box. */
  filters?: ReactNode
}

type SortDir = 'asc' | 'desc'

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Table renders a responsive, accessible data table.
 *
 * Sort state is local — memoisation is not justified because the sort runs on the
 * already-rendered rows array, which is owned by the parent query cache.
 *
 * The search filter re-renders on every keystroke; `useDeferredValue` would only
 * help for very large row sets. Given page sizes ≤ 20 it is not warranted.
 */
export function Table<T>({
  columns,
  rows,
  onExport,
  searchPlaceholder = 'Search…',
  isLoading = false,
  emptyMessage = 'No records found.',
  filters,
}: TableProps<T>): ReactElement {
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<SortDir>('asc')

  // Client-side search: filter rows where any column value contains the search string
  const searched = search.trim() === ''
    ? rows
    : rows.filter((row) =>
        columns.some((col) => {
          const val = col.accessor(row)
          return val !== null && val !== undefined && String(val).toLowerCase().includes(search.toLowerCase())
        }),
      )

  // Client-side sort
  const sorted = sortKey === null
    ? searched
    : [...searched].sort((a, b) => {
        const col = columns.find((c) => c.key === sortKey)
        if (col === undefined) return 0
        const av = col.accessor(a) ?? ''
        const bv = col.accessor(b) ?? ''
        const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true })
        return sortDir === 'asc' ? cmp : -cmp
      })

  const handleSort = (key: string): void => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
      {/* Toolbar panel (wireframe): filters row, then search + export row, both left-aligned */}
      <div className="bg-gray-50 border-b border-gray-200 p-4 space-y-3">
        {filters !== undefined && (
          <div className="flex flex-wrap gap-2 items-center">
            {filters}
          </div>
        )}
        <div className="flex items-center gap-2">
          {/* Search box */}
          <label htmlFor="table-search" className="sr-only">
            {searchPlaceholder}
          </label>
          <input
            id="table-search"
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={searchPlaceholder}
            className="
              px-3 py-2 border border-gray-300 rounded-md
              text-sm bg-white text-gray-900
              focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500
              min-h-[44px] w-48 sm:w-64
            "
          />
          {/* Export icon (R-UI-2) */}
          {onExport !== undefined && (
            <button
              type="button"
              onClick={onExport}
              aria-label="Export to Excel"
              title="Export to Excel"
              className="
                flex items-center justify-center min-h-[44px] min-w-[44px]
                border border-gray-300 rounded-md
                text-gray-600
                hover:bg-gray-100
                focus-visible:ring-2 focus-visible:ring-blue-500
                transition-colors
              "
            >
              {/* Download icon SVG */}
              <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="wf-table-wrap rounded-none border-0 shadow-none">
        <table className="wf-table">
          <thead>
            <tr>
              {columns.map((col) => (
                <th
                  key={col.key}
                  scope="col"
                  className="px-4 py-3 whitespace-nowrap"
                >
                  {(col.sortable ?? true) ? (
                    <button
                      type="button"
                      onClick={() => handleSort(col.key)}
                      className="flex items-center gap-1.5 uppercase font-semibold focus-visible:ring-2 focus-visible:ring-white rounded"
                      aria-sort={
                        sortKey === col.key
                          ? sortDir === 'asc' ? 'ascending' : 'descending'
                          : 'none'
                      }
                    >
                      {col.header}
                      {/* Wireframe th.sortable indicator: ⇅ idle, ↑/↓ active */}
                      <span aria-hidden="true" className={sortKey === col.key ? '' : 'opacity-55 text-[11px]'}>
                        {sortKey === col.key ? (sortDir === 'asc' ? '↑' : '↓') : '⇅'}
                      </span>
                    </button>
                  ) : (
                    col.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 3 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {columns.map((col) => (
                    <td key={col.key} className="px-4 py-3">
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))
            ) : sorted.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-4 py-8 text-center text-gray-400"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              sorted.map((row, idx) => (
                <tr
                  key={idx}
                  className="bg-white hover:bg-gray-50"
                >
                  {columns.map((col) => (
                    <td key={col.key} className="px-4 py-3">
                      {col.render !== undefined ? col.render(row) : String(col.accessor(row) ?? '—')}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
