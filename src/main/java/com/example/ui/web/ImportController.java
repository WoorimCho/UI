package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Bulk CSV import for both catalogues. */
@Controller
public class ImportController {

    private final IngredientClient ingredients;
    private final RecipeClient recipes;

    public ImportController(IngredientClient ingredients, RecipeClient recipes) {
        this.ingredients = ingredients;
        this.recipes = recipes;
    }

    @GetMapping("/import")
    public String page() {
        return "import/index";
    }

    @PostMapping("/import/ingredients")
    public String importIngredients(@RequestParam("file") MultipartFile file, Model model) {
        model.addAttribute("ingredientResult", ingredients.importCsv(read(file), file.getOriginalFilename()));
        return "import/index";
    }

    @PostMapping("/import/recipes")
    public String importRecipes(@RequestParam("file") MultipartFile file, Model model) {
        model.addAttribute("recipeResult", recipes.importCsv(read(file), file.getOriginalFilename()));
        return "import/index";
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty CSV file first.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
