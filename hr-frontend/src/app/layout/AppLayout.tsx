/**
 * @component AppLayout
 * @description Root page shell: top bar + sidebar + main content outlet.
 * All authenticated pages render inside this layout via the React Router
 * nested route (`<Outlet />`). The skip-to-content link lives in TopBar
 * as the first focusable element on every page.
 * @props (none — layout wrapper)
 * @example
 * Used automatically by the router; not rendered directly.
 */

import type { ReactElement } from 'react'
import { Outlet } from 'react-router-dom'
import { TopBar } from './TopBar'
import { Sidebar } from './Sidebar'

/**
 * AppLayout composes the two persistent chrome elements (TopBar, Sidebar)
 * with the page-specific content slot (`<Outlet />`).
 *
 * Layout intent:
 * - `flex flex-col` on the outer div gives a full-viewport column.
 * - The inner row (`flex flex-row`) fills the remaining height so the
 * sidebar and main content both stretch to the bottom.
 * - `overflow-y-auto` on `<main>` lets page content scroll independently
 * of the sidebar, which stays fixed in view.
 */
export function AppLayout(): ReactElement {
  return (
    <div className="flex flex-col min-h-screen bg-gray-100">
      <TopBar />

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar hidden on mobile, visible from md breakpoint */}
        <div className="hidden md:flex">
          <Sidebar />
        </div>

        {/* Page content — receives the matched child route via <Outlet> */}
        <main
          id="main-content"
          tabIndex={-1}
          className="
            flex-1 overflow-y-auto
            container mx-auto px-4 sm:px-6 lg:px-8
            py-6
            focus:outline-none
          "
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}
