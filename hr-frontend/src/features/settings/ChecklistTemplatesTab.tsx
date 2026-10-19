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
import { CARD, CARD_TITLE_ROW, CARD_TITLE, ADD_BTN, SECONDARY_BTN, REMOVE_BTN, CloseIcon, SortHeader, sortRows, tabClass, UNSORTED } from './settingsUi'
import type { SortState } from './settingsUi'

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

const ALL_SECTIONS = [...ONBOARDING_SECTIONS, ...OFFBOARDING_SECTIONS]

/** DOCUMENTS_ADMIN → "Documents & admin". */
function sectionLabel(value: string): string {
  return ALL_SECTIONS.find((o) => o.value === value)?.label ?? value
}

/** IT_ADMIN → "IT admin". */
function ownerLabel(value: string): string {
  return OWNER_OPTIONS.find((o) => o.value === value)?.label ?? value
}

/** Wireframe "Due" column: −5 → "−5 days" (before start), 0 → "Day 1", 4 → "Day 5". */
function dueLabel(offsetDays: number): string {
  if (offsetDays < 0) return `−${Math.abs(offsetDays)} day${offsetDays === -1 ? '' : 's'}`
  return `Day ${offsetDays + 1}`
}

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
  const [itemSort, setItemSort] = useState<SortState>(UNSORTED)

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

  // Unsorted view lists items in section order (Documents → Equipment → Accounts → Orientation), as the wireframe does
  const sectionOrdered = [...(detail?.items ?? [])].sort(
    (a, b) => ALL_SECTIONS.findIndex((o) => o.value === a.section) - ALL_SECTIONS.findIndex((o) => o.value === b.section))
  const sortedItems = sortRows(sectionOrdered, itemSort, (it, key) => {
    switch (key) {
      case 'section': return sectionLabel(it.section)
      case 'owner': return ownerLabel(it.ownerRole)
      case 'mandatory': return it.mandatory ? 0 : 1
      case 'due': return it.dueOffsetDays
      default: return it.name
    }
  })

  return (
    <div className="space-y-4">
      <section aria-labelledby="checklist-heading" className={CARD}>
        {/* Header row: title left, actions right (R-UI-1) */}
        <div className={CARD_TITLE_ROW}>
          <h2 id="checklist-heading" className={CARD_TITLE}>
            Checklist templates
          </h2>
          <div className="flex items-center gap-2">
            {selectedTemplate !== null && selectedTemplate.archivedAt === null && (
              <button
                type="button"
                onClick={() => {
                  setEditingItem(null)
                  setAddItemOpen(true)
                }}
                className={ADD_BTN}
              >
                + Add item
              </button>
            )}
            <button
              type="button"
              onClick={() => setNewTemplateOpen(true)}
              className={SECONDARY_BTN}
            >
              + New template
            </button>
          </div>
        </div>

        {/* Template tabs (wireframe .tabs inside the card) */}
        {templateList.length > 0 && (
          <div
            role="tablist"
            aria-label="Checklist templates"
            className="flex flex-wrap gap-1 border-b-2 border-gray-200 mb-5"
          >
            {templateList.map((tmpl) => (
              <button
                key={tmpl.id}
                type="button"
                role="tab"
                aria-selected={tmpl.id === activeId}
                onClick={() => setSelectedTemplateId(tmpl.id)}
                className={`${tabClass(tmpl.id === activeId)} ${tmpl.archivedAt !== null ? 'opacity-60' : ''}`}
              >
                {tmpl.name}
                {tmpl.archivedAt !== null && ' (archived)'}
              </button>
            ))}
          </div>
        )}

        {templateList.length === 0 && (
          <p className="text-sm text-gray-500 mb-4">
            No templates yet. Create one with "+ New template".
          </p>
        )}

        {/* Items table for selected template */}
        {activeId !== null && detail !== null && detail !== undefined && (
          <div className="wf-table-wrap">
            <table className="wf-table">
              <thead>
                <tr>
                  <SortHeader label="Item" sortKey="name" sort={itemSort} onSort={setItemSort} />
                  <SortHeader label="Section" sortKey="section" sort={itemSort} onSort={setItemSort} />
                  <SortHeader label="Owner" sortKey="owner" sort={itemSort} onSort={setItemSort} />
                  <SortHeader label="Mandatory" sortKey="mandatory" sort={itemSort} onSort={setItemSort} center />
                  <SortHeader label="Due" sortKey="due" sort={itemSort} onSort={setItemSort} />
                  <th scope="col" className="text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                {sortedItems.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-center text-gray-400">
                      No items yet.
                    </td>
                  </tr>
                )}
                {sortedItems.map((item) => (
                  <tr key={item.id}>
                    <td className="font-semibold">{item.name}</td>
                    <td>{sectionLabel(item.section)}</td>
                    <td>{ownerLabel(item.ownerRole)}</td>
                    <td className="text-center">
                      {item.mandatory
                        ? <><span aria-hidden="true">✓</span><span className="sr-only">Mandatory</span></>
                        : <><span aria-hidden="true">—</span><span className="sr-only">Optional</span></>}
                    </td>
                    <td className="whitespace-nowrap">{dueLabel(item.dueOffsetDays)}</td>
                    <td className="text-center">
                      {detail.archivedAt === null && (
                        <div className="inline-flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => {
                              setEditingItem(item)
                              setAddItemOpen(true)
                            }}
                            aria-label={`Edit ${item.name}`}
                            className={SECONDARY_BTN}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            onClick={() => { void handleDeleteItem(item) }}
                            aria-label={`Remove ${item.name}`}
                            className={REMOVE_BTN}
                          >
                            <CloseIcon />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Archive (orange outline, left — R-UI-1) and archived filter, below the table */}
        <div className="flex flex-wrap items-center justify-between gap-3 mt-4">
          {selectedTemplate !== null && selectedTemplate.archivedAt === null ? (
            <button
              type="button"
              onClick={() => { void handleArchive() }}
              className="inline-flex items-center h-8 px-3 text-[11px] font-semibold uppercase tracking-[0.4px] rounded-md border-[1.5px] border-orange-500 text-orange-600 bg-white hover:bg-orange-50 focus-visible:ring-2 focus-visible:ring-orange-500"
            >
              Archive template
            </button>
          ) : <span />}
          <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
            <input
              type="checkbox"
              checked={includeArchived}
              onChange={(e) => setIncludeArchived(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
            />
            Show archived
          </label>
        </div>
      </section>

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
