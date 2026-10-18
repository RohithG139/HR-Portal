package com.cubeworks.hr.service.workspace;

import com.cubeworks.hr.model.workspace.ManagerChange;
import com.cubeworks.hr.model.workspace.ManagerChangeStatus;
import com.cubeworks.hr.repository.workspace.ManagerChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Polls for scheduled manager transfers whose effective date has arrived and applies them.
 *
 * <h2>Transaction model</h2>
 * <p>The poll query ({@link #applyDueTransfers}) is intentionally NOT {@code @Transactional}.
 * Each individual change is applied by {@link ManagerTransferApplier#applyChange(Long)}, which
 * is its own {@code @Transactional} method (via the Spring proxy — the applier is injected, not
 * {@code this}). This means one failing change does not roll back the others in the same poll
 * cycle — exactly the same isolation model as {@link com.cubeworks.hr.task.UndoWindowScheduler}
 * and {@link com.cubeworks.hr.task.OverdueReminderScheduler}.</p>
 *
 * <h2>Cadence</h2>
 * <p>Manager transfers take effect on a specific calendar date, so hourly polling is more than
 * sufficient — same cadence as {@link com.cubeworks.hr.task.OverdueReminderScheduler} (3 600 000 ms).
 * The UndoWindowScheduler's 2-second cadence is necessary only for sub-minute precision.</p>
 *
 * <h2>Idempotency</h2>
 * <p>{@link ManagerTransferApplier#applyChange(Long)} guards against double-application with an
 * idempotent check on {@code status}: if the same change is processed by the after-commit hook
 * and the scheduler concurrently, the second call exits immediately without side effects.</p>
 *
 * <h2>Virtual-thread safety</h2>
 * <p>This scheduler uses only repository calls and the applier bean — no {@code synchronized}
 * blocks. No virtual-thread pinning risk (no carrier-thread holds during blocking calls).</p>
 */
@Component
public class ManagerTransferScheduler {

    private static final Logger log = LoggerFactory.getLogger(ManagerTransferScheduler.class);

    private final ManagerChangeRepository managerChangeRepository;
    private final ManagerTransferApplier managerTransferApplier;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param managerChangeRepository for the poll query — reads SCHEDULED changes due today or earlier
     * @param managerTransferApplier  shared apply logic; its {@code applyChange} is {@code @Transactional},
     *                                so calling it from this non-transactional scheduler method starts
     *                                a fresh transaction per change, achieving per-row isolation
     * @param clock                   application-wide clock bean (never call {@code LocalDate.now()} directly)
     */
    public ManagerTransferScheduler(final ManagerChangeRepository managerChangeRepository,
                                     final ManagerTransferApplier managerTransferApplier,
                                     final Clock clock) {
        this.managerChangeRepository = managerChangeRepository;
        this.managerTransferApplier = managerTransferApplier;
        this.clock = clock;
    }

    /**
     * Polls hourly for scheduled manager transfers whose effective date has passed.
     *
     * <p>Not {@code @Transactional}: each change is processed in its own transaction
     * (via the applier bean's proxy). Exceptions from individual changes are caught and
     * logged; the poll continues to the next change. Failed changes remain SCHEDULED and
     * will be retried on the next hourly tick (R-ARC-5 resilience model).</p>
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void applyDueTransfers() {
        final LocalDate today = LocalDate.now(clock);
        final List<ManagerChange> dueChanges =
                managerChangeRepository.findDue(ManagerChangeStatus.SCHEDULED, today);

        if (dueChanges.isEmpty()) {
            return; // Nothing to do — avoid unnecessary log noise
        }

        log.info("ManagerTransferScheduler: {} due transfer(s) to process for date {}",
                dueChanges.size(), today);

        for (final ManagerChange change : dueChanges) {
            try {
                // Each applyChange call is @Transactional on the applier bean's proxy —
                // one transaction per change, so a failing row does not block the others.
                managerTransferApplier.applyChange(change.getId());
            } catch (final Exception ex) {
                log.error("Failed to apply manager transfer changeId={}; will retry on next tick",
                        change.getId(), ex);
            }
        }
    }
}
