package com.cubeworks.hr.asset;

/**
 * Physical condition of an asset when it was returned.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in
 * {@code hr.asset.return_condition}. Required when {@code status = 'RETURNED'}
 * (enforced by a DB CHECK).</p>
 */
public enum ReturnCondition {
    /** Asset returned in good, undamaged condition. */
    GOOD,
    /** Asset returned with visible damage. */
    DAMAGED
}
