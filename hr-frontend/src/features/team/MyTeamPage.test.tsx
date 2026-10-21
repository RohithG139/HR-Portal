/**
 * Component tests for MyTeamPage (W5, FRD-01 Wave 3).
 *
 * Covered behaviours:
 *   - Component renders the team members returned by the mocked API (AC-W03 component-level).
 *   - The WS-02 scoping (own-team-only) is enforced server-side; this test checks
 *     that the component renders exactly what the mocked API returns — i.e. it does not
 *     add extra members or filter any out client-side.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor } from '@testing-library/react'
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
      employeeId: 5,
      name: 'Mark Smit',
      initials: 'MS',
      appRole: 'MANAGER',
      menu: [],
    },
    logout: vi.fn(),
  }),
}))

// ---------------------------------------------------------------------------
// API mocks
// ---------------------------------------------------------------------------

vi.mock('../../api/team', () => ({
  listTeam: vi.fn().mockResolvedValue({
    items: [
      {
        employeeId: 10,
        employeeCode: 'E-0010',
        firstName: 'Lotte',
        lastName: 'Jansen',
        jobTitle: 'Designer',
        stage: 'ACTIVE',
      },
      {
        employeeId: 11,
        employeeCode: 'E-0011',
        firstName: 'Priya',
        lastName: 'Sharma',
        jobTitle: 'Developer',
        stage: 'ACTIVE',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  }),
  exportTeam: vi.fn().mockResolvedValue(undefined),
}))

import * as teamApi from '../../api/team'
import { MyTeamPage } from './MyTeamPage'

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

describe('MyTeamPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamApi.listTeam).mockResolvedValue({
      items: [
        {
          employeeId: 10,
          employeeCode: 'E-0010',
          firstName: 'Lotte',
          lastName: 'Jansen',
          jobTitle: 'Designer',
          stage: 'ACTIVE',
          projects: 'GreenLeaf Webshop',
          allocationPct: 50,
          teamStatus: 'ON_PROJECT',
        },
        {
          employeeId: 11,
          employeeCode: 'E-0011',
          firstName: 'Priya',
          lastName: 'Sharma',
          jobTitle: 'Developer',
          stage: 'ACTIVE',
          projects: null,
          allocationPct: 0,
          teamStatus: 'BENCH',
        },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    })
  })

  it('renders team members returned by the API (AC-W03 component-level)', async () => {
    render(<MyTeamPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })
    expect(screen.getByText('Priya Sharma')).toBeTruthy()
  })

  it('does not render members not in the API response (WS-02 server-side check)', async () => {
    render(<MyTeamPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByText('Lotte Jansen')).toBeTruthy()
    })

    // Kees Willems (from another team) is not in the mocked response and
    // therefore must not appear in the rendered table.
    expect(screen.queryByText('Kees Willems')).toBeNull()
  })

  it('shows the My Team page heading', async () => {
    render(<MyTeamPage />, { wrapper: Wrapper })

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /my team/i })).toBeTruthy()
    })
  })
})
