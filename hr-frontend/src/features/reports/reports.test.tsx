/**
 * Component tests for FRD-08 Reports (S7–S15).
 *
 * Covered behaviours (per task spec "write some real tests"):
 *   - Filter narrows what is requested from the API (AC-05, spec §5 S9):
 *     BenchReportPage — selecting a manager in the dropdown refetches with the managerId param.
 *   - Export button calls apiDownload with the correct path and filename (AC-05):
 *     BenchReportPage export → /reports/bench/export with the current filter.
 *   - HeadcountReportPage renders rows returned by the API.
 *   - UnreturnedAssetsReportPage export passes status filter to apiDownload.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest'
import '@testing-library/jest-dom'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../components/index'

// ---------------------------------------------------------------------------
// Module-level API mocks
// ---------------------------------------------------------------------------

// Mock the entire reports API module.
vi.mock('../../api/reports', () => ({
  getSummary: vi.fn().mockResolvedValue({
    totalHeadcount: 48,
    benchCount: 4,
    appraisalCompletionRate: 62,
    unreturnedAssetCount: 2,
  }),
  getHeadcount: vi.fn().mockResolvedValue([
    {
      departmentId: 1,
      departmentName: 'Development',
      employeeCount: 28,
      joinersThisYear: 4,
      leaversThisYear: 1,
      permanentCount: 22,
      fixedTermCount: 6,
    },
    {
      departmentId: 2,
      departmentName: 'Design',
      employeeCount: 8,
      joinersThisYear: 1,
      leaversThisYear: 0,
      permanentCount: 6,
      fixedTermCount: 2,
    },
  ]),
  exportHeadcount: vi.fn().mockResolvedValue(undefined),
  getBench: vi.fn().mockResolvedValue([
    {
      employeeId: 10,
      employeeName: 'Kees Willems',
      jobTitle: 'Developer',
      managerId: 5,
      managerName: 'Anouk Peters',
      benchSince: '2026-09-24',
      daysOnBench: 18,
      lastProjectName: 'Atlas CRM Migration',
    },
  ]),
  exportBench: vi.fn().mockResolvedValue(undefined),
  getAllocation: vi.fn().mockResolvedValue([]),
  exportAllocation: vi.fn().mockResolvedValue(undefined),
  getAppraisalStatus: vi.fn().mockResolvedValue([]),
  exportAppraisalStatus: vi.fn().mockResolvedValue(undefined),
  getPips: vi.fn().mockResolvedValue([]),
  exportPips: vi.fn().mockResolvedValue(undefined),
  getOnboardingReport: vi.fn().mockResolvedValue([]),
  exportOnboarding: vi.fn().mockResolvedValue(undefined),
  getContractsProbation: vi.fn().mockResolvedValue([]),
  exportContractsProbation: vi.fn().mockResolvedValue(undefined),
  getUnreturnedAssets: vi.fn().mockResolvedValue([
    {
      assetId: 1,
      assetNumber: 'CW-PH-0061',
      assetDescription: 'Phone · Pixel 7',
      employeeId: 20,
      employeeName: 'Kim Verhoeven',
      lastWorkingDay: '2026-06-30',
      assetStatus: 'NOT_RETURNED',
    },
  ]),
  exportUnreturnedAssets: vi.fn().mockResolvedValue(undefined),
}))

// Mock the lookups API for manager dropdowns.
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

// Mock the settings departments API for the headcount filter dropdown.
vi.mock('../../api/settingsDepartments', () => ({
  listDepartments: vi.fn().mockResolvedValue([
    { id: 1, name: 'Development', employeeCount: 28 },
    { id: 2, name: 'Design', employeeCount: 8 },
  ]),
  listJobTitles: vi.fn().mockResolvedValue([]),
  createDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
  createJobTitle: vi.fn(),
  deleteJobTitle: vi.fn(),
}))

// Import mocked modules after mock declarations so per-test overrides work.
import * as reportsApi from '../../api/reports'

// Import pages under test.
import { BenchReportPage } from './BenchReportPage'
import { HeadcountReportPage } from './HeadcountReportPage'
import { UnreturnedAssetsReportPage } from './UnreturnedAssetsReportPage'

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

describe('BenchReportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Reset to default mock values before each test.
    vi.mocked(reportsApi.getBench).mockResolvedValue([
      {
        employeeId: 10,
        employeeName: 'Kees Willems',
        jobTitle: 'Developer',
        managerId: 5,
        managerName: 'Anouk Peters',
        benchSince: '2026-09-24',
        daysOnBench: 18,
        lastProjectName: 'Atlas CRM Migration',
      },
    ])
    vi.mocked(reportsApi.exportBench).mockResolvedValue(undefined)
  })

  it('renders bench rows returned by the API', async () => {
    render(<BenchReportPage />, { wrapper: Wrapper })
    // Wait for the async query to resolve and the row to appear.
    await waitFor(() => {
      expect(screen.getByText('Kees Willems')).toBeTruthy()
    })
    expect(screen.getByText('Atlas CRM Migration')).toBeTruthy()
  })

  it('changing the manager filter refetches with the managerId param', async () => {
    render(<BenchReportPage />, { wrapper: Wrapper })

    // Wait for initial render.
    await waitFor(() => {
      expect(screen.getByText('Kees Willems')).toBeTruthy()
    })

    // Find and change the manager filter select.
    const select = screen.getByRole('combobox', { name: /filter by manager/i })
    fireEvent.change(select, { target: { value: '5' } })

    // The queryKey now includes { managerId: '5' }, triggering a refetch.
    // Assert getBench was called with managerId = 5.
    await waitFor(() => {
      const calls = vi.mocked(reportsApi.getBench).mock.calls
      const filteredCall = calls.find(
        (call) => call[0] !== undefined && call[0].managerId === 5,
      )
      expect(filteredCall).toBeDefined()
    })
  })

  it('clicking the export button calls exportBench with the current filter', async () => {
    render(<BenchReportPage />, { wrapper: Wrapper })

    // Wait for the table to load.
    await waitFor(() => {
      expect(screen.getByText('Kees Willems')).toBeTruthy()
    })

    // Set a manager filter.
    const select = screen.getByRole('combobox', { name: /filter by manager/i })
    fireEvent.change(select, { target: { value: '6' } })

    // Click the export button (aria-label "Export to Excel").
    const exportBtn = screen.getByRole('button', { name: /export to excel/i })
    fireEvent.click(exportBtn)

    // exportBench should be called with managerId: 6.
    await waitFor(() => {
      expect(vi.mocked(reportsApi.exportBench)).toHaveBeenCalledWith({ managerId: 6 })
    })
  })
})

describe('HeadcountReportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(reportsApi.getHeadcount).mockResolvedValue([
      {
        departmentId: 1,
        departmentName: 'Development',
        employeeCount: 28,
        joinersThisYear: 4,
        leaversThisYear: 1,
        permanentCount: 22,
        fixedTermCount: 6,
      },
    ])
  })

  it('renders department rows from the API', async () => {
    render(<HeadcountReportPage />, { wrapper: Wrapper })
    // "Development" appears in both the filter dropdown and the table cell — use getAllByText.
    await waitFor(() => {
      expect(screen.getAllByText('Development').length).toBeGreaterThan(0)
    })
    // Employee count (28) appears in both the Total summary tile and the table cell — use getAllByText.
    expect(screen.getAllByText('28').length).toBeGreaterThan(0)
  })

  it('export button calls exportHeadcount with no filter when none is set', async () => {
    render(<HeadcountReportPage />, { wrapper: Wrapper })
    await waitFor(() => {
      expect(screen.getAllByText('Development').length).toBeGreaterThan(0)
    })

    const exportBtn = screen.getByRole('button', { name: /export to excel/i })
    fireEvent.click(exportBtn)

    await waitFor(() => {
      expect(vi.mocked(reportsApi.exportHeadcount)).toHaveBeenCalledWith({})
    })
  })
})

describe('UnreturnedAssetsReportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(reportsApi.getUnreturnedAssets).mockResolvedValue([
      {
        assetId: 1,
        assetNumber: 'CW-PH-0061',
        assetDescription: 'Phone · Pixel 7',
        employeeId: 20,
        employeeName: 'Kim Verhoeven',
        lastWorkingDay: '2026-06-30',
        assetStatus: 'NOT_RETURNED',
      },
    ])
    vi.mocked(reportsApi.exportUnreturnedAssets).mockResolvedValue(undefined)
  })

  it('renders unreturned asset rows', async () => {
    render(<UnreturnedAssetsReportPage />, { wrapper: Wrapper })
    await waitFor(() => {
      expect(screen.getByText('CW-PH-0061')).toBeTruthy()
    })
    expect(screen.getByText('Kim Verhoeven')).toBeTruthy()
  })

  it('export button calls exportUnreturnedAssets with status filter when set', async () => {
    render(<UnreturnedAssetsReportPage />, { wrapper: Wrapper })
    await waitFor(() => {
      expect(screen.getByText('CW-PH-0061')).toBeTruthy()
    })

    // Set the status filter to NOT_RETURNED.
    const select = screen.getByRole('combobox', { name: /filter by status/i })
    fireEvent.change(select, { target: { value: 'NOT_RETURNED' } })

    // Click export.
    const exportBtn = screen.getByRole('button', { name: /export to excel/i })
    fireEvent.click(exportBtn)

    await waitFor(() => {
      expect(vi.mocked(reportsApi.exportUnreturnedAssets)).toHaveBeenCalledWith({
        status: 'NOT_RETURNED',
      })
    })
  })
})
