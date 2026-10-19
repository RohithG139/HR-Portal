package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Grade;
import jakarta.persistence.*;

/**
 * A job title within CubeWorks HR, with the grade and department HR assigns to it in
 * Settings (wireframe {@code m-job-add}; V15, FRD-08 §12 D-01 reversed 2026-10-19).
 * The employee keeps its own {@code department_id}/{@code grade} — these are the job
 * title's defaults, not a replacement.
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

    /** Grade for this job title; {@code null} only on rows created before V15. */
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", length = 10)
    private Grade grade;

    /** Department for this job title; {@code null} only on rows created before V15. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

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

    /**
     * Creates a new job title with its grade and department.
     *
     * @param name       the job title display name; unique
     * @param grade      the job title's grade
     * @param department the job title's department
     */
    public JobTitle(final String name, final Grade grade, final Department department) {
        this.name = name;
        this.grade = grade;
        this.department = department;
    }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public Grade getGrade() { return grade; }
    public Department getDepartment() { return department; }
}
