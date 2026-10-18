package com.cubeworks.hr.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Onboarding}.
 *
 * <p>{@link #findByEmployeeId} matches the unique constraint on {@code employee_id}:
 * there is exactly one onboarding record per employee. This finder is used to load
 * the onboarding detail for a given employee without requiring a join.</p>
 */
public interface OnboardingRepository extends JpaRepository<Onboarding, Long> {

    /**
     * Returns the onboarding record for a given employee.
     *
     * <p>The unique constraint on {@code employee_id} guarantees at most one result.</p>
     *
     * @param employeeId the ID of the employee
     * @return the onboarding record, if it exists
     */
    Optional<Onboarding> findByEmployeeId(Long employeeId);
}
