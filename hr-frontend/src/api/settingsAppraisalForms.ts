/**
 * API module for Settings — Appraisal forms (FRD-08 §6 C14–C24).
 * DTO shapes match the Java records in dto/settings/ exactly (R-CODE-3, R-SRC-2).
 */

import { apiGet, apiPost, apiPut, apiDelete } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** List-view row for an appraisal form (C14). */
export interface AppraisalFormListDto {
  id: number
  name: string
  usedFor: string
  status: string
  sectionCount: number
  updatedAt: string
}

/** One question within a form section (C16, C21, C22). */
export interface FormQuestionDto {
  id: number
  text: string
  description: string | null
  answerType: string
  required: boolean
  sortOrder: number
}

/** One section of an appraisal form (C16, C18, C19). */
export interface FormSectionDto {
  id: number
  name: string
  bySelf: boolean
  byPeer: boolean
  byManager: boolean
  byHr: boolean
  sortOrder: number
  questions: FormQuestionDto[]
}

/** Full detail of an appraisal form including sections and questions (C16). */
export interface AppraisalFormDetailDto {
  id: number
  name: string
  usedFor: string
  status: string
  overallScoreMethod: string
  label1: string | null
  label2: string | null
  label3: string | null
  label4: string | null
  label5: string | null
  version: number
  sections: FormSectionDto[]
}

/** Request body for creating a new appraisal form (C15). */
export interface AppraisalFormCreateRequest {
  name: string
  usedFor: string
}

/** Request body for updating a form's header fields (C17). */
export interface AppraisalFormUpdateRequest {
  name: string
  usedFor: string
  status: string
  overallScoreMethod: string
  label1: string | null
  label2: string | null
  label3: string | null
  label4: string | null
  label5: string | null
  version: number
}

/** Request body for creating or updating a form section (C18, C19). */
export interface FormSectionRequest {
  name: string
  bySelf: boolean
  byPeer: boolean
  byManager: boolean
  byHr: boolean
}

/** Request body for creating or updating a form question (C21, C22). */
export interface FormQuestionRequest {
  text: string
  description: string | null
  answerType: string
  required: boolean
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** C14 — List all appraisal forms (summary, for the S3 list view). */
export function listAppraisalForms(): Promise<AppraisalFormListDto[]> {
  return apiGet<AppraisalFormListDto[]>('/settings/appraisal-forms')
}

/** C15 — Create a new appraisal form (status defaults to DRAFT). Returns the new form's id. */
export function createAppraisalForm(body: AppraisalFormCreateRequest): Promise<{ id: number }> {
  return apiPost<{ id: number }>('/settings/appraisal-forms', body)
}

/** C16 — Get full detail of one appraisal form including all sections and questions. */
export function getAppraisalForm(id: number): Promise<AppraisalFormDetailDto> {
  return apiGet<AppraisalFormDetailDto>(`/settings/appraisal-forms/${id}`)
}

/** C17 — Update an appraisal form's header fields. Returns updated form detail. */
export function updateAppraisalForm(
  id: number,
  body: AppraisalFormUpdateRequest,
): Promise<AppraisalFormDetailDto> {
  return apiPut<AppraisalFormDetailDto>(`/settings/appraisal-forms/${id}`, body)
}

/** C18 — Add a new section to an appraisal form. */
export function addFormSection(
  formId: number,
  body: FormSectionRequest,
): Promise<FormSectionDto> {
  return apiPost<FormSectionDto>(`/settings/appraisal-forms/${formId}/sections`, body)
}

/** C19 — Update a section's name and "completed by" flags. */
export function updateFormSection(
  formId: number,
  sectionId: number,
  body: FormSectionRequest,
): Promise<FormSectionDto> {
  return apiPut<FormSectionDto>(
    `/settings/appraisal-forms/${formId}/sections/${sectionId}`,
    body,
  )
}

/** C20 — Delete a section (cascades its questions). */
export function deleteFormSection(formId: number, sectionId: number): Promise<void> {
  return apiDelete<void>(`/settings/appraisal-forms/${formId}/sections/${sectionId}`)
}

/** C21 — Add a question to a section. Note: path uses /form-sections/:id/ per FRD §4.4. */
export function addFormQuestion(
  sectionId: number,
  body: FormQuestionRequest,
): Promise<FormQuestionDto> {
  return apiPost<FormQuestionDto>(`/settings/form-sections/${sectionId}/questions`, body)
}

/** C22 — Update a question. */
export function updateFormQuestion(
  sectionId: number,
  questionId: number,
  body: FormQuestionRequest,
): Promise<FormQuestionDto> {
  return apiPut<FormQuestionDto>(
    `/settings/form-sections/${sectionId}/questions/${questionId}`,
    body,
  )
}

/** C23 — Delete a question. */
export function deleteFormQuestion(sectionId: number, questionId: number): Promise<void> {
  return apiDelete<void>(`/settings/form-sections/${sectionId}/questions/${questionId}`)
}

/** C24 — Archive an appraisal form (ADM-04). Sets status to ARCHIVED. */
export function archiveAppraisalForm(id: number): Promise<void> {
  return apiPost<void>(`/settings/appraisal-forms/${id}/archive`, {})
}
