package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.UserClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class RecipeController {

    private final RecipeClient recipes;
    private final IngredientClient ingredients;
    private final BffClient bff;
    private final UserClient users;
    private final SessionSupport sessions;

    public RecipeController(RecipeClient recipes, IngredientClient ingredients,
                            BffClient bff, UserClient users, SessionSupport sessions) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.bff = bff;
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping("/recipes")
    public String list(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false, defaultValue = "all") String match,
                       @RequestParam(required = false) String ingredientId,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        List<Long> ids = parseIds(ingredientId);
        model.addAttribute("page", recipes.list(name, IngredientController.splitCsv(tag), match, ids, page, 20));
        model.addAttribute("name", name);
        model.addAttribute("tag", tag);
        model.addAttribute("match", match);
        model.addAttribute("ingredientId", ingredientId);
        model.addAttribute("pageNo", page);
        return "recipes/list";
    }

    @GetMapping("/recipes/random")
    public String random(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String tag,
                         @RequestParam(required = false, defaultValue = "all") String match,
                         Model model) {
        var r = recipes.random(name, IngredientController.splitCsv(tag), match, null);
        return "redirect:/recipes/" + r.id();
    }

    @GetMapping("/recipes/{id}")
    public String detail(@PathVariable long id, HttpSession session, Model model) {
        CurrentUser user = sessions.current(session);
        RecipeDetail view = (user != null && user.bffCookie() != null)
                ? fromBff(bff.recipe(id, user.bffCookie()))
                : fromCatalogue(recipes.get(id));
        model.addAttribute("recipe", view);
        model.addAttribute("favourited", user != null && isFavourite(user.accountId(), id));
        return "recipes/detail";
    }

    private boolean isFavourite(long accountId, long recipeId) {
        try {
            return users.favoriteRecipeIds(accountId).contains(recipeId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ── create / edit ────────────────────────────────────────────────────────

    @GetMapping("/recipes/new")
    public String newForm(Model model) {
        model.addAttribute("mode", "new");
        model.addAttribute("pageTitle", "New recipe");
        return "recipes/form";
    }

    @GetMapping("/recipes/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("mode", "edit");
        model.addAttribute("pageTitle", "Edit recipe");
        model.addAttribute("recipe", recipes.get(id));
        return "recipes/form";
    }

    @PostMapping("/recipes")
    public String create(@RequestParam MultiValueMap<String, String> params) {
        var created = recipes.create(RecipeForm.from(params).toBody());
        return "redirect:/recipes/" + created.id();
    }

    @PostMapping("/recipes/{id}")
    public String update(@PathVariable long id, @RequestParam MultiValueMap<String, String> params) {
        recipes.update(id, RecipeForm.from(params).toBody());
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/recipes/{id}/delete")
    public String delete(@PathVariable long id) {
        recipes.delete(id);
        return "redirect:/recipes";
    }

    // ── assemblers ───────────────────────────────────────────────────────────

    private RecipeDetail fromBff(BffClient.RecipeView v) {
        List<RecipeDetail.Step> steps = v.stepsOrEmpty().stream()
                .map(s -> new RecipeDetail.Step(s.position(), s.text(), s.toolsOrEmpty()))
                .toList();
        List<RecipeDetail.Line> lines = v.ingredientsOrEmpty().stream()
                .map(l -> new RecipeDetail.Line(
                        l.ingredientId(), l.name(), l.resolved(), l.quantity(), l.amount(), l.unit(),
                        l.optional(), l.replaceable(), l.tagsOrEmpty(),
                        l.replacementsOrEmpty().stream()
                                .map(r -> new RecipeDetail.Sub(r.ingredientId(), r.name(), r.resolved(), r.recipeId()))
                                .toList(),
                        l.preferredReplacementId(), l.preferredReplacementName()))
                .toList();
        List<RecipeDetail.Conflict> conflicts = v.conflictsOrEmpty().stream()
                .map(c -> new RecipeDetail.Conflict(c.restriction(), c.ingredientIds(), c.ingredientNames()))
                .toList();
        return new RecipeDetail(v.id(), v.name(), v.creator(), v.version(), steps, lines,
                v.tagsOrEmpty(), v.inheritedTagsOrEmpty(), conflicts, true);
    }

    private RecipeDetail fromCatalogue(RecipeClient.Recipe r) {
        Set<Long> ids = r.ingredientsOrEmpty().stream().map(RecipeClient.Line::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        r.ingredientsOrEmpty().forEach(l -> l.replacementsOrEmpty()
                .forEach(rep -> ids.add(rep.ingredientId())));
        Map<Long, IngredientClient.Ingredient> resolved = ingredients.byIds(ids);

        Set<String> inherited = new LinkedHashSet<>();
        List<RecipeDetail.Line> lines = new ArrayList<>();
        for (RecipeClient.Line l : r.ingredientsOrEmpty()) {
            IngredientClient.Ingredient entry = resolved.get(l.ingredientId());
            List<String> tags = entry == null ? List.of()
                    : entry.tagsOrEmpty().stream().map(IngredientClient.Tag::name).toList();
            inherited.addAll(tags);
            List<RecipeDetail.Sub> subs = l.replacementsOrEmpty().stream()
                    .map(rep -> {
                        IngredientClient.Ingredient s = resolved.get(rep.ingredientId());
                        return new RecipeDetail.Sub(rep.ingredientId(),
                                s != null ? s.name() : "unknown ingredient (#" + rep.ingredientId() + ")",
                                s != null, rep.recipeId());
                    }).toList();
            lines.add(new RecipeDetail.Line(
                    l.ingredientId(),
                    entry != null ? entry.name() : "unknown ingredient (#" + l.ingredientId() + ")",
                    entry != null, l.quantity(), l.amount(), l.unit(),
                    l.optional(), l.replaceable(), tags, subs, null, null));
        }
        List<RecipeDetail.Step> steps = r.stepsOrEmpty().stream()
                .map(s -> new RecipeDetail.Step(s.position(), s.text(), s.toolsOrEmpty()))
                .toList();
        List<String> own = r.tagsOrEmpty().stream().map(RecipeClient.Tag::name).toList();
        return new RecipeDetail(r.id(), r.name(), r.creator(), r.version(), steps, lines,
                own, List.copyOf(inherited), null, false);
    }

    private static List<Long> parseIds(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split("[,\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
    }
}
