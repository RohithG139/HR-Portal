package com.cubeworks.hr.model.probation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@code hr.review_answer}: {@code (review_id, question_id)}. */
@Embeddable
public class ReviewAnswerId implements Serializable {

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** Required no-arg constructor for JPA. */
    public ReviewAnswerId() {}

    public ReviewAnswerId(final Long reviewId, final Long questionId) {
        this.reviewId = reviewId;
        this.questionId = questionId;
    }

    public Long getReviewId() { return reviewId; }
    public Long getQuestionId() { return questionId; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ReviewAnswerId other)) return false;
        return Objects.equals(reviewId, other.reviewId) && Objects.equals(questionId, other.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewId, questionId);
    }
}
