package com.cubeworks.hr.model.probation;

/** {@code review.status} values (V13 CHECK). A probation review only uses DRAFT and SUBMITTED. */
public enum ReviewStatus {
    REQUESTED,
    DRAFT,
    SUBMITTED,
    DECLINED,
    REPLACED,
    CANCELLED
}
