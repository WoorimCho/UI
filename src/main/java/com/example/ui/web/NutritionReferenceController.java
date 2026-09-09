package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Browse and extend the nutrition reference (common foods, per 100 g). */
@Controller
public class NutritionReferenceController {

    private final IngredientClient client;

    public NutritionReferenceController(IngredientClient client) {
        this.client = client;
    }

    @GetMapping("/nutrition-reference")
    public String list(@RequestParam(required = false) String name, Model model) {
        model.addAttribute("rows", client.nutritionReference(name));
        model.addAttribute("name", name);
        return "nutrition-reference/list";
    }

    @PostMapping("/nutrition-reference")
    public String add(@RequestParam String name,
                      @RequestParam(required = false) Double basisGrams,
                      @RequestParam(required = false) Double kcal,
                      @RequestParam(required = false) Double proteinG,
                      @RequestParam(required = false) Double carbsG,
                      @RequestParam(required = false) Double fatG,
                      @RequestParam(required = false) Double fiberG,
                      @RequestParam(required = false) Double sugarG,
                      @RequestParam(required = false) Double sodiumMg,
                      RedirectAttributes flash) {
        Map<String, Object> body = body(name, basisGrams, kcal, proteinG, carbsG, fatG, fiberG, sugarG, sodiumMg);
        try {
            client.addNutritionReference(body);
            flash.addFlashAttribute("notice", "Added \"" + name.trim() + "\" to the reference.");
        } catch (HttpClientErrorException.Conflict e) {
            flash.addFlashAttribute("error", "\"" + name.trim() + "\" is already in the reference.");
        }
        return "redirect:/nutrition-reference";
    }

    @PostMapping("/nutrition-reference/{id}")
    public String update(@PathVariable long id,
                         @RequestParam String name,
                         @RequestParam(required = false) Double basisGrams,
                         @RequestParam(required = false) Double kcal,
                         @RequestParam(required = false) Double proteinG,
                         @RequestParam(required = false) Double carbsG,
                         @RequestParam(required = false) Double fatG,
                         @RequestParam(required = false) Double fiberG,
                         @RequestParam(required = false) Double sugarG,
                         @RequestParam(required = false) Double sodiumMg,
                         RedirectAttributes flash) {
        Map<String, Object> body = body(name, basisGrams, kcal, proteinG, carbsG, fatG, fiberG, sugarG, sodiumMg);
        try {
            client.updateNutritionReference(id, body);
            flash.addFlashAttribute("notice", "Updated \"" + name.trim() + "\".");
        } catch (HttpClientErrorException.Conflict e) {
            flash.addFlashAttribute("error", "Another reference row is already named \"" + name.trim() + "\".");
        } catch (HttpClientErrorException.NotFound e) {
            flash.addFlashAttribute("error", "That reference row no longer exists.");
        }
        return "redirect:/nutrition-reference";
    }

    private static Map<String, Object> body(String name, Double basisGrams, Double kcal, Double proteinG,
                                            Double carbsG, Double fatG, Double fiberG, Double sugarG, Double sodiumMg) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", name.trim());
        put(b, "basisGrams", basisGrams);
        put(b, "kcal", kcal);
        put(b, "proteinG", proteinG);
        put(b, "carbsG", carbsG);
        put(b, "fatG", fatG);
        put(b, "fiberG", fiberG);
        put(b, "sugarG", sugarG);
        put(b, "sodiumMg", sodiumMg);
        return b;
    }

    private static void put(Map<String, Object> m, String k, Double v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}
