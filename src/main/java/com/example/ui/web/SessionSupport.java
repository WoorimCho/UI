package com.example.ui.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Read / write the {@link CurrentUser} in the session. */
@Component
public class SessionSupport {

    static final String KEY = "ui.currentUser";

    public CurrentUser current(HttpSession session) {
        return session == null ? null : (CurrentUser) session.getAttribute(KEY);
    }

    public boolean isAuthenticated(HttpSession session) {
        return current(session) != null;
    }

    public void store(HttpSession session, CurrentUser user) {
        session.setAttribute(KEY, user);
    }

    public void clear(HttpSession session) {
        session.removeAttribute(KEY);
    }
}
