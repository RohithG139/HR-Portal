package com.cubeworks.hr.asset;

/**
 * Current disposition of an asset in the register.
 *
 * <p>Stored as {@code varchar(15)} with a CHECK constraint in {@code hr.asset.status}.
 * DB CHECKs enforce:</p>
 * <ul>
 *   <li>{@code ISSUED}, {@code RETURN_DUE}, {@code NOT_RETURNED} require a non-null
 *       {@code employee_id}.</li>
 *   <li>{@code RETURNED} requires a non-null {@code return_condition}.</li>
 * </ul>
 */
public enum AssetStatus {
    /** Asset is available in storage, not assigned to any employee. */
    IN_STORAGE,
    /** Asset has been issued to an employee and is in active use. */
    ISSUED,
    /** Asset has been issued but a return is due (e.g. during offboarding). */
    RETURN_DUE,
    /** Asset has been returned and inspected. */
    RETURNED,
    /** Asset was not returned despite being due (escalation required). */
    NOT_RETURNED
}
