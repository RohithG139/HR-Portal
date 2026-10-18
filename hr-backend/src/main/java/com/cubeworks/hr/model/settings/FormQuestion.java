package com.cubeworks.hr.model.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * A single question within a {@link FormSection} (FRD-08 §4.4).
 *
 * <p>Maps to {@code hr.form_question}. The parent {@link FormSection} uses
 * {@code ON DELETE CASCADE}, so deleting a section removes its questions automatically.</p>
 */
@Entity
@Table(name = "form_question")
public class FormQuestion extends BaseEntity {

    /**
     * The section that owns this question.
     * Lazy: listing questions never requires materialising the parent section object.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private FormSection section;

    /** Question text. */
    @Column(name = "text", length = 200, nullable = false)
    private String text;

    /** Optional supporting description shown under the question text. */
    @Column(name = "description", length = 300)
    private String description;

    /** How this question is answered. */
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", length = 10, nullable = false)
    private FormAnswerType answerType;

    /** Whether an answer is required to submit the review. */
    @Column(name = "required", nullable = false)
    private boolean required = true;

    /** Display order within the section. Lower values appear first. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Required no-arg constructor for JPA. */
    public FormQuestion() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public FormSection getSection() { return section; }
    public void setSection(final FormSection section) { this.section = section; }

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }

    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }

    public FormAnswerType getAnswerType() { return answerType; }
    public void setAnswerType(final FormAnswerType answerType) { this.answerType = answerType; }

    public boolean isRequired() { return required; }
    public void setRequired(final boolean required) { this.required = required; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(final int sortOrder) { this.sortOrder = sortOrder; }
}
