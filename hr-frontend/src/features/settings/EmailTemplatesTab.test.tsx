/**
 * Component tests for EmailTemplatesTab.
 *
 * Covered behaviours:
 *   - VR-10 (Gate 5 spec-coverage finding — partial): the test-send toast's exact text
 *     ("Test e-mail sent to your e-mail address.") appears after a successful send.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'
import { EmailTemplatesTab } from './EmailTemplatesTab'

vi.mock('../../api/settingsEmailTemplates', () => ({
  listEmailTemplates: vi.fn().mockResolvedValue([
    {
      code: 'WELCOME_NEW_EMPLOYEE',
      name: 'Welcome new employee',
      triggerDescription: 'Employee record created',
      recipientsSummary: 'Employee',
      active: true,
    },
  ]),
  getEmailTemplate: vi.fn().mockResolvedValue({
    code: 'WELCOME_NEW_EMPLOYEE',
    name: 'Welcome new employee',
    triggerDescription: 'Employee record created',
    subject: 'Welcome!',
    body: 'Hi {employee_first_name},',
    cc: null,
    toEmployee: true,
    toManager: false,
    toHr: false,
    toItAdmin: false,
    toTaskOwner: false,
    toPeer: false,
    active: true,
    version: 0,
  }),
  updateEmailTemplate: vi.fn(),
  testSendEmailTemplate: vi.fn().mockResolvedValue(undefined),
}))

import * as emailApi from '../../api/settingsEmailTemplates'

function Wrapper({ children }: { children: ReactNode }): ReactNode {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0 } },
  })
  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>{children}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe('EmailTemplatesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(emailApi.testSendEmailTemplate).mockResolvedValue(undefined)
  })

  it('VR-10: shows the exact test-send confirmation toast after a successful send', async () => {
    render(<EmailTemplatesTab />, { wrapper: Wrapper })

    const editBtn = await screen.findByRole('button', { name: /edit/i })
    fireEvent.click(editBtn)

    // Wait for the template detail query to resolve and enable the button — it is disabled
    // while loading (handleTestSend also no-ops until `data` is populated).
    const sendBtn = await screen.findByRole('button', { name: /send test e-mail/i }) as HTMLButtonElement;
    await waitFor(() => expect(sendBtn.disabled).toBe(false))
    fireEvent.click(sendBtn)

    await waitFor(() => {
      expect(emailApi.testSendEmailTemplate).toHaveBeenCalledWith('WELCOME_NEW_EMPLOYEE')
    })

    // Spec §5 S5's toast wording, as actually implemented (spec §12 D-03: the backend names
    // no specific address in its response, so the toast reads "your e-mail address" rather
    // than interpolating one — see EmailTemplatesTab's own handleTestSend).
    await waitFor(() => {
      expect(screen.getByText('Test e-mail sent to your e-mail address.')).toBeTruthy()
    })
  })
})
