/**
 * Component tests for TransferManagerModal (W6, FRD-01 Wave 3).
 *
 * Covered behaviours:
 *   - AC-W04: "Choose a new manager." appears when Transfer is clicked without selecting a manager.
 *   - AC-W05: "The effective date cannot be in the past." appears when a past date is entered.
 *   - Exact validation messages per R-SRC-3.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'

// ---------------------------------------------------------------------------
// Auth context mock
// ---------------------------------------------------------------------------

vi.mock('../../app/auth/AuthContext', () => ({
  useAuth: () => ({
    user: {
      employeeId: 99,
      name: 'HR User',
      initials: 'HU',
      appRole: 'HR',
      menu: [],
    },
    logout: vi.fn(),
  }),
}))

// ---------------------------------------------------------------------------
// API mocks
// ---------------------------------------------------------------------------

vi.mock('../../api/managerTransfers', () => ({
  getManagerTasks: vi.fn().mockResolvedValue({
    openManagerTasks: [
      { id: 201, taskName: 'Approve probation review', dueDate: '2026-11-01' },
      { id: 202, taskName: 'Sign off development plan', dueDate: null },
    ],
  }),
  createManagerTransfer: vi.fn().mockResolvedValue({ changeId: 42 }),
}))

vi.mock('../../api/lookups', () => ({
  getOnboardingLookups: vi.fn().mockResolvedValue({
    departments: [],
    jobTitles: [],
    managers: [
      { id: 5, name: 'Anouk Peters' },
      { id: 6, name: 'Mark Smit' },
    ],
    buddies: [],
    grades: [],
    appRoles: [],
    templates: [],
  }),
}))

import * as transferApi from '../../api/managerTransfers'
import { TransferManagerModal } from './TransferManagerModal'

// ---------------------------------------------------------------------------
// Test wrapper
// ---------------------------------------------------------------------------

function Wrapper({ children }: { children: ReactNode }): ReactNode {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0, gcTime: 0 },
    },
  })
  return (
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>{children}</ToastProvider>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

// ---------------------------------------------------------------------------
// Default props for the modal
// ---------------------------------------------------------------------------

const defaultProps = {
  employeeId: 10,
  employeeName: 'Lotte Jansen',
  currentManager: 'Mark Smit',
  isOpen: true,
  onClose: vi.fn(),
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('TransferManagerModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the modal with pre-filled Employee and Current manager fields', async () => {
    render(<TransferManagerModal {...defaultProps} />, { wrapper: Wrapper })

    // Read-only employee name is pre-filled
    expect(screen.getByDisplayValue('Lotte Jansen')).toBeTruthy()
    // Read-only current manager is pre-filled
    expect(screen.getByDisplayValue('Mark Smit')).toBeTruthy()
  })

  it('AC-W04: shows "Choose a new manager." when Transfer is clicked without a manager selected', async () => {
    render(<TransferManagerModal {...defaultProps} />, { wrapper: Wrapper })

    // Click Transfer without selecting a manager or date
    const transferBtn = screen.getByRole('button', { name: /^transfer$/i })
    fireEvent.click(transferBtn)

    // Exact message from spec §5 W6 (R-SRC-3)
    expect(screen.getByText('Choose a new manager.')).toBeTruthy()

    // The API should NOT have been called
    expect(vi.mocked(transferApi.createManagerTransfer)).not.toHaveBeenCalled()
  })

  it('AC-W05: shows "The effective date cannot be in the past." for a past date', async () => {
    render(<TransferManagerModal {...defaultProps} />, { wrapper: Wrapper })

    // Wait for manager options to load
    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: /new manager/i })).toBeTruthy()
    })

    // Select a manager
    const managerSelect = screen.getByRole('combobox', { name: /new manager/i })
    fireEvent.change(managerSelect, { target: { value: '5' } })

    // Enter a past date (2020-01-01)
    const dateInput = screen.getByLabelText(/effective date/i)
    fireEvent.change(dateInput, { target: { value: '2020-01-01' } })

    // Click Transfer
    const transferBtn = screen.getByRole('button', { name: /^transfer$/i })
    fireEvent.click(transferBtn)

    // Exact message from spec §5 W6 (R-SRC-3)
    expect(screen.getByText('The effective date cannot be in the past.')).toBeTruthy()

    // The API should NOT have been called
    expect(vi.mocked(transferApi.createManagerTransfer)).not.toHaveBeenCalled()
  })

  it('renders open manager tasks as checkboxes in the tick-list', async () => {
    render(<TransferManagerModal {...defaultProps} />, { wrapper: Wrapper })

    // Wait for the tasks to load (W09)
    await waitFor(() => {
      expect(screen.getByText('Approve probation review')).toBeTruthy()
    })
    expect(screen.getByText('Sign off development plan')).toBeTruthy()
  })

  it('calls onClose when Cancel is clicked', () => {
    const onClose = vi.fn()
    render(
      <TransferManagerModal {...defaultProps} onClose={onClose} />,
      { wrapper: Wrapper },
    )

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onClose).toHaveBeenCalled()
  })
})
