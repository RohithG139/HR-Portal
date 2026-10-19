/**
 * @component TopBar
 * @description Application-wide top navigation bar (R-UI-3).
 * Displays: current user role, notification bell with unread count and
 * notifications dropdown (wireframe #notif), user initials with dropdown (My profile / Log out).
 * All items are keyboard-navigable with focus-visible ring.
 * @props (none — reads from AuthContext and notification API)
 * @example
 * <TopBar />
 */

import { useEffect, useRef, useState } from 'react'
import type { ReactElement } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import {
  getNotificationCount,
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from '../../api/notifications'
import type { NotificationDto } from '../../api/notifications'
import { formatDate } from '../../lib/date'

/**
 * BellIcon — decorative SVG notification bell.
 * aria-hidden because the button has its own aria-label.
 */
function BellIcon(): ReactElement {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  )
}

/**
 * TopBar renders the application header with role badge, notification bell,
 * and user initials dropdown.
 *
 * Notification count: polled every 30 s via React Query's staleTime default.
 * On tab close the query is garbage-collected automatically.
 */
export function TopBar(): ReactElement {
  const [menuOpen, setMenuOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const notifRef = useRef<HTMLDivElement>(null)
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const initials = user?.initials ?? '--'
  const roleLabel = user?.appRole ?? 'Not signed in'

  // A38 unread count — used for the badge on the bell icon
  const { data: countData } = useQuery({
    queryKey: ['notification-count'],
    queryFn: getNotificationCount,
    // Refetch every 60s to keep the count fresh without hammering the backend
    refetchInterval: 60_000,
    enabled: user !== null,
  })

  const unreadCount = countData?.count ?? 0

  // A38 list — fetched only while the bell dropdown is open
  const { data: notifData } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => getNotifications(),
    enabled: user !== null && notifOpen,
  })

  const invalidateNotifications = (): void => {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] })
    void queryClient.invalidateQueries({ queryKey: ['notification-count'] })
  }

  const markReadMutation = useMutation({
    mutationFn: (id: number) => markNotificationRead(id),
    onSuccess: invalidateNotifications,
  })

  const markAllMutation = useMutation({
    mutationFn: () => markAllNotificationsRead(),
    onSuccess: invalidateNotifications,
  })

  /** Marks the notification read and follows its link, if any. */
  const openNotification = (notif: NotificationDto): void => {
    if (!notif.read) markReadMutation.mutate(notif.id)
    if (notif.linkRoute) {
      setNotifOpen(false)
      void navigate(notif.linkRoute)
    }
  }

  // Close the dropdown on outside click or Escape
  useEffect(() => {
    if (!notifOpen) return undefined
    const onPointerDown = (e: PointerEvent): void => {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) setNotifOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent): void => {
      if (e.key === 'Escape') setNotifOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [notifOpen])

  return (
    <header
      className="
        flex items-center justify-between
        h-14 px-4 sm:px-6 lg:px-8
        bg-white
        border-b border-gray-200
        shadow-sm
      "
    >
      {/* Skip-to-content link — first focusable element on every page (accessibility) */}
      <a
        href="#main-content"
        className="
          sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2
          focus:z-50 focus:px-4 focus:py-2
          bg-white text-gray-900
          focus-visible:ring-2 focus-visible:ring-blue-500 rounded
        "
      >
        Skip to main content
      </a>

      {/* Brand */}
      <div className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="flex items-center justify-center w-9 h-9 rounded-lg bg-blue-900 text-white text-sm font-extrabold"
        >
          CW
        </span>
        <span className="text-base sm:text-lg font-semibold text-gray-900 tracking-tight">
          CubeWorks HR
        </span>
      </div>

      {/* Right side: role badge, notification bell, initials */}
      <nav
        aria-label="User menu"
        className="flex items-center gap-3 sm:gap-4"
      >
        {/* Active role (R-UI-3) */}
        <span
          aria-label={`Signed in as ${roleLabel}`}
          className="
            hidden sm:inline-block
            text-xs font-medium uppercase tracking-wide
            text-gray-500
          "
        >
          {roleLabel}
        </span>

        {/* Notification bell with unread count badge + dropdown (wireframe #notif) */}
        <div ref={notifRef} className="relative">
        <button
          type="button"
          aria-label={unreadCount > 0 ? `Notifications (${unreadCount} unread)` : 'Notifications'}
          aria-expanded={notifOpen}
          aria-haspopup="dialog"
          onClick={() => {
            setMenuOpen(false)
            setNotifOpen((prev) => !prev)
          }}
          className="
            relative flex items-center justify-center
            min-h-[44px] min-w-[44px] rounded-full
            text-gray-600
            hover:bg-gray-100
            focus-visible:ring-2 focus-visible:ring-blue-500
            transition-colors
          "
        >
          <BellIcon />
          {unreadCount > 0 && (
            <span
              aria-hidden="true"
              className="
                absolute top-1 right-1
                flex items-center justify-center
                min-w-[18px] h-[18px] px-1
                rounded-full bg-red-600 text-white text-[10px] font-bold
              "
            >
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>

          {notifOpen && (
            <div
              role="dialog"
              aria-label="Notifications"
              className="
                absolute right-0 mt-2 w-[min(24rem,calc(100vw-2rem))] z-50
                bg-white border border-gray-200 rounded-xl shadow-lg
                overflow-hidden
              "
            >
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
                <h2 className="text-sm font-semibold text-gray-900">Notifications</h2>
                {(notifData?.content ?? []).some((n) => !n.read) && (
                  <button
                    type="button"
                    onClick={() => markAllMutation.mutate()}
                    disabled={markAllMutation.isPending}
                    className="text-xs font-medium text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded px-1"
                  >
                    Mark all read
                  </button>
                )}
              </div>

              {notifData === undefined || notifData.content.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-6">No notifications.</p>
              ) : (
                <ul className="max-h-[60vh] overflow-y-auto divide-y divide-gray-100" aria-live="polite">
                  {notifData.content.map((notif: NotificationDto) => (
                    <li key={notif.id}>
                      <button
                        type="button"
                        onClick={() => openNotification(notif)}
                        aria-label={`${notif.title}: ${notif.message}${notif.read ? ' (read)' : ' (unread)'}`}
                        className={`
                          w-full text-left px-4 py-3 text-sm transition-colors
                          focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500
                          ${notif.read ? 'bg-white hover:bg-gray-50' : 'bg-blue-50 hover:bg-blue-100'}
                        `}
                      >
                        <p className="text-gray-900">
                          <span className={notif.read ? '' : 'font-semibold'}>{notif.title}</span>
                          {notif.message !== '' && <span>: {notif.message}</span>}
                        </p>
                        <p className="text-xs text-gray-500 mt-0.5">{formatDate(notif.createdAt.slice(0, 10))}</p>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        {/* Initials button + dropdown */}
        <div className="relative">
          <button
            type="button"
            aria-label="Open user menu"
            aria-expanded={menuOpen}
            aria-haspopup="menu"
            onClick={() => {
              setNotifOpen(false)
              setMenuOpen((prev) => !prev)
            }}
            className="
              flex items-center justify-center
              min-h-[44px] min-w-[44px] rounded-full
              bg-blue-600
              text-white text-sm font-semibold
              hover:bg-blue-700
              focus-visible:ring-2 focus-visible:ring-blue-500
              transition-colors
            "
          >
            {initials}
          </button>

          {/* Dropdown menu */}
          {menuOpen && (
            <ul
              role="menu"
              aria-label="User actions"
              className="
                absolute right-0 mt-2 w-48 z-50
                bg-white
                border border-gray-200
                rounded-md shadow-lg
                py-1
              "
            >
              <li role="none">
                <button
                  role="menuitem"
                  type="button"
                  onClick={() => {
                    setMenuOpen(false)
                    void navigate('/my-profile')
                  }}
                  className="
                    w-full text-left px-4 py-2 text-sm
                    text-gray-700
                    hover:bg-gray-100
                    min-h-[44px]
                    focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500
                  "
                >
                  My profile
                </button>
              </li>
              <li role="none">
                <button
                  role="menuitem"
                  type="button"
                  onClick={() => {
                    setMenuOpen(false)
                    logout()
                  }}
                  className="
                    w-full text-left px-4 py-2 text-sm
                    text-gray-700
                    hover:bg-gray-100
                    min-h-[44px]
                    focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-500
                  "
                >
                  Log out
                </button>
              </li>
            </ul>
          )}
        </div>
      </nav>
    </header>
  )
}
