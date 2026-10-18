package com.cubeworks.hr.asset;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * A physical asset in the company's asset register.
 *
 * <p>Maps to {@code hr.asset}. Asset history (issue, return, condition changes) is tracked
 * via {@code hr.audit_log} rows with {@code entity_type = 'ASSET'} rather than a separate
 * history table (data-model §4, former {@code asset_event} table removed in v2).</p>
 *
 * <p>DB CHECKs enforce the lifecycle invariants:</p>
 * <ul>
 *   <li>{@link AssetStatus#ISSUED}, {@link AssetStatus#RETURN_DUE} and
 *       {@link AssetStatus#NOT_RETURNED} require a non-null {@link #employee}.</li>
 *   <li>{@link AssetStatus#RETURNED} requires a non-null {@link #returnCondition}.</li>
 * </ul>
 *
 * <p>Assets are linked to checklist items (equipment items) via
 * {@code hr.checklist_item.asset_id} — NOT via a collection on this entity, to keep the
 * asset domain clean and avoid loading checklist data unnecessarily.</p>
 */
@Entity
@Table(name = "asset")
public class Asset extends BaseEntity {

    /**
     * Human-readable asset identifier, e.g. {@code LAP-0042}. Must be unique.
     */
    @Column(name = "asset_number", length = 20, nullable = false, unique = true)
    private String assetNumber;

    /** Category of the physical asset. */
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", length = 20, nullable = false)
    private AssetType assetType;

    /** Model name or description (e.g. "MacBook Pro 14-inch M3"). */
    @Column(name = "model", length = 100, nullable = false)
    private String model;

    /** Hardware serial number, or {@code null} if not recorded. */
    @Column(name = "serial_number", length = 60)
    private String serialNumber;

    /** Current disposition of the asset. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 15, nullable = false)
    private AssetStatus status = AssetStatus.IN_STORAGE;

    /**
     * The employee the asset is currently assigned to, or {@code null} if in storage.
     * Required for {@link AssetStatus#ISSUED}, {@link AssetStatus#RETURN_DUE} and
     * {@link AssetStatus#NOT_RETURNED} (DB CHECK).
     * Lazy: the employee object is rarely needed when querying the asset register.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Date the asset was issued to the current employee, or {@code null} if in storage. */
    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** Date by which the asset must be returned (set during offboarding). */
    @Column(name = "return_due_date")
    private LocalDate returnDueDate;

    /** Date the asset was returned to IT. */
    @Column(name = "returned_on")
    private LocalDate returnedOn;

    /**
     * Physical condition at return time.
     * Required when {@link #status} is {@link AssetStatus#RETURNED} (DB CHECK).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 10)
    private ReturnCondition returnCondition;

    /** Required no-arg constructor for JPA. */
    protected Asset() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getAssetNumber() { return assetNumber; }
    public void setAssetNumber(final String assetNumber) { this.assetNumber = assetNumber; }

    public AssetType getAssetType() { return assetType; }
    public void setAssetType(final AssetType assetType) { this.assetType = assetType; }

    public String getModel() { return model; }
    public void setModel(final String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(final String serialNumber) { this.serialNumber = serialNumber; }

    public AssetStatus getStatus() { return status; }
    public void setStatus(final AssetStatus status) { this.status = status; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(final LocalDate issuedOn) { this.issuedOn = issuedOn; }

    public LocalDate getReturnDueDate() { return returnDueDate; }
    public void setReturnDueDate(final LocalDate returnDueDate) { this.returnDueDate = returnDueDate; }

    public LocalDate getReturnedOn() { return returnedOn; }
    public void setReturnedOn(final LocalDate returnedOn) { this.returnedOn = returnedOn; }

    public ReturnCondition getReturnCondition() { return returnCondition; }
    public void setReturnCondition(final ReturnCondition returnCondition) { this.returnCondition = returnCondition; }
}
