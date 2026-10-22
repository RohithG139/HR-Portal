/**
 * API module for the role-dispatched dashboard endpoint (W01, FRD-01 Wave 3).
 * One endpoint, four DTO shapes — the server picks the shape based on the caller's role.
 * R-SRC-2: DTO shapes match the Java records exactly.
 */

import { apiGet } from './client'

// ---------------------------------------------------------------------------
// Shared sub-types
// ---------------------------------------------------------------------------

/**
 * A single task item shown on role-specific dashboards (W01–W04).
 * Maps from DashboardTaskDto.java.
 */
export interface DashboardTaskDto {
  taskId: number | null
  title: string
  employeeId: number | null
  dueDate: string | null
  /** "OVERDUE", "DUE_SOON" (within 3 days), or "UPCOMING" (R-UI-6) */
  dueState: string
  /** Name of the employee the task is about, for "Task · Name" rows. */
  employeeName?: string | null
  /** Employee code and related id, for the probation task routes (FRD-02 Screen 12/13). */
  employeeCode?: string | null
  relatedId?: number | null
}

/**
 * A single issued asset shown on the Employee active dashboard equipment card (W03).
 * Maps from EquipmentDto.java.
 */
export interface EquipmentDto {
  assetId: number
  assetNumber: string
  assetType: string
  model: string
}

// ---------------------------------------------------------------------------
// Per-role dashboard DTOs
// ---------------------------------------------------------------------------

/**
 * HR dashboard shape (W01/HrDashboardDto.java).
 * Deferred fields (bench, pip, appraisal, upcoming) are absent and will be
 * added additively by F04/F07/F06/F08 (additive DTO contract, spec §7 D-W2).
 */
export interface HrDashboardDto {
  activeEmployees: number
  /** Count of employees who became ACTIVE within the current calendar month. */
  activeEmployeesDelta: number
  onboardingCount: number
  /** Up to 2 names of employees currently onboarding. */
  onboardingNames: string[]
  tasks: DashboardTaskDto[]
  /** "Upcoming" card rows, soonest first (max 5). Optional: absent on older backends. */
  upcoming?: UpcomingEventDto[]
}

/** One "Upcoming" event on the HR dashboard (UpcomingEventDto.java). */
export interface UpcomingEventDto {
  /** PROBATION | CONTRACT | PIP | START | LAST_DAY */
  type: string
  employeeId: number
  employeeName: string
  /** ISO date (YYYY-MM-DD) */
  date: string
}

/**
 * Manager dashboard shape (W02/ManagerDashboardDto.java).
 * Deferred fields (benchCount, reviewsToWrite, plansToApprove) added by F04/F05/F06.
 */
export interface ManagerDashboardDto {
  teamMembers: number
  tasks: DashboardTaskDto[]
  myTeam: TeamMemberSummaryDto[]
}

/**
 * Minimal team member summary used inside the Manager dashboard's team card.
 * Maps from TeamMemberDto.java.
 */
export interface TeamMemberSummaryDto {
  employeeId: number
  employeeCode: string
  firstName: string
  lastName: string
  jobTitle: string | null
  stage: string
}

/**
 * Employee active-stage dashboard shape (W03/EmployeeDashboardDto.java).
 * Deferred fields (currentProjects, developmentPlan, appraisal) added by F04/F05/F06.
 */
export interface EmployeeDashboardDto {
  equipment: EquipmentDto[]
  tasks: DashboardTaskDto[]
}

/**
 * IT admin dashboard shape (W04/ItAdminDashboardDto.java).
 * Deferred fields (accessToRevokeCount, notReturnedCount) added by F08.
 */
export interface ItAdminDashboardDto {
  equipmentRequestsCount: number
  openSetupItemsCount: number
  tasks: DashboardTaskDto[]
  /** Earliest-due open equipment request: employee name and contract start (ISO). */
  nextEquipmentName?: string | null
  nextEquipmentStartDate?: string | null
  /** Up to 2 names with open "Remaining setup" tasks. */
  setupItemNames?: string[]
  /** Exits in progress whose access must be revoked; earliest one's name + last day (ISO). */
  accessToRevokeCount?: number
  nextRevokeName?: string | null
  nextRevokeDate?: string | null
  /** Assets not returned by ex-employees. */
  notReturnedCount?: number
}

/**
 * Union of all possible dashboard shapes.
 * The caller must branch on the authenticated user's role (from useAuth()) to
 * know which shape was returned — the same way the backend dispatches server-side.
 */
export type DashboardDto =
  | HrDashboardDto
  | ManagerDashboardDto
  | EmployeeDashboardDto
  | ItAdminDashboardDto

// ---------------------------------------------------------------------------
// API function
// ---------------------------------------------------------------------------

/**
 * W01 — Fetches the role-dispatched dashboard data.
 *
 * Returns `unknown` intentionally: the caller must narrow to the correct DTO
 * shape based on `user.appRole` (from useAuth()), since the endpoint returns
 * one of four different shapes depending on the authenticated role.
 */
export function getDashboard(): Promise<unknown> {
  return apiGet<unknown>('/dashboard')
}
