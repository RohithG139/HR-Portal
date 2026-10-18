/**
 * @component AppraisalFormsTab
 * @description S3 — Settings tab 3: Appraisal forms list.
 * Shows form name, used-for, section count, last edited, status, and an Open link.
 * "+ New form" and "Open" both navigate to the form editor (S4).
 * @props (none)
 * @example
 * <AppraisalFormsTab />
 */

import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listAppraisalForms } from '../../api/settingsAppraisalForms'
import { formatDate } from '../../lib/date'

/** Human-readable labels for usedFor values (spec §5 S3/S4 — only 3 options built, D-05). */
const USED_FOR_LABELS: Record<string, string> = {
  STANDARD: 'All employees',
  LEADERSHIP: 'Tech leads & managers',
  PROBATION: 'Probation reviews',
  PROJECT: 'Project reviews',
}

/** Human-readable status labels. */
const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

/** Status badge colours. */
function statusClass(status: string): string {
  if (status === 'ACTIVE') return 'bg-green-100 text-green-800'
  if (status === 'ARCHIVED') return 'bg-gray-100 text-gray-600'
  return 'bg-yellow-100 text-yellow-800'
}

/**
 * AppraisalFormsTab lists all appraisal forms for the HR admin.
 * Navigation to the editor is done via React Router (no API mutation here).
 */
export function AppraisalFormsTab(): ReactElement {
  const navigate = useNavigate()

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'appraisal-forms'],
    queryFn: listAppraisalForms,
  })

  const forms = data ?? []

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-gray-900">
          Appraisal forms
        </h2>
        {/* + New form — green, top right (R-UI-1) */}
        <button
          type="button"
          onClick={() => navigate('/settings/forms/new')}
          className="px-3 py-1.5 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500"
        >
          + New form
        </button>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="w-full text-sm text-left text-gray-700">
          <thead className="bg-gray-50 text-xs uppercase text-gray-500">
            <tr>
              <th scope="col" className="px-4 py-3">Form</th>
              <th scope="col" className="px-4 py-3">Used for</th>
              <th scope="col" className="px-4 py-3">Sections</th>
              <th scope="col" className="px-4 py-3">Last edited</th>
              <th scope="col" className="px-4 py-3">Status</th>
              <th scope="col" className="px-4 py-3">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {isLoading && (
              Array.from({ length: 3 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {Array.from({ length: 6 }).map((__, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))
            )}
            {!isLoading && forms.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  No appraisal forms yet.
                </td>
              </tr>
            )}
            {!isLoading && forms.map((form) => {
              // updatedAt is an ISO-8601 timestamp; extract the date part for display
              const datePart = form.updatedAt.substring(0, 10)
              const displayDate = datePart !== '' ? formatDate(datePart) : '—'

              return (
                <tr
                  key={form.id}
                  className="bg-white hover:bg-gray-50 cursor-pointer"
                  onClick={() => navigate(`/settings/forms/${form.id}`)}
                >
                  <td className="px-4 py-3 font-medium">{form.name}</td>
                  <td className="px-4 py-3 text-gray-500">
                    {USED_FOR_LABELS[form.usedFor] ?? form.usedFor}
                  </td>
                  <td className="px-4 py-3 text-gray-500">{form.sectionCount}</td>
                  <td className="px-4 py-3 text-gray-500">{displayDate}</td>
                  <td className="px-4 py-3">
                    <span
                      className={[
                        'inline-flex px-2 py-0.5 rounded-full text-xs font-medium',
                        statusClass(form.status),
                      ].join(' ')}
                    >
                      {STATUS_LABELS[form.status] ?? form.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        navigate(`/settings/forms/${form.id}`)
                      }}
                      className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded min-h-[44px] px-1"
                    >
                      Open
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
