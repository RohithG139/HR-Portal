/**
 * @component RemainingSetupPage
 * @description S6 — Remaining setup screen (IT admin).
 * Shows the IT admin's remaining checklist items (accounts & installations).
 * "Mark done" (A24) completes the "Remaining setup" engine task via a 202 undo window.
 * Disabled until all mandatory items are ticked (spec §6). Only shown when that task exists:
 * the engine creates it only if the equipment request was completed with IT items still open,
 * so for everyone else each tick is the whole job and there is nothing further to submit.
 * @props (none — reads employeeId from route params)
 * @example
 * Route: { path: '/it/setup/:employeeId', element: <RemainingSetupPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getChecklist, tickItem, untickItem, completeItSetup } from '../../api/onboarding'
import { getMyTasks } from '../../api/tasks'
import { ApiResponseError } from '../../api/types'
import { Card, useToast } from '../../components/index'
import { formatDate } from '../../lib/date'

/**
 * RemainingSetupPage is the IT admin's second task after issuing equipment.
 *
 * It shows the remaining accounts/installations checklist (filtered to owner=IT_ADMIN).
 * "Mark done" (A24) returns a 202 undo window. If mandatory items are still open
 * the backend returns 409 MANDATORY_ITEMS_OPEN and the page shows the spec message.
 */
export function RemainingSetupPage(): ReactElement {
  const { employeeId: rawId } = useParams<{ employeeId: string }>()
  const employeeId = parseInt(rawId ?? '0', 10)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [tickingId, setTickingId] = useState<number | null>(null)

  // Load IT admin's own checklist items for this employee (owner=IT_ADMIN)
  const { data: checklist, isLoading } = useQuery({
    queryKey: ['checklist-it', employeeId],
    queryFn: () => getChecklist(employeeId, { owner: 'IT_ADMIN' }),
  })

  // Is there an open "Remaining setup" task for this employee? (same query as My Tasks)
  const { data: myTasks } = useQuery({
    queryKey: ['my-tasks'],
    queryFn: () => getMyTasks(),
  })
  const hasSetupTask = (myTasks?.content ?? []).some(
    (t) => t.title === 'Remaining setup' && t.employeeId === employeeId)

  const tickMutation = useMutation({
    mutationFn: (itemId: number) => {
      setTickingId(itemId)
      return tickItem(itemId)
    },
    onSuccess: () => {
      setTickingId(null)
      void queryClient.invalidateQueries({ queryKey: ['checklist-it', employeeId] })
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
      void queryClient.invalidateQueries({ queryKey: ['checklist-it', employeeId] })
    },
    onError: () => {
      setTickingId(null)
      showToast('Failed to untick item.', 'error')
    },
  })

  const completeMutation = useMutation({
    mutationFn: () => completeItSetup(employeeId),
    onSuccess: (result) => {
      showToast(`Setup marked done. Undo available until ${result.undoUntil}.`)
      void queryClient.invalidateQueries({ queryKey: ['checklist-it', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['my-tasks'] })
      // Task is finished: back to the task list (the toast is global, so it stays)
      void navigate('/my-tasks')
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'MANDATORY_ITEMS_OPEN') {
        // Spec §6 message (R-SRC-3)
        showToast('Tick all mandatory items first.', 'error')
      } else if (err instanceof ApiResponseError && err.body.code === 'NOT_FOUND') {
        showToast('There is no open setup task for this employee.', 'error')
      } else {
        showToast('Failed to mark setup done.', 'error')
      }
    },
  })

  if (isLoading || checklist === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse">
        <div className="h-8 bg-gray-200 rounded w-48 mb-4" />
      </div>
    )
  }

  // All items across all sections (IT admin sees their own)
  const allItems = checklist.sections.flatMap((s) => s.items)
  const canComplete = checklist.canComplete

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <Link to="/my-tasks" className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
        ← Back to My Tasks
      </Link>

      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Remaining setup
      </h1>

      {/* Progress summary */}
      <p className="text-sm text-gray-500">
        Progress: {checklist.progress} mandatory items ({checklist.progressPercent}%)
      </p>
      <div className="w-full bg-gray-200 rounded-full h-2">
        <div
          className="bg-blue-600 h-2 rounded-full transition-all"
          style={{ width: `${checklist.progressPercent}%` }}
          role="progressbar"
          aria-valuenow={checklist.progressPercent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`Setup progress: ${checklist.progressPercent}%`}
        />
      </div>

      {/* Checklist sections */}
      {checklist.sections.map((section) => (
        <Card key={section.section} title={section.section.replace(/_/g, ' ')}>
          {section.items.length === 0 ? (
            <p className="text-sm text-gray-400">No items in this section.</p>
          ) : (
            <ul className="space-y-3">
              {section.items.map((item) => {
                const isDone = item.status === 'DONE'
                return (
                  <li key={item.id} className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      id={`item-${item.id}`}
                      checked={isDone}
                      disabled={tickingId === item.id}
                      onChange={() =>
                        isDone ? untickMutation.mutate(item.id) : tickMutation.mutate(item.id)
                      }
                      className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                    />
                    <label
                      htmlFor={`item-${item.id}`}
                      className={`flex-1 text-sm cursor-pointer ${
                        isDone ? 'line-through text-gray-400' : 'text-gray-900'
                      }`}
                    >
                      {item.name}
                      {item.mandatory ? (
                        <span aria-label="mandatory" className="ml-1 text-red-500">*</span>
                      ) : (
                        <span className="ml-2 px-1.5 py-0.5 rounded bg-gray-100 text-gray-500 text-[11px] font-medium">Optional</span>
                      )}
                      {item.dueDate !== '' && item.dueDate !== null && (
                        <span className="ml-2 text-xs text-gray-400">
                          Due {formatDate(item.dueDate)}
                        </span>
                      )}
                    </label>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>
      ))}

      {allItems.length === 0 && (
        <p className="text-center text-gray-400 py-8">No items to complete.</p>
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
        {hasSetupTask ? (
          <button
            type="button"
            onClick={() => completeMutation.mutate()}
            disabled={!canComplete || completeMutation.isPending}
            aria-disabled={!canComplete}
            title={!canComplete ? 'Tick all mandatory items first' : undefined}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {completeMutation.isPending ? 'Saving…' : 'Mark done'}
          </button>
        ) : (
          <div className="flex items-center gap-3">
            <p className="text-sm text-gray-500">Each tick is saved right away — nothing else to submit.</p>
            <Link
              to="/my-tasks"
              className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] inline-flex items-center focus-visible:ring-2 focus-visible:ring-green-500 transition-colors"
            >
              Done
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
