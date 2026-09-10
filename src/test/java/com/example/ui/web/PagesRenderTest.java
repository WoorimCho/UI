package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.PageResult;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.UserClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.ui.config.SecurityConfig;
import com.example.ui.config.WebConfig;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The templates render and the auth guard bites. Downstream clients are mocked —
 * this is the view + routing layer, not an integration test.
 */
@WebMvcTest({HomeController.class, AuthController.class, IngredientController.class, AccountController.class})
@Import({SessionSupport.class, AuthInterceptor.class, GlobalModelAdvice.class, WebConfig.class, SecurityConfig.class})
class PagesRenderTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean IngredientClient ingredients;
    @MockitoBean RecipeClient recipes;
    @MockitoBean UserClient users;
    @MockitoBean BffClient bff;

    private static <T> PageResult<T> emptyPage() {
        return new PageResult<>(List.of(), 0, 20, 0, 0, true, true, 0, true);
    }

    @Test
    void homeRenders() throws Exception {
        when(ingredients.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(emptyPage());
        when(recipes.list(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(emptyPage());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Allergen Information System")));
    }

    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void ingredientListRenders() throws Exception {
        when(ingredients.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(emptyPage());
        mvc.perform(get("/ingredients"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ingredients")));
    }

    @Test
    void accountRedirectsToLoginWhenAnonymous() throws Exception {
        mvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }
}
