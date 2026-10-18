package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * A job title within CubeWorks HR — independent of department (keeping
 * {@code employee.department_id} in 3NF; see data-model §5).
 *
 * <p>Maps to {@code hr.job_title}. Job titles are reference data managed by HR via the
 * Settings screens. A job title cannot be deleted while any employee or checklist template
 * references it ({@code ON DELETE RESTRICT}).</p>
 *
 * <p>Checklist templates optionally reference a job title to pre-select the right template
 * during onboarding (see {@link ChecklistTemplate#getJobTitle()}).</p>
 */
@Entity
@Table(name = "job_title")
public class JobTitle extends BaseEntity {

    /**
     * Display name of the job title. Must be unique across all job titles.
     */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /** Required no-arg constructor for JPA. */
    protected JobTitle() {}

    /**
     * Creates a new job title.
     *
     * @param name the job title display name; must not be null or blank, and must be unique
     */
    public JobTitle(final String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
}
