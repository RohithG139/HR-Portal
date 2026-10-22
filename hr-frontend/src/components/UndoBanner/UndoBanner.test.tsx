/**
 * Component tests for UndoBanner (R-API-6 / G-03 undo affordance, AC-23).
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastProvider } from '../Toast/ToastContext'

vi.mock('../../api/tasks', () => ({
  undoTask: vi.fn().mockResolvedValue(undefined),
}))

import * as tasksApi from '../../api/tasks'
import { UndoBanner } from './UndoBanner'

function Wrapper({ children }: { children: ReactNode }): ReactNode {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  )
}

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('UndoBanner', () => {
  it('calls A37 undo with the engine task id and reports success', async () => {
    const onUndone = vi.fn()
    const onExpired = vi.fn()
    const until = new Date(Date.now() + 10_000).toISOString()
    render(
      <UndoBanner taskId={77} undoUntil={until} message="Onboarding completed." onUndone={onUndone} onExpired={onExpired} />,
      { wrapper: Wrapper },
    )

    expect(screen.getByText(/You can undo until \d{2}-\d{2}-\d{4} \d{2}:\d{2}:\d{2}/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Undo' }))

    await waitFor(() => expect(onUndone).toHaveBeenCalled())
    expect(vi.mocked(tasksApi.undoTask)).toHaveBeenCalledWith(77)
    expect(onExpired).not.toHaveBeenCalled()
  })

  it('calls onExpired once the window has ended and disables Undo', () => {
    vi.useFakeTimers()
    const onExpired = vi.fn()
    const until = new Date(Date.now() + 2_000).toISOString()
    render(
      <UndoBanner taskId={5} undoUntil={until} message="Setup marked done." onUndone={vi.fn()} onExpired={onExpired} />,
      { wrapper: Wrapper },
    )
    expect(onExpired).not.toHaveBeenCalled()

    act(() => { vi.advanceTimersByTime(3_000) })

    expect(onExpired).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Undo' }).hasAttribute('disabled')).toBe(true)
  })
})
