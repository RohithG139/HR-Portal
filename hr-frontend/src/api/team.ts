/**
 * API module for the Manager's My Team endpoints (W05/W06, FRD-01 Wave 3).
 * DTO shapes match TeamMemberDto.java and TeamPageDto.java (R-SRC-2, R-CODE-3).
 */

import { apiGet, apiDownload } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/**
 * A single row in the My Team list (W05 / TeamMemberDto.java).
 * Deferred fields (currentProjects, allocation, development) are absent and
 * will be added by F04/F05 (additive DTO contract, spec §7 D-W2).
 */
export interface TeamMemberDto {
  employeeId: number
  employeeCode: string
  firstName: string
  lastName: string
  /** Job title name from job_title.name; null if no title set. */
  jobTitle: string | null
  /** Lifecycle stage code, e.g. "ACTIVE", "ONBOARDING" (R-UI-5). */
  stage: string
  /** Current project names, comma-separated; null when on no project. */
  projects: string | null
  /** Sum of current project allocations, 0-100+. */
  allocationPct: number
  /** ONBOARDING | PIP | OFFBOARDING | ON_PROJECT | BENCH */
  teamStatus: string
}

/**
 * Paginated team list response (W05 / TeamPageDto.java).
 */
export interface TeamPageDto {
  items: TeamMemberDto[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

// ---------------------------------------------------------------------------
// Query params
// ---------------------------------------------------------------------------

export interface TeamListParams {
  /** Status filter: All / Onboarding / On project / Bench / PIP */
  status?: string
  /** Free-text search on employee name. */
  q?: string
  /** Sort column (backend default: lastName). */
  sort?: string
  /** Zero-based page index. */
  page?: number
  /** Page size. */
  size?: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/**
 * W05 — Paginated list of the manager's direct reports.
 * WS-02 scoping is enforced server-side; the caller gets only their own team.
 */
export function listTeam(params: TeamListParams = {}): Promise<TeamPageDto> {
  const qs = new URLSearchParams()
  if (params.status !== undefined) qs.set('status', params.status)
  if (params.q !== undefined) qs.set('q', params.q)
  if (params.sort !== undefined) qs.set('sort', params.sort)
  if (params.page !== undefined) qs.set('page', String(params.page))
  if (params.size !== undefined) qs.set('size', String(params.size))
  const query = qs.toString()
  return apiGet<TeamPageDto>(`/team${query ? `?${query}` : ''}`)
}

/**
 * W06 — Downloads the team list as Excel (R-UI-2).
 * Applies the same status/q filters as W05.
 */
export function exportTeam(params: Pick<TeamListParams, 'status' | 'q'> = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.status !== undefined) qs.set('status', params.status)
  if (params.q !== undefined) qs.set('q', params.q)
  const query = qs.toString()
  return apiDownload(`/team/export${query ? `?${query}` : ''}`, 'team.xlsx')
}
