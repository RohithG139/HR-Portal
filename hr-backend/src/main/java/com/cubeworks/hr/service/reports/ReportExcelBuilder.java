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
        return buildWorkbook("Headcount", headers, data);
    }

    public static byte[] buildBench(final List<BenchReportRow> rows) {
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
        return buildWorkbook("Bench", headers, data);
    }

    public static byte[] buildAllocation(final List<AllocationReportRow> rows) {
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
        return buildWorkbook("Allocation", headers, data);
    }

    public static byte[] buildAppraisalStatus(final List<AppraisalStatusReportRow> rows) {
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
        return buildWorkbook("Appraisal Status", headers, data);
    }

    public static byte[] buildPips(final List<PipReportRow> rows) {
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
        return buildWorkbook("PIPs", headers, data);
    }

    public static byte[] buildOnboarding(final List<OnboardingReportRow> rows) {
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
        return buildWorkbook("Onboarding", headers, data);
    }

    public static byte[] buildContractsProbation(final List<ContractsProbationReportRow> rows) {
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
        return buildWorkbook("Contracts & Probation", headers, data);
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
        return buildWorkbook("My Team", headers, data);
    }

    public static byte[] buildUnreturnedAssets(final List<UnreturnedAssetsReportRow> rows) {
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
        return buildWorkbook("Unreturned Assets", headers, data);
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
     * Generic Excel builder shared by every {@code buildXxx} method above.
     *
     * <p>Creates a single sheet with a header row, one data row per {@code rows} entry, and
     * auto-sizes every column. This is the single place that owns POI boilerplate, so a style
     * change (e.g. header font, freeze pane) is made once here and inherited by all 8 exports.</p>
     *
     * @param sheetName the Excel sheet tab name
     * @param headers   column header strings for row 0
     * @param rows      data rows; each inner array must have the same length as {@code headers}
     * @return the workbook, serialised to bytes
     * @throws UncheckedIOException if POI fails to serialise the workbook — a write to an
     *         in-memory {@link ByteArrayOutputStream} failing is not an expected runtime
     *         condition, so callers (thin controller methods) are not forced to declare
     *         {@code throws IOException} for something that in practice never happens
     */
    private static byte[] buildWorkbook(final String sheetName,
                                         final String[] headers,
                                         final List<String[]> rows) {
        try (final Workbook wb = new XSSFWorkbook();
             final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            final Sheet sheet = wb.createSheet(sheetName);

            final Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (final String[] row : rows) {
                final Row xlRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < row.length; i++) {
                    xlRow.createCell(i).setCellValue(row[i] != null ? row[i] : "");
                }
            }

            // Auto-size all columns for readability (R-UI-2 export)
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            log.debug("Built Excel '{}' with {} data rows", sheetName, rows.size());
            return out.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to build Excel workbook: " + sheetName, e);
        }
    }
}
