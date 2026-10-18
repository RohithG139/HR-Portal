package com.cubeworks.hr.employee;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * An employment contract for one period of an employee's tenure.
 *
 * <p>Maps to {@code hr.contract}. Each renewal or conversion adds a new row; the current
 * contract is identified by {@link #isCurrent} = {@code true} and enforced as unique per
 * employee by the partial index {@code ux_contract_current} ({@code WHERE is_current}).
 * This means only one row per employee may have {@code is_current = true} at any time.</p>
 *
 * <p>A DB CHECK enforces the invariant
 * {@code (contract_type = 'PERMANENT') = (end_date IS NULL)}: permanent contracts have no
 * end date; fixed-term contracts must have one. This is not validated in Java because the
 * DB constraint is definitive and always enforced even for migrations or direct SQL access.</p>
 *
 * <p>The probation end date is computed in the {@code v_contract} view
 * ({@code start_date + probation_months}) and is not stored (R-DB-3).</p>
 */
@Entity
@Table(name = "contract")
public class Contract extends BaseEntity {

    /**
     * The employee this contract belongs to.
     * Lazy: avoid N+1 when listing all contracts of an employee — the employee object is
     * rarely needed when processing contracts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Whether this is a permanent or fixed-term contract. */
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 10, nullable = false)
    private ContractType contractType;

    /** Contract start date (inclusive). */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Contract end date (exclusive). Must be null for {@link ContractType#PERMANENT}
     * and non-null for {@link ContractType#FIXED_TERM} (DB CHECK).
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Contracted hours per week. DB CHECK: must be between 1 and 40.
     * Using {@code Short} to match the PostgreSQL {@code smallint} column type.
     */
    @Column(name = "hours_per_week", nullable = false)
    private Short hoursPerWeek;

    /**
     * Length of the probation period in months. DB CHECK: must be 0, 1 or 2.
     * 0 means no probation period. Using {@code Short} to match {@code smallint}.
     */
    @Column(name = "probation_months", nullable = false)
    private Short probationMonths;

    /**
     * Whether this is the currently active contract. The partial unique index
     * {@code ux_contract_current} guarantees at most one current contract per employee.
     * Set to {@code false} when a new contract row is created to replace this one.
     */
    @Column(name = "is_current", nullable = false)
    private boolean isCurrent = true;

    /**
     * No-arg constructor for JPA, and for application code to construct a new row via the
     * public setters below (this codebase uses plain setters rather than a builder, so the
     * constructor must be reachable from other feature packages, e.g. {@code onboarding}).
     */
    public Contract() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public ContractType getContractType() { return contractType; }
    public void setContractType(final ContractType contractType) { this.contractType = contractType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(final LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(final LocalDate endDate) { this.endDate = endDate; }

    public Short getHoursPerWeek() { return hoursPerWeek; }
    public void setHoursPerWeek(final Short hoursPerWeek) { this.hoursPerWeek = hoursPerWeek; }

    public Short getProbationMonths() { return probationMonths; }
    public void setProbationMonths(final Short probationMonths) { this.probationMonths = probationMonths; }

    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(final boolean current) { isCurrent = current; }
}
