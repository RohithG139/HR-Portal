/**
 * API module for Settings — Timers & rules (FRD-08 §6 C29–C30).
 * DTO shapes match the Java records in dto/settings/ exactly (R-CODE-3, R-SRC-2).
 */

import { apiGet, apiPut } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/**
 * One timer/rule setting row (C29).
 * `code`, `category`, `module` and `label` are read-only (ADM-01) — never sent in update requests.
 * Only `valueInt`/`valueText`, `recipientRole` and `enabled` are writable.
 */
export interface TimerDto {
  code: string
  category: string
  module: string
  label: string
  /** Numeric value (days/minutes/etc.); null for text-valued settings like KPI_CYCLE. */
  valueInt: number | null
  /** Unit of valueInt, e.g. "DAYS", "SECONDS", "MINUTES", "CHARACTERS". */
  valueUnit: string | null
  /** Text value; non-null only for KPI_CYCLE. */
  valueText: string | null
  /** Who is notified when this timer fires; null if no notification. */
  recipientRole: string | null
  /** Whether HR can toggle the enabled state for this row. */
  canDisable: boolean
  /** Whether this setting is currently active. */
  enabled: boolean
  /** Optimistic-lock version — must be sent back in update requests (R-DB-5, spec §12 D-10). */
  version: number
}

/**
 * Request body for updating a single timer/rule setting (C30).
 * `valueInt`/`valueText`/`recipientRole`/`enabled` are optional — null means "do not change
 * this field". `version` is required (R-DB-5, D-10); the server rejects a stale write with
 * `409 STALE_VERSION`. `code`/`category`/`module`/`label` are never in the body (ADM-01).
 */
export interface TimerUpdateRequest {
  valueInt?: number | null
  valueText?: string | null
  recipientRole?: string | null
  enabled?: boolean | null
  version: number
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** C29 — List all 17 timer/rule settings. */
export function listTimers(): Promise<TimerDto[]> {
  return apiGet<TimerDto[]>('/settings/timers')
}

/** C30 — Update one timer/rule setting by code. Only writable fields are sent. */
export function updateTimer(code: string, body: TimerUpdateRequest): Promise<TimerDto> {
  return apiPut<TimerDto>(`/settings/timers/${encodeURIComponent(code)}`, body)
}
