/**
 * Unit tests for src/lib/date.ts
 *
 * Tests cover both directions of conversion (ISO ↔ display) and edge cases
 * including single-digit day/month components (stored as zero-padded strings
 * by the backend, e.g. "2024-01-05" → "05-01-2024").
 */

import { describe, expect, it } from 'vitest'
import { daysUntil, dueDateColourClass, formatDate, formatDateTime, parseDate, toDisplayDateOrEmpty, toIsoDateOrEmpty } from './date'

// ---------------------------------------------------------------------------
// formatDate — ISO → display
// ---------------------------------------------------------------------------

describe('formatDate', () => {
  it('converts a standard ISO date to display format', () => {
    expect(formatDate('2024-10-15')).toBe('15-10-2024')
  })

  it('preserves leading zeros for single-digit day', () => {
    expect(formatDate('2024-10-05')).toBe('05-10-2024')
  })

  it('preserves leading zeros for single-digit month', () => {
    expect(formatDate('2024-01-20')).toBe('20-01-2024')
  })

  it('preserves leading zeros for single-digit day AND month', () => {
    expect(formatDate('2024-01-05')).toBe('05-01-2024')
  })

  it('handles the last day of the year', () => {
    expect(formatDate('2023-12-31')).toBe('31-12-2023')
  })

  it('handles the first day of the year', () => {
    expect(formatDate('2025-01-01')).toBe('01-01-2025')
  })

  it('returns an empty string for empty input', () => {
    expect(formatDate('')).toBe('')
  })

  it('throws for an invalid format (wrong separators)', () => {
    expect(() => formatDate('2024/10/15')).toThrow(/formatDate.*expected/)
  })

  it('throws for a display-format string passed in the wrong direction', () => {
    expect(() => formatDate('15-10-2024')).toThrow(/formatDate.*expected/)
  })
})

// ---------------------------------------------------------------------------
// parseDate — display → ISO
// ---------------------------------------------------------------------------

describe('parseDate', () => {
  it('converts a standard display date to ISO format', () => {
    expect(parseDate('15-10-2024')).toBe('2024-10-15')
  })

  it('preserves leading zeros for single-digit day', () => {
    expect(parseDate('05-10-2024')).toBe('2024-10-05')
  })

  it('preserves leading zeros for single-digit month', () => {
    expect(parseDate('20-01-2024')).toBe('2024-01-20')
  })

  it('preserves leading zeros for single-digit day AND month', () => {
    expect(parseDate('05-01-2024')).toBe('2024-01-05')
  })

  it('handles the last day of the year', () => {
    expect(parseDate('31-12-2023')).toBe('2023-12-31')
  })

  it('handles the first day of the year', () => {
    expect(parseDate('01-01-2025')).toBe('2025-01-01')
  })

  it('returns an empty string for empty input', () => {
    expect(parseDate('')).toBe('')
  })

  it('throws for an invalid format (wrong separators)', () => {
    expect(() => parseDate('15/10/2024')).toThrow(/parseDate.*expected/)
  })

  it('throws for an ISO-format string passed in the wrong direction', () => {
    expect(() => parseDate('2024-10-15')).toThrow(/parseDate.*expected/)
  })
})

// ---------------------------------------------------------------------------
// Round-trip
// ---------------------------------------------------------------------------

describe('formatDate / parseDate round-trip', () => {
  const isoDates = [
    '2024-01-05',
    '2024-10-15',
    '2023-12-31',
    '2025-01-01',
    '2000-06-09',
  ]

  for (const iso of isoDates) {
    it(`round-trips ${iso}`, () => {
      expect(parseDate(formatDate(iso))).toBe(iso)
    })
  }

  const displayDates = [
    '05-01-2024',
    '15-10-2024',
    '31-12-2023',
    '01-01-2025',
    '09-06-2000',
  ]

  for (const display of displayDates) {
    it(`round-trips display ${display}`, () => {
      expect(formatDate(parseDate(display))).toBe(display)
    })
  }
})

// ---------------------------------------------------------------------------
// Lenient conversions, due-date colours (R-UI-6) and date-time display
// ---------------------------------------------------------------------------


describe('toIsoDateOrEmpty / toDisplayDateOrEmpty', () => {
  it('converts full dates and returns "" for partial input instead of throwing', () => {
    expect(toIsoDateOrEmpty('21-10-2026')).toBe('2026-10-21')
    expect(toIsoDateOrEmpty('21-10-20')).toBe('')
    expect(toIsoDateOrEmpty('')).toBe('')
    expect(toDisplayDateOrEmpty('2026-10-21')).toBe('21-10-2026')
    expect(toDisplayDateOrEmpty('2026-10')).toBe('')
  })
})

describe('daysUntil / dueDateColourClass', () => {
  // 21-10-2026 at 23:30 local time — late in the day, where UTC parsing used to go wrong
  const now = new Date(2026, 9, 21, 23, 30, 0)

  it('reads yyyy-MM-dd as a local calendar date', () => {
    expect(daysUntil('2026-10-21', now)).toBe(0)
    expect(daysUntil('2026-10-20', now)).toBe(-1)
    expect(daysUntil('2026-10-24', now)).toBe(3)
    expect(daysUntil('not-a-date', now)).toBeNull()
  })

  it('due today is orange, not red; overdue is red; within 3 days orange; later grey', () => {
    expect(dueDateColourClass('2026-10-21', now)).toBe('text-orange-500')
    expect(dueDateColourClass('2026-10-20', now)).toBe('text-red-600')
    expect(dueDateColourClass('2026-10-24', now)).toBe('text-orange-500')
    expect(dueDateColourClass('2026-10-25', now)).toBe('text-gray-500')
  })
})

describe('formatDateTime', () => {
  it('shows an instant as local dd-mm-yyyy HH:mm:ss, never raw ISO', () => {
    const local = new Date(2026, 9, 21, 9, 5, 7)
    expect(formatDateTime(local.toISOString())).toBe('21-10-2026 09:05:07')
    expect(formatDateTime('garbage')).toBe('')
  })
})
