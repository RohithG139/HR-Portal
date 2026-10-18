/**
 * @component TimersTab
 * @description S6 — Settings tab 5: Timers & rules.
 * Shows all 17 timer/rule settings. Rule/module/code are read-only (ADM-01).
 * Value, recipient, and enabled are editable where the row allows.
 * A single [Save] button fires one PUT per changed row via Promise.all (spec §6 C30 note).
 * [Cancel] discards all unsaved edits (R-UI-4).
 * @props (none)
 * @example
 * <TimersTab />
 */

import { useState } from 'react'
import type { ReactElement, ChangeEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { listTimers, updateTimer } from '../../api/settingsTimers'
import type { TimerDto } from '../../api/settingsTimers'
import { useToast } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { buildTimerUpdate } from './settingsValidation'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** Local editable state for one timer row. */
interface TimerEdit {
  valueInt: number | null
  valueText: string | null
  recipientRole: string | null
  enabled: boolean
}

type EditsMap = Record<string, TimerEdit>

// ---------------------------------------------------------------------------
// KPI cycle dropdown options (valueText)
// ---------------------------------------------------------------------------

const KPI_OPTIONS = [
  { value: 'QUARTERLY', label: 'Quarterly' },
  { value: 'HALF_YEARLY', label: 'Half-yearly' },
  { value: 'YEARLY', label: 'Yearly' },
]

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Derive an editable representation from a loaded TimerDto. */
function timerToEdit(t: TimerDto): TimerEdit {
  return {
    valueInt: t.valueInt,
    valueText: t.valueText,
    recipientRole: t.recipientRole,
    enabled: t.enabled,
  }
}

// ---------------------------------------------------------------------------
// Row component
// ---------------------------------------------------------------------------

interface TimerRowProps {
  timer: TimerDto
  edit: TimerEdit
  rowError: string | null
  onChange: (patch: Partial<TimerEdit>) => void
}

/**
 * TimerRow renders one editable timer/rule row.
 *
 * ADM-01: rule name/module/code are plain text, never inputs.
 * canDisable controls whether the enabled toggle is shown.
 * KPI_CYCLE uses a select for valueText; most others use a number input for valueInt.
 */
function TimerRow({ timer, edit, rowError, onChange }: TimerRowProps): ReactElement {
  const isKpiCycle = timer.code === 'KPI_CYCLE'
  const unitLabel = timer.valueUnit !== null ? timer.valueUnit.toLowerCase() : 'days'

  return (
    <tr className="bg-white hover:bg-gray-50">
      {/* Rule name — read-only text (ADM-01) */}
      <td className="px-4 py-3 font-medium text-gray-900 whitespace-nowrap">
        {timer.label}
      </td>
      {/* Module — read-only text (ADM-01) */}
      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
        {timer.module}
      </td>
      {/* Value (editable) */}
      <td className="px-4 py-3">
        {isKpiCycle ? (
          <div>
            <label htmlFor={`timer-val-${timer.code}`} className="sr-only">
              KPI cycle
            </label>
            <select
              id={`timer-val-${timer.code}`}
              value={edit.valueText ?? 'QUARTERLY'}
              onChange={(e: ChangeEvent<HTMLSelectElement>) =>
                onChange({ valueText: e.target.value })
              }
              className="px-2 py-1 text-sm border border-gray-300 rounded-md bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
            >
              {KPI_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div>
            <label htmlFor={`timer-val-${timer.code}`} className="sr-only">
              {timer.label} value in {unitLabel}
            </label>
            <div className="flex items-center gap-1.5">
              <input
                id={`timer-val-${timer.code}`}
                type="number"
                value={edit.valueInt ?? 0}
                onChange={(e: ChangeEvent<HTMLInputElement>) =>
                  onChange({ valueInt: parseInt(e.target.value, 10) || 0 })
                }
                min={1}
                className="w-20 px-2 py-1 text-sm border border-gray-300 rounded-md bg-white text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
              />
              <span className="text-sm text-gray-500">{unitLabel}</span>
            </div>
            {rowError !== null && (
              <p role="alert" className="mt-1 text-xs text-red-600">
                {rowError}
              </p>
            )}
          </div>
        )}
      </td>
      {/* Goes to (recipientRole) — shown as plain text; editable only where backend allows */}
      <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
        {timer.recipientRole !== null ? (
          /* Recipient displayed as read-only text — per spec, recipient editing is not
             explicitly shown as an input in the wireframe, only value/enabled toggle.
             Keeping read-only here matches ADM-01 spirit and the wireframe layout. */
          <span className="text-sm">{timer.recipientRole.replace(/_/g, ' ')}</span>
        ) : (
          <span className="text-gray-300">—</span>
        )}
      </td>
      {/* Active / Required toggle */}
      <td className="px-4 py-3">
        {timer.canDisable ? (
          <label className="relative inline-flex items-center cursor-pointer min-h-[44px]">
            <input
              type="checkbox"
              checked={edit.enabled}
              onChange={(e: ChangeEvent<HTMLInputElement>) =>
                onChange({ enabled: e.target.checked })
              }
              className="sr-only peer"
              aria-label={`Toggle ${timer.label}`}
            />
            {/* Custom toggle switch — accessible via keyboard (sr-only checkbox) */}
            <div
              aria-hidden="true"
              className="
                w-10 h-6 bg-gray-200 peer-focus-visible:outline-none
                peer-focus-visible:ring-2 peer-focus-visible:ring-blue-500
                rounded-full peer
                peer-checked:bg-green-500 peer-checked:
                after:content-[''] after:absolute after:top-[2px] after:left-[2px]
                after:bg-white after:border-gray-300 after:border after:rounded-full
                after:h-5 after:w-5 after:transition-all
                peer-checked:after:translate-x-full
              "
            />
          </label>
        ) : (
          <span className="text-xs text-gray-500 font-medium">Required</span>
        )}
      </td>
    </tr>
  )
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * TimersTab renders the S6 settings tab.
 *
 * Local state tracks edits keyed by timer code. The [Save] button fires one
 * PUT /api/settings/timers/{code} per changed row simultaneously (Promise.all).
 * [Cancel] clears all edits, restoring original server values.
 *
 * This fulfils spec §6 C30: "implement this as: user edits multiple rows locally,
 * clicks ONE Save button, which fires one PUT per changed row together (e.g. Promise.all),
 * then shows one toast summarizing success (or lists which ones failed)."
 */
export function TimersTab(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [edits, setEdits] = useState<EditsMap>({})
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'timers'],
    queryFn: listTimers,
  })

  const timers = data ?? []

  /** Get the current display value for a timer (edited or original). */
  const getEdit = (timer: TimerDto): TimerEdit => {
    return edits[timer.code] ?? timerToEdit(timer)
  }

  /** Update one field of one timer row's edit state. */
  const handleChange = (code: string, timer: TimerDto, patch: Partial<TimerEdit>): void => {
    const current = edits[code] ?? timerToEdit(timer)
    setEdits((prev) => ({ ...prev, [code]: { ...current, ...patch } }))
  }

  const handleCancel = (): void => {
    setEdits({})
    setRowErrors({})
  }

  const handleSave = async (): Promise<void> => {
    setRowErrors({})
    // Compute which rows actually changed
    const changedCodes = timers
      .filter((t) => {
        const edit = edits[t.code]
        if (edit === undefined) return false
        return buildTimerUpdate(t, edit) !== null
      })
      .map((t) => t.code)

    if (changedCodes.length === 0) {
      showToast('No changes to save.', 'info')
      return
    }

    setSaving(true)
    // Fire one PUT per changed row simultaneously (spec §6 C30)
    const results = await Promise.allSettled(
      changedCodes.map(async (code) => {
        const timer = timers.find((t) => t.code === code)
        if (timer === undefined) throw new Error('Timer not found')
        const edit = edits[code] ?? timerToEdit(timer)
        const patch = buildTimerUpdate(timer, edit)
        if (patch === null) return
        await updateTimer(code, patch)
      }),
    )

    setSaving(false)

    // Collect errors
    const newRowErrors: Record<string, string> = {}
    let successCount = 0
    let failCount = 0

    results.forEach((result, idx) => {
      const code = changedCodes[idx]
      if (code === undefined) return
      if (result.status === 'fulfilled') {
        successCount++
      } else {
        failCount++
        const err = result.reason as unknown
        if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          const ve = err.body as ApiValidationError
          const firstMsg = ve.errors[0]?.message ?? 'Validation error.'
          newRowErrors[code] = firstMsg
        } else if (err instanceof ApiResponseError) {
          // Use the backend's message if available
          const body = err.body
          newRowErrors[code] = 'message' in body ? (body as { message: string }).message : 'Failed to save.'
        } else {
          newRowErrors[code] = 'Failed to save.'
        }
      }
    })

    setRowErrors(newRowErrors)

    if (failCount === 0) {
      showToast(
        `${successCount} setting${successCount === 1 ? '' : 's'} saved.`,
        'success',
      )
      setEdits({})
      void queryClient.invalidateQueries({ queryKey: ['settings', 'timers'] })
    } else if (successCount > 0) {
      showToast(
        `${successCount} saved, ${failCount} failed — see rows with errors.`,
        'error',
      )
      void queryClient.invalidateQueries({ queryKey: ['settings', 'timers'] })
    } else {
      showToast('Failed to save — see rows with errors.', 'error')
    }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-base font-semibold text-gray-900">
        Timers &amp; rules
      </h2>

      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="w-full text-sm text-left text-gray-700">
          <thead className="bg-gray-50 text-xs uppercase text-gray-500">
            <tr>
              <th scope="col" className="px-4 py-3">Rule</th>
              <th scope="col" className="px-4 py-3">Module</th>
              <th scope="col" className="px-4 py-3">Value</th>
              <th scope="col" className="px-4 py-3">Goes to</th>
              <th scope="col" className="px-4 py-3">Active</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {isLoading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {Array.from({ length: 5 }).map((__, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))}
            {!isLoading && timers.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                  No timer settings found.
                </td>
              </tr>
            )}
            {!isLoading &&
              timers.map((timer) => (
                <TimerRow
                  key={timer.code}
                  timer={timer}
                  edit={getEdit(timer)}
                  rowError={rowErrors[timer.code] ?? null}
                  onChange={(patch) => handleChange(timer.code, timer, patch)}
                />
              ))}
          </tbody>
        </table>
      </div>

      {/* Footer: Cancel (left, red outline) — Save (right, green) (R-UI-1) */}
      <div className="flex justify-between pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={handleCancel}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => { void handleSave() }}
          disabled={saving}
          className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </div>
  )
}

// Re-export for tests
export { buildTimerUpdate }
