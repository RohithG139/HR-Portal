/**
 * @component ToastContext
 * @description Global toast notification context. Provides showToast() to all descendants.
 *   Toasts auto-dismiss after 4 seconds (R-UI-4: "short confirmation toast").
 * @props children - ReactNode - app tree
 * @example
 *   const { showToast } = useToast()
 *   showToast('Changes saved.', 'success')
 */

// This file requires client-side state (useState, useEffect) for the auto-dismiss
// timer and the toast queue — it cannot be a static module.

import { createContext, useCallback, useContext, useState } from 'react'
import type { ReactElement, ReactNode } from 'react'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type ToastVariant = 'success' | 'error' | 'info'

interface Toast {
  id: number
  message: string
  variant: ToastVariant
}

interface ToastContextValue {
  /** Display a toast notification. Auto-dismisses after 4 s. */
  showToast: (message: string, variant?: ToastVariant) => void
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const ToastContext = createContext<ToastContextValue | undefined>(undefined)

let _nextId = 0

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

/** ToastProvider renders the toast stack and provides `showToast` to all descendants. */
export function ToastProvider({ children }: { children: ReactNode }): ReactElement {
  const [toasts, setToasts] = useState<Toast[]>([])

  // useCallback is justified: showToast is passed to many child components and
  // would otherwise cause unnecessary re-renders if recreated on every render.
  const showToast = useCallback((message: string, variant: ToastVariant = 'success'): void => {
    const id = ++_nextId
    setToasts((prev) => [...prev, { id, message, variant }])
    // Auto-dismiss after 4 seconds (R-UI-4)
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, 4000)
  }, [])

  const dismiss = (id: number): void => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }

  const variantStyles: Record<ToastVariant, string> = {
    success: 'bg-green-600 text-white',
    error: 'bg-red-600 text-white',
    info: 'bg-blue-600 text-white',
  }

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}

      {/* Toast stack — fixed bottom-right, accessible live region */}
      <div
        aria-live="polite"
        aria-atomic="false"
        className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 items-end"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role="status"
            className={[
              'flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg',
              'text-sm font-medium max-w-xs',
              variantStyles[t.variant],
            ].join(' ')}
          >
            <span>{t.message}</span>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              aria-label="Dismiss notification"
              className="ml-auto flex items-center justify-center min-h-[44px] min-w-[44px] rounded focus-visible:ring-2 focus-visible:ring-white/50"
            >
              <span aria-hidden="true">×</span>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * Returns the showToast function from the nearest ToastProvider.
 * @throws {Error} When used outside of `<ToastProvider>`.
 */
export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (ctx === undefined) {
    throw new Error('useToast must be used inside <ToastProvider>')
  }
  return ctx
}
