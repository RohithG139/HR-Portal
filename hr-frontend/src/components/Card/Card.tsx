/**
 * @component Card
 * @description Container card with optional heading and action slot (top-right "+ Add" buttons, R-UI-1).
 * @props title - string - optional card heading
 * @props action - ReactNode - optional top-right action (e.g. "+ Add item")
 * @props children - ReactNode - card body content
 * @props className - string - optional additional Tailwind classes
 * @example
 * <Card title="Personal details">
 * <Field ... />
 * </Card>
 */

import type { ReactElement, ReactNode } from 'react'

interface CardProps {
  title?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}

/** Rounded white card with optional title and top-right action. */
export function Card({ title, action, children, className = '' }: CardProps): ReactElement {
  return (
    <section
      className={[
        'bg-white',
        'border border-gray-200',
        'rounded-lg p-4 sm:p-6',
        className,
      ].join(' ')}
      aria-labelledby={title !== undefined ? undefined : undefined}
    >
      {(title !== undefined || action !== undefined) && (
        <div className="flex items-center justify-between mb-4">
          {title !== undefined && (
            <h2 className="text-base sm:text-lg font-semibold text-gray-900">
              {title}
            </h2>
          )}
          {action !== undefined && (
            <div className="ml-auto">
              {action}
            </div>
          )}
        </div>
      )}
      {children}
    </section>
  )
}
