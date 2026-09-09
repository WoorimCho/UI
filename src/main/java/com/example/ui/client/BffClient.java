package com.example.ui.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * The BFF: form-login (it stays the auth authority for {@code /bff/**}), the
 * composed + personalised recipe view, favourites, and the calculators.
 *
 * <p>The BFF holds a servlet session; this UI logs in on the user's behalf,
 * keeps the {@code JSESSIONID} cookie in its own session, and relays it on
 * every subsequent call.
 */
@Component
public class BffClient {

    private final RestClient http;

    public BffClient(RestClient bffRestClient) {
        this.http = bffRestClient;
    }

    /**
     * POST /bff/login (form-encoded). Returns the {@code JSESSIONID=...} cookie
     * to stash, or {@code null} if the credentials were rejected (401).
     */
    public String login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        try {
            var response = http.post().uri("/bff/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().toBodilessEntity();
            return sessionCookie(response.getHeaders());
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            return null;
        }
    }

    public void logout(String sessionCookie) {
        if (sessionCookie == null) {
            return;
        }
        http.post().uri("/bff/logout").header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve().toBodilessEntity();
    }

    public RecipeView recipe(long id, String sessionCookie) {
        return http.get().uri("/bff/recipes/{id}", id)
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve().body(RecipeView.class);
    }

    public List<RecipeSummary> favoriteRecipes(long accountId, String sessionCookie) {
        return http.get().uri("/bff/accounts/{id}/favorite-recipes", accountId)
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<List<RecipeSummary>>() {
                });
    }

    public NutritionResult nutrition(long recipeId, int servings, String sessionCookie) {
        return http.get().uri(uri -> uri.path("/bff/recipes/{id}/nutrition").queryParam("servings", servings).build(recipeId))
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve().body(NutritionResult.class);
    }

    public CalorieResult calories(long recipeId, int servings, String sessionCookie) {
        return http.get().uri(uri -> uri.path("/bff/recipes/{id}/calories").queryParam("servings", servings).build(recipeId))
                .header(HttpHeaders.COOKIE, sessionCookie)
                .retrieve().body(CalorieResult.class);
    }

    public PortionResult portions(long recipeId, Double scale,
                                  Long anchorIngredientId, Double anchorAmount, String anchorUnit,
                                  String sessionCookie) {
        return http.get().uri(uri -> {
            uri.path("/bff/recipes/{id}/portions");
            if (scale != null) {
                uri.queryParam("scale", scale);
            }
            if (anchorIngredientId != null) {
                uri.queryParam("anchorIngredientId", anchorIngredientId);
            }
            if (anchorAmount != null) {
                uri.queryParam("anchorAmount", anchorAmount);
            }
            if (anchorUnit != null && !anchorUnit.isBlank()) {
                uri.queryParam("anchorUnit", anchorUnit);
            }
            return uri.build(recipeId);
        }).header(HttpHeaders.COOKIE, sessionCookie).retrieve().body(PortionResult.class);
    }

    private static String sessionCookie(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return null;
        }
        for (String c : setCookies) {
            if (c.regionMatches(true, 0, "JSESSIONID=", 0, "JSESSIONID=".length())) {
                return c.split(";", 2)[0];   // "JSESSIONID=abc123"
            }
        }
        return null;
    }

    // ── shapes (the BFF's response records) ──────────────────────────────────

    public record RecipeView(
            Long id, String name, String creator, Integer version,
            List<Step> steps, List<Line> ingredients,
            List<String> tags, List<String> inheritedTags,
            List<RestrictionConflict> restrictionConflicts, Boolean compatibleWithAccount) {
        public List<Step> stepsOrEmpty() {
            return steps == null ? List.of() : steps;
        }
        public List<Line> ingredientsOrEmpty() {
            return ingredients == null ? List.of() : ingredients;
        }
        public List<String> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }
        public List<String> inheritedTagsOrEmpty() {
            return inheritedTags == null ? List.of() : inheritedTags;
        }
        public List<RestrictionConflict> conflictsOrEmpty() {
            return restrictionConflicts == null ? List.of() : restrictionConflicts;
        }
    }

    public record Step(Integer position, String text, java.util.Set<String> tools) {
        public java.util.Set<String> toolsOrEmpty() {
            return tools == null ? java.util.Set.of() : tools;
        }
    }

    public record Line(
            Long ingredientId, String name, boolean resolved, String quantity,
            Double amount, String unit,
            boolean optional, boolean replaceable,
            List<String> tags, List<Replacement> replacements,
            Long preferredReplacementId, String preferredReplacementName) {
        public List<String> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }
        public List<Replacement> replacementsOrEmpty() {
            return replacements == null ? List.of() : replacements;
        }
    }

    public record Replacement(Long ingredientId, String name, boolean resolved, Long recipeId) {
    }

    public record RestrictionConflict(String restriction, List<Long> ingredientIds, List<String> ingredientNames) {
    }

    public record RecipeSummary(Long id, String name, String creator, Integer version, List<String> tags) {
        public List<String> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }
    }

    public record NutritionSum(
            Double kcal, Double proteinG, Double carbsG, Double fatG,
            Double fiberG, Double sugarG, Double sodiumMg) {
    }

    public record LineNutrition(
            Long ingredientId, String name, Double amount, String unit, Double grams, Double millilitres,
            boolean optional, boolean counted, String note, NutritionSum contribution) {
    }

    public record NutritionResult(
            Long recipeId, String recipeName, int servings,
            NutritionSum total, NutritionSum perServing,
            NutritionSum per100g, Double countedGrams,
            List<LineNutrition> lines, List<String> notCounted) {
        public List<LineNutrition> linesOrEmpty() {
            return lines == null ? List.of() : lines;
        }
        public List<String> notCountedOrEmpty() {
            return notCounted == null ? List.of() : notCounted;
        }
    }

    public record CalorieResult(
            Long recipeId, String recipeName, int servings,
            Double totalKcal, Double perServingKcal,
            Double kcalPer100g, Double kcalPerGram, Double countedGrams,
            int countedLines, int totalLines) {
    }

    public record ScaledLine(
            Long ingredientId, String name, Double originalAmount, String unit,
            Double scaledAmount, Double scaledGrams, Double scaledMillilitres,
            String quantityText, boolean scaled) {
    }

    public record PortionResult(Long recipeId, String recipeName, double scale, List<ScaledLine> lines) {
        public List<ScaledLine> linesOrEmpty() {
            return lines == null ? List.of() : lines;
        }
    }
}
