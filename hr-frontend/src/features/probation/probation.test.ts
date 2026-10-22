/**
 * Probation review (FRD-02 Screen 12/13): task routes and the client-side mirror of the P03
 * validation messages (R-CODE-2, exact FRD texts).
 */

import { describe, expect, it } from 'vitest'
import { taskRoute } from '../tasks/taskRoute'
import { MSG_REQUIRED, MSG_SUMMARY_OUTCOME, validateProbationReview } from './ProbationReviewPage'
import type { ProbationQuestionDto } from '../../api/probation'

const q = (questionId: number, answerType: ProbationQuestionDto['answerType']): ProbationQuestionDto => ({
  questionId,
  section: 'Probation review',
  text: `Q${questionId}`,
  description: null,
  answerType,
  required: true,
  rating: null,
  yesNo: null,
  textAnswer: null,
})

const review = { questions: [q(1, 'SCALE_1_5'), q(2, 'SCALE_1_5'), q(3, 'TEXT')] }

describe('probation task routes', () => {
  const ref = { employeeCode: 'E-0031', relatedId: 7 }

  it('manager opens Screen 12 with the review id', () => {
    expect(taskRoute('MANAGER', 'Probation review', 31, undefined, ref)).toBe('/team/E-0031/probation-review?reviewId=7')
  })

  it('HR opens Screen 13 for "Approve end of employment"', () => {
    expect(taskRoute('HR', 'Approve end of employment', 31, undefined, ref))
      .toBe('/employees/E-0031/probation-review/decision?reviewId=7')
  })

  it('HR opens checklist step for "Onboarding overdue"', () => {
    expect(taskRoute('HR', 'Onboarding overdue', 31)).toBe('/employees/31/onboarding?step=2')
  })

  it('employee opens the development plan for "Create development plan"', () => {
    expect(taskRoute('EMPLOYEE', 'Create development plan', 31)).toBe('/me/development/plan')
  })

  it('manager orientation items still open the orientation page', () => {
    expect(taskRoute('MANAGER', 'Welcome lunch', 31)).toBe('/manager/team/31/orientation')
  })
})

describe('validateProbationReview', () => {
  it('AC-P3: ratings but blank summary -> "Add a summary and choose an outcome."', () => {
    const errors = validateProbationReview(review, {
      1: { rating: 4, yesNo: null, textAnswer: '' },
      2: { rating: 4, yesNo: null, textAnswer: '' },
      3: { rating: null, yesNo: null, textAnswer: '   ' },
    }, 'CONFIRM')
    expect(errors).toEqual({ 'answers.3': MSG_SUMMARY_OUTCOME })
    expect(MSG_SUMMARY_OUTCOME).toBe('Add a summary and choose an outcome.')
  })

  it('missing outcome and rating', () => {
    const errors = validateProbationReview(review, {
      1: { rating: 4, yesNo: null, textAnswer: '' },
      3: { rating: null, yesNo: null, textAnswer: 'Good' },
    }, null)
    expect(errors).toEqual({ 'answers.2': MSG_REQUIRED, outcome: MSG_SUMMARY_OUTCOME })
    expect(MSG_REQUIRED).toBe('This field is required.')
  })

  it('complete review passes', () => {
    expect(validateProbationReview(review, {
      1: { rating: 1, yesNo: null, textAnswer: '' },
      2: { rating: 5, yesNo: null, textAnswer: '' },
      3: { rating: null, yesNo: null, textAnswer: 'Good' },
    }, 'END')).toEqual({})
  })
})
