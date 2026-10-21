/**
 * @component EquipmentRequestPage
 * @description S5 — Equipment request screen (IT admin).
 * Shows employee info, issued assets table, accounts & installations checklist,
 * and the HR-submitted request details (read-only).
 * @props (none — reads employeeId from route params)
 * @example
 * Route: { path: '/it/equipment-requests/:employeeId', element: <EquipmentRequestPage /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getEquipmentRequest, tickItem, untickItem, completeEquipmentRequest } from '../../api/onboarding'
import { unissueAsset } from '../../api/assets'
import { Card, Badge, useToast } from '../../components/index'
import { formatDate } from '../../lib/date'
import { AddEquipmentModal } from './AddEquipmentModal'

/**
 * EquipmentRequestPage is the IT admin's view for provisioning equipment and
 * ticking accounts for a new employee.
 *
 * "Mark setup done" (A23) returns a 202 undo window. The page shows an undo button
 * until the window expires.
 */
export function EquipmentRequestPage(): ReactElement {
  const { employeeId: rawId } = useParams<{ employeeId: string }>()
  const employeeId = parseInt(rawId ?? '0', 10)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [addEquipOpen, setAddEquipOpen] = useState(false)
  const [tickingId, setTickingId] = useState<number | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['equipment-request', employeeId],
    queryFn: () => getEquipmentRequest(employeeId),
  })

  const tickMutation = useMutation({
    mutationFn: (itemId: number) => { setTickingId(itemId); return tickItem(itemId) },
    onSuccess: () => { setTickingId(null); void queryClient.invalidateQueries({ queryKey: ['equipment-request', employeeId] }) },
    onError: () => { setTickingId(null); showToast('Failed to tick item.', 'error') },
  })

  const untickMutation = useMutation({
    mutationFn: (itemId: number) => { setTickingId(itemId); return untickItem(itemId) },
    onSuccess: () => { setTickingId(null); void queryClient.invalidateQueries({ queryKey: ['equipment-request', employeeId] }) },
    onError: () => { setTickingId(null); showToast('Failed to untick item.', 'error') },
  })

  const unissueMutation = useMutation({
    mutationFn: (assetId: number) => unissueAsset(assetId),
    onSuccess: () => { showToast('Asset removed.'); void queryClient.invalidateQueries({ queryKey: ['equipment-request', employeeId] }) },
    onError: () => showToast('Failed to remove asset.', 'error'),
  })

  const completeMutation = useMutation({
    mutationFn: () => completeEquipmentRequest(employeeId),
    onSuccess: (result) => {
      showToast(`Setup marked done. Undo until ${result.undoUntil}.`)
      void queryClient.invalidateQueries({ queryKey: ['my-tasks'] })
      // Task is finished: back to the task list (the toast is global, so it stays)
      void navigate('/my-tasks')
    },
    onError: () => showToast('Failed to mark setup done.', 'error'),
  })

  if (isLoading || data === undefined) {
    return <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse"><div className="h-8 bg-gray-200 rounded w-48 mb-4" /></div>
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <Link to="/my-tasks" className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
        ← Back to My Tasks
      </Link>

      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
            Equipment request · {data.employeeName}
          </h1>
          {data.readyBy !== null && (
            <p className="text-sm text-gray-500">
              Ready by {formatDate(data.readyBy)}
            </p>
          )}
        </div>
        <Badge label={data.itStatus} variant={data.itStatus === 'COMPLETED' ? 'success' : 'info'} />
      </div>

      {/* Equipment issued */}
      <Card
        title="Equipment issued"
        action={
          <button
            type="button"
            onClick={() => setAddEquipOpen(true)}
            className="px-3 py-1.5 text-sm font-medium rounded bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors"
          >
            + Add equipment
          </button>
        }
      >
        {data.issuedAssets.length === 0 ? (
          <p className="text-sm text-gray-400">No equipment issued yet.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-xs uppercase text-gray-500">
              <tr>
                <th className="text-left py-2">Type</th>
                <th className="text-left py-2">Model</th>
                <th className="text-left py-2">Asset no.</th>
                <th className="text-left py-2">Status</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.issuedAssets.map((asset) => (
                <tr key={asset.id}>
                  <td className="py-2 text-gray-600">{asset.assetType}</td>
                  <td className="py-2 text-gray-900">{asset.model}</td>
                  <td className="py-2 text-gray-600">{asset.assetNumber}</td>
                  <td className="py-2"><Badge label={asset.status} variant="info" /></td>
                  <td className="py-2 text-right">
                    <button
                      type="button"
                      onClick={() => unissueMutation.mutate(asset.id)}
                      aria-label={`Remove asset ${asset.assetNumber}`}
                      className="text-red-500 hover:text-red-700 text-sm focus-visible:ring-2 focus-visible:ring-red-500 rounded min-h-[44px] min-w-[44px] flex items-center justify-center"
                    >
                      ×
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      {/* Accounts & installations */}
      <Card title="Accounts & installations">
        {data.accountItems.length === 0 ? (
          <p className="text-sm text-gray-400">No account items.</p>
        ) : (
          <ul className="space-y-2">
            {data.accountItems.map((item) => {
              const isDone = item.status === 'DONE'
              return (
                <li key={item.id} className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    id={`acct-${item.id}`}
                    checked={isDone}
                    disabled={tickingId === item.id}
                    onChange={() => isDone ? untickMutation.mutate(item.id) : tickMutation.mutate(item.id)}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                  />
                  <label htmlFor={`acct-${item.id}`} className={`text-sm cursor-pointer ${isDone ? 'line-through text-gray-400' : 'text-gray-900'}`}>
                    {item.name}
                    {item.mandatory
                      ? <span aria-label="mandatory" className="ml-1 text-red-500">*</span>
                      : <span className="ml-2 px-1.5 py-0.5 rounded bg-gray-100 text-gray-500 text-[11px] font-medium">Optional</span>}
                  </label>
                </li>
              )
            })}
          </ul>
        )}
      </Card>

      {/* Requested by HR (read-only) */}
      <Card title="Requested by HR">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <div><dt className="text-gray-500">Laptop</dt><dd className="text-gray-900">{data.laptopModel ?? '—'}</dd></div>
          <div><dt className="text-gray-500">Keyboard layout</dt><dd className="text-gray-900">{data.keyboardLayout ?? '—'}</dd></div>
          <div><dt className="text-gray-500">Delivery</dt><dd className="text-gray-900">{data.deliveryMethod ?? '—'}</dd></div>
          {data.notes !== null && data.notes !== '' && (
            <div className="sm:col-span-2"><dt className="text-gray-500">Notes</dt><dd className="text-gray-900">{data.notes}</dd></div>
          )}
        </dl>
      </Card>

      {/* Action bar (R-UI-1) */}
      <div className="flex justify-between items-center pt-4 border-t border-gray-200">
        <button
          type="button"
          onClick={() => window.history.back()}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={() => completeMutation.mutate()}
          disabled={completeMutation.isPending}
          className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
        >
          {completeMutation.isPending ? 'Saving…' : 'Mark setup done'}
        </button>
      </div>

      <AddEquipmentModal
        isOpen={addEquipOpen}
        onClose={() => setAddEquipOpen(false)}
        onSaved={() => queryClient.invalidateQueries({ queryKey: ['equipment-request', employeeId] })}
        employeeId={employeeId}
      />
    </div>
  )
}
