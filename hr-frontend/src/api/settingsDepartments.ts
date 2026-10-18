/**
 * API module for Settings — Departments & Job titles (FRD-08 §6 C01–C06).
 * DTO shapes match the Java records in dto/settings/ exactly (R-CODE-3, R-SRC-2).
 */

import { apiGet, apiPost, apiDelete } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** Response body for one department row (C01). "People" column = employeeCount. */
export interface DepartmentDto {
  id: number
  name: string
  employeeCount: number
}

/** Response body for one job-title row (C04). Grade/department not in schema (spec §5 D-01). */
export interface JobTitleDto {
  id: number
  name: string
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** C01 — List all departments with employee counts. */
export function listDepartments(): Promise<DepartmentDto[]> {
  return apiGet<DepartmentDto[]>('/settings/departments')
}

/** C02 — Create a new department. Returns 201 on success; 409 DUPLICATE_NAME if name taken. */
export function createDepartment(name: string): Promise<DepartmentDto> {
  return apiPost<DepartmentDto>('/settings/departments', { name })
}

/** C03 — Delete a department. Returns 204; 409 ITEM_IN_USE if employees are assigned (ADM-03). */
export function deleteDepartment(id: number): Promise<void> {
  return apiDelete<void>(`/settings/departments/${id}`)
}

/** C04 — List all job titles. */
export function listJobTitles(): Promise<JobTitleDto[]> {
  return apiGet<JobTitleDto[]>('/settings/job-titles')
}

/** C05 — Create a new job title. Returns 201; 409 DUPLICATE_NAME if name taken. */
export function createJobTitle(name: string): Promise<JobTitleDto> {
  return apiPost<JobTitleDto>('/settings/job-titles', { name })
}

/** C06 — Delete a job title. Returns 204; 409 ITEM_IN_USE if employees use it (ADM-03). */
export function deleteJobTitle(id: number): Promise<void> {
  return apiDelete<void>(`/settings/job-titles/${id}`)
}
