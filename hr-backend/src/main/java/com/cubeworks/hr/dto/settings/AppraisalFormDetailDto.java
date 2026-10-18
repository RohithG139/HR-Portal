package com.cubeworks.hr.dto.settings;

import java.util.List;

/**
 * Full-detail response for an appraisal form, with all sections and questions nested
 * (FRD-08 §6 C16).
 *
 * @param id                 the form's surrogate PK
 * @param name               the form's display name
 * @param usedFor            "STANDARD", "LEADERSHIP", "PROBATION", or "PROJECT"
 * @param status             "DRAFT", "ACTIVE", or "ARCHIVED"
 * @param overallScoreMethod "WEIGHTED_AVERAGE" or "MANAGER_DECIDES"
 * @param label1             rating label for score 1
 * @param label2             rating label for score 2
 * @param label3             rating label for score 3
 * @param label4             rating label for score 4
 * @param label5             rating label for score 5
 * @param version            optimistic-lock version (returned to the client for C17)
 * @param sections           sections with their questions, ordered by sort_order
 */
public record AppraisalFormDetailDto(
        Long id,
        String name,
        String usedFor,
        String status,
        String overallScoreMethod,
        String label1,
        String label2,
        String label3,
        String label4,
        String label5,
        Integer version,
        List<FormSectionDto> sections) {
}
