/**
 * @component Badge
 * @description Status badge for employee stage, account status, IT request status, etc.
 * @props label - string - text to display inside the badge
 * @props variant - 'default'|'success'|'warning'|'danger'|'info'|'draft' - color scheme
 * @props className - string - optional additional Tailwind classes
 * @example
 * <Badge label="Onboarding" variant="info" />
 * <Badge label="Draft" variant="draft" />
 */

import type { ReactElement } from 'react'

export type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'draft'

interface BadgeProps {
  label: string
  variant?: BadgeVariant
  className?: string
}

/** Tailwind class map per variant. */
const variantClasses: Record<BadgeVariant, string> = {
  default: 'bg-gray-100 text-gray-700',
  success: 'bg-green-100 text-green-700',
  warning: 'bg-orange-100 text-orange-700',
  danger: 'bg-red-100 text-red-700',
  info: 'bg-blue-100 text-blue-700',
  draft: 'bg-yellow-100 text-yellow-700',
}

/**
 * Maps a backend stage/status string to a badge variant.
 * Called by screens that pass a stage code directly.
 */
export function stageVariant(stage: string): BadgeVariant {
  switch (stage.toUpperCase()) {
    case 'ACTIVE': return 'success'
    case 'ONBOARDING': return 'info'
    case 'OFFBOARDING': return 'warning'
    case 'DRAFT': return 'draft'
    case 'SENT': return 'info'
    case 'COMPLETED': return 'success'
    case 'NOT_SENT': return 'default'
    default: return 'default'
  }
}

/** Inline status badge, no interactive behaviour. */
export function Badge({ label, variant = 'default', className = '' }: BadgeProps): ReactElement {
  return (
    <span
      className={[
        'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
        variantClasses[variant],
        className,
      ].join(' ')}
    >
      {label}
    </span>
  )
}
