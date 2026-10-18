/**
 * API module for My Tasks endpoints (A36, A37).
 * DTO shapes match TaskService.java records exactly (R-SRC-2, R-CODE-3).
 */

import { apiGet, apiPost } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/**
 * A single task item (TaskService.TaskItemDto).
 *
 * dueState: "OVERDUE" | "DUE_SOON" | "UPCOMING" — computed by the backend (R-UI-6, R-ARC-1).
 * React maps this to colour only; never recomputes due state client-side.
 */
export interface TaskItemDto {
  taskId: number
  module: string
  title: string
  employeeId: number | null
  dueDate: string | null
  dueState: 'OVERDUE' | 'DUE_SOON' | 'UPCOMING'
}

/** Paginated task list (A36, TaskService.TaskPageDto). */
export interface TaskPageDto {
  content: TaskItemDto[]
  totalElements: number
}

/** A36 query params. */
export interface TaskListParams {
  module?: string
  q?: string
  page?: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A36 — Get My Tasks list. */
export function getMyTasks(params: TaskListParams = {}): Promise<TaskPageDto> {
  const qs = new URLSearchParams()
  if (params.module !== undefined) qs.set('module', params.module)
  if (params.q !== undefined) qs.set('q', params.q)
  if (params.page !== undefined) qs.set('page', String(params.page))
  const query = qs.toString()
  return apiGet<TaskPageDto>(`/tasks${query ? `?${query}` : ''}`)
}

/** A36 — Get open task count (for the badge). */
export function getTaskCount(): Promise<{ count: number }> {
  return apiGet<{ count: number }>('/tasks/count')
}

/** A37 — Undo a pending task completion. */
export function undoTask(taskId: number): Promise<void> {
  return apiPost<void>(`/tasks/${taskId}/undo`, {})
}
