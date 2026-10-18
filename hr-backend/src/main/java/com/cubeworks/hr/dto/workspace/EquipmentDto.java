package com.cubeworks.hr.dto.workspace;

/**
 * A single issued asset shown in the Employee dashboard equipment card (W03, FRD-01 Wave 3).
 *
 * <p>Maps from {@link com.cubeworks.hr.asset.Asset} rows where
 * {@code status IN ('ISSUED', 'RETURN_DUE', 'NOT_RETURNED')} and
 * {@code employee_id = caller.id} — same query as the onboarding S7 equipment list (F01).</p>
 *
 * @param assetId     surrogate PK of the asset row
 * @param assetNumber human-readable asset identifier (e.g. {@code CW-LT-0042})
 * @param assetType   category code (e.g. {@code "LAPTOP"})
 * @param model       model name or description (e.g. "MacBook Pro 14-inch M3")
 */
public record EquipmentDto(
        Long assetId,
        String assetNumber,
        String assetType,
        String model
) {}
