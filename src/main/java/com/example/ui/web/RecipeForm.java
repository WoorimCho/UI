package com.example.ui.web;

import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Form backing for recipe create / edit. Every repeating row submits every field
 * (blank where empty) so the parallel lists stay index-aligned; rows with no step
 * text / no ingredient id are dropped. {@link #toBody()} produces the JSON
 * RecipeCatalogue expects.
 *
 * <p>Built from the raw request parameter map rather than by {@code @ModelAttribute}
 * bean binding: Spring converts a single-valued parameter to a {@code List<String>}
 * by <em>splitting it on commas</em>, which would shred a step like
 * "mix, rest, bake" into three. Reading {@link MultiValueMap} values (which come
 * from {@code getParameterValues}) keeps each row's text whole. Genuine CSV fields
 * ({@code tags}, replacement ids) are split by hand in {@link #toBody()}.
 */
public class RecipeForm {

    private String name;
    private String creator;
    private Integer version;
    private String tags;

    private List<String> stepText = List.of();
    private List<String> stepTools = List.of();

    private List<String> lineIngredientId = List.of();
    private List<String> lineQuantity = List.of();
    private List<String> lineAmount = List.of();
    private List<String> lineUnit = List.of();
    private List<String> lineOptional = List.of();
    private List<String> lineReplaceable = List.of();
    private List<String> lineReplacements = List.of();

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
        f.lineIngredientId = p.getOrDefault("lineIngredientId", List.of());
        f.lineQuantity = p.getOrDefault("lineQuantity", List.of());
        f.lineAmount = p.getOrDefault("lineAmount", List.of());
        f.lineUnit = p.getOrDefault("lineUnit", List.of());
        f.lineOptional = p.getOrDefault("lineOptional", List.of());
        f.lineReplaceable = p.getOrDefault("lineReplaceable", List.of());
        f.lineReplacements = p.getOrDefault("lineReplacements", List.of());
        return f;
    }

    public Map<String, Object> toBody() {
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
        for (int i = 0; i < lineIngredientId.size(); i++) {
            String id = lineIngredientId.get(i);
            if (!StringUtils.hasText(id)) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("ingredientId", Long.parseLong(id.trim()));
            if (StringUtils.hasText(at(lineQuantity, i))) {
                line.put("quantity", at(lineQuantity, i).trim());
            }
            if (StringUtils.hasText(at(lineAmount, i))) {
                line.put("amount", Double.parseDouble(at(lineAmount, i).trim()));
                line.put("unit", at(lineUnit, i).trim());
            }
            line.put("optional", "true".equalsIgnoreCase(at(lineOptional, i)));
            boolean replaceable = "true".equalsIgnoreCase(at(lineReplaceable, i));
            line.put("replaceable", replaceable);
            List<Map<String, Object>> reps = new ArrayList<>();
            for (Long rid : idList(at(lineReplacements, i))) {
                reps.add(Map.of("ingredientId", rid));
            }
            line.put("replacements", reps);
            lines.add(line);
        }
        body.put("ingredients", lines);
        body.put("tags", csv(tags));
        return body;
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

    private static List<Long> idList(String s) {
        return csv(s).stream().map(Long::parseLong).toList();
    }
}
