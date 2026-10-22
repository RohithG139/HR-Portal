package com.cubeworks.hr.service.probation;

import com.cubeworks.hr.model.probation.Review;
import com.cubeworks.hr.model.probation.ReviewStatus;
import com.cubeworks.hr.repository.probation.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Timer "Probation review" (FRD-02 §4.7, FRD-08 {@code PROBATION_REVIEW}, cannot be disabled —
 * D-P1). E1 spike fallback ({@code 03-case-file.md} §1): CaseFabric 1.1.35 timers cannot read a
 * case-file date, so Spring polls {@code v_contract} and raises the task itself.
 *
 * <p>Each contract and each retry runs in its own transaction (through the service proxy), so
 * one failure never blocks the others.</p>
 */
@Component
public class ProbationReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProbationReviewScheduler.class);

    private final ReviewRepository reviewRepository;
    private final ProbationReviewService probationReviewService;
    private final Clock clock;

    /** Constructor injection (R-CODE-1). */
    public ProbationReviewScheduler(final ReviewRepository reviewRepository,
                                    final ProbationReviewService probationReviewService,
                                    final Clock clock) {
        this.reviewRepository = reviewRepository;
        this.probationReviewService = probationReviewService;
        this.clock = clock;
    }

    /**
     * Hourly by default (day-granularity rule): creates due reviews, then retries unconfirmed
     * engine calls. The dev stack polls every minute ({@code HR_PROBATION_CHECK_INTERVAL_MS}) so
     * a tester sees a review within a minute of making an employee due.
     */
    @Scheduled(fixedDelayString = "${hr.probation.check-interval-ms:3600000}", initialDelay = 60_000)
    public void run() {
        final LocalDate today = LocalDate.now(clock);
        raiseDueReviews(today);
        for (final Review review : reviewRepository.findProbationWithoutEngineTask(ReviewStatus.DRAFT)) {
            try {
                probationReviewService.reconcileEngine(review.getId());
            } catch (final Exception ex) {
                log.error("Probation review engine reconcile failed for review id={}; will retry",
                        review.getId(), ex);
            }
        }
    }

    /**
     * Creates every probation review that is due today or earlier.
     *
     * @param today the current date
     * @return number of reviews created
     */
    public int raiseDueReviews(final LocalDate today) {
        int created = 0;
        for (final Long contractId : reviewRepository.findContractIdsDueForProbationReview(
                probationReviewService.reviewDaysBefore(), today)) {
            try {
                if (probationReviewService.createReviewIfDue(contractId, today)) {
                    created++;
                }
            } catch (final Exception ex) {
                log.error("Failed to create probation review for contract id={}; will retry", contractId, ex);
            }
        }
        return created;
    }
}
