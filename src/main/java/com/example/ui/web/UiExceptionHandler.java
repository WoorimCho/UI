package com.example.ui.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a friendly page when a downstream call fails, instead of a stack
 * trace. Pulls the RFC 9457 {@code detail} out of the problem body when there
 * is one.
 */
@ControllerAdvice
public class UiExceptionHandler {

    @ExceptionHandler(RestClientResponseException.class)
    public String downstreamError(RestClientResponseException ex, Model model) {
        HttpStatusCode status = ex.getStatusCode();
        model.addAttribute("status", status.value());
        model.addAttribute("heading", switch (status.value()) {
            case 400 -> "That didn't go through";
            case 401 -> "Please sign in";
            case 403 -> "Not allowed";
            case 404 -> "Not found";
            case 409 -> "Conflict";
            case 502, 503 -> "A service is unavailable";
            default -> "Something went wrong";
        });
        model.addAttribute("detail", extractDetail(ex));
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String badInput(IllegalArgumentException ex, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("heading", "That didn't go through");
        model.addAttribute("detail", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(ResourceAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String downstreamUnreachable(ResourceAccessException ex, Model model) {
        model.addAttribute("status", 503);
        model.addAttribute("heading", "A service is unreachable");
        model.addAttribute("detail", "Could not reach one of the catalogue services. Is the stack running?");
        return "error";
    }

    // "detail": "…"  — value may contain escaped quotes / backslashes.
    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private static String extractDetail(RestClientResponseException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null) {
                Matcher m = DETAIL.matcher(body);
                if (m.find()) {
                    return m.group(1)
                            .replace("\\\"", "\"")
                            .replace("\\n", " ")
                            .replace("\\\\", "\\");
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return ex.getStatusText();
    }
}
