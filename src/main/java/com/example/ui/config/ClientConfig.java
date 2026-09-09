package com.example.ui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per downstream. The UI talks to the three catalogue
 * services directly for browse / CRUD (they are open on a trusted network) and
 * to the BFF for the composed recipe view + the calculators.
 */
@Configuration
public class ClientConfig {

    @Bean
    RestClient ingredientCatalogueRestClient(RestClient.Builder builder,
                                           @Value("${ingredient-catalogue.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    RestClient recipeCatalogueRestClient(RestClient.Builder builder,
                                       @Value("${recipe-catalogue.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    RestClient userRestClient(RestClient.Builder builder,
                              @Value("${user-service.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    RestClient bffRestClient(RestClient.Builder builder,
                             @Value("${bff.url}") String url) {
        return builder.baseUrl(url).build();
    }
}
