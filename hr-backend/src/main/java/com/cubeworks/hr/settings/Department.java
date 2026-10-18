package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * An organisational department within CubeWorks HR.
 *
 * <p>Maps to {@code hr.department}. Departments are reference data managed by HR
 * administrators via the Settings screens (FRD-08). A department cannot be removed
 * while any employee references it ({@code ON DELETE RESTRICT}).</p>
 *
 * <p>The {@code name} column has a {@code UNIQUE} constraint; the unique finder
 * {@link DepartmentRepository#findByName} supports duplicate-name validation at the
 * service layer before any INSERT attempt hits the constraint.</p>
 */
@Entity
@Table(name = "department")
public class Department extends BaseEntity {

    /**
     * Display name of the department.
     * Must be unique across all departments; duplicates are rejected with a service-layer
     * validation error before reaching the DB constraint.
     */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /** Required no-arg constructor for JPA. */
    protected Department() {}

    /**
     * Creates a new department with the given name.
     *
     * @param name the department display name; must not be null or blank, and must be unique
     */
    public Department(final String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
}
