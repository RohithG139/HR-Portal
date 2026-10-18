/**
 * Unit tests for src/lib/date.ts
 *
 * Tests cover both directions of conversion (ISO ↔ display) and edge cases
 * including single-digit day/month components (stored as zero-padded strings
 * by the backend, e.g. "2024-01-05" → "05-01-2024").
 */

import { describe, expect, it } from 'vitest'
import { formatDate, parseDate } from './date'

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
