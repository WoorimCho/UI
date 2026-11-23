package com.example.ui.web;

/**
 * Turns a request-supplied "go back to here" value ({@code ?next=}, {@code back})
 * into a redirect target that cannot leave this site (SECURITY.md N3).
 *
 * <p>A bare {@code startsWith("/")} is not enough: {@code //evil.example} is a
 * scheme-relative URL, and browsers read {@code /\evil.example} the same way.
 */
final class SafeRedirect {

    private static final int MAX_LENGTH = 2048;

    private SafeRedirect() {
    }

    /** {@code candidate} when it is a same-site absolute path, otherwise {@code fallback}. */
    static String localPath(String candidate, String fallback) {
        if (candidate == null || candidate.length() > MAX_LENGTH || !candidate.startsWith("/")) {
            return fallback;
        }
        if (candidate.startsWith("//") || candidate.startsWith("/\\")) {
            return fallback;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            // Backslash: browsers treat it as "/". Whitespace / control chars: browsers
            // strip tab, CR and LF, so "/<TAB>/host" becomes "//host". Braces: RedirectView
            // would try to expand them as URI-template variables.
            if (c == '\\' || c <= ' ' || c == 0x7f || c == '{' || c == '}') {
                return fallback;
            }
        }
        return candidate;
    }
}
