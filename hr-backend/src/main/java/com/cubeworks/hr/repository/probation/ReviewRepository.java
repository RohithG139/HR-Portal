package com.cubeworks.hr.repository.probation;

import com.cubeworks.hr.model.probation.Review;
import com.cubeworks.hr.model.probation.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Review} (FRD-02 probation half). */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Whether a review of this type exists for a contract (one probation review per contract). */
    boolean existsByContractIdAndReviewType(Long contractId, String reviewType);

    /** The latest review of this type for a contract. */
    Optional<Review> findFirstByContractIdAndReviewTypeOrderByIdDesc(Long contractId, String reviewType);

    /**
     * Current contracts whose probation review is due ({@code v_contract.probation_end_date −
     * daysBefore <= today}, ONB-01 via the view), whose probation has not ended yet, and that
     * have no probation review yet.
     *
     * @param daysBefore {@code setting.PROBATION_REVIEW} days
     * @param today      the current date
     * @return contract ids
     */
    @Query(value = """
            SELECT c.id FROM hr.v_contract c
            WHERE c.is_current AND c.probation_months > 0
              AND c.probation_end_date - CAST(:daysBefore AS integer) <= CAST(:today AS date)
              AND c.probation_end_date >= CAST(:today AS date)
              AND NOT EXISTS (SELECT 1 FROM hr.review r
                              WHERE r.contract_id = c.id AND r.review_type = 'PROBATION')
            """, nativeQuery = true)
    List<Long> findContractIdsDueForProbationReview(@Param("daysBefore") int daysBefore,
                                                    @Param("today") java.time.LocalDate today);

    /**
     * Probation reviews still in draft whose engine "Probation review" task has not been seen in
     * the engine yet: the scheduler (re)drives the engine for these (R-ARC-5 retry).
     */
    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.contract c
            JOIN FETCH c.employee
            WHERE r.reviewType = 'PROBATION'
              AND r.status = :status
              AND r.engineTaskId IS NULL
            """)
    List<Review> findProbationWithoutEngineTask(@Param("status") ReviewStatus status);
}
