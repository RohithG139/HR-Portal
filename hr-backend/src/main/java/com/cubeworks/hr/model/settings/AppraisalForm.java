package com.cubeworks.hr.model.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * An appraisal form template managed by HR (FRD-08 §4.4).
 *
 * <p>Maps to {@code hr.appraisal_form}. {@code label_1}..{@code label_5} are the rating-scale
 * labels shown for {@link FormAnswerType#SCALE_1_5} questions on any section of this form —
 * defaulted in the schema to "Poor"/"Below expectations"/"Meets expectations"/"Exceeds
 * expectations"/"Outstanding" (FRD-08's own wording), editable by HR per form.</p>
 *
 * <p>A form cannot be activated ({@link #status} set to {@link FormStatus#ACTIVE}) without at
 * least one {@link FormSection} containing at least one {@link FormQuestion} — enforced by the
 * service layer, not a DB CHECK, since the DB cannot see across three tables conditionally
 * (FRD-08 spec §5 S3/S4, §6 C17).</p>
 *
 * <p>Archiving ({@link FormStatus#ARCHIVED}) removes a form from pickers for new reviews but
 * never touches an existing {@code review} row that already references it (ADM-04).</p>
 */
@Entity
@Table(name = "appraisal_form")
public class AppraisalForm extends BaseEntity {

    /** Human-readable form name. Must be unique. */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /** Who this form is used for. */
    @Enumerated(EnumType.STRING)
    @Column(name = "used_for", length = 12, nullable = false)
    private FormUsedFor usedFor;

    /** Lifecycle status. Defaults to {@link FormStatus#DRAFT} on creation. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private FormStatus status = FormStatus.DRAFT;

    /** How the overall score is computed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_score_method", length = 20, nullable = false)
    private OverallScoreMethod overallScoreMethod = OverallScoreMethod.WEIGHTED_AVERAGE;

    @Column(name = "label_1", length = 60, nullable = false)
    private String label1 = "Poor";

    @Column(name = "label_2", length = 60, nullable = false)
    private String label2 = "Below expectations";

    @Column(name = "label_3", length = 60, nullable = false)
    private String label3 = "Meets expectations";

    @Column(name = "label_4", length = 60, nullable = false)
    private String label4 = "Exceeds expectations";

    @Column(name = "label_5", length = 60, nullable = false)
    private String label5 = "Outstanding";

    /** Required no-arg constructor for JPA. */
    public AppraisalForm() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public FormUsedFor getUsedFor() { return usedFor; }
    public void setUsedFor(final FormUsedFor usedFor) { this.usedFor = usedFor; }

    public FormStatus getStatus() { return status; }
    public void setStatus(final FormStatus status) { this.status = status; }

    public OverallScoreMethod getOverallScoreMethod() { return overallScoreMethod; }
    public void setOverallScoreMethod(final OverallScoreMethod overallScoreMethod) { this.overallScoreMethod = overallScoreMethod; }

    public String getLabel1() { return label1; }
    public void setLabel1(final String label1) { this.label1 = label1; }

    public String getLabel2() { return label2; }
    public void setLabel2(final String label2) { this.label2 = label2; }

    public String getLabel3() { return label3; }
    public void setLabel3(final String label3) { this.label3 = label3; }

    public String getLabel4() { return label4; }
    public void setLabel4(final String label4) { this.label4 = label4; }

    public String getLabel5() { return label5; }
    public void setLabel5(final String label5) { this.label5 = label5; }
}
