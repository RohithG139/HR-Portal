/**
 * @component Sidebar
 * @description Role-aware left navigation sidebar (R-UI-7).
 * Nav items are shown per the authenticated user's role.
 * Hiding menu items is a UX convenience; the backend enforces all permissions (R-API-2).
 * @props (none — reads from AuthContext)
 * @example
 * <Sidebar />
 */

import type { ReactElement } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { AppRole } from '../auth/AuthContext'

// ---------------------------------------------------------------------------
// Nav icons — small inline outline SVGs, same hand-rolled style as TopBar's
// BellIcon (no icon-library dependency). Decorative only: the NavLink text
// is the accessible name, so each icon is aria-hidden.
// ---------------------------------------------------------------------------

type IconProps = { className?: string }

function TasksIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <path d="m8.5 12 2.5 2.5 4.5-5" />
    </svg>
  )
}

function EmployeesIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <circle cx="9" cy="8" r="3" />
      <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
      <path d="M16 6.2c1.2.4 2 1.5 2 2.8s-.8 2.4-2 2.8" />
      <path d="M21 20c0-2.8-1.9-5.1-4.5-5.8" />
    </svg>
  )
}

function SettingsIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M4 6h10M18 6h2M4 12h2M8 12h12M4 18h10M18 18h2" />
      <circle cx="16" cy="6" r="2" />
      <circle cx="6" cy="12" r="2" />
      <circle cx="16" cy="18" r="2" />
    </svg>
  )
}

function ReportsIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M4 20V10M12 20V4M20 20v-7" />
      <path d="M2 20h20" />
    </svg>
  )
}

function OnboardingIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M4 21v-7a8 8 0 0 1 16 0v7" />
      <path d="M4 14h16" />
      <circle cx="12" cy="8" r="3" />
    </svg>
  )
}

/** Grid icon for "Dashboard". */
function DashboardIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <rect x="3" y="3" width="8" height="8" rx="1.5" />
      <rect x="13" y="3" width="8" height="8" rx="1.5" />
      <rect x="3" y="13" width="8" height="8" rx="1.5" />
      <rect x="13" y="13" width="8" height="8" rx="1.5" />
    </svg>
  )
}

/** Team/group icon for "My Team" (Manager). */
function TeamIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <circle cx="9" cy="7" r="3" />
      <circle cx="17" cy="7" r="3" />
      <path d="M3 20c0-3.3 2.7-6 6-6" />
      <path d="M11 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
    </svg>
  )
}

function ProfileIcon({ className }: IconProps): ReactElement {
  return (
    <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-3.9 3.6-7 8-7s8 3.1 8 7" />
    </svg>
  )
}

// ---------------------------------------------------------------------------
// Navigation item definition
// ---------------------------------------------------------------------------

interface NavItem {
  label: string
  to: string
  icon: (props: IconProps) => ReactElement
  /** Roles that can see this item (R-UI-7). Backend enforces access (R-API-2). */
  roles: AppRole[]
}

/**
 * Nav items. Items are shown only for the roles listed in the `roles` array (R-UI-7).
 * Backend enforces access regardless of what the UI shows (R-API-2).
 *
 * FRD-08 Gate 4 part 1: "Settings" added here for HR (S1–S6).
 * FRD-08 Gate 4 part 2: "Reports" added here for HR (S7–S15).
 * FRD-01 Wave 3: "My Team" added for Manager; IT_ADMIN added to "Employees".
 *   Dashboard nav item: reinstated 2026-10-18 (user-reported gap during live end-to-end
 *   testing). It was previously omitted on the reasoning that the default route (/) already
 *   lands on the dashboard, making a sidebar entry a same-page no-op — but that reasoning only
 *   holds on first load. Once a user navigates to any other screen (Employees, My Team,
 *   Settings, My Tasks...), there was no link anywhere back to the dashboard except editing
 *   the URL bar, since no logo/home link exists in TopBar either. The spec (§2) lists
 *   "Dashboard" in every role's menu table; this restores that for all four roles, routing to
 *   "/" (DashboardStub already role-dispatches and handles the Employee ONBOARDING-stage
 *   redirect internally).
 */
const NAV_ITEMS: NavItem[] = [
  // All roles: Dashboard (always routes to "/" — DashboardStub role-dispatches internally)
  { label: 'Dashboard', to: '/', icon: DashboardIcon, roles: ['HR', 'MANAGER', 'EMPLOYEE', 'IT_ADMIN'] },
  // All roles: My Tasks
  { label: 'My Tasks', to: '/my-tasks', icon: TasksIcon, roles: ['HR', 'MANAGER', 'EMPLOYEE', 'IT_ADMIN'] },
  // HR: employee list, reports, settings (wireframe order: Reports above Settings)
  { label: 'Employees', to: '/employees', icon: EmployeesIcon, roles: ['HR', 'IT_ADMIN'] },
  { label: 'Reports', to: '/reports', icon: ReportsIcon, roles: ['HR'] },
  { label: 'Settings', to: '/settings', icon: SettingsIcon, roles: ['HR'] },
  // Manager: My Team (W5, FRD-01 Wave 3)
  { label: 'My Team', to: '/team', icon: TeamIcon, roles: ['MANAGER'] },
  // Employee: own dashboard and profile
  { label: 'My Onboarding', to: '/dashboard/my-onboarding', icon: OnboardingIcon, roles: ['EMPLOYEE'] },
  { label: 'My Profile', to: '/my-profile', icon: ProfileIcon, roles: ['EMPLOYEE'] },
]

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Sidebar renders the role-filtered navigation.
 *
 * Role filtering runs on each render; the role is stable within a session
 * (set once at login via Keycloak → GET /api/me) so the cost is negligible.
 */
export function Sidebar(): ReactElement {
  const { user } = useAuth()

  const visibleItems = user
    ? NAV_ITEMS.filter((item) => item.roles.includes(user.appRole))
    : []

  return (
    <aside
      aria-label="Main navigation"
      className="
        w-56 shrink-0
        h-full
        bg-blue-900
        flex flex-col
        py-4
      "
    >
      <nav aria-label="Sidebar navigation">
        {visibleItems.length === 0 ? (
          <p
            aria-hidden="true"
            className="px-4 text-xs text-blue-300 italic"
          >
            No menu items
          </p>
        ) : (
          <ul className="space-y-1 px-2">
            {visibleItems.map((item) => {
              const Icon = item.icon
              return (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/'}
                    className={({ isActive }) =>
                      [
                        'flex items-center gap-3 pl-3 pr-3 py-2 rounded-md text-sm font-medium',
                        'min-h-[44px] border-l-4',
                        'focus-visible:ring-2 focus-visible:ring-white',
                        'transition-colors',
                        isActive
                          ? 'bg-white/10 border-white text-white font-semibold'
                          : 'border-transparent text-blue-100 hover:bg-white/5 hover:text-white',
                      ].join(' ')
                    }
                  >
                    <Icon className="shrink-0" />
                    {item.label}
                  </NavLink>
                </li>
              )
            })}
          </ul>
        )}
      </nav>
    </aside>
  )
}
