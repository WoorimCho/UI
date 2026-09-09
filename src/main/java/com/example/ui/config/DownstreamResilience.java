package com.example.ui.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.time.Duration;

/**
 * One {@link io.github.resilience4j.circuitbreaker.CircuitBreaker} per downstream
 * (the three catalogue/User services and the BFF), all sharing this config:
 * trips when 50%+ of the last 20 calls fail or run slow (&gt; 3s); stays OPEN
 * 15s, then probes with 4 calls before closing.
 *
 * <p>Breaker state is published to Micrometer -> {@code /actuator/prometheus}
 * (metric {@code resilience4j_circuitbreaker_*}) and Grafana.
 */
@Configuration
public class DownstreamResilience {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .slowCallRateThreshold(50f)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(4)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
                                                             ObjectProvider<MeterRegistry> meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        meterRegistry.ifAvailable(metrics::bindTo);
        return metrics;
    }

    /** Convenience for {@link ClientConfig}. */
    public static ClientHttpRequestInterceptor forDownstream(CircuitBreakerRegistry registry, String name) {
        return new CircuitBreakerInterceptor(registry.circuitBreaker(name));
    }
}
