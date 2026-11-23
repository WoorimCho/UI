package com.example.ui.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The UI theme picker. The choice is a per-browser preference (a year-long
 * {@code ui.theme} cookie); the head fragment applies it before first paint and
 * {@link GlobalModelAdvice} exposes it so the profile page can show the current
 * selection. No login required — it's a display setting.
 */
@Controller
public class ThemeController {

    /** id → display label. "cherry" is the default (no data-theme attribute). */
    public static final Map<String, String> THEMES = new LinkedHashMap<>();

    /** id → the brand emoji shown next to "Allergen Info" for that theme. */
    public static final Map<String, String> EMOJI = new LinkedHashMap<>();

    static {
        THEMES.put("cherry", "Cherry Blossom");
        THEMES.put("blueberry", "Blueberry");
        THEMES.put("matcha", "Matcha");
        THEMES.put("lavender", "Lavender");

        EMOJI.put("cherry", "🌸");     // 🌸
        EMOJI.put("blueberry", "🫐");  // 🫐
        EMOJI.put("matcha", "🍵");     // 🍵
        EMOJI.put("lavender", "🪻");   // 🪻
    }

    /** The brand emoji for a theme id, falling back to the cherry blossom. */
    public static String emojiFor(String themeId) {
        return EMOJI.getOrDefault(themeId, EMOJI.get("cherry"));
    }

    @PostMapping("/settings/theme")
    public String setTheme(@RequestParam String theme,
                           @RequestParam(required = false) String back,
                           HttpServletRequest request, HttpServletResponse response) {
        String chosen = THEMES.containsKey(theme) ? theme : "cherry";
        Cookie cookie = new Cookie("ui.theme", chosen);
        cookie.setPath("/");
        cookie.setMaxAge(365 * 24 * 60 * 60);
        cookie.setHttpOnly(false); // the head script reads it
        response.addCookie(cookie);
        return "redirect:" + SafeRedirect.localPath(back, "/account");
    }
}
