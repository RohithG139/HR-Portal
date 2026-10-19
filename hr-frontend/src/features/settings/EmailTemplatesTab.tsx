/**
 * @component EmailTemplatesTab
 * @description S5 — Settings tab 4: E-mail templates.
 * List of all 21 templates; "Edit" opens a modal for editing name/subject/body/recipients/CC.
 * "Send test e-mail" queues a test send to the HR user's e-mail (C28).
 * No create/delete — the 21 templates are a closed set (spec §12 D-04).
 * @props (none)
 * @example
 * <EmailTemplatesTab />
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listEmailTemplates,
  getEmailTemplate,
  updateEmailTemplate,
  testSendEmailTemplate,
} from '../../api/settingsEmailTemplates'
import type { EmailTemplateDetailDto } from '../../api/settingsEmailTemplates'
import { Modal, Field, useToast } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { validateCcField } from './settingsValidation'
import { CARD, CARD_TITLE_ROW, CARD_TITLE, SECONDARY_BTN, TABLE_TOOLBAR, SEARCH_INPUT, SortHeader, Switch, sortRows, UNSORTED } from './settingsUi'
import type { SortState } from './settingsUi'

// ---------------------------------------------------------------------------
// Placeholder chip helpers
// ---------------------------------------------------------------------------

const PLACEHOLDERS = [
  '{employee_name}',
  '{employee_first_name}',
  '{manager_name}',
  '{due_date}',
  '{task_name}',
  '{task_link}',
  '{start_date}',
  '{last_day}',
]

// ---------------------------------------------------------------------------
// Edit modal
// ---------------------------------------------------------------------------

interface EditModalProps {
  templateCode: string
  onClose: () => void
  onSaved: () => void
}

/** Form state for the e-mail template editor. */
interface EditForm {
  name: string
  triggerDescription: string
  subject: string
  body: string
  cc: string
  toEmployee: boolean
  toManager: boolean
  toHr: boolean
  toItAdmin: boolean
  toTaskOwner: boolean
  toPeer: boolean
  active: boolean
}

function templateToForm(d: EmailTemplateDetailDto): EditForm {
  return {
    name: d.name,
    triggerDescription: d.triggerDescription,
    subject: d.subject,
    body: d.body,
    cc: d.cc ?? '',
    toEmployee: d.toEmployee,
    toManager: d.toManager,
    toHr: d.toHr,
    toItAdmin: d.toItAdmin,
    toTaskOwner: d.toTaskOwner,
    toPeer: d.toPeer,
    active: d.active,
  }
}

/**
 * EmailEditModal fetches a single template by code, allows editing, and saves.
 * "Send test e-mail" sends the currently-saved (server) version as a test,
 * per spec D-03 and backend C28 behaviour (renders saved values, not draft).
 */
function EmailEditModal({ templateCode, onClose, onSaved }: EditModalProps): ReactElement {
  const { showToast } = useToast()

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'email-template', templateCode],
    queryFn: () => getEmailTemplate(templateCode),
  })

  const [form, setForm] = useState<EditForm | null>(null)
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({})
  const [saving, setSaving] = useState(false)
  const [testSending, setTestSending] = useState(false)

  // Initialise form from loaded data (only once)
  if (data !== undefined && form === null) {
    setForm(templateToForm(data))
  }

  const f = form ?? (data !== undefined ? templateToForm(data) : null)

  const setField = <K extends keyof EditForm>(key: K, value: EditForm[K]): void => {
    setForm((prev) => {
      const base = prev ?? (data !== undefined ? templateToForm(data) : null)
      if (base === null) return prev
      return { ...base, [key]: value }
    })
  }

  const validate = (): boolean => {
    if (f === null) return false
    const e: Record<string, string> = {}
    if (f.name.trim() === '') e['name'] = 'This field is required.'
    if (f.triggerDescription.trim() === '') e['triggerDescription'] = 'This field is required.'
    if (f.subject.trim() === '') e['subject'] = 'This field is required.'
    if (f.body.trim() === '') e['body'] = 'This field is required.'
    const anyRecipient = f.toEmployee || f.toManager || f.toHr || f.toItAdmin || f.toTaskOwner || f.toPeer
    if (!anyRecipient) e['recipients'] = 'Choose at least one recipient.'
    const ccErr = validateCcField(f.cc)
    if (ccErr !== null) e['cc'] = ccErr
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSave = async (): Promise<void> => {
    if (f === null || data === undefined) return
    if (!validate()) return
    setSaving(true)
    try {
      await updateEmailTemplate(templateCode, {
        name: f.name,
        triggerDescription: f.triggerDescription,
        subject: f.subject,
        body: f.body,
        cc: f.cc.trim() !== '' ? f.cc.trim() : null,
        toEmployee: f.toEmployee,
        toManager: f.toManager,
        toHr: f.toHr,
        toItAdmin: f.toItAdmin,
        toTaskOwner: f.toTaskOwner,
        toPeer: f.toPeer,
        active: f.active,
        version: data.version,
      })
      showToast('Template saved.', 'success')
      onSaved()
      onClose()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          const ve = err.body as ApiValidationError
          const e: Record<string, string> = {}
          for (const fe of ve.errors) { e[fe.field] = fe.message }
          // Backend also sends recipient/CC errors as root-level messages
          const recipientErr = ve.errors.find((fe) => fe.message.includes('recipient'))
          if (recipientErr !== undefined) e['recipients'] = recipientErr.message
          const ccErr = ve.errors.find((fe) => fe.message.includes('e-mail addresses'))
          if (ccErr !== undefined) e['cc'] = ccErr.message
          setErrors(e)
        } else if (err.body.code === 'STALE_VERSION') {
          showToast('Template was changed by someone else. Please close and re-open to edit.', 'error')
        } else {
          showToast('Failed to save template.', 'error')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  const handleTestSend = async (): Promise<void> => {
    if (data === undefined) return
    setTestSending(true)
    try {
      await testSendEmailTemplate(templateCode)
      showToast(`Test e-mail sent to your e-mail address.`, 'success')
    } catch {
      showToast('Failed to send test e-mail.', 'error')
    } finally {
      setTestSending(false)
    }
  }

  const insertPlaceholder = (placeholder: string): void => {
    if (f === null) return
    setField('body', f.body + placeholder)
  }

  return (
    <Modal
      isOpen
      title={f?.name ?? 'E-mail template'}
      onClose={onClose}
      size="lg"
      footer={
        <div className="flex w-full justify-between gap-2 flex-wrap">
          {/* Cancel — red outline, left (R-UI-1) */}
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          <div className="flex gap-2">
            {/* Send test e-mail — does not close the modal */}
            <button
              type="button"
              onClick={() => { void handleTestSend() }}
              disabled={testSending || isLoading}
              className="px-4 py-2 text-sm font-medium rounded-md border border-blue-500 text-blue-600 hover:bg-blue-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-60"
            >
              {testSending ? 'Sending…' : 'Send test e-mail'}
            </button>
            {/* Save template — green, right (R-UI-1) */}
            <button
              type="button"
              onClick={() => { void handleSave() }}
              disabled={saving || isLoading}
              className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
            >
              {saving ? 'Saving…' : 'Save template'}
            </button>
          </div>
        </div>
      }
    >
      {isLoading || f === null ? (
        <p className="text-gray-400">Loading…</p>
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field
              id="et-name"
              label="Template name"
              value={f.name}
              onChange={(v) => setField('name', String(v))}
              required
              error={errors['name'] ?? null}
            />
            <Field
              id="et-active"
              label="Status"
              type="select"
              value={f.active ? 'true' : 'false'}
              onChange={(v) => setField('active', v === 'true')}
              options={[
                { value: 'true', label: 'Active' },
                { value: 'false', label: 'Inactive' },
              ]}
            />
          </div>
          <Field
            id="et-trigger"
            label="Sent when"
            value={f.triggerDescription}
            onChange={(v) => setField('triggerDescription', String(v))}
            required
            error={errors['triggerDescription'] ?? null}
          />
          <Field
            id="et-subject"
            label="Subject"
            value={f.subject}
            onChange={(v) => setField('subject', String(v))}
            required
            error={errors['subject'] ?? null}
          />

          {/* Recipients */}
          <fieldset>
            <legend className="block text-sm font-medium text-gray-700 mb-2">
              Send to <span aria-hidden="true" className="text-red-500 ml-1">*</span>
            </legend>
            {errors['recipients'] !== undefined && (
              <p role="alert" className="mb-2 text-xs text-red-600">
                {errors['recipients']}
              </p>
            )}
            <div className="flex flex-wrap gap-4">
              {(
                [
                  { key: 'toEmployee' as const, label: 'Employee' },
                  { key: 'toManager' as const, label: 'Manager' },
                  { key: 'toHr' as const, label: 'HR' },
                  { key: 'toItAdmin' as const, label: 'IT admin' },
                  { key: 'toTaskOwner' as const, label: 'Task owner' },
                  { key: 'toPeer' as const, label: 'Peer' },
                ] as const
              ).map(({ key, label }) => (
                <label key={key} className="flex items-center gap-1.5 text-sm cursor-pointer min-h-[44px]">
                  <input
                    type="checkbox"
                    checked={f[key]}
                    onChange={(e) => setField(key, e.target.checked)}
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
                  />
                  <span className="text-gray-700">{label}</span>
                </label>
              ))}
            </div>
            <p className="mt-1 text-xs text-gray-400">
              Roles are resolved per employee when the e-mail is sent — e.g. "Manager" becomes the employee's current manager.
            </p>
          </fieldset>

          <Field
            id="et-cc"
            label="CC (optional, comma-separated e-mail addresses)"
            value={f.cc}
            onChange={(v) => setField('cc', String(v))}
            placeholder="e.g. compliance@cubeworks.nl, cto@cubeworks.nl"
            error={errors['cc'] ?? null}
          />

          <Field
            id="et-body"
            label="Body"
            type="textarea"
            value={f.body}
            onChange={(v) => setField('body', String(v))}
            required
            rows={8}
            error={errors['body'] ?? null}
          />

          {/* Placeholder chips — insert into body (wireframe §5 S5) */}
          <div>
            <p className="text-xs text-gray-500 mb-1">
              Insert placeholder:
            </p>
            <div className="flex flex-wrap gap-1.5">
              {PLACEHOLDERS.map((ph) => (
                <button
                  key={ph}
                  type="button"
                  onClick={() => insertPlaceholder(ph)}
                  className="px-2 py-1 text-xs font-mono bg-gray-100 text-gray-600 rounded border border-gray-200 hover:bg-gray-200 focus-visible:ring-2 focus-visible:ring-blue-500 min-h-[44px]"
                >
                  {ph}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </Modal>
  )
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

/**
 * EmailTemplatesTab renders the S5 settings tab — list of all 21 e-mail templates.
 * Clicking "Edit" opens the EmailEditModal for the selected template.
 */
export function EmailTemplatesTab(): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [editingCode, setEditingCode] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [sort, setSort] = useState<SortState>(UNSORTED)
  const [togglingCode, setTogglingCode] = useState<string | null>(null)

  const { data: templates, isLoading } = useQuery({
    queryKey: ['settings', 'email-templates'],
    queryFn: listEmailTemplates,
  })

  const q = search.trim().toLowerCase()
  const templateList = sortRows(
    (templates ?? []).filter((t) =>
      q === '' ||
      t.name.toLowerCase().includes(q) ||
      t.triggerDescription.toLowerCase().includes(q) ||
      t.recipientsSummary.toLowerCase().includes(q)),
    sort,
    (t, key) => {
      switch (key) {
        case 'trigger': return t.triggerDescription
        case 'recipients': return t.recipientsSummary
        case 'active': return t.active ? 0 : 1
        default: return t.name
      }
    },
  )

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['settings', 'email-templates'] })
  }

  /**
   * Inline Active switch (wireframe): reads the template's current detail (C26) and saves it
   * back with only `active` flipped (C27) — same endpoint and version check as the Edit pop-up.
   */
  const handleToggleActive = async (code: string, active: boolean): Promise<void> => {
    setTogglingCode(code)
    try {
      const d = await getEmailTemplate(code)
      await updateEmailTemplate(code, {
        name: d.name,
        triggerDescription: d.triggerDescription,
        subject: d.subject,
        body: d.body,
        cc: d.cc,
        toEmployee: d.toEmployee,
        toManager: d.toManager,
        toHr: d.toHr,
        toItAdmin: d.toItAdmin,
        toTaskOwner: d.toTaskOwner,
        toPeer: d.toPeer,
        active,
        version: d.version,
      })
      showToast(active ? 'Template switched on.' : 'Template switched off.', 'success')
      invalidate()
      void queryClient.invalidateQueries({ queryKey: ['settings', 'email-template', code] })
    } catch {
      showToast('Failed to update template.', 'error')
    } finally {
      setTogglingCode(null)
    }
  }

  return (
    <section aria-labelledby="emails-heading" className={CARD}>
      <div className={CARD_TITLE_ROW}>
        <h2 id="emails-heading" className={CARD_TITLE}>
          E-mail templates
        </h2>
      </div>

      <div className="wf-table-wrap">
        {/* Search toolbar (R-UI-2, wireframe .table-toolbar) */}
        <div className={TABLE_TOOLBAR}>
          <label htmlFor="email-template-search" className="sr-only">Search templates</label>
          <input
            id="email-template-search"
            type="search"
            placeholder="Search templates…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className={SEARCH_INPUT}
          />
        </div>
        <table className="wf-table">
          <thead>
            <tr>
              <SortHeader label="Template" sortKey="name" sort={sort} onSort={setSort} />
              <SortHeader label="Sent when" sortKey="trigger" sort={sort} onSort={setSort} />
              <SortHeader label="Recipients" sortKey="recipients" sort={sort} onSort={setSort} />
              <SortHeader label="Active" sortKey="active" sort={sort} onSort={setSort} center />
              <th scope="col" className="text-center">Action</th>
            </tr>
          </thead>
          <tbody>
            {isLoading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {Array.from({ length: 5 }).map((__, j) => (
                    <td key={j}>
                      <div className="h-4 bg-gray-200 rounded w-32" />
                    </td>
                  ))}
                </tr>
              ))}
            {!isLoading && templateList.length === 0 && (
              <tr>
                <td colSpan={5} className="text-center text-gray-400">
                  No templates found.
                </td>
              </tr>
            )}
            {!isLoading &&
              templateList.map((tmpl) => (
                <tr key={tmpl.code}>
                  <td className="font-semibold">{tmpl.name}</td>
                  <td>{tmpl.triggerDescription}</td>
                  <td>{tmpl.recipientsSummary}</td>
                  <td className="text-center">
                    <Switch
                      checked={tmpl.active}
                      disabled={togglingCode === tmpl.code}
                      onChange={(checked) => { void handleToggleActive(tmpl.code, checked) }}
                      label={`${tmpl.name} active`}
                    />
                  </td>
                  <td className="text-center">
                    <button
                      type="button"
                      onClick={() => setEditingCode(tmpl.code)}
                      aria-label={`Edit ${tmpl.name}`}
                      className={SECONDARY_BTN}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {editingCode !== null && (
        <EmailEditModal
          templateCode={editingCode}
          onClose={() => setEditingCode(null)}
          onSaved={() => {
            invalidate()
            void queryClient.invalidateQueries({
              queryKey: ['settings', 'email-template', editingCode],
            })
          }}
        />
      )}
    </section>
  )
}
