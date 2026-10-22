package com.cubeworks.hr.model.probation;

/**
 * Probation review outcome (FRD-02 Screen 12): CONFIRM = "Confirm employment" (milestone
 * "Probation passed"), END = "End employment" (HR approval, then offboarding, ONB-08).
 * Stored in {@code review.outcome} and the case file's {@code Probation.outcome}.
 */
public enum ProbationOutcome {
    CONFIRM,
    END
}
