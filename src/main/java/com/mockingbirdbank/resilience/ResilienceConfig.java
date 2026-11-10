package com.mockingbirdbank.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One shared tuning for every circuit breaker in this app, each one registered by {@code
 * AccountService}/{@code TransactionService}/{@code ProgramService} around its own repository calls
 * (see {@link ServiceUnavailableException}): half of the last 10 calls failing trips it open for
 * 10s (fail fast instead of piling up threads/connections against an already-struggling Postgres),
 * then 3 trial calls in the half-open state decide whether to close again.
 *
 * <p>Exposed as a static method - not just inlined into the bean below - so {@code
 * CircuitBreakerBehaviorTest} exercises this exact configuration rather than a separately
 * hand-typed copy of the same numbers that could quietly drift from what's actually deployed.
 */
@Configuration
public class ResilienceConfig {

    public static CircuitBreakerConfig defaultConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meterRegistry) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig());
        // Every breaker's state/call-outcome counters land in the same product.events
        // Micrometer registry AnalyticsEventService uses, under the resilience4j.* names -
        // queryable the same way once /actuator/metrics is exposed.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }
}
