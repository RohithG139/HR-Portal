/**
 * @component NewJoinerDashboard
 * @description S7 — New-joiner dashboard (Employee, `p-sb-dash`).
 * Displays the employee's own onboarding tasks (ONB-07), issued equipment,
 * and modals for policy acknowledgement (S8) and equipment confirmation (S9).
 * @props (none — reads from auth context)
 * @example
 * Route: { path: '/dashboard/my-onboarding', element: <NewJoinerDashboard /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyOnboarding } from '../../api/onboarding'
import type { ChecklistItemDto, AssetDto } from '../../api/onboarding'
import { Card, Badge } from '../../components/index'
import { formatDate } from '../../lib/date'
import { PolicyModal } from './PolicyModal'
import { ConfirmEquipmentModal } from './ConfirmEquipmentModal'

/**
 * Due-state CSS classes for R-UI-6 colour coding.
 * The backend computes dueState; React only maps to colours (R-ARC-1).
 */
function dueDateClass(item: ChecklistItemDto): string {
  if (item.status === 'DONE') return 'text-gray-400'
  if (item.dueDate === null || item.dueDate === '') return 'text-gray-500'
  const today = new Date()
  const due = new Date(item.dueDate)
  const diffMs = due.getTime() - today.getTime()
  const diffDays = diffMs / (1000 * 60 * 60 * 24)
  if (diffDays < 0) return 'text-red-600'
  if (diffDays <= 3) return 'text-orange-500'
  return 'text-gray-500'
}

/**
 * NewJoinerDashboard renders the employee's personalised onboarding view.
 *
 * Items are grouped by section. POLICY items open the PolicyModal (S8).
 * EQUIPMENT items open the ConfirmEquipmentModal (S9).
 * All other items are plain checkboxes (employee ticks them via A17/A18
 * through the regular checklist flow — shown as read-only status here).
 */
export function NewJoinerDashboard(): ReactElement {
  const queryClient = useQueryClient()
  const [policyItemId, setPolicyItemId] = useState<number | null>(null)
  const [equipItemId, setEquipItemId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['my-onboarding'],
    queryFn: getMyOnboarding,
  })

  if (isLoading || data === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse space-y-4">
        <div className="h-8 bg-gray-200 rounded w-64" />
        <div className="h-40 bg-gray-200 rounded" />
      </div>
    )
  }

  // Group items by section
  const sections: Record<string, ChecklistItemDto[]> = {}
  data.items.forEach((item) => {
    const key = item.section
    if (sections[key] === undefined) sections[key] = []
    sections[key]!.push(item)
  })

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        My onboarding
      </h1>

      {/* Checklist sections */}
      {Object.entries(sections).map(([section, items]) => (
        <Card key={section} title={section.replace(/_/g, ' ')}>
          <ul className="space-y-3">
            {items.map((item) => {
              const isDone = item.status === 'DONE'
              return (
                <li key={item.id} className="flex items-start gap-3">
                  {/* Status indicator */}
                  <span
                    aria-label={isDone ? 'Done' : 'Pending'}
                    className={`mt-0.5 flex-none w-5 h-5 rounded-full border-2 flex items-center justify-center text-xs ${
                      isDone
                        ? 'bg-green-500 border-green-500 text-white'
                        : 'border-gray-300'
                    }`}
                  >
                    {isDone && '✓'}
                  </span>

                  <div className="flex-1">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
                      <span className={`text-sm ${isDone ? 'line-through text-gray-400' : 'text-gray-900'}`}>
                        {item.name}
                        {item.mandatory && (
                          <span aria-label="mandatory" className="ml-1 text-red-500">*</span>
                        )}
                      </span>
                      {item.dueDate !== null && item.dueDate !== '' && (
                        <span className={`text-xs ${dueDateClass(item)}`}>
                          Due {formatDate(item.dueDate)}
                        </span>
                      )}
                    </div>

                    {/* Action buttons for special item kinds */}
                    {!isDone && item.itemKind === 'POLICY' && (
                      <button
                        type="button"
                        onClick={() => setPolicyItemId(item.id)}
                        className="mt-1 text-xs text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded min-h-[44px]"
                      >
                        Read & acknowledge policy →
                      </button>
                    )}
                    {!isDone && item.itemKind === 'EQUIPMENT' && (
                      <button
                        type="button"
                        onClick={() => setEquipItemId(item.id)}
                        className="mt-1 text-xs text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded min-h-[44px]"
                      >
                        Confirm equipment received →
                      </button>
                    )}
                  </div>

                  <Badge label={isDone ? 'Done' : 'Pending'} variant={isDone ? 'success' : 'default'} />
                </li>
              )
            })}
          </ul>
        </Card>
      ))}

      {/* Issued equipment summary */}
      {data.myAssets.length > 0 && (
        <Card title="My equipment">
          <ul className="space-y-2">
            {data.myAssets.map((asset: AssetDto) => (
              <li key={asset.id} className="flex items-center justify-between text-sm">
                <span className="text-gray-900">{asset.assetType} — {asset.model}</span>
                <span className="text-gray-500 text-xs">{asset.assetNumber}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {data.items.length === 0 && (
        <p className="text-center text-gray-400 py-8">
          No onboarding tasks yet. Check back soon!
        </p>
      )}

      {/* S8 — Policy acknowledgement modal */}
      {policyItemId !== null && (
        <PolicyModal
          isOpen={policyItemId !== null}
          onClose={() => setPolicyItemId(null)}
          itemId={policyItemId}
          onAcknowledged={() => {
            void queryClient.invalidateQueries({ queryKey: ['my-onboarding'] })
          }}
        />
      )}

      {/* S9 — Confirm equipment modal */}
      {equipItemId !== null && (
        <ConfirmEquipmentModal
          isOpen={equipItemId !== null}
          onClose={() => setEquipItemId(null)}
          itemId={equipItemId}
          assets={data.myAssets}
          onConfirmed={() => {
            void queryClient.invalidateQueries({ queryKey: ['my-onboarding'] })
          }}
        />
      )}
    </div>
  )
}
