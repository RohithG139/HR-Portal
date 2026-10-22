/**
 * @component UndoBanner
 * @description Undo affordance for a task completion inside the undo window (R-API-6, G-03).
 * The complete endpoints (A19/A23/A24) return `202 {undoUntil, taskId}`; this banner shows
 * the deadline as local `dd-mm-yyyy HH:mm:ss` with a seconds countdown and an "Undo" button
 * that calls A37 (`POST /api/tasks/{taskId}/undo`). When the window ends it calls `onExpired`
 * (e.g. to navigate away) — the page must not navigate away before that.
 * @props taskId - number - engine_task id from the 202 body
 * @props undoUntil - string - ISO-8601 instant from the 202 body
 * @props message - string - what was completed, e.g. "Onboarding completed."
 * @props onUndone - function - called after a successful undo
 * @props onExpired - function - called once when the window has ended
 * @example
 * <UndoBanner taskId={12} undoUntil="2026-10-21T09:00:10Z" message="Setup marked done." onUndone={refetch} onExpired={() => navigate('/my-tasks')} />
 */

import { useEffect, useRef, useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { undoTask } from '../../api/tasks'
import { ApiResponseError } from '../../api/types'
import { formatDateTime } from '../../lib/date'
import { useToast } from '../Toast/ToastContext'

interface UndoBannerProps {
  taskId: number
  undoUntil: string
  message: string
  onUndone: () => void
  onExpired: () => void
}

/** Whole seconds left until `undoUntil` (never negative). */
function secondsLeft(undoUntil: string): number {
  const ms = new Date(undoUntil).getTime() - Date.now()
  return Number.isNaN(ms) ? 0 : Math.max(0, Math.ceil(ms / 1000))
}

/** Banner with a countdown and an Undo button for a pending task completion. */
export function UndoBanner({ taskId, undoUntil, message, onUndone, onExpired }: UndoBannerProps): ReactElement {
  const { showToast } = useToast()
  const [left, setLeft] = useState(() => secondsLeft(undoUntil))
  const expiredFired = useRef(false)
  const onExpiredRef = useRef(onExpired)
  onExpiredRef.current = onExpired

  const undoMutation = useMutation({
    mutationFn: () => undoTask(taskId),
    onSuccess: () => {
      expiredFired.current = true // the window no longer applies
      showToast('Completion undone.')
      onUndone()
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && 'message' in err.body) {
        showToast(err.body.message, 'error')
      } else {
        showToast('Failed to undo.', 'error')
      }
    },
  })

  useEffect(() => {
    const tick = (): void => {
      const s = secondsLeft(undoUntil)
      setLeft(s)
      if (s === 0 && !expiredFired.current && !undoMutation.isPending) {
        expiredFired.current = true
        onExpiredRef.current()
      }
    }
    tick()
    const handle = window.setInterval(tick, 1000)
    return () => window.clearInterval(handle)
  }, [undoUntil, undoMutation.isPending])

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900"
    >
      <p>
        {message} You can undo until {formatDateTime(undoUntil)}
        {left > 0 && <span className="ml-1 font-medium">({left} s left)</span>}.
      </p>
      <button
        type="button"
        onClick={() => undoMutation.mutate()}
        disabled={left === 0 || undoMutation.isPending || undoMutation.isSuccess}
        className="px-4 py-2 text-sm font-medium rounded-md border border-blue-600 text-blue-700 hover:bg-blue-100 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors disabled:opacity-50"
      >
        {undoMutation.isPending ? 'Undoing…' : 'Undo'}
      </button>
    </div>
  )
}
