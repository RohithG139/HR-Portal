/**
 * @component OverviewTab
 * @description Employee record — Overview tab (S11, A31).
 * Read-only card showing personal, contact and employment details.
 * Visible to HR (all employees) and Manager (own team only — enforced server-side).
 * @props employeeId - number - the employee to display
 * @example
 * <OverviewTab employeeId={42} />
 */

import type { ReactElement } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getEmployeeOverview } from '../../../api/employees'
import { Card, Badge, stageVariant } from '../../../components/index'
import { formatDate } from '../../../lib/date'

interface OverviewTabProps {
  employeeId: number
}

/** OverviewTab shows read-only employee details sourced from A31. */
export function OverviewTab({ employeeId }: OverviewTabProps): ReactElement {
  const { data, isLoading } = useQuery({
    queryKey: ['employee-overview', employeeId],
    queryFn: () => getEmployeeOverview(employeeId),
  })

  if (isLoading || data === undefined) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-6 bg-gray-200 rounded w-40" />
        <div className="h-32 bg-gray-200 rounded" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">
            {data.firstName} {data.lastName}
          </h2>
          <p className="text-sm text-gray-500">{data.employeeCode}</p>
        </div>
        {data.stage !== null && (
          <Badge label={data.stage} variant={stageVariant(data.stage)} />
        )}
      </div>

      <Card title="Personal details">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          <div>
            <dt className="text-gray-500">Private e-mail</dt>
            <dd className="text-gray-900">{data.privateEmail}</dd>
          </div>
          {data.workEmail !== null && (
            <div>
              <dt className="text-gray-500">Work e-mail</dt>
              <dd className="text-gray-900">{data.workEmail}</dd>
            </div>
          )}
          <div>
            <dt className="text-gray-500">Mobile phone</dt>
            <dd className="text-gray-900">{data.mobilePhone}</dd>
          </div>
          {data.dateOfBirth !== null && (
            <div>
              <dt className="text-gray-500">Date of birth</dt>
              <dd className="text-gray-900">{formatDate(data.dateOfBirth)}</dd>
            </div>
          )}
          {data.nationality !== null && (
            <div>
              <dt className="text-gray-500">Nationality</dt>
              <dd className="text-gray-900">{data.nationality}</dd>
            </div>
          )}
          <div className="sm:col-span-2">
            <dt className="text-gray-500">Address</dt>
            <dd className="text-gray-900">
              {data.street}, {data.postcode} {data.city}
            </dd>
          </div>
        </dl>
      </Card>

      <Card title="Employment">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          {data.jobTitle !== null && (
            <div>
              <dt className="text-gray-500">Job title</dt>
              <dd className="text-gray-900">{data.jobTitle}</dd>
            </div>
          )}
          {data.department !== null && (
            <div>
              <dt className="text-gray-500">Department</dt>
              <dd className="text-gray-900">{data.department}</dd>
            </div>
          )}
          {data.grade !== null && (
            <div>
              <dt className="text-gray-500">Grade</dt>
              <dd className="text-gray-900">{data.grade}</dd>
            </div>
          )}
          {data.managerName !== null && (
            <div>
              <dt className="text-gray-500">Manager</dt>
              <dd className="text-gray-900">{data.managerName}</dd>
            </div>
          )}
          {data.accountStatus !== null && (
            <div>
              <dt className="text-gray-500">Account status</dt>
              <dd className="text-gray-900">{data.accountStatus}</dd>
            </div>
          )}
        </dl>
      </Card>
    </div>
  )
}
