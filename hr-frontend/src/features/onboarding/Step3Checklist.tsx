/**
 * @component Step3Checklist
 * @description S4 — Step 3: Checklist (HR). Shows the onboarding checklist with progress,
 * tick/untick, "+ Add item" modal, and "Complete onboarding" button (disabled until all
 * mandatory items are done, ONB-05).
 * No separate Save button — ticking saves immediately (ONB-04, overrides wireframe).
 * @props employeeId - number - the employee being onboarded
 * @example
 * <Step3Checklist employeeId={42} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getChecklist, tickItem, untickItem, completeOnboarding,
} from '../../api/onboarding'
import type { UndoWindowResult } from '../../api/onboarding'
import { ApiResponseError } from '../../api/types'
import { Checklist, UndoBanner, useToast } from '../../components/index'
import { AddItemModal } from './AddItemModal'

interface Step3Props {
  employeeId: number
}

/**
 * Step3Checklist renders the HR view of the onboarding checklist.
 *
 * React Query invalidation: tick/untick mutations invalidate the checklist query so the
 * progress bar and canComplete flag always reflect the latest state from the backend.
 */
export function Step3Checklist({ employeeId }: Step3Props): ReactElement {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [addItemOpen, setAddItemOpen] = useState(false)
  const [tickingId, setTickingId] = useState<number | null>(null)
  // Pending completion inside the undo window (R-API-6): stay on the page until it ends
  const [pending, setPending] = useState<UndoWindowResult | null>(null)

  const { data: checklist, isLoading } = useQuery({
    queryKey: ['checklist', employeeId],
    queryFn: () => getChecklist(employeeId),
  })

  const tickMutation = useMutation({
    mutationFn: (itemId: number) => {
      setTickingId(itemId)
      return tickItem(itemId)
    },
    onSuccess: () => {
      setTickingId(null)
      void queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })
    },
    onError: () => {
      setTickingId(null)
      showToast('Failed to tick item.', 'error')
    },
  })

  const untickMutation = useMutation({
    mutationFn: (itemId: number) => {
      setTickingId(itemId)
      return untickItem(itemId)
    },
    onSuccess: () => {
      setTickingId(null)
      void queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })
    },
    onError: () => {
      setTickingId(null)
      showToast('Failed to untick item.', 'error')
    },
  })

  const completeMutation = useMutation({
    mutationFn: () => completeOnboarding(employeeId),
    onSuccess: (result) => {
      // Stay here until the undo window ends (UndoBanner.onExpired), then go back to the list
      setPending(result)
      void queryClient.invalidateQueries({ queryKey: ['employees'] })
      void queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'MANDATORY_ITEMS_OPEN') {
        showToast('Tick all mandatory items first.', 'error')
      } else {
        showToast('Failed to complete onboarding.', 'error')
      }
    },
  })

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-4 bg-gray-200 rounded w-1/3" />
        <div className="h-32 bg-gray-200 rounded" />
      </div>
    )
  }

  const canComplete = checklist?.canComplete ?? false

  return (
    <div className="space-y-6">
      {pending !== null && (
        <UndoBanner
          taskId={pending.taskId}
          undoUntil={pending.undoUntil}
          message="Onboarding completed."
          onUndone={() => {
            setPending(null)
            completeMutation.reset()
            void queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })
            void queryClient.invalidateQueries({ queryKey: ['employees'] })
          }}
          onExpired={() => {
            void queryClient.invalidateQueries({ queryKey: ['employees'] })
            void navigate('/employees')
          }}
        />
      )}
      {checklist !== undefined && (
        <Checklist
          sections={checklist.sections}
          progress={checklist.progress}
          progressPercent={checklist.progressPercent}
          onTick={(id) => tickMutation.mutate(id)}
          onUntick={(id) => untickMutation.mutate(id)}
          tickingId={tickingId}
        />
      )}

      {/* Action bar (R-UI-1) */}
      <div className="flex justify-between items-center pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={() => window.history.back()}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
        >
          Cancel
        </button>

        {/* + Add item — top right of checklist section (R-UI-1), placed in action bar here */}
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => setAddItemOpen(true)}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors"
          >
            + Add item
          </button>
          <button
            type="button"
            onClick={() => completeMutation.mutate()}
            disabled={!canComplete || completeMutation.isPending || pending !== null}
            aria-disabled={!canComplete}
            title={!canComplete ? 'Complete all mandatory items first' : undefined}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {completeMutation.isPending || pending !== null ? 'Completing…' : 'Complete onboarding'}
          </button>
        </div>
      </div>

      <AddItemModal
        isOpen={addItemOpen}
        onClose={() => setAddItemOpen(false)}
        onSaved={() => queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })}
        employeeId={employeeId}
      />
    </div>
  )
}
