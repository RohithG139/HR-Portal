package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link ChecklistTemplate}.
 *
 * <p>{@link #findByArchivedAtIsNull} returns only active (non-archived) templates for the
 * onboarding-form template dropdown (A02). Archived templates cannot be assigned to new
 * onboardings (ADM-04).</p>
 *
 * <p>FRD-08 Settings CRUD (C07–C10) adds kind-filtered and name-unique finders needed by the
 * settings screens.</p>
 */
public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, Long> {

    /**
     * Returns all active (non-archived) checklist templates.
     *
     * <p>Used by the onboarding-form template dropdown (A02) and the template preview (A06).
     * Archived templates have {@code archivedAt != null} and must not be shown in the dropdown.</p>
     *
     * @return all active templates, in no guaranteed order
     */
    List<ChecklistTemplate> findByArchivedAtIsNull();

    /**
     * Finds a template by its unique name, for duplicate-name validation (FRD-08 C09).
     *
     * @param name the exact template name (case-sensitive)
     * @return the matching template, if any
     */
    Optional<ChecklistTemplate> findByName(String name);

    /**
     * Returns all templates of a given kind, including archived ones (C07 when
     * {@code includeArchived=true}).
     *
     * @param kind the template kind to filter by
     * @return all templates with the given kind
     */
    List<ChecklistTemplate> findByKind(TemplateKind kind);

    /**
     * Returns only active (non-archived) templates of a given kind (C07 when
     * {@code includeArchived=false}).
     *
     * @param kind the template kind to filter by
     * @return active templates with the given kind
     */
    List<ChecklistTemplate> findByKindAndArchivedAtIsNull(TemplateKind kind);
}
