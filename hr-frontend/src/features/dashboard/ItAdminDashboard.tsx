/**
 * @component ItAdminDashboard
 * @description W4 — IT admin dashboard (wireframe #it-dash): four KPI cards (equipment
 * requests, open setup items, access to revoke, not returned) with detail lines, and the
 * IT admin's own task list.
 * @props data - ItAdminDashboardDto - pre-fetched dashboard data
 * @example
 * <ItAdminDashboard data={data} />
 */

import type { ReactElement } from 'react'
import type { ItAdminDashboardDto } from '../../api/dashboard'
import { formatDate } from '../../lib/date'
import { DashboardTasks, StatCard } from './DashboardParts'

interface ItAdminDashboardProps {
  data: ItAdminDashboardDto
}

/** "Name · starts 21-10-2026" / "Name · 24-10-2026"; just the name when there is no date. */
function nameAndDate(name: string | null | undefined, date: string | null | undefined, prefix = ''): string {
  if (name == null) return ''
  return date != null ? `${name} · ${prefix}${formatDate(date)}` : name
}

export function ItAdminDashboard({ data }: ItAdminDashboardProps): ReactElement {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Equipment requests"
          value={data.equipmentRequestsCount}
          sub={nameAndDate(data.nextEquipmentName, data.nextEquipmentStartDate, 'starts ')}
        />
        <StatCard
          label="Open setup items"
          value={data.openSetupItemsCount}
          sub={(data.setupItemNames ?? []).join(' · ')}
        />
        <StatCard
          label="Access to revoke"
          value={data.accessToRevokeCount ?? 0}
          sub={nameAndDate(data.nextRevokeName, data.nextRevokeDate)}
        />
        <StatCard
          label="Not returned"
          value={data.notReturnedCount ?? 0}
          sub={(data.notReturnedCount ?? 0) > 0 ? 'Ex-employee' : ''}
        />
      </div>

      <DashboardTasks tasks={data.tasks} />
    </div>
  )
}
