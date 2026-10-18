/**
 * @component App
 * @description Application root. Wraps the entire tree in:
 *   1. `QueryClientProvider` — TanStack Query for all server state (R-CODE-2).
 *   2. `AuthProvider`        — Keycloak authentication (keycloak-js, cubeworks-hr realm).
 *   3. `ToastProvider`       — Global toast notifications (R-UI-4).
 *   4. `RouterProvider`      — React Router v7 data router.
 *
 * QueryClient is created once outside the component so it survives
 * React StrictMode double-mount and Fast Refresh cycles.
 *
 * @props (none — root component)
 * @example
 *   <App />  // rendered by main.tsx
 */

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { RouterProvider } from 'react-router-dom'
import { AuthProvider } from './app/auth/AuthContext'
import { ToastProvider } from './components/Toast/ToastContext'
import { router } from './app/router'

/**
 * Singleton QueryClient.
 *
 * staleTime: 30 s — avoids refetching data that was just loaded when the user
 * navigates between routes. Each feature can override staleTime on its own queries.
 * retry: 1 — one automatic retry on network error; avoids hammering the backend
 * on temporary outages while still recovering from transient failures.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})

export default function App(): ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      {/*
       * AuthProvider initialises the keycloak-js adapter and redirects to Keycloak
       * login if the user has no valid session. It renders null (loading state) until
       * auth is resolved to prevent unauthenticated API calls.
       */}
      <AuthProvider>
        <ToastProvider>
          <RouterProvider router={router} />
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
