/**
 * API module for asset endpoints (A21, A22).
 * DTO shapes match AssetService.java (R-SRC-2, R-CODE-3).
 */

import { apiPost } from './client'
import type { AssetDto } from './onboarding'

// ---------------------------------------------------------------------------
// Request types
// ---------------------------------------------------------------------------

/** Request body for adding a new asset (A21). */
export interface AddAssetRequest {
  assetType: string
  model: string
  assetNumber: string
  serialNumber?: string | null
  issuedToEmployeeId: number
  issueDate: string
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A21 — IT admin records issued equipment (adds asset + ticks checklist item). */
export function addAsset(employeeId: number, request: AddAssetRequest): Promise<AssetDto> {
  return apiPost<AssetDto>(`/employees/${employeeId}/assets`, request)
}

/** A22 — IT admin removes an asset before the employee confirms receipt. */
export function unissueAsset(assetId: number): Promise<void> {
  return apiPost<void>(`/assets/${assetId}/unissue`, {})
}
