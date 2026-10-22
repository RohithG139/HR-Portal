package com.cubeworks.hr.model.probation;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Contract;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.model.settings.AppraisalForm;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row of {@code hr.review} (V13). Only the PROBATION review type is mapped by this feature
 * (FRD-02 §4.7): {@code contract_id} is set, {@code appraisal_id}/{@code assignment_id} stay null
 * (DB CHECK {@code num_nonnulls(...) = 1}). Appraisal/peer/project reviews (FRD-03/05) reuse the
 * same table later and will add their own columns to this mapping.
 *
 * <p>Outcome and HR decision are stored as plain strings because the column's allowed values
 * depend on {@code review_type} (DB CHECK); {@link ProbationOutcome} / {@link HrDecision} hold the
 * PROBATION values.</p>
 */
@Entity
@Table(name = "review")
public class Review extends BaseEntity {

    @Column(name = "review_type", length = 10, nullable = false)
    private String reviewType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private AppraisalForm form;

    /** The manager who performs the review (FRD-02 Screen 12). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private Employee reviewer;

    /** The contract whose probation is reviewed; its employee is the reviewed employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    /** Probation end date (ONB-01): the review is due before probation ends. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private ReviewStatus status = ReviewStatus.DRAFT;

    /** The engine's real "Probation review" task id, once resolved (null until then). */
    @Column(name = "engine_task_id", length = 64, unique = true)
    private String engineTaskId;

    @Column(name = "outcome", length = 25)
    private String outcome;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "hr_decision", length = 10)
    private String hrDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_decided_by")
    private Employee hrDecidedBy;

    @Column(name = "hr_decided_at")
    private OffsetDateTime hrDecidedAt;

    /** Required no-arg constructor for JPA. */
    public Review() {}

    public String getReviewType() { return reviewType; }
    public void setReviewType(final String reviewType) { this.reviewType = reviewType; }

    public AppraisalForm getForm() { return form; }
    public void setForm(final AppraisalForm form) { this.form = form; }

    public Employee getReviewer() { return reviewer; }
    public void setReviewer(final Employee reviewer) { this.reviewer = reviewer; }

    public Contract getContract() { return contract; }
    public void setContract(final Contract contract) { this.contract = contract; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(final LocalDate dueDate) { this.dueDate = dueDate; }

    public ReviewStatus getStatus() { return status; }
    public void setStatus(final ReviewStatus status) { this.status = status; }

    public String getEngineTaskId() { return engineTaskId; }
    public void setEngineTaskId(final String engineTaskId) { this.engineTaskId = engineTaskId; }

    public String getOutcome() { return outcome; }
    public void setOutcome(final String outcome) { this.outcome = outcome; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(final OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getHrDecision() { return hrDecision; }
    public void setHrDecision(final String hrDecision) { this.hrDecision = hrDecision; }

    public Employee getHrDecidedBy() { return hrDecidedBy; }
    public void setHrDecidedBy(final Employee hrDecidedBy) { this.hrDecidedBy = hrDecidedBy; }

    public OffsetDateTime getHrDecidedAt() { return hrDecidedAt; }
    public void setHrDecidedAt(final OffsetDateTime hrDecidedAt) { this.hrDecidedAt = hrDecidedAt; }
}
