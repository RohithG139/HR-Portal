package com.cubeworks.hr.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the application-wide {@link Clock} bean.
 *
 * <p>All service methods that need "today" or "now" <strong>must</strong> inject this bean
 * rather than calling {@link java.time.LocalDate#now()}, {@link java.time.Instant#now()}, or
 * {@link java.time.LocalDateTime#now()} directly. This constraint (R-CODE-1) makes
 * time-sensitive business logic deterministic in unit tests: replace this bean with
 * {@link Clock#fixed(java.time.Instant, java.time.ZoneId)} to freeze the clock at any
 * desired moment without mocking static methods.</p>
 *
 * <h2>Usage in a service</h2>
 * <pre>{@code
 * @Service
 * public class OnboardingService {
 *     private final Clock clock;
 *
 *     public OnboardingService(Clock clock) { this.clock = clock; }
 *
 *     LocalDate today() { return LocalDate.now(clock); }
 * }
 * }</pre>
 *
 * <h2>Usage in a test</h2>
 * <pre>{@code
 * @TestConfiguration
 * static class FixedClockConfig {
 *     @Bean
 *     Clock clock() {
 *         return Clock.fixed(Instant.parse("2025-01-15T09:00:00Z"), ZoneOffset.UTC);
 *     }
 * }
 * }</pre>
 */
@Configuration
public class ClockConfig {

    /**
     * Returns a {@link Clock} using the JVM default time zone.
     *
     * <p>In production this is the system clock. Tests override this bean with a fixed clock
     * to make assertions on date-driven logic (due dates, SLA timers, etc.) deterministic.</p>
     *
     * @return the system-default-zone clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
