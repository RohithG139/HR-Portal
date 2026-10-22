/**
 * Typed HTTP client for the CubeWorks HR backend.
 *
 * All requests go to `VITE_API_BASE_URL` (default: http://localhost:8080/api).
 * Per R-ARC-4, React never calls the CaseFabric engine or Keycloak admin APIs
 * directly — every call goes through this client to the Spring Boot BFF.
 *
 * Authorization: every request carries `Authorization: Bearer <token>` read from
 * the token module registered by AuthProvider (see src/api/token.ts).
 *
 * Error contract (R-API-3):
 *   - Non-2xx responses are parsed as `ApiError` and thrown as `ApiResponseError`.
 *   - Network failures propagate as native `Error`.
 */

import { ApiResponseError } from './types'
import type { ApiError } from './types'
import { getToken } from './token'

/** Resolved at module load so the base URL is set once. */
const BASE_URL: string =
  import.meta.env['VITE_API_BASE_URL'] ?? 'http://localhost:8080/api'

/**
 * Default headers sent with every request.
 * Includes the Keycloak Bearer token when authenticated (R-ARC-4, R-API-2).
 */
function defaultHeaders(): HeadersInit {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
  if (token !== undefined) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return headers
}

/**
 * Parse a non-2xx response body as `ApiError`.
 * Falls back to a generic shape when the body is not valid JSON
 * (e.g. a 502 gateway error with an HTML body).
 */
async function parseErrorBody(response: Response): Promise<ApiError> {
  try {
    return (await response.json()) as ApiError
  } catch {
    return { code: 'NOT_FOUND' }
  }
}

/**
 * Core fetch wrapper. Resolves with the parsed JSON body on success,
 * throws `ApiResponseError` on non-2xx, and rethrows network errors.
 *
 * @param path   Path relative to `VITE_API_BASE_URL`, must start with `/`.
 * @param init   Standard `RequestInit` options (method, body, headers, …).
 * @returns      Parsed response body cast to `T`.
 */
export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const url = `${BASE_URL}${path}`
  const response = await fetch(url, {
    ...init,
    headers: {
      ...defaultHeaders(),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const body = await parseErrorBody(response)
    throw new ApiResponseError(response.status, body)
  }

  // 204 No Content — return undefined cast to T (callers that expect no body
  // should type T as `void` or `undefined`).
  if (response.status === 204) {
    return undefined as T
  }

  // Some endpoints return a 2xx with an empty body (e.g. 202 Accepted with no payload —
  // found live, 2026-10-18: calling response.json() on an empty body throws, which the
  // caller's error handling then misreports as a failed request even though it succeeded).
  // Read as text first so an empty body resolves to undefined instead of throwing.
  const text = await response.text()
  if (text.length === 0) {
    return undefined as T
  }
  return JSON.parse(text) as T
}

/** Convenience wrapper for GET requests. */
export function apiGet<T>(path: string, init?: Omit<RequestInit, 'method'>): Promise<T> {
  return apiFetch<T>(path, { ...init, method: 'GET' })
}

/** Convenience wrapper for POST requests with a JSON body. */
export function apiPost<T>(
  path: string,
  body: unknown,
  init?: Omit<RequestInit, 'method' | 'body'>,
): Promise<T> {
  return apiFetch<T>(path, {
    ...init,
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** Convenience wrapper for PUT requests with a JSON body. */
export function apiPut<T>(
  path: string,
  body: unknown,
  init?: Omit<RequestInit, 'method' | 'body'>,
): Promise<T> {
  return apiFetch<T>(path, {
    ...init,
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

/** Convenience wrapper for PATCH requests with a JSON body. */
export function apiPatch<T>(
  path: string,
  body: unknown,
  init?: Omit<RequestInit, 'method' | 'body'>,
): Promise<T> {
  return apiFetch<T>(path, {
    ...init,
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

/** Convenience wrapper for DELETE requests. */
export function apiDelete<T>(
  path: string,
  init?: Omit<RequestInit, 'method'>,
): Promise<T> {
  return apiFetch<T>(path, { ...init, method: 'DELETE' })
}

/**
 * Fetches an authenticated API endpoint and returns the raw response body as a Blob,
 * for endpoints that return a binary payload directly (e.g. a PDF) rather than JSON.
 *
 * @param path API path (relative to BASE_URL)
 */
export async function apiFetchBlob(path: string): Promise<Blob> {
  const token = getToken()
  const headers: Record<string, string> = { Accept: '*/*' }
  if (token !== undefined) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${BASE_URL}${path}`, { headers })
  if (!response.ok) {
    const body = await parseErrorBody(response)
    throw new ApiResponseError(response.status, body)
  }
  return response.blob()
}

/**
 * Triggers a file download from an authenticated API endpoint.
 * The response is streamed as a Blob and saved using a temporary anchor element.
 *
 * @param path     API path (relative to BASE_URL)
 * @param filename Suggested file name for the download
 */
export async function apiDownload(path: string, filename: string): Promise<void> {
  const blob = await apiFetchBlob(path)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/**
 * POSTs a multipart form (file upload) and parses the JSON response.
 * The `Content-Type` header is left to the browser so it can add the multipart boundary.
 *
 * @param path API path (relative to BASE_URL)
 * @param form the multipart body
 */
export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token !== undefined) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(`${BASE_URL}${path}`, { method: 'POST', headers, body: form })
  if (!response.ok) {
    const body = await parseErrorBody(response)
    throw new ApiResponseError(response.status, body)
  }
  return (await response.json()) as T
}
