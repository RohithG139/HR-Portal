/**
 * @component Step1Details
 * @description S2 — Step 1: Employee details form. Handles draft save and full submission.
 * Validation messages are exact copies from spec §5 (R-SRC-3).
 * @props employeeId - number|null - existing employee (null = new)
 * @props onCreated - function - called with new employeeId after successful creation
 * @props onContinue - function - called when navigating to step 2 for existing employee
 * @example
 * <Step1Details employeeId={null} onCreated={(id) => navigate(`/onboarding/${id}`)} onContinue={...} />
 */

import { useState, useEffect } from 'react'
import type { ReactElement } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createOnboarding, createDraft, updateDraft,
  getOnboarding, getTemplatePreview,
} from '../../api/onboarding'
import { getOnboardingLookups } from '../../api/lookups'
import { ApiResponseError } from '../../api/types'
import type { ApiValidationError } from '../../api/types'
import { Card, Field, useToast } from '../../components/index'
import { formatDate, parseDate } from '../../lib/date'
import { isValidEmail, isValidPhone } from '../../lib/validation'

interface Step1Props {
  employeeId: number | null
  onCreated: (employeeId: number) => void
  onContinue: () => void
  /**
   * Resuming a previously-saved draft (from the Employees list "Continue" action).
   * Only the draft id and the two fields A08 validates are available here — the
   * backend has no `GET /api/onboarding-drafts/{id}` endpoint (not in spec.md §6),
   * so the rest of a previously-entered form cannot be restored; the employee
   * re-enters it. See F01 spec.md §12 D-23. `undefined` for a brand-new draft.
   */
  initialDraftId?: number
  initialFirstName?: string
  initialLastName?: string
}

interface FormState {
  firstName: string
  lastName: string
  privateEmail: string
  mobilePhone: string
  dateOfBirth: string // display format dd-mm-yyyy
  nationality: string
  street: string
  postcode: string
  city: string
  contractType: string
  startDate: string // display format dd-mm-yyyy
  endDate: string // display format dd-mm-yyyy
  hoursPerWeek: string
  probationMonths: string
  idChecked: boolean
  contractSignedReceived: boolean
  rightToWorkVerified: boolean
  jobTitleId: string
  departmentId: string
  managerId: string
  grade: string
  appRole: string
  buddyId: string
  templateId: string
}

const EMPTY_FORM: FormState = {
  firstName: '', lastName: '', privateEmail: '', mobilePhone: '',
  dateOfBirth: '', nationality: '', street: '', postcode: '', city: '',
  contractType: 'PERMANENT', startDate: '', endDate: '',
  hoursPerWeek: '40', probationMonths: '0',
  idChecked: false, contractSignedReceived: false, rightToWorkVerified: false,
  jobTitleId: '', departmentId: '', managerId: '', grade: '',
  appRole: 'EMPLOYEE', buddyId: '', templateId: '',
}

type FieldErrors = Partial<Record<keyof FormState | 'verification' | '_general', string>>

/** Client-side validation. Messages are exact copies from spec §5 (R-SRC-3). */
function validate(form: FormState, isDraft: boolean): FieldErrors {
  if (isDraft) {
    const errs: FieldErrors = {}
    if (form.firstName.trim() === '') errs.firstName = 'This field is required.'
    if (form.lastName.trim() === '') errs.lastName = 'This field is required.'
    return errs
  }
  const errs: FieldErrors = {}
  if (form.firstName.trim() === '') errs.firstName = 'This field is required.'
  if (form.lastName.trim() === '') errs.lastName = 'This field is required.'
  if (form.privateEmail.trim() === '') errs.privateEmail = 'This field is required.'
  else if (!isValidEmail(form.privateEmail)) errs.privateEmail = 'Enter a valid e-mail address.'
  if (form.mobilePhone.trim() === '') errs.mobilePhone = 'This field is required.'
  else if (!isValidPhone(form.mobilePhone)) errs.mobilePhone = 'Enter a valid phone number, e.g. +31 6 12345678.'
  if (form.dateOfBirth.trim() === '') errs.dateOfBirth = 'This field is required.'
  if (form.street.trim() === '') errs.street = 'This field is required.'
  if (form.postcode.trim() === '') errs.postcode = 'This field is required.'
  else if (!/^\d{4} [A-Z]{2}$/.test(form.postcode)) errs.postcode = 'Enter a valid postcode, e.g. 3511 AV.'
  if (form.city.trim() === '') errs.city = 'This field is required.'
  if (form.startDate.trim() === '') errs.startDate = 'This field is required.'
  if (form.contractType === 'FIXED_TERM' && form.endDate.trim() === '') errs.endDate = 'This field is required.'
  if (form.hoursPerWeek === '' || +form.hoursPerWeek < 1 || +form.hoursPerWeek > 40) {
    errs.hoursPerWeek = 'Hours must be between 1 and 40.'
  }
  if (!form.idChecked || !form.contractSignedReceived) {
    errs.verification = 'Confirm that the ID was checked and the contract is signed.'
  }
  if (form.jobTitleId === '') errs.jobTitleId = 'This field is required.'
  if (form.departmentId === '') errs.departmentId = 'This field is required.'
  if (form.grade === '') errs.grade = 'This field is required.'
  if (form.appRole === '') errs.appRole = 'This field is required.'
  if (form.templateId === '') errs.templateId = 'This field is required.'
  return errs
}

/**
 * Step1Details renders the employee-details form.
 *
 * Draft mode: only firstName/lastName are required (D-13). A draft ID is stored
 * in local state; subsequent "Save draft" calls use PUT /api/onboarding-drafts/{id}.
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
  const [form, setForm] = useState<FormState>(() => ({
    ...EMPTY_FORM,
    firstName: initialFirstName ?? '',
    lastName: initialLastName ?? '',
  }))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [draftId, setDraftId] = useState<number | null>(initialDraftId ?? null)

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

  // Template preview — re-fetched whenever templateId changes
  const { data: preview } = useQuery({
    queryKey: ['template-preview', form.templateId],
    queryFn: () => getTemplatePreview(parseInt(form.templateId, 10)),
    enabled: form.templateId !== '',
  })

  // Populate form when editing an existing employee
  useEffect(() => {
    if (existing !== undefined) {
      setForm((f) => ({
        ...f,
        startDate: existing.startDate !== '' ? formatDate(existing.startDate) : '',
        endDate: existing.endDate !== null ? formatDate(existing.endDate) : '',
      }))
    }
  }, [existing])

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]): void => {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
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

  // Mutations
  const createDraftMutation = useMutation({
    mutationFn: () => createDraft({ firstName: form.firstName, lastName: form.lastName }),
    onSuccess: (result) => {
      setDraftId(result.draftId)
      showToast('Draft saved.')
    },
    onError: () => showToast('Failed to save draft.', 'error'),
  })

  const updateDraftMutation = useMutation({
    mutationFn: () => updateDraft(draftId!, { firstName: form.firstName, lastName: form.lastName }),
    onSuccess: () => showToast('Draft saved.'),
    onError: () => showToast('Failed to save draft.', 'error'),
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
        jobTitleId: parseInt(form.jobTitleId, 10),
        departmentId: parseInt(form.departmentId, 10),
        managerId: form.managerId !== '' ? parseInt(form.managerId, 10) : undefined,
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
        const ve = err.body as ApiValidationError
        const fieldErrs: FieldErrors = {}
        ve.errors.forEach((e) => {
          if (e.field !== undefined) {
            const k = e.field as keyof FormState
            fieldErrs[k] = e.message
          }
        })
        setErrors(fieldErrs)
      } else if (err instanceof ApiResponseError && err.body.code === 'DUPLICATE_PRIVATE_EMAIL') {
        setErrors({ privateEmail: 'An employee with this e-mail address already exists.' })
      } else {
        showToast('Failed to save employee.', 'error')
      }
    },
  })

  const handleSaveDraft = (): void => {
    const errs = validate(form, true)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    if (draftId === null) {
      createDraftMutation.mutate()
    } else {
      updateDraftMutation.mutate()
    }
  }

  const handleSaveAndContinue = (): void => {
    const errs = validate(form, false)
    if (Object.keys(errs).length > 0) { setErrors(errs); return }
    if (employeeId !== null) {
      onContinue()
    } else {
      createMutation.mutate()
    }
  }

  const gradeOptions = (lookups?.grades ?? []).map((g) => ({ value: g, label: g.replace('_', ' ') }))
  const roleOptions = (lookups?.appRoles ?? []).map((r) => ({ value: r, label: r.replace('_', ' ') }))
  const deptOptions = (lookups?.departments ?? []).map((d) => ({ value: String(d.id), label: d.name }))
  const jobTitleOptions = (lookups?.jobTitles ?? []).map((jt) => ({ value: String(jt.id), label: jt.name }))
  const managerOptions = (lookups?.managers ?? []).map((m) => ({ value: String(m.id), label: m.name }))
  const buddyOptions = (lookups?.buddies ?? []).map((b) => ({ value: String(b.id), label: b.name }))
  const templateOptions = (lookups?.templates ?? []).map((t) => ({ value: String(t.id), label: t.name }))

  return (
    <div className="space-y-6">
      {/* Personal details */}
      <Card title="Personal details">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field id="firstName" label="First name" value={form.firstName} onChange={(v) => setField('firstName', String(v))} required error={errors.firstName} />
          <Field id="lastName" label="Last name" value={form.lastName} onChange={(v) => setField('lastName', String(v))} required error={errors.lastName} />
          <Field id="privateEmail" label="Private e-mail" type="email" value={form.privateEmail} onChange={(v) => setField('privateEmail', String(v))} required error={errors.privateEmail} />
          <Field id="mobilePhone" label="Mobile phone" type="tel" value={form.mobilePhone} onChange={(v) => setField('mobilePhone', String(v))} required placeholder="+31 6 12345678" error={errors.mobilePhone} />
          <Field id="dateOfBirth" label="Date of birth (dd-mm-yyyy)" type="text" value={form.dateOfBirth} onChange={(v) => setField('dateOfBirth', String(v))} required placeholder="dd-mm-yyyy" error={errors.dateOfBirth} />
          <Field id="nationality" label="Nationality" value={form.nationality} onChange={(v) => setField('nationality', String(v))} />
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
            id="contractType" label="Contract type" type="select"
            value={form.contractType}
            onChange={(v) => setField('contractType', String(v))}
            required
            options={[
              { value: 'PERMANENT', label: 'Permanent' },
              { value: 'FIXED_TERM', label: 'Fixed-term' },
            ]}
            error={errors.contractType}
          />
          <Field id="startDate" label="Start date (dd-mm-yyyy)" value={form.startDate} onChange={(v) => setField('startDate', String(v))} required placeholder="dd-mm-yyyy" error={errors.startDate} />
          {form.contractType === 'FIXED_TERM' && (
            <Field id="endDate" label="End date (dd-mm-yyyy)" value={form.endDate} onChange={(v) => setField('endDate', String(v))} required placeholder="dd-mm-yyyy" error={errors.endDate} />
          )}
          <Field id="hoursPerWeek" label="Hours per week" type="number" value={form.hoursPerWeek} onChange={(v) => setField('hoursPerWeek', String(v))} required min={1} max={40} error={errors.hoursPerWeek} />
          <Field
            id="probationMonths" label="Probation period" type="select"
            value={form.probationMonths}
            onChange={(v) => setField('probationMonths', String(v))}
            options={[
              { value: '0', label: 'None' },
              { value: '1', label: '1 month' },
              { value: '2', label: '2 months' },
            ]}
          />
          {probationEnd !== '' && (
            <Field id="probationEnd" label="Probation ends" value={probationEnd} readOnly />
          )}
        </div>
      </Card>

      {/* Verification */}
      <Card title="Verification">
        <div className="space-y-3">
          <Field id="idChecked" label="ID document checked in person" type="checkbox" value={form.idChecked} onChange={(v) => setField('idChecked', Boolean(v))} required />
          <Field id="contractSignedReceived" label="Signed contract received" type="checkbox" value={form.contractSignedReceived} onChange={(v) => setField('contractSignedReceived', Boolean(v))} required />
          <Field id="rightToWorkVerified" label="Right-to-work verified" type="checkbox" value={form.rightToWorkVerified} onChange={(v) => setField('rightToWorkVerified', Boolean(v))} />
          {errors.verification !== undefined && (
            <p role="alert" className="text-xs text-red-600">{errors.verification}</p>
          )}
        </div>
      </Card>

      {/* Role & reporting + Template preview */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Role & reporting">
          <div className="space-y-4">
            <Field id="jobTitleId" label="Job title" type="select" value={form.jobTitleId} onChange={(v) => setField('jobTitleId', String(v))} required options={jobTitleOptions} error={errors.jobTitleId} />
            <Field id="departmentId" label="Department" type="select" value={form.departmentId} onChange={(v) => setField('departmentId', String(v))} required options={deptOptions} error={errors.departmentId} />
            <Field id="managerId" label="Manager" type="select" value={form.managerId} onChange={(v) => setField('managerId', String(v))} options={managerOptions} />
            <Field id="grade" label="Grade" type="select" value={form.grade} onChange={(v) => setField('grade', String(v))} required options={gradeOptions} error={errors.grade} />
            <Field id="appRole" label="Application role" type="select" value={form.appRole} onChange={(v) => setField('appRole', String(v))} required options={roleOptions} error={errors.appRole} />
            <Field id="buddyId" label="Buddy" type="select" value={form.buddyId} onChange={(v) => setField('buddyId', String(v))} options={buddyOptions} />
            <Field id="templateId" label="Onboarding template" type="select" value={form.templateId} onChange={(v) => setField('templateId', String(v))} required options={templateOptions} error={errors.templateId} />
          </div>
        </Card>

        {/* Template preview panel */}
        <Card title="Template preview">
          {preview !== undefined ? (
            <div className="text-sm space-y-2">
              <p className="font-medium text-gray-900">
                {preview.totalItems} checklist items will be created
              </p>
              <ul className="space-y-1">
                {preview.sections.map((s) => (
                  <li key={s.section} className="flex justify-between text-gray-600">
                    <span>{s.section.replace(/_/g, ' ')}</span>
                    <span className="font-medium">({s.count})</span>
                  </li>
                ))}
              </ul>
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
            disabled={createMutation.isPending}
            className="px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50"
          >
            {createMutation.isPending ? 'Saving…' : 'Save & continue'}
          </button>
        </div>
      </div>
    </div>
  )
}
