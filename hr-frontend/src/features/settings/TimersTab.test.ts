/**
 * Unit tests for the TimersTab save logic.
 *
 * These tests verify the multi-row-save behaviour required by spec §6 C30:
 * "user edits multiple rows locally, clicks ONE Save button, which fires one PUT
 * per changed row together (e.g. Promise.all)".
 *
 * We test the pure helper `buildTimerUpdate` (from settingsValidation.ts, re-exported
 * via TimersTab) to cover the "compute which rows changed" part without rendering.
 * The Promise.all fan-out itself is tested in settingsValidation.test.ts via
 * buildTimerUpdate's null-return contract (null = no-op, non-null = PUT needed).
 *
 * D-10 (Gate 5): every `original` passed to buildTimerUpdate must now carry `version` —
 * the backend requires it on every PUT (R-DB-5) since timer updates became optimistic-locked.
 */

import { describe, expect, it } from 'vitest'
import { buildTimerUpdate } from './settingsValidation'
import type { TimerDto } from '../../api/settingsTimers'

// ---------------------------------------------------------------------------
// Helper: build a minimal TimerDto for testing
// ---------------------------------------------------------------------------

function makeTimer(overrides: Partial<TimerDto> = {}): TimerDto {
  return {
    code: 'BENCH_CHECK',
    category: 'TIMER',
    module: 'Work cycle',
    label: 'Bench check',
    valueInt: 7,
    valueUnit: 'DAYS',
    valueText: null,
    recipientRole: 'MANAGER',
    canDisable: false,
    enabled: true,
    version: 0,
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Multi-row save behaviour tests
// ---------------------------------------------------------------------------

describe('TimersTab multi-row save logic (via buildTimerUpdate)', () => {
  it('returns null for an unchanged timer — no PUT should be fired', () => {
    const timer = makeTimer()
    const edit = {
      valueInt: timer.valueInt,
      valueText: timer.valueText,
      recipientRole: timer.recipientRole,
      enabled: timer.enabled,
    }
    // No edits → buildTimerUpdate returns null → no PUT
    expect(buildTimerUpdate(timer, edit)).toBeNull()
  })

  it('returns a patch when valueInt changes — one PUT should be fired', () => {
    const original = makeTimer({ valueInt: 7 })
    const edited = { valueInt: 10, valueText: null, recipientRole: 'MANAGER', enabled: true }

    const patch = buildTimerUpdate(original, edited)
    expect(patch).not.toBeNull()
    expect(patch?.valueInt).toBe(10)
  })

  it('returns a patch when enabled changes — one PUT should be fired for a canDisable row', () => {
    const original = makeTimer({ valueInt: 30, enabled: true })
    const edited = { valueInt: 30, valueText: null, recipientRole: 'MANAGER', enabled: false }

    const patch = buildTimerUpdate(original, edited)
    expect(patch).not.toBeNull()
    expect(patch?.enabled).toBe(false)
  })

  it('correctly identifies multiple changed fields in one patch', () => {
    const original = makeTimer({ valueInt: 14, enabled: true })
    const edited = { valueInt: 10, valueText: null, recipientRole: 'MANAGER', enabled: false }

    const patch = buildTimerUpdate(original, edited)
    expect(patch?.valueInt).toBe(10)
    expect(patch?.enabled).toBe(false)
  })

  it('handles KPI_CYCLE valueText changes correctly', () => {
    const original = makeTimer({ valueInt: null, valueText: 'QUARTERLY' })
    const edited = { valueInt: null, valueText: 'YEARLY', recipientRole: 'MANAGER', enabled: true }

    const patch = buildTimerUpdate(original, edited)
    expect(patch).not.toBeNull()
    expect(patch?.valueText).toBe('YEARLY')
    // valueInt did not change, should not be in patch
    expect(patch).not.toHaveProperty('valueInt')
  })

  it('D-10: includes the current version in every non-null patch', () => {
    const original = makeTimer({ valueInt: 7, version: 3 })
    const edited = { valueInt: 10, valueText: null, recipientRole: 'MANAGER', enabled: true }

    const patch = buildTimerUpdate(original, edited)
    expect(patch?.version).toBe(3)
  })

  it('correctly identifies 0 changed timers when no edits were made', () => {
    // This simulates the full loop: for N timers, only those with non-null patches need PUTs
    const timerList = [
      makeTimer({ code: 'BENCH_CHECK', valueInt: 7 }),
      makeTimer({ code: 'PIP_CHECKIN', valueInt: 14 }),
      makeTimer({ code: 'PIP_DURATION', valueInt: 90 }),
    ]

    const changedCount = timerList.filter((t) => {
      const edit = { valueInt: t.valueInt, valueText: t.valueText, recipientRole: t.recipientRole, enabled: t.enabled }
      // No edits — same as original
      return buildTimerUpdate(t, edit) !== null
    }).length

    // All unchanged → 0 PUTs needed
    expect(changedCount).toBe(0)
  })

  it('correctly identifies which timers need a PUT when only some are edited', () => {
    const timers = [
      makeTimer({ code: 'BENCH_CHECK', valueInt: 7 }),
      makeTimer({ code: 'PIP_CHECKIN', valueInt: 14 }),
    ]

    // Only BENCH_CHECK is edited
    const edits: Record<string, { valueInt: number | null; valueText: string | null; recipientRole: string | null; enabled: boolean }> = {
      'BENCH_CHECK': { valueInt: 5, valueText: null, recipientRole: 'MANAGER', enabled: true },
    }

    const codesNeedingPut = timers
      .filter((t) => {
        const edit = edits[t.code] ?? { valueInt: t.valueInt, valueText: t.valueText, recipientRole: t.recipientRole, enabled: t.enabled }
        return buildTimerUpdate(t, edit) !== null
      })
      .map((t) => t.code)

    expect(codesNeedingPut).toEqual(['BENCH_CHECK'])
    expect(codesNeedingPut).not.toContain('PIP_CHECKIN')
  })
})
