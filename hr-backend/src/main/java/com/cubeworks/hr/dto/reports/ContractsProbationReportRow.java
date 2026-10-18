package com.cubeworks.hr.dto.reports;

import java.time.LocalDate;

/**
 * One row in the Contracts &amp; Probation report (S14 / C44 — {@code GET /api/reports/contracts-probation}).
 *
 * <p>Reads from {@code v_upcoming_dates}, which covers probation-end dates, contract-end
 * dates and PIP final reviews within the next 90 days.</p>
 *
 * @param employeeId   subject employee's primary key
 * @param employeeName subject employee's full name
 * @param eventType    {@code PROBATION}, {@code CONTRACT} or {@code PIP}
 * @param eventDate    the date the event occurs (probation end / contract end / PIP final)
 * @param daysUntil    calendar days until the event (may be 0 if today)
 * @param ownerRole    the role responsible for action (MANAGER / HR / MANAGER_HR)
 * @param eventStatus  status string (UPCOMING for date events; PIP status for PIP events)
 */
public record ContractsProbationReportRow(
        long employeeId,
        String employeeName,
        String eventType,
        LocalDate eventDate,
        int daysUntil,
        String ownerRole,
        String eventStatus) {
}
