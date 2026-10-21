/**
 * Component tests for the role-filtered Sidebar navigation (R-UI-7).
 *
 * Covered behaviour:
 *   - AC-W01: Manager's sidebar shows My Tasks + My Team, and does NOT show the
 *     HR-only Employees/Settings/Reports links (FRD-01 Wave 3, spec-coverage EC-1).
 *   - Other roles keep their existing F01/FRD-08 nav items, confirming the MANAGER
 *     addition did not regress role filtering for HR/EMPLOYEE/IT_ADMIN.
 *   - Dashboard nav item (reinstated 2026-10-18): every role sees it, routing to "/".
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

let mockRole = 'MANAGER'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: {
      employeeId: 1,
      name: 'Test User',
      initials: 'TU',
      appRole: mockRole,
      stage: 'ACTIVE',
      menu: [],
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../../api/tasks', () => ({
  getTaskCount: vi.fn().mockResolvedValue({ count: 6 }),
}))

import { Sidebar } from './Sidebar'

function renderSidebar(): void {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('Sidebar — Manager role (AC-W01, FRD-01 Wave 3)', () => {
  beforeEach(() => {
    mockRole = 'MANAGER'
  })

  it('renders Dashboard, My Tasks and My Team', () => {
    renderSidebar()
    expect(screen.getByText('Dashboard')).toBeTruthy()
    expect(screen.getByText('My Tasks')).toBeTruthy()
    expect(screen.getByText('My Team')).toBeTruthy()
  })

  it('does not render the HR-only Employees, Settings or Reports links', () => {
    renderSidebar()
    expect(screen.queryByText('Employees')).toBeNull()
    expect(screen.queryByText('Settings')).toBeNull()
    expect(screen.queryByText('Reports')).toBeNull()
  })
})

describe('Sidebar — HR role', () => {
  beforeEach(() => {
    mockRole = 'HR'
  })

  it('renders Dashboard, Employees, Settings and Reports, and not My Team', () => {
    renderSidebar()
    expect(screen.getByText('Dashboard')).toBeTruthy()
    expect(screen.getByText('Employees')).toBeTruthy()
    expect(screen.getByText('Settings')).toBeTruthy()
    expect(screen.getByText('Reports')).toBeTruthy()
    expect(screen.queryByText('My Team')).toBeNull()
  })
})

describe('Sidebar — IT admin role', () => {
  beforeEach(() => {
    mockRole = 'IT_ADMIN'
  })

  it('renders Dashboard and Employees (D-W9 reduced view) and not My Team or Settings', () => {
    renderSidebar()
    expect(screen.getByText('Dashboard')).toBeTruthy()
    expect(screen.getByText('Employees')).toBeTruthy()
    expect(screen.queryByText('My Team')).toBeNull()
    expect(screen.queryByText('Settings')).toBeNull()
  })
})

describe('Sidebar — Employee role', () => {
  beforeEach(() => {
    mockRole = 'EMPLOYEE'
  })

  it('renders Dashboard and My Profile, no separate My Onboarding, and no HR/Manager-only links', () => {
    renderSidebar()
    expect(screen.getByText('Dashboard')).toBeTruthy()
    expect(screen.queryByText('My Onboarding')).toBeNull()
    expect(screen.getByText('My Profile')).toBeTruthy()
    expect(screen.queryByText('Employees')).toBeNull()
    expect(screen.queryByText('My Team')).toBeNull()
  })
})

describe('Sidebar — My Tasks badge', () => {
  beforeEach(() => {
    mockRole = 'HR'
  })

  it('shows the open-task count next to My Tasks', async () => {
    renderSidebar()
    expect((await screen.findByLabelText('6 open')).textContent).toBe('6')
  })
})
