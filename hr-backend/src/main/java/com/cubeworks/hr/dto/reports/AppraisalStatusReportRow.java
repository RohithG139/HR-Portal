package com.cubeworks.hr.dto.reports;

import java.math.BigDecimal;

/**
 * One row in the Appraisal Status report (S11 / C38 — {@code GET /api/reports/appraisal-status}).
 *
 * <p>Reads from {@code v_appraisal_status}, which is scoped to the currently open appraisal
 * round.  Returns empty when no round has {@code status = 'OPEN'}.</p>
 *
 * @param appraisalId          appraisal primary key
 * @param employeeId           subject employee's primary key
 * @param employeeName         subject employee's full name
 * @param managerId            line manager's employee ID; {@code null} if no manager
 * @param managerName          line manager's full name; {@code null} if no manager
 * @param selfStatus           self-review status (e.g. DRAFT, SUBMITTED); {@code null} if not requested
 * @param peersSubmitted       number of peer reviews already submitted
 * @param peersTotal           number of peer reviews requested (excluding DECLINED/CANCELLED/REPLACED)
 * @param managerReviewStatus  manager review status; {@code null} if not started
 * @param managerReviewOutcome manager review outcome (PROMOTE/MEETS/BELOW); {@code null} if not submitted
 * @param clientFeedbackAvg    average client-feedback rating for this employee; {@code null} if none
 * @param clientFeedbackCount  number of client-feedback records for this employee
 * @param result               appraisal outcome (PROMOTED/NO_CHANGE/UNDER_PERFORMING); {@code null} if open
 * @param appraisalStatus      appraisal row status (OPEN/FINALISED/CANCELLED)
 */
public record AppraisalStatusReportRow(
        long appraisalId,
        long employeeId,
        String employeeName,
        Long managerId,
        String managerName,
        String selfStatus,
        long peersSubmitted,
        long peersTotal,
        String managerReviewStatus,
        String managerReviewOutcome,
        BigDecimal clientFeedbackAvg,
        long clientFeedbackCount,
        String result,
        String appraisalStatus) {
}
