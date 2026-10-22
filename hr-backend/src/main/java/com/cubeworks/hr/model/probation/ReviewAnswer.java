package com.cubeworks.hr.model.probation;

import com.cubeworks.hr.common.AuditableEntity;
import jakarta.persistence.*;

/**
 * One answer of a review (V17 {@code hr.review_answer}). At most one of {@code rating},
 * {@code yesNo}, {@code textAnswer} is set (DB CHECK), matching the question's answer type.
 */
@Entity
@Table(name = "review_answer")
public class ReviewAnswer extends AuditableEntity {

    @EmbeddedId
    private ReviewAnswerId id;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "yes_no")
    private Boolean yesNo;

    @Column(name = "text_answer")
    private String textAnswer;

    @Column(name = "comment")
    private String comment;

    /** Required no-arg constructor for JPA. */
    public ReviewAnswer() {}

    public ReviewAnswer(final ReviewAnswerId id) {
        this.id = id;
    }

    public ReviewAnswerId getId() { return id; }

    public Short getRating() { return rating; }
    public void setRating(final Short rating) { this.rating = rating; }

    public Boolean getYesNo() { return yesNo; }
    public void setYesNo(final Boolean yesNo) { this.yesNo = yesNo; }

    public String getTextAnswer() { return textAnswer; }
    public void setTextAnswer(final String textAnswer) { this.textAnswer = textAnswer; }

    public String getComment() { return comment; }
    public void setComment(final String comment) { this.comment = comment; }
}
