package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Browse and extend the nutrition reference (common foods, per 100 g). */
@Controller
public class NutritionReferenceController {

    /** UI field key -> catalogue sort property. Order is the header order. */
    static final Map<String, String> SORTABLE = new LinkedHashMap<>();
    static {
        SORTABLE.put("name", "name");
        SORTABLE.put("kcal", "kcal");
        SORTABLE.put("protein", "proteinG");
        SORTABLE.put("carbs", "carbsG");
        SORTABLE.put("fat", "fatG");
        SORTABLE.put("fiber", "fiberG");
        SORTABLE.put("sugar", "sugarG");
        SORTABLE.put("sodium", "sodiumMg");
    }

    private final IngredientClient client;

    public NutritionReferenceController(IngredientClient client) {
        this.client = client;
    }

    private static final int PAGE_SIZE = 25;

    @GetMapping("/nutrition-reference")
    public String list(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false, defaultValue = "asc") String dir,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(name = "jump", required = false) Integer jump,
                       Model model) {
        if (jump != null && jump > 0) {
            page = jump - 1;
        }
        String sortQuery = Sorts.query(sort, dir, SORTABLE);
        // the catalogue endpoint returns the whole (small) reference — paginate here
        List<IngredientClient.NutritionRef> all = client.nutritionReference(name, sortQuery);
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(page, 0), totalPages - 1);
        int from = page * PAGE_SIZE;
        model.addAttribute("rows", all.subList(from, Math.min(from + PAGE_SIZE, all.size())));

        MultiValueMap<String, String> base = new LinkedMultiValueMap<>();
        base.add("name", name);
        model.addAttribute("sortLinks", Sorts.links("/nutrition-reference", base, sort, dir, SORTABLE.keySet()));
        model.addAttribute("sortField", sort == null ? "" : sort);
        model.addAttribute("sortDir", "desc".equalsIgnoreCase(dir) ? "desc" : "asc");
        model.addAttribute("name", name);

        Map<String, String> pagerParams = new LinkedHashMap<>();
        pagerParams.put("name", name);
        pagerParams.put("sort", sort);
        pagerParams.put("dir", dir);
        model.addAttribute("pager", new Pager("/nutrition-reference", pagerParams, page, totalPages));
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
