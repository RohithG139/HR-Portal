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
import { CARD, CARD_TITLE_ROW, CARD_TITLE, INLINE_FIELD, INLINE_SELECT, Switch } from './settingsUi'

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
// Recipient ("Task / e-mail goes to") options
// ---------------------------------------------------------------------------

/** Recipients HR can pick between for task/e-mail rules (setting.recipient_role values). */
const EDITABLE_RECIPIENTS = ['MANAGER', 'HR', 'MANAGER_HR', 'EMPLOYEE', 'TASK_OWNER', 'IT_ADMIN']

const RECIPIENT_LABELS: Record<string, string> = {
  MANAGER: 'Manager',
  HR: 'HR',
  MANAGER_HR: 'Manager + HR',
  EMPLOYEE: 'Employee',
  TASK_OWNER: 'Task owner',
  IT_ADMIN: 'IT admin',
  ALL_USERS: 'All users',
  NEW_USER: 'New user',
  USER: 'User',
}

/** MANAGER_HR → "Manager + HR". */
function recipientLabel(role: string): string {
  return RECIPIENT_LABELS[role] ?? role
}

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
  const recipientEditable = timer.recipientRole !== null && EDITABLE_RECIPIENTS.includes(timer.recipientRole)

  return (
    <tr>
      {/* Rule name — read-only text (ADM-01) */}
      <td className="font-semibold">
        {timer.label}
      </td>
      {/* Module — read-only text (ADM-01) */}
      <td className="whitespace-nowrap">
        {timer.module}
      </td>
      {/* Value (editable) */}
      <td>
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
              className={`${INLINE_SELECT} w-36`}
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
            <div className="flex items-center gap-2.5">
              <input
                id={`timer-val-${timer.code}`}
                type="number"
                value={edit.valueInt ?? 0}
                onChange={(e: ChangeEvent<HTMLInputElement>) =>
                  onChange({ valueInt: parseInt(e.target.value, 10) || 0 })
                }
                min={1}
                className={`${INLINE_FIELD} w-[72px]`}
              />
              <span className="text-xs text-gray-500 whitespace-nowrap">{unitLabel}</span>
            </div>
            {rowError !== null && (
              <p role="alert" className="mt-1 text-xs text-red-600">
                {rowError}
              </p>
            )}
          </div>
        )}
      </td>
      {/* Task / e-mail goes to (recipientRole) — editable per C30 */}
      <td className="whitespace-nowrap">
        {recipientEditable ? (
          <>
            <label htmlFor={`timer-to-${timer.code}`} className="sr-only">
              {timer.label} goes to
            </label>
            <select
              id={`timer-to-${timer.code}`}
              value={edit.recipientRole ?? ''}
              onChange={(e: ChangeEvent<HTMLSelectElement>) =>
                onChange({ recipientRole: e.target.value })
              }
              className={`${INLINE_SELECT} w-36`}
            >
              {EDITABLE_RECIPIENTS.map((r) => (
                <option key={r} value={r}>
                  {recipientLabel(r)}
                </option>
              ))}
            </select>
          </>
        ) : timer.recipientRole !== null ? (
          <span>{recipientLabel(timer.recipientRole)}</span>
        ) : (
          <span className="text-gray-300">—</span>
        )}
      </td>
      {/* Active switch, or "Required" when the rule cannot be switched off */}
      <td className="text-center">
        {timer.canDisable ? (
          <Switch
            checked={edit.enabled}
            onChange={(checked) => onChange({ enabled: checked })}
            label={`Toggle ${timer.label}`}
          />
        ) : (
          <span className="text-xs text-gray-500">Required</span>
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
    <section aria-labelledby="timers-heading" className={CARD}>
      <div className={CARD_TITLE_ROW}>
        <h2 id="timers-heading" className={CARD_TITLE}>
          Timers &amp; rules
        </h2>
      </div>

      <div className="wf-table-wrap">
        <table className="wf-table">
          <thead>
            <tr>
              <th scope="col">Rule</th>
              <th scope="col">Module</th>
              <th scope="col">Value</th>
              <th scope="col">Task / e-mail goes to</th>
              <th scope="col" className="text-center">Active</th>
            </tr>
          </thead>
          <tbody>
            {isLoading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {Array.from({ length: 5 }).map((__, j) => (
                    <td key={j}>
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))}
            {!isLoading && timers.length === 0 && (
              <tr>
                <td colSpan={5} className="text-center text-gray-400">
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

      {/* Footer inside the card: Cancel (left, red outline) — Save (right, green) (R-UI-1) */}
      <div className="flex justify-between items-center mt-6 pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={handleCancel}
          className="inline-flex items-center h-[38px] px-[18px] text-xs font-semibold uppercase tracking-[0.4px] rounded-md border-[1.5px] border-red-500 text-red-600 bg-white hover:bg-red-50 focus-visible:ring-2 focus-visible:ring-red-500"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => { void handleSave() }}
          disabled={saving}
          className="inline-flex items-center h-[38px] px-[18px] text-xs font-semibold uppercase tracking-[0.4px] rounded-md bg-green-600 hover:bg-green-700 text-white shadow-sm focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </section>
  )
}

// Re-export for tests
export { buildTimerUpdate }
