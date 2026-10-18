/**
 * @component ManagerDashboard
 * @description W2 — Manager dashboard. Shows team member count, My Tasks list, and a
 * mini My Team table. Deferred cards (bench, reviews to write, plans to approve) are omitted
 * until F04/F05/F06 add their DTO fields (additive contract, spec §7 D-W2).
 * @props data - ManagerDashboardDto - pre-fetched dashboard data
 * @example
 * <ManagerDashboard data={data} />
 */

import type { ReactElement } from 'react'
import { Link } from 'react-router-dom'
import { Card, Badge, stageVariant } from '../../components/index'
import type { ManagerDashboardDto, DashboardTaskDto, TeamMemberSummaryDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'

interface ManagerDashboardProps {
  data: ManagerDashboardDto
}

/** R-UI-6: due-state colour for dashboard task rows. */
function dueStateClass(dueState: string): string {
  if (dueState === 'OVERDUE') return 'text-red-600'
  if (dueState === 'DUE_SOON') return 'text-orange-500'
  return 'text-gray-500'
}

/** Stage label map for display (R-UI-5). */
const stageLabel: Record<string, string> = {
  ONBOARDING: 'Onboarding',
  ACTIVE: 'Active',
  OFFBOARDING: 'Offboarding',
}

/**
 * ManagerDashboard renders the Manager role's dashboard view.
 *
 * The "Team members" card links to /team (AC-W06).
 */
export function ManagerDashboard({ data }: ManagerDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      {/* KPI cards row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* Team members count — links to /team (AC-W06) */}
        <Link
          to="/team"
          className="block focus-visible:ring-2 focus-visible:ring-blue-500 rounded-lg"
          aria-label={`Team members: ${data.teamMembers}`}
        >
          <Card className="hover:border-blue-300 transition-colors cursor-pointer">
            <p className="text-sm text-gray-500 font-medium">Team members</p>
            <p className="mt-2 text-3xl font-bold text-gray-900">{data.teamMembers}</p>
          </Card>
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* My Tasks */}
        <Card title="My Tasks" action={
          <Link
            to="/my-tasks"
            className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
          >
            View all
          </Link>
        }>
          {data.tasks.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">No open tasks. Well done!</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {data.tasks.map((task: DashboardTaskDto) => (
                <li key={task.taskId} className="py-3 flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{task.title}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    {task.dueDate !== null && (
                      <p className={`text-xs ${dueStateClass(task.dueState)}`}>
                        {formatDate(task.dueDate)}
                      </p>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* My Team mini-table */}
        <Card title="My Team" action={
          <Link
            to="/team"
            className="text-sm text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
          >
            View all
          </Link>
        }>
          {data.myTeam.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-4">Your team is empty.</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {data.myTeam.slice(0, 5).map((member: TeamMemberSummaryDto) => (
                <li key={member.employeeId} className="py-3 flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <Link
                      to={`/employees/${member.employeeId}`}
                      className="text-sm font-medium text-blue-600 hover:underline focus-visible:ring-2 focus-visible:ring-blue-500 rounded"
                    >
                      {member.firstName} {member.lastName}
                    </Link>
                    {member.jobTitle !== null && (
                      <p className="text-xs text-gray-500 truncate">{member.jobTitle}</p>
                    )}
                  </div>
                  <div className="shrink-0">
                    <Badge
                      label={stageLabel[member.stage] ?? member.stage}
                      variant={stageVariant(member.stage)}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
