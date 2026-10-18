package com.cubeworks.hr.dto.reports;

import java.time.LocalDate;

/**
 * One row in the Onboarding report (S13 / C42 — {@code GET /api/reports/onboarding}).
 *
 * <p>Aggregates data from {@code onboarding}, {@code checklist_item} and
 * {@code checklist_template} for employees currently in the {@code IN_PROGRESS} onboarding
 * status.</p>
 *
 * @param employeeId    subject employee's primary key
 * @param employeeName  subject employee's full name
 * @param startDate     employment start date from the current contract; {@code null} if no contract
 * @param templateName  onboarding checklist template name
 * @param checklistPct  percentage of onboarding checklist items marked DONE (0–100)
 * @param itStatus      IT request status (NOT_SENT / SENT / IN_PROGRESS / COMPLETED)
 * @param openItems     number of checklist items still in OPEN status
 */
public record OnboardingReportRow(
        long employeeId,
        String employeeName,
        LocalDate startDate,
        String templateName,
        int checklistPct,
        String itStatus,
        long openItems) {
}
