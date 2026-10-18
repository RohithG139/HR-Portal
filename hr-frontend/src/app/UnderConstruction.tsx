/**
 * @component UnderConstruction
 * @description Placeholder index page shown at Gate 0 before any real feature
 * screens exist. Gate 4 replaces this with the role-appropriate dashboard.
 * @props (none)
 * @example
 * <UnderConstruction />
 */

import type { ReactElement } from 'react'

/** Temporary placeholder page — removed when Gate 4 adds the real dashboard. */
export function UnderConstruction(): ReactElement {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
      <h1 className="text-2xl sm:text-3xl lg:text-4xl font-bold text-gray-900">
        CubeWorks HR
      </h1>
      <p className="text-sm sm:text-base text-gray-500">
        Under construction — feature screens are added in Gate 4.
      </p>
    </div>
  )
}
