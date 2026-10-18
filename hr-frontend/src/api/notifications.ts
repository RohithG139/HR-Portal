/**
 * API module for notification endpoints (A38).
 * DTO shapes match NotificationController.java response maps.
 */

import { apiGet, apiPost } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** One notification item (A38, NotificationController response map). */
export interface NotificationDto {
  id: number
  title: string
  message: string
  linkRoute: string
  read: boolean
  createdAt: string
}

/** Paginated notification list (A38). */
export interface NotificationPageDto {
  content: NotificationDto[]
  totalElements: number
  totalPages: number
  page: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A38 — Get notification list. */
export function getNotifications(unreadOnly = false, page = 0): Promise<NotificationPageDto> {
  return apiGet<NotificationPageDto>(
    `/notifications?unreadOnly=${String(unreadOnly)}&page=${page}`,
  )
}

/** A38 — Get unread notification count (for bell badge). */
export function getNotificationCount(): Promise<{ count: number }> {
  return apiGet<{ count: number }>('/notifications/count')
}

/** A38 — Mark one notification as read. */
export function markNotificationRead(id: number): Promise<void> {
  return apiPost<void>(`/notifications/${id}/read`, {})
}

/** A38 — Mark all notifications as read. */
export function markAllNotificationsRead(): Promise<void> {
  return apiPost<void>('/notifications/read-all', {})
}
