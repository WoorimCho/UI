package com.example.ui.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * One {@link RestClient} per downstream. The UI talks to the three catalogue
 * services directly for browse / CRUD (they are open on a trusted network) and
 * to the BFF for the composed recipe view + the calculators.
 *
 * <p>Each client gets a short connect/read timeout ({@code spring.http.client.*}
 * on the auto-configured builder) and a per-downstream circuit-breaker
 * interceptor, so a slow or dead service turns into a fast 503 page instead of a
 * hung request.
 */
@Configuration
public class ClientConfig {

    private final CircuitBreakerRegistry breakers;

    public ClientConfig(CircuitBreakerRegistry breakers) {
        this.breakers = breakers;
    }

    @Bean
    RestClient ingredientCatalogueRestClient(RestClient.Builder builder,
                                             @Value("${ingredient-catalogue.url}") String url) {
        return builder.baseUrl(url)
                .requestInterceptor(DownstreamResilience.forDownstream(breakers, "ingredient-catalogue"))
                .build();
    }

    @Bean
    RestClient recipeCatalogueRestClient(RestClient.Builder builder,
                                         @Value("${recipe-catalogue.url}") String url) {
        return builder.baseUrl(url)
                .requestInterceptor(DownstreamResilience.forDownstream(breakers, "recipe-catalogue"))
                .build();
    }

    @Bean
    RestClient userRestClient(RestClient.Builder builder,
                              @Value("${user-service.url}") String url) {
        return builder.baseUrl(url)
                .requestInterceptor(DownstreamResilience.forDownstream(breakers, "user-service"))
                .build();
    }

    @Bean
    RestClient bffRestClient(RestClient.Builder builder,
                             @Value("${bff.url}") String url) {
        return builder.baseUrl(url)
                .requestInterceptor(DownstreamResilience.forDownstream(breakers, "bff"))
                .build();
    }

    /**
     * A crawl walks many pages with a per-host politeness delay, so it can take
     * a minute or more — this client overrides the short shared read timeout.
     * No circuit breaker: it's an infrequent, deliberately slow operation and a
     * long crawl would otherwise count against the breaker's slow-call rate.
     */
    @Bean
    RestClient recipeCrawlerRestClient(RestClient.Builder builder,
                                       @Value("${recipe-crawler.url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofMinutes(3));
        return builder.baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}
