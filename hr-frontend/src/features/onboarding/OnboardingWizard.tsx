/**
 * @component OnboardingWizard
 * @description S2/S3/S4 — Three-step onboarding wizard (HR). One page, three step tabs.
 * Steps can be opened in any order once the employee exists (spec §5).
 * The active tab is driven by a `step` URL search param.
 * @props (none — reads employeeId from route params, step from search params)
 * @example
 * Route: { path: '/onboarding/new', element: <OnboardingWizard /> }
 * Route: { path: '/onboarding/:employeeId', element: <OnboardingWizard /> }
 */

import { Fragment, useState } from 'react'
import type { ReactElement } from 'react'
import { useParams, useSearchParams, useLocation, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getOnboarding } from '../../api/onboarding'
import { Step1Details } from './Step1Details'
import { Step2ItRequest } from './Step2ItRequest'
import { Step3Checklist } from './Step3Checklist'

type WizardStep = 0 | 1 | 2

const STEP_LABELS: [string, string, string] = [
  '1 · Employee details',
  '2 · IT & equipment request',
  '3 · Checklist',
]

/**
 * OnboardingWizard orchestrates the three-step onboarding process.
 *
 * When `employeeId` is absent from the route (new employee flow), Step 1 starts
 * in draft mode. After Step 1 creates the employee, the wizard navigates to the
 * same route with the new employee ID, and all three tabs become available.
 *
 * The step is stored in a URL search param (`?step=0|1|2`) so the browser
 * back button navigates between steps correctly.
 */
export function OnboardingWizard(): ReactElement {
  const { employeeId: rawId, draftId: rawDraftId } = useParams<{ employeeId?: string; draftId?: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const employeeId = rawId !== undefined && rawId !== '' ? parseInt(rawId, 10) : null

  // Resuming a draft from the Employees list "Continue" action (route
  // /onboarding/draft/:draftId). firstName/lastName travel via router state
  // (set on the Link in EmployeesListPage), not the URL, so this personal data
  // never lands in browser history or server access logs — it's simply absent
  // after a hard refresh, which only affects this prefill convenience, not
  // correctness: Step1Details reads the whole saved draft back with
  // A09a GET /api/onboarding-drafts/{id} (D-23).
  const draftState = location.state as { firstName?: string; lastName?: string } | null
  const initialDraftId = rawDraftId !== undefined && rawDraftId !== '' ? parseInt(rawDraftId, 10) : undefined
  const initialFirstName = draftState?.firstName
  const initialLastName = draftState?.lastName

  // Default to step 0 for new employees, step read from URL otherwise
  const stepParam = parseInt(searchParams.get('step') ?? '0', 10)
  const activeStep: WizardStep = ([0, 1, 2].includes(stepParam) ? stepParam : 0) as WizardStep

  // For an existing employee, fetch the summary so we can show the name and status
  const { data: summary } = useQuery({
    queryKey: ['onboarding-summary', employeeId],
    queryFn: () => getOnboarding(employeeId!),
    enabled: employeeId !== null,
  })

  const [createdEmployeeId, setCreatedEmployeeId] = useState<number | null>(null)
  const effectiveEmployeeId = employeeId ?? createdEmployeeId

  const goToStep = (step: WizardStep): void => {
    setSearchParams({ step: String(step) })
  }

  const handleStep1Created = (newEmployeeId: number): void => {
    setCreatedEmployeeId(newEmployeeId)
    // Move to the employee's own onboarding URL (replacing /employees/new), so a logout,
    // refresh or Back keeps the employee and step 1 shows the saved details again
    void navigate(`/employees/${newEmployeeId}/onboarding?step=1`, { replace: true })
  }

  const employeeName = summary?.employeeName ?? 'New employee'

  // Green "done" pills: details saved (employee exists), IT request sent, all mandatory
  // checklist items done (optional items don't count — same rule as Complete onboarding)
  const stepDone: [boolean, boolean, boolean] = [
    effectiveEmployeeId !== null,
    summary?.itSentAt != null,
    summary !== undefined && summary.canComplete,
  ]

  return (
    <div className="container mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
      {/* Back link */}
      <Link
        to="/employees"
        className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
      >
        ← Back to Employees
      </Link>

      {/* Page title */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl sm:text-2xl font-semibold text-gray-900">
          {employeeName}
        </h1>
        <span className="text-sm text-gray-500">Onboarding</span>
      </div>

      {/* Step tabs — wireframe .bcrumb.steptabs: pills joined by arrows;
          current = blue, done = green, upcoming = grey */}
      <div
        role="tablist"
        aria-label="Onboarding steps"
        className="flex flex-wrap items-center gap-2"
      >
        {STEP_LABELS.map((label, idx) => {
          const step = idx as WizardStep
          // Only allow navigation to steps after step 1 once the employee exists
          const disabled = step > 0 && effectiveEmployeeId === null
          const current = activeStep === step
          const done = stepDone[step]
          return (
            <Fragment key={step}>
              {idx > 0 && <span aria-hidden="true" className="text-gray-400">→</span>}
              <button
                type="button"
                role="tab"
                aria-selected={current}
                aria-controls={`step-panel-${step}`}
                disabled={disabled}
                onClick={() => { if (!disabled) goToStep(step) }}
                className={[
                  'px-3.5 py-1.5 text-sm rounded-md border transition-colors',
                  'focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none',
                  current
                    ? 'bg-blue-600 border-blue-600 text-white font-semibold'
                    : done
                      ? 'bg-green-100 border-green-300 text-green-800 font-medium hover:border-blue-600'
                      : 'bg-gray-50 border-gray-200 text-gray-500 font-medium hover:border-blue-600',
                  disabled ? 'opacity-50 cursor-not-allowed hover:border-gray-200' : 'cursor-pointer',
                ].join(' ')}
              >
                {label}
                {done && !current && <span className="sr-only"> (done)</span>}
              </button>
            </Fragment>
          )
        })}
      </div>

      {/* Step panels */}
      <div
        id={`step-panel-${activeStep}`}
        role="tabpanel"
        aria-label={STEP_LABELS[activeStep]}
      >
        {activeStep === 0 && (
          <Step1Details
            employeeId={effectiveEmployeeId}
            onCreated={handleStep1Created}
            onContinue={() => goToStep(1)}
            initialDraftId={initialDraftId}
            initialFirstName={initialFirstName}
            initialLastName={initialLastName}
          />
        )}
        {/* Step 2 stays mounted (hidden) while the employee exists, so a selection not saved yet (accessories and
            accounts are only stored when sent to IT, D-05) survives switching tabs */}
        {effectiveEmployeeId !== null && (
          <div hidden={activeStep !== 1}>
            <Step2ItRequest
              employeeId={effectiveEmployeeId}
              onContinue={() => goToStep(2)}
            />
          </div>
        )}
        {activeStep === 2 && effectiveEmployeeId !== null && (
          <Step3Checklist employeeId={effectiveEmployeeId} />
        )}
      </div>
    </div>
  )
}
