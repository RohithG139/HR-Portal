package com.cubeworks.hr.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OnboardingDraft}.
 *
 * <p>Drafts are created by "Save draft" (A08), updated by "Update draft" (A09) and deleted
 * atomically by "Save &amp; continue" (A10) once the employee and onboarding rows have been
 * committed.</p>
 *
 * <p>The table is small — at most one draft per in-progress onboarding — so no additional
 * query methods are needed beyond the standard CRUD. The draft ID is used as the foreign key
 * in A10's request body ({@code draftId}) to identify which draft to promote.</p>
 */
public interface OnboardingDraftRepository extends JpaRepository<OnboardingDraft, Long> {
}
