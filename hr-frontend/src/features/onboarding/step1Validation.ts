/**
 * Step 1 (S2 Employee details) form state and client-side validation.
 *
 * Messages are exact copies of the backend's (spec §5, R-SRC-3) — the client mirrors the
 * server checks (R-CODE-2); the server remains the authority.
 */

import { toIsoDateOrEmpty } from '../../lib/date'
import { isValidEmail, isValidPhone } from '../../lib/validation'

/** Step-1 form values; dates are held in display format `dd-mm-yyyy`. */
export interface Step1FormState {
  firstName: string
  lastName: string
  privateEmail: string
  mobilePhone: string
  dateOfBirth: string
  nationality: string
  street: string
  postcode: string
  city: string
  contractType: string
  startDate: string
  endDate: string
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

/** Empty form. Probation period has no default: HR must choose (spec §5 "Probation period*"). */
export const EMPTY_STEP1_FORM: Step1FormState = {
  firstName: '', lastName: '', privateEmail: '', mobilePhone: '',
  dateOfBirth: '', nationality: '', street: '', postcode: '', city: '',
  contractType: 'PERMANENT', startDate: '', endDate: '',
  hoursPerWeek: '40', probationMonths: '',
  idChecked: false, contractSignedReceived: false, rightToWorkVerified: false,
  jobTitleId: '', departmentId: '', managerId: '', grade: '',
  appRole: 'EMPLOYEE', buddyId: '', templateId: '',
}

export type Step1Errors = Partial<Record<keyof Step1FormState | 'verification' | 'contractFile', string>>

const REQUIRED = 'This field is required.'

/** True when someone born on `dobIso` is at least 16 on `today` (local calendar dates). */
export function isAtLeast16(dobIso: string, today: Date = new Date()): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dobIso)
  if (m === null) return true // format errors are not an age error
  const sixteenth = new Date(Number(m[1]) + 16, Number(m[2]) - 1, Number(m[3]))
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return sixteenth.getTime() <= t.getTime()
}

/**
 * Validates step 1. A draft checks only first and last name (D-13).
 *
 * @param form    the form values
 * @param isDraft true for "Save draft"
 * @param today   injectable "today" for the age rule
 */
export function validateStep1(form: Step1FormState, isDraft: boolean, today: Date = new Date()): Step1Errors {
  const errs: Step1Errors = {}
  if (form.firstName.trim() === '') errs.firstName = REQUIRED
  if (form.lastName.trim() === '') errs.lastName = REQUIRED
  if (isDraft) return errs

  if (form.privateEmail.trim() === '') errs.privateEmail = REQUIRED
  else if (!isValidEmail(form.privateEmail)) errs.privateEmail = 'Enter a valid e-mail address.'
  if (form.mobilePhone.trim() === '') errs.mobilePhone = REQUIRED
  else if (!isValidPhone(form.mobilePhone)) errs.mobilePhone = 'Enter a valid phone number, e.g. +31 6 12345678.'
  if (form.dateOfBirth.trim() === '') errs.dateOfBirth = REQUIRED
  else if (!isAtLeast16(toIsoDateOrEmpty(form.dateOfBirth), today)) {
    errs.dateOfBirth = 'The employee must be at least 16 years old.'
  }
  if (form.street.trim() === '') errs.street = REQUIRED
  if (form.postcode.trim() === '') errs.postcode = REQUIRED
  else if (!/^\d{4} [A-Z]{2}$/.test(form.postcode)) errs.postcode = 'Enter a valid postcode, e.g. 3511 AV.'
  if (form.city.trim() === '') errs.city = REQUIRED
  if (form.startDate.trim() === '') errs.startDate = REQUIRED
  if (form.contractType === 'FIXED_TERM') {
    if (form.endDate.trim() === '') {
      errs.endDate = REQUIRED
    } else {
      const start = toIsoDateOrEmpty(form.startDate)
      const end = toIsoDateOrEmpty(form.endDate)
      if (start !== '' && end !== '' && end <= start) {
        errs.endDate = 'The end date must be after the start date.'
      }
    }
  }
  if (form.hoursPerWeek === '' || +form.hoursPerWeek < 1 || +form.hoursPerWeek > 40) {
    errs.hoursPerWeek = 'Hours must be between 1 and 40.'
  }
  if (form.probationMonths === '') errs.probationMonths = REQUIRED
  if (!form.idChecked || !form.contractSignedReceived) {
    errs.verification = 'Confirm that the ID was checked and the contract is signed.'
  }
  if (form.jobTitleId === '') errs.jobTitleId = REQUIRED
  if (form.departmentId === '') errs.departmentId = REQUIRED
  if (form.managerId === '') errs.managerId = REQUIRED
  if (form.grade === '') errs.grade = REQUIRED
  if (form.appRole === '') errs.appRole = REQUIRED
  if (form.templateId === '') errs.templateId = REQUIRED
  return errs
}

/**
 * Restores a draft's saved `form` (A09a) onto the empty form, keeping only known fields
 * of the right type — the stored JSON is not trusted to match the current form shape.
 */
export function formFromDraft(saved: Record<string, unknown>): Step1FormState {
  const form: Step1FormState = { ...EMPTY_STEP1_FORM }
  const target = form as unknown as Record<string, unknown>
  for (const key of Object.keys(EMPTY_STEP1_FORM) as (keyof Step1FormState)[]) {
    const v = saved[key]
    if (typeof v === typeof EMPTY_STEP1_FORM[key]) target[key] = v
  }
  return form
}
