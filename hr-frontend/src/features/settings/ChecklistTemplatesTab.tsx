/**
 * @component ChecklistTemplatesTab
 * @description S2 — Settings tab 2: Checklist templates.
 * Template tabs (one per template), items table with Add/Edit/Remove,
 * Archive button (orange outline, left), "+ New template" modal.
 * @props (none)
 * @example
 * <ChecklistTemplatesTab />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listChecklistTemplates,
  getChecklistTemplate,
  createChecklistTemplate,
  archiveChecklistTemplate,
  addChecklistTemplateItem,
  updateChecklistTemplateItem,
  deleteChecklistTemplateItem,
} from '../../api/settingsChecklistTemplates'
import type {
  ChecklistTemplateListDto,
  ChecklistTemplateItemDto,
  ChecklistTemplateItemRequest,
} from '../../api/settingsChecklistTemplates'
import { listJobTitles } from '../../api/settingsDepartments'
import type { JobTitleDto } from '../../api/settingsDepartments'
import { Modal, Field, useToast } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'

// ---------------------------------------------------------------------------
// Section / owner options
// ---------------------------------------------------------------------------

const ONBOARDING_SECTIONS = [
  { value: 'DOCUMENTS_ADMIN', label: 'Documents & admin' },
  { value: 'EQUIPMENT_ISSUED', label: 'Equipment issued' },
  { value: 'ACCOUNTS_INSTALLATIONS', label: 'Accounts & installations' },
  { value: 'ORIENTATION', label: 'Orientation' },
]

const OFFBOARDING_SECTIONS = [
  { value: 'RETURN_ASSETS', label: 'Return assets' },
  { value: 'REMOVE_ACCESS', label: 'Remove access' },
  { value: 'HR_HANDOVER', label: 'HR handover' },
  { value: 'REASSIGN_TEAM', label: 'Reassign team' },
]

const OWNER_OPTIONS = [
  { value: 'HR', label: 'HR' },
  { value: 'IT_ADMIN', label: 'IT admin' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'EMPLOYEE', label: 'Employee' },
]

// ---------------------------------------------------------------------------
// Add / Edit item modal
// ---------------------------------------------------------------------------

interface ItemModalProps {
  isOpen: boolean
  kind: 'ONBOARDING' | 'OFFBOARDING'
  templateId: number
  editingItem: ChecklistTemplateItemDto | null
  onClose: () => void
  onSaved: () => void
}

const EMPTY_ITEM: ChecklistTemplateItemRequest = {
  name: '',
  section: '',
  ownerRole: '',
  mandatory: true,
  dueOffsetDays: 0,
}

function ItemModal({ isOpen, kind, templateId, editingItem, onClose, onSaved }: ItemModalProps): ReactElement {
  const { showToast } = useToast()
  const [form, setForm] = useState<ChecklistTemplateItemRequest>(
    editingItem !== null
      ? {
          name: editingItem.name,
          section: editingItem.section,
          ownerRole: editingItem.ownerRole,
          mandatory: editingItem.mandatory,
          dueOffsetDays: editingItem.dueOffsetDays,
        }
      : EMPTY_ITEM,
  )
  const [errors, setErrors] = useState<Partial<Record<keyof ChecklistTemplateItemRequest, string>>>({})
  const [saving, setSaving] = useState(false)

  const sectionOptions = kind === 'OFFBOARDING' ? OFFBOARDING_SECTIONS : ONBOARDING_SECTIONS

  const validate = (): boolean => {
    const e: Partial<Record<keyof ChecklistTemplateItemRequest, string>> = {}
    if (form.name.trim() === '') e.name = 'This field is required.'
    if (form.section === '') e.section = 'This field is required.'
    if (form.ownerRole === '') e.ownerRole = 'This field is required.'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = async (): Promise<void> => {
    if (!validate()) return
    setSaving(true)
    try {
      if (editingItem !== null) {
        await updateChecklistTemplateItem(templateId, editingItem.id, form)
      } else {
        await addChecklistTemplateItem(templateId, form)
      }
      showToast('Item saved.', 'success')
      onSaved()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
        const ve = err.body as ApiValidationError
        const newErrors: Partial<Record<keyof ChecklistTemplateItemRequest, string>> = {}
        for (const fe of ve.errors) {
          const key = fe.field as keyof ChecklistTemplateItemRequest
          newErrors[key] = fe.message
        }
        setErrors(newErrors)
      } else {
        showToast('Failed to save item.', 'error')
      }
    } finally {
      setSaving(false)
    }
  }

  const handleClose = (): void => {
    setErrors({})
    setForm(EMPTY_ITEM)
    onClose()
  }

  return (
    <Modal
      isOpen={isOpen}
      title="Checklist item"
      onClose={handleClose}
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleSave() }}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <Field
        id="item-name"
        label="Item"
        value={form.name}
        onChange={(v) => setForm((f) => ({ ...f, name: String(v) }))}
        required
        error={errors.name}
      />
      <div className="grid grid-cols-2 gap-4">
        <Field
          id="item-section"
          label="Section"
          type="select"
          value={form.section}
          onChange={(v) => setForm((f) => ({ ...f, section: String(v) }))}
          required
          options={sectionOptions}
          error={errors.section}
        />
        <Field
          id="item-owner"
          label="Owner"
          type="select"
          value={form.ownerRole}
          onChange={(v) => setForm((f) => ({ ...f, ownerRole: String(v) }))}
          required
          options={OWNER_OPTIONS}
          error={errors.ownerRole}
        />
      </div>
      <div className="grid grid-cols-2 gap-4">
        <Field
          id="item-due"
          label="Due (days from start)"
          type="number"
          value={form.dueOffsetDays}
          onChange={(v) => setForm((f) => ({ ...f, dueOffsetDays: parseInt(String(v), 10) || 0 }))}
          required
        />
        <Field
          id="item-mandatory"
          label="Mandatory"
          type="select"
          value={form.mandatory ? 'true' : 'false'}
          onChange={(v) => setForm((f) => ({ ...f, mandatory: v === 'true' }))}
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

// ---------------------------------------------------------------------------
// New template modal
// ---------------------------------------------------------------------------

interface NewTemplateModalProps {
  isOpen: boolean
  templates: ChecklistTemplateListDto[]
  jobTitles: JobTitleDto[]
  onClose: () => void
  onCreated: () => void
}

function NewTemplateModal({ isOpen, templates, jobTitles, onClose, onCreated }: NewTemplateModalProps): ReactElement {
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [kind, setKind] = useState('ONBOARDING')
  const [copiedFromId, setCopiedFromId] = useState('')
  const [jobTitleId, setJobTitleId] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const handleCreate = async (): Promise<void> => {
    setNameError(null)
    if (name.trim() === '') {
      setNameError('This field is required.')
      return
    }
    setSaving(true)
    try {
      await createChecklistTemplate({
        name: name.trim(),
        kind,
        copiedFromId: copiedFromId !== '' ? parseInt(copiedFromId, 10) : null,
        jobTitleId: jobTitleId !== '' ? parseInt(jobTitleId, 10) : null,
      })
      showToast('Template created.', 'success')
      setName('')
      setKind('ONBOARDING')
      setCopiedFromId('')
      setJobTitleId('')
      onCreated()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'DUPLICATE_NAME') {
          setNameError('This name already exists.')
        } else {
          showToast('Failed to create template.', 'error')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  const handleClose = (): void => {
    setName('')
    setNameError(null)
    onClose()
  }

  // Templates available to copy from (active, non-archived)
  const copyOptions = templates
    .filter((t) => t.archivedAt === null)
    .map((t) => ({ value: String(t.id), label: t.name }))

  const jobTitleOptions = jobTitles.map((jt) => ({ value: String(jt.id), label: jt.name }))

  return (
    <Modal
      isOpen={isOpen}
      title="New checklist template"
      onClose={handleClose}
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleCreate() }}
            disabled={saving}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
          >
            {saving ? 'Creating…' : 'Create template'}
          </button>
        </>
      }
    >
      <Field
        id="tmpl-name"
        label="Template name"
        value={name}
        onChange={(v) => setName(String(v))}
        required
        error={nameError}
      />
      <Field
        id="tmpl-kind"
        label="Type"
        type="select"
        value={kind}
        onChange={(v) => setKind(String(v))}
        required
        options={[
          { value: 'ONBOARDING', label: 'Onboarding' },
          { value: 'OFFBOARDING', label: 'Offboarding' },
        ]}
      />
      <Field
        id="tmpl-copy"
        label="Copy items from"
        type="select"
        value={copiedFromId}
        onChange={(v) => setCopiedFromId(String(v))}
        options={copyOptions}
        placeholder="Empty template"
      />
      <Field
        id="tmpl-job-title"
        label="Used for job title (optional)"
        type="select"
        value={jobTitleId}
        onChange={(v) => setJobTitleId(String(v))}
        options={jobTitleOptions}
        placeholder="Any job title"
      />
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * ChecklistTemplatesTab renders the S2 settings tab.
 *
 * Templates are shown as pill tabs; clicking a tab loads the template's items.
 * The "include archived" toggle adds archived templates to the list.
 */
export function ChecklistTemplatesTab(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [includeArchived, setIncludeArchived] = useState(false)
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null)
  const [addItemOpen, setAddItemOpen] = useState(false)
  const [editingItem, setEditingItem] = useState<ChecklistTemplateItemDto | null>(null)
  const [newTemplateOpen, setNewTemplateOpen] = useState(false)

  const { data: templates } = useQuery({
    queryKey: ['settings', 'checklist-templates', { includeArchived }],
    queryFn: () => listChecklistTemplates(undefined, includeArchived),
  })

  const { data: jobTitles } = useQuery({
    queryKey: ['settings', 'job-titles'],
    queryFn: listJobTitles,
  })

  // Select first template by default when data loads
  const templateList = templates ?? []
  const activeId = selectedTemplateId ?? templateList[0]?.id ?? null

  const { data: detail } = useQuery({
    queryKey: ['settings', 'checklist-template-detail', activeId],
    queryFn: () => (activeId !== null ? getChecklistTemplate(activeId) : null),
    enabled: activeId !== null,
  })

  const selectedTemplate = templateList.find((t) => t.id === activeId) ?? null

  const invalidateAll = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['settings', 'checklist-templates'] })
    if (activeId !== null) {
      void queryClient.invalidateQueries({
        queryKey: ['settings', 'checklist-template-detail', activeId],
      })
    }
  }

  const handleArchive = async (): Promise<void> => {
    if (activeId === null) return
    try {
      await archiveChecklistTemplate(activeId)
      showToast('Template archived.', 'success')
      setSelectedTemplateId(null)
      invalidateAll()
    } catch {
      showToast('Failed to archive template.', 'error')
    }
  }

  const handleDeleteItem = async (item: ChecklistTemplateItemDto): Promise<void> => {
    if (activeId === null) return
    try {
      await deleteChecklistTemplateItem(activeId, item.id)
      showToast('Item removed.', 'success')
      invalidateAll()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'ITEM_IN_USE') {
        showToast('This item is in use and cannot be removed.', 'error')
      } else {
        showToast('Failed to remove item.', 'error')
      }
    }
  }

  return (
    <div className="space-y-4">
      {/* Header row */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <h2 className="text-base font-semibold text-gray-900">
            Checklist templates
          </h2>
          {/* Include archived toggle */}
          <label className="flex items-center gap-1.5 text-sm text-gray-600 cursor-pointer">
            <input
              type="checkbox"
              checked={includeArchived}
              onChange={(e) => setIncludeArchived(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
            />
            Show archived
          </label>
        </div>
        <div className="flex gap-2">
          {/* + Add item — top right of the section (R-UI-1) */}
          {selectedTemplate !== null && selectedTemplate.archivedAt === null && (
            <button
              type="button"
              onClick={() => {
                setEditingItem(null)
                setAddItemOpen(true)
              }}
              className="px-3 py-1.5 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500"
            >
              + Add item
            </button>
          )}
          {/* + New template — top right (R-UI-1) */}
          <button
            type="button"
            onClick={() => setNewTemplateOpen(true)}
            className="px-3 py-1.5 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500"
          >
            + New template
          </button>
        </div>
      </div>

      {/* Template tab pills */}
      {templateList.length > 0 && (
        <div role="tablist" aria-label="Checklist templates" className="flex flex-wrap gap-2">
          {templateList.map((tmpl) => (
            <button
              key={tmpl.id}
              type="button"
              role="tab"
              aria-selected={tmpl.id === activeId}
              onClick={() => setSelectedTemplateId(tmpl.id)}
              className={[
                'px-3 py-1.5 text-sm rounded-full border min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors',
                tmpl.id === activeId
                  ? 'bg-blue-600 border-blue-600 text-white'
                  : 'border-gray-300 text-gray-700 hover:bg-gray-100',
                tmpl.archivedAt !== null ? 'opacity-60' : '',
              ].join(' ')}
            >
              {tmpl.name}
              {tmpl.archivedAt !== null && ' (archived)'}
            </button>
          ))}
        </div>
      )}

      {templateList.length === 0 && (
        <p className="text-sm text-gray-500">
          No templates yet. Create one with "+ New template".
        </p>
      )}

      {/* Archive button for selected template — orange outline, left (R-UI-1) */}
      {selectedTemplate !== null && selectedTemplate.archivedAt === null && (
        <div>
          <button
            type="button"
            onClick={() => { void handleArchive() }}
            className="px-3 py-1.5 text-sm font-medium rounded-md border border-orange-500 text-orange-600 hover:bg-orange-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-orange-500"
          >
            Archive template
          </button>
        </div>
      )}

      {/* Items table for selected template */}
      {activeId !== null && detail !== null && detail !== undefined && (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="w-full text-sm text-left text-gray-700">
            <thead className="bg-gray-50 text-xs uppercase text-gray-500">
              <tr>
                <th scope="col" className="px-4 py-3">Item</th>
                <th scope="col" className="px-4 py-3">Section</th>
                <th scope="col" className="px-4 py-3">Owner</th>
                <th scope="col" className="px-4 py-3">Mandatory</th>
                <th scope="col" className="px-4 py-3">Due (days)</th>
                <th scope="col" className="px-4 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {detail.items.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-gray-400">
                    No items yet.
                  </td>
                </tr>
              )}
              {detail.items.map((item) => (
                <tr
                  key={item.id}
                  className="bg-white hover:bg-gray-50"
                >
                  <td className="px-4 py-3">{item.name}</td>
                  <td className="px-4 py-3 text-gray-500">{item.section}</td>
                  <td className="px-4 py-3 text-gray-500">{item.ownerRole}</td>
                  <td className="px-4 py-3">{item.mandatory ? '✔' : '—'}</td>
                  <td className="px-4 py-3">{item.dueOffsetDays}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1">
                      {detail.archivedAt === null && (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              setEditingItem(item)
                              setAddItemOpen(true)
                            }}
                            className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded px-1 min-h-[44px]"
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            onClick={() => { void handleDeleteItem(item) }}
                            aria-label={`Remove ${item.name}`}
                            className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-red-500 rounded"
                          >
                            <span aria-hidden="true">×</span>
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals */}
      {activeId !== null && detail !== null && detail !== undefined && (
        <ItemModal
          isOpen={addItemOpen}
          kind={detail.kind}
          templateId={activeId}
          editingItem={editingItem}
          onClose={() => {
            setAddItemOpen(false)
            setEditingItem(null)
          }}
          onSaved={invalidateAll}
        />
      )}

      <NewTemplateModal
        isOpen={newTemplateOpen}
        templates={templateList}
        jobTitles={jobTitles ?? []}
        onClose={() => setNewTemplateOpen(false)}
        onCreated={invalidateAll}
      />
    </div>
  )
}
