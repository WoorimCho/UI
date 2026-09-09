package com.example.ui.web;

/**
 * The logged-in user, kept in the UI's own {@code HttpSession}. {@code bffCookie}
 * is the BFF's {@code JSESSIONID}, captured at login and relayed on calls to
 * {@code /bff/**} (the composed recipe view + the calculators).
 */
public record CurrentUser(Long accountId, String username, String displayName, String bffCookie) {

    public String displayNameOrUsername() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }
}
