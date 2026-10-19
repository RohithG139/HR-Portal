/**
 * @component AppraisalFormsTab
 * @description S3 — Settings tab 3: Appraisal forms list.
 * Shows form name, used-for, section count, last edited, status, and an Open button.
 * "+ New form" and "Open" both navigate to the form editor (S4).
 * @props (none)
 * @example
 * <AppraisalFormsTab />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listAppraisalForms } from '../../api/settingsAppraisalForms'
import { formatDate } from '../../lib/date'
import { CARD, CARD_TITLE_ROW, CARD_TITLE, ADD_BTN, SECONDARY_BTN, SortHeader, StatusBadge, sortRows, UNSORTED } from './settingsUi'
import type { SortState } from './settingsUi'

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

/** Status badge tone. */
function statusTone(status: string): 'success' | 'neutral' | 'warning' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'neutral'
  return 'warning'
}

/**
 * AppraisalFormsTab lists all appraisal forms for the HR admin.
 * Navigation to the editor is done via React Router (no API mutation here).
 */
export function AppraisalFormsTab(): ReactElement {
  const navigate = useNavigate()
  const [sort, setSort] = useState<SortState>(UNSORTED)

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'appraisal-forms'],
    queryFn: listAppraisalForms,
  })

  const forms = sortRows(data ?? [], sort, (f, key) => {
    switch (key) {
      case 'usedFor': return USED_FOR_LABELS[f.usedFor] ?? f.usedFor
      case 'sections': return f.sectionCount
      case 'updated': return f.updatedAt
      case 'status': return STATUS_LABELS[f.status] ?? f.status
      default: return f.name
    }
  })

  return (
    <section aria-labelledby="forms-heading" className={CARD}>
      <div className={CARD_TITLE_ROW}>
        <h2 id="forms-heading" className={CARD_TITLE}>
          Appraisal forms
        </h2>
        {/* + New form — green, top right (R-UI-1) */}
        <button
          type="button"
          onClick={() => navigate('/settings/forms/new')}
          className={ADD_BTN}
        >
          + New form
        </button>
      </div>

      <div className="wf-table-wrap">
        <table className="wf-table">
          <thead>
            <tr>
              <SortHeader label="Form" sortKey="name" sort={sort} onSort={setSort} />
              <SortHeader label="Used for" sortKey="usedFor" sort={sort} onSort={setSort} />
              <SortHeader label="Sections" sortKey="sections" sort={sort} onSort={setSort} center />
              <SortHeader label="Last edited" sortKey="updated" sort={sort} onSort={setSort} />
              <SortHeader label="Status" sortKey="status" sort={sort} onSort={setSort} />
              <th scope="col" className="text-center">Action</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              Array.from({ length: 3 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {Array.from({ length: 6 }).map((__, j) => (
                    <td key={j}>
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))
            )}
            {!isLoading && forms.length === 0 && (
              <tr>
                <td colSpan={6} className="text-center text-gray-400">
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
                  className="cursor-pointer"
                  onClick={() => navigate(`/settings/forms/${form.id}`)}
                >
                  <td className="font-semibold">{form.name}</td>
                  <td>{USED_FOR_LABELS[form.usedFor] ?? form.usedFor}</td>
                  <td className="text-center tabular-nums">{form.sectionCount}</td>
                  <td className="whitespace-nowrap">{displayDate}</td>
                  <td>
                    <StatusBadge tone={statusTone(form.status)}>
                      {STATUS_LABELS[form.status] ?? form.status}
                    </StatusBadge>
                  </td>
                  <td className="text-center">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        navigate(`/settings/forms/${form.id}`)
                      }}
                      className={SECONDARY_BTN}
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
    </section>
  )
}
