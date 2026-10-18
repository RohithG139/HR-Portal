/**
 * Component tests for DepartmentsTab.
 *
 * Covered behaviours:
 *   - 409 DUPLICATE_NAME error surfaces as "This name already exists." in the name field
 *   - required field validation shows "This field is required."
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'
import { ApiResponseError } from '../../api/types'
import { DepartmentsTab } from './DepartmentsTab'

// ---------------------------------------------------------------------------
// API mocks — hoisted by Vitest so they apply before module resolution
// ---------------------------------------------------------------------------

vi.mock('../../api/settingsDepartments', () => ({
  listDepartments: vi.fn().mockResolvedValue([]),
  listJobTitles: vi.fn().mockResolvedValue([]),
  createDepartment: vi.fn().mockResolvedValue({ id: 1, name: 'Development', employeeCount: 0 }),
  deleteDepartment: vi.fn().mockResolvedValue(undefined),
  createJobTitle: vi.fn().mockResolvedValue({ id: 1, name: 'Developer' }),
  deleteJobTitle: vi.fn().mockResolvedValue(undefined),
}))

// Import the mocked module so we can override per-test
import * as deptApi from '../../api/settingsDepartments'

// ---------------------------------------------------------------------------
// Test wrapper providing all required React context providers
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
// Tests
// ---------------------------------------------------------------------------

describe('DepartmentsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(deptApi.listDepartments).mockResolvedValue([])
    vi.mocked(deptApi.listJobTitles).mockResolvedValue([])
    vi.mocked(deptApi.createDepartment).mockResolvedValue({
      id: 1,
      name: 'Development',
      employeeCount: 0,
    })
  })

  it('surfaces 409 DUPLICATE_NAME as a field error on the name input', async () => {
    // Arrange: createDepartment rejects with a 409 DUPLICATE_NAME
    vi.mocked(deptApi.createDepartment).mockRejectedValue(
      new ApiResponseError(409, {
        code: 'DUPLICATE_NAME',
        message: 'This name already exists.',
      }),
    )

    render(<DepartmentsTab />, { wrapper: Wrapper })

    // Open the "Add department" modal (button renders quickly without data dependency)
    // The lists may still be loading, but the "+ Add department" button is always rendered
    const addBtns = await screen.findAllByText(/\+ Add department/i)
    fireEvent.click(addBtns[0]!)

    // Fill in a name
    const nameInput = screen.getByRole('textbox', { name: /department name/i })
    fireEvent.change(nameInput, { target: { value: 'Development' } })

    // Submit — finds the modal's confirm button (not the page-level "Add department" button)
    // The modal's button has text "Add department" (without the + prefix)
    const buttons = screen.getAllByRole('button', { name: /add department/i })
    // The last one in DOM order is the modal's submit button
    const submitBtn = buttons[buttons.length - 1]!
    fireEvent.click(submitBtn)

    // Assert: exact spec message appears (R-SRC-3)
    await waitFor(() => {
      expect(screen.getByText('This name already exists.')).toBeTruthy()
    })
  })

  it('shows required field validation when name is empty', async () => {
    render(<DepartmentsTab />, { wrapper: Wrapper })

    const addBtns = await screen.findAllByText(/\+ Add department/i)
    fireEvent.click(addBtns[0]!)

    // Submit without filling a name — client-side validation fires
    const buttons = screen.getAllByRole('button', { name: /add department/i })
    const submitBtn = buttons[buttons.length - 1]!
    fireEvent.click(submitBtn)

    // "This field is required." must appear (spec §5 S1, R-SRC-3)
    expect(screen.getByText('This field is required.')).toBeTruthy()
  })
})
