/**
 * @component ConfirmEquipmentModal
 * @description S9 — Confirm equipment received pop-up (Employee, modal `m-equip`).
 * Shows a table of issued assets with "Received" checkboxes and an optional
 * "Anything missing or damaged?" remarks field. Uses A28 to submit.
 * @props isOpen - boolean - visibility
 * @props onClose - function - close handler
 * @props itemId - number - the EQUIPMENT checklist item id
 * @props assets - AssetDto[] - assets to confirm
 * @props onConfirmed - function - called after successful confirmation
 * @example
 * <ConfirmEquipmentModal isOpen={open} onClose={() => setOpen(false)} itemId={3} assets={myAssets} onConfirmed={refetch} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { confirmEquipment } from '../../api/onboarding'
import type { AssetDto } from '../../api/onboarding'
import { Modal, Field, useToast } from '../../components/index'

interface ConfirmEquipmentModalProps {
  isOpen: boolean
  onClose: () => void
  itemId: number
  assets: AssetDto[]
  onConfirmed: () => void
}

/**
 * ConfirmEquipmentModal lets the new joiner tick which assets they have received
 * and optionally note any missing or damaged items.
 *
 * No required-field validation is defined in the spec for this modal (spec §7:
 * "No required-field message defined by the FRD; the field is optional.").
 * The "Received" checkboxes can all be unchecked.
 */
export function ConfirmEquipmentModal({
  isOpen,
  onClose,
  itemId,
  assets,
  onConfirmed,
}: ConfirmEquipmentModalProps): ReactElement {
  const { showToast } = useToast()
  const [receivedIds, setReceivedIds] = useState<Set<number>>(new Set())
  const [remarks, setRemarks] = useState('')

  const toggleReceived = (assetId: number): void => {
    setReceivedIds((prev) => {
      const next = new Set(prev)
      if (next.has(assetId)) {
        next.delete(assetId)
      } else {
        next.add(assetId)
      }
      return next
    })
  }

  const mutation = useMutation({
    mutationFn: () =>
      confirmEquipment(itemId, {
        receivedAssetIds: Array.from(receivedIds),
        remarks: remarks.trim() !== '' ? remarks.trim() : null,
      }),
    onSuccess: () => {
      showToast('Equipment confirmed.')
      setReceivedIds(new Set())
      setRemarks('')
      onConfirmed()
      onClose()
    },
    onError: () => showToast('Failed to confirm equipment.', 'error'),
  })

  return (
    <Modal
      isOpen={isOpen}
      title="Confirm equipment received"
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {mutation.isPending ? 'Confirming…' : 'Confirm'}
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {assets.length === 0 ? (
          <p className="text-sm text-gray-400">No equipment to confirm.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-xs uppercase text-gray-500">
              <tr>
                <th className="text-left py-2">Type</th>
                <th className="text-left py-2">Model</th>
                <th className="text-left py-2">Asset no.</th>
                <th className="py-2">Received</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {assets.map((asset) => (
                <tr key={asset.id}>
                  <td className="py-2 text-gray-600">{asset.assetType}</td>
                  <td className="py-2 text-gray-900">{asset.model}</td>
                  <td className="py-2 text-gray-600">{asset.assetNumber}</td>
                  <td className="py-2 text-center">
                    <input
                      type="checkbox"
                      id={`recv-${asset.id}`}
                      checked={receivedIds.has(asset.id)}
                      onChange={() => toggleReceived(asset.id)}
                      aria-label={`Received ${asset.model} (${asset.assetNumber})`}
                      className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Remarks (optional per spec §7) */}
        <Field
          id="remarks"
          label="Anything missing or damaged?"
          type="textarea"
          value={remarks}
          onChange={(v) => setRemarks(String(v))}
          rows={3}
        />
      </div>
    </Modal>
  )
}
