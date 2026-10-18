package com.cubeworks.hr.onboarding;

/**
 * Lifecycle status of an {@link Onboarding} record.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.onboarding.status}.
 * A DB CHECK enforces {@code (status = 'COMPLETED') = (completed_at IS NOT NULL)}.</p>
 */
public enum OnboardingStatus {
    /** Onboarding is underway; checklist items are being completed. */
    IN_PROGRESS,
    /** All mandatory checklist items are done and the HR task has been completed. */
    COMPLETED,
    /** Onboarding was cancelled before completion (e.g. employee did not join). */
    CANCELLED
}
