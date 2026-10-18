/**
 * API error types matching the contract defined in CLAUDE.md R-API-3.
 *
 * Every error response from the Spring Boot backend follows one of these shapes:
 *   400 → ApiValidationError
 *   403 → ApiForbiddenError
 *   404 → ApiNotFoundError
 *   409 → ApiConflictError
 *
 * The `code` discriminant lets callers narrow the error type safely.
 */

/** One field-level validation failure inside a 400 response. */
export interface ValidationFieldError {
  field: string
  message: string
}

/** 400 VALIDATION_FAILED — one or more field errors. */
export interface ApiValidationError {
  code: 'VALIDATION_FAILED'
  errors: ValidationFieldError[]
}

/** 403 FORBIDDEN — caller lacks the required role or fails the self-check (G-13). */
export interface ApiForbiddenError {
  code: 'FORBIDDEN'
}

/** 404 NOT_FOUND — the requested resource does not exist. */
export interface ApiNotFoundError {
  code: 'NOT_FOUND'
}

/**
 * 409 conflict — the specific code identifies the conflict kind,
 * e.g. STALE_VERSION for optimistic-locking failures (R-DB-5).
 */
export interface ApiConflictError {
  code: string
  message: string
}

/** Union of all structured error responses from the backend. */
export type ApiError =
  | ApiValidationError
  | ApiForbiddenError
  | ApiNotFoundError
  | ApiConflictError

/**
 * Wrapper thrown by the typed fetch client when the server returns a non-2xx
 * status. Callers narrow on `error.body.code` to handle each case.
 */
export class ApiResponseError extends Error {
  // Explicit property declarations required because `erasableSyntaxOnly` is
  // enabled — TypeScript parameter-property shorthand is not allowed.
  readonly status: number
  readonly body: ApiError

  constructor(status: number, body: ApiError) {
    super(`API error ${status}: ${body.code}`)
    this.name = 'ApiResponseError'
    this.status = status
    this.body = body
  }
}
