package com.cubeworks.hr.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link EmailOutbox}.
 *
 * <p>{@link #findByStatus} is used by {@link EmailOutboxSenderJob} to load all pending rows
 * on each scheduler tick. For gate 2 the volume is small so a simple derived query suffices.
 * A later optimisation can add {@code SELECT FOR UPDATE SKIP LOCKED} to prevent duplicate
 * processing across multiple scheduler threads or JVM instances.</p>
 */
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Returns all outbox rows with the given delivery status.
     *
     * <p>Used by the background sender job to fetch {@link EmailOutboxStatus#PENDING} rows
     * that have not yet been delivered to the SMTP relay.</p>
     *
     * @param status the status to filter by
     * @return all matching outbox rows; empty list if none
     */
    List<EmailOutbox> findByStatus(EmailOutboxStatus status);
}
