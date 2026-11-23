package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.UserClient;
import com.example.ui.config.SecurityConfig;
import com.example.ui.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SECURITY.md N3, end to end through the three endpoints that redirect to a
 * request-supplied target: login ({@code next}), favourites and the theme
 * switch ({@code back}). An off-site target must fall back to a local page.
 */
@WebMvcTest({AuthController.class, AccountController.class, ThemeController.class})
@Import({SessionSupport.class, AuthInterceptor.class, GlobalModelAdvice.class, WebConfig.class, SecurityConfig.class})
class OpenRedirectTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean IngredientClient ingredients;
    @MockitoBean RecipeClient recipes;
    @MockitoBean UserClient users;
    @MockitoBean BffClient bff;

    private void loginSucceeds() {
        when(users.authenticate("bob", "pw")).thenReturn(new UserClient.Identity(1L, "bob", "Bob"));
        when(bff.login("bob", "pw")).thenReturn("JSESSIONID=abc");
    }

    private org.springframework.test.web.servlet.ResultActions login(String next) throws Exception {
        loginSucceeds();
        return mvc.perform(post("/login").with(csrf())
                .param("identifier", "bob").param("password", "pw").param("next", next));
    }

    @Test
    void loginFollowsALocalNext() throws Exception {
        login("/recipes/5?servings=2").andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/recipes/5?servings=2"));
    }

    @Test
    void loginIgnoresASchemeRelativeNext() throws Exception {
        login("//evil.example/").andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void loginIgnoresABackslashNext() throws Exception {
        login("/\\evil.example").andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void loginIgnoresAnAbsoluteNext() throws Exception {
        login("https://evil.example/").andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void favouriteBackIsSanitisedToo() throws Exception {
        var user = new CurrentUser(1L, "bob", "Bob", "JSESSIONID=abc");
        mvc.perform(post("/favourites/recipes/5").with(csrf()).sessionAttr(SessionSupport.KEY, user)
                        .param("back", "//evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/favourites"));
        mvc.perform(post("/favourites/recipes/5").with(csrf()).sessionAttr(SessionSupport.KEY, user)
                        .param("back", "/recipes/5"))
                .andExpect(redirectedUrl("/recipes/5"));
    }

    @Test
    void themeBackIsSanitisedToo() throws Exception {
        mvc.perform(post("/settings/theme").with(csrf()).param("theme", "matcha").param("back", "/\\evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));
        mvc.perform(post("/settings/theme").with(csrf()).param("theme", "matcha").param("back", "/recipes"))
                .andExpect(redirectedUrl("/recipes"));
    }
}
