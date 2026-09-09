package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.UserClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;

/**
 * The UI keeps its own session. Login does two things: verify credentials with
 * the User service (to learn the account id, used for direct catalogue calls) and
 * log in to the BFF (to get a {@code JSESSIONID} for the {@code /bff/**}
 * composed view + calculators).
 */
@Controller
public class AuthController {

    private final UserClient users;
    private final BffClient bff;
    private final SessionSupport sessions;

    public AuthController(UserClient users, BffClient bff, SessionSupport sessions) {
        this.users = users;
        this.bff = bff;
        this.sessions = sessions;
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", next);
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String identifier,
                        @RequestParam String password,
                        @RequestParam(required = false) String next,
                        HttpServletRequest request, HttpSession session, Model model) {
        UserClient.Identity id;
        try {
            id = users.authenticate(identifier, password);
        } catch (HttpClientErrorException.Unauthorized e) {
            model.addAttribute("error", "That username / email and password don't match.");
            model.addAttribute("next", next);
            return "auth/login";
        }

        // Also establish a BFF session for the composed view + calculators.
        String bffCookie = bff.login(id.username(), password);

        request.changeSessionId();                 // new id on privilege change (SECURITY.md M1)
        sessions.store(session, new CurrentUser(id.accountId(), id.username(), id.displayName(), bffCookie));
        return "redirect:" + (StringUtils.hasText(next) && next.startsWith("/") ? next : "/");
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        CurrentUser user = sessions.current(session);
        if (user != null && user.bffCookie() != null) {
            try {
                bff.logout(user.bffCookie());
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        sessions.clear(session);
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String displayName,
                           @RequestParam String password,
                           HttpServletRequest request, HttpSession session, Model model) {
        try {
            var account = users.register(username, email, displayName, password);
            String bffCookie = bff.login(account.username(), password);
            request.changeSessionId();             // new id on privilege change (SECURITY.md M1)
            sessions.store(session, new CurrentUser(account.id(), account.username(),
                    account.displayName(), bffCookie));
            return "redirect:/account";
        } catch (HttpClientErrorException.BadRequest e) {
            model.addAttribute("error", "Check the form: username 3–50 chars, password ≥ 8 chars, a valid email if you give one, and username/email must be unused.");
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            return "auth/register";
        }
    }
}
