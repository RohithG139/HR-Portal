package com.cubeworks.hr.controller.reports;

import com.cubeworks.hr.dto.reports.*;
import com.cubeworks.hr.service.reports.ReportExcelBuilder;
import com.cubeworks.hr.service.reports.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for FRD-08 report endpoints (C31–C47).
 *
 * <h2>Authorization</h2>
 * <p>All report endpoints are HR-only (FRD-08 §2, AC-17).  The {@code @PreAuthorize}
 * annotation is placed at class level so every method inherits it without repetition.</p>
 *
 * <h2>Single source of truth for data (spec §7 MUST)</h2>
 * <p>Each report's JSON endpoint and its corresponding {@code .../export} endpoint call
 * the <em>same</em> {@link ReportService} method.  The controller's export method hands the
 * returned {@code List<T>} to {@link ReportExcelBuilder}, which owns the column headers,
 * the row-to-string-array mapping and the POI workbook itself (R-CODE-1 — controllers stay
 * thin; extracted here in Gate 5's code review). There is no separate export code path that
 * could drift from the screen view.</p>
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('HR')")
public class ReportController {

    private final ReportService reportService;

    /**
     * Constructor injection (R-CODE-1, R-ARC-1).
     *
     * @param reportService the service holding all report queries
     */
    public ReportController(final ReportService reportService) {
        this.reportService = reportService;
    }

    // ── C31 — Summary ─────────────────────────────────────────────────────────

    /**
     * C31 — Summary tile: four KPI numbers shown on the reports overview (S7).
     *
     * @return 200 with {@link SummaryDto}
     */
    @GetMapping("/summary")
    public ResponseEntity<SummaryDto> getSummary() {
        return ResponseEntity.ok(reportService.getSummary());
    }

    // ── C32 / C33 — Headcount ─────────────────────────────────────────────────

    /**
     * C32 — Headcount report: one row per department (S8).
     *
     * @param departmentId optional filter — when supplied, returns only that department's row
     * @return 200 with list of headcount rows
     */
    @GetMapping("/headcount")
    public ResponseEntity<List<HeadcountReportRow>> getHeadcount(
            @RequestParam(required = false) final Long departmentId) {
        return ResponseEntity.ok(reportService.getHeadcount(departmentId));
    }

    /**
     * C33 — Headcount report Excel export (S8, AC-05).
     *
     * <p>Calls the same service method as C32 — data is always identical to the screen view.</p>
     *
     * @param departmentId optional filter
     * @return 200 with Excel attachment
     */
    @GetMapping("/headcount/export")
    public ResponseEntity<byte[]> exportHeadcount(
            @RequestParam(required = false) final Long departmentId) {
        final byte[] xlsx = ReportExcelBuilder.buildHeadcount(reportService.getHeadcount(departmentId));
        return ReportExcelBuilder.response("headcount.xlsx", xlsx);
    }

    // ── C34 / C35 — Bench ─────────────────────────────────────────────────────

    /**
     * C34 — Bench report: employees with no active project assignment (S9).
     *
     * @param managerId optional filter on line manager
     * @param q         optional free-text search on employee name
     * @param sort      sort column ({@code employeeName}, {@code benchSince}, {@code daysOnBench})
     * @param dir       {@code asc} or {@code desc}
     * @return 200 with list of bench rows
     */
    @GetMapping("/bench")
    public ResponseEntity<List<BenchReportRow>> getBench(
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        return ResponseEntity.ok(reportService.getBench(managerId, q, sort, dir));
    }

    /**
     * C35 — Bench report Excel export (S9, AC-05).
     *
     * <p>Calls the same service method as C34 — data is always identical to the screen view.</p>
     *
     * @param managerId optional filter on line manager
     * @param q         optional free-text search on employee name
     * @param sort      sort column
     * @param dir       sort direction
     * @return 200 with Excel attachment
     */
    @GetMapping("/bench/export")
    public ResponseEntity<byte[]> exportBench(
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        final byte[] xlsx = ReportExcelBuilder.buildBench(reportService.getBench(managerId, q, sort, dir));
        return ReportExcelBuilder.response("bench.xlsx", xlsx);
    }

    // ── C36 / C37 — Allocation ────────────────────────────────────────────────

    /**
     * C36 — Project Allocation report: current allocation per active employee (S10).
     *
     * @param status optional filter on allocation status
     *               (ON_BENCH / PARTLY_ALLOCATED / FULLY_ALLOCATED / OVER_ALLOCATED)
     * @param q      optional free-text search on employee name
     * @param sort   sort column ({@code employeeName}, {@code totalAllocationPct}, {@code allocationStatus})
     * @param dir    {@code asc} or {@code desc}
     * @return 200 with list of allocation rows
     */
    @GetMapping("/allocation")
    public ResponseEntity<List<AllocationReportRow>> getAllocation(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        return ResponseEntity.ok(reportService.getAllocation(status, q, sort, dir));
    }

    /**
     * C37 — Project Allocation report Excel export (S10, AC-05).
     *
     * @param status optional filter on allocation status
     * @param q      optional free-text search on employee name
     * @param sort   sort column
     * @param dir    sort direction
     * @return 200 with Excel attachment
     */
    @GetMapping("/allocation/export")
    public ResponseEntity<byte[]> exportAllocation(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        final byte[] xlsx = ReportExcelBuilder.buildAllocation(reportService.getAllocation(status, q, sort, dir));
        return ReportExcelBuilder.response("allocation.xlsx", xlsx);
    }

    // ── C38 / C39 — Appraisal Status ─────────────────────────────────────────

    /**
     * C38 — Appraisal Status report: per-employee review progress in the open round (S11).
     *
     * @param managerId optional filter on line manager
     * @param result    optional filter on appraisal outcome
     * @param q         optional free-text search on employee name
     * @param sort      sort column ({@code employeeName})
     * @param dir       {@code asc} or {@code desc}
     * @return 200 with list of appraisal status rows
     */
    @GetMapping("/appraisal-status")
    public ResponseEntity<List<AppraisalStatusReportRow>> getAppraisalStatus(
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String result,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        return ResponseEntity.ok(reportService.getAppraisalStatus(managerId, result, q, sort, dir));
    }

    /**
     * C39 — Appraisal Status report Excel export (S11, AC-05).
     *
     * @param managerId optional filter on line manager
     * @param result    optional filter on appraisal outcome
     * @param q         optional free-text search on employee name
     * @param sort      sort column
     * @param dir       sort direction
     * @return 200 with Excel attachment
     */
    @GetMapping("/appraisal-status/export")
    public ResponseEntity<byte[]> exportAppraisalStatus(
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String result,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        final byte[] xlsx = ReportExcelBuilder.buildAppraisalStatus(
                reportService.getAppraisalStatus(managerId, result, q, sort, dir));
        return ReportExcelBuilder.response("appraisal-status.xlsx", xlsx);
    }

    // ── C40 / C41 — PIPs ─────────────────────────────────────────────────────

    /**
     * C40 — PIPs report: active and pending improvement plans (S12).
     *
     * @param status optional filter on PIP status
     * @param sort   sort column ({@code employeeName}, {@code status}, {@code startDate})
     * @param dir    {@code asc} or {@code desc}
     * @return 200 with list of PIP rows
     */
    @GetMapping("/pips")
    public ResponseEntity<List<PipReportRow>> getPips(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        return ResponseEntity.ok(reportService.getPips(status, sort, dir));
    }

    /**
     * C41 — PIPs report Excel export (S12, AC-05).
     *
     * @param status optional filter on PIP status
     * @param sort   sort column
     * @param dir    sort direction
     * @return 200 with Excel attachment
     */
    @GetMapping("/pips/export")
    public ResponseEntity<byte[]> exportPips(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        final byte[] xlsx = ReportExcelBuilder.buildPips(reportService.getPips(status, sort, dir));
        return ReportExcelBuilder.response("pips.xlsx", xlsx);
    }

    // ── C42 / C43 — Onboarding ────────────────────────────────────────────────

    /**
     * C42 — Onboarding report: employees currently in IN_PROGRESS onboarding (S13).
     *
     * @param sort sort column ({@code employeeName}, {@code startDate})
     * @param dir  {@code asc} or {@code desc}
     * @return 200 with list of onboarding rows
     */
    @GetMapping("/onboarding")
    public ResponseEntity<List<OnboardingReportRow>> getOnboarding(
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        return ResponseEntity.ok(reportService.getOnboarding(sort, dir));
    }

    /**
     * C43 — Onboarding report Excel export (S13, AC-05).
     *
     * @param sort sort column
     * @param dir  sort direction
     * @return 200 with Excel attachment
     */
    @GetMapping("/onboarding/export")
    public ResponseEntity<byte[]> exportOnboarding(
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String dir) {
        final byte[] xlsx = ReportExcelBuilder.buildOnboarding(reportService.getOnboarding(sort, dir));
        return ReportExcelBuilder.response("onboarding.xlsx", xlsx);
    }

    // ── C44 / C45 — Contracts & Probation ────────────────────────────────────

    /**
     * C44 — Contracts &amp; Probation report: events within the next 90 days (S14).
     *
     * @param type optional filter on event type (PROBATION / CONTRACT / PIP)
     * @return 200 with list of upcoming date rows
     */
    @GetMapping("/contracts-probation")
    public ResponseEntity<List<ContractsProbationReportRow>> getContractsProbation(
            @RequestParam(required = false) final String type) {
        return ResponseEntity.ok(reportService.getContractsProbation(type));
    }

    /**
     * C45 — Contracts &amp; Probation report Excel export (S14, AC-05).
     *
     * @param type optional filter on event type
     * @return 200 with Excel attachment
     */
    @GetMapping("/contracts-probation/export")
    public ResponseEntity<byte[]> exportContractsProbation(
            @RequestParam(required = false) final String type) {
        final byte[] xlsx = ReportExcelBuilder.buildContractsProbation(
                reportService.getContractsProbation(type));
        return ReportExcelBuilder.response("contracts-probation.xlsx", xlsx);
    }

    // ── C46 / C47 — Unreturned Assets ────────────────────────────────────────

    /**
     * C46 — Unreturned Assets report: assets in RETURN_DUE or NOT_RETURNED status (S15).
     *
     * @param status optional filter on asset status (RETURN_DUE / NOT_RETURNED)
     * @return 200 with list of unreturned asset rows
     */
    @GetMapping("/unreturned-assets")
    public ResponseEntity<List<UnreturnedAssetsReportRow>> getUnreturnedAssets(
            @RequestParam(required = false) final String status) {
        return ResponseEntity.ok(reportService.getUnreturnedAssets(status));
    }

    /**
     * C47 — Unreturned Assets report Excel export (S15, AC-05).
     *
     * @param status optional filter on asset status
     * @return 200 with Excel attachment
     */
    @GetMapping("/unreturned-assets/export")
    public ResponseEntity<byte[]> exportUnreturnedAssets(
            @RequestParam(required = false) final String status) {
        final byte[] xlsx = ReportExcelBuilder.buildUnreturnedAssets(
                reportService.getUnreturnedAssets(status));
        return ReportExcelBuilder.response("unreturned-assets.xlsx", xlsx);
    }
}
