/**
 * taskRoute: where a task's OPEN button goes. Regression for IT admins: checklist items of
 * an employee whose Equipment request is still open must open the Equipment request page
 * (its "Mark setup done" works); the Remaining setup page has no task to complete then.
 */

import { describe, expect, it } from 'vitest'
import { equipmentRequestEmployees, taskRoute } from './taskRoute'

describe('taskRoute — IT admin', () => {
  const tasks = [
    { title: 'Equipment request', employeeId: 30 },
    { title: 'Microsoft 365', employeeId: 30 },
    { title: 'VPN', employeeId: 13 },
  ]
  const open = equipmentRequestEmployees(tasks)

  it('opens the Equipment request page for the request itself', () => {
    expect(taskRoute('IT_ADMIN', 'Equipment request', 30, open)).toBe('/it/equipment-requests/30')
  })

  it('opens the Equipment request page for items of an employee with an open request', () => {
    expect(taskRoute('IT_ADMIN', 'Microsoft 365', 30, open)).toBe('/it/equipment-requests/30')
  })

  it('opens Remaining setup for items of an employee with no open request', () => {
    expect(taskRoute('IT_ADMIN', 'VPN', 13, open)).toBe('/it/setup/13')
  })
})
