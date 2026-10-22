/**
 * API module for onboarding endpoints (A06–A28).
 * DTO shapes match OnboardingService.java records exactly (R-SRC-2, R-CODE-3).
 */

import { apiGet, apiPost, apiPut, apiDelete, apiFetchBlob } from './client'

// ---------------------------------------------------------------------------
// DTOs (matching OnboardingService Java records)
// ---------------------------------------------------------------------------

/** Template preview section (A06). */
export interface SectionPreviewDto {
  section: string
  count: number
  items: string[]
}

/** Template preview (A06, OnboardingService.TemplatePreviewDto). */
export interface TemplatePreviewDto {
  totalItems: number
  sections: SectionPreviewDto[]
}

/** Onboarding summary returned by A11. */
export interface OnboardingSummaryDto {
  employeeId: number
  employeeName: string
  startDate: string
  endDate: string | null
  probationEndDate: string | null
  status: string
  itStatus: string
  itSentAt: string | null
  totalItems: number
  doneItems: number
  canComplete: boolean
  version: number | null
  /** Saved step-1 values (OnboardingDetailsDto); absent on older backends. */
  details?: OnboardingDetailsDto
  /** Saved step-2 values (OnboardingService.ItRequestDto); absent on older backends. */
  itRequest?: ItRequestValuesDto
}

/** Step-2 values of an onboarding, for re-showing the IT & equipment request form. */
export interface ItRequestValuesDto {
  laptopModel: string | null
  keyboardLayout: string | null
  /** ISO yyyy-MM-dd */
  readyBy: string | null
  deliveryMethod: string | null
  notes: string | null
  accessories: string[]
  accounts: string[]
}

/** Step-1 values of an onboarding, for re-showing the Employee details form. */
export interface OnboardingDetailsDto {
  firstName: string
  lastName: string
  privateEmail: string
  mobilePhone: string
  dateOfBirth: string | null
  nationality: string | null
  street: string
  postcode: string
  city: string
  contractType: string | null
  hoursPerWeek: number | null
  probationMonths: number | null
  idChecked: boolean
  contractSignedReceived: boolean
  rightToWorkVerified: boolean
  jobTitleId: number | null
  departmentId: number | null
  managerId: number | null
  grade: string | null
  appRole: string | null
  buddyId: number | null
  templateId: number | null
  /** Optimistic-lock version for A12 (update details). */
  employeeVersion: number | null
}

/** One checklist item (OnboardingService.ChecklistItemDto). */
export interface ChecklistItemDto {
  id: number
  name: string
  section: string
  ownerRole: string
  itemKind: string
  mandatory: boolean
  dueDate: string
  status: string
  doneAt: string | null
  doneBy: string | null
}

/** One section of the checklist (A15). */
export interface ChecklistSectionDto {
  section: string
  items: ChecklistItemDto[]
}

/** Full checklist response (A15). */
export interface ChecklistDto {
  progress: string
  progressPercent: number
  sections: ChecklistSectionDto[]
  canComplete: boolean
}

/** Tick/untick result (A17/A18). */
export interface TickResultDto {
  item: ChecklistItemDto
  allMandatoryDone: boolean
}

/** Undo window result (A19, A23, A24). */
export interface UndoWindowResult {
  /** ISO-8601 instant until which the completion can be undone (R-API-6). */
  undoUntil: string
  /** engine_task id to pass to A37 `POST /api/tasks/{taskId}/undo`. */
  taskId: number
}

/** IT send result (A14). */
export interface ItSendResultDto {
  status: string
  sentAt: string
}

/** Asset DTO (OnboardingService.AssetDto). */
export interface AssetDto {
  id: number
  assetNumber: string
  assetType: string
  model: string
  status: string
}

/** Equipment request view (A20). */
export interface EquipmentRequestDto {
  employeeName: string
  laptopModel: string | null
  keyboardLayout: string | null
  readyBy: string | null
  deliveryMethod: string | null
  notes: string | null
  itStatus: string
  issuedAssets: AssetDto[]
  accountItems: ChecklistItemDto[]
}

/** Employee's own onboarding view (A25). */
export interface EmployeeOnboardingDto {
  items: ChecklistItemDto[]
  myAssets: AssetDto[]
}

/** Step-1 form data for creating an onboarding (A10). */
export interface CreateOnboardingRequest {
  draftId?: number | null
  firstName: string
  lastName: string
  privateEmail: string
  mobilePhone: string
  dateOfBirth: string
  nationality?: string | null
  street: string
  postcode: string
  city: string
  contractType: string
  startDate: string
  endDate?: string | null
  hoursPerWeek: number
  probationMonths?: number | null
  idChecked: boolean
  contractSignedReceived: boolean
  rightToWorkVerified?: boolean | null
  contractDocumentId?: number | null
  jobTitleId: number
  departmentId: number
  managerId?: number | null
  grade: string
  appRole: string
  buddyId?: number | null
  templateId: number
}

/** Result of creating an onboarding (A10). */
export interface CreateOnboardingResult {
  employeeId: number
  employeeCode: string
}

/** Draft creation/update (A08/A09). */
export interface DraftRequest {
  firstName: string
  lastName: string
  form?: Record<string, unknown>
}

/** IT request form (A13/A14). */
export interface ItRequestPayload {
  laptopModel?: string | null
  keyboardLayout?: string | null
  readyBy?: string | null
  deliveryMethod?: string | null
  notes?: string | null
  accessories?: string[]
  accounts?: string[]
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A06 — Template preview. */
export function getTemplatePreview(templateId: number): Promise<TemplatePreviewDto> {
  return apiGet<TemplatePreviewDto>(`/checklist-templates/${templateId}/preview`)
}

/** A08 — Create draft. */
export function createDraft(request: DraftRequest): Promise<{ draftId: number }> {
  return apiPost<{ draftId: number }>('/onboarding-drafts', request)
}

/** A09 — Update draft. */
export function updateDraft(draftId: number, request: DraftRequest): Promise<void> {
  return apiPut<void>(`/onboarding-drafts/${draftId}`, request)
}

/** A saved draft (A09a, OnboardingService.DraftDto): `form` is what step 1 last saved. */
export interface DraftDto {
  id: number
  firstName: string
  lastName: string
  form: Record<string, unknown>
}

/** A09a — Read a draft to resume step 1 with every saved field (D-23). */
export function getDraft(draftId: number): Promise<DraftDto> {
  return apiGet<DraftDto>(`/onboarding-drafts/${draftId}`)
}

/** A10 — Create full onboarding (employee + contract + checklist). */
export function createOnboarding(request: CreateOnboardingRequest): Promise<CreateOnboardingResult> {
  return apiPost<CreateOnboardingResult>('/onboardings', request)
}

/** A11 — Get onboarding summary. */
export function getOnboarding(employeeId: number): Promise<OnboardingSummaryDto> {
  return apiGet<OnboardingSummaryDto>(`/onboardings/${employeeId}`)
}

/** A12 — Update onboarding details. */
export function updateOnboardingDetails(
  employeeId: number,
  request: Partial<CreateOnboardingRequest> & { version?: number | null },
): Promise<void> {
  return apiPut<void>(`/onboardings/${employeeId}/details`, request)
}

/** A13 — Save IT request without sending. */
export function saveItRequest(employeeId: number, request: ItRequestPayload): Promise<void> {
  return apiPut<void>(`/onboardings/${employeeId}/it-request`, request)
}

/** A14 — Send IT request. */
export function sendItRequest(employeeId: number, request: ItRequestPayload): Promise<ItSendResultDto> {
  return apiPost<ItSendResultDto>(`/onboardings/${employeeId}/it-request/send`, request)
}

/** A15 — Get checklist. */
export function getChecklist(
  employeeId: number,
  params?: { owner?: string; status?: string },
): Promise<ChecklistDto> {
  // Query-param names match OnboardingController.getChecklist (ownerFilter/statusFilter)
  const qs = new URLSearchParams()
  if (params?.owner !== undefined) qs.set('ownerFilter', params.owner)
  if (params?.status !== undefined) qs.set('statusFilter', params.status)
  const q = qs.toString()
  return apiGet<ChecklistDto>(`/onboardings/${employeeId}/checklist${q ? `?${q}` : ''}`)
}

/** A16 — Add checklist item. */
export function addChecklistItem(
  employeeId: number,
  request: { name: string; section: string; ownerRole: string; mandatory: boolean; dueOffsetDays: number },
): Promise<ChecklistItemDto> {
  return apiPost<ChecklistItemDto>(`/onboardings/${employeeId}/checklist-items`, request)
}

/** A17 — Tick checklist item. */
export function tickItem(itemId: number): Promise<TickResultDto> {
  return apiPost<TickResultDto>(`/checklist-items/${itemId}/done`, {})
}

/** A18 — Untick checklist item. */
export function untickItem(itemId: number): Promise<TickResultDto> {
  return apiDelete<TickResultDto>(`/checklist-items/${itemId}/done`)
}

/** A19 — Complete onboarding (returns undo window). */
export function completeOnboarding(employeeId: number): Promise<UndoWindowResult> {
  return apiPost<UndoWindowResult>(`/onboardings/${employeeId}/complete`, {})
}

/** A20 — Get IT equipment request view. */
export function getEquipmentRequest(employeeId: number): Promise<EquipmentRequestDto> {
  return apiGet<EquipmentRequestDto>(`/it/equipment-requests/${employeeId}`)
}

/** A23 — IT admin completes "Equipment request" task. */
export function completeEquipmentRequest(employeeId: number): Promise<UndoWindowResult> {
  return apiPost<UndoWindowResult>(`/onboardings/${employeeId}/it-request/complete`, {})
}

/** A24 — IT admin completes "Remaining setup" task. */
export function completeItSetup(employeeId: number): Promise<UndoWindowResult> {
  return apiPost<UndoWindowResult>(`/onboardings/${employeeId}/it-setup/complete`, {})
}

/** A25 — Employee's own onboarding data. */
export function getMyOnboarding(): Promise<EmployeeOnboardingDto> {
  return apiGet<EmployeeOnboardingDto>('/me/onboarding')
}

/**
 * A26 — Fetches the policy PDF for a checklist item.
 *
 * The backend (`OnboardingController.getPolicyDocument`) returns the PDF bytes
 * directly (Content-Type from the stored document), not a JSON reference — there
 * is no separate "fetch by storage key" endpoint. Callers turn the blob into an
 * object URL (`URL.createObjectURL`) to embed it, and must revoke that URL when
 * done to avoid leaking memory.
 */
export function getPolicyDocument(itemId: number): Promise<Blob> {
  return apiFetchBlob(`/checklist-items/${itemId}/policy`)
}

/** A27 — Acknowledge a policy checklist item. */
export function acknowledgePolicy(itemId: number, accepted: boolean): Promise<void> {
  return apiPost<void>(`/checklist-items/${itemId}/acknowledge`, { accepted })
}

/** A28 — Confirm equipment received. */
export function confirmEquipment(
  itemId: number,
  request: { receivedAssetIds: number[]; remarks?: string | null },
): Promise<void> {
  return apiPost<void>(`/checklist-items/${itemId}/confirm-equipment`, request)
}
