package com.cubeworks.hr.dto.probation;

/**
 * P04 request body (ONB-08).
 *
 * @param decision APPROVED / REJECTED
 */
public record HrDecisionRequest(String decision) {
}
