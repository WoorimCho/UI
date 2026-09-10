package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tiny JSON endpoints that back the {@code <datalist>} autocompletes (tag filter,
 * tag merge, favourite substitutions, ingredient search). Each just proxies a
 * catalogue search and returns a flat list of names — the forms resolve a chosen
 * name back to an id server-side on submit.
 */
@RestController
public class SuggestController {

    private final IngredientClient ingredients;
    private final RecipeClient recipes;

    public SuggestController(IngredientClient ingredients, RecipeClient recipes) {
        this.ingredients = ingredients;
        this.recipes = recipes;
    }

    @GetMapping("/suggest/tags")
    public List<String> tags(@RequestParam(name = "q", required = false) String q) {
        return safe(() -> ingredients.suggestTagNames(q));
    }

    @GetMapping("/suggest/ingredients")
    public List<String> ingredientNames(@RequestParam(name = "q", required = false) String q) {
        return safe(() -> ingredients.list(q, null, null, null, 0, 20)
                .contentOrEmpty().stream().map(IngredientClient.Ingredient::name).toList());
    }

    @GetMapping("/suggest/recipes")
    public List<String> recipeNames(@RequestParam(name = "q", required = false) String q) {
        return safe(() -> recipes.suggestNames(q));
    }

    private static List<String> safe(java.util.function.Supplier<List<String>> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
