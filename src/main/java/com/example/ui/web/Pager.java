package com.example.ui.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View model for the list-page pager fragment ({@code fragments/layout :: pager}):
 * a Prev link, a "go to page N" form, and a Next link. Everything the template
 * needs is a plain public field so it can be read with {@code ${pager.field}}.
 *
 * <p>{@code params} are the filter + sort params carried across every hop (blank
 * values dropped). {@code pageIndex} in is 0-based; the form shows the 1-based
 * {@link #humanPage} and submits {@code jump} (1-based), which each list
 * controller converts back.
 */
public final class Pager {

    public final String path;
    public final Map<String, String> params;
    public final int totalPages;
    public final int humanPage;
    public final boolean show;
    /** Full href for the previous / next page, or {@code null} when there isn't one. */
    public final String prevHref;
    public final String nextHref;

    public Pager(String path, Map<String, String> params, int pageIndex, int totalPages) {
        this.path = path;
        Map<String, String> clean = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    clean.put(k, v);
                }
            });
        }
        this.params = Collections.unmodifiableMap(clean);
        this.totalPages = totalPages;
        this.humanPage = pageIndex + 1;
        this.show = totalPages > 1;
        this.prevHref = pageIndex > 0 ? href(pageIndex - 1) : null;
        this.nextHref = pageIndex + 1 < totalPages ? href(pageIndex + 1) : null;
    }

    // getters as well — Thymeleaf/SpEL reads either a public field or a getter
    public String getPath() { return path; }
    public Map<String, String> getParams() { return params; }
    public int getTotalPages() { return totalPages; }
    public int getHumanPage() { return humanPage; }
    public boolean isShow() { return show; }
    public String getPrevHref() { return prevHref; }
    public String getNextHref() { return nextHref; }

    private String href(int page) {
        StringBuilder q = new StringBuilder();
        params.forEach((k, v) -> append(q, k, v));
        append(q, "page", Integer.toString(page));
        return path + "?" + q;
    }

    private static void append(StringBuilder q, String key, String value) {
        if (q.length() > 0) {
            q.append('&');
        }
        q.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
