package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link ChecklistTemplateItem}.
 *
 * <p>{@link #findByTemplateIdOrderBySortOrder} is the primary query for the template-preview
 * (A06) and for instantiating checklist items when an onboarding starts (A10).</p>
 */
public interface ChecklistTemplateItemRepository extends JpaRepository<ChecklistTemplateItem, Long> {

    /**
     * Returns all items for a given template, ordered by their display position.
     *
     * <p>N+1 prevention: this single query loads all template items for the given template in
     * one round-trip, used when instantiating a checklist (A10) — prevents one SELECT per item.
     * The LEFT JOIN FETCH on {@code policyDocument} loads any attached policy PDFs in the same
     * query rather than lazily triggering one SELECT per POLICY_ACK item.</p>
     *
     * @param templateId the template primary key
     * @return items ordered by section then sort_order
     */
    @Query("""
            SELECT i FROM ChecklistTemplateItem i
            LEFT JOIN FETCH i.policyDocument
            WHERE i.template.id = :templateId
            ORDER BY i.section, i.sortOrder
            """)
    List<ChecklistTemplateItem> findByTemplateIdOrderBySortOrder(@Param("templateId") Long templateId);

    /**
     * Returns the number of items on a template, for the list view's item count (FRD-08 C07)
     * without loading every item's full row (N+1 found in Gate 5 code review — the previous
     * code called {@link #findByTemplateIdOrderBySortOrder} and took {@code .size()}).
     *
     * @param templateId the template primary key
     * @return the item count
     */
    long countByTemplateId(Long templateId);
}
