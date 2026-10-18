package com.cubeworks.hr.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link EmailTemplate}.
 *
 * <p>{@link #findByCode} is the primary lookup for the outbox sender and the template
 * settings screen — the {@code code} is the stable, machine-readable identifier used
 * across the application (e.g. {@code ONB_INVITE}).</p>
 *
 * <p>FRD-08 Settings CRUD (C25–C28) adds a name-unique finder needed by the settings
 * screen to detect duplicate names on update (C27).</p>
 */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    /**
     * Finds an e-mail template by its unique machine-readable code.
     *
     * <p>Used by the outbox sender to validate template references and by the settings
     * screen to load a specific template for editing (C26).</p>
     *
     * @param code the unique template code (e.g. {@code "ONB_INVITE"})
     * @return the matching template, if any
     */
    Optional<EmailTemplate> findByCode(String code);

    /**
     * Finds an e-mail template by its human-readable name, for duplicate-name validation
     * on update (C27: "This name already exists.").
     *
     * @param name the template name to look up (case-sensitive)
     * @return the matching template, if any
     */
    Optional<EmailTemplate> findByName(String name);
}
