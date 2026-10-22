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

/**
 * Lenient variant of {@link parseDate} for form fields that may hold a partly-typed
 * value: `dd-mm-yyyy` → `yyyy-MM-dd`, or `''` when the input is not a full date yet.
 * Never throws (used while the user is still typing, and by the native date picker).
 */
export function toIsoDateOrEmpty(displayDate: string): string {
  const m = /^(\d{2})-(\d{2})-(\d{4})$/.exec(displayDate)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : ''
}

/**
 * Lenient variant of {@link formatDate}: `yyyy-MM-dd` → `dd-mm-yyyy`, or `''` when the
 * input is not a full ISO date. Never throws.
 */
export function toDisplayDateOrEmpty(isoDate: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate)
  return m ? `${m[3]}-${m[2]}-${m[1]}` : ''
}

/**
 * Whole calendar days from today (local time) until an ISO `yyyy-MM-dd` due date:
 * 0 = due today, negative = overdue, positive = days left. The date is read as a
 * local calendar date — `new Date('yyyy-MM-dd')` would parse it as UTC midnight and
 * shift it by a day in timezones west of UTC.
 *
 * @param isoDate ISO date string, e.g. `"2026-10-21"`.
 * @param now     the current moment (injectable for tests).
 * @returns       day difference, or `null` when `isoDate` is not a valid ISO date.
 */
export function daysUntil(isoDate: string, now: Date = new Date()): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate)
  if (m === null) return null
  const due = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]))
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  // Math.round absorbs the 1-hour DST shift between two local midnights
  return Math.round((due.getTime() - today.getTime()) / 86_400_000)
}

/**
 * R-UI-6 / G-05 due-date colour: overdue (before today) = red, due today or within
 * 3 days = orange, otherwise grey.
 */
export function dueDateColourClass(isoDate: string, now: Date = new Date()): string {
  const days = daysUntil(isoDate, now)
  if (days === null) return 'text-gray-500'
  if (days < 0) return 'text-red-600'
  if (days <= 3) return 'text-orange-500'
  return 'text-gray-500'
}

/**
 * Formats an ISO-8601 instant (e.g. `2026-10-21T09:15:30Z`) as a local
 * `dd-mm-yyyy HH:mm:ss` string for display (R-UI-5) — never shown as raw ISO.
 * Returns `''` for an unparseable value.
 */
export function formatDateTime(isoInstant: string): string {
  const d = new Date(isoInstant)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${p(d.getDate())}-${p(d.getMonth() + 1)}-${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
