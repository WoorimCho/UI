package com.example.ui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * {@code spring-boot-zipkin} 4.1.x's JDK-{@code HttpClient} span sender fails
 * with {@code ClosedChannelException} in a container network; this replaces it
 * with the plain {@code HttpURLConnection} sender (Boot's is
 * {@code @ConditionalOnMissingBean(BytesMessageSender.class)}). Mirrors the
 * config in the three catalogue services and the BFF.
 */
@Configuration
public class TracingSenderConfig {

    @Bean
    BytesMessageSender zipkinSender(
            @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}") String endpoint) {
        return URLConnectionSender.create(endpoint);
    }
}
