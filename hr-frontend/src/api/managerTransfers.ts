/**
 * API module for the Transfer Manager feature (W09/W10, FRD-01 Wave 3).
 * DTO shapes match ManagerTaskItemDto.java, ManagerTasksResponse.java,
 * TransferManagerRequest.java and TransferManagerResponse.java (R-SRC-2, R-CODE-3).
 */

import { apiGet, apiPost } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/**
 * A single open task in the Transfer Manager tick-list (W09 / ManagerTaskItemDto.java).
 * HR ticks which tasks to move to the new manager; ticked IDs go in W10's taskIdsToMove.
 */
export interface ManagerTaskItemDto {
  id: number
  taskName: string
  /** ISO yyyy-MM-dd due date, or null if not set. */
  dueDate: string | null
}

/**
 * Response wrapper for W09 (GET /api/employees/{id}/manager-tasks).
 * Maps from ManagerTasksResponse.java.
 */
export interface ManagerTasksResponse {
  openManagerTasks: ManagerTaskItemDto[]
}

/**
 * Request body for W10 (POST /api/employees/{id}/manager-transfers).
 * Maps from TransferManagerRequest.java.
 *
 * Validation errors (R-API-3):
 *   newManagerId required/differs → 400 {field:"newManagerId", message:"Choose a new manager."}
 *   effectiveDate not before today → 400 {field:"effectiveDate", message:"The effective date cannot be in the past."}
 */
export interface TransferManagerRequest {
  /** ID of the employee who will become the new manager. */
  newManagerId: number | null
  /** ISO yyyy-MM-dd effective date; must be today or later. */
  effectiveDate: string
  /** Optional reason for the transfer. */
  reason?: string
  /** Surrogate PKs of engine_task rows to move to the new manager. */
  taskIdsToMove: number[]
}

/**
 * Response body for W10 (POST /api/employees/{id}/manager-transfers → 201).
 * Maps from TransferManagerResponse.java.
 */
export interface TransferManagerResponse {
  changeId: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/**
 * W09 — Returns the open engine tasks for an employee owned by their current manager.
 * Used to populate the tick-list in the Transfer Manager modal.
 *
 * @param employeeId - the employee whose manager tasks to fetch
 */
export function getManagerTasks(employeeId: number): Promise<ManagerTasksResponse> {
  return apiGet<ManagerTasksResponse>(`/employees/${employeeId}/manager-tasks`)
}

/**
 * W10 — Creates a manager transfer request.
 * Returns 201 with the new changeId on success.
 * Returns 400 VALIDATION_FAILED with field errors on validation failure.
 *
 * @param employeeId - the employee whose manager to transfer
 * @param request    - the transfer details
 */
export function createManagerTransfer(
  employeeId: number,
  request: TransferManagerRequest,
): Promise<TransferManagerResponse> {
  return apiPost<TransferManagerResponse>(`/employees/${employeeId}/manager-transfers`, request)
}
