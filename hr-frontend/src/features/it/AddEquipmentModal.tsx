/**
 * @component AddEquipmentModal
 * @description S5 — "+ Add equipment" modal (IT admin). Records issued equipment.
 *   Prefills asset number with "CW-" (spec §5). All required fields; error messages from spec.
 * @props isOpen - boolean - visibility
 * @props onClose - function - close handler
 * @props onSaved - function - called after saving
 * @props employeeId - number - the employee receiving the asset
 * @example
 *   <AddEquipmentModal isOpen={open} onClose={() => setOpen(false)} onSaved={refetch} employeeId={42} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { addAsset } from '../../api/assets'
import { ApiResponseError } from '../../api/types'
import { Modal, Field, useToast } from '../../components/index'
import { toIsoDateOrEmpty } from '../../lib/date'

interface AddEquipmentModalProps {
  isOpen: boolean
  onClose: () => void
  onSaved: () => void
  employeeId: number
}

interface AssetForm {
  assetType: string
  model: string
  assetNumber: string
  serialNumber: string
  issueDate: string    // display dd-mm-yyyy
}

const EMPTY: AssetForm = {
  assetType: '', model: '', assetNumber: 'CW-', serialNumber: '', issueDate: '',
}

const ASSET_TYPE_OPTIONS = [
  { value: 'LAPTOP', label: 'Laptop' },
  { value: 'MONITOR', label: 'Monitor' },
  { value: 'PHONE', label: 'Phone' },
  { value: 'ACCESS_BADGE', label: 'Access badge' },
  { value: 'DOCKING_STATION', label: 'Docking station' },
  { value: 'HEADSET', label: 'Headset' },
  { value: 'KEYBOARD_MOUSE', label: 'Keyboard & mouse' },
  { value: 'OTHER', label: 'Other' },
]

/** Add equipment modal (S5, m-add-asset). */
export function AddEquipmentModal({ isOpen, onClose, onSaved, employeeId }: AddEquipmentModalProps): ReactElement {
  const { showToast } = useToast()
  const [form, setForm] = useState<AssetForm>(EMPTY)
  const [errors, setErrors] = useState<Partial<Record<keyof AssetForm, string>>>({})

  const mutation = useMutation({
    mutationFn: () =>
      addAsset(employeeId, {
        assetType: form.assetType,
        model: form.model,
        assetNumber: form.assetNumber,
        serialNumber: form.serialNumber || null,
        issuedToEmployeeId: employeeId,
        issueDate: toIsoDateOrEmpty(form.issueDate),
      }),
    onSuccess: () => {
      showToast('Equipment added.')
      setForm(EMPTY)
      setErrors({})
      onSaved()
      onClose()
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'ASSET_NUMBER_IN_USE') {
        setErrors({ assetNumber: 'This asset number is already in use.' })
      } else {
        showToast('Failed to add equipment.', 'error')
      }
    },
  })

  const validate = (): boolean => {
    const errs: Partial<Record<keyof AssetForm, string>> = {}
    if (form.assetType === '') errs.assetType = 'This field is required.'
    if (form.model.trim() === '') errs.model = 'This field is required.'
    if (form.assetNumber.trim() === '' || form.assetNumber === 'CW-') errs.assetNumber = 'This field is required.'
    if (form.issueDate.trim() === '') errs.issueDate = 'This field is required.'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleSave = (): void => {
    if (validate()) mutation.mutate()
  }

  const setField = <K extends keyof AssetForm>(key: K, val: string): void => {
    setForm((p) => ({ ...p, [key]: val }))
    setErrors((p) => ({ ...p, [key]: undefined }))
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Add equipment"
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors">
            Cancel
          </button>
          <button type="button" onClick={handleSave} disabled={mutation.isPending} className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Field id="assetType" label="Type" type="select" value={form.assetType} onChange={(v) => setField('assetType', String(v))} required options={ASSET_TYPE_OPTIONS} error={errors.assetType} />
        <Field id="assetModel" label="Model" value={form.model} onChange={(v) => setField('model', String(v))} required error={errors.model} />
        <Field id="assetNumber" label="Asset no." value={form.assetNumber} onChange={(v) => setField('assetNumber', String(v))} required error={errors.assetNumber} />
        <Field id="serialNumber" label="Serial number" value={form.serialNumber} onChange={(v) => setField('serialNumber', String(v))} />
        <Field id="issueDate" label="Issue date (dd-mm-yyyy)" type="date" value={form.issueDate} onChange={(v) => setField('issueDate', String(v))} required placeholder="dd-mm-yyyy" error={errors.issueDate} />
      </div>
    </Modal>
  )
}
