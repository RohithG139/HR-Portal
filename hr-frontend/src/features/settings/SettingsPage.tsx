/**
 * @component SettingsPage
 * @description Settings landing page (S1–S6). Hosts 5 tabs navigated via the ?tab= query
 * parameter so each tab has a bookmarkable URL (spec §4 route table).
 * All tabs are HR-only; the backend enforces access (R-API-2, R-UI-7).
 * @props (none)
 * @example
 * Route: { path: '/settings', element: <SettingsPage /> }
 */

// "use client" not needed — Vite React project, all components are client-side.

import type { ReactElement } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DepartmentsTab } from './DepartmentsTab'
import { ChecklistTemplatesTab } from './ChecklistTemplatesTab'
import { AppraisalFormsTab } from './AppraisalFormsTab'
import { EmailTemplatesTab } from './EmailTemplatesTab'
import { TimersTab } from './TimersTab'

// ---------------------------------------------------------------------------
// Tab definitions
// ---------------------------------------------------------------------------

type TabId = 'departments' | 'checklists' | 'forms' | 'emails' | 'timers'

interface TabDef {
  id: TabId
  label: string
}

const TABS: TabDef[] = [
  { id: 'departments', label: 'Departments & job titles' },
  { id: 'checklists', label: 'Checklist templates' },
  { id: 'forms', label: 'Appraisal forms' },
  { id: 'emails', label: 'E-mail templates' },
  { id: 'timers', label: 'Timers & rules' },
]

function isValidTab(s: string | null): s is TabId {
  return s === 'departments' || s === 'checklists' || s === 'forms' || s === 'emails' || s === 'timers'
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * SettingsPage reads and writes the `?tab=` query parameter to navigate between tabs.
 * Tab state lives in the URL so the browser back-button and bookmarks work correctly.
 */
export function SettingsPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawTab = searchParams.get('tab')
  const activeTab: TabId = isValidTab(rawTab) ? rawTab : 'departments'

  const handleTabChange = (id: TabId): void => {
    setSearchParams({ tab: id })
  }

  const renderTab = (): ReactElement => {
    switch (activeTab) {
      case 'departments': return <DepartmentsTab />
      case 'checklists': return <ChecklistTemplatesTab />
      case 'forms': return <AppraisalFormsTab />
      case 'emails': return <EmailTemplatesTab />
      case 'timers': return <TimersTab />
    }
  }

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Skip nav anchor so keyboard users can jump to main content */}
      <a
        href="#settings-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:bg-white focus:text-blue-600 focus:rounded focus:shadow"
      >
        Skip to content
      </a>

      {/* Page title */}
      <h1 className="font-display text-[22px] font-bold tracking-[-0.3px] text-gray-900">
        Settings
      </h1>

      {/* Tab bar — wireframe .tabs / .tab.on */}
      <nav aria-label="Settings tabs">
        <div
          role="tablist"
          aria-label="Settings sections"
          className="flex flex-wrap gap-1 border-b-2 border-gray-200"
        >
          {TABS.map((tab) => {
            const isActive = tab.id === activeTab
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={isActive}
                aria-controls={`settings-content`}
                id={`tab-${tab.id}`}
                onClick={() => handleTabChange(tab.id)}
                className={[
                  'px-[22px] py-[11px] -mb-[2px] text-[13px] font-semibold tracking-[0.2px] border-b-[3px] min-h-[44px]',
                  'focus-visible:ring-2 focus-visible:ring-blue-500 transition-colors',
                  isActive
                    ? 'border-blue-600 text-blue-600 bg-gradient-to-b from-blue-50 to-transparent'
                    : 'border-transparent text-gray-500 hover:text-blue-600',
                ].join(' ')}
              >
                {tab.label}
              </button>
            )
          })}
        </div>
      </nav>

      {/* Tab panel */}
      <div
        id="settings-content"
        role="tabpanel"
        aria-labelledby={`tab-${activeTab}`}
        tabIndex={0}
        className="focus:outline-none"
      >
        {renderTab()}
      </div>
    </div>
  )
}
