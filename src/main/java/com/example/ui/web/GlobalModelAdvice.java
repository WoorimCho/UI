package com.example.ui.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Puts per-request bits every view needs on the model: the current user and the chosen theme. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SessionSupport sessions;

    public GlobalModelAdvice(SessionSupport sessions) {
        this.sessions = sessions;
    }

    @ModelAttribute("currentUser")
    CurrentUser currentUser(HttpSession session) {
        return sessions.current(session);
    }

    /** The theme id from the {@code ui.theme} cookie ({@link ThemeController#THEMES}); defaults to "cherry". */
    @ModelAttribute("uiTheme")
    String uiTheme(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("ui.theme".equals(c.getName()) && ThemeController.THEMES.containsKey(c.getValue())) {
                    return c.getValue();
                }
            }
        }
        return "cherry";
    }

    /** The brand emoji that matches the current theme. */
    @ModelAttribute("uiThemeEmoji")
    String uiThemeEmoji(HttpServletRequest request) {
        return ThemeController.emojiFor(uiTheme(request));
    }
}
