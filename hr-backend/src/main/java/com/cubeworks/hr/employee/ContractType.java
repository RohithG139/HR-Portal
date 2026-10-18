package com.cubeworks.hr.employee;

/**
 * Employment contract type.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.contract.contract_type}.
 * A DB CHECK enforces the invariant {@code (contract_type = 'PERMANENT') = (end_date IS NULL)}:
 * permanent contracts have no end date; fixed-term contracts must have one.</p>
 */
public enum ContractType {
    /** Open-ended employment — no contract end date. */
    PERMANENT,
    /** Contract with a defined end date (fixed term). */
    FIXED_TERM
}
