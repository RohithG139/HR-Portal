/**
 * Component tests for the role-dispatched DashboardStub/DashboardPage (W1–W4, FRD-01 Wave 3).
 *
 * Covered behaviours:
 *   - AC-W01: Manager's dashboard shows team-related cards (WS-01).
 *   - AC-W07: IT admin dashboard shows Equipment requests, Open setup items, My Tasks cards,
 *     and no HR-only or Manager-only cards.
 *   - HR dashboard shows active employee count and onboarding count.
 *   - Employee role does not call getDashboard (redirects instead).
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'

// ---------------------------------------------------------------------------
// Auth context mock — mutable role updated before each test
// ---------------------------------------------------------------------------

// The let declaration is fine here; the vi.mock factory is hoisted but reads
// the variable at call time, not at hoist time.
let mockRole = 'HR'
let mockStage = 'ACTIVE'

vi.mock('../../app/auth/AuthContext', () => ({
  useAuth: () => ({
    user: {
      employeeId: 1,
      name: 'Test User',
      initials: 'TU',
      appRole: mockRole,
      stage: mockStage,
      menu: [],
    },
    logout: vi.fn(),
  }),
}))

// ---------------------------------------------------------------------------
// Dashboard API mock — default returns HR data; overridden per-test in beforeEach
// ---------------------------------------------------------------------------

vi.mock('../../api/dashboard', () => ({
  getDashboard: vi.fn().mockResolvedValue({
    activeEmployees: 42,
    activeEmployeesDelta: 3,
    onboardingCount: 2,
    onboardingNames: ['Lotte Jansen', 'Kees Willems'],
    tasks: [],
  }),
}))

import * as dashboardApi from '../../api/dashboard'
import { DashboardStub } from './DashboardStub'

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

describe('DashboardStub — HR role (W1)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'HR'
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue({
      activeEmployees: 42,
      activeEmployeesDelta: 3,
      onboardingCount: 2,
      onboardingNames: ['Lotte Jansen', 'Kees Willems'],
      tasks: [],
    })
  })

  it('renders active employee count and onboarding count cards', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('42')).toBeTruthy()
    })
    expect(screen.getByText('Active employees')).toBeTruthy()
    expect(screen.getByText('2')).toBeTruthy()
    expect(screen.getByText('In onboarding')).toBeTruthy()
  })

  it('shows the delta as "+3 this month"', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('+3 this month')).toBeTruthy()
    })
  })
})

describe('DashboardStub — Manager role (W2, AC-W01)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'MANAGER'
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue({
      teamMembers: 5,
      tasks: [
        {
          taskId: 101,
          title: 'Review probation: Lotte Jansen',
          employeeId: 10,
          dueDate: '2026-11-01',
          dueState: 'UPCOMING',
        },
      ],
      myTeam: [
        {
          employeeId: 10,
          employeeCode: 'E-0010',
          firstName: 'Lotte',
          lastName: 'Jansen',
          jobTitle: 'Designer',
          stage: 'ACTIVE',
        },
      ],
    })
  })

  it('renders team members count and My Team section (AC-W01)', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('5')).toBeTruthy()
    })
    expect(screen.getByText('Team members')).toBeTruthy()
    expect(screen.getByText('My Team')).toBeTruthy()
    expect(screen.getByText('Lotte Jansen')).toBeTruthy()
  })

  it('renders My Tasks section with task name (AC-W01)', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Review probation: Lotte Jansen')).toBeTruthy()
    })
  })
})

describe('DashboardStub — IT admin role (W4, AC-W07)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'IT_ADMIN'
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue({
      equipmentRequestsCount: 3,
      openSetupItemsCount: 7,
      tasks: [],
    })
  })

  it('shows Equipment requests and Open setup items cards (AC-W07)', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Equipment requests')).toBeTruthy()
    })
    expect(screen.getByText('3')).toBeTruthy()
    expect(screen.getByText('Open setup items')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
  })

  it('does NOT show HR-only or Manager-only content (AC-W07)', async () => {
    render(<DashboardStub />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Equipment requests')).toBeTruthy()
    })

    expect(screen.queryByText('Active employees')).toBeNull()
    expect(screen.queryByText('In onboarding')).toBeNull()
    expect(screen.queryByText('Team members')).toBeNull()
    expect(screen.queryByText('My Team')).toBeNull()
  })
})

describe('DashboardStub — Employee role', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRole = 'EMPLOYEE'
    vi.mocked(dashboardApi.getDashboard).mockResolvedValue({
      equipment: [],
      tasks: [],
    })
  })

  it('ONBOARDING stage: does NOT call getDashboard (query disabled, redirects to the new-joiner dashboard instead)', () => {
    mockStage = 'ONBOARDING'
    render(<DashboardStub />, { wrapper: Wrapper })

    // Query is disabled while stage is ONBOARDING; the component redirects immediately
    // to F01's own /dashboard/my-onboarding, unchanged by this wave.
    expect(vi.mocked(dashboardApi.getDashboard)).not.toHaveBeenCalled()
  })

  it('ACTIVE stage: calls getDashboard and renders the EmployeeDashboard (Gate 4 routing fix)', async () => {
    mockStage = 'ACTIVE'
    render(<DashboardStub />, { wrapper: Wrapper })

    // Unlike ONBOARDING, an ACTIVE-stage employee is NOT redirected — this is the
    // fix for the gap where EmployeeDashboard was built but unreachable from any route.
    await waitFor(() => {
      expect(vi.mocked(dashboardApi.getDashboard)).toHaveBeenCalled()
    })
  })
})
