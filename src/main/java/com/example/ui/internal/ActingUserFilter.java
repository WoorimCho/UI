package com.example.ui.internal;

import com.example.ui.web.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Copies the logged-in account id from the session into {@link ActingUser} for
 * the duration of the request, so outbound calls can sign it into the
 * {@code X-Internal-Auth} header. Anonymous request → no acting user.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActingUserFilter extends OncePerRequestFilter {

    /** Mirrors {@code SessionSupport.KEY}. */
    private static final String SESSION_KEY = "ui.currentUser";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);
            Object cu = session == null ? null : session.getAttribute(SESSION_KEY);
            if (cu instanceof CurrentUser user) {
                ActingUser.set(user.accountId());
            }
            chain.doFilter(request, response);
        } finally {
            ActingUser.clear();
        }
    }
}
