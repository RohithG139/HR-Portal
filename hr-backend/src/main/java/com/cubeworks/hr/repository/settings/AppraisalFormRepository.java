package com.cubeworks.hr.repository.settings;

import com.cubeworks.hr.model.settings.AppraisalForm;
import com.cubeworks.hr.model.settings.FormStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link AppraisalForm} (FRD-08 §4.4).
 */
public interface AppraisalFormRepository extends JpaRepository<AppraisalForm, Long> {

    /**
     * Returns every form that is not archived, for pickers that offer a form for a new review.
     * Archived forms stay out of new-review pickers but remain reachable by id for any review
     * that already references them (ADM-04).
     *
     * @return all draft/active forms
     */
    List<AppraisalForm> findByStatusNot(FormStatus status);

    /**
     * Looks up a form by its unique name, for the "This name already exists." duplicate check
     * (FRD-08 spec §5 S3/S4).
     *
     * @param name the form name to check
     * @return the form, if one with this exact name exists
     */
    Optional<AppraisalForm> findByName(String name);
}
