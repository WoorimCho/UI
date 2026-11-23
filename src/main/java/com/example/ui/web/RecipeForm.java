package com.example.ui.web;

import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Form backing for recipe create / edit. Every repeating row submits every field
 * (blank where empty) so the parallel lists stay index-aligned; rows with no step
 * text / no ingredient name are dropped.
 *
 * <p>Ingredient lines and their replacements are typed as <em>names</em>. The
 * controller resolves them to catalogue ids ({@link #allIngredientNames()} +
 * {@code IngredientClient.resolveIds}) before calling {@link #toBody(Map)}; if a
 * name doesn't resolve it re-renders the form from {@link #toModel()} with an
 * error, so nothing the user typed is lost.
 *
 * <p>Built from the raw request parameter map rather than by {@code @ModelAttribute}
 * bean binding: Spring converts a single-valued parameter to a {@code List<String>}
 * by <em>splitting it on commas</em>, which would shred a step like
 * "mix, rest, bake" into three. Reading {@link MultiValueMap} values (which come
 * from {@code getParameterValues}) keeps each row's text whole. Genuine CSV fields
 * ({@code tags}, replacement names) are split by hand.
 */
public class RecipeForm {

    private String name;
    private String creator;
    private Integer version;
    private String tags;

    private List<String> stepText = List.of();
    private List<String> stepTools = List.of();

    private List<String> lineIngredientName = List.of();
    private List<String> lineQuantity = List.of();
    private List<String> lineAmount = List.of();
    private List<String> lineUnit = List.of();
    private List<String> lineOptional = List.of();
    private List<String> lineReplaceable = List.of();
    private List<String> lineReplacementNames = List.of();

    /** Assemble from the flat request parameter map (see class Javadoc for why). */
    public static RecipeForm from(MultiValueMap<String, String> p) {
        RecipeForm f = new RecipeForm();
        f.name = p.getFirst("name");
        f.creator = p.getFirst("creator");
        String v = p.getFirst("version");
        f.version = StringUtils.hasText(v) ? Integer.valueOf(v.trim()) : null;
        f.tags = p.getFirst("tags");
        f.stepText = p.getOrDefault("stepText", List.of());
        f.stepTools = p.getOrDefault("stepTools", List.of());
        f.lineIngredientName = p.getOrDefault("lineIngredientName", List.of());
        f.lineQuantity = p.getOrDefault("lineQuantity", List.of());
        f.lineAmount = p.getOrDefault("lineAmount", List.of());
        f.lineUnit = p.getOrDefault("lineUnit", List.of());
        f.lineOptional = p.getOrDefault("lineOptional", List.of());
        f.lineReplaceable = p.getOrDefault("lineReplaceable", List.of());
        f.lineReplacementNames = p.getOrDefault("lineReplacementNames", List.of());
        return f;
    }

    /** True when at least one ingredient-line row carries a name (replacements alone don't count). */
    public boolean hasLineIngredient() {
        return lineIngredientName.stream().anyMatch(StringUtils::hasText);
    }

    // The UI mirrors RecipeCatalogue's limits (its Dto/Limits) so an oversized form is refused here,
    // before allIngredientNames() turns into one catalogue lookup per name. RecipeCatalogue stays
    // authoritative. SECURITY.md N4.
    static final int MAX_INGREDIENT_LINES = 100;
    static final int MAX_STEPS = 200;
    static final int MAX_REPLACEMENTS_PER_LINE = 10;
    static final int MAX_TAGS = 50;

    /** A user-facing message when the form is over a limit, otherwise {@code null}. It only counts — no I/O. */
    public String limitViolation() {
        if (count(lineIngredientName) > MAX_INGREDIENT_LINES) {
            return "A recipe can have at most " + MAX_INGREDIENT_LINES + " ingredient lines.";
        }
        if (count(stepText) > MAX_STEPS) {
            return "A recipe can have at most " + MAX_STEPS + " steps.";
        }
        if (lineReplacementNames.stream().anyMatch(r -> csv(r).size() > MAX_REPLACEMENTS_PER_LINE)) {
            return "An ingredient can have at most " + MAX_REPLACEMENTS_PER_LINE + " replacements.";
        }
        if (csv(tags).size() > MAX_TAGS) {
            return "A recipe can have at most " + MAX_TAGS + " tags.";
        }
        return null;
    }

    private static long count(List<String> values) {
        return values.stream().filter(StringUtils::hasText).count();
    }

    /** Every distinct ingredient name mentioned — line ingredients and replacements — for bulk resolution. */
    public List<String> allIngredientNames() {
        Set<String> out = new LinkedHashSet<>();
        for (String n : lineIngredientName) {
            if (StringUtils.hasText(n)) {
                out.add(n.trim());
            }
        }
        for (String csv : lineReplacementNames) {
            out.addAll(csv(csv));
        }
        return List.copyOf(out);
    }

    /** The JSON RecipeCatalogue expects, with names resolved to ids via {@code nameToId}. */
    public Map<String, Object> toBody(Map<String, Long> nameToId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name == null ? "" : name.trim());
        if (StringUtils.hasText(creator)) {
            body.put("creator", creator.trim());
        }
        if (version != null) {
            body.put("version", version);
        }

        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < stepText.size(); i++) {
            String text = stepText.get(i);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            steps.add(Map.of("text", text.trim(), "tools", csv(at(stepTools, i))));
        }
        body.put("steps", steps);

        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < lineIngredientName.size(); i++) {
            String nm = lineIngredientName.get(i);
            if (!StringUtils.hasText(nm)) {
                continue;
            }
            Long id = nameToId.get(nm.trim());
            if (id == null) {
                continue;   // the controller validates first; belt and braces
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("ingredientId", id);
            if (StringUtils.hasText(at(lineQuantity, i))) {
                line.put("quantity", at(lineQuantity, i).trim());
            }
            if (StringUtils.hasText(at(lineAmount, i))) {
                line.put("amount", Double.parseDouble(at(lineAmount, i).trim()));
                line.put("unit", at(lineUnit, i).trim());
            }
            line.put("optional", "true".equalsIgnoreCase(at(lineOptional, i)));
            line.put("replaceable", "true".equalsIgnoreCase(at(lineReplaceable, i)));
            List<Map<String, Object>> reps = new ArrayList<>();
            for (String rn : csv(at(lineReplacementNames, i))) {
                Long rid = nameToId.get(rn);
                if (rid != null) {
                    reps.add(Map.of("ingredientId", rid));
                }
            }
            line.put("replacements", reps);
            lines.add(line);
        }
        body.put("ingredients", lines);
        body.put("tags", csv(tags));
        return body;
    }

    /** Re-render backing after a failed submit — exactly what the user typed, nothing resolved. */
    public View toModel() {
        List<View.Step> steps = new ArrayList<>();
        int stepRows = Math.max(stepText.size(), stepTools.size());
        for (int i = 0; i < stepRows; i++) {
            if (!StringUtils.hasText(at(stepText, i)) && !StringUtils.hasText(at(stepTools, i))) {
                continue;
            }
            steps.add(new View.Step(at(stepText, i), at(stepTools, i)));
        }
        List<View.Line> lines = new ArrayList<>();
        for (int i = 0; i < lineIngredientName.size(); i++) {
            if (!StringUtils.hasText(at(lineIngredientName, i))
                    && !StringUtils.hasText(at(lineAmount, i))
                    && !StringUtils.hasText(at(lineQuantity, i))
                    && !StringUtils.hasText(at(lineReplacementNames, i))) {
                continue;
            }
            lines.add(new View.Line(
                    at(lineIngredientName, i), at(lineAmount, i), at(lineUnit, i), at(lineQuantity, i),
                    "true".equalsIgnoreCase(at(lineOptional, i)),
                    "true".equalsIgnoreCase(at(lineReplaceable, i)),
                    at(lineReplacementNames, i)));
        }
        return new View(null, name, creator, version, tags, steps, lines);
    }

    /**
     * What {@code recipes/form.html} renders in edit mode and after a failed
     * create — all strings, so a value the catalogue would reject still shows.
     */
    public record View(Long id, String name, String creator, Integer version, String tagsCsv,
                       List<Step> steps, List<Line> lines) {

        public record Step(String text, String toolsCsv) {
        }

        public record Line(String ingredientName, String amount, String unit, String quantity,
                           boolean optional, boolean replaceable, String replacementNamesCsv) {
        }
    }

    private static String at(List<String> list, int i) {
        return list != null && i < list.size() && list.get(i) != null ? list.get(i) : "";
    }

    private static List<String> csv(String s) {
        if (!StringUtils.hasText(s)) {
            return List.of();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
