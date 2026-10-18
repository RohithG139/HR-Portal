package com.cubeworks.hr.model.settings;

/**
 * How a {@link FormQuestion} is answered.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.form_question.answer_type}.</p>
 */
public enum FormAnswerType {
    /** A 1-5 rating, using the owning form's {@code label_1}..{@code label_5}. */
    SCALE_1_5,
    /** A yes/no answer. */
    YES_NO,
    /** Free text. */
    TEXT
}
