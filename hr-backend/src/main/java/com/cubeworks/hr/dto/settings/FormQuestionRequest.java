package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or updating a form question (FRD-08 §6 C21, C22).
 *
 * @param text        the question text; required
 * @param description optional supporting description
 * @param answerType  required; one of SCALE_1_5 / YES_NO / TEXT
 * @param required    whether an answer is required to submit the review
 */
public record FormQuestionRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 200)
        String text,

        @Size(max = 300)
        String description,

        @NotNull(message = "This field is required.")
        String answerType,

        boolean required) {
}
