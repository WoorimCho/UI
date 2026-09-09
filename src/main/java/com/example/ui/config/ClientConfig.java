package com.example.ui.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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

    @Bean
    RestClient recipeCrawlerRestClient(RestClient.Builder builder,
                                       @Value("${recipe-crawler.url}") String url) {
        return builder.baseUrl(url)
                .requestInterceptor(DownstreamResilience.forDownstream(breakers, "recipe-crawler"))
                .build();
    }
}
