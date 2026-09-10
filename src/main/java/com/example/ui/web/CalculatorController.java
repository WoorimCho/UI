package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** The BFF calculators. Login-guarded by {@link com.example.ui.config.WebConfig}. */
@Controller
public class CalculatorController {

    private final BffClient bff;
    private final IngredientClient ingredients;
    private final SessionSupport sessions;

    public CalculatorController(BffClient bff, IngredientClient ingredients, SessionSupport sessions) {
        this.bff = bff;
        this.ingredients = ingredients;
        this.sessions = sessions;
    }

    private String cookie(HttpSession session) {
        CurrentUser u = sessions.current(session);
        return u == null ? null : u.bffCookie();
    }

    @GetMapping("/recipes/{id}/nutrition")
    public String nutrition(@PathVariable long id,
                            @RequestParam(defaultValue = "1") int servings,
                            HttpSession session, Model model) {
        model.addAttribute("result", bff.nutrition(id, Math.max(servings, 1), cookie(session)));
        model.addAttribute("recipeId", id);
        model.addAttribute("servings", Math.max(servings, 1));
        return "calculators/nutrition";
    }

    @GetMapping("/recipes/{id}/calories")
    public String calories(@PathVariable long id,
                           @RequestParam(defaultValue = "1") int servings,
                           HttpSession session, Model model) {
        model.addAttribute("result", bff.calories(id, Math.max(servings, 1), cookie(session)));
        model.addAttribute("recipeId", id);
        model.addAttribute("servings", Math.max(servings, 1));
        return "calculators/calories";
    }

    @GetMapping("/recipes/{id}/portions")
    public String portions(@PathVariable long id,
                           @RequestParam(required = false) Double scale,
                           @RequestParam(required = false) String anchorIngredientName,
                           @RequestParam(required = false) Double anchorAmount,
                           @RequestParam(required = false) String anchorUnit,
                           HttpSession session, Model model) {
        model.addAttribute("recipeId", id);
        model.addAttribute("scale", scale);
        model.addAttribute("anchorIngredientName", anchorIngredientName);

        Long anchorIngredientId = null;
        if (StringUtils.hasText(anchorIngredientName)) {
            anchorIngredientId = ingredients.resolveId(anchorIngredientName.trim());
            if (anchorIngredientId == null) {
                model.addAttribute("error", "No ingredient found named \""
                        + anchorIngredientName.trim() + "\".");
                return "calculators/portions";
            }
        }

        boolean asked = scale != null || anchorIngredientId != null || anchorAmount != null;
        if (asked) {
            BffClient.PortionResult result =
                    bff.portions(id, scale, anchorIngredientId, anchorAmount, anchorUnit, cookie(session));
            model.addAttribute("result", result);
            model.addAttribute("batchTotals", batchTotals(result));
        }
        return "calculators/portions";
    }

    /** "Batch ≈ 1,346 g · 1,929 kcal" — whichever the recipe could weigh; blank if neither. */
    private static String batchTotals(BffClient.PortionResult r) {
        StringBuilder sb = new StringBuilder();
        if (r.totalGrams() != null) {
            sb.append(String.format("%,d g", Math.round(r.totalGrams())));
        }
        if (r.totalKcal() != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(String.format("%,d kcal", Math.round(r.totalKcal())));
        }
        return sb.length() == 0 ? "" : "Batch ≈ " + sb;
    }

    /** JSON for the live portion slider — same data as the page, no re-render. */
    @GetMapping("/recipes/{id}/portions.json")
    @ResponseBody
    public com.example.ui.client.BffClient.PortionResult portionsJson(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") double scale,
            HttpSession session) {
        return bff.portions(id, Math.max(scale, 0.01), null, null, null, cookie(session));
    }
}
