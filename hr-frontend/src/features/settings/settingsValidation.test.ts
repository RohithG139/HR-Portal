/**
 * Unit tests for pure validation helpers in settingsValidation.ts.
 *
 * These are pure-function tests — no React rendering needed.
 * Covered behaviours:
 *   - validateFormHasSectionWithQuestion: the AC-09 client-side guard
 *   - validateCcField: the AC-11 CC validation
 *   - buildTimerUpdate: used by the timers tab to compute changed rows
 */

import { describe, expect, it } from 'vitest'
import {
  validateFormHasSectionWithQuestion,
  validateCcField,
  buildTimerUpdate,
} from './settingsValidation'
import type { FormSectionDto } from '../../api/settingsAppraisalForms'

// ---------------------------------------------------------------------------
// validateFormHasSectionWithQuestion (AC-09)
// ---------------------------------------------------------------------------

describe('validateFormHasSectionWithQuestion', () => {
  const makeSection = (questionCount: number): FormSectionDto => ({
    id: 1,
    name: 'Test section',
    bySelf: false,
    byPeer: false,
    byManager: true,
    byHr: false,
    sortOrder: 1,
    questions: Array.from({ length: questionCount }, (_, i) => ({
      id: i + 1,
      text: `Question ${i + 1}`,
      description: null,
      answerType: 'SCALE_1_5',
      required: true,
      sortOrder: i + 1,
    })),
  })

  it('returns the error message when there are no sections', () => {
    // AC-09: no sections → cannot activate
    expect(validateFormHasSectionWithQuestion([])).toBe(
      'Add at least one section with a question.',
    )
  })

  it('returns the error message when a section has no questions', () => {
    // AC-09: section exists but has no questions → cannot activate
    const sections = [makeSection(0)]
    expect(validateFormHasSectionWithQuestion(sections)).toBe(
      'Add at least one section with a question.',
    )
  })

  it('returns the error when only some sections have questions (all must have at least one)', () => {
    const sections = [makeSection(2), makeSection(0)]
    expect(validateFormHasSectionWithQuestion(sections)).toBe(
      'Add at least one section with a question.',
    )
  })

  it('returns null when all sections have at least one question', () => {
    const sections = [makeSection(1), makeSection(3)]
    expect(validateFormHasSectionWithQuestion(sections)).toBeNull()
  })

  it('returns null for a single section with one question', () => {
    expect(validateFormHasSectionWithQuestion([makeSection(1)])).toBeNull()
  })
})

// ---------------------------------------------------------------------------
// validateCcField (AC-11)
// ---------------------------------------------------------------------------

describe('validateCcField', () => {
  it('returns null for an empty CC string (CC is optional)', () => {
    expect(validateCcField('')).toBeNull()
  })

  it('returns null for a whitespace-only CC string', () => {
    expect(validateCcField('   ')).toBeNull()
  })

  it('returns null for a single valid e-mail address', () => {
    expect(validateCcField('hr@cubeworks.nl')).toBeNull()
  })

  it('returns null for multiple valid e-mail addresses', () => {
    expect(validateCcField('hr@cubeworks.nl, cto@cubeworks.nl')).toBeNull()
  })

  it('returns the error message for an invalid address (AC-11)', () => {
    expect(validateCcField('not-an-email')).toBe(
      'Enter valid e-mail addresses, separated by commas.',
    )
  })

  it('returns the error message when one address in a list is invalid', () => {
    // AC-11 scenario verbatim: "hr@cubeworks.nl, not-an-email"
    expect(validateCcField('hr@cubeworks.nl, not-an-email')).toBe(
      'Enter valid e-mail addresses, separated by commas.',
    )
  })

  it('returns the error for an address missing the @', () => {
    expect(validateCcField('notanemail.com')).toBe(
      'Enter valid e-mail addresses, separated by commas.',
    )
  })
})

// ---------------------------------------------------------------------------
// buildTimerUpdate (used by TimersTab Promise.all save logic)
// ---------------------------------------------------------------------------

describe('buildTimerUpdate', () => {
  const original = {
    valueInt: 14,
    valueText: null,
    recipientRole: 'MANAGER',
    enabled: true,
    version: 0,
  }

  it('returns null when nothing changed', () => {
    expect(buildTimerUpdate(original, { ...original })).toBeNull()
  })

  it('includes valueInt when it changed', () => {
    const patch = buildTimerUpdate(original, { ...original, valueInt: 10 })
    expect(patch).not.toBeNull()
    expect(patch?.valueInt).toBe(10)
  })

  it('does not include unchanged fields', () => {
    const patch = buildTimerUpdate(original, { ...original, valueInt: 10 })
    // Only valueInt should be in the patch; others are unchanged
    expect(patch).not.toHaveProperty('valueText')
    expect(patch).not.toHaveProperty('recipientRole')
    expect(patch).not.toHaveProperty('enabled')
  })

  it('includes enabled when it changed', () => {
    const patch = buildTimerUpdate(original, { ...original, enabled: false })
    expect(patch?.enabled).toBe(false)
  })

  it('includes valueText when it changed (KPI_CYCLE scenario)', () => {
    const kpiOriginal = { valueInt: null, valueText: 'QUARTERLY', recipientRole: 'MANAGER', enabled: true, version: 0 }
    const patch = buildTimerUpdate(kpiOriginal, { ...kpiOriginal, valueText: 'HALF_YEARLY' })
    expect(patch?.valueText).toBe('HALF_YEARLY')
  })

  it('includes all changed fields when multiple fields change', () => {
    const patch = buildTimerUpdate(original, { ...original, valueInt: 7, enabled: false })
    expect(patch?.valueInt).toBe(7)
    expect(patch?.enabled).toBe(false)
  })
})
