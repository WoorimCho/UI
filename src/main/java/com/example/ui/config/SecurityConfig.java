package com.example.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * The UI keeps its <em>own</em> session-based auth ({@code ui.currentUser} +
 * {@link com.example.ui.web.AuthInterceptor}). Spring Security is here only for
 * the cross-cutting hardening it does well (SECURITY.md):
 *
 * <ul>
 *   <li><b>H2</b> — CSRF tokens on state-changing form posts (Thymeleaf's
 *       {@code th:action} injects the hidden {@code _csrf} field automatically),
 *       plus {@code SameSite=Strict} on the session cookie (in
 *       {@code application.properties}).</li>
 *   <li><b>M5</b> — {@code X-Frame-Options: DENY}, {@code X-Content-Type-Options:
 *       nosniff}, a Content-Security-Policy, {@code Referrer-Policy}.</li>
 *   <li><b>M1</b> — session-fixation protection; the login flow also calls
 *       {@code request.changeSessionId()} explicitly (Spring's own auth events
 *       don't fire for the UI's home-grown login).</li>
 * </ul>
 *
 * Every request is permitted — there is no login wall; anonymous browsing is a
 * feature. Spring's form login / basic / logout are disabled so they don't
 * shadow the UI's own {@code /login}, {@code /register}, {@code /logout} routes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSP =
            "default-src 'self'; "
            + "img-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'";

    @Bean
    SecurityFilterChain uiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable());
        return http.build();
    }
}
