/**
 * API module for GET /api/me (A01), GET /api/me/profile (A29), PUT /api/me/profile (A30).
 * Returns the current user's identity, role and role-filtered menu list, and own profile.
 */

import { apiGet, apiPut } from './client'

/** Shape of the GET /api/me response (A01, LookupController.getMe). */
export interface MeDto {
  employeeId: number
  name: string
  initials: string
  email: string
  appRole: string
  menu: string[]
}

/** Employee's own editable profile (A29, MeProfileController). */
export interface MyProfileDto {
  id: number
  firstName: string
  lastName: string
  privateEmail: string
  workEmail: string | null
  mobilePhone: string
  dateOfBirth: string | null
  street: string
  postcode: string
  city: string
  emergencyName: string | null
  emergencyRelationship: string | null
  emergencyPhone: string | null
  jobTitle: string | null
  department: string | null
  grade: string | null
  contractType: string | null
  startDate: string | null
  hoursPerWeek: number | null
  version: number | null
}

/** Fields the employee can update (WS-03). */
export interface MyProfileUpdateRequest {
  privateEmail?: string
  mobilePhone?: string
  street?: string
  postcode?: string
  city?: string
  emergencyName?: string | null
  emergencyRelationship?: string | null
  emergencyPhone?: string | null
  version?: number | null
}

/** Fetches the current user's identity from the backend (A01). */
export function fetchMe(): Promise<MeDto> {
  return apiGet<MeDto>('/me')
}

/** A29 — Employee reads own full profile. */
export function getMyProfile(): Promise<MyProfileDto> {
  return apiGet<MyProfileDto>('/me/profile')
}

/** A30 — Employee updates own contact + emergency details (WS-03 only). */
export function updateMyProfile(request: MyProfileUpdateRequest): Promise<MyProfileDto> {
  return apiPut<MyProfileDto>('/me/profile', request)
}
