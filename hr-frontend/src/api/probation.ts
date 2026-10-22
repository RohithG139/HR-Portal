/**
 * API module for the probation review (FRD-02 §4.7, P01–P04).
 * DTO shapes match hr-backend dto/probation/*.java exactly (R-CODE-3).
 */

import { apiGet, apiPost, apiPut } from './client'

/** One question of the probation form with its saved answer (ProbationQuestionDto). */
export interface ProbationQuestionDto {
  questionId: number
  section: string
  text: string
  description: string | null
  answerType: 'SCALE_1_5' | 'YES_NO' | 'TEXT'
  required: boolean
  rating: number | null
  yesNo: boolean | null
  textAnswer: string | null
}

export type ProbationOutcome = 'CONFIRM' | 'END'
export type HrDecision = 'APPROVED' | 'REJECTED'

/** P01 response (ProbationReviewDto). */
export interface ProbationReviewDto {
  id: number
  version: number
  employeeId: number
  employeeCode: string
  employeeName: string
  managerName: string
  status: 'DRAFT' | 'SUBMITTED'
  dueDate: string
  startDate: string
  probationEndDate: string
  /** Always null until FRD-03 (Projects) exists. */
  currentProject: string | null
  onboardingPercent: number
  buddyName: string | null
  formName: string
  scaleLabels: string[]
  questions: ProbationQuestionDto[]
  outcome: ProbationOutcome | null
  submittedAt: string | null
  hrDecision: HrDecision | null
  hrDecidedAt: string | null
  /** The caller (current manager) may save/submit. */
  editable: boolean
  /** An "End employment" outcome waits for HR (ONB-08). */
  awaitingHrDecision: boolean
  /** The caller is HR and may approve/reject now. */
  canDecide: boolean
}

/** One answer in the P02/P03 body. */
export interface ProbationAnswer {
  questionId: number
  rating?: number | null
  yesNo?: boolean | null
  textAnswer?: string | null
}

/** P02/P03 request body (SaveProbationReviewRequest). */
export interface SaveProbationReviewRequest {
  version: number
  answers: ProbationAnswer[]
  outcome: ProbationOutcome | null
}

/** 202 body of P03/P04. */
export interface UndoWindowResponse {
  undoUntil: string
  taskId: number
}

/** P01 — the review form. */
export function getProbationReview(reviewId: number): Promise<ProbationReviewDto> {
  return apiGet<ProbationReviewDto>(`/probation-reviews/${reviewId}`)
}

/** P02 — save draft. */
export function saveProbationReview(reviewId: number, body: SaveProbationReviewRequest): Promise<ProbationReviewDto> {
  return apiPut<ProbationReviewDto>(`/probation-reviews/${reviewId}`, body)
}

/** P03 — submit (undo window). */
export function submitProbationReview(reviewId: number, body: SaveProbationReviewRequest): Promise<UndoWindowResponse> {
  return apiPost<UndoWindowResponse>(`/probation-reviews/${reviewId}/submit`, body)
}

/** P04 — HR approves or rejects "End employment" (undo window). */
export function decideProbationReview(reviewId: number, decision: HrDecision): Promise<UndoWindowResponse> {
  return apiPost<UndoWindowResponse>(`/probation-reviews/${reviewId}/hr-decision`, { decision })
}
