package com.cubeworks.hr.repository.probation;

import com.cubeworks.hr.model.probation.ReviewAnswer;
import com.cubeworks.hr.model.probation.ReviewAnswerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link ReviewAnswer}. */
public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, ReviewAnswerId> {

    /** Every answer of one review. */
    List<ReviewAnswer> findByIdReviewId(Long reviewId);
}
