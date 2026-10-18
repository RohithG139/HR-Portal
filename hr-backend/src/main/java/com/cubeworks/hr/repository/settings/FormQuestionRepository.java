package com.cubeworks.hr.repository.settings;

import com.cubeworks.hr.model.settings.FormQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link FormQuestion} (FRD-08 §4.4).
 */
public interface FormQuestionRepository extends JpaRepository<FormQuestion, Long> {

    /**
     * Returns every question of a section, ordered for display.
     *
     * @param sectionId the owning {@link com.cubeworks.hr.model.settings.FormSection} id
     * @return questions in {@code sort_order} order
     */
    List<FormQuestion> findBySectionIdOrderBySortOrderAsc(Long sectionId);

    /**
     * Counts how many questions a section currently has — used to enforce "Add at least one
     * section with a question." (FRD-08 spec §5 S3/S4, §6 C17).
     *
     * @param sectionId the owning section's id
     * @return the number of questions on that section
     */
    long countBySectionId(Long sectionId);
}
