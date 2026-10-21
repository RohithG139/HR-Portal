package com.cubeworks.hr.dto.workspace;

import java.util.List;

/**
 * Dashboard DTO for HR users (W01, FRD-01 Wave 3).
 *
 * <p>Fields marked as buildable now are populated immediately; fields for deferred features
 * (bench, PIP, appraisal, upcoming dates) are absent from this record and will be added
 * additively by F04/F07/F06/F08 respectively — the frontend must treat missing keys as
 * "not yet available" rather than null (additive DTO contract per spec §7 / D-W2).</p>
 *
 * @param activeEmployees      count of employees whose {@code stage = 'ACTIVE'}
 * @param activeEmployeesDelta count of employees who became ACTIVE in the current calendar month.
 *                             <strong>[ASSUMPTION]</strong>: uses {@code employee.stage_since}
 *                             falling within the current month, since the FRD does not specify the
 *                             exact computation (spec §5 W1 "[ASSUMPTION]"). The field is named
 *                             "delta" because the wireframe shows it as "+N this month".
 * @param onboardingCount      count of employees whose {@code stage = 'ONBOARDING'}
 * @param onboardingNames      up to 2 first+last names of employees currently onboarding
 * @param tasks                the HR user's open tasks (same shape as A36/S12 My Tasks)
 * @param upcoming             next dated events (probation/contract ends, PIP reviews, start
 *                             dates, last days), soonest first, at most 5
 */
public record HrDashboardDto(
        long activeEmployees,
        long activeEmployeesDelta,
        long onboardingCount,
        List<String> onboardingNames,
        List<DashboardTaskDto> tasks,
        List<UpcomingEventDto> upcoming
) {}
