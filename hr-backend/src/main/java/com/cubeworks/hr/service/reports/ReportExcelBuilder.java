package com.cubeworks.hr.service.reports;

import com.cubeworks.hr.dto.reports.AllocationReportRow;
import com.cubeworks.hr.dto.workspace.TeamMemberDto;
import com.cubeworks.hr.dto.reports.AppraisalStatusReportRow;
import com.cubeworks.hr.dto.reports.BenchReportRow;
import com.cubeworks.hr.dto.reports.ContractsProbationReportRow;
import com.cubeworks.hr.dto.reports.HeadcountReportRow;
import com.cubeworks.hr.dto.reports.OnboardingReportRow;
import com.cubeworks.hr.dto.reports.PipReportRow;
import com.cubeworks.hr.dto.reports.UnreturnedAssetsReportRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds the Excel (.xlsx) export for each of FRD-08's 8 reports (C33/C35/C37/C39/C41/C43/C45/C47).
 *
 * <p>Extracted out of {@code ReportController} (Gate 5 code review finding — a ~500-line
 * controller with ~170 lines of POI/formatting logic violated R-CODE-1, "controllers stay
 * thin"). Every {@code buildXxx} method owns exactly one report's column headers and
 * row-to-string-array mapping; {@link #response} owns the shared HTTP response shape. The
 * controller's export endpoints call the same {@code ReportService} method as their JSON
 * sibling and then delegate here — the single-source-of-data guarantee (spec §7 MUST) is
 * unaffected by this refactor, since no query logic lives in this class.</p>
 */
public final class ReportExcelBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReportExcelBuilder.class);

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ReportExcelBuilder() {
        // static helper class — not instantiable
    }

    public static byte[] buildHeadcount(final List<HeadcountReportRow> rows) {
        return writeWorkbook(List.of(headcountSheet(rows)));
    }

    private static ReportSheet headcountSheet(final List<HeadcountReportRow> rows) {
        final String[] headers = {
                "Department", "Employee Count", "Joiners This Year",
                "Leavers This Year", "Permanent", "Fixed Term"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.departmentName(),
                String.valueOf(r.employeeCount()),
                String.valueOf(r.joinersThisYear()),
                String.valueOf(r.leaversThisYear()),
                String.valueOf(r.permanentCount()),
                String.valueOf(r.fixedTermCount())
        }).toList();
        return new ReportSheet("Headcount", headers, data);
    }

    public static byte[] buildBench(final List<BenchReportRow> rows) {
        return writeWorkbook(List.of(benchSheet(rows)));
    }

    private static ReportSheet benchSheet(final List<BenchReportRow> rows) {
        final String[] headers = {
                "Employee", "Job Title", "Manager", "Bench Since", "Days On Bench", "Last Project"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.jobTitle(),
                r.managerName() != null ? r.managerName() : "",
                r.benchSince() != null ? r.benchSince().toString() : "",
                r.daysOnBench() != null ? String.valueOf(r.daysOnBench()) : "",
                r.lastProjectName() != null ? r.lastProjectName() : ""
        }).toList();
        return new ReportSheet("Bench", headers, data);
    }

    public static byte[] buildAllocation(final List<AllocationReportRow> rows) {
        return writeWorkbook(List.of(allocationSheet(rows)));
    }

    private static ReportSheet allocationSheet(final List<AllocationReportRow> rows) {
        final String[] headers = {
                "Employee", "Job Title", "Total Allocation %", "Status", "Projects"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.jobTitle(),
                String.valueOf(r.totalAllocationPct()),
                r.allocationStatus(),
                r.projects() != null ? r.projects() : ""
        }).toList();
        return new ReportSheet("Allocation", headers, data);
    }

    public static byte[] buildAppraisalStatus(final List<AppraisalStatusReportRow> rows) {
        return writeWorkbook(List.of(appraisalStatusSheet(rows)));
    }

    private static ReportSheet appraisalStatusSheet(final List<AppraisalStatusReportRow> rows) {
        final String[] headers = {
                "Employee", "Manager", "Self Status", "Peers Submitted",
                "Peers Total", "Manager Review Status", "Manager Review Outcome",
                "Client Feedback Avg", "Client Feedback Count", "Result", "Appraisal Status"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.managerName() != null ? r.managerName() : "",
                r.selfStatus() != null ? r.selfStatus() : "",
                String.valueOf(r.peersSubmitted()),
                String.valueOf(r.peersTotal()),
                r.managerReviewStatus() != null ? r.managerReviewStatus() : "",
                r.managerReviewOutcome() != null ? r.managerReviewOutcome() : "",
                r.clientFeedbackAvg() != null ? r.clientFeedbackAvg().toPlainString() : "",
                String.valueOf(r.clientFeedbackCount()),
                r.result() != null ? r.result() : "",
                r.appraisalStatus() != null ? r.appraisalStatus() : ""
        }).toList();
        return new ReportSheet("Appraisal Status", headers, data);
    }

    public static byte[] buildPips(final List<PipReportRow> rows) {
        return writeWorkbook(List.of(pipsSheet(rows)));
    }

    private static ReportSheet pipsSheet(final List<PipReportRow> rows) {
        final String[] headers = {
                "Employee", "Manager", "Status",
                "Start Date", "Final Review Date", "Days Left", "Extensions"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.managerName() != null ? r.managerName() : "",
                r.status() != null ? r.status() : "",
                r.startDate() != null ? r.startDate().toString() : "",
                r.finalReviewDate() != null ? r.finalReviewDate().toString() : "",
                r.daysLeft() != null ? String.valueOf(r.daysLeft()) : "",
                String.valueOf(r.extensionCount())
        }).toList();
        return new ReportSheet("PIPs", headers, data);
    }

    public static byte[] buildOnboarding(final List<OnboardingReportRow> rows) {
        return writeWorkbook(List.of(onboardingSheet(rows)));
    }

    private static ReportSheet onboardingSheet(final List<OnboardingReportRow> rows) {
        final String[] headers = {
                "Employee", "Start Date", "Template",
                "Checklist %", "IT Status", "Open Items"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.startDate() != null ? r.startDate().toString() : "",
                r.templateName(),
                String.valueOf(r.checklistPct()),
                r.itStatus() != null ? r.itStatus() : "",
                String.valueOf(r.openItems())
        }).toList();
        return new ReportSheet("Onboarding", headers, data);
    }

    public static byte[] buildContractsProbation(final List<ContractsProbationReportRow> rows) {
        return writeWorkbook(List.of(contractsProbationSheet(rows)));
    }

    private static ReportSheet contractsProbationSheet(final List<ContractsProbationReportRow> rows) {
        final String[] headers = {
                "Employee", "Event Type", "Event Date", "Days Until", "Owner Role", "Status"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeName(),
                r.eventType(),
                r.eventDate() != null ? r.eventDate().toString() : "",
                String.valueOf(r.daysUntil()),
                r.ownerRole() != null ? r.ownerRole() : "",
                r.eventStatus() != null ? r.eventStatus() : ""
        }).toList();
        return new ReportSheet("Contracts & Probation", headers, data);
    }

    /**
     * Builds the My Team export workbook (W06, FRD-01 Wave 3).
     *
     * <p>Columns match the {@link com.cubeworks.hr.dto.workspace.TeamMemberDto} record.
     * Deferred columns (Current projects, Allocation, Development) are not included until F04/F05
     * add those tables — the export reflects exactly what is displayed in the table (R-UI-2 MUST).</p>
     *
     * @param rows the team member rows to serialise (same data as the My Team screen table)
     * @return the workbook bytes as {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}
     */
    public static byte[] buildTeam(final List<TeamMemberDto> rows) {
        return writeWorkbook(List.of(teamSheet(rows)));
    }

    private static ReportSheet teamSheet(final List<TeamMemberDto> rows) {
        final String[] headers = {
                "Employee Code", "First Name", "Last Name", "Job Title", "Stage"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.employeeCode() != null ? r.employeeCode() : "",
                r.firstName() != null ? r.firstName() : "",
                r.lastName() != null ? r.lastName() : "",
                r.jobTitle() != null ? r.jobTitle() : "",
                r.stage() != null ? r.stage() : ""
        }).toList();
        return new ReportSheet("My Team", headers, data);
    }

    public static byte[] buildUnreturnedAssets(final List<UnreturnedAssetsReportRow> rows) {
        return writeWorkbook(List.of(unreturnedAssetsSheet(rows)));
    }

    private static ReportSheet unreturnedAssetsSheet(final List<UnreturnedAssetsReportRow> rows) {
        final String[] headers = {
                "Asset Number", "Description", "Employee", "Last Working Day", "Status"
        };
        final List<String[]> data = rows.stream().map(r -> new String[]{
                r.assetNumber() != null ? r.assetNumber() : "",
                r.assetDescription() != null ? r.assetDescription() : "",
                r.employeeName() != null ? r.employeeName() : "",
                r.lastWorkingDay() != null ? r.lastWorkingDay().toString() : "",
                r.assetStatus() != null ? r.assetStatus() : ""
        }).toList();
        return new ReportSheet("Unreturned Assets", headers, data);
    }

    /**
     * Wraps a built workbook's bytes as a 200 response with the standard Excel content type
     * and {@code Content-Disposition: attachment} header (A05 pattern, {@code EmployeeController}).
     *
     * @param filename   the download filename (e.g. {@code "bench.xlsx"})
     * @param xlsxBytes  the workbook bytes from one of the {@code buildXxx} methods
     * @return 200 response with Excel body
     */
    public static ResponseEntity<byte[]> response(final String filename, final byte[] xlsxBytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(xlsxBytes);
    }

    /**
     * "Export all to Excel" (C48, S7): one workbook with one sheet per report, in the same order
     * as the overview's report cards. Each list comes from the same {@code ReportService} method
     * as that report's own JSON endpoint, unfiltered (spec §7 single source of data).
     */
    public static byte[] buildAll(final List<HeadcountReportRow> headcount,
                                  final List<BenchReportRow> bench,
                                  final List<AllocationReportRow> allocation,
                                  final List<AppraisalStatusReportRow> appraisalStatus,
                                  final List<PipReportRow> pips,
                                  final List<OnboardingReportRow> onboarding,
                                  final List<ContractsProbationReportRow> contractsProbation,
                                  final List<UnreturnedAssetsReportRow> unreturnedAssets) {
        return writeWorkbook(List.of(
                headcountSheet(headcount),
                benchSheet(bench),
                allocationSheet(allocation),
                appraisalStatusSheet(appraisalStatus),
                pipsSheet(pips),
                onboardingSheet(onboarding),
                contractsProbationSheet(contractsProbation),
                unreturnedAssetsSheet(unreturnedAssets)));
    }

    /** One sheet's tab name, header row and data rows. */
    private record ReportSheet(String name, String[] headers, List<String[]> rows) {
    }

    /**
     * Generic Excel writer shared by every {@code buildXxx} method above.
     *
     * <p>Creates one sheet per {@link ReportSheet}, each with a header row, one data row per
     * entry, and auto-sized columns. This is the single place that owns POI boilerplate, so a
     * style change (e.g. header font, freeze pane) is made once here and inherited by all exports.</p>
     *
     * @param sheets the sheets to write, in tab order; each row array must have the same length
     *               as its sheet's {@code headers}
     * @return the workbook, serialised to bytes
     * @throws UncheckedIOException if POI fails to serialise the workbook — a write to an
     *         in-memory {@link ByteArrayOutputStream} failing is not an expected runtime
     *         condition, so callers (thin controller methods) are not forced to declare
     *         {@code throws IOException} for something that in practice never happens
     */
    private static byte[] writeWorkbook(final List<ReportSheet> sheets) {
        try (final Workbook wb = new XSSFWorkbook();
             final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (final ReportSheet rs : sheets) {
                final Sheet sheet = wb.createSheet(rs.name());

                final Row headerRow = sheet.createRow(0);
                for (int i = 0; i < rs.headers().length; i++) {
                    headerRow.createCell(i).setCellValue(rs.headers()[i]);
                }

                int rowIdx = 1;
                for (final String[] row : rs.rows()) {
                    final Row xlRow = sheet.createRow(rowIdx++);
                    for (int i = 0; i < row.length; i++) {
                        xlRow.createCell(i).setCellValue(row[i] != null ? row[i] : "");
                    }
                }

                // Auto-size all columns for readability (R-UI-2 export)
                for (int i = 0; i < rs.headers().length; i++) {
                    sheet.autoSizeColumn(i);
                }
                log.debug("Built Excel sheet '{}' with {} data rows", rs.name(), rs.rows().size());
            }

            wb.write(out);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to build Excel workbook", e);
        }
    }
}
