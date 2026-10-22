package com.cubeworks.hr.dto.probation;

/**
 * One question of the probation form with its saved answer (P01).
 *
 * @param questionId  form_question id
 * @param section     section name
 * @param text        question text (e.g. "Quality of work")
 * @param description optional help text
 * @param answerType  SCALE_1_5 / YES_NO / TEXT
 * @param required    whether an answer is required to submit
 * @param rating      saved 1-5 rating, or null
 * @param yesNo       saved yes/no, or null
 * @param textAnswer  saved text (the summary for the TEXT question), or null
 */
public record ProbationQuestionDto(Long questionId, String section, String text, String description,
                                   String answerType, boolean required, Integer rating,
                                   Boolean yesNo, String textAnswer) {
}
