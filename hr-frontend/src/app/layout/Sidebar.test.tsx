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

import { Sidebar } from './Sidebar'

function renderSidebar(): void {
  render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>
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

  it('renders Dashboard, My Onboarding and My Profile, and no HR/Manager-only links', () => {
    renderSidebar()
    expect(screen.getByText('Dashboard')).toBeTruthy()
    expect(screen.getByText('My Onboarding')).toBeTruthy()
    expect(screen.getByText('My Profile')).toBeTruthy()
    expect(screen.queryByText('Employees')).toBeNull()
    expect(screen.queryByText('My Team')).toBeNull()
  })
})
