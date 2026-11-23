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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SECURITY.md N4 in the UI: resolving a recipe form costs one catalogue call per
 * distinct ingredient name, so an oversized form has to be refused <em>before</em>
 * that happens.
 */
@WebMvcTest(RecipeController.class)
@Import({SessionSupport.class, AuthInterceptor.class, GlobalModelAdvice.class, WebConfig.class, SecurityConfig.class})
class RecipeFormLimitsTest {

    @Autowired MockMvc mvc;

    @MockitoBean IngredientClient ingredients;
    @MockitoBean RecipeClient recipes;
    @MockitoBean UserClient users;
    @MockitoBean BffClient bff;

    private static MultiValueMap<String, String> form(int lines) {
        MultiValueMap<String, String> p = new LinkedMultiValueMap<>();
        p.add("name", "Soup");
        p.add("tags", "");
        p.add("stepText", "Boil.");
        IntStream.rangeClosed(1, lines).forEach(i -> {
            p.add("lineIngredientName", "ingredient-" + i);
            p.add("lineReplacementNames", "");
        });
        return p;
    }

    // ---- the form model on its own ----

    @Test
    void aTypicalFormIsWithinTheLimits() {
        assertThat(RecipeForm.from(form(12)).limitViolation()).isNull();
    }

    @Test
    void exactlyTheMaximumNumberOfLinesIsFine() {
        assertThat(RecipeForm.from(form(RecipeForm.MAX_INGREDIENT_LINES)).limitViolation()).isNull();
    }

    @Test
    void oneLineTooMany() {
        assertThat(RecipeForm.from(form(RecipeForm.MAX_INGREDIENT_LINES + 1)).limitViolation())
                .contains("at most " + RecipeForm.MAX_INGREDIENT_LINES);
    }

    @Test
    void tooManySteps() {
        MultiValueMap<String, String> p = form(1);
        p.put("stepText", IntStream.rangeClosed(1, RecipeForm.MAX_STEPS + 1).mapToObj(i -> "step " + i)
                .collect(Collectors.toList()));
        assertThat(RecipeForm.from(p).limitViolation()).contains("at most " + RecipeForm.MAX_STEPS + " steps");
    }

    @Test
    void tooManyReplacementsOnOneLine() {
        MultiValueMap<String, String> p = form(1);
        p.put("lineReplacementNames", Collections.singletonList(IntStream.rangeClosed(1, RecipeForm.MAX_REPLACEMENTS_PER_LINE + 1)
                .mapToObj(i -> "sub" + i).collect(Collectors.joining(", "))));
        assertThat(RecipeForm.from(p).limitViolation()).contains("at most " + RecipeForm.MAX_REPLACEMENTS_PER_LINE);
    }

    @Test
    void tooManyTags() {
        MultiValueMap<String, String> p = form(1);
        p.set("tags", IntStream.rangeClosed(1, RecipeForm.MAX_TAGS + 1).mapToObj(i -> "t" + i)
                .collect(Collectors.joining(",")));
        assertThat(RecipeForm.from(p).limitViolation()).contains("at most " + RecipeForm.MAX_TAGS + " tags");
    }

    @Test
    void blankRowsDoNotCountTowardsTheLimits() {
        MultiValueMap<String, String> p = form(3);
        for (int i = 0; i < 500; i++) {
            p.add("lineIngredientName", "   ");
            p.add("stepText", "");
        }
        assertThat(RecipeForm.from(p).limitViolation()).isNull();
    }

    // ---- through the controller ----

    private MockHttpServletRequestBuilder loggedInPost(String url, MultiValueMap<String, String> params) {
        return post(url).with(csrf())
                .sessionAttr(SessionSupport.KEY, new CurrentUser(1L, "bob", "Bob", "JSESSIONID=abc"))
                .params(params);
    }

    @Test
    void anOversizedFormIsRefusedBeforeAnyIngredientLookup() throws Exception {
        mvc.perform(loggedInPost("/recipes", form(RecipeForm.MAX_INGREDIENT_LINES + 1)))
                .andExpect(status().isOk())                                   // the form is re-rendered, nothing lost
                .andExpect(content().string(containsString("at most " + RecipeForm.MAX_INGREDIENT_LINES)));
        verify(ingredients, never()).resolveIds(any());
        verify(recipes, never()).create(any());
    }

    @Test
    void theSameGoesForEditing() throws Exception {
        mvc.perform(loggedInPost("/recipes/5", form(RecipeForm.MAX_INGREDIENT_LINES + 1)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at most " + RecipeForm.MAX_INGREDIENT_LINES)));
        verify(ingredients, never()).resolveIds(any());
        verify(recipes, never()).update(anyLong(), any());
    }
}
