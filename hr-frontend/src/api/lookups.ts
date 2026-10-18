/**
 * API module for lookup endpoints (A02, A03).
 * DTO shapes match LookupService.java records (R-SRC-2, R-CODE-3).
 */

import { apiGet } from './client'

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

/** Simple id + name pair used in all dropdown lists. */
export interface IdNameDto {
  id: number
  name: string
}

/** All lookups for the onboarding form dropdowns (A02). */
export interface OnboardingLookupsDto {
  departments: IdNameDto[]
  jobTitles: IdNameDto[]
  managers: IdNameDto[]
  buddies: IdNameDto[]
  grades: string[]
  appRoles: string[]
  templates: IdNameDto[]
}

/** Fixed IT option lists (A03). */
export interface ItLookupsDto {
  laptops: string[]
  keyboardLayouts: string[]
  deliveryMethods: string[]
  accessories: string[]
  accounts: string[]
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

/** A02 — Onboarding form lookups (HR only). */
export function getOnboardingLookups(): Promise<OnboardingLookupsDto> {
  return apiGet<OnboardingLookupsDto>('/lookups/onboarding')
}

/** A03 — IT request option lists (HR and IT_ADMIN). */
export function getItLookups(): Promise<ItLookupsDto> {
  return apiGet<ItLookupsDto>('/lookups/it')
}
