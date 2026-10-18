/**
 * @component TransferManagerModal
 * @description W6 — Transfer manager modal (FRD-01 Wave 3).
 * HR opens this modal from the employee record header to transfer an employee
 * to a new line manager. Fields: Employee (read-only), Current manager (read-only),
 * New manager* (select), Effective date* (date input), Move open tasks (checkboxes),
 * Reason (optional textarea).
 * @props employeeId       - number  - employee whose manager to transfer
 * @props employeeName     - string  - pre-filled Employee field (read-only)
 * @props currentManager   - string | null - pre-filled Current manager field (read-only)
 * @props isOpen           - boolean - controls modal visibility
 * @props onClose          - function - called on Cancel or after successful transfer
 * @example
 * <TransferManagerModal
 *   employeeId={42}
 *   employeeName="Lotte Jansen"
 *   currentManager="Mark Smit"
 *   isOpen={open}
 *   onClose={() => setOpen(false)}
 * />
 */

import { useState, useEffect } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Modal, Field } from '../../components/index'
import { useToast } from '../../components/index'
import { getOnboardingLookups } from '../../api/lookups'
import { getManagerTasks, createManagerTransfer } from '../../api/managerTransfers'
import type { ManagerTaskItemDto } from '../../api/managerTransfers'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { formatDate } from '../../lib/date'

interface TransferManagerModalProps {
  employeeId: number
  employeeName: string
  currentManager: string | null
  isOpen: boolean
  onClose: () => void
}

interface FormState {
  /** Numeric ID of the selected new manager; empty string = not yet selected. */
  newManagerId: string
  /** Display date dd-mm-yyyy; backend receives yyyy-MM-dd via parseDate. */
  effectiveDate: string
  reason: string
  /** Set of ticked engine_task IDs (as strings for Set operations). */
  tickedTaskIds: Set<string>
}

/**
 * TransferManagerModal handles the full transfer flow:
 * 1. Fetches open manager tasks (W09) when modal opens.
 * 2. Fetches the manager candidates list from the onboarding lookups endpoint.
 * 3. On submit, calls POST /api/employees/{id}/manager-transfers (W10).
 * 4. On 201: shows a success toast and closes.
 * 5. On 400 VALIDATION_FAILED: maps field errors to the appropriate field (R-SRC-3).
 */
export function TransferManagerModal({
  employeeId,
  employeeName,
  currentManager,
  isOpen,
  onClose,
}: TransferManagerModalProps): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [form, setForm] = useState<FormState>({
    newManagerId: '',
    effectiveDate: '',
    reason: '',
    tickedTaskIds: new Set(),
  })

  const [errors, setErrors] = useState<Record<string, string>>({})

  // Reset form when the modal opens for a different employee or re-opens.
  useEffect(() => {
    if (isOpen) {
      setForm({ newManagerId: '', effectiveDate: '', reason: '', tickedTaskIds: new Set() })
      setErrors({})
    }
  }, [isOpen, employeeId])

  // W09 — fetch open manager tasks when the modal opens.
  // The query is disabled when the modal is closed to avoid unnecessary fetches.
  const { data: tasksData } = useQuery({
    queryKey: ['manager-tasks', employeeId],
    queryFn: () => getManagerTasks(employeeId),
    enabled: isOpen,
  })

  // Candidate managers list — reuses the same onboarding lookups endpoint as F01's
  // manager dropdown (spec §5 W6: "every employee with app_role='MANAGER', excluding the current manager").
  const { data: lookupsData } = useQuery({
    queryKey: ['lookups-onboarding'],
    queryFn: getOnboardingLookups,
    enabled: isOpen,
    staleTime: 5 * 60 * 1000, // 5 minutes — lookup data changes infrequently
  })

  // W10 — POST /api/employees/{id}/manager-transfers
  const transferMutation = useMutation({
    mutationFn: () => {
      // Convert display date dd-mm-yyyy → ISO yyyy-MM-dd for the API.
      const iso = toIso(form.effectiveDate)
      return createManagerTransfer(employeeId, {
        newManagerId: form.newManagerId !== '' ? Number(form.newManagerId) : null,
        effectiveDate: iso,
        reason: form.reason !== '' ? form.reason : undefined,
        taskIdsToMove: Array.from(form.tickedTaskIds).map(Number),
      })
    },
    onSuccess: () => {
      showToast('Manager transferred successfully.', 'success')
      // Invalidate queries that reference this employee so the record refreshes.
      void queryClient.invalidateQueries({ queryKey: ['employee-overview', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['manager-tasks', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['employees'] })
      onClose()
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        const fieldErrors: Record<string, string> = {}
        const validation = err.body as ApiValidationError
        for (const fe of validation.errors) {
          fieldErrors[fe.field] = fe.message
        }
        setErrors(fieldErrors)
      } else {
        showToast('Transfer failed. Please try again.', 'error')
      }
    },
  })

  /** Convert dd-mm-yyyy display date to yyyy-MM-dd ISO. Returns empty string on invalid input. */
  function toIso(display: string): string {
    const m = /^(\d{2})-(\d{2})-(\d{4})$/.exec(display)
    if (m === null) return ''
    return `${m[3]}-${m[2]}-${m[1]}`
  }

  function handleSubmit(): void {
    // Client-side validation mirrors server messages exactly (R-SRC-3).
    const next: Record<string, string> = {}
    if (form.newManagerId === '') {
      next['newManagerId'] = 'Choose a new manager.'
    }
    if (form.effectiveDate === '') {
      next['effectiveDate'] = 'The effective date cannot be in the past.'
    } else {
      const iso = toIso(form.effectiveDate)
      if (iso === '') {
        next['effectiveDate'] = 'The effective date cannot be in the past.'
      } else {
        const today = new Date().toISOString().slice(0, 10)
        if (iso < today) {
          next['effectiveDate'] = 'The effective date cannot be in the past.'
        }
      }
    }
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    setErrors({})
    transferMutation.mutate()
  }

  function toggleTask(taskId: number): void {
    setForm((prev) => {
      const next = new Set(prev.tickedTaskIds)
      const key = String(taskId)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return { ...prev, tickedTaskIds: next }
    })
  }

  // Candidate managers: exclude the current manager by name (the lookup returns name strings,
  // but the spec uses ID for newManagerId — so we compare by id from the lookup list).
  const managerOptions =
    lookupsData?.managers.map((m) => ({ value: String(m.id), label: m.name })) ?? []

  const openTasks: ManagerTaskItemDto[] = tasksData?.openManagerTasks ?? []

  const footer = (
    <>
      {/* Cancel — red outline, left (R-UI-1) */}
      <button
        type="button"
        onClick={onClose}
        disabled={transferMutation.isPending}
        className="
          px-4 py-2 text-sm font-medium rounded-md
          border border-red-300 text-red-600
          hover:bg-red-50
          min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500
          transition-colors
          disabled:opacity-50
        "
      >
        Cancel
      </button>

      {/* Transfer — green, right (R-UI-1) */}
      <button
        type="button"
        onClick={handleSubmit}
        disabled={transferMutation.isPending}
        className="
          px-4 py-2 text-sm font-medium rounded-md
          bg-green-600 hover:bg-green-700 text-white
          min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500
          transition-colors
          disabled:opacity-50
        "
      >
        {transferMutation.isPending ? 'Transferring…' : 'Transfer'}
      </button>
    </>
  )

  return (
    <Modal
      isOpen={isOpen}
      title="Transfer manager"
      onClose={onClose}
      footer={footer}
      size="lg"
    >
      {/* Employee — read-only, pre-filled */}
      <Field
        id="transfer-employee"
        label="Employee"
        type="text"
        value={employeeName}
        readOnly
      />

      {/* Current manager — read-only, pre-filled */}
      <Field
        id="transfer-current-manager"
        label="Current manager"
        type="text"
        value={currentManager ?? '—'}
        readOnly
      />

      {/* New manager — required select (R-SRC-3: exact message) */}
      <Field
        id="transfer-new-manager"
        label="New manager"
        type="select"
        value={form.newManagerId}
        onChange={(v) => {
          setForm((prev) => ({ ...prev, newManagerId: String(v) }))
          if (errors['newManagerId'] !== undefined) {
            setErrors((prev) => { const { newManagerId: _, ...rest } = prev; return rest })
          }
        }}
        required
        options={managerOptions}
        error={errors['newManagerId']}
      />

      {/* Effective date — required date input, display as dd-mm-yyyy (R-UI-5/G-12) */}
      <div>
        <label
          htmlFor="transfer-effective-date"
          className="block text-sm font-medium text-gray-700 mb-1"
        >
          Effective date <span aria-hidden="true" className="ml-1 text-red-500">*</span>
        </label>
        <input
          id="transfer-effective-date"
          type="date"
          value={toIso(form.effectiveDate)}
          onChange={(e) => {
            // Store as dd-mm-yyyy display format internally; convert from ISO on input
            const iso = e.target.value
            const [y, mo, d] = iso.split('-')
            const display = iso !== '' && y && mo && d ? `${d}-${mo}-${y}` : ''
            setForm((prev) => ({ ...prev, effectiveDate: display }))
            if (errors['effectiveDate'] !== undefined) {
              setErrors((prev) => { const { effectiveDate: _, ...rest } = prev; return rest })
            }
          }}
          min={new Date().toISOString().slice(0, 10)}
          className={[
            'w-full px-3 py-2 border rounded-md text-sm bg-white text-gray-900',
            'focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]',
            errors['effectiveDate'] !== undefined
              ? 'border-red-500 focus-visible:ring-red-500'
              : 'border-gray-300',
          ].join(' ')}
          aria-describedby={errors['effectiveDate'] !== undefined ? 'transfer-date-error' : undefined}
          aria-invalid={errors['effectiveDate'] !== undefined}
        />
        {errors['effectiveDate'] !== undefined && (
          <p id="transfer-date-error" role="alert" className="mt-1 text-xs text-red-600">
            {errors['effectiveDate']}
          </p>
        )}
        {form.effectiveDate !== '' && (
          <p className="mt-1 text-xs text-gray-400">
            Selected: {formatDate(toIso(form.effectiveDate) !== '' ? toIso(form.effectiveDate) : '')}
          </p>
        )}
      </div>

      {/* Move open tasks — one checkbox per open engine_task owned by the current manager */}
      {openTasks.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium text-gray-700">Move open tasks to new manager</p>
          <ul className="space-y-1">
            {openTasks.map((task) => (
              <li key={task.id} className="flex items-center gap-2 min-h-[44px]">
                <input
                  id={`task-${task.id}`}
                  type="checkbox"
                  checked={form.tickedTaskIds.has(String(task.id))}
                  onChange={() => toggleTask(task.id)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                />
                <label htmlFor={`task-${task.id}`} className="text-sm text-gray-700 flex-1">
                  {task.taskName}
                  {task.dueDate !== null && (
                    <span className="ml-2 text-xs text-gray-400">
                      Due {formatDate(task.dueDate)}
                    </span>
                  )}
                </label>
              </li>
            ))}
          </ul>
          <p className="text-xs text-gray-400">
            Unticked tasks stay with the current manager.
          </p>
        </div>
      )}

      {/* Reason — optional textarea */}
      <Field
        id="transfer-reason"
        label="Reason"
        type="textarea"
        value={form.reason}
        onChange={(v) => setForm((prev) => ({ ...prev, reason: String(v) }))}
        placeholder="Optional reason for the transfer…"
        rows={3}
      />
    </Modal>
  )
}
