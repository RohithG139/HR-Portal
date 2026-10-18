/**
 * Date formatting utilities for CubeWorks HR.
 *
 * Rule R-UI-5 / G-12: all dates are displayed and entered as `dd-mm-yyyy`.
 * The backend stores and receives dates as ISO `yyyy-MM-dd` strings (R-API-1).
 * These helpers are the single conversion point — no other file should
 * perform date string transformations.
 *
 * Both functions operate on strings only — no `Date` objects — to avoid
 * timezone and DST issues with `new Date('yyyy-MM-dd')` parsing.
 */

/**
 * Converts an ISO date string (`yyyy-MM-dd`) returned by the backend into the
 * display format (`dd-mm-yyyy`) required by R-UI-5.
 *
 * @param isoDate  ISO date string from the API, e.g. `"2024-01-05"`.
 * @returns        Display string `"05-01-2024"`, or an empty string when the
 *                 input is empty/undefined.
 * @throws {Error} When `isoDate` does not match `yyyy-MM-dd`.
 *
 * @example
 *   formatDate('2024-01-05') // → '05-01-2024'
 *   formatDate('2024-12-31') // → '31-12-2024'
 */
export function formatDate(isoDate: string): string {
  if (isoDate === '') return ''

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate)
  if (match === null) {
    throw new Error(
      `formatDate: expected "yyyy-MM-dd" but received "${isoDate}"`,
    )
  }

  // match[1] = year, match[2] = month, match[3] = day
  // All groups are always present when the regex matches, so the non-null
  // assertion below is safe; the regex guarantees three capture groups.
  const year = match[1]!
  const month = match[2]!
  const day = match[3]!

  return `${day}-${month}-${year}`
}

/**
 * Converts a user-entered display date (`dd-mm-yyyy`) into the ISO format
 * (`yyyy-MM-dd`) required by the backend API (R-API-1).
 *
 * @param displayDate  Display date string, e.g. `"05-01-2024"`.
 * @returns            ISO string `"2024-01-05"`, or an empty string when the
 *                     input is empty/undefined.
 * @throws {Error}     When `displayDate` does not match `dd-mm-yyyy`.
 *
 * @example
 *   parseDate('05-01-2024') // → '2024-01-05'
 *   parseDate('31-12-2024') // → '2024-12-31'
 */
export function parseDate(displayDate: string): string {
  if (displayDate === '') return ''

  const match = /^(\d{2})-(\d{2})-(\d{4})$/.exec(displayDate)
  if (match === null) {
    throw new Error(
      `parseDate: expected "dd-mm-yyyy" but received "${displayDate}"`,
    )
  }

  // match[1] = day, match[2] = month, match[3] = year
  const day = match[1]!
  const month = match[2]!
  const year = match[3]!

  return `${year}-${month}-${day}`
}
