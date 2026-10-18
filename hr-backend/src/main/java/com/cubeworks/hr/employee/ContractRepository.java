package com.cubeworks.hr.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Contract}.
 *
 * <p>{@link #findByEmployeeIdAndIsCurrentTrue} matches the partial unique index
 * {@code ux_contract_current} — exactly one row per employee may have {@code is_current = true}.
 * This finder avoids an extra JOIN when checking for an existing current contract during
 * onboarding or renewal.</p>
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {

    /**
     * Returns the current (active) contract for an employee.
     *
     * <p>The partial unique index {@code ux_contract_current} guarantees at most one result.
     * Returns an empty Optional only if the employee has no contract at all (should not
     * happen after onboarding is complete).</p>
     *
     * @param employeeId the ID of the employee
     * @return the current contract, if any
     */
    Optional<Contract> findByEmployeeIdAndIsCurrentTrue(Long employeeId);
}
