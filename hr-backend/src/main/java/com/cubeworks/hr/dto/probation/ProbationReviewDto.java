package com.cubeworks.hr.dto.probation;

import java.util.List;

/**
 * P01 response: the probation review form (FRD-02 Screen 12) with its context panel, and the
 * HR decision state for Screen 13 (HR action bar on the same page, spec D-P4).
 *
 * @param id                 review id
 * @param version            optimistic-lock version (R-DB-5); send back on P02/P03
 * @param employeeId         reviewed employee
 * @param employeeCode       reviewed employee's code (= case id)
 * @param employeeName       "First Last"
 * @param managerName        the reviewer
 * @param status             DRAFT / SUBMITTED
 * @param dueDate            review due date = probation end date (ISO)
 * @param startDate          contract start date (ISO)
 * @param probationEndDate   ONB-01 (ISO)
 * @param currentProject     always null until FRD-03 (spec §5 [OPEN], rendered "—")
 * @param onboardingPercent  onboarding checklist completion (0-100)
 * @param buddyName          buddy, or null
 * @param formName           the PROBATION appraisal form used
 * @param scaleLabels        the form's 5 rating labels (label_1 … label_5)
 * @param questions          questions with the saved answers
 * @param outcome            CONFIRM / END, or null
 * @param submittedAt        ISO-8601 UTC, or null
 * @param hrDecision         APPROVED / REJECTED, or null
 * @param hrDecidedAt        ISO-8601 UTC, or null
 * @param editable           true when the caller may save/submit (the employee's manager, draft)
 * @param awaitingHrDecision true when HR may approve/reject now (ONB-08)
 * @param canDecide          true when the caller is HR and may decide (not self, G-13)
 */
public record ProbationReviewDto(Long id, Integer version, Long employeeId, String employeeCode,
                                 String employeeName, String managerName, String status,
                                 String dueDate, String startDate, String probationEndDate,
                                 String currentProject, int onboardingPercent, String buddyName,
                                 String formName, List<String> scaleLabels,
                                 List<ProbationQuestionDto> questions, String outcome,
                                 String submittedAt, String hrDecision, String hrDecidedAt,
                                 boolean editable, boolean awaitingHrDecision, boolean canDecide) {
}
