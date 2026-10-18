/**
 * API module for Settings — Checklist templates (FRD-08 §6 C07–C13).
 * DTO shapes match the Java records in dto/settings/ exactly (R-CODE-3, R-SRC-2).
 */

import { apiGet, apiPost, apiPut, apiDelete } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** List-view row for a checklist template (C07). */
export interface ChecklistTemplateListDto {
  id: number
  name: string
  kind: 'ONBOARDING' | 'OFFBOARDING'
  itemCount: number
  /** null when active; non-null ISO timestamp when archived (ADM-04). */
  archivedAt: string | null
}

/** Full detail for a checklist template including all items (C08). */
export interface ChecklistTemplateDetailDto {
  id: number
  name: string
  kind: 'ONBOARDING' | 'OFFBOARDING'
  jobTitleId: number | null
  copiedFromId: number | null
  archivedAt: string | null
  items: ChecklistTemplateItemDto[]
}

/** One item row within a checklist template (C08, C11, C12). */
export interface ChecklistTemplateItemDto {
  id: number
  name: string
  section: string
  ownerRole: string
  mandatory: boolean
  dueOffsetDays: number
  sortOrder: number
}

/** Request body for creating or updating a checklist template item (C11, C12). */
export interface ChecklistTemplateItemRequest {
  name: string
  section: string
  ownerRole: string
  mandatory: boolean
  dueOffsetDays: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** C07 — List checklist templates, optionally filtered by kind and archived state. */
export function listChecklistTemplates(
  kind?: string,
  includeArchived?: boolean,
): Promise<ChecklistTemplateListDto[]> {
  const qs = new URLSearchParams()
  if (kind !== undefined && kind !== '') qs.set('kind', kind)
  if (includeArchived === true) qs.set('includeArchived', 'true')
  const query = qs.toString()
  return apiGet<ChecklistTemplateListDto[]>(
    `/settings/checklist-templates${query ? `?${query}` : ''}`,
  )
}

/** C08 — Get full detail of one checklist template (admin view, with items). */
export function getChecklistTemplate(id: number): Promise<ChecklistTemplateDetailDto> {
  return apiGet<ChecklistTemplateDetailDto>(`/settings/checklist-templates/${id}`)
}

/** C09 — Create a new checklist template (optionally copied from an existing one). */
export function createChecklistTemplate(body: {
  name: string
  kind: string
  jobTitleId?: number | null
  copiedFromId?: number | null
}): Promise<{ id: number }> {
  return apiPost<{ id: number }>('/settings/checklist-templates', body)
}

/** C10 — Archive a checklist template (ADM-04). Sets archivedAt; never deletes. */
export function archiveChecklistTemplate(id: number): Promise<void> {
  return apiPost<void>(`/settings/checklist-templates/${id}/archive`, {})
}

/** C11 — Add an item to a checklist template. */
export function addChecklistTemplateItem(
  templateId: number,
  item: ChecklistTemplateItemRequest,
): Promise<ChecklistTemplateItemDto> {
  return apiPost<ChecklistTemplateItemDto>(
    `/settings/checklist-templates/${templateId}/items`,
    item,
  )
}

/** C12 — Update an existing item within a checklist template. */
export function updateChecklistTemplateItem(
  templateId: number,
  itemId: number,
  item: ChecklistTemplateItemRequest,
): Promise<ChecklistTemplateItemDto> {
  return apiPut<ChecklistTemplateItemDto>(
    `/settings/checklist-templates/${templateId}/items/${itemId}`,
    item,
  )
}

/** C13 — Delete an item. 409 ITEM_IN_USE if already instantiated into a running case (spec §7 D-02). */
export function deleteChecklistTemplateItem(
  templateId: number,
  itemId: number,
): Promise<void> {
  return apiDelete<void>(`/settings/checklist-templates/${templateId}/items/${itemId}`)
}
