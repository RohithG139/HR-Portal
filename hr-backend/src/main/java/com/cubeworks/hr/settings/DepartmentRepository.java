package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link Department}.
 *
 * <p>The derived finder {@link #findByName} supports duplicate-name validation before
 * an INSERT touches the DB unique constraint, enabling a clean service-layer validation
 * error instead of a constraint-violation exception (R-API-3).</p>
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /**
     * Finds a department by its unique name.
     *
     * <p>Used for duplicate-name validation: if a non-empty result is returned and the
     * returned ID differs from the current entity's ID, the name is already taken.</p>
     *
     * @param name the exact department name to look up (case-sensitive)
     * @return the matching department, if any
     */
    Optional<Department> findByName(String name);
}
