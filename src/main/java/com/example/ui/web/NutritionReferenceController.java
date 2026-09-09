package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name.trim());
        put(body, "basisGrams", basisGrams);
        put(body, "kcal", kcal);
        put(body, "proteinG", proteinG);
        put(body, "carbsG", carbsG);
        put(body, "fatG", fatG);
        put(body, "fiberG", fiberG);
        put(body, "sugarG", sugarG);
        put(body, "sodiumMg", sodiumMg);
        try {
            client.addNutritionReference(body);
            flash.addFlashAttribute("notice", "Added \"" + name.trim() + "\" to the reference.");
        } catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
            flash.addFlashAttribute("error", "\"" + name.trim() + "\" is already in the reference.");
        }
        return "redirect:/nutrition-reference";
    }

    private static void put(Map<String, Object> m, String k, Double v) {
        if (v != null) {
            m.put(k, v);
        }
    }
}
