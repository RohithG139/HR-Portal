/**
 * @component AuthContext
 * @description Keycloak-backed authentication context.
 * Initialises the keycloak-js adapter on mount, redirects to Keycloak login if
 * not authenticated (onLoad: 'login-required'), then calls GET /api/me to populate
 * the AuthUser with the role-filtered menu list from the backend.
 * Refreshes the token silently whenever Keycloak signals it is about to expire.
 *
 * Shape:
 * { user: AuthUser | null, logout: () => void }
 *
 * Usage:
 * const { user } = useAuth()
 */

import { createContext, useContext, useEffect, useRef, useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import Keycloak from 'keycloak-js'
import { setTokenGetter } from '../../api/token'
import { apiGet } from '../../api/client'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Application roles as recognised by the backend (R-API-2, FRD-00 permissions table).
 * Must match hr-backend/src/main/java/com/cubeworks/hr/employee/AppRole.java exactly.
 */
export type AppRole = 'HR' | 'MANAGER' | 'EMPLOYEE' | 'IT_ADMIN'

/** Shape of the GET /api/me response (A01). `stage` added 2026-10-17, F03/FRD-01 Wave 3. */
interface MeDto {
  employeeId: number
  name: string
  initials: string
  appRole: string
  stage: string
  menu: string[]
}

/** The authenticated user's identity, derived from Keycloak JWT + GET /api/me. */
export interface AuthUser {
  /** The employee's primary key in `hr.employee`. */
  employeeId: number
  /** Display name (first + last). */
  name: string
  /** Two-letter initials shown in the top bar (R-UI-3). */
  initials: string
  /** The single active application role for this session. */
  appRole: AppRole
  /**
   * Employee lifecycle stage ("ONBOARDING" | "ACTIVE" | "OFFBOARDING"). Used by the
   * dashboard (W01, F03/FRD-01 Wave 3) to pick between the Employee new-joiner dashboard
   * and the active-stage dashboard — meaningless for non-EMPLOYEE roles, but always present.
   */
  stage: string
  /** Menu item codes the backend allows for this role (WS-01). */
  menu: string[]
}

/** Value provided to all consumers of AuthContext. */
export interface AuthContextValue {
  /** Null while auth is initialising or if not authenticated. */
  user: AuthUser | null
  /** Triggers Keycloak logout and redirect. */
  logout: () => void
}

// ---------------------------------------------------------------------------
// Keycloak instance
// ---------------------------------------------------------------------------

/**
 * Singleton Keycloak adapter. Created once at module level so it persists
 * across React StrictMode double-mounts without re-initialising the OIDC flow.
 *
 * Config comes from VITE_KEYCLOAK_* env vars (see .env.example), not hard-coded,
 * so a non-dev deploy can point at a different (https) Keycloak without a code
 * change. None of these are secrets — hr-frontend is a public OIDC client — so
 * it's safe for Vite to inline them into the built JS same as VITE_API_BASE_URL.
 * Defaults match the dev docker-compose setup (CLAUDE.md §1.3 / the realm JSON).
 */
const keycloak = new Keycloak({
  url: import.meta.env['VITE_KEYCLOAK_URL'] ?? 'http://localhost:8180',
  realm: import.meta.env['VITE_KEYCLOAK_REALM'] ?? 'cubeworks-hr',
  clientId: import.meta.env['VITE_KEYCLOAK_CLIENT_ID'] ?? 'hr-frontend',
})

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

interface AuthProviderProps {
  children: ReactNode
}

/**
 * AuthProvider initialises the Keycloak adapter and manages the auth lifecycle.
 *
 * Mount behaviour:
 * 1. Call keycloak.init({ onLoad: 'login-required' }) — redirects to Keycloak
 * login page if the user has no valid session; returns here with a token otherwise.
 * 2. Register the token getter so the API client can read the current token.
 * 3. Call GET /api/me to populate `AuthUser` with backend-derived details.
 * 4. Set up token-expiry listener to refresh silently before the token expires.
 *
 * The dep array is empty because Keycloak initialisation must run exactly once on mount.
 * The component renders `null` while initialisation is in progress to prevent
 * unauthenticated API calls.
 */
export function AuthProvider({ children }: AuthProviderProps): ReactElement | null {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [ready, setReady] = useState(false)
  const [initFailed, setInitFailed] = useState(false)
  // Guards against React 18/19 StrictMode double-invoking this effect in dev
  // (mount -> cleanup -> mount). The `keycloak` singleton's init() is NOT
  // idempotent — calling it twice throws "Keycloak instance can only be
  // initialized once." Without this ref, the app would crash on every
  // `npm run dev` load under <StrictMode> (see main.tsx).
  const initStarted = useRef(false)

  useEffect(() => {
    if (initStarted.current) {
      return
    }
    initStarted.current = true

    // keycloak.init resolves to true (authenticated) or redirects to login.
    // checkLoginIframe: false avoids an unnecessary iframe check in dev.
    keycloak
      .init({ onLoad: 'login-required', checkLoginIframe: false })
      .then(async (authenticated) => {
        if (authenticated) {
          // Register token getter so api/client.ts can read the current token
          // on every request without prop-drilling.
          setTokenGetter(() => keycloak.token)

          // Call GET /api/me to get the backend-derived identity and menu
          try {
            const me = await apiGet<MeDto>('/me')
            setUser({
              employeeId: me.employeeId,
              name: me.name,
              initials: me.initials,
              appRole: me.appRole as AppRole,
              stage: me.stage,
              menu: me.menu,
            })
          } catch {
            // /api/me failed — log out rather than proceeding with broken state
            keycloak.logout().catch(() => undefined)
          }
        }
        setReady(true)
      })
      .catch(() => {
        // Init failed (network error, misconfigured realm) — the login redirect
        // never happened, so `user` stays null and every API call would get a
        // 401 anyway, but silently rendering the app shell with no way to sign
        // in is confusing. Show an explicit error instead (see render below).
        setInitFailed(true)
        setReady(true)
      })

    // Refresh the token silently 30 seconds before it expires.
    // onTokenExpired fires when Keycloak detects the token will expire soon.
    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).then((refreshed) => {
        if (refreshed) {
          // Re-register getter so new token value is picked up immediately
          setTokenGetter(() => keycloak.token)
        }
      }).catch(() => {
        // Refresh failed — session is dead, redirect to login
        keycloak.logout().catch(() => undefined)
      })
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  // Empty dep array: Keycloak init runs once on mount. The keycloak singleton
  // is stable; re-running init would restart the OIDC flow incorrectly.

  const logout = (): void => {
    keycloak.logout().catch(() => undefined)
  }

  // Do not render children until auth is resolved to prevent flicker and
  // unauthenticated API calls on initial render.
  if (!ready) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex items-center justify-center min-h-screen text-gray-500"
      >
        Loading…
      </div>
    )
  }

  if (initFailed) {
    return (
      <div
        role="alert"
        className="flex items-center justify-center min-h-screen text-red-600 text-sm"
      >
        Sign-in is unavailable right now. Please try again later.
      </div>
    )
  }

  return (
    <AuthContext.Provider value={{ user, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Returns the current auth context value.
 *
 * @throws {Error} When used outside of `<AuthProvider>`.
 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (ctx === undefined) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return ctx
}
