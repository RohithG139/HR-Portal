/**
 * @component ProbationReviewPage
 * @description FRD-02 Screen 12 "Probation review" (Manager, `/team/:code/probation-review`)
 * and Screen 13 "Approve end of employment" (HR, `/employees/:code/probation-review/decision`).
 * Screen 13 is the same page, read-only, with an HR action bar (spec D-P4, user decision
 * 2026-10-21). The questions come from the active PROBATION appraisal form (P01), not a fixed
 * layout. Submit and the HR decision go through the undo window (R-API-6).
 * @props mode - 'manager' | 'hr' - which route rendered the page
 * @example
 * <ProbationReviewPage mode="manager" />   // route /team/E-0031/probation-review?reviewId=7
 */

import { useEffect, useState } from 'react'
import type { ReactElement } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  decideProbationReview,
  getProbationReview,
  saveProbationReview,
  submitProbationReview,
} from '../../api/probation'
import type {
  HrDecision,
  ProbationOutcome,
  ProbationReviewDto,
  SaveProbationReviewRequest,
  UndoWindowResponse,
} from '../../api/probation'
import { ApiResponseError } from '../../api/types'
import { UndoBanner, useToast } from '../../components/index'
import { formatDate } from '../../lib/date'

/** FRD-02 §4 validation table (R-SRC-3, exact). */
export const MSG_SUMMARY_OUTCOME = 'Add a summary and choose an outcome.'
export const MSG_REQUIRED = 'This field is required.'

type Answers = Record<number, { rating: number | null; yesNo: boolean | null; textAnswer: string }>

const greenButton =
  'px-4 py-2 text-sm font-medium rounded-md bg-green-600 hover:bg-green-700 text-white min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed'
const greenOutlineButton =
  'px-4 py-2 text-sm font-medium rounded-md border border-green-600 text-green-700 hover:bg-green-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed'
const redOutlineButton =
  'px-4 py-2 text-sm font-medium rounded-md border border-red-600 text-red-700 hover:bg-red-50 min-h-[44px] focus-visible:ring-2 focus-visible:ring-red-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed'

const OUTCOME_LABEL: Record<ProbationOutcome, string> = {
  CONFIRM: 'Confirm employment',
  END: 'End employment',
}

function initialAnswers(review: ProbationReviewDto): Answers {
  const a: Answers = {}
  for (const q of review.questions) {
    a[q.questionId] = { rating: q.rating, yesNo: q.yesNo, textAnswer: q.textAnswer ?? '' }
  }
  return a
}

/**
 * Client-side mirror of the P03 checks (R-CODE-2: same texts as the API).
 * @returns field → message; empty when the review can be submitted
 */
export function validateProbationReview(
  review: Pick<ProbationReviewDto, 'questions'>,
  answers: Answers,
  outcome: ProbationOutcome | null,
): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const q of review.questions) {
    if (!q.required) continue
    const a = answers[q.questionId]
    const answered =
      a !== undefined &&
      (q.answerType === 'SCALE_1_5' ? a.rating !== null
        : q.answerType === 'YES_NO' ? a.yesNo !== null
        : a.textAnswer.trim() !== '')
    if (!answered) {
      errors[`answers.${q.questionId}`] = q.answerType === 'TEXT' ? MSG_SUMMARY_OUTCOME : MSG_REQUIRED
    }
  }
  if (outcome === null) errors.outcome = MSG_SUMMARY_OUTCOME
  return errors
}

/** Probation review page — manager form (Screen 12) or HR decision (Screen 13). */
export function ProbationReviewPage({ mode }: { mode: 'manager' | 'hr' }): ReactElement {
  const [params] = useSearchParams()
  const reviewId = Number(params.get('reviewId') ?? '0')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  const [answers, setAnswers] = useState<Answers>({})
  const [outcome, setOutcome] = useState<ProbationOutcome | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState<{ undo: UndoWindowResponse; message: string } | null>(null)

  const { data: review, isLoading, isError } = useQuery({
    queryKey: ['probation-review', reviewId],
    queryFn: () => getProbationReview(reviewId),
    enabled: reviewId > 0,
  })

  useEffect(() => {
    if (review !== undefined) {
      setAnswers(initialAnswers(review))
      setOutcome(review.outcome)
    }
  }, [review])

  const refresh = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['probation-review', reviewId] })
  }

  const handleError = (err: unknown): void => {
    if (err instanceof ApiResponseError && err.body.code === 'VALIDATION_FAILED' && 'errors' in err.body) {
      const fieldErrors: Record<string, string> = {}
      for (const e of err.body.errors) fieldErrors[e.field] = e.message
      setErrors(fieldErrors)
      return
    }
    if (err instanceof ApiResponseError && 'message' in err.body) {
      showToast(err.body.message, 'error')
      return
    }
    showToast('Something went wrong. Please try again.', 'error')
  }

  const buildBody = (r: ProbationReviewDto): SaveProbationReviewRequest => ({
    version: r.version,
    outcome,
    answers: r.questions.map((q) => {
      const a = answers[q.questionId]
      return {
        questionId: q.questionId,
        rating: q.answerType === 'SCALE_1_5' ? a?.rating ?? null : null,
        yesNo: q.answerType === 'YES_NO' ? a?.yesNo ?? null : null,
        textAnswer: q.answerType === 'TEXT' ? a?.textAnswer ?? '' : null,
      }
    }),
  })

  const saveMutation = useMutation({
    mutationFn: (r: ProbationReviewDto) => saveProbationReview(r.id, buildBody(r)),
    onSuccess: (saved) => {
      setErrors({})
      queryClient.setQueryData(['probation-review', reviewId], saved)
      showToast('Draft saved.')
    },
    onError: handleError,
  })

  const submitMutation = useMutation({
    mutationFn: (r: ProbationReviewDto) => submitProbationReview(r.id, buildBody(r)),
    onSuccess: (undo) => {
      setErrors({})
      setPending({ undo, message: 'Probation review submitted.' })
      refresh()
    },
    onError: handleError,
  })

  const decideMutation = useMutation({
    mutationFn: ({ id, decision }: { id: number; decision: HrDecision }) => decideProbationReview(id, decision),
    onSuccess: (undo, { decision }) => {
      setPending({
        undo,
        message: decision === 'APPROVED' ? 'End of employment approved.' : 'End of employment rejected.',
      })
      refresh()
    },
    onError: handleError,
  })

  if (reviewId <= 0 || isError) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-4">
        <Link to="/my-tasks" className="text-sm text-blue-600 hover:underline">← Back to My Tasks</Link>
        <p className="text-sm text-gray-600">This probation review could not be loaded.</p>
      </div>
    )
  }

  if (isLoading || review === undefined) {
    return (
      <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-pulse space-y-4">
        <div className="h-8 bg-gray-200 rounded w-72" />
        <div className="h-64 bg-gray-200 rounded" />
      </div>
    )
  }

  const editable = mode === 'manager' && review.editable && pending === null
  const busy = saveMutation.isPending || submitMutation.isPending || decideMutation.isPending

  const onSubmit = (): void => {
    const clientErrors = validateProbationReview(review, answers, outcome)
    setErrors(clientErrors)
    if (Object.keys(clientErrors).length === 0) submitMutation.mutate(review)
  }

  const setAnswer = (questionId: number, patch: Partial<Answers[number]>): void => {
    setAnswers((prev) => ({
      ...prev,
      [questionId]: { rating: null, yesNo: null, textAnswer: '', ...prev[questionId], ...patch },
    }))
  }

  const scaleQuestions = review.questions.filter((q) => q.answerType !== 'TEXT')
  const textQuestions = review.questions.filter((q) => q.answerType === 'TEXT')
  const title = mode === 'hr' ? 'Approve end of employment' : 'Probation review'

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <Link to="/my-tasks" className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded">
        ← Back to My Tasks
      </Link>

      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-2">
        <h1 className="text-xl sm:text-2xl font-bold text-gray-900">
          {title} · {review.employeeName}
        </h1>
        <p className="text-sm text-gray-500">
          Started {formatDate(review.startDate)} · probation ends {formatDate(review.probationEndDate)}
        </p>
      </div>

      {pending !== null && (
        <UndoBanner
          taskId={pending.undo.taskId}
          undoUntil={pending.undo.undoUntil}
          message={pending.message}
          onUndone={() => { setPending(null); refresh() }}
          onExpired={() => navigate('/my-tasks')}
        />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <section className="lg:col-span-2 bg-white border border-gray-200 rounded-lg p-4 sm:p-6 space-y-6" aria-labelledby="pr-review">
          <h2 id="pr-review" className="text-base sm:text-lg font-semibold text-gray-900 pb-3 border-b border-gray-200">Review</h2>

          {scaleQuestions.map((q) => {
            const a = answers[q.questionId]
            const err = errors[`answers.${q.questionId}`]
            return (
              <fieldset key={q.questionId} className="space-y-2">
                <legend className="text-sm font-medium text-gray-900">
                  {q.text}{q.required && <span className="text-red-600"> *</span>}
                </legend>
                {q.description !== null && <p className="text-xs text-gray-500">{q.description}</p>}
                {q.answerType === 'SCALE_1_5' ? (
                  <>
                    <div className="flex gap-2" role="radiogroup" aria-label={q.text}>
                      {[1, 2, 3, 4, 5].map((n) => {
                        const selected = a?.rating === n
                        return (
                          <button
                            key={n}
                            type="button"
                            role="radio"
                            aria-checked={selected}
                            aria-label={`${q.text}: ${n} — ${review.scaleLabels[n - 1] ?? ''}`}
                            disabled={!editable}
                            onClick={() => setAnswer(q.questionId, { rating: n })}
                            className={`w-11 h-11 rounded-md border text-sm font-medium focus-visible:ring-2 focus-visible:ring-blue-500 ${
                              selected ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-gray-300 text-gray-700 hover:bg-gray-50'
                            } disabled:cursor-default`}
                          >
                            {n}
                          </button>
                        )
                      })}
                    </div>
                    <div className="flex justify-between max-w-[15rem] text-xs text-gray-500">
                      <span>{review.scaleLabels[1]}</span>
                      <span>{review.scaleLabels[2]}</span>
                      <span>{review.scaleLabels[3]}</span>
                    </div>
                  </>
                ) : (
                  <div className="flex gap-2" role="radiogroup" aria-label={q.text}>
                    {[true, false].map((v) => (
                      <button
                        key={String(v)}
                        type="button"
                        role="radio"
                        aria-checked={a?.yesNo === v}
                        disabled={!editable}
                        onClick={() => setAnswer(q.questionId, { yesNo: v })}
                        className={`px-4 h-11 rounded-md border text-sm font-medium ${
                          a?.yesNo === v ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-gray-300 text-gray-700'
                        }`}
                      >
                        {v ? 'Yes' : 'No'}
                      </button>
                    ))}
                  </div>
                )}
                {err !== undefined && <p className="text-xs text-red-600" role="alert">{err}</p>}
              </fieldset>
            )
          })}

          {textQuestions.map((q) => {
            const err = errors[`answers.${q.questionId}`]
            const id = `pr-text-${q.questionId}`
            return (
              <div key={q.questionId} className="space-y-1">
                <label htmlFor={id} className="text-sm font-medium text-gray-900">
                  {q.text}{q.required && <span className="text-red-600">*</span>}
                </label>
                <textarea
                  id={id}
                  rows={4}
                  value={answers[q.questionId]?.textAnswer ?? ''}
                  readOnly={!editable}
                  onChange={(e) => setAnswer(q.questionId, { textAnswer: e.target.value })}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-blue-500 read-only:bg-gray-50"
                />
                {err !== undefined && <p className="text-xs text-red-600" role="alert">{err}</p>}
              </div>
            )
          })}

          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-gray-900">Outcome</legend>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2" role="radiogroup" aria-label="Outcome">
              {(['CONFIRM', 'END'] as const).map((o) => {
                const selected = outcome === o
                const colour = o === 'CONFIRM'
                  ? (selected ? 'bg-green-600 border-green-600 text-white' : 'border-green-600 text-green-700 bg-white')
                  : (selected ? 'bg-red-600 border-red-600 text-white' : 'border-red-600 text-red-700 bg-white')
                return (
                  <button
                    key={o}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    disabled={!editable}
                    onClick={() => setOutcome(o)}
                    className={`rounded-md border px-4 py-2 text-left min-h-[44px] focus-visible:ring-2 focus-visible:ring-blue-500 ${colour}`}
                  >
                    <span className="block text-sm font-medium">{OUTCOME_LABEL[o]}</span>
                    <span className="block text-xs opacity-80">
                      {o === 'CONFIRM' ? 'Probation passed' : 'HR approval, then offboarding'}
                    </span>
                  </button>
                )
              })}
            </div>
            {errors.outcome !== undefined && <p className="text-xs text-red-600" role="alert">{errors.outcome}</p>}
          </fieldset>

          {review.status === 'SUBMITTED' && (
            <p className="text-sm text-gray-600">
              Submitted by {review.managerName}
              {review.submittedAt !== null && ` on ${formatDate(review.submittedAt.slice(0, 10))}`}
              {review.hrDecision !== null && ` · HR decision: ${review.hrDecision === 'APPROVED' ? 'Approved' : 'Rejected'}`}
            </p>
          )}
        </section>

        <aside className="bg-white border border-gray-200 rounded-lg p-4 sm:p-6 h-fit" aria-labelledby="pr-context">
          <h2 id="pr-context" className="text-base sm:text-lg font-semibold text-gray-900 pb-3 mb-3 border-b border-gray-200">Context</h2>
          <dl className="grid grid-cols-2 gap-y-2 text-sm">
            <dt className="text-gray-500">Started</dt><dd className="text-gray-900">{formatDate(review.startDate)}</dd>
            <dt className="text-gray-500">Project</dt><dd className="text-gray-900">{review.currentProject ?? '—'}</dd>
            <dt className="text-gray-500">Onboarding</dt><dd className="text-gray-900">{review.onboardingPercent}%</dd>
            <dt className="text-gray-500">Buddy</dt><dd className="text-gray-900">{review.buddyName ?? '—'}</dd>
          </dl>
        </aside>
      </div>

      {editable && (
        <div className="flex flex-col-reverse sm:flex-row sm:justify-between gap-3">
          <button type="button" className={redOutlineButton} onClick={() => navigate('/my-tasks')} disabled={busy}>
            Cancel
          </button>
          <div className="flex flex-col sm:flex-row gap-3">
            <button type="button" className={greenOutlineButton} onClick={() => saveMutation.mutate(review)} disabled={busy}>
              Save draft
            </button>
            <button type="button" className={greenButton} onClick={onSubmit} disabled={busy}>
              {submitMutation.isPending ? 'Submitting…' : 'Submit'}
            </button>
          </div>
        </div>
      )}

      {mode === 'hr' && review.canDecide && pending === null && (
        <div className="flex flex-col-reverse sm:flex-row sm:justify-between gap-3 border-t border-gray-200 pt-4" aria-label="HR decision">
          <button
            type="button"
            className={redOutlineButton}
            disabled={busy}
            onClick={() => decideMutation.mutate({ id: review.id, decision: 'REJECTED' })}
          >
            Reject
          </button>
          <button
            type="button"
            className={greenButton}
            disabled={busy}
            onClick={() => decideMutation.mutate({ id: review.id, decision: 'APPROVED' })}
          >
            Approve
          </button>
        </div>
      )}
    </div>
  )
}
