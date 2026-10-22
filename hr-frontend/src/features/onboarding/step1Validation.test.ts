/**
 * Unit tests for step-1 client validation (S2). Messages must equal the backend's
 * exactly (R-SRC-3, R-CODE-2).
 */

import { describe, expect, it } from 'vitest'
import { EMPTY_STEP1_FORM, formFromDraft, isAtLeast16, validateStep1 } from './step1Validation'
import type { Step1FormState } from './step1Validation'

const today = new Date(2026, 9, 21)

const valid: Step1FormState = {
  ...EMPTY_STEP1_FORM,
  firstName: 'Jesse', lastName: 'Willems', privateEmail: 'jesse@outlook.com',
  mobilePhone: '+31 6 12345678', dateOfBirth: '21-10-2000', street: 'Keizersgracht 1',
  postcode: '3511 AV', city: 'Utrecht', contractType: 'PERMANENT', startDate: '05-01-2027',
  hoursPerWeek: '40', probationMonths: '2', idChecked: true, contractSignedReceived: true,
  jobTitleId: '1', departmentId: '1', managerId: '7', grade: 'MEDIOR', appRole: 'EMPLOYEE',
  templateId: '1',
}

describe('validateStep1', () => {
  it('accepts a complete permanent contract without an end date', () => {
    expect(validateStep1(valid, false, today)).toEqual({})
  })

  it('requires a manager and a probation period (no silent default)', () => {
    const errs = validateStep1({ ...valid, managerId: '', probationMonths: '' }, false, today)
    expect(errs.managerId).toBe('This field is required.')
    expect(errs.probationMonths).toBe('This field is required.')
    expect(EMPTY_STEP1_FORM.probationMonths).toBe('')
  })

  it('accepts probation "None" (0) as an explicit choice', () => {
    expect(validateStep1({ ...valid, probationMonths: '0' }, false, today).probationMonths).toBeUndefined()
  })

  it('rejects an employee younger than 16 with the exact message', () => {
    expect(validateStep1({ ...valid, dateOfBirth: '22-10-2010' }, false, today).dateOfBirth)
      .toBe('The employee must be at least 16 years old.')
    expect(isAtLeast16('2010-10-21', today)).toBe(true)
  })

  it('fixed-term: end date required, and must be after the start date', () => {
    const ft = { ...valid, contractType: 'FIXED_TERM' }
    expect(validateStep1({ ...ft, endDate: '' }, false, today).endDate).toBe('This field is required.')
    expect(validateStep1({ ...ft, endDate: '05-01-2027' }, false, today).endDate)
      .toBe('The end date must be after the start date.')
    expect(validateStep1({ ...ft, endDate: '06-01-2027' }, false, today).endDate).toBeUndefined()
  })

  it('a draft checks only first and last name (D-13)', () => {
    expect(validateStep1({ ...EMPTY_STEP1_FORM, firstName: 'A', lastName: 'B' }, true, today)).toEqual({})
  })
})

describe('formFromDraft', () => {
  it('restores known fields with the right type and ignores the rest', () => {
    const form = formFromDraft({ privateEmail: 'x@y.nl', idChecked: true, hoursPerWeek: 32, evil: '<x>' })
    expect(form.privateEmail).toBe('x@y.nl')
    expect(form.idChecked).toBe(true)
    expect(form.hoursPerWeek).toBe('40') // a number is not accepted for a string field
    expect((form as unknown as Record<string, unknown>)['evil']).toBeUndefined()
  })
})
