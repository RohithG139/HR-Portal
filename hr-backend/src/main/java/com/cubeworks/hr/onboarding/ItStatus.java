package com.cubeworks.hr.onboarding;

/**
 * Progress status of the IT setup request for an onboarding.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in
 * {@code hr.onboarding.it_status}. A DB CHECK enforces that once the status is not
 * {@code NOT_SENT}, the mandatory IT fields ({@code it_laptop_model}, {@code it_ready_by},
 * {@code it_delivery_method}) must all be non-null.</p>
 */
public enum ItStatus {
    /** IT request has not yet been submitted by HR. */
    NOT_SENT,
    /** IT request has been sent to the IT admin. */
    SENT,
    /** IT admin has acknowledged the request and is preparing the equipment. */
    IN_PROGRESS,
    /** All IT items have been provisioned and the request is complete. */
    COMPLETED
}
