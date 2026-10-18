package com.cubeworks.hr.dto.reports;

/**
 * One row in the Headcount report (S8 / C32 — {@code GET /api/reports/headcount}).
 *
 * <p>Aggregates employee counts per department using the {@code employee},
 * {@code department}, {@code contract} and {@code employee_exit} tables.</p>
 *
 * @param departmentId     department primary key
 * @param departmentName   human-readable department name
 * @param employeeCount    current non-ARCHIVED employees in the department
 * @param joinersThisYear  employees whose current contract started this calendar year
 * @param leaversThisYear  employees who left this calendar year (from employee_exit)
 * @param permanentCount   current employees on a PERMANENT contract
 * @param fixedTermCount   current employees on a FIXED_TERM contract
 */
public record HeadcountReportRow(
        long departmentId,
        String departmentName,
        long employeeCount,
        long joinersThisYear,
        long leaversThisYear,
        long permanentCount,
        long fixedTermCount) {
}
