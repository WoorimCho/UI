package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.UserClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Account: profile, password, dietary restrictions, favourite recipes + substitutions. Login-guarded. */
@Controller
public class AccountController {

    private final UserClient users;
    private final RecipeClient recipes;
    private final IngredientClient ingredients;
    private final BffClient bff;
    private final SessionSupport sessions;

    public AccountController(UserClient users, RecipeClient recipes, IngredientClient ingredients,
                            BffClient bff, SessionSupport sessions) {
        this.users = users;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.bff = bff;
        this.sessions = sessions;
    }

    private long accountId(HttpSession s) {
        return sessions.current(s).accountId();
    }

    @GetMapping("/account")
    public String profile(HttpSession session, Model model) {
        model.addAttribute("account", users.account(accountId(session)));
        model.addAttribute("themes", ThemeController.THEMES);
        return "account/profile";
    }

    @PostMapping("/account/profile")
    public String updateProfile(@RequestParam String email,
                                @RequestParam(required = false) String displayName,
                                HttpSession session) {
        long id = accountId(session);
        var updated = users.updateProfile(id, email, displayName);
        CurrentUser u = sessions.current(session);
        sessions.store(session, new CurrentUser(u.accountId(), u.username(), updated.displayName(), u.bffCookie()));
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 HttpSession session, Model model) {
        try {
            users.changePassword(accountId(session), currentPassword, newPassword);
            model.addAttribute("passwordChanged", true);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            model.addAttribute("passwordError", "Current password is wrong, or the new one is under 8 characters.");
        }
        model.addAttribute("account", users.account(accountId(session)));
        model.addAttribute("themes", ThemeController.THEMES);
        return "account/profile";
    }

    @GetMapping("/account/restrictions")
    public String restrictions(HttpSession session, Model model) {
        long id = accountId(session);
        List<UserClient.Restriction> all = users.restrictionCatalogue(null);
        // group by kind for the checkbox sections
        Map<String, List<UserClient.Restriction>> byKind = new TreeMap<>();
        Set<String> catalogueCodes = new LinkedHashSet<>();
        for (UserClient.Restriction r : all) {
            byKind.computeIfAbsent(r.kind() == null ? "other" : r.kind(), k -> new ArrayList<>()).add(r);
            catalogueCodes.add(r.code());
        }
        List<String> selected = users.restrictions(id);
        // anything the account carries that isn't in the seeded catalogue is a custom one
        List<String> custom = selected.stream().filter(c -> !catalogueCodes.contains(c)).sorted().toList();

        model.addAttribute("byKind", byKind);
        model.addAttribute("selected", selected);
        model.addAttribute("custom", custom);
        return "account/restrictions";
    }

    @PostMapping("/account/restrictions")
    public String saveRestrictions(@RequestParam(name = "code", required = false) List<String> codes,
                                   @RequestParam(name = "customCodes", required = false) String customCodes,
                                   HttpSession session) {
        Set<String> all = new LinkedHashSet<>(codes == null ? List.of() : codes);
        // free-text box: comma- or newline-separated custom restrictions
        if (StringUtils.hasText(customCodes)) {
            for (String piece : customCodes.split("[,\\n]")) {
                if (StringUtils.hasText(piece)) {
                    all.add(piece.trim());
                }
            }
        }
        users.replaceRestrictions(accountId(session), List.copyOf(all));
        return "redirect:/account/restrictions";
    }

    @GetMapping("/account/favourites")
    public String favourites(HttpSession session, Model model) {
        CurrentUser u = sessions.current(session);
        model.addAttribute("recipes", bff.favoriteRecipes(u.accountId(), u.bffCookie()));
        model.addAttribute("alternatives", resolveAlternatives(users.favoriteAlternatives(u.accountId())));
        return "account/favourites";
    }

    /** A favourite substitution with the ids resolved to names for display. */
    public record AltRow(Long recipeId, String recipeName,
                         Long ingredientId, String ingredientName,
                         Long replacementIngredientId, String replacementName) {
    }

    private List<AltRow> resolveAlternatives(List<UserClient.FavoriteAlternative> raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        Set<Long> ingredientIds = new LinkedHashSet<>();
        for (UserClient.FavoriteAlternative a : raw) {
            ingredientIds.add(a.ingredientId());
            ingredientIds.add(a.replacementIngredientId());
        }
        Map<Long, IngredientClient.Ingredient> ings = safeByIds(ingredientIds);
        Map<Long, String> recipeNames = new LinkedHashMap<>();

        List<AltRow> rows = new ArrayList<>();
        for (UserClient.FavoriteAlternative a : raw) {
            String rName = recipeNames.computeIfAbsent(a.recipeId(), this::safeRecipeName);
            rows.add(new AltRow(
                    a.recipeId(), rName,
                    a.ingredientId(), nameOf(ings, a.ingredientId()),
                    a.replacementIngredientId(), nameOf(ings, a.replacementIngredientId())));
        }
        return rows;
    }

    private Map<Long, IngredientClient.Ingredient> safeByIds(Set<Long> ids) {
        try {
            return ingredients.byIds(ids);
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private String safeRecipeName(long id) {
        try {
            var r = recipes.get(id);
            return r == null ? "#" + id : r.name();
        } catch (RuntimeException e) {
            return "#" + id;
        }
    }

    private static String nameOf(Map<Long, IngredientClient.Ingredient> ings, Long id) {
        IngredientClient.Ingredient i = ings.get(id);
        return i != null ? i.name() : "#" + id;
    }

    @PostMapping("/favourites/recipes/{recipeId}")
    public String favourite(@org.springframework.web.bind.annotation.PathVariable long recipeId,
                            @RequestParam(defaultValue = "add") String action,
                            @RequestParam(required = false) String back,
                            HttpSession session) {
        long id = accountId(session);
        if ("remove".equals(action)) {
            users.removeFavoriteRecipe(id, recipeId);
        } else {
            users.addFavoriteRecipe(id, recipeId);
        }
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/account/favourites");
    }

    /**
     * Add or update a favourite substitution. {@code recipe} / {@code ingredient}
     * / {@code replacement} each accept a name or a numeric id.
     */
    @PostMapping("/favourites/alternatives")
    public String saveAlternative(@RequestParam String recipe,
                                  @RequestParam String ingredient,
                                  @RequestParam String replacement,
                                  HttpSession session, RedirectAttributes flash) {
        long id = accountId(session);

        Long recipeId = resolveRecipeId(recipe);
        Long ingredientId = resolveIngredientId(ingredient);
        Long replacementId = resolveIngredientId(replacement);
        List<String> missing = new ArrayList<>();
        if (recipeId == null) {
            missing.add("recipe \"" + recipe + "\"");
        }
        if (ingredientId == null) {
            missing.add("ingredient \"" + ingredient + "\"");
        }
        if (replacementId == null) {
            missing.add("ingredient \"" + replacement + "\"");
        }
        if (!missing.isEmpty()) {
            flash.addFlashAttribute("error", "Couldn't find " + String.join(", ", missing) + ".");
            return "redirect:/account/favourites";
        }

        Map<String, Map<String, Object>> byKey = currentAlternatives(id);
        byKey.put(recipeId + ":" + ingredientId, Map.of(
                "recipeId", recipeId, "ingredientId", ingredientId,
                "replacementIngredientId", replacementId));
        users.replaceFavoriteAlternatives(id, List.copyOf(byKey.values()));
        return "redirect:/account/favourites";
    }

    @PostMapping("/favourites/alternatives/remove")
    public String removeAlternative(@RequestParam long recipeId,
                                    @RequestParam long ingredientId,
                                    HttpSession session) {
        long id = accountId(session);
        Map<String, Map<String, Object>> byKey = currentAlternatives(id);
        byKey.remove(recipeId + ":" + ingredientId);
        users.replaceFavoriteAlternatives(id, List.copyOf(byKey.values()));
        return "redirect:/account/favourites";
    }

    private Map<String, Map<String, Object>> currentAlternatives(long accountId) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (UserClient.FavoriteAlternative a : users.favoriteAlternatives(accountId)) {
            byKey.put(a.recipeId() + ":" + a.ingredientId(), Map.of(
                    "recipeId", a.recipeId(), "ingredientId", a.ingredientId(),
                    "replacementIngredientId", a.replacementIngredientId()));
        }
        return byKey;
    }

    private Long resolveRecipeId(String ref) {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        if (ref.trim().matches("\\d+")) {
            return Long.valueOf(ref.trim());
        }
        var r = recipes.findByName(ref);
        return r == null ? null : r.id();
    }

    private Long resolveIngredientId(String ref) {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        if (ref.trim().matches("\\d+")) {
            return Long.valueOf(ref.trim());
        }
        var i = ingredients.findByName(ref);
        return i == null ? null : i.id();
    }
}
