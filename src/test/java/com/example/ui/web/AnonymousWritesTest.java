package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.RecipeCrawlerClient;
import com.example.ui.client.UserClient;
import com.example.ui.config.SecurityConfig;
import com.example.ui.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SECURITY.md N7, end to end through every controller: an anonymous visitor can read
 * the catalogues but cannot change anything or reach the import / crawl tools. A
 * mock of every downstream client proves nothing was called on a refused request.
 */
@WebMvcTest({HomeController.class, AuthController.class, IngredientController.class, AccountController.class,
        RecipeController.class, TagController.class, NutritionReferenceController.class, CalculatorController.class,
        ImportController.class, ImportUrlController.class, SuggestController.class, ThemeController.class})
@Import({SessionSupport.class, AuthInterceptor.class, GlobalModelAdvice.class, WebConfig.class, SecurityConfig.class})
class AnonymousWritesTest {

    @Autowired MockMvc mvc;
    @Autowired RequestMappingHandlerMapping mappings;

    @MockitoBean IngredientClient ingredients;
    @MockitoBean RecipeClient recipes;
    @MockitoBean UserClient users;
    @MockitoBean BffClient bff;
    @MockitoBean RecipeCrawlerClient crawler;

    private void assertNothingWasCalled() {
        verifyNoInteractions(ingredients, recipes, users, bff, crawler);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/ingredients", "/ingredients/5", "/ingredients/5/delete", "/ingredients/5/nutrition",
            "/recipes", "/recipes/5", "/recipes/5/delete",
            "/tags", "/tags/merge", "/tags/5/delete",
            "/nutrition-reference", "/nutrition-reference/5",
            "/import/ingredients", "/import/recipes",
            "/recipes/import-url/preview", "/recipes/import-url/commit", "/recipes/crawl",
            "/account/profile", "/account/password", "/account/restrictions",
            "/favourites/recipes/5", "/favourites/alternatives", "/favourites/alternatives/remove"
    })
    void anonymousWritesAreBouncedToLoginAndReachNothing(String url) throws Exception {
        mvc.perform(post(url).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?next=*"));
        assertNothingWasCalled();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/ingredients/new", "/ingredients/5/edit", "/recipes/new", "/recipes/5/edit",
            "/recipes/import-url", "/recipes/crawl", "/import",
            "/account", "/account/favourites", "/account/restrictions",
            "/recipes/5/nutrition", "/recipes/5/calories", "/recipes/5/portions", "/recipes/5/portions.json"
    })
    void formsAndToolsNeedALoginJustToOpen(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?next=*"));
        assertNothingWasCalled();
    }

    @Test
    void signInAndSignUpPagesStayPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void theThemeSwitchAndLogoutWorkWithoutASession() throws Exception {
        mvc.perform(post("/settings/theme").with(csrf()).param("theme", "matcha"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account"));
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void aRefusedWriteGoesBackToThePageTheUserWasOn() throws Exception {
        mvc.perform(post("/tags").with(csrf()).header("Referer", "http://localhost/tags?prefix=veg"))
                .andExpect(redirectedUrl("/login?next=%2Ftags%3Fprefix%3Dveg"));
        mvc.perform(post("/tags").with(csrf()))                                  // no Referer -> home
                .andExpect(redirectedUrl("/login?next=%2F"));
    }

    @Test
    void aHostileRefererCannotMakeTheLoginRedirectLeaveTheSite() throws Exception {
        mvc.perform(post("/tags").with(csrf()).header("Referer", "http://localhost//evil.example/x"))
                .andExpect(redirectedUrl("/login?next=%2F"));
        mvc.perform(post("/tags").with(csrf()).header("Referer", "not a url at all"))
                .andExpect(redirectedUrl("/login?next=%2F"));
    }

    @Test
    void aLoggedInUserGetsThrough() throws Exception {
        var user = new CurrentUser(1L, "bob", "Bob", "JSESSIONID=abc");
        mvc.perform(post("/tags/5/delete").with(csrf()).sessionAttr(SessionSupport.KEY, user))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tags"));
        verify(ingredients).deleteTag(5L);
    }

    @Test
    void aRefusedRequestDoesNotCreateASession() throws Exception {
        var result = mvc.perform(post("/ingredients").with(csrf())).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    /**
     * The invariant behind all of the above, checked against the real route table:
     * every write route requires a login except the four that must not. A new POST
     * route that slipped onto the anonymous list would fail here.
     */
    @Test
    void everyWriteRouteRequiresALoginExceptTheAnonymousFour() {
        Set<String> anonymousWrites = new TreeSet<>();
        mappings.getHandlerMethods().keySet().forEach(info -> {
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            for (HttpMethod method : info.getMethodsCondition().getMethods().stream()
                    .map(m -> HttpMethod.valueOf(m.name())).toList()) {
                if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
                    continue;
                }
                for (String route : patterns) {
                    if (!AuthInterceptor.requiresLogin(method.name(), route)) {
                        anonymousWrites.add(method + " " + route);
                    }
                }
            }
        });
        assertThat(anonymousWrites).containsExactlyInAnyOrder(
                "POST /login", "POST /register", "POST /logout", "POST /settings/theme");
    }
}
