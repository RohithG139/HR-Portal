/**
 * API module for FRD-08 Reports endpoints (C31–C47).
 *
 * All report endpoints are HR-only (FRD-08 §2, AC-17).
 * DTO field names match the Java records in `dto/reports/` exactly (R-SRC-2, R-CODE-3).
 * Dates arrive as ISO `yyyy-MM-dd` strings from the backend (R-API-1) and are
 * formatted to `dd-mm-yyyy` at render time using `lib/date.ts` (R-UI-5).
 */

import { apiGet, apiDownload } from './client'

// ---------------------------------------------------------------------------
// DTOs — matching Java record field names (camelCase, R-API-1)
// ---------------------------------------------------------------------------

/** C31 — Four KPI numbers shown on the reports overview (S7). */
export interface SummaryDto {
  totalHeadcount: number
  benchCount: number
  /** Percentage 0–100; 0.0 when no round is open */
  appraisalCompletionRate: number
  unreturnedAssetCount: number
}

/** One row in the Headcount report (S8 / C32). */
export interface HeadcountReportRow {
  departmentId: number
  departmentName: string
  employeeCount: number
  joinersThisYear: number
  leaversThisYear: number
  permanentCount: number
  fixedTermCount: number
}

/** One row in the Bench report (S9 / C34). */
export interface BenchReportRow {
  employeeId: number
  employeeName: string
  jobTitle: string
  managerId: number | null
  managerName: string | null
  /** ISO date string `yyyy-MM-dd`, or null if uncomputable */
  benchSince: string | null
  daysOnBench: number | null
  lastProjectName: string | null
}

/** One row in the Project Allocation report (S10 / C36). */
export interface AllocationReportRow {
  employeeId: number
  employeeName: string
  jobTitle: string
  totalAllocationPct: number
  /** ON_BENCH | PARTLY_ALLOCATED | FULLY_ALLOCATED | OVER_ALLOCATED */
  allocationStatus: string
  /** Comma-separated project names; null when on bench */
  projects: string | null
}

/** One row in the Appraisal Status report (S11 / C38). */
export interface AppraisalStatusReportRow {
  appraisalId: number
  employeeId: number
  employeeName: string
  managerId: number | null
  managerName: string | null
  /** e.g. DRAFT | SUBMITTED; null if not requested */
  selfStatus: string | null
  peersSubmitted: number
  peersTotal: number
  managerReviewStatus: string | null
  /** PROMOTE | MEETS | BELOW; null if not submitted */
  managerReviewOutcome: string | null
  /** Average rating; null if no client feedback */
  clientFeedbackAvg: number | null
  clientFeedbackCount: number
  /** PROMOTED | NO_CHANGE | UNDER_PERFORMING; null if open */
  result: string | null
  /** OPEN | FINALISED | CANCELLED */
  appraisalStatus: string | null
}

/** One row in the PIPs report (S12 / C40). */
export interface PipReportRow {
  pipId: number
  employeeId: number
  employeeName: string
  managerId: number | null
  managerName: string | null
  status: string | null
  /** ISO date `yyyy-MM-dd`; null if not started */
  startDate: string | null
  /** ISO date `yyyy-MM-dd`; null when startDate is null */
  finalReviewDate: string | null
  /** Days until final review for ACTIVE PIPs; null otherwise */
  daysLeft: number | null
  extensionCount: number
}

/** One row in the Onboarding report (S13 / C42). */
export interface OnboardingReportRow {
  employeeId: number
  employeeName: string
  /** ISO date `yyyy-MM-dd`; null if no contract */
  startDate: string | null
  templateName: string
  /** Percentage 0–100 */
  checklistPct: number
  /** NOT_SENT | SENT | IN_PROGRESS | COMPLETED */
  itStatus: string | null
  openItems: number
}

/** One row in the Contracts & Probation report (S14 / C44). */
export interface ContractsProbationReportRow {
  employeeId: number
  employeeName: string
  /** PROBATION | CONTRACT | PIP */
  eventType: string
  /** ISO date `yyyy-MM-dd` */
  eventDate: string | null
  daysUntil: number
  /** MANAGER | HR | MANAGER_HR */
  ownerRole: string | null
  /** UPCOMING for date events; PIP status for PIP events */
  eventStatus: string | null
}

/** One row in the Unreturned Assets report (S15 / C46). */
export interface UnreturnedAssetsReportRow {
  assetId: number
  assetNumber: string | null
  assetDescription: string | null
  employeeId: number | null
  employeeName: string | null
  /** ISO date `yyyy-MM-dd` */
  lastWorkingDay: string | null
  /** RETURN_DUE | NOT_RETURNED */
  assetStatus: string | null
}

// ---------------------------------------------------------------------------
// Query-param shapes
// ---------------------------------------------------------------------------

export interface HeadcountParams {
  departmentId?: number
}

export interface BenchParams {
  managerId?: number
}

export interface AllocationParams {
  status?: string
}

export interface AppraisalStatusParams {
  managerId?: number
  result?: string
}

export interface PipsParams {
  status?: string
}

export interface ContractsProbationParams {
  type?: string
}

export interface UnreturnedAssetsParams {
  status?: string
}

// ---------------------------------------------------------------------------
// API functions — data fetching (C31–C47)
// ---------------------------------------------------------------------------

/** C31 — Summary KPI tiles for the reports overview (S7). */
export function getSummary(): Promise<SummaryDto> {
  return apiGet<SummaryDto>('/reports/summary')
}

/** C32 — Headcount report rows, optionally filtered by department. */
export function getHeadcount(params: HeadcountParams = {}): Promise<HeadcountReportRow[]> {
  const qs = new URLSearchParams()
  if (params.departmentId !== undefined) qs.set('departmentId', String(params.departmentId))
  const q = qs.toString()
  return apiGet<HeadcountReportRow[]>(`/reports/headcount${q ? `?${q}` : ''}`)
}

/** C33 — Headcount Excel export; triggers browser download. */
export function exportHeadcount(params: HeadcountParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.departmentId !== undefined) qs.set('departmentId', String(params.departmentId))
  const q = qs.toString()
  return apiDownload(`/reports/headcount/export${q ? `?${q}` : ''}`, 'headcount.xlsx')
}

/** C34 — Bench report rows, optionally filtered by manager. */
export function getBench(params: BenchParams = {}): Promise<BenchReportRow[]> {
  const qs = new URLSearchParams()
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  const q = qs.toString()
  return apiGet<BenchReportRow[]>(`/reports/bench${q ? `?${q}` : ''}`)
}

/** C35 — Bench Excel export; triggers browser download. */
export function exportBench(params: BenchParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  const q = qs.toString()
  return apiDownload(`/reports/bench/export${q ? `?${q}` : ''}`, 'bench.xlsx')
}

/** C36 — Project Allocation report rows, optionally filtered by status. */
export function getAllocation(params: AllocationParams = {}): Promise<AllocationReportRow[]> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiGet<AllocationReportRow[]>(`/reports/allocation${q ? `?${q}` : ''}`)
}

/** C37 — Allocation Excel export; triggers browser download. */
export function exportAllocation(params: AllocationParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiDownload(`/reports/allocation/export${q ? `?${q}` : ''}`, 'allocation.xlsx')
}

/** C38 — Appraisal Status report rows, with optional manager and result filters. */
export function getAppraisalStatus(params: AppraisalStatusParams = {}): Promise<AppraisalStatusReportRow[]> {
  const qs = new URLSearchParams()
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  if (params.result !== undefined && params.result !== '') qs.set('result', params.result)
  const q = qs.toString()
  return apiGet<AppraisalStatusReportRow[]>(`/reports/appraisal-status${q ? `?${q}` : ''}`)
}

/** C39 — Appraisal Status Excel export; triggers browser download. */
export function exportAppraisalStatus(params: AppraisalStatusParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.managerId !== undefined) qs.set('managerId', String(params.managerId))
  if (params.result !== undefined && params.result !== '') qs.set('result', params.result)
  const q = qs.toString()
  return apiDownload(`/reports/appraisal-status/export${q ? `?${q}` : ''}`, 'appraisal-status.xlsx')
}

/** C40 — PIPs report rows, optionally filtered by status. */
export function getPips(params: PipsParams = {}): Promise<PipReportRow[]> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiGet<PipReportRow[]>(`/reports/pips${q ? `?${q}` : ''}`)
}

/** C41 — PIPs Excel export; triggers browser download. */
export function exportPips(params: PipsParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiDownload(`/reports/pips/export${q ? `?${q}` : ''}`, 'pips.xlsx')
}

/** C42 — Onboarding report rows (no filters). */
export function getOnboardingReport(): Promise<OnboardingReportRow[]> {
  return apiGet<OnboardingReportRow[]>('/reports/onboarding')
}

/** C43 — Onboarding Excel export; triggers browser download. */
export function exportOnboarding(): Promise<void> {
  return apiDownload('/reports/onboarding/export', 'onboarding.xlsx')
}

/** C44 — Contracts & Probation report rows, optionally filtered by event type. */
export function getContractsProbation(params: ContractsProbationParams = {}): Promise<ContractsProbationReportRow[]> {
  const qs = new URLSearchParams()
  if (params.type !== undefined && params.type !== '') qs.set('type', params.type)
  const q = qs.toString()
  return apiGet<ContractsProbationReportRow[]>(`/reports/contracts-probation${q ? `?${q}` : ''}`)
}

/** C45 — Contracts & Probation Excel export; triggers browser download. */
export function exportContractsProbation(params: ContractsProbationParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.type !== undefined && params.type !== '') qs.set('type', params.type)
  const q = qs.toString()
  return apiDownload(`/reports/contracts-probation/export${q ? `?${q}` : ''}`, 'contracts-probation.xlsx')
}

/** C46 — Unreturned Assets report rows, optionally filtered by asset status. */
export function getUnreturnedAssets(params: UnreturnedAssetsParams = {}): Promise<UnreturnedAssetsReportRow[]> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiGet<UnreturnedAssetsReportRow[]>(`/reports/unreturned-assets${q ? `?${q}` : ''}`)
}

/** C47 — Unreturned Assets Excel export; triggers browser download. */
export function exportUnreturnedAssets(params: UnreturnedAssetsParams = {}): Promise<void> {
  const qs = new URLSearchParams()
  if (params.status !== undefined && params.status !== '') qs.set('status', params.status)
  const q = qs.toString()
  return apiDownload(`/reports/unreturned-assets/export${q ? `?${q}` : ''}`, 'unreturned-assets.xlsx')
}
