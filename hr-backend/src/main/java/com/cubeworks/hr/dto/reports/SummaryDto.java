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
 */
public record SummaryDto(
        long totalHeadcount,
        long benchCount,
        BigDecimal appraisalCompletionRate,
        long unreturnedAssetCount) {
}
