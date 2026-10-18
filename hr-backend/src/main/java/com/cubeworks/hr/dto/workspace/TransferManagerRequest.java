package com.cubeworks.hr.dto.workspace;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/employees/{id}/manager-transfers} (W10, FRD-01 Wave 3).
 *
 * <p>Business validation (required fields, date in past, task ownership) is performed in the
 * service layer (spec §5 W6 / §7 W10) to ensure the exact FRD-specified error messages are
 * returned (R-SRC-3). Size constraints are applied here via Bean Validation to prevent
 * excessively large payloads from reaching the service (fix #8).</p>
 *
 * @param newManagerId   ID of the employee to become the new manager; required, must differ
 *                       from the current manager — error: "Choose a new manager."
 * @param effectiveDate  ISO {@code yyyy-MM-dd} date when the transfer takes effect; required,
 *                       must not be before today — error: "The effective date cannot be in the past."
 * @param reason         optional free-text reason for the transfer; max 2000 characters
 * @param taskIdsToMove  surrogate PKs of {@code engine_task} rows HR ticked to move to the
 *                       new manager; unticked tasks remain with the current manager; may be
 *                       empty (nothing ticked is valid); max 200 entries
 */
public record TransferManagerRequest(
        Long newManagerId,
        String effectiveDate,
        @Size(max = 2000) String reason,
        @Size(max = 200) List<Long> taskIdsToMove
) {}
