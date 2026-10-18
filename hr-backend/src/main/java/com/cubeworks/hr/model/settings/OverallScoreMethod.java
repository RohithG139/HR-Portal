package com.cubeworks.hr.model.settings;

/**
 * How an {@link AppraisalForm}'s overall score is computed.
 *
 * <p>Stored as {@code varchar(20)} with a CHECK constraint in
 * {@code hr.appraisal_form.overall_score_method}.</p>
 */
public enum OverallScoreMethod {
    /** The overall score is the weighted average of every scored question. */
    WEIGHTED_AVERAGE,
    /** No automatic calculation — the manager enters the overall score directly. */
    MANAGER_DECIDES
}
