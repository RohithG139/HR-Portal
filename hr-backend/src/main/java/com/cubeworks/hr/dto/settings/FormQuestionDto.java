package com.cubeworks.hr.dto.settings;

/**
 * Response body for a single form question (FRD-08 §6 C16, C21, C22).
 *
 * @param id          the question's surrogate PK
 * @param text        the question text
 * @param description optional supporting description
 * @param answerType  "SCALE_1_5", "YES_NO", or "TEXT"
 * @param required    whether an answer is required to submit the review
 * @param sortOrder   display position within the section
 */
public record FormQuestionDto(
        Long id,
        String text,
        String description,
        String answerType,
        boolean required,
        int sortOrder) {
}
