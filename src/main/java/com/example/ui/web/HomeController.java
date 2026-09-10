package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final IngredientClient ingredients;
    private final RecipeClient recipes;

    public HomeController(IngredientClient ingredients, RecipeClient recipes) {
        this.ingredients = ingredients;
        this.recipes = recipes;
    }

    @GetMapping("/")
    public String home(Model model) {
        try {
            model.addAttribute("ingredientCount", ingredients.list(null, null, null, 0, 1).totalElements());
            model.addAttribute("recipeCount", recipes.list(null, null, null, null, null, 0, 1).totalElements());
        } catch (RuntimeException e) {
            model.addAttribute("countsUnavailable", true);
        }
        return "home";
    }
}
