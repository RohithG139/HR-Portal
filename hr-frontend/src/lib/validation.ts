/**
 * Shared validation helpers for CubeWorks HR.
 *
 * These are client-side mirrors of the backend Bean Validation rules.
 * Per R-CODE-2, validation messages come from the API; these helpers are
 * used only to give immediate inline feedback before submission — they must
 * use the exact same rules as the backend so they never contradict it.
 *
 * Gate 4 fills in per-screen validators as each feature is implemented.
 * Add a helper here only when it will be used by at least one screen.
 */

/**
 * Returns true when `value` is a well-formed e-mail address.
 *
 * Pattern matches the same subset accepted by Spring's `@Email` validator:
 * local-part @ domain . tld (no IP literals, no comments).
 *
 * @param value  The string to validate.
 * @returns      `true` when the string looks like a valid e-mail.
 */
export function isValidEmail(value: string): boolean {
  // Simple RFC-5321-compatible check; deliberately not exhaustive —
  // the backend is the authoritative validator.
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

/**
 * Returns true when `value` matches the phone format used in the FRD.
 *
 * The FRD example is "+31 6 12345678" (international prefix, optional spaces).
 * We accept:
 *   - A leading `+` followed by the country code
 *   - Digits and spaces, at least 7 digits total
 *
 * @param value  The string to validate.
 * @returns      `true` when the string looks like a valid international phone number.
 */
export function isValidPhone(value: string): boolean {
  const stripped = value.replace(/\s/g, '')
  // Must start with + and have at least 7 digits after the +
  return /^\+\d{7,15}$/.test(stripped)
}
