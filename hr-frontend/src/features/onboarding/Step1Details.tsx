/**
 * @component Step1Details
 * @description S2 — Step 1: Employee details form. Handles draft save/resume and full submission.
 * Validation messages are exact copies from spec §5 (R-SRC-3), see step1Validation.ts.
 * @props employeeId - number|null - existing employee (null = new)
 * @props onCreated - function - called with new employeeId after successful creation
 * @props onContinue - function - called when navigating to step 2 for existing employee
 * @example
 * <Step1Details employeeId={null} onCreated={(id) => navigate(`/onboarding/${id}`)} onContinue={...} />
 */

import { useState, useEffect, useRef } from 'react'
import type { ChangeEvent, ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createOnboarding, createDraft, updateDraft, updateOnboardingDetails,
  getOnboarding, getTemplatePreview, getDraft,
} from '../../api/onboarding'
import { uploadDocument } from '../../api/documents'
import { getOnboardingLookups } from '../../api/lookups'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { Card, Field, useToast } from '../../components/index'
import { formatDate, parseDate } from '../../lib/date'
import { EMPTY_STEP1_FORM, formFromDraft, validateStep1 } from './step1Validation'
import type { Step1Errors, Step1FormState } from './step1Validation'

interface Step1Props {
  employeeId: number | null
  onCreated: (employeeId: number) => void
  onContinue: () => void
  /**
   * Resuming a previously-saved draft (Employees list "Continue", route
   * /onboarding/draft/:draftId). The whole saved form is read back with
   * A09a `GET /api/onboarding-drafts/{id}` (D-23); the router-state names are only
   * shown until that response arrives. `undefined` for a brand-new employee.
   */
  initialDraftId?: number
  initialFirstName?: string
  initialLastName?: string
}

const SECTION_LABELS: Record<string, string> = {
  DOCUMENTS_ADMIN: 'Documents & admin',
  EQUIPMENT_ISSUED: 'Equipment issued',
  ACCOUNTS_INSTALLATIONS: 'Accounts & installations',
  ORIENTATION: 'Orientation',
}

/** Maps a 400 VALIDATION_FAILED body onto the form's field errors. */
function toFieldErrors(err: ApiValidationError): Step1Errors {
  const out: Step1Errors = {}
  err.errors.forEach((e) => {
    if (e.field !== undefined && e.field !== '') {
      out[e.field as keyof Step1Errors] = e.message
    }
  })
  return out
}

/**
 * Step1Details renders the employee-details form.
 *
 * Draft mode: only firstName/lastName are required (D-13). The whole form (plus an
 * uploaded contract's document id) is stored in the draft and restored on resume.
 *
 * Probation end date is derived in the component (ONB-01) from startDate + probationMonths
 * for display only — the backend recomputes it and the API never receives it.
 */
export function Step1Details({
  employeeId, onCreated, onContinue,
  initialDraftId, initialFirstName, initialLastName,
}: Step1Props): ReactElement {
  const queryClient = useQueryClient()
  const { showToast } = useToast()
  const [form, setForm] = useState<Step1FormState>(() => ({
    ...EMPTY_STEP1_FORM,
    firstName: initialFirstName ?? '',
    lastName: initialLastName ?? '',
  }))
  const [errors, setErrors] = useState<Step1Errors>({})
  const [draftId, setDraftId] = useState<number | null>(initialDraftId ?? null)
  // ONB-09: the employee that already uses the entered private e-mail (409 body)
  const [existingEmployeeId, setExistingEmployeeId] = useState<number | null>(null)
  // Contract file (FRD-02 §4.1 "Upload the contract file" → document CONTRACT, A07)
  const [contractDocumentId, setContractDocumentId] = useState<number | null>(null)
  const [contractFileName, setContractFileName] = useState<string>('')

  // Load lookups (departments, job titles, managers, etc.)
  const { data: lookups } = useQuery({
    queryKey: ['onboarding-lookups'],
    queryFn: getOnboardingLookups,
  })

  // Load existing onboarding for edit flow
  const { data: existing } = useQuery({
    queryKey: ['onboarding-summary', employeeId],
    queryFn: () => getOnboarding(employeeId!),
    enabled: employeeId !== null,
  })

  // Resume a saved draft: restore every field it holds (A09a, D-23)
  const { data: savedDraft } = useQuery({
    queryKey: ['onboarding-draft', initialDraftId],
    queryFn: () => getDraft(initialDraftId!),
    enabled: initialDraftId !== undefined && employeeId === null,
  })
  const draftRestored = useRef(false)
  useEffect(() => {
    if (savedDraft === undefined || draftRestored.current) return
    draftRestored.current = true
    setForm({
      ...formFromDraft(savedDraft.form),
      firstName: savedDraft.firstName,
      lastName: savedDraft.lastName,
    })
    const docId = savedDraft.form['contractDocumentId']
    if (typeof docId === 'number') setContractDocumentId(docId)
    const docName = savedDraft.form['contractFileName']
    if (typeof docName === 'string') setContractFileName(docName)
  }, [savedDraft])

  // Template preview — re-fetched whenever templateId changes
  const { data: preview } = useQuery({
    queryKey: ['template-preview', form.templateId],
    queryFn: () => getTemplatePreview(parseInt(form.templateId, 10)),
    enabled: form.templateId !== '',
  })

  // Populate the form with everything saved for an existing employee, so going back to
  // step 1 (or returning after logout / refresh) shows the details that were entered
  useEffect(() => {
    if (existing !== undefined) {
      const d = existing.details
      const str = (v: string | number | null | undefined): string => (v === null || v === undefined ? '' : String(v))
      setForm((f) => ({
        ...f,
        ...(d !== undefined && {
          firstName: d.firstName,
          lastName: d.lastName,
          privateEmail: d.privateEmail,
          mobilePhone: d.mobilePhone,
          dateOfBirth: d.dateOfBirth !== null ? formatDate(d.dateOfBirth) : '',
          nationality: str(d.nationality),
          street: d.street,
          postcode: d.postcode,
          city: d.city,
          contractType: d.contractType ?? f.contractType,
          hoursPerWeek: str(d.hoursPerWeek),
          probationMonths: str(d.probationMonths),
          idChecked: d.idChecked,
          contractSignedReceived: d.contractSignedReceived,
          rightToWorkVerified: d.rightToWorkVerified,
          jobTitleId: str(d.jobTitleId),
          departmentId: str(d.departmentId),
          managerId: str(d.managerId),
          grade: str(d.grade),
          appRole: str(d.appRole),
          buddyId: str(d.buddyId),
          templateId: str(d.templateId),
        }),
        startDate: existing.startDate !== '' ? formatDate(existing.startDate) : '',
        endDate: existing.endDate !== null ? formatDate(existing.endDate) : '',
      }))
    }
  }, [existing])

  // After creation only some details can still change (A12: name, phone, address and the
  // contract dates/hours/probation); the rest is shown read-only instead of looking editable
  const locked = employeeId !== null

  const updateMutation = useMutation({
    mutationFn: () =>
      updateOnboardingDetails(employeeId!, {
        firstName: form.firstName,
        lastName: form.lastName,
        mobilePhone: form.mobilePhone,
        street: form.street,
        postcode: form.postcode,
        city: form.city,
        startDate: parseDate(form.startDate),
        endDate: form.contractType === 'FIXED_TERM' && form.endDate ? parseDate(form.endDate) : undefined,
        hoursPerWeek: parseInt(form.hoursPerWeek, 10),
        probationMonths: parseInt(form.probationMonths, 10),
        version: existing?.details?.employeeVersion ?? undefined,
      }),
    onSuccess: () => {
      showToast('Details saved.')
      void queryClient.invalidateQueries({ queryKey: ['onboarding-summary', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['checklist', employeeId] })
      void queryClient.invalidateQueries({ queryKey: ['employees'] })
      onContinue()
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        setErrors(toFieldErrors(err.body as ApiValidationError))
      } else if (err instanceof ApiResponseError && err.body.code === 'STALE_VERSION') {
        showToast('Data changed by someone else. Please reload.', 'error')
      } else {
        showToast('Failed to save details.', 'error')
      }
    },
  })

  const setField = <K extends keyof Step1FormState>(key: K, value: Step1FormState[K]): void => {
    setForm((prev) => ({
      ...prev,
      [key]: value,
      // Permanent contract: the end date is disabled and not required (FRD-02 "Other paths")
      ...(key === 'contractType' && value === 'PERMANENT' ? { endDate: '' } : {}),
    }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
    if (key === 'privateEmail') setExistingEmployeeId(null)
  }

  // Derived: probation end date (ONB-01) — display only
  const probationEnd: string = (() => {
    if (form.startDate === '' || form.probationMonths === '0' || form.probationMonths === '') return ''
    try {
      const iso = parseDate(form.startDate)
      const [y, m, d] = iso.split('-').map(Number)
      const end = new Date(y!, (m! - 1) + parseInt(form.probationMonths, 10), d!)
      const yy = end.getFullYear()
      const mm = String(end.getMonth() + 1).padStart(2, '0')
      const dd = String(end.getDate()).padStart(2, '0')
      return formatDate(`${yy}-${mm}-${dd}`)
    } catch {
      return ''
    }
  })()

  // The draft stores the whole form plus the uploaded contract's id/name (D-13, D-23)
  const draftPayload = (): { firstName: string; lastName: string; form: Record<string, unknown> } => ({
    firstName: form.firstName,
    lastName: form.lastName,
    form: {
      ...form,
      ...(contractDocumentId !== null && { contractDocumentId, contractFileName }),
    },
  })

  const createDraftMutation = useMutation({
    mutationFn: () => createDraft(draftPayload()),
    onSuccess: (result) => {
      setDraftId(result.draftId)
      showToast('Draft saved.')
    },
    onError: () => showToast('Failed to save draft.', 'error'),
  })

  const updateDraftMutation = useMutation({
    mutationFn: () => updateDraft(draftId!, draftPayload()),
    onSuccess: () => showToast('Draft saved.'),
    onError: () => showToast('Failed to save draft.', 'error'),
  })

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadDocument(file, 'CONTRACT'),
    onSuccess: (result, file) => {
      setContractDocumentId(result.documentId)
      setContractFileName(file.name)
      setErrors((prev) => ({ ...prev, contractFile: undefined }))
    },
    onError: (err: unknown) => {
      setContractDocumentId(null)
      setContractFileName('')
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        const first = (err.body as ApiValidationError).errors[0]
        setErrors((prev) => ({ ...prev, contractFile: first?.message }))
      } else {
        showToast('Failed to upload the contract file.', 'error')
      }
    },
  })

  const createMutation = useMutation({
    mutationFn: () =>
      createOnboarding({
        draftId: draftId ?? undefined,
        firstName: form.firstName,
        lastName: form.lastName,
        privateEmail: form.privateEmail,
        mobilePhone: form.mobilePhone,
        dateOfBirth: parseDate(form.dateOfBirth),
        nationality: form.nationality || undefined,
        street: form.street,
        postcode: form.postcode,
        city: form.city,
        contractType: form.contractType,
        startDate: parseDate(form.startDate),
        endDate: form.contractType === 'FIXED_TERM' && form.endDate ? parseDate(form.endDate) : undefined,
        hoursPerWeek: parseInt(form.hoursPerWeek, 10),
        probationMonths: parseInt(form.probationMonths, 10),
        idChecked: form.idChecked,
        contractSignedReceived: form.contractSignedReceived,
        rightToWorkVerified: form.rightToWorkVerified,
        contractDocumentId: contractDocumentId ?? undefined,
        jobTitleId: parseInt(form.jobTitleId, 10),
        departmentId: parseInt(form.departmentId, 10),
        managerId: parseInt(form.managerId, 10),
        grade: form.grade,
        appRole: form.appRole,
        buddyId: form.buddyId !== '' ? parseInt(form.buddyId, 10) : undefined,
        templateId: parseInt(form.templateId, 10),
      }),
    onSuccess: (result) => {
      showToast('Employee created.')
      void queryClient.invalidateQueries({ queryKey: ['employees'] })
      onCreated(result.employeeId)
    },
    onError: (err: unknown) => {
      if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED') {
        setErrors(toFieldErrors(err.body as ApiValidationError))
      } else if (err instanceof ApiResponseError && err.body.code === 'DUPLICATE_PRIVATE_EMAIL') {
        // ONB-09 / AC-06: show the message and a link to the existing employee
        const extra = (err.body as unknown as { existingEmployeeId?: unknown }).existingEmployeeId
        setExistingEmployeeId(typeof extra === 'number' ? extra : null)
        setErrors({ privateEmail: 'An employee with this e-mail address already exists.' })
      } else {
        showToast('Failed to save employee.', 'error')
      }
    },
  })

  const handleSaveDraft = (): void => {
    const errs = validateStep1(form, true)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    if (draftId === null) {
      createDraftMutation.mutate()
    } else {
      updateDraftMutation.mutate()
    }
  }

  const handleSaveAndContinue = (): void => {
    const errs = validateStep1(form, false)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    if (employeeId !== null) {
      updateMutation.mutate()
    } else {
      createMutation.mutate()
    }
  }

  const handleContractFile = (e: ChangeEvent<HTMLInputElement>): void => {
    const file = e.target.files?.[0]
    if (file !== undefined) uploadMutation.mutate(file)
  }

  const gradeOptions = (lookups?.grades ?? []).map((g) => ({ value: g, label: g.replace('_', ' ') }))
  const roleOptions = (lookups?.appRoles ?? []).map((r) => ({ value: r, label: r.replace('_', ' ') }))
  const deptOptions = (lookups?.departments ?? []).map((d) => ({ value: String(d.id), label: d.name }))
  const jobTitleOptions = (lookups?.jobTitles ?? []).map((jt) => ({ value: String(jt.id), label: jt.name }))
  const managerOptions = (lookups?.managers ?? []).map((m) => ({ value: String(m.id), label: m.name }))
  const buddyOptions = (lookups?.buddies ?? []).map((b) => ({ value: String(b.id), label: b.name }))
  const templateOptions = (lookups?.templates ?? []).map((t) => ({ value: String(t.id), label: t.name }))
  const permanent = form.contractType !== 'FIXED_TERM'

  return (
    <div className="space-y-6">
      {/* Personal details */}
      <Card title="Personal details">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field id="firstName" label="First name" value={form.firstName} onChange={(v) => setField('firstName', String(v))} required error={errors.firstName} />
          <Field id="lastName" label="Last name" value={form.lastName} onChange={(v) => setField('lastName', String(v))} required error={errors.lastName} />
          <div>
            <Field id="privateEmail" disabled={locked} label="Private e-mail" type="email" value={form.privateEmail} onChange={(v) => setField('privateEmail', String(v))} required error={errors.privateEmail} />
            {existingEmployeeId !== null && (
              <Link
                to={`/employees/${existingEmployeeId}`}
                className="mt-1 inline-block text-xs text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
              >
                Open the existing employee →
              </Link>
            )}
          </div>
          <Field id="mobilePhone" label="Mobile phone" type="tel" value={form.mobilePhone} onChange={(v) => setField('mobilePhone', String(v))} required placeholder="+31 6 12345678" error={errors.mobilePhone} />
          <Field id="dateOfBirth" disabled={locked} label="Date of birth (dd-mm-yyyy)" type="date" value={form.dateOfBirth} onChange={(v) => setField('dateOfBirth', String(v))} required placeholder="dd-mm-yyyy" error={errors.dateOfBirth} />
          <Field id="nationality" disabled={locked} label="Nationality" value={form.nationality} onChange={(v) => setField('nationality', String(v))} />
          <div className="sm:col-span-2">
            <Field id="street" label="Street + number" value={form.street} onChange={(v) => setField('street', String(v))} required error={errors.street} />
          </div>
          <Field id="postcode" label="Postcode" value={form.postcode} onChange={(v) => setField('postcode', String(v))} required placeholder="3511 AV" error={errors.postcode} />
          <Field id="city" label="City" value={form.city} onChange={(v) => setField('city', String(v))} required error={errors.city} />
        </div>
      </Card>

      {/* Contract */}
      <Card title="Contract">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            id="contractType" disabled={locked} label="Contract type" type="select"
            value={form.contractType}
            onChange={(v) => setField('contractType', String(v))}
            required
            options={[
              { value: 'PERMANENT', label: 'Permanent' },
              { value: 'FIXED_TERM', label: 'Fixed-term' },
            ]}
            error={errors.contractType}
          />
          <Field id="startDate" label="Start date (dd-mm-yyyy)" type="date" value={form.startDate} onChange={(v) => setField('startDate', String(v))} required placeholder="dd-mm-yyyy" error={errors.startDate} />
          {/* Permanent: shown disabled and not required (FRD-02 "Other paths") */}
          <Field
            id="endDate" label="End date (dd-mm-yyyy)" type="date"
            value={form.endDate}
            onChange={(v) => setField('endDate', String(v))}
            required={!permanent}
            disabled={permanent}
            placeholder="dd-mm-yyyy"
            error={errors.endDate}
          />
          <Field id="hoursPerWeek" label="Hours per week" type="number" value={form.hoursPerWeek} onChange={(v) => setField('hoursPerWeek', String(v))} required min={1} max={40} error={errors.hoursPerWeek} />
          <Field
            id="probationMonths" label="Probation period" type="select"
            value={form.probationMonths}
            onChange={(v) => setField('probationMonths', String(v))}
            required
            options={[
              { value: '1', label: '1 month' },
              { value: '2', label: '2 months' },
              { value: '0', label: 'None' },
            ]}
            error={errors.probationMonths}
          />
          {probationEnd !== '' && (
            <Field id="probationEnd" label="Probation ends" value={probationEnd} readOnly />
          )}
        </div>
      </Card>

      {/* Verification */}
      <Card title="Verification">
        <div className="space-y-3">
          <Field id="idChecked" disabled={locked} label="ID document checked in person" type="checkbox" value={form.idChecked} onChange={(v) => setField('idChecked', Boolean(v))} required />
          <Field id="contractSignedReceived" disabled={locked} label="Signed contract received" type="checkbox" value={form.contractSignedReceived} onChange={(v) => setField('contractSignedReceived', Boolean(v))} required />
          <Field id="rightToWorkVerified" disabled={locked} label="Right-to-work verified" type="checkbox" value={form.rightToWorkVerified} onChange={(v) => setField('rightToWorkVerified', Boolean(v))} />
          {errors.verification !== undefined && (
            <p role="alert" className="text-xs text-red-600">{errors.verification}</p>
          )}
          {!locked && (
            <div className="pt-2">
              <label htmlFor="contractFile" className="block text-sm font-medium text-gray-700 mb-1">
                Contract file
              </label>
              <input
                id="contractFile"
                type="file"
                accept="application/pdf,image/png,image/jpeg"
                onChange={handleContractFile}
                disabled={uploadMutation.isPending}
                className="block w-full text-sm text-gray-700 file:mr-3 file:px-3 file:py-2 file:rounded-md file:border file:border-gray-300 file:bg-white file:text-sm min-h-[44px]"
              />
              <p className="mt-1 text-xs text-gray-500">PDF, PNG or JPEG, up to 10 MB.</p>
              {uploadMutation.isPending && <p className="mt-1 text-xs text-gray-500">Uploading…</p>}
              {contractDocumentId !== null && !uploadMutation.isPending && (
                <p className="mt-1 text-xs text-green-700">Uploaded: {contractFileName}</p>
              )}
              {errors.contractFile !== undefined && (
                <p role="alert" className="mt-1 text-xs text-red-600">{errors.contractFile}</p>
              )}
            </div>
          )}
        </div>
      </Card>

      {/* Role & reporting + Template preview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Role & reporting">
          <div className="space-y-4">
            <Field id="jobTitleId" disabled={locked} label="Job title" type="select" value={form.jobTitleId} onChange={(v) => setField('jobTitleId', String(v))} required options={jobTitleOptions} error={errors.jobTitleId} />
            <Field id="departmentId" disabled={locked} label="Department" type="select" value={form.departmentId} onChange={(v) => setField('departmentId', String(v))} required options={deptOptions} error={errors.departmentId} />
            <Field id="managerId" disabled={locked} label="Manager" type="select" value={form.managerId} onChange={(v) => setField('managerId', String(v))} required options={managerOptions} error={errors.managerId} />
            <Field id="grade" disabled={locked} label="Grade" type="select" value={form.grade} onChange={(v) => setField('grade', String(v))} required options={gradeOptions} error={errors.grade} />
            <Field id="appRole" disabled={locked} label="Application role" type="select" value={form.appRole} onChange={(v) => setField('appRole', String(v))} required options={roleOptions} error={errors.appRole} />
            <Field id="buddyId" disabled={locked} label="Buddy" type="select" value={form.buddyId} onChange={(v) => setField('buddyId', String(v))} options={buddyOptions} />
            <Field id="templateId" disabled={locked} label="Onboarding template" type="select" value={form.templateId} onChange={(v) => setField('templateId', String(v))} required options={templateOptions} error={errors.templateId} />
          </div>
        </Card>

        {/* Template preview panel: total, then each section with its count and item names */}
        <Card title="Template preview">
          {preview !== undefined ? (
            <div className="text-sm space-y-3">
              <p className="font-medium text-gray-900">
                {preview.totalItems} checklist items will be created
              </p>
              {preview.sections.map((s) => (
                <div key={s.section}>
                  <p className="flex justify-between text-gray-700 font-medium">
                    <span>{SECTION_LABELS[s.section] ?? s.section.replace(/_/g, ' ')}</span>
                    <span>({s.count})</span>
                  </p>
                  <ul className="mt-1 ml-4 list-disc text-gray-600 space-y-0.5">
                    {s.items.map((name, i) => (
                      <li key={`${s.section}-${i}`}>{name}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-400">Select a template to preview.</p>
          )}
        </Card>
      </div>

      {/* Action bar (R-UI-1) */}
      <div className="flex justify-between items-center pt-4 border-t border-gray-200">
        {/* Cancel — red outline, left */}
        <button
          type="button"
          onClick={() => window.history.back()}
          className="px-4 py-2 text-sm font-medium rounded-md border border-red-500 text-red-500 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors"
        >
          Cancel
        </button>
        {/* Save draft + Save & continue — green, right */}
        <div className="flex gap-3">
          {employeeId === null && (
            <button
              type="button"
              onClick={handleSaveDraft}
              disabled={createDraftMutation.isPending || updateDraftMutation.isPending}
              className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
            >
              Save draft
            </button>
          )}
          <button
            type="button"
            onClick={handleSaveAndContinue}
            disabled={createMutation.isPending || updateMutation.isPending || uploadMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {createMutation.isPending || updateMutation.isPending ? 'Saving…' : 'Save & continue'}
          </button>
        </div>
      </div>
    </div>
  )
}
