package com.cubeworks.hr.dto.workspace;

import java.time.LocalDate;

/**
 * One row of the HR dashboard's "Upcoming" card (wireframe #hr-dash).
 *
 * @param type         PROBATION, CONTRACT or PIP (from {@code v_upcoming_dates}),
 *                     START (onboarding employee's first day) or LAST_DAY (approved exit)
 * @param employeeId   the employee the event is about
 * @param employeeName first + last name
 * @param date         the event date
 */
public record UpcomingEventDto(
        String type,
        long employeeId,
        String employeeName,
        LocalDate date
) {}
