package com.example.ui.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** SECURITY.md N3: a "where to go next" value must never leave this site. */
class SafeRedirectTest {

    private static final String FALLBACK = "/home";

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/recipes",
            "/recipes/42?servings=2",
            "/recipes/42/nutrition?servings=2#top",
            "/ingredients?name=a%2Fb",
            "/%2F%2Fevil.example",          // percent-encoded slashes stay inside the path
            "/.//evil.example",             // still a path on this host once the browser resolves it
            "/a:b",
            "/@evil.example"
    })
    void keepsSameSitePaths(String candidate) {
        assertThat(SafeRedirect.localPath(candidate, FALLBACK)).isEqualTo(candidate);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "//evil.example",               // scheme-relative -> http://evil.example
            "//evil.example/path",
            "///evil.example",
            "/\\evil.example",              // browsers read "/\" as "//"
            "/\\/evil.example",
            "\\\\evil.example",
            "\\/evil.example",
            "/a\\b",                        // a backslash anywhere
            "/\t/evil.example",             // browsers strip TAB / CR / LF -> "//evil.example"
            "/\r\n/evil.example",
            "/ spaced",
            "/\u0000",
            "/\u007f",
            "http://evil.example",
            "https://evil.example/",
            "HTTP://evil.example",
            "javascript:alert(1)",
            "data:text/html,x",
            "recipes",                      // relative, not absolute-path
            "evil.example",
            "/{placeholder}",               // RedirectView would try to expand it
            "/x/{id}"
    })
    void rejectsAnythingThatCouldLeaveTheSite(String candidate) {
        assertThat(SafeRedirect.localPath(candidate, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    void rejectsAbsurdlyLongTargets() {
        String tooLong = "/" + "a".repeat(2100);
        assertThat(SafeRedirect.localPath(tooLong, FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SafeRedirect.localPath("/" + "a".repeat(2000), FALLBACK)).startsWith("/aaa");
    }
}
