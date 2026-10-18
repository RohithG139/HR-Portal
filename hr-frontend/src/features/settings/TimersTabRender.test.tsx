/**
 * Render test for TimersTab (companion to TimersTab.test.ts, which covers the pure
 * save-logic helpers without rendering).
 *
 * Covered behaviour:
 *   - AC-15 (Gate 5 spec-coverage finding — partial): "Bench check"'s rule name and module
 *     are rendered as plain text, never as an input element (ADM-01).
 */

import { describe, expect, it, vi } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '../../components/index'
import { TimersTab } from './TimersTab'

vi.mock('../../api/settingsTimers', () => ({
  listTimers: vi.fn().mockResolvedValue([
    {
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
    },
  ]),
  updateTimer: vi.fn(),
}))

function Wrapper({ children }: { children: ReactNode }): ReactNode {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0 } },
  })
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  )
}

describe('TimersTab rendering', () => {
  it('AC-15: renders the rule name and module as text, not as input elements', async () => {
    render(<TimersTab />, { wrapper: Wrapper })

    const label = await screen.findByText('Bench check')
    const module = await screen.findByText('Work cycle')

    // Both must be plain text nodes (ADM-01) — not form controls a user could type into.
    expect(label.tagName).not.toBe('INPUT')
    expect(module.tagName).not.toBe('INPUT')
    expect(screen.queryByDisplayValue('Bench check')).toBeNull()
    expect(screen.queryByDisplayValue('Work cycle')).toBeNull()
  })
})
