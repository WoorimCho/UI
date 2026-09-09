package com.example.ui.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Guards the pages that need a login (registered for {@code /account/**} and the
 * per-recipe calculators / personalised view). Anonymous browsing of the
 * catalogues stays open.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionSupport sessions;

    public AuthInterceptor(SessionSupport sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (sessions.isAuthenticated(request.getSession(true))) {
            return true;
        }
        String next = request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        response.sendRedirect(request.getContextPath()
                + "/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
        return false;
    }
}
