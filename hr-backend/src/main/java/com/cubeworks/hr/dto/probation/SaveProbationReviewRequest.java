package com.cubeworks.hr.dto.probation;

import java.util.List;

/**
 * P02 (save draft) and P03 (submit) request body. Nothing is required on a draft save; P03
 * checks every required answer and the outcome (FRD-02 §4 "Probation summary and outcome").
 *
 * @param version optimistic-lock version from P01 (R-DB-5); required
 * @param answers one entry per answered question
 * @param outcome CONFIRM / END, or null while undecided
 */
public record SaveProbationReviewRequest(Integer version, List<Answer> answers, String outcome) {

    /**
     * One answer; set the field matching the question's answer type.
     *
     * @param questionId form_question id
     * @param rating     1-5 for SCALE_1_5
     * @param yesNo      for YES_NO
     * @param textAnswer for TEXT (the summary)
     */
    public record Answer(Long questionId, Integer rating, Boolean yesNo, String textAnswer) {}
}
