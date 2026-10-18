package com.cubeworks.hr.dto.reports;

import java.time.LocalDate;

/**
 * One row in the Unreturned Assets report (S15 / C46 — {@code GET /api/reports/unreturned-assets}).
 *
 * <p>Reads from {@code v_unreturned_assets}, which exposes assets in {@code RETURN_DUE}
 * or {@code NOT_RETURNED} status, joined with the ex-employee's last working day from
 * {@code employee_exit}.</p>
 *
 * @param assetId          asset primary key
 * @param assetNumber      unique asset registration number
 * @param assetDescription human-readable description ({@code asset_type - model})
 * @param employeeId       assigned employee's primary key; {@code null} if no longer assigned
 * @param employeeName     assigned employee's full name; {@code null} if unassigned
 * @param lastWorkingDay   last working day from the employee's exit record; {@code null} if none
 * @param assetStatus      {@code RETURN_DUE} or {@code NOT_RETURNED}
 */
public record UnreturnedAssetsReportRow(
        long assetId,
        String assetNumber,
        String assetDescription,
        Long employeeId,
        String employeeName,
        LocalDate lastWorkingDay,
        String assetStatus) {
}
