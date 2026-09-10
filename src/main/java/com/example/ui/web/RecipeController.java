package com.example.ui.web;

import com.example.ui.client.BffClient;
import com.example.ui.client.IngredientClient;
import com.example.ui.client.RecipeClient;
import com.example.ui.client.UserClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
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
                       @RequestParam(required = false) String notTag,
                       @RequestParam(required = false) String ingredient,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        List<String> ingredientNames = IngredientController.splitCsv(ingredient);
        List<Long> ids = ingredientNames.isEmpty()
                ? List.of()
                : List.copyOf(ingredients.resolveIds(ingredientNames).values());
        if (!ingredientNames.isEmpty() && ids.isEmpty()) {
            model.addAttribute("notice", "No ingredient matches \"" + ingredient + "\".");
        }
        model.addAttribute("page", recipes.list(name, IngredientController.splitCsv(tag), match,
                IngredientController.splitCsv(notTag), ids, page, 20));
        model.addAttribute("name", name);
        model.addAttribute("tag", tag);
        model.addAttribute("match", match);
        model.addAttribute("notTag", notTag);
        model.addAttribute("ingredient", ingredient);
        model.addAttribute("pageNo", page);
        return "recipes/list";
    }

    @GetMapping("/recipes/random")
    public String random(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String tag,
                         @RequestParam(required = false, defaultValue = "all") String match,
                         @RequestParam(required = false) String notTag) {
        var r = recipes.random(name, IngredientController.splitCsv(tag), match,
                IngredientController.splitCsv(notTag), null);
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
        model.addAttribute("recipe", editModel(recipes.get(id)));
        return "recipes/form";
    }

    @PostMapping("/recipes")
    public String create(@RequestParam MultiValueMap<String, String> params, Model model) {
        RecipeForm form = RecipeForm.from(params);
        Map<String, Long> resolved = resolveOrReport(form, model);
        if (resolved == null) {
            model.addAttribute("mode", "new");
            model.addAttribute("pageTitle", "New recipe");
            model.addAttribute("recipe", form.toModel());
            return "recipes/form";
        }
        var created = recipes.create(form.toBody(resolved));
        return "redirect:/recipes/" + created.id();
    }

    @PostMapping("/recipes/{id}")
    public String update(@PathVariable long id, @RequestParam MultiValueMap<String, String> params, Model model) {
        RecipeForm form = RecipeForm.from(params);
        Map<String, Long> resolved = resolveOrReport(form, model);
        if (resolved == null) {
            model.addAttribute("mode", "edit");
            model.addAttribute("pageTitle", "Edit recipe");
            RecipeForm.View view = form.toModel();
            model.addAttribute("recipe", new RecipeForm.View(id, view.name(), view.creator(),
                    view.version(), view.tagsCsv(), view.steps(), view.lines()));
            return "recipes/form";
        }
        recipes.update(id, form.toBody(resolved));
        return "redirect:/recipes/" + id;
    }

    /**
     * Resolve every ingredient name the form mentions to a catalogue id. Returns
     * the {name: id} map, or {@code null} after adding an {@code error} to the
     * model when one or more names don't match anything.
     */
    private Map<String, Long> resolveOrReport(RecipeForm form, Model model) {
        List<String> names = form.allIngredientNames();
        Map<String, Long> resolved = names.isEmpty() ? Map.of() : ingredients.resolveIds(names);
        List<String> missing = names.stream().filter(n -> !resolved.containsKey(n)).toList();
        if (!missing.isEmpty()) {
            model.addAttribute("error", "No ingredient found named: " + String.join(", ", missing)
                    + ". Add it on the Ingredients page first, then come back.");
            return null;
        }
        return resolved;
    }

    /** Edit-form backing: the catalogue recipe with every ingredient id resolved back to a name. */
    private RecipeForm.View editModel(RecipeClient.Recipe r) {
        Set<Long> ids = r.ingredientsOrEmpty().stream()
                .map(RecipeClient.Line::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        r.ingredientsOrEmpty().forEach(l -> l.replacementsOrEmpty()
                .forEach(rep -> ids.add(rep.ingredientId())));
        Map<Long, IngredientClient.Ingredient> byId = ingredients.byIds(ids);

        List<RecipeForm.View.Step> steps = r.stepsOrEmpty().stream()
                .map(s -> new RecipeForm.View.Step(s.text(), String.join(", ", s.toolsOrEmpty())))
                .toList();
        List<RecipeForm.View.Line> lines = new ArrayList<>();
        for (RecipeClient.Line l : r.ingredientsOrEmpty()) {
            String repNames = l.replacementsOrEmpty().stream()
                    .map(rep -> nameOrId(byId, rep.ingredientId()))
                    .collect(Collectors.joining(", "));
            lines.add(new RecipeForm.View.Line(
                    nameOrId(byId, l.ingredientId()),
                    plainNumber(l.amount()), l.unit() == null ? "" : l.unit(),
                    l.quantity() == null ? "" : l.quantity(),
                    l.optional(), l.replaceable(), repNames));
        }
        List<String> tagNames = r.tagsOrEmpty().stream().map(RecipeClient.Tag::name).toList();
        return new RecipeForm.View(r.id(), r.name(), r.creator(), r.version(),
                String.join(", ", tagNames), steps, lines);
    }

    private static String nameOrId(Map<Long, IngredientClient.Ingredient> byId, Long id) {
        IngredientClient.Ingredient i = byId.get(id);
        return i != null ? i.name() : "#" + id;
    }

    /** "200" not "200.0"; blank for null. */
    private static String plainNumber(Double d) {
        if (d == null) {
            return "";
        }
        return d == Math.rint(d) && !d.isInfinite() ? Long.toString(d.longValue()) : d.toString();
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

}
