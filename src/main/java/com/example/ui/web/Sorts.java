package com.example.ui.web;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the "click a column header to sort" links for the list pages. Each page
 * declares a whitelist of {@code uiField -> backend sort property}; this turns a
 * chosen field + direction into the {@code ?sort=} the catalogue expects, and a
 * per-field {@link Link} (href to sort by it, plus an arrow when it's active).
 */
final class Sorts {

    private Sorts() {
    }

    /** One header's state: where clicking it goes, and the arrow to show (empty unless it's the active sort). */
    record Link(String href, String arrow) {
    }

    /** The {@code sort=} query value for the catalogue call, or {@code null} when the field isn't sortable. */
    static String query(String uiField, String dir, Map<String, String> whitelist) {
        if (!StringUtils.hasText(uiField)) {
            return null;
        }
        String property = whitelist.get(uiField);
        if (property == null) {
            return null;
        }
        return property + "," + ("desc".equalsIgnoreCase(dir) ? "desc" : "asc");
    }

    /**
     * A {@code Link} per whitelisted field. {@code baseParams} are the filter
     * params to keep on the URL (name / tag / …); {@code sort}, {@code dir} and
     * {@code page} are managed here — re-sorting always returns to page 0.
     */
    static Map<String, Link> links(String path, MultiValueMap<String, String> baseParams,
                                   String activeField, String activeDir, Iterable<String> fields) {
        String active = StringUtils.hasText(activeField) ? activeField : "";
        String dir = "desc".equalsIgnoreCase(activeDir) ? "desc" : "asc";

        Map<String, Link> out = new LinkedHashMap<>();
        for (String field : fields) {
            boolean isActive = field.equals(active);
            String nextDir = isActive && "asc".equals(dir) ? "desc" : "asc";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            baseParams.forEach((k, values) -> values.forEach(v -> {
                if (StringUtils.hasText(v)) {
                    params.add(k, v);
                }
            }));
            params.set("sort", field);
            params.set("dir", nextDir);

            String arrow = isActive ? ("asc".equals(dir) ? "▲" : "▼") : "";
            out.put(field, new Link(path + "?" + encode(params), arrow));
        }
        return out;
    }

    private static String encode(MultiValueMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> values.forEach(value -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }));
        return sb.toString();
    }
}
