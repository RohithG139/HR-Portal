package com.cubeworks.hr.dto.reports;

import java.time.LocalDate;

/**
 * One row in the Bench report (S9 / C34 — {@code GET /api/reports/bench}).
 *
 * <p>Reads from {@code v_bench}, which exposes employees with no active project
 * assignment, enriched with the date they went on bench and their last project.</p>
 *
 * @param employeeId      employee primary key
 * @param employeeName    full name (first + last)
 * @param jobTitle        job title name
 * @param managerId       manager's employee ID, {@code null} if no manager
 * @param managerName     manager's full name, {@code null} if no manager
 * @param benchSince      date the employee went on bench; {@code null} if uncomputable
 * @param daysOnBench     days since {@code benchSince}; {@code null} when benchSince is null
 * @param lastProjectName name of the most recent project, {@code null} if never assigned
 */
public record BenchReportRow(
        long employeeId,
        String employeeName,
        String jobTitle,
        Long managerId,
        String managerName,
        LocalDate benchSince,
        Integer daysOnBench,
        String lastProjectName) {
}
