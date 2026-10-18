package com.cubeworks.hr;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the CubeWorks HR backend.
 *
 * <p>This service is the BFF (Backend For Frontend) for the CubeWorks HR React application.
 * It owns all business data in PostgreSQL (schema {@code hr}) and delegates workflow decisions
 * to the CaseFabric engine via its REST API. React talks only to this service — never directly
 * to the engine or Keycloak (R-ARC-4).</p>
 *
 * <p>Package layout (by feature — CLAUDE.md §1.2):</p>
 * <ul>
 *   <li>{@code config}       — CORS, OpenAPI, Jackson configuration</li>
 *   <li>{@code security}     — JWT resource-server filter chain, current-user helper</li>
 *   <li>{@code common/error} — {@code ApiException}, error codes, global exception handler</li>
 *   <li>{@code common/time}  — {@code Clock} bean (never call {@code LocalDate.now()} directly)</li>
 *   <li>{@code engine}       — CaseFabric REST client (gate 3+)</li>
 *   <li>{@code employee}     — employee entity and lifecycle (gate 1+)</li>
 *   <li>{@code onboarding}   — F01 onboarding workflow (gate 1+)</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class HrApplication {

    static {
        // Force UTC before any connection pool or JDBC driver reads the JVM default timezone.
        // On a host whose OS timezone is an older IANA alias (for example "Asia/Calcutta", the
        // deprecated name for "Asia/Kolkata"), pgjdbc sends that alias to Postgres during the
        // connection handshake and Postgres rejects it outright:
        // "FATAL: invalid value for parameter "TimeZone": ...". All stored instants are
        // timestamptz anyway (R-DB-2), so the backend runs in UTC regardless of the host's zone.
        // A static initializer (not a line in main()) so this also applies when @SpringBootTest
        // loads this class directly, without ever calling main().
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(final String[] args) {
        SpringApplication.run(HrApplication.class, args);
    }
}
