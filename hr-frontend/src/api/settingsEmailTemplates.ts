/**
 * API module for Settings — E-mail templates (FRD-08 §6 C25–C28).
 * DTO shapes match the Java records in dto/settings/ exactly (R-CODE-3, R-SRC-2).
 */

import { apiGet, apiPost, apiPut } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** List-view row for an e-mail template (C25). `code` is the stable URL key (ADM-01). */
export interface EmailTemplateListDto {
  code: string
  name: string
  triggerDescription: string
  recipientsSummary: string
  active: boolean
}

/** Full detail of an e-mail template (C26). `code` is never editable (ADM-01). */
export interface EmailTemplateDetailDto {
  code: string
  name: string
  triggerDescription: string
  subject: string
  body: string
  cc: string | null
  toEmployee: boolean
  toManager: boolean
  toHr: boolean
  toItAdmin: boolean
  toTaskOwner: boolean
  toPeer: boolean
  active: boolean
  /** Optimistic-lock version — must be sent back in update requests (R-DB-5). */
  version: number
}

/** Request body for updating an e-mail template (C27). */
export interface EmailTemplateUpdateRequest {
  name: string
  triggerDescription: string
  subject: string
  body: string
  cc: string | null
  toEmployee: boolean
  toManager: boolean
  toHr: boolean
  toItAdmin: boolean
  toTaskOwner: boolean
  toPeer: boolean
  active: boolean
  version: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** C25 — List all 21 e-mail templates (summary view). */
export function listEmailTemplates(): Promise<EmailTemplateListDto[]> {
  return apiGet<EmailTemplateListDto[]>('/settings/email-templates')
}

/** C26 — Get full detail of one e-mail template by code. */
export function getEmailTemplate(code: string): Promise<EmailTemplateDetailDto> {
  return apiGet<EmailTemplateDetailDto>(`/settings/email-templates/${encodeURIComponent(code)}`)
}

/** C27 — Update an e-mail template's editable fields. Returns updated detail. */
export function updateEmailTemplate(
  code: string,
  body: EmailTemplateUpdateRequest,
): Promise<EmailTemplateDetailDto> {
  return apiPut<EmailTemplateDetailDto>(`/settings/email-templates/${encodeURIComponent(code)}`, body)
}

/**
 * C28 — Send a test e-mail of the template to the requesting HR user's e-mail address.
 * Renders current saved values with sample placeholder data; writes to email_outbox (R-ARC-6).
 * Returns 202 Accepted (queued, not synchronous).
 */
export function testSendEmailTemplate(code: string): Promise<void> {
  return apiPost<void>(`/settings/email-templates/${encodeURIComponent(code)}/test`, {})
}
