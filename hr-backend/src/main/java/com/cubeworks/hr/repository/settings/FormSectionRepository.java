package com.cubeworks.hr.repository.settings;

import com.cubeworks.hr.model.settings.FormSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data repository for {@link FormSection} (FRD-08 §4.4).
 */
public interface FormSectionRepository extends JpaRepository<FormSection, Long> {

    /**
     * Returns every section of a form, ordered for display.
     *
     * @param formId the owning {@link com.cubeworks.hr.model.settings.AppraisalForm} id
     * @return sections in {@code sort_order} order
     */
    List<FormSection> findByFormIdOrderBySortOrderAsc(Long formId);

    /**
     * Counts how many sections a form currently has — used to enforce "Add at least one section
     * with a question." (FRD-08 spec §5 S3/S4, §6 C17) before a form can be set to
     * {@link com.cubeworks.hr.model.settings.FormStatus#ACTIVE}.
     *
     * @param formId the owning form's id
     * @return the number of sections on that form
     */
    long countByFormId(Long formId);

    /**
     * Returns the section count for every form in one query, as {@code [formId, count]} pairs.
     *
     * <p>N+1 prevention (FRD-08 C14, Gate 5 code review): the appraisal-forms list shows every
     * form's section count on one screen; this single aggregate query replaces what would
     * otherwise be one {@link #countByFormId} call per form. Forms with zero sections are
     * absent from the result — callers default missing ids to 0.</p>
     *
     * @return one {@code Object[]} per form that has at least one section:
     *         index 0 is the form id ({@code Long}), index 1 is the count ({@code Long})
     */
    @Query("SELECT s.form.id, COUNT(s) FROM FormSection s GROUP BY s.form.id")
    List<Object[]> countSectionsGroupedByForm();
}
