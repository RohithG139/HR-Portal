package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.ChecklistTemplate;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The onboarding record for one employee — created when HR submits step 1 ("Save &amp; continue").
 *
 * <p>Maps to {@code hr.onboarding}. The unique constraint on {@link #employee} (via the
 * partial index {@code ux_contract_current}) ensures there is exactly one onboarding per
 * employee. The IT-request fields (prefixed {@code it_}) replace the separate {@code it_request}
 * table from v1 (data-model §4).</p>
 *
 * <p>DB CHECKs enforce:</p>
 * <ul>
 *   <li>{@code (status = 'COMPLETED') = (completed_at IS NOT NULL)}</li>
 *   <li>Once {@link #itStatus} is not {@link ItStatus#NOT_SENT}, the IT detail fields
 *       ({@link #itLaptopModel}, {@link #itReadyBy}, {@link #itDeliveryMethod}) must all
 *       be non-null.</li>
 *   <li>{@link #idChecked} AND {@link #contractSignedReceived} must both be true before
 *       the onboarding can be completed (FRD constraint).</li>
 * </ul>
 *
 * <p>The {@link #rightToWorkVerified} flag is a separate boolean used by HR to confirm the
 * right-to-work check; it is tracked but does not block completion in the DB (the service
 * layer enforces any conditional logic from the FRD).</p>
 */
@Entity
@Table(name = "onboarding")
public class Onboarding extends BaseEntity {

    /**
     * The employee this onboarding belongs to. Must be unique (one onboarding per employee).
     * Using @OneToOne because the FK is on this side and is unique, making the relationship
     * semantically one-to-one even though the column is a FK.
     * Lazy: the employee object is rarely needed when working with onboarding-specific fields.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /**
     * The checklist template used to instantiate the onboarding checklist items.
     * Lazy: the template detail is only needed when displaying the checklist setup screen.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    /**
     * Whether HR has verified that the employee's identity document was checked.
     * Together with {@link #contractSignedReceived}, this must be {@code true} before
     * the onboarding can be marked complete (DB CHECK: {@code id_checked AND contract_signed_received}).
     */
    @Column(name = "id_checked", nullable = false)
    private boolean idChecked;

    /**
     * Whether HR has confirmed that the signed contract has been received.
     * Part of the same DB CHECK as {@link #idChecked}.
     */
    @Column(name = "contract_signed_received", nullable = false)
    private boolean contractSignedReceived;

    /** Whether HR has verified the employee's right to work in the relevant jurisdiction. */
    @Column(name = "right_to_work_verified", nullable = false)
    private boolean rightToWorkVerified;

    /** Current lifecycle status of the onboarding. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private OnboardingStatus status = OnboardingStatus.IN_PROGRESS;

    /**
     * Timestamp when the onboarding was marked complete.
     * Non-null if and only if {@link #status} is {@link OnboardingStatus#COMPLETED} (DB CHECK).
     */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * The HR employee who marked the onboarding complete.
     * Lazy: rarely needed outside the completion flow.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private Employee completedBy;

    // ── IT request fields ────────────────────────────────────────────────────

    /** Laptop model requested for this employee (e.g. "MacBook Pro 14"). */
    @Column(name = "it_laptop_model", length = 60)
    private String itLaptopModel;

    /** Keyboard layout preference (e.g. "UK", "US QWERTY"). */
    @Column(name = "it_keyboard_layout", length = 30)
    private String itKeyboardLayout;

    /**
     * Date by which IT must have the equipment ready.
     * Required once the IT request is submitted (DB CHECK).
     */
    @Column(name = "it_ready_by")
    private LocalDate itReadyBy;

    /**
     * How the equipment will be delivered to the employee.
     * Required once the IT request is submitted (DB CHECK).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "it_delivery_method", length = 20)
    private ItDeliveryMethod itDeliveryMethod;

    /** Free-text notes for the IT admin (accessories, special requirements, etc.). */
    @Column(name = "it_notes", columnDefinition = "text")
    private String itNotes;

    /** Status of the IT provisioning request. Defaults to not sent until HR submits it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "it_status", length = 12, nullable = false)
    private ItStatus itStatus = ItStatus.NOT_SENT;

    /** Timestamp when the IT request was submitted to the IT admin. */
    @Column(name = "it_sent_at")
    private OffsetDateTime itSentAt;

    /** Required no-arg constructor for JPA. */
    protected Onboarding() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(final ChecklistTemplate template) { this.template = template; }

    public boolean isIdChecked() { return idChecked; }
    public void setIdChecked(final boolean idChecked) { this.idChecked = idChecked; }

    public boolean isContractSignedReceived() { return contractSignedReceived; }
    public void setContractSignedReceived(final boolean contractSignedReceived) { this.contractSignedReceived = contractSignedReceived; }

    public boolean isRightToWorkVerified() { return rightToWorkVerified; }
    public void setRightToWorkVerified(final boolean rightToWorkVerified) { this.rightToWorkVerified = rightToWorkVerified; }

    public OnboardingStatus getStatus() { return status; }
    public void setStatus(final OnboardingStatus status) { this.status = status; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(final OffsetDateTime completedAt) { this.completedAt = completedAt; }

    public Employee getCompletedBy() { return completedBy; }
    public void setCompletedBy(final Employee completedBy) { this.completedBy = completedBy; }

    public String getItLaptopModel() { return itLaptopModel; }
    public void setItLaptopModel(final String itLaptopModel) { this.itLaptopModel = itLaptopModel; }

    public String getItKeyboardLayout() { return itKeyboardLayout; }
    public void setItKeyboardLayout(final String itKeyboardLayout) { this.itKeyboardLayout = itKeyboardLayout; }

    public LocalDate getItReadyBy() { return itReadyBy; }
    public void setItReadyBy(final LocalDate itReadyBy) { this.itReadyBy = itReadyBy; }

    public ItDeliveryMethod getItDeliveryMethod() { return itDeliveryMethod; }
    public void setItDeliveryMethod(final ItDeliveryMethod itDeliveryMethod) { this.itDeliveryMethod = itDeliveryMethod; }

    public String getItNotes() { return itNotes; }
    public void setItNotes(final String itNotes) { this.itNotes = itNotes; }

    public ItStatus getItStatus() { return itStatus; }
    public void setItStatus(final ItStatus itStatus) { this.itStatus = itStatus; }

    public OffsetDateTime getItSentAt() { return itSentAt; }
    public void setItSentAt(final OffsetDateTime itSentAt) { this.itSentAt = itSentAt; }
}
