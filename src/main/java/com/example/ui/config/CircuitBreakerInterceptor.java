package com.example.ui.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/**
 * Wraps every outbound HTTP call to one downstream in a named
 * {@link CircuitBreaker}.
 *
 * <ul>
 *   <li>A connection failure, a read timeout, or a <b>5xx</b> from the
 *       downstream counts as a fault.</li>
 *   <li>A <b>4xx</b> passes straight through — it's a normal API answer.</li>
 *   <li>When the breaker is <b>OPEN</b> the call is rejected immediately as a
 *       {@link ResourceAccessException} ({@code UiExceptionHandler} maps that to
 *       a 503 page), instead of the page hanging on a dead service.</li>
 * </ul>
 *
 * Paired with a short connect/read timeout ({@code spring.http.client.*}) so a
 * slow downstream trips the breaker via {@code slowCallRateThreshold}.
 */
public final class CircuitBreakerInterceptor implements ClientHttpRequestInterceptor {

    private final CircuitBreaker breaker;

    public CircuitBreakerInterceptor(CircuitBreaker breaker) {
        this.breaker = breaker;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        try {
            return breaker.executeCallable(() -> {
                ClientHttpResponse response = execution.execute(request, body);
                if (response.getStatusCode().is5xxServerError()) {
                    throw new IOException(breaker.getName() + " returned HTTP "
                            + response.getStatusCode().value());
                }
                return response;
            });
        } catch (CallNotPermittedException open) {
            throw new ResourceAccessException(
                    breaker.getName() + " is unavailable (circuit " + breaker.getState() + ")");
        } catch (IOException io) {
            // Transport failure (connect / read timeout) or our own 5xx signal;
            // already recorded as a fault by the breaker. Normalise to the type
            // UiExceptionHandler maps to a 503 page.
            throw new ResourceAccessException(breaker.getName() + " call failed: " + io.getMessage(), io);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new ResourceAccessException(breaker.getName() + " call failed", new IOException(e));
        }
    }
}
