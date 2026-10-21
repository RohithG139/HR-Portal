/**
 * @component HrDashboard
 * @description W1 — HR dashboard. Shows active employee count, onboarding snapshot,
 * and the HR user's own open task list. Deferred cards (bench, PIPs, appraisal, upcoming)
 * are omitted until F04/F07/F06/F08 add their DTO fields (additive contract, spec §7 D-W2).
 * @props data - HrDashboardDto - pre-fetched dashboard data
 * @example
 * <HrDashboard data={data} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import type { HrDashboardDto, UpcomingEventDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'
import { DashboardTasks, StatCard } from './DashboardParts'

interface HrDashboardProps {
  data: HrDashboardDto
}

/**
 * HrDashboard renders the HR role's dashboard view.
 *
 * AC-W06: the "In onboarding" card links to /employees?stage=ONBOARDING so clicking the
 * number opens the Employees list filtered to onboarding stage.
 * The "Active employees" card links to /employees?stage=ACTIVE.
 */
export function HrDashboard({ data }: HrDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      {/* KPI cards row — 4 columns to leave room for bench/PIP cards (deferred) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Active employees — links to /employees?stage=ACTIVE (AC-W06) */}
        <StatCard
          label="Active employees"
          value={data.activeEmployees}
          sub={data.activeEmployeesDelta > 0 ? `+${data.activeEmployeesDelta} this month` : ''}
          to="/employees?stage=ACTIVE"
          ariaLabel={`Active employees: ${data.activeEmployees}, plus ${data.activeEmployeesDelta} this month`}
        />
        {/* In onboarding — links to /employees?stage=ONBOARDING (AC-W06) */}
        <StatCard
          label="In onboarding"
          value={data.onboardingCount}
          sub={data.onboardingNames.join(' · ')}
          to="/employees?stage=ONBOARDING"
          ariaLabel={`In onboarding: ${data.onboardingCount}`}
        />
      </div>

      {/* My Tasks (2/3) beside Upcoming (1/3) on large screens, stacked below */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
      <DashboardTasks tasks={data.tasks} className="lg:col-span-2" />

      <UpcomingCard events={data.upcoming ?? []} />
      </div>
    </div>
  )
}

/** Title per event type, matching the wireframe's wording. */
function upcomingTitle(e: UpcomingEventDto): string {
  switch (e.type) {
    case 'PIP': return `Final PIP review · ${e.employeeName}`
    case 'START': return `${e.employeeName} starts`
    case 'LAST_DAY': return `Last day · ${e.employeeName}`
    case 'PROBATION': return `Probation ends · ${e.employeeName}`
    case 'CONTRACT': return `Contract ends · ${e.employeeName}`
    default: return e.employeeName
  }
}

/** Orange ring for review-type events (PIP, probation), blue for the rest (wireframe). */
function upcomingRingClass(type: string): string {
  return type === 'PIP' || type === 'PROBATION' ? 'border-orange-400' : 'border-blue-600'
}

/** "Upcoming" card: vertical timeline of the next dated events. */
function UpcomingCard({ events }: { events: UpcomingEventDto[] }): ReactElement {
  return (
    <section className="bg-white border border-gray-200 rounded-lg p-4 sm:p-6" aria-labelledby="hr-dash-upcoming">
      <h2 id="hr-dash-upcoming" className="pb-3 mb-4 border-b border-gray-200 text-base sm:text-lg font-semibold text-gray-900">
        Upcoming
      </h2>
      {events.length === 0 ? (
        <p className="text-sm text-gray-400 text-center py-4">Nothing in the next 90 days.</p>
      ) : (
        <ol className="relative">
          {/* Timeline line behind the rings */}
          <span aria-hidden="true" className="absolute left-[7px] top-2 bottom-2 w-px bg-gray-200" />
          {events.map((e) => (
            <li key={`${e.type}-${e.employeeId}-${e.date}`} className="relative flex gap-3 pb-4 last:pb-0">
              <span
                aria-hidden="true"
                className={`relative mt-0.5 shrink-0 w-4 h-4 rounded-full border-2 bg-white ${upcomingRingClass(e.type)}`}
              />
              <Link
                to={`/employees/${e.employeeId}`}
                className="min-w-0 rounded focus-visible:ring-2 focus-visible:ring-blue-500 group"
              >
                <p className="text-sm font-medium text-gray-900 group-hover:underline">{upcomingTitle(e)}</p>
                <p className="text-xs text-gray-500">{formatDate(e.date)}</p>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
