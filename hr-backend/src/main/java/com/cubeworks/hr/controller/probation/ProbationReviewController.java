package com.cubeworks.hr.controller.probation;

import com.cubeworks.hr.dto.probation.HrDecisionRequest;
import com.cubeworks.hr.dto.probation.ProbationReviewDto;
import com.cubeworks.hr.dto.probation.SaveProbationReviewRequest;
import com.cubeworks.hr.dto.probation.UndoWindowResponse;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.probation.ProbationReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Probation review endpoints P01–P04 (FRD-02 v1.2 §4.7; spec FRD-02 §6). Thin controller
 * (R-CODE-1): role checks, ownership, G-13 and validation live in {@link ProbationReviewService}.
 */
@RestController
@RequestMapping("/api/probation-reviews")
public class ProbationReviewController {

    private final ProbationReviewService probationReviewService;
    private final CurrentUser currentUser;

    /** Constructor injection (R-CODE-1). */
    public ProbationReviewController(final ProbationReviewService probationReviewService,
                                     final CurrentUser currentUser) {
        this.probationReviewService = probationReviewService;
        this.currentUser = currentUser;
    }

    /** P01 — form with ratings, summary, outcome (Manager own report, HR). */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ProbationReviewDto> get(@PathVariable final Long reviewId) {
        return ResponseEntity.ok(probationReviewService.getReview(reviewId, currentUser.require()));
    }

    /** P02 — save draft (Manager own report). */
    @PutMapping("/{reviewId}")
    public ResponseEntity<ProbationReviewDto> saveDraft(@PathVariable final Long reviewId,
                                                        @RequestBody final SaveProbationReviewRequest req) {
        return ResponseEntity.ok(probationReviewService.saveDraft(reviewId, req, currentUser.require()));
    }

    /** P03 — submit; {@code 202 {undoUntil, taskId}} (R-API-6). */
    @PostMapping("/{reviewId}/submit")
    public ResponseEntity<UndoWindowResponse> submit(@PathVariable final Long reviewId,
                                                     @RequestBody final SaveProbationReviewRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(probationReviewService.submit(reviewId, req, currentUser.require()));
    }

    /** P04 — HR approves or rejects "End employment" (ONB-08); {@code 202 {undoUntil, taskId}}. */
    @PostMapping("/{reviewId}/hr-decision")
    public ResponseEntity<UndoWindowResponse> hrDecision(@PathVariable final Long reviewId,
                                                         @RequestBody final HrDecisionRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(probationReviewService.decide(reviewId, req, currentUser.require()));
    }
}
