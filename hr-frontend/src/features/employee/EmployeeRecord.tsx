/**
 * @component EmployeeRecord
 * @description S11 — Employee record container (`p-hr-case-jesse`, `p-mg-case-jesse`).
 * Renders five tabs: Overview, Profile, Projects (placeholder), Development & reviews
 * (placeholder), History. History tab is HR-only. Manager sees Overview, Profile, Projects,
 * Development & reviews (read-only). Projects and Development & reviews tabs are placeholders
 * per D-W1 (FRD-01 Wave 3) — built now as visible nav items, content delivered by F04/F05/F06.
 * @props (none — reads employeeId from route params, role from AuthContext)
 * @example
 * Route: { path: '/employees/:employeeId', element: <EmployeeRecord /> }
 */

import { useState } from 'react'
import type { ReactElement } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../app/auth/AuthContext'
import { getEmployeeOverview } from '../../api/employees'
import { OverviewTab } from './tabs/OverviewTab'
import { ProfileTab } from './tabs/ProfileTab'
import { HistoryTab } from './tabs/HistoryTab'
import { ProjectsTab } from './tabs/ProjectsTab'
import { DevelopmentTab } from './tabs/DevelopmentTab'
import { TransferManagerModal } from './TransferManagerModal'

type TabId = 'overview' | 'profile' | 'projects' | 'development' | 'history'

interface TabDef {
  id: TabId
  label: string
}

/**
 * EmployeeRecord is a tabbed container for the employee record.
 *
 * The active tab is managed with local state — no URL hash is needed here
 * because deep-linking to a specific tab is not a stated requirement.
 * Tab keyboard navigation follows the ARIA tab pattern (role="tab", aria-selected,
 * aria-controls, and the tabpanel role below).
 *
 * Header actions (FRD-01 §4):
 * - HR: "Transfer manager" button (green, top-right per R-UI-1), hidden when stage=OFFBOARDING.
 * - Manager: "Start PIP" and "Assign project" buttons belong to F07/F04 (D-W8 — omitted here).
 */
export function EmployeeRecord(): ReactElement {
  const { employeeId: rawId } = useParams<{ employeeId: string }>()
  const employeeId = parseInt(rawId ?? '0', 10)
  const { user } = useAuth()
  const [activeTab, setActiveTab] = useState<TabId>('overview')
  const [transferOpen, setTransferOpen] = useState(false)

  const isHR = user?.appRole === 'HR'

  // Fetch the overview to get the employee's name, manager, and stage for the header actions.
  // This is already fetched by OverviewTab too, but React Query deduplicates the request
  // so no extra network call is made.
  const { data: overview } = useQuery({
    queryKey: ['employee-overview', employeeId],
    queryFn: () => getEmployeeOverview(employeeId),
  })

  // Projects and Development & reviews tabs are visible to both HR and Manager (D-W1).
  const tabs: TabDef[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'profile', label: 'Profile' },
    // Placeholder tabs — built now per D-W1, content delivered by F04/F05/F06.
    { id: 'projects', label: 'Projects' },
    { id: 'development', label: 'Development & reviews' },
    ...(isHR ? [{ id: 'history' as TabId, label: 'History' }] : []),
  ]

  // Transfer manager is HR-only and hidden when stage === 'OFFBOARDING' (spec §4).
  const showTransferManager =
    isHR &&
    overview !== undefined &&
    overview.stage !== 'OFFBOARDING'

  const employeeName =
    overview !== undefined
      ? `${overview.firstName} ${overview.lastName}`
      : '…'

  const currentManager = overview?.managerName ?? null

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      <div className="flex items-center justify-between gap-4">
        <Link
          to="/employees"
          className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
        >
          ← Back to Employees
        </Link>

        {/* Header actions — HR only (D-W8: Initiate exit / Start PIP / Assign project are
            built by their owning features; only Transfer manager is built here per W6) */}
        {showTransferManager && (
          <button
            type="button"
            onClick={() => setTransferOpen(true)}
            className="
              px-4 py-2 text-sm font-medium rounded-md
              bg-green-600 hover:bg-green-700 text-white
              min-h-[44px] focus-visible:ring-2 focus-visible:ring-green-500
              transition-colors
            "
          >
            Transfer manager
          </button>
        )}
      </div>

      {/* Tab bar — accessible ARIA tab pattern */}
      <div
        role="tablist"
        aria-label="Employee record sections"
        className="flex border-b border-gray-200 gap-1 overflow-x-auto"
      >
        {tabs.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={activeTab === tab.id}
            aria-controls={`tabpanel-${tab.id}`}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={[
              'px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors min-h-[44px] whitespace-nowrap',
              'focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500 rounded-t',
              activeTab === tab.id
                ? 'border-blue-600 text-blue-700'
                : 'border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab panels */}
      <div
        role="tabpanel"
        id={`tabpanel-${activeTab}`}
        aria-labelledby={`tab-${activeTab}`}
      >
        {activeTab === 'overview' && <OverviewTab employeeId={employeeId} />}
        {activeTab === 'profile' && (
          <ProfileTab
            employeeId={employeeId}
            canEdit={isHR} // Manager can only read (spec §11)
          />
        )}
        {/* Projects placeholder — no API call, visible per D-W1 */}
        {activeTab === 'projects' && <ProjectsTab employeeId={employeeId} />}
        {/* Development & reviews placeholder — no API call, visible per D-W1 */}
        {activeTab === 'development' && <DevelopmentTab employeeId={employeeId} />}
        {activeTab === 'history' && isHR && <HistoryTab employeeId={employeeId} />}
      </div>

      {/* Transfer Manager modal — HR only (W6) */}
      {isHR && (
        <TransferManagerModal
          employeeId={employeeId}
          employeeName={employeeName}
          currentManager={currentManager}
          isOpen={transferOpen}
          onClose={() => setTransferOpen(false)}
        />
      )}
    </div>
  )
}
