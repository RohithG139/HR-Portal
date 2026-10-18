package com.cubeworks.hr.security;

import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the currently authenticated {@link Employee} from the Keycloak JWT in the
 * Spring Security context.
 *
 * <h2>Resolution strategy</h2>
 * <ol>
 *   <li>Reads the {@link Authentication} from {@link SecurityContextHolder}.</li>
 *   <li>If it is a {@link JwtAuthenticationToken}, extracts the {@code sub} claim.</li>
 *   <li>Looks up the employee by {@code idp_subject} — the Keycloak subject UUID that
 *       was stored when the employee's Keycloak account was created.</li>
 * </ol>
 *
 * <h2>Test usage</h2>
 * <p>In integration tests, inject a mock JWT via
 * {@code SecurityMockMvcRequestPostProcessors.jwt().subject("test-sub")} and pre-seed an
 * {@link Employee} row with {@code idpSubject = "test-sub"} to simulate a logged-in user.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This bean reads a thread-local (the security context) and delegates to a
 * {@code JpaRepository} — both are safe to call from any thread that has a security context
 * populated (every Spring MVC request thread does).</p>
 */
@Component
public class CurrentUser {

    private final EmployeeRepository employeeRepository;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository used to look up the employee by Keycloak subject
     */
    public CurrentUser(final EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * Returns the authenticated employee, or {@link Optional#empty()} if the current
     * authentication does not correspond to any known employee.
     *
     * <p>Returns empty rather than throwing so that callers can distinguish between
     * "no authentication" and "employee not found" when logging.</p>
     *
     * @return the authenticated employee, or empty
     */
    public Optional<Employee> get() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            final Jwt jwt = (Jwt) jwtAuth.getPrincipal();
            final String sub = jwt.getSubject();
            if (sub != null) {
                return employeeRepository.findByIdpSubject(sub);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the authenticated employee, throwing {@code 403 FORBIDDEN} if the JWT subject
     * does not match any employee in the database.
     *
     * <p>Use this variant in service methods that require a resolved employee — it converts
     * the "employee not found" case into an explicit 403 rather than a NullPointerException.</p>
     *
     * @return the authenticated employee, never null
     * @throws ApiException with {@code 403 FORBIDDEN} if no matching employee is found
     */
    public Employee require() {
        return get().orElseThrow(() -> new ApiException(
                ErrorCodes.FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "Authenticated JWT subject does not match any employee in this system"));
    }

    /**
     * Returns the Keycloak subject ({@code sub} claim) from the current JWT.
     *
     * <p>Useful when an employee row does not yet exist (e.g. during account creation)
     * but the caller still needs the raw subject string.</p>
     *
     * @return the JWT subject, or empty if there is no JWT in the security context
     */
    public Optional<String> getSubject() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.ofNullable(((Jwt) jwtAuth.getPrincipal()).getSubject());
        }
        return Optional.empty();
    }
}
