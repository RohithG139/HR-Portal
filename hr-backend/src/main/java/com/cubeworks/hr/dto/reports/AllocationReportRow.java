package com.cubeworks.hr.dto.reports;

/**
 * One row in the Project Allocation report (S10 / C36 — {@code GET /api/reports/allocation}).
 *
 * <p>Reads from {@code v_employee_allocation}, which aggregates active project assignments
 * per employee and derives the allocation status.</p>
 *
 * @param employeeId        employee primary key
 * @param employeeName      full name
 * @param jobTitle          job title name
 * @param totalAllocationPct sum of allocation_pct across all active assignments (0 = on bench)
 * @param allocationStatus  one of: {@code ON_BENCH}, {@code PARTLY_ALLOCATED},
 *                          {@code FULLY_ALLOCATED}, {@code OVER_ALLOCATED}
 * @param projects          comma-separated names of active projects; {@code null} when on bench
 */
public record AllocationReportRow(
        long employeeId,
        String employeeName,
        String jobTitle,
        int totalAllocationPct,
        String allocationStatus,
        String projects) {
}
