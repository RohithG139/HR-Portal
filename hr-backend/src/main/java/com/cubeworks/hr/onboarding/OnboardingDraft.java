package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Document;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A temporary, partially validated draft of step 1 of the onboarding form.
 *
 * <p>Maps to {@code hr.onboarding_draft}. Created when HR clicks "Save draft" on step 1
 * before all mandatory fields are valid. Only {@link #firstName} and {@link #lastName} are
 * verified at this point so the draft can appear in the Employees list. The raw form data
 * is stored as JSON in {@link #form} for round-trip fidelity; it is never read by business
 * logic — it is simply re-hydrated into the step-1 form when HR resumes editing.</p>
 *
 * <p>This row is <strong>deleted</strong> when HR clicks "Save &amp; continue": the service
 * layer validates all fields, creates the {@code employee} and {@code contract} rows, and
 * removes the draft atomically (F01 Q13, data-model change log 2026-10-15).</p>
 *
 * <p>The optional {@link #contractDocument} holds the contract PDF uploaded during the draft
 * phase; if present it is linked to the new {@code document} row when the draft is promoted.</p>
 */
@Entity
@Table(name = "onboarding_draft")
public class OnboardingDraft extends BaseEntity {

    /**
     * First name of the draft employee — the only field checked on "Save draft".
     * Required so HR can identify the draft in the Employees list.
     */
    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    /**
     * Last name of the draft employee — required for the same reason as {@link #firstName}.
     */
    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    /**
     * Raw step-1 form data as typed by HR, stored as PostgreSQL {@code jsonb}.
     * This field is never interpreted by business logic; it is purely for round-trip
     * fidelity when HR resumes editing the draft. The JSON structure mirrors the
     * React form state exactly.
     *
     * <p>Using {@code @JdbcTypeCode(SqlTypes.JSON)} so Hibernate 6 correctly handles
     * the PostgreSQL {@code jsonb} wire type without a custom converter.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form", columnDefinition = "jsonb", nullable = false)
    private String form;

    /**
     * An already-uploaded contract document linked to this draft, or {@code null} if
     * no contract has been uploaded yet. If set, the document is associated with the
     * new employee row when the draft is promoted.
     * Lazy: the document object is only needed during draft promotion, not when listing drafts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_document_id")
    private Document contractDocument;

    /** Required no-arg constructor for JPA. */
    protected OnboardingDraft() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getFirstName() { return firstName; }
    public void setFirstName(final String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(final String lastName) { this.lastName = lastName; }

    public String getForm() { return form; }
    public void setForm(final String form) { this.form = form; }

    public Document getContractDocument() { return contractDocument; }
    public void setContractDocument(final Document contractDocument) { this.contractDocument = contractDocument; }
}
