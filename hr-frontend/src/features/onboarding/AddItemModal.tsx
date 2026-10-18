/**
 * @component AddItemModal
 * @description S4 — "+ Add item" modal pop-up for adding manual checklist items (A16).
 *   All fields required (spec §5). Messages: "This field is required." (R-SRC-3).
 * @props isOpen - boolean - visibility
 * @props onClose - function - close handler
 * @props onSaved - function - called after the item is saved
 * @props employeeId - number - the employee's checklist to add to
 * @example
 *   <AddItemModal isOpen={open} onClose={() => setOpen(false)} onSaved={refetch} employeeId={42} />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useMutation } from '@tanstack/react-query'
import { addChecklistItem } from '../../api/onboarding'
import { Modal, Field, useToast } from '../../components/index'

interface AddItemModalProps {
  isOpen: boolean
  onClose: () => void
  onSaved: () => void
  employeeId: number
}

interface ItemForm {
  name: string
  section: string
  ownerRole: string
  mandatory: string
  dueOffsetDays: string
}

const EMPTY: ItemForm = { name: '', section: '', ownerRole: '', mandatory: 'true', dueOffsetDays: '0' }

/** Add manual checklist item modal (S4, m-add-item). */
export function AddItemModal({ isOpen, onClose, onSaved, employeeId }: AddItemModalProps): ReactElement {
  const { showToast } = useToast()
  const [form, setForm] = useState<ItemForm>(EMPTY)
  const [errors, setErrors] = useState<Partial<Record<keyof ItemForm, string>>>({})

  const mutation = useMutation({
    mutationFn: () =>
      addChecklistItem(employeeId, {
        name: form.name,
        section: form.section,
        ownerRole: form.ownerRole,
        mandatory: form.mandatory === 'true',
        dueOffsetDays: parseInt(form.dueOffsetDays, 10),
      }),
    onSuccess: () => {
      showToast('Item added.')
      setForm(EMPTY)
      setErrors({})
      onSaved()
      onClose()
    },
    onError: () => showToast('Failed to add item.', 'error'),
  })

  const validate = (): boolean => {
    const errs: Partial<Record<keyof ItemForm, string>> = {}
    if (form.name.trim() === '') errs.name = 'This field is required.'
    if (form.section === '') errs.section = 'This field is required.'
    if (form.ownerRole === '') errs.ownerRole = 'This field is required.'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const handleSave = (): void => {
    if (validate()) mutation.mutate()
  }

  const setField = <K extends keyof ItemForm>(key: K, val: string): void => {
    setForm((p) => ({ ...p, [key]: val }))
    setErrors((p) => ({ ...p, [key]: undefined }))
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Checklist item"
      onClose={onClose}
      footer={
        <>
          {/* Cancel — red outline, left (R-UI-1) */}
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors">
            Cancel
          </button>
          {/* Save — green, right (R-UI-1) */}
          <button type="button" onClick={handleSave} disabled={mutation.isPending} className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50">
            {mutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <Field id="item-name" label="Item" value={form.name} onChange={(v) => setField('name', String(v))} required error={errors.name} />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Field
          id="item-section" label="Section" type="select"
          value={form.section}
          onChange={(v) => setField('section', String(v))}
          required
          options={[
            { value: 'DOCUMENTS_ADMIN', label: 'Documents & admin' },
            { value: 'EQUIPMENT_ISSUED', label: 'Equipment issued' },
            { value: 'ACCOUNTS_INSTALLATIONS', label: 'Accounts & installations' },
            { value: 'ORIENTATION', label: 'Orientation' },
          ]}
          error={errors.section}
        />
        <Field
          id="item-owner" label="Owner" type="select"
          value={form.ownerRole}
          onChange={(v) => setField('ownerRole', String(v))}
          required
          options={[
            { value: 'HR', label: 'HR' },
            { value: 'IT_ADMIN', label: 'IT admin' },
            { value: 'MANAGER', label: 'Manager' },
            { value: 'EMPLOYEE', label: 'Employee' },
          ]}
          error={errors.ownerRole}
        />
        <Field id="item-due" label="Due (days from start)" type="number" value={form.dueOffsetDays} onChange={(v) => setField('dueOffsetDays', String(v))} required min={0} error={errors.dueOffsetDays} />
        <Field
          id="item-mandatory" label="Mandatory" type="select"
          value={form.mandatory}
          onChange={(v) => setField('mandatory', String(v))}
          required
          options={[
            { value: 'true', label: 'Yes' },
            { value: 'false', label: 'No' },
          ]}
        />
      </div>
    </Modal>
  )
}
