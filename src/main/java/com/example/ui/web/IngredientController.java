package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class IngredientController {

    private final IngredientClient client;
    private final RecipeClient recipes;

    public IngredientController(IngredientClient client, RecipeClient recipes) {
        this.client = client;
        this.recipes = recipes;
    }

    @GetMapping("/ingredients")
    public String list(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false, defaultValue = "all") String match,
                       @RequestParam(required = false) String notTag,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", client.list(name, splitCsv(tag), match, splitCsv(notTag), page, 24));
        model.addAttribute("name", name);
        model.addAttribute("tag", tag);
        model.addAttribute("match", match);
        model.addAttribute("notTag", notTag);
        model.addAttribute("pageNo", page);
        return "ingredients/list";
    }

    @GetMapping("/ingredients/{id}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("ingredient", client.get(id));
        return "ingredients/detail";
    }

    @GetMapping("/ingredients/new")
    public String newForm(Model model) {
        model.addAttribute("mode", "new");
        model.addAttribute("pageTitle", "New ingredient");
        return "ingredients/form";
    }

    @PostMapping("/ingredients")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String tags,
                         @RequestParam(required = false) Double densityGPerMl) {
        var created = client.create(name.trim(), splitCsv(tags), densityGPerMl);
        return "redirect:/ingredients/" + created.id();
    }

    @GetMapping("/ingredients/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "Edit ingredient");
        model.addAttribute("ingredient", client.get(id));
        return "ingredients/form";
    }

    @PostMapping("/ingredients/{id}")
    public String update(@PathVariable long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String tags,
                         @RequestParam(required = false) Double densityGPerMl) {
        client.update(id, name.trim(), splitCsv(tags), densityGPerMl);
        return "redirect:/ingredients/" + id;
    }

    @PostMapping("/ingredients/{id}/nutrition")
    public String setNutrition(@PathVariable long id,
                               @RequestParam(required = false) Double basisGrams,
                               @RequestParam(required = false) Double kcal,
                               @RequestParam(required = false) Double proteinG,
                               @RequestParam(required = false) Double carbsG,
                               @RequestParam(required = false) Double fatG,
                               @RequestParam(required = false) Double fiberG,
                               @RequestParam(required = false) Double sugarG,
                               @RequestParam(required = false) Double sodiumMg) {
        Map<String, Object> n = new LinkedHashMap<>();
        put(n, "basisGrams", basisGrams);
        put(n, "kcal", kcal);
        put(n, "proteinG", proteinG);
        put(n, "carbsG", carbsG);
        put(n, "fatG", fatG);
        put(n, "fiberG", fiberG);
        put(n, "sugarG", sugarG);
        put(n, "sodiumMg", sodiumMg);
        client.setNutrition(id, n);
        return "redirect:/ingredients/" + id;
    }

    @PostMapping("/ingredients/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes flash) {
        // Ingredients and recipes live in separate services with no cross-service
        // FK, so the catalogue would delete this happily and orphan any recipe line
        // that points at it. Block it here instead, with a count the user can act on.
        long usedBy = 0;
        try {
            usedBy = recipes.list(null, null, "all", null, List.of(id), 0, 1).totalElements();
        } catch (RuntimeException ignored) {
            // recipe catalogue unreachable — fall through and let the delete proceed
        }
        if (usedBy > 0) {
            IngredientClient.Ingredient ing = null;
            try {
                ing = client.get(id);
            } catch (RuntimeException ignored) {
                // fine — fall back to the id in the message
            }
            String who = ing != null ? "\"" + ing.name() + "\"" : "#" + id;
            flash.addFlashAttribute("error", "Can't delete " + who + " — it's used by "
                    + usedBy + " recipe" + (usedBy == 1 ? "" : "s") + ". Remove it from those first.");
            return "redirect:/ingredients/" + id;
        }
        client.delete(id);
        flash.addFlashAttribute("notice", "Ingredient deleted.");
        return "redirect:/ingredients";
    }

    private static void put(Map<String, Object> m, String k, Double v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    static List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
