package com.cubeworks.hr.security;

import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.employee.Employee;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Enforces G-13: a user never reviews, approves or finalises anything about themselves
 * (R-API-2). Call this from the service layer, in addition to the ordinary role check,
 * on every endpoint where the caller could otherwise act on their own employee record —
 * for example an HR user editing their own onboarding, or an IT admin issuing an asset
 * to themselves, while they are also the subject of that same onboarding case.
 *
 * <p>This is a separate, explicit check rather than something folded into the existing
 * role/ownership checks because it applies across roles (HR, IT_ADMIN) wherever the
 * caller and the subject employee could be the same person — a role check alone cannot
 * express "not when it's about me".</p>
 */
@Component
public class SelfActionGuard {

    /**
     * Throws {@code 403 FORBIDDEN} when the caller is the subject employee.
     *
     * @param caller           the authenticated user performing the action
     * @param subjectEmployeeId the employee the action would affect
     */
    public void forbidSelf(final Employee caller, final Long subjectEmployeeId) {
        if (subjectEmployeeId != null && caller.getId().equals(subjectEmployeeId)) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You cannot perform this action on your own record (G-13)");
        }
    }
}
