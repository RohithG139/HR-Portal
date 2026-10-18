package com.cubeworks.hr.dto.workspace;

/**
 * Response for {@code POST /api/employees/{id}/manager-transfers} (W10, FRD-01 Wave 3).
 *
 * <p>Returns {@code 201 Created} with the new {@code manager_change} row's surrogate PK.
 * Shape matches F01's own {@code 201} conventions: a minimal wrapper with the created record's
 * ID (spec §6 W10 "[ASSUMPTION]"). If the effective date is today, the transfer is applied
 * immediately in an after-commit step; the response still conveys the {@code changeId} so
 * the caller can look it up (e.g. for a history entry).</p>
 *
 * @param changeId the surrogate PK of the newly created {@code manager_change} row
 */
public record TransferManagerResponse(Long changeId) {}
