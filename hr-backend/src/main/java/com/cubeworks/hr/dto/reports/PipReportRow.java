package com.cubeworks.hr.dto.reports;

import java.time.LocalDate;

/**
 * One row in the PIPs report (S12 / C40 — {@code GET /api/reports/pips}).
 *
 * <p>Reads from {@code v_pip} (joined with {@code checkin} inside the view for
 * extension count and final-review status).</p>
 *
 * @param pipId           PIP primary key
 * @param employeeId      subject employee's primary key
 * @param employeeName    subject employee's full name
 * @param managerId       PIP manager's employee ID (snapshot at PIP creation time)
 * @param managerName     PIP manager's full name
 * @param status          current PIP status
 * @param startDate       date the PIP started; {@code null} if not yet started (DRAFT/IN_HR_REVIEW)
 * @param finalReviewDate calculated end date (start + duration_days); {@code null} when startDate is null
 * @param daysLeft        days until final review for ACTIVE PIPs; {@code null} otherwise
 * @param extensionCount  number of times the PIP was extended (EXTEND outcomes in PIP_FINAL checkins)
 */
public record PipReportRow(
        long pipId,
        long employeeId,
        String employeeName,
        Long managerId,
        String managerName,
        String status,
        LocalDate startDate,
        LocalDate finalReviewDate,
        Integer daysLeft,
        long extensionCount) {
}
