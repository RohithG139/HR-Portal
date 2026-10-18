package com.cubeworks.hr.employee;

import com.cubeworks.hr.common.audit.AuditLogRepository;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.security.CurrentUser;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for employee management endpoints.
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>A04 {@code GET /api/employees} — paginated list with filters</li>
 *   <li>A05 {@code GET /api/employees/export} — Excel export</li>
 *   <li>A31 {@code GET /api/employees/{id}} — overview tab</li>
 *   <li>A32 {@code GET /api/employees/{id}/profile} — profile tab</li>
 *   <li>A33 {@code PUT /api/employees/{id}/profile} — HR profile edit</li>
 *   <li>A34 {@code POST /api/employees/{id}/account/resend-invite} — resend set-password e-mail</li>
 *   <li>A35 {@code GET /api/employees/{id}/history} — history tab</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic lives in {@link EmployeeService}.</p>
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     */
    public EmployeeController(final EmployeeService employeeService,
                               final AuditLogRepository auditLogRepository,
                               final CurrentUser currentUser) {
        this.employeeService = employeeService;
        this.auditLogRepository = auditLogRepository;
        this.currentUser = currentUser;
    }

    /**
     * A04 — Paginated, filterable list of employees and drafts (S1 / D-W9).
     *
     * <p>IT admin callers receive a reduced read-only projection (D-W9, FRD-01 Wave 3):
     * {@code employeeCode, firstName, lastName, jobTitle, stage, assetCount} — all other fields
     * are null. The role-branch is applied server-side in {@link EmployeeService#listEmployees};
     * the caller's role is never a request parameter (R-API-2).</p>
     *
     * @param stage        filter by lifecycle stage
     * @param departmentId filter by department
     * @param managerId    filter by manager
     * @param q            free-text search
     * @param sort         sort field (default: lastName)
     * @param page         zero-based page index (default: 0)
     * @param size         page size (default: 20)
     * @return 200 with paged employee list
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR','IT_ADMIN')")
    public ResponseEntity<EmployeeService.EmployeePageDto> listEmployees(
            @RequestParam(required = false) final EmployeeStage stage,
            @RequestParam(required = false) final Long departmentId,
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        final Employee caller = currentUser.require();
        // Fix #7: clamp page/size to safe bounds to prevent abuse (negative page, huge page load).
        // exportEmployees intentionally calls the service directly with size=10_000 and must NOT
        // be clamped — only this paginated endpoint is clamped.
        final int clampedPage = Math.max(page, 0);
        final int clampedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(
                employeeService.listEmployees(stage, departmentId, managerId, q,
                        sort, clampedPage, clampedSize, caller));
    }

    /**
     * A05 — Excel export of the employee list with the same filters as A04.
     *
     * @param stage        filter by stage
     * @param departmentId filter by department
     * @param managerId    filter by manager
     * @param q            free-text search
     * @return 200 with Excel file as {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<byte[]> exportEmployees(
            @RequestParam(required = false) final EmployeeStage stage,
            @RequestParam(required = false) final Long departmentId,
            @RequestParam(required = false) final Long managerId,
            @RequestParam(required = false) final String q) throws IOException {

        final Employee caller = currentUser.require();
        // Load all (no pagination) for export — use large page to get all rows.
        // For very large datasets this should be streamed; for gate 2 a page of 10_000 is safe.
        final EmployeeService.EmployeePageDto data =
                employeeService.listEmployees(stage, departmentId, managerId, q,
                        "lastName", 0, 10_000, caller);

        final byte[] bytes = buildExcel(data.items());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employees.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /**
     * A31 — Overview tab: basic identity, contract and stage data for the employee record.
     *
     * @param id the employee ID
     * @return 200 with overview DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeService.EmployeeOverviewDto> getOverview(
            @PathVariable final Long id) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(employeeService.getOverview(id, caller));
    }

    /**
     * A32 — Profile tab: full employee profile including HR-only fields.
     *
     * @param id the employee ID
     * @return 200 with profile DTO
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<EmployeeService.EmployeeProfileDto> getProfile(
            @PathVariable final Long id) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(employeeService.getProfile(id, caller));
    }

    /**
     * A33 — HR profile edit: updates profile fields and writes audit_log (WS-05).
     *
     * @param id      the employee ID
     * @param request updated profile fields
     * @return 200 with updated profile
     */
    @PutMapping("/{id}/profile")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeService.EmployeeProfileDto> updateProfile(
            @PathVariable final Long id,
            @Valid @RequestBody final EmployeeService.UpdateProfileRequest request) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(employeeService.updateProfile(id, request, caller));
    }

    /**
     * A34 — HR resends the "Set your password" e-mail to the employee.
     *
     * @param id the employee ID
     * @return 202 accepted; {@code 409 ALREADY_LOGGED_IN} if the account is already ACTIVE
     */
    @PostMapping("/{id}/account/resend-invite")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> resendInvite(@PathVariable final Long id) {
        final Employee caller = currentUser.require();
        employeeService.resendInvite(id, caller);
        return ResponseEntity.accepted().build();
    }

    /**
     * A35 — History tab: paginated audit log and milestone entries for the employee.
     *
     * <p>Builds each row with {@link HashMap} rather than {@link Map#of}: {@code before}/
     * {@code after} are legitimately {@code null} for CREATE/DELETE actions and {@code entityId}
     * can be null too (data-model §5 comment on {@code AuditLog}) — {@code Map.of} throws
     * {@code NullPointerException} on any null value, which is why this previously silently
     * omitted {@code entityId}/{@code before}/{@code after} entirely rather than crashing, and
     * used the wrong field names ({@code changedAt}/{@code changedBy}) that never matched the
     * frontend's {@code HistoryEntryDto} ({@code createdAt}/{@code actorName}) — found live,
     * 2026-10-18 (crashed the whole History tab for any employee with at least one real entry).
     *
     * @param id   the employee ID
     * @param page zero-based page index
     * @return 200 with audit log entries
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Object> getHistory(
            @PathVariable final Long id,
            @RequestParam(defaultValue = "0") final int page) {
        final Employee caller = currentUser.require();
        employeeService.getHistory(id, page, caller);
        final var auditPage = auditLogRepository.findByEmployeeId(
                id, PageRequest.of(page, 20, Sort.by("changedAt").descending()));
        final List<Map<String, Object>> content = new java.util.ArrayList<>();
        for (final var a : auditPage.getContent()) {
            final Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("id", a.getId());
            entry.put("entityType", a.getEntityType());
            entry.put("entityId", a.getEntityId());
            entry.put("action", a.getAction());
            entry.put("actorName", a.getChangedBy() != null
                    ? a.getChangedBy().getFirstName() + " " + a.getChangedBy().getLastName()
                    : "System");
            entry.put("before", a.getBeforeValue());
            entry.put("after", a.getAfterValue());
            entry.put("createdAt", a.getChangedAt().toString());
            content.add(entry);
        }
        return ResponseEntity.ok(Map.of(
                "content", content,
                "totalElements", auditPage.getTotalElements(),
                "totalPages", auditPage.getTotalPages(),
                "page", page
        ));
    }

    // ── Excel helpers ─────────────────────────────────────────────────────────

    /**
     * Builds an Excel workbook from the employee list items (R-UI-2, A05).
     *
     * @param items the list of employee rows to include
     * @return the workbook bytes as a byte array
     */
    private byte[] buildExcel(final List<EmployeeService.EmployeeListItemDto> items)
            throws IOException {
        try (final Workbook wb = new XSSFWorkbook();
             final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            final Sheet sheet = wb.createSheet("Employees");
            // Header row
            final Row header = sheet.createRow(0);
            final String[] cols = {"Employee Code", "First Name", "Last Name",
                    "Job Title", "Department", "Manager", "Stage", "Account Status"};
            for (int i = 0; i < cols.length; i++) {
                final Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
            }
            // Data rows
            int rowIdx = 1;
            for (final EmployeeService.EmployeeListItemDto item : items) {
                final Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.employeeCode() != null ? item.employeeCode() : "DRAFT");
                row.createCell(1).setCellValue(item.firstName());
                row.createCell(2).setCellValue(item.lastName());
                row.createCell(3).setCellValue(item.jobTitle() != null ? item.jobTitle() : "");
                row.createCell(4).setCellValue(item.department() != null ? item.department() : "");
                row.createCell(5).setCellValue(item.managerName() != null ? item.managerName() : "");
                row.createCell(6).setCellValue(item.stage() != null ? item.stage() : "");
                row.createCell(7).setCellValue(item.accountStatus() != null ? item.accountStatus() : "");
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
