/**
 * API module for employee endpoints (A04, A05, A31, A32, A33, A34, A35).
 * DTO shapes match EmployeeService.java records exactly (R-SRC-2, R-CODE-3).
 */

import { apiGet, apiPut, apiPost, apiDownload } from './client'

// ---------------------------------------------------------------------------
// DTOs (matching EmployeeService Java records)
// ---------------------------------------------------------------------------

/** A single row in the Employees list (A04, EmployeeService.EmployeeListItemDto). */
export interface EmployeeListItemDto {
  id: number | null
  draftId: number | null
  firstName: string
  lastName: string
  jobTitle: string | null
  department: string | null
  managerName: string | null
  stage: string
  employeeCode: string | null
  accountStatus: string | null
  draft: boolean
  /** Work e-mail, else private e-mail; null for draft rows. */
  email: string | null
  /**
   * Count of assets currently assigned to this employee.
   * Populated for IT_ADMIN callers (D-W9); null for HR callers (not needed in the HR list view).
   */
  assetCount: number | null
}

/** Paginated employee list (A04). */
export interface EmployeePageDto {
  items: EmployeeListItemDto[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

/** Overview tab data (A31, EmployeeService.EmployeeOverviewDto). */
export interface EmployeeOverviewDto {
  id: number
  employeeCode: string
  firstName: string
  lastName: string
  privateEmail: string
  workEmail: string | null
  mobilePhone: string
  dateOfBirth: string | null
  nationality: string | null
  street: string
  postcode: string
  city: string
  jobTitle: string | null
  department: string | null
  grade: string | null
  managerName: string | null
  stage: string | null
  accountStatus: string | null
}

/** Profile tab data (A32, EmployeeService.EmployeeProfileDto). */
export interface EmployeeProfileDto {
  id: number
  firstName: string
  lastName: string
  privateEmail: string
  workEmail: string | null
  mobilePhone: string
  dateOfBirth: string | null
  nationality: string | null
  street: string
  postcode: string
  city: string
  emergencyName: string | null
  emergencyRelationship: string | null
  emergencyPhone: string | null
  jobTitle: string | null
  department: string | null
  grade: string | null
  appRole: string | null
  accountStatus: string | null
  loginEmail: string | null
  version: number | null
}

/** One audit/history entry (A35). */
export interface HistoryEntryDto {
  id: number
  entityType: string
  entityId: number
  action: string
  actorName: string
  before: string | null
  after: string | null
  createdAt: string
}

/** Paginated history response (A35). */
export interface HistoryPageDto {
  content: HistoryEntryDto[]
  totalElements: number
  totalPages: number
  page: number
}

// ---------------------------------------------------------------------------
// Query params
// ---------------------------------------------------------------------------

export interface EmployeeListParams {
  stage?: string
  departmentId?: number
  managerId?: number
  q?: string
  sort?: string
  page?: number
  size?: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A04 — Paginated employee list with filters. */
export function listEmployees(params: EmployeeListParams = {}): Promise<EmployeePageDto> {
  const qs = new URLSearchParams()
  if (params.stage !== undefined) qs.set('stage', params.stage)
  if (params.departmentId !== undefined) qs.set('departmentId', String(params.departmentId))
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  if (params.q !== undefined) qs.set('q', params.q)
  if (params.sort !== undefined) qs.set('sort', params.sort)
  if (params.page !== undefined) qs.set('page', String(params.page))
  if (params.size !== undefined) qs.set('size', String(params.size))
  const query = qs.toString()
  return apiGet<EmployeePageDto>(`/employees${query ? `?${query}` : ''}`)
}

/** A05 — Download employee list as Excel. Triggers browser download. */
export function exportEmployees(params: EmployeeListParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.stage !== undefined) qs.set('stage', params.stage)
  if (params.departmentId !== undefined) qs.set('departmentId', String(params.departmentId))
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  if (params.q !== undefined) qs.set('q', params.q)
  const query = qs.toString()
  return apiDownload(`/employees/export${query ? `?${query}` : ''}`, 'employees.xlsx')
}

/** A31 — Employee overview tab. */
export function getEmployeeOverview(employeeId: number): Promise<EmployeeOverviewDto> {
  return apiGet<EmployeeOverviewDto>(`/employees/${employeeId}`)
}

/** A32 — Employee profile tab (HR/Manager). */
export function getEmployeeProfile(employeeId: number): Promise<EmployeeProfileDto> {
  return apiGet<EmployeeProfileDto>(`/employees/${employeeId}/profile`)
}

/** A33 — HR updates employee profile. */
export function updateEmployeeProfile(
  employeeId: number,
  request: { firstName?: string; lastName?: string; appRole?: string },
): Promise<EmployeeProfileDto> {
  return apiPut<EmployeeProfileDto>(`/employees/${employeeId}/profile`, request)
}

/** A34 — HR resends the set-password email. */
export function resendInvite(employeeId: number): Promise<void> {
  return apiPost<void>(`/employees/${employeeId}/account/resend-invite`, {})
}

/** A35 — HR views employee history. */
export function getEmployeeHistory(employeeId: number, page = 0): Promise<HistoryPageDto> {
  return apiGet<HistoryPageDto>(`/employees/${employeeId}/history?page=${page}`)
}
