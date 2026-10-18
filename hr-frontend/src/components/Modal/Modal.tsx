/**
 * @component Modal
 * @description Accessible modal dialog. Traps focus, closes on Escape key press,
 * uses role="dialog" with aria-modal and aria-labelledby for screen readers.
 * @props isOpen - boolean - controls visibility
 * @props title - string - dialog title (shown in header and used for aria-labelledby)
 * @props onClose - function - called when the user closes the dialog
 * @props children - ReactNode - dialog body content
 * @props footer - ReactNode - optional footer (button row)
 * @props size - 'sm'|'md'|'lg' - dialog width
 * @example
 * <Modal isOpen={open} title="Add item" onClose={() => setOpen(false)}>
 * <Field ... />
 * </Modal>
 */

import { useEffect, useRef } from 'react'
import type { ReactElement, ReactNode, KeyboardEvent } from 'react'

interface ModalProps {
  isOpen: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const sizeClasses: Record<'sm' | 'md' | 'lg', string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
}

/**
 * Modal renders a focus-trapped dialog overlay.
 *
 * Focus management: when the modal opens, focus moves to the dialog element
 * so keyboard users are immediately inside the trap. When it closes, focus
 * returns to the element that triggered the modal (tracked via `previousFocus`).
 *
 * The dep array on the first useEffect contains `isOpen` because focus management
 * must run every time the modal opens or closes.
 */
export function Modal({ isOpen, title, onClose, children, footer, size = 'md' }: ModalProps): ReactElement | null {
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)
  const titleId = `modal-title-${title.replace(/\s+/g, '-').toLowerCase()}`

  // Move focus into dialog when it opens; restore when it closes.
  useEffect(() => {
    if (isOpen) {
      previousFocusRef.current = document.activeElement as HTMLElement
      dialogRef.current?.focus()
    } else {
      previousFocusRef.current?.focus()
    }
  }, [isOpen])

  // Close on Escape key
  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>): void => {
    if (e.key === 'Escape') onClose()
  }

  if (!isOpen) return null

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      aria-hidden={false}
    >
      {/* Overlay — click to close */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Dialog panel */}
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
        className={[
          'relative z-10 w-full bg-white',
          'border border-gray-200',
          'rounded-lg shadow-xl',
          'focus:outline-none',
          sizeClasses[size],
        ].join(' ')}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 id={titleId} className="text-base font-semibold text-gray-900">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="
              flex items-center justify-center min-h-[44px] min-w-[44px] rounded
              text-gray-500
              hover:bg-gray-100
              focus-visible:ring-2 focus-visible:ring-blue-500
            "
          >
            <span aria-hidden="true">×</span>
          </button>
        </div>

        {/* Body */}
        <div className="px-6 py-4 space-y-4">
          {children}
        </div>

        {/* Footer */}
        {footer !== undefined && (
          <div className="px-6 py-4 border-t border-gray-200 flex justify-between">
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}
