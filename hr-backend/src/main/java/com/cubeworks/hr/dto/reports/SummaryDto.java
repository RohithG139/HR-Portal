package com.cubeworks.hr.dto.reports;

import java.math.BigDecimal;

/**
 * DTO for the reports overview KPI tile summary (C31 — {@code GET /api/reports/summary}).
 *
 * <p>Composed from the same underlying queries as the individual detail reports so there
 * is a single source of truth for each number (spec §7).</p>
 *
 * @param totalHeadcount        count of non-ARCHIVED employees
 * @param benchCount            count of employees with no active project assignment
 * @param appraisalCompletionRate percentage (0–100) of appraisals finalised in the current
 *                              open round; 0.0 when no round is open or no appraisals exist
 * @param unreturnedAssetCount  count of assets in RETURN_DUE or NOT_RETURNED status
 * @param headcountChangeVsLastMonth joiners minus leavers over the last month (may be negative);
 *                              caption "+2 vs last month" on the Headcount tile
 * @param billableStaffCount    employees in the allocation pool ({@code v_employee_allocation});
 *                              denominator for the Bench tile's "8% of billable staff" caption
 * @param appraisalRoundYear    year of the open round's review period end; {@code null} when no
 *                              round is open — caption "Round 2026" on the Appraisal tile
 * @param unreturnedAssetExEmployeeCount distinct employees holding unreturned assets whose last
 *                              working day has passed — caption "1 ex-employee" on the Assets tile
 */
public record SummaryDto(
        long totalHeadcount,
        long benchCount,
        BigDecimal appraisalCompletionRate,
        long unreturnedAssetCount,
        long headcountChangeVsLastMonth,
        long billableStaffCount,
        Integer appraisalRoundYear,
        long unreturnedAssetExEmployeeCount) {
}
