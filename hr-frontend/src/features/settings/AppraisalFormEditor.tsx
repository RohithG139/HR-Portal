/**
 * @component AppraisalFormEditor
 * @description S4 — Appraisal form editor. Handles both "/settings/forms/new"
 * (create a new form) and "/settings/forms/:formId" (edit an existing form).
 * Rating scale labels, sections, and questions are all managed here.
 * Client-side guard: setting status to ACTIVE requires at least one section
 * with at least one question (spec AC-09). Backend enforces this too.
 * @props (none — reads formId from useParams)
 * @example
 * Route: { path: '/settings/forms/:formId', element: <AppraisalFormEditor /> }
 * Route: { path: '/settings/forms/new', element: <AppraisalFormEditor /> }
 */

import { useState, useEffect } from 'react'
import type { ReactElement, ChangeEvent } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAppraisalForm,
  getAppraisalForm,
  updateAppraisalForm,
  archiveAppraisalForm,
  addFormSection,
  updateFormSection,
  deleteFormSection,
  addFormQuestion,
  deleteFormQuestion,
} from '../../api/settingsAppraisalForms'
import type {
  AppraisalFormDetailDto,
  FormSectionDto,
  FormQuestionDto,
  FormSectionRequest,
  FormQuestionRequest,
} from '../../api/settingsAppraisalForms'
import { Field, useToast } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { validateFormHasSectionWithQuestion } from './settingsValidation'

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const USED_FOR_OPTIONS = [
  { value: 'STANDARD', label: 'All employees' },
  { value: 'LEADERSHIP', label: 'Tech leads & managers' },
  { value: 'PROBATION', label: 'Probation reviews' },
  // PROJECT is not offered in the UI (spec §12 D-05)
]

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ARCHIVED', label: 'Archived' },
]

const OVERALL_SCORE_OPTIONS = [
  { value: 'WEIGHTED_AVERAGE', label: 'Weighted average' },
  { value: 'MANAGER_DECIDES', label: 'Manager decides' },
]

const ANSWER_TYPE_OPTIONS = [
  { value: 'SCALE_1_5', label: '1–5 scale' },
  { value: 'YES_NO', label: 'Yes-No' },
  { value: 'TEXT', label: 'Text' },
]

// ---------------------------------------------------------------------------
// Header form state type
// ---------------------------------------------------------------------------

interface HeaderState {
  name: string
  usedFor: string
  status: string
  overallScoreMethod: string
  label1: string
  label2: string
  label3: string
  label4: string
  label5: string
}

function headerFromDetail(d: AppraisalFormDetailDto): HeaderState {
  return {
    name: d.name,
    usedFor: d.usedFor,
    status: d.status,
    overallScoreMethod: d.overallScoreMethod,
    label1: d.label1 ?? '',
    label2: d.label2 ?? '',
    label3: d.label3 ?? '',
    label4: d.label4 ?? '',
    label5: d.label5 ?? '',
  }
}

// ---------------------------------------------------------------------------
// "New form" sub-page
// ---------------------------------------------------------------------------

/**
 * NewFormPanel handles the /settings/forms/new route.
 * Collects name + usedFor, POSTs to create, then navigates to the editor.
 */
function NewFormPanel(): ReactElement {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [usedFor, setUsedFor] = useState('STANDARD')
  const [nameError, setNameError] = useState<string | null>(null)
  const [usedForError, setUsedForError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const handleCreate = async (): Promise<void> => {
    let valid = true
    setNameError(null)
    setUsedForError(null)
    if (name.trim() === '') { setNameError('This field is required.'); valid = false }
    if (usedFor === '') { setUsedForError('This field is required.'); valid = false }
    if (!valid) return

    setSaving(true)
    try {
      const result = await createAppraisalForm({ name: name.trim(), usedFor })
      showToast('Form created.', 'success')
      navigate(`/settings/forms/${result.id}`, { replace: true })
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          const ve = err.body as ApiValidationError
          for (const fe of ve.errors) {
            if (fe.field === 'name') setNameError(fe.message)
            if (fe.field === 'usedFor') setUsedForError(fe.message)
          }
        } else if (err.body.code === 'DUPLICATE_NAME') {
          setNameError('This name already exists.')
        } else {
          showToast('Failed to create form.', 'error')
        }
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 max-w-2xl space-y-6">
      <button
        type="button"
        onClick={() => navigate('/settings?tab=forms')}
        className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        ← Back to Settings
      </button>

      <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
        New appraisal form
      </h1>

      <div className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <Field
          id="new-form-name"
          label="Form name"
          value={name}
          onChange={(v) => setName(String(v))}
          required
          error={nameError}
          placeholder="e.g. Standard appraisal form"
        />
        <Field
          id="new-form-used-for"
          label="Used for"
          type="select"
          value={usedFor}
          onChange={(v) => setUsedFor(String(v))}
          required
          options={USED_FOR_OPTIONS}
          error={usedForError}
        />
      </div>

      <div className="flex justify-between">
        {/* Cancel — red outline, left (R-UI-1) */}
        <button
          type="button"
          onClick={() => navigate('/settings?tab=forms')}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
        >
          Cancel
        </button>
        {/* Create — green, right (R-UI-1) */}
        <button
          type="button"
          onClick={() => { void handleCreate() }}
          disabled={saving}
          className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
        >
          {saving ? 'Creating…' : 'Create form'}
        </button>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Section editor sub-component
// ---------------------------------------------------------------------------

interface SectionEditorProps {
  formId: number
  section: FormSectionDto
  isArchived: boolean
  onChanged: () => void
}

/**
 * SectionEditor handles editing one section inline (name, checkboxes, questions).
 * Section name and "completed by" changes are saved immediately via PUT.
 * Questions are added/removed immediately via POST/DELETE.
 */
function SectionEditor({ formId, section, isArchived, onChanged }: SectionEditorProps): ReactElement {
  const { showToast } = useToast()
  const [sectionName, setSectionName] = useState(section.name)
  const [bySelf, setBySelf] = useState(section.bySelf)
  const [byPeer, setByPeer] = useState(section.byPeer)
  const [byManager, setByManager] = useState(section.byManager)
  const [byHr, setByHr] = useState(section.byHr)
  const [sectionError, setSectionError] = useState<string | null>(null)

  // New-question form state (one inline form per section)
  const [showAddQ, setShowAddQ] = useState(false)
  const [qText, setQText] = useState('')
  const [qDesc, setQDesc] = useState('')
  const [qType, setQType] = useState('SCALE_1_5')
  const [qRequired, setQRequired] = useState(true)
  const [qTextError, setQTextError] = useState<string | null>(null)
  const [savingSection, setSavingSection] = useState(false)
  const [savingQuestion, setSavingQuestion] = useState(false)

  // Keep local state in sync when the section prop changes (after invalidation)
  useEffect(() => {
    setSectionName(section.name)
    setBySelf(section.bySelf)
    setByPeer(section.byPeer)
    setByManager(section.byManager)
    setByHr(section.byHr)
  }, [section])

  const buildRequest = (): FormSectionRequest => ({
    name: sectionName.trim(),
    bySelf,
    byPeer,
    byManager,
    byHr,
  })

  const handleSaveSection = async (): Promise<void> => {
    setSectionError(null)
    if (sectionName.trim() === '') {
      setSectionError('This field is required.')
      return
    }
    setSavingSection(true)
    try {
      await updateFormSection(formId, section.id, buildRequest())
      showToast('Section saved.', 'success')
      onChanged()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
        const ve = err.body as ApiValidationError
        setSectionError(ve.errors.find((e) => e.field === 'name')?.message ?? null)
      } else {
        showToast('Failed to save section.', 'error')
      }
    } finally {
      setSavingSection(false)
    }
  }

  const handleDeleteSection = async (): Promise<void> => {
    try {
      await deleteFormSection(formId, section.id)
      showToast('Section removed.', 'success')
      onChanged()
    } catch {
      showToast('Failed to remove section.', 'error')
    }
  }

  const handleAddQuestion = async (): Promise<void> => {
    setQTextError(null)
    if (qText.trim() === '') {
      setQTextError('This field is required.')
      return
    }
    setSavingQuestion(true)
    const body: FormQuestionRequest = {
      text: qText.trim(),
      description: qDesc.trim() !== '' ? qDesc.trim() : null,
      answerType: qType,
      required: qRequired,
    }
    try {
      await addFormQuestion(section.id, body)
      setQText('')
      setQDesc('')
      setQType('SCALE_1_5')
      setQRequired(true)
      setShowAddQ(false)
      onChanged()
    } catch (err: unknown) {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
        const ve = err.body as ApiValidationError
        setQTextError(ve.errors.find((e) => e.field === 'text')?.message ?? null)
      } else {
        showToast('Failed to add question.', 'error')
      }
    } finally {
      setSavingQuestion(false)
    }
  }

  const handleDeleteQuestion = async (q: FormQuestionDto): Promise<void> => {
    try {
      await deleteFormQuestion(section.id, q.id)
      onChanged()
    } catch {
      showToast('Failed to remove question.', 'error')
    }
  }

  return (
    <div className="border border-gray-200 rounded-lg p-4 space-y-3">
      {/* Section header row */}
      <div className="flex flex-col sm:flex-row sm:items-end gap-3">
        <div className="flex-1">
          <Field
            id={`section-name-${section.id}`}
            label="Section name"
            value={sectionName}
            onChange={(v) => setSectionName(String(v))}
            required
            disabled={isArchived}
            error={sectionError}
          />
        </div>
        {!isArchived && (
          <div className="flex gap-2 pb-0.5">
            <button
              type="button"
              onClick={() => { void handleSaveSection() }}
              disabled={savingSection}
              className="inline-flex items-center h-8 px-3 text-[11px] font-semibold uppercase tracking-[0.4px] rounded-md bg-green-600 hover:bg-green-700 text-white shadow-sm focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
            >
              {savingSection ? 'Saving…' : 'Save section'}
            </button>
            {/* Remove section — red outline, right of the section header (R-UI-1) */}
            <button
              type="button"
              onClick={() => { void handleDeleteSection() }}
              className="px-3 py-1.5 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
            >
              Remove section
            </button>
          </div>
        )}
      </div>

      {/* Completed by checkboxes */}
      <fieldset>
        <legend className="text-sm font-medium text-gray-700 mb-1">
          Completed by
        </legend>
        <div className="flex flex-wrap gap-4">
          {[
            { id: `by-self-${section.id}`, label: 'Self', checked: bySelf, set: setBySelf },
            { id: `by-peer-${section.id}`, label: 'Peer', checked: byPeer, set: setByPeer },
            { id: `by-mgr-${section.id}`, label: 'Manager', checked: byManager, set: setByManager },
            { id: `by-hr-${section.id}`, label: 'HR', checked: byHr, set: setByHr },
          ].map(({ id, label, checked, set }) => (
            <label key={id} className="flex items-center gap-1.5 text-sm cursor-pointer min-h-[44px]">
              <input
                id={id}
                type="checkbox"
                checked={checked}
                disabled={isArchived}
                onChange={(e: ChangeEvent<HTMLInputElement>) => set(e.target.checked)}
                className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500"
              />
              <span className="text-gray-700">{label}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {/* Questions list */}
      {section.questions.length > 0 && (
        <div className="space-y-2">
          {section.questions.map((q) => (
            <div
              key={q.id}
              className="flex items-start gap-2 p-2 bg-gray-50 rounded"
            >
              <div className="flex-1 text-sm text-gray-700">
                <span className="font-medium">{q.text}</span>
                {q.description !== null && q.description !== '' && (
                  <span className="ml-1 text-gray-500"> — {q.description}</span>
                )}
                <span className="ml-2 text-xs text-gray-400">
                  [{q.answerType}]{q.required ? ' *' : ''}
                </span>
              </div>
              {!isArchived && (
                <button
                  type="button"
                  onClick={() => { void handleDeleteQuestion(q) }}
                  aria-label={`Remove question: ${q.text}`}
                  className="flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-400 hover:text-red-600 focus-visible:ring-2 focus-visible:ring-red-500 rounded"
                >
                  <span aria-hidden="true">×</span>
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Add question inline form */}
      {!isArchived && (
        <div>
          {!showAddQ ? (
            <button
              type="button"
              onClick={() => setShowAddQ(true)}
              className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded min-h-[44px]"
            >
              + Add question
            </button>
          ) : (
            <div className="border border-dashed border-gray-300 rounded-lg p-3 space-y-3">
              <Field
                id={`q-text-${section.id}`}
                label="Question text"
                value={qText}
                onChange={(v) => setQText(String(v))}
                required
                error={qTextError}
              />
              <Field
                id={`q-desc-${section.id}`}
                label="Description (optional)"
                value={qDesc}
                onChange={(v) => setQDesc(String(v))}
              />
              <div className="grid grid-cols-2 gap-3">
                <Field
                  id={`q-type-${section.id}`}
                  label="Answer type"
                  type="select"
                  value={qType}
                  onChange={(v) => setQType(String(v))}
                  required
                  options={ANSWER_TYPE_OPTIONS}
                />
                <Field
                  id={`q-req-${section.id}`}
                  label="Required"
                  type="select"
                  value={qRequired ? 'true' : 'false'}
                  onChange={(v) => setQRequired(v === 'true')}
                  options={[
                    { value: 'true', label: 'Yes' },
                    { value: 'false', label: 'No' },
                  ]}
                />
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { void handleAddQuestion() }}
                  disabled={savingQuestion}
                  className="inline-flex items-center h-8 px-3 text-[11px] font-semibold uppercase tracking-[0.4px] rounded-md bg-green-600 hover:bg-green-700 text-white shadow-sm focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
                >
                  {savingQuestion ? 'Adding…' : 'Add question'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowAddQ(false)
                    setQText('')
                    setQDesc('')
                    setQTextError(null)
                  }}
                  className="px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Edit form panel
// ---------------------------------------------------------------------------

interface EditFormPanelProps {
  formId: number
}

/**
 * EditFormPanel handles /settings/forms/:formId.
 *
 * Design: sections/questions are saved in real-time (each add/delete triggers an API call).
 * The form header (name/usedFor/status/labels) is saved only when [Save form] is clicked.
 * [Cancel] resets the header state to the server-loaded values.
 *
 * This split is intentional: sections/questions need real IDs from the server before
 * questions can be nested under them. The Cancel affordance applies to header edits only.
 */
function EditFormPanel({ formId }: EditFormPanelProps): ReactElement {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const { data, isLoading } = useQuery({
    queryKey: ['settings', 'appraisal-form', formId],
    queryFn: () => getAppraisalForm(formId),
  })

  // Local header state — initialised from server data
  const [header, setHeader] = useState<HeaderState | null>(null)
  const [headerErrors, setHeaderErrors] = useState<Partial<Record<string, string>>>({})
  const [headerSaving, setHeaderSaving] = useState(false)
  const [sectionError, setSectionError] = useState<string | null>(null)
  const [addingSec, setAddingSec] = useState(false)
  const [newSecName, setNewSecName] = useState('')

  // Initialise header state once the data loads; don't overwrite user edits on re-fetch
  useEffect(() => {
    if (data !== undefined && header === null) {
      setHeader(headerFromDetail(data))
    }
  }, [data, header])

  const invalidate = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['settings', 'appraisal-form', formId] })
    void queryClient.invalidateQueries({ queryKey: ['settings', 'appraisal-forms'] })
  }

  const handleSaveHeader = async (): Promise<void> => {
    if (header === null || data === undefined) return
    setHeaderErrors({})
    setSectionError(null)

    // Client-side guard: if trying to set ACTIVE, check sections (AC-09)
    // "data.sections" reflects the server-saved sections (always current for sections)
    if (header.status === 'ACTIVE') {
      const err = validateFormHasSectionWithQuestion(data.sections)
      if (err !== null) {
        setSectionError(err)
        return
      }
    }

    setHeaderSaving(true)
    try {
      await updateAppraisalForm(formId, {
        name: header.name,
        usedFor: header.usedFor,
        status: header.status,
        overallScoreMethod: header.overallScoreMethod,
        label1: header.label1 !== '' ? header.label1 : null,
        label2: header.label2 !== '' ? header.label2 : null,
        label3: header.label3 !== '' ? header.label3 : null,
        label4: header.label4 !== '' ? header.label4 : null,
        label5: header.label5 !== '' ? header.label5 : null,
        version: data.version,
      })
      showToast('Form saved.', 'success')
      invalidate()
      // Reset header to re-sync version from server
      setHeader(null)
    } catch (err: unknown) {
      if (err instanceof ApiResponseError) {
        if (err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
          const ve = err.body as ApiValidationError
          const e: Record<string, string> = {}
          for (const fe of ve.errors) { e[fe.field] = fe.message }
          // Backend sends the "at least one section" error as a generic 400 message
          const sectionMsg = ve.errors.find((fe) => fe.message.includes('section'))?.message
          if (sectionMsg !== undefined) { setSectionError(sectionMsg) }
          setHeaderErrors(e)
        } else if (err.body.code === 'DUPLICATE_NAME') {
          setHeaderErrors({ name: 'This name already exists.' })
        } else if (err.body.code === 'STALE_VERSION') {
          showToast('The form was changed by someone else. Please reload and try again.', 'error')
        } else {
          showToast('Failed to save form.', 'error')
        }
      }
    } finally {
      setHeaderSaving(false)
    }
  }

  const handleCancel = (): void => {
    // Reset header to server state (Cancel never saves — R-UI-4)
    if (data !== undefined) setHeader(headerFromDetail(data))
    setHeaderErrors({})
    setSectionError(null)
  }

  const handleArchive = async (): Promise<void> => {
    try {
      await archiveAppraisalForm(formId)
      showToast('Form archived.', 'success')
      navigate('/settings?tab=forms')
    } catch {
      showToast('Failed to archive form.', 'error')
    }
  }

  const handleAddSection = async (): Promise<void> => {
    if (newSecName.trim() === '') return
    const body: FormSectionRequest = {
      name: newSecName.trim(),
      bySelf: false,
      byPeer: false,
      byManager: true,
      byHr: false,
    }
    try {
      await addFormSection(formId, body)
      setNewSecName('')
      setAddingSec(false)
      invalidate()
    } catch {
      showToast('Failed to add section.', 'error')
    }
  }

  if (isLoading || data === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <p className="text-gray-500">Loading…</p>
      </div>
    )
  }

  const isArchived = data.status === 'ARCHIVED'
  // Use local header state for controlled fields, fall back to server data while loading
  const h = header ?? headerFromDetail(data)

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Back link */}
      <button
        type="button"
        onClick={() => navigate('/settings?tab=forms')}
        className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        ← Back to Settings
      </button>

      {/* Page title */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
          {data.name}
        </h1>
        {!isArchived && (
          /* Archive — orange outline, left (R-UI-1) */
          <button
            type="button"
            onClick={() => { void handleArchive() }}
            className="px-3 py-1.5 text-sm font-medium rounded-md border border-orange-500 text-orange-600 hover:bg-orange-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-orange-500"
          >
            Archive form
          </button>
        )}
      </div>

      {/* Form details section */}
      <section
        aria-labelledby="form-details-heading"
        className="bg-white border border-gray-200 rounded-lg p-6 space-y-4"
      >
        <h2 id="form-details-heading" className="text-sm font-semibold text-gray-500 uppercase">
          Form details
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="form-name"
            label="Form name"
            value={h.name}
            onChange={(v) => setHeader((prev) => ({ ...(prev ?? h), name: String(v) }))}
            required
            disabled={isArchived}
            error={headerErrors['name'] ?? null}
          />
          <Field
            id="form-used-for"
            label="Used for"
            type="select"
            value={h.usedFor}
            onChange={(v) => setHeader((prev) => ({ ...(prev ?? h), usedFor: String(v) }))}
            required
            disabled={isArchived}
            options={USED_FOR_OPTIONS}
            error={headerErrors['usedFor'] ?? null}
          />
          <Field
            id="form-status"
            label="Status"
            type="select"
            value={h.status}
            onChange={(v) => setHeader((prev) => ({ ...(prev ?? h), status: String(v) }))}
            disabled={isArchived}
            options={STATUS_OPTIONS}
          />
          <Field
            id="form-score-method"
            label="Overall score"
            type="select"
            value={h.overallScoreMethod}
            onChange={(v) => setHeader((prev) => ({ ...(prev ?? h), overallScoreMethod: String(v) }))}
            disabled={isArchived}
            options={OVERALL_SCORE_OPTIONS}
          />
        </div>
      </section>

      {/* Rating scale section */}
      <section
        aria-labelledby="rating-scale-heading"
        className="bg-white border border-gray-200 rounded-lg p-6 space-y-4"
      >
        <h2 id="rating-scale-heading" className="text-sm font-semibold text-gray-500 uppercase">
          Rating scale
        </h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs uppercase text-gray-500">
                <th scope="col" className="py-2 pr-4 text-left w-12">Score</th>
                <th scope="col" className="py-2 text-left">Label</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {([5, 4, 3, 2, 1] as const).map((score) => {
                const key = `label${score}` as keyof HeaderState
                return (
                  <tr key={score}>
                    <td className="py-2 pr-4 font-medium text-gray-600">
                      {score}
                    </td>
                    <td className="py-2">
                      <input
                        type="text"
                        id={`label-${score}`}
                        aria-label={`Label for score ${score}`}
                        value={h[key] as string}
                        onChange={(e: ChangeEvent<HTMLInputElement>) =>
                          setHeader((prev) => ({ ...(prev ?? h), [key]: e.target.value }))
                        }
                        disabled={isArchived}
                        placeholder={`Label for score ${score}`}
                        className="w-full px-3 py-2 border rounded-md text-sm bg-white text-gray-900 border-gray-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:bg-gray-100 min-h-[44px]"
                      />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      {/* Sections */}
      <div className="space-y-4">
        {sectionError !== null && (
          <p role="alert" className="text-sm text-red-600">
            {sectionError}
          </p>
        )}
        {data.sections.map((section) => (
          <SectionEditor
            key={section.id}
            formId={formId}
            section={section}
            isArchived={isArchived}
            onChanged={invalidate}
          />
        ))}

        {/* Add section inline form */}
        {!isArchived && (
          <div>
            {!addingSec ? (
              <button
                type="button"
                onClick={() => setAddingSec(true)}
                className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded min-h-[44px]"
              >
                + Add section
              </button>
            ) : (
              <div className="border border-dashed border-gray-300 rounded-lg p-4 space-y-3">
                <Field
                  id="new-section-name"
                  label="Section name"
                  value={newSecName}
                  onChange={(v) => setNewSecName(String(v))}
                  required
                  placeholder="e.g. Results & KPIs"
                />
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => { void handleAddSection() }}
                    className="inline-flex items-center h-8 px-3 text-[11px] font-semibold uppercase tracking-[0.4px] rounded-md bg-green-600 hover:bg-green-700 text-white shadow-sm focus-visible:ring-2 focus-visible:ring-green-500"
                  >
                    Add section
                  </button>
                  <button
                    type="button"
                    onClick={() => { setAddingSec(false); setNewSecName('') }}
                    className="px-3 py-1.5 text-sm font-medium rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer: Cancel (left, red outline) — Save form (right, green) (R-UI-1) */}
      {!isArchived && (
        <div className="flex justify-between pt-4 border-t border-gray-200">
          <button
            type="button"
            onClick={handleCancel}
            className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-600 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => { void handleSaveHeader() }}
            disabled={headerSaving}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 disabled:opacity-60"
          >
            {headerSaving ? 'Saving…' : 'Save form'}
          </button>
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Router entry point
// ---------------------------------------------------------------------------

/**
 * AppraisalFormEditor is the route component for both /settings/forms/new
 * and /settings/forms/:formId. It delegates to the appropriate sub-component.
 */
export function AppraisalFormEditor(): ReactElement {
  const { formId } = useParams<{ formId: string }>()

  if (formId === 'new' || formId === undefined) {
    return <NewFormPanel />
  }

  const idNum = parseInt(formId, 10)
  if (isNaN(idNum)) {
    return <NewFormPanel />
  }

  return <EditFormPanel formId={idNum} />
}
