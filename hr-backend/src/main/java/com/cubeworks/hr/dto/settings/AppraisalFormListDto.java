package com.cubeworks.hr.dto.settings;

import java.time.OffsetDateTime;

/**
 * Response body for one row in the appraisal-form list (FRD-08 §6 C14).
 *
 * @param id           the form's surrogate PK
 * @param name         the form's display name
 * @param usedFor      "STANDARD", "LEADERSHIP", "PROBATION", or "PROJECT"
 * @param status       "DRAFT", "ACTIVE", or "ARCHIVED"
 * @param sectionCount number of sections on this form
 * @param updatedAt    timestamp of the most recent update (last-edited column in S3)
 */
public record AppraisalFormListDto(
        Long id,
        String name,
        String usedFor,
        String status,
        long sectionCount,
        OffsetDateTime updatedAt) {
}
