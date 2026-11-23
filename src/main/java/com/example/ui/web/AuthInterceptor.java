package com.example.ui.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Who may do what (SECURITY.md N7).
 *
 * <ul>
 *   <li><b>Reading is public.</b> Browsing ingredients, recipes, tags and nutrition
 *       needs no account.</li>
 *   <li><b>Every write needs a login</b> — any request that is not GET / HEAD /
 *       OPTIONS. It is default-deny: a POST route added tomorrow is protected
 *       without anyone remembering to list it. Only signing in, signing up,
 *       signing out and the cookie-only theme switch stay anonymous.</li>
 *   <li><b>A few pages need a login just to open</b>: the account area, the
 *       per-recipe calculators (they ride on the BFF session), and the forms and
 *       tools that exist only to change or import data.</li>
 * </ul>
 *
 * The decision is made on the route the handler mapping actually matched
 * ({@code /ingredients/{id}/edit}), not on the raw URL, so path tricks can't
 * route around it. Registered for every path in {@code WebConfig}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Write routes that must work without a session. */
    private static final Set<String> ANONYMOUS_WRITES = Set.of("/login", "/register", "/logout", "/settings/theme");

    /** Read routes that need a session (besides everything under /account and /favourites). */
    private static final Set<String> LOGIN_ONLY_READS = Set.of(
            "/recipes/{id}/nutrition", "/recipes/{id}/calories", "/recipes/{id}/portions",
            "/recipes/{id}/portions.json", "/recipes/{id}/composed",
            "/ingredients/new", "/ingredients/{id}/edit", "/recipes/new", "/recipes/{id}/edit",
            "/recipes/import-url", "/recipes/crawl", "/import");

    private final SessionSupport sessions;

    public AuthInterceptor(SessionSupport sessions) {
        this.sessions = sessions;
    }

    /** True when {@code route} (a matched route template) may only be used by a logged-in user. */
    static boolean requiresLogin(String method, String route) {
        if (isRead(method)) {
            return route.equals("/account") || route.startsWith("/account/") || route.startsWith("/favourites/")
                    || LOGIN_ONLY_READS.contains(route);
        }
        return !ANONYMOUS_WRITES.contains(route);
    }

    private static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!requiresLogin(request.getMethod(), routeOf(request))
                || sessions.isAuthenticated(request.getSession(false))) {
            return true;
        }
        response.sendRedirect(request.getContextPath()
                + "/login?next=" + URLEncoder.encode(returnTargetFor(request), StandardCharsets.UTF_8));
        return false;
    }

    /** The route template the handler mapping matched, e.g. {@code /recipes/{id}/edit}. */
    private static String routeOf(HttpServletRequest request) {
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return matched != null ? matched.toString() : request.getRequestURI();
    }

    /**
     * Where to send the user after signing in. A refused page is simply reopened;
     * a refused write can't be replayed by a GET, so the user goes back to the
     * page they were on (from {@code Referer}, path and query only) or home.
     */
    private static String returnTargetFor(HttpServletRequest request) {
        String target;
        if (isRead(request.getMethod())) {
            target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        } else {
            target = pathOf(request.getHeader("Referer"));
        }
        return SafeRedirect.localPath(target, "/");
    }

    private static String pathOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getRawPath() == null || uri.getRawPath().isEmpty()) {
                return null;
            }
            return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
