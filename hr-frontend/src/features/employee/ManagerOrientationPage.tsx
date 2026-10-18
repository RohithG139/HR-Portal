/**
 * @component ManagerOrientationPage
 * @description S7M — Manager orientation checklist.
 * Reuses the Checklist component (per spec §4 S7M note), filtered to
 * owner=MANAGER, for the manager's own orientation items on a specific employee.
 * @props (none — reads employeeId from route params)
 * @example
 * Route: { path: '/manager/team/:employeeId/orientation', element: <ManagerOrientationPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getChecklist, tickItem, untickItem } from '../../api/onboarding'
import { Checklist, useToast } from '../../components/index'

/**
 * ManagerOrientationPage reuses the shared Checklist component, filtered to
 * the manager's own orientation items for a given employee.
 *
 * The manager cannot complete onboarding (spec §2 permissions table); this page
 * is tick-only. The backend enforces the owner restriction (R-API-2, D-04).
 */
export function ManagerOrientationPage(): ReactElement {
  const { employeeId: rawId } = useParams<{ employeeId: string }>()
  const employeeId = parseInt(rawId ?? '0', 10)
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [tickingId, setTickingId] = useState<number | null>(null)

  const { data: checklist, isLoading } = useQuery({
    queryKey: ['checklist-manager', employeeId],
    queryFn: () => getChecklist(employeeId, { owner: 'MANAGER' }),
  })

  const tickMutation = useMutation({
    mutationFn: (itemId: number) => {
      setTickingId(itemId)
      return tickItem(itemId)
    },
    onSuccess: () => {
      setTickingId(null)
      void queryClient.invalidateQueries({ queryKey: ['checklist-manager', employeeId] })
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
      void queryClient.invalidateQueries({ queryKey: ['checklist-manager', employeeId] })
    },
    onError: () => {
      setTickingId(null)
      showToast('Failed to untick item.', 'error')
    },
  })

  if (isLoading || checklist === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse space-y-4">
        <div className="h-8 bg-gray-200 rounded w-56" />
        <div className="h-40 bg-gray-200 rounded" />
      </div>
    )
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <Link to="/my-tasks" className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
        ← Back to My Tasks
      </Link>
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        Orientation checklist
      </h1>

      <Checklist
        sections={checklist.sections}
        progress={checklist.progress}
        progressPercent={checklist.progressPercent}
        onTick={(id) => tickMutation.mutate(id)}
        onUntick={(id) => untickMutation.mutate(id)}
        tickingId={tickingId}
      />
    </div>
  )
}
