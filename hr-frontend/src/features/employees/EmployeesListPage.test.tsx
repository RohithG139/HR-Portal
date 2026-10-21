/**
 * Component tests for EmployeesListPage (S1 + D-W9, FRD-01 Wave 3).
 *
 * Covered behaviours:
 *   - AC-W08: IT admin sees Employee, Job title, Status and Assets columns only,
 *     with no row action to open the full record (no "View" link).
 *   - HR sees the full column set including an Action column with "View" links.
 *   - "Read-only" subtitle is shown for IT admin.
 *   - "+ New Employee" button is hidden for IT admin but visible for HR.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'

// ---------------------------------------------------------------------------
// Auth context mock — mutable role
// ---------------------------------------------------------------------------

let mockRole = 'HR'

vi.mock('../../app/auth/AuthContext', () => ({
  useAuth: () => ({
    user: {
      employeeId: 1,
      name: 'Test User',
      initials: 'TU',
      appRole: mockRole,
      menu: [],
    },
    logout: vi.fn(),
  }),
}))

// ---------------------------------------------------------------------------
// API mock — inline data in factory to avoid hoisting issues
// ---------------------------------------------------------------------------

vi.mock('../../api/employees', () => ({
  listEmployees: vi.fn().mockResolvedValue({
    items: [
      {
        id: 10,
        draftId: null,
        firstName: 'Lotte',
        lastName: 'Jansen',
        jobTitle: 'Designer',
        department: 'Design',
        managerName: 'Mark Smit',
        stage: 'ACTIVE',
        employeeCode: 'E-0010',
        accountStatus: 'ACTIVE',
        draft: false,
        assetCount: 2,
      },
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  }),
  exportEmployees: vi.fn().mockResolvedValue(undefined),
}))

import * as employeesApi from '../../api/employees'
import { EmployeesListPage } from './EmployeesListPage'

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
// Tests
// ---------------------------------------------------------------------------

describe('EmployeesListPage — IT admin variant (AC-W08)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'IT_ADMIN'
    vi.mocked(employeesApi.listEmployees).mockResolvedValue({
      items: [
        {
          id: 10,
          draftId: null,
          firstName: 'Lotte',
          lastName: 'Jansen',
          jobTitle: 'Designer',
          department: 'Design',
          managerName: 'Mark Smit',
          stage: 'ACTIVE',
          employeeCode: 'E-0010',
          accountStatus: 'ACTIVE',
          draft: false,
          email: null,
          assetCount: 2,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    })
  })

  it('shows Assets column header for IT admin (AC-W08)', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByRole('columnheader', { name: /assets/i })).toBeTruthy()
    })
  })

  it('does NOT show Action/View column for IT admin (AC-W08)', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })

    expect(screen.queryByRole('link', { name: /^view$/i })).toBeNull()
  })

  it('shows the Assets count from assetCount field', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })
    expect(screen.getByText('2')).toBeTruthy()
  })

  it('shows "Read-only" subtitle for IT admin', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Read-only')).toBeTruthy()
    })
  })

  it('does NOT show the "+ New Employee" button for IT admin', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })
    expect(screen.queryByRole('button', { name: /\+ new employee/i })).toBeNull()
  })
})

describe('EmployeesListPage — HR variant (AC-W08)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'HR'
    vi.mocked(employeesApi.listEmployees).mockResolvedValue({
      items: [
        {
          id: 10,
          draftId: null,
          firstName: 'Lotte',
          lastName: 'Jansen',
          jobTitle: 'Designer',
          department: 'Design',
          managerName: 'Mark Smit',
          stage: 'ACTIVE',
          employeeCode: 'E-0010',
          accountStatus: 'ACTIVE',
          draft: false,
          email: null,
          assetCount: null,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    })
  })

  it('shows the "View" action link for HR (AC-W08 HR side)', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })
    expect(screen.getByRole('link', { name: /view/i })).toBeTruthy()
  })

  it('shows the "+ New Employee" button for HR', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /\+ new employee/i })).toBeTruthy()
    })
  })

  it('does NOT show the "Read-only" subtitle for HR', async () => {
    render(<EmployeesListPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })
    expect(screen.queryByText('Read-only')).toBeNull()
  })
})
