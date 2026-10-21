/**
 * @component Checklist
 * @description Renders onboarding checklist sections with tick/untick behaviour (ONB-04, D-15).
 * Each item saves immediately on tick/untick (ONB-04 — no separate Save button).
 * Due-date colours follow R-UI-6: orange within 3 days, red if overdue.
 * @props sections - ChecklistSectionDto[] - grouped checklist items from the backend
 * @props progress - string - progress string from the backend (e.g. "3/10")
 * @props progressPercent - number - 0–100 for the progress bar
 * @props onTick - function - called with itemId when an item is ticked
 * @props onUntick - function - called with itemId when an item is unticked
 * @props readOnly - boolean - disables all checkboxes (e.g. onboarding completed)
 * @props tickingId - number|null - id of the item currently being saved (shows spinner)
 * @example
 * <Checklist sections={data.sections} progress={data.progress} onTick={tick} onUntick={untick} />
 */

import type { ReactElement } from 'react'
import type { ChecklistSectionDto, ChecklistItemDto } from '../../api/onboarding'
import { formatDate } from '../../lib/date'
import { Badge } from '../Badge/Badge'

interface ChecklistProps {
  sections: ChecklistSectionDto[]
  progress: string
  progressPercent: number
  onTick: (itemId: number) => void
  onUntick: (itemId: number) => void
  readOnly?: boolean
  tickingId?: number | null
}

/** Section labels for display. */
const sectionLabels: Record<string, string> = {
  DOCUMENTS_ADMIN: 'Documents & admin',
  EQUIPMENT_ISSUED: 'Equipment issued',
  ACCOUNTS_INSTALLATIONS: 'Accounts & installations',
  ORIENTATION: 'Orientation',
}

/** Owner labels for display. */
const ownerLabels: Record<string, string> = {
  HR: 'HR',
  IT_ADMIN: 'IT admin',
  MANAGER: 'Manager',
  EMPLOYEE: 'Employee',
}

/**
 * Due-date colour class. The backend already computed dueState for engine tasks;
 * for checklist items we compute it client-side from the ISO dueDate string
 * only for display colour — not for business logic (R-ARC-1).
 */
function dueDateClass(isoDate: string): string {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const due = new Date(isoDate)
  const diffMs = due.getTime() - today.getTime()
  const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'text-red-600' // overdue
  if (diffDays <= 3) return 'text-orange-500' // due soon
  return 'text-gray-500'
}

/** One checklist item row. */
function ChecklistItemRow({
  item,
  onTick,
  onUntick,
  readOnly,
  isTickin,
}: {
  item: ChecklistItemDto
  onTick: (id: number) => void
  onUntick: (id: number) => void
  readOnly: boolean
  isTickin: boolean
}): ReactElement {
  const isDone = item.status === 'DONE'
  const checkboxId = `checklist-item-${item.id}`

  const handleChange = (): void => {
    if (readOnly || isTickin) return
    if (isDone) {
      onUntick(item.id)
    } else {
      onTick(item.id)
    }
  }

  return (
    <li className="flex items-start gap-3 py-2 border-b border-gray-100 last:border-0">
      {/* Checkbox — immediate save (ONB-04) */}
      <div className="flex items-center mt-0.5">
        <input
          id={checkboxId}
          type="checkbox"
          checked={isDone}
          onChange={handleChange}
          disabled={readOnly || isTickin}
          aria-label={`${isDone ? 'Untick' : 'Tick'} "${item.name}"`}
          className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed"
        />
      </div>

      {/* Item details */}
      <label htmlFor={checkboxId} className="flex-1 text-sm cursor-pointer">
        <span className={isDone ? 'line-through text-gray-400' : 'text-gray-900'}>
          {item.name}
          {item.mandatory && <span aria-label="mandatory" className="ml-1 text-red-500 text-xs">*</span>}
        </span>
        {!item.mandatory && (
          <span className="ml-2 px-1.5 py-0.5 rounded bg-gray-100 text-gray-500 text-[11px] font-medium">Optional</span>
        )}
        <span className="ml-2 text-xs text-gray-400">{ownerLabels[item.ownerRole] ?? item.ownerRole}</span>
        {item.dueDate !== '' && item.dueDate !== null && (
          <span className={`ml-2 text-xs ${dueDateClass(item.dueDate)}`}>
            {formatDate(item.dueDate)}
          </span>
        )}
        {isDone && item.doneBy !== null && (
          <span className="ml-2 text-xs text-gray-400">by {item.doneBy}</span>
        )}
      </label>

      {/* Status badge */}
      <Badge
        label={isDone ? 'Done' : 'Open'}
        variant={isDone ? 'success' : 'default'}
      />
    </li>
  )
}

/** Checklist component with progress bar and section grouping. */
export function Checklist({
  sections,
  progress,
  progressPercent,
  onTick,
  onUntick,
  readOnly = false,
  tickingId = null,
}: ChecklistProps): ReactElement {
  return (
    <div className="space-y-4">
      {/* Progress bar */}
      <div className="space-y-1">
        <div className="flex justify-between text-sm text-gray-600">
          <span>{progress} mandatory items</span>
          <span>{progressPercent}%</span>
        </div>
        <div
          role="progressbar"
          aria-valuenow={progressPercent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Onboarding progress"
          className="w-full h-2 bg-gray-200 rounded-full overflow-hidden"
        >
          <div
            className="h-full bg-blue-500 rounded-full transition-all duration-300"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {/* Sections */}
      {sections.map((section) => (
        <div key={section.section} className="bg-white border border-gray-200 rounded-lg p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-2">
            {sectionLabels[section.section] ?? section.section}
          </h3>
          <ul className="space-y-0.5">
            {section.items.map((item) => (
              <ChecklistItemRow
                key={item.id}
                item={item}
                onTick={onTick}
                onUntick={onUntick}
                readOnly={readOnly}
                isTickin={tickingId === item.id}
              />
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}
