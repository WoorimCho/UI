package com.example.ui.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** IngredientCatalogue: ingredients (+ tags, + nutrition), tags, and the nutrition reference. */
@Component
public class IngredientClient {

    private static final ParameterizedTypeReference<PageResult<Ingredient>> INGREDIENT_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PageResult<Tag>> TAG_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<NutritionRef>> NUTRITION_REF_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    public IngredientClient(RestClient ingredientCatalogueRestClient) {
        this.http = ingredientCatalogueRestClient;
    }

    // ── ingredients ──────────────────────────────────────────────────────────

    public PageResult<Ingredient> list(String name, List<String> tags, String match, int page, int size) {
        return http.get().uri(uri -> {
            uri.path("/api/ingredients").queryParam("page", page).queryParam("size", size);
            if (StringUtils.hasText(name)) {
                uri.queryParam("name", name.trim());
            }
            if (tags != null && !tags.isEmpty()) {
                uri.queryParam("tag", tags.toArray());
                uri.queryParam("match", StringUtils.hasText(match) ? match : "all");
            }
            return uri.build();
        }).retrieve().body(INGREDIENT_PAGE);
    }

    /** First ingredient whose name matches {@code name} exactly (case-insensitive), or null. */
    public Ingredient findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        PageResult<Ingredient> hits = list(name.trim(), null, null, 0, 25);
        return hits.contentOrEmpty().stream()
                .filter(i -> i.name() != null && i.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    /** {@code name} -> id (exact, case-insensitive), or null when there's no such ingredient. */
    public Long resolveId(String name) {
        Ingredient found = findByName(name);
        return found == null ? null : found.id();
    }

    /**
     * Resolve many names in one go -> {trimmed input name: id}. Names that don't
     * match anything are simply absent from the map, so the caller can diff.
     */
    public Map<String, Long> resolveIds(java.util.Collection<String> names) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (String raw : names) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String key = raw.trim();
            if (out.containsKey(key)) {
                continue;
            }
            Long id = resolveId(key);
            if (id != null) {
                out.put(key, id);
            }
        }
        return out;
    }

    public Ingredient get(long id) {
        return http.get().uri("/api/ingredients/{id}", id).retrieve().body(Ingredient.class);
    }

    /** Batch resolve ids -> {id: ingredient}. Unknown ids are simply absent. */
    public java.util.Map<Long, Ingredient> byIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        List<Ingredient> found = http.get()
                .uri(uri -> uri.path("/api/ingredients/by-ids").queryParam("id", ids.toArray()).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Ingredient>>() {
                });
        java.util.Map<Long, Ingredient> byId = new java.util.LinkedHashMap<>();
        for (Ingredient i : (found == null ? List.<Ingredient>of() : found)) {
            byId.put(i.id(), i);
        }
        return byId;
    }

    public Ingredient create(String name, List<String> tags, Double densityGPerMl) {
        return http.post().uri("/api/ingredients").contentType(MediaType.APPLICATION_JSON)
                .body(ingredientBody(name, tags, densityGPerMl))
                .retrieve().body(Ingredient.class);
    }

    public Ingredient update(long id, String name, List<String> tags, Double densityGPerMl) {
        return http.put().uri("/api/ingredients/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(ingredientBody(name, tags, densityGPerMl))
                .retrieve().body(Ingredient.class);
    }

    private static Map<String, Object> ingredientBody(String name, List<String> tags, Double densityGPerMl) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        body.put("tags", tags == null ? List.of() : tags);
        if (densityGPerMl != null) {          // omit -> service leaves it untouched
            body.put("densityGPerMl", densityGPerMl);
        }
        return body;
    }

    public void delete(long id) {
        http.delete().uri("/api/ingredients/{id}", id).retrieve().toBodilessEntity();
    }

    public Ingredient setNutrition(long id, Map<String, Object> nutrition) {
        RestClient.RequestBodySpec req = http.put().uri("/api/ingredients/{id}/nutrition", id)
                .contentType(MediaType.APPLICATION_JSON);
        return (nutrition == null || nutrition.isEmpty() ? req : req.body(nutrition))
                .retrieve().body(Ingredient.class);
    }

    // ── tags ─────────────────────────────────────────────────────────────────

    /** {@code nameContains} matches anywhere in the tag name (the API param is still called {@code prefix}). */
    public PageResult<Tag> tags(String nameContains, String namespace, int page, int size) {
        return http.get().uri(uri -> {
            uri.path("/api/tags").queryParam("page", page).queryParam("size", size);
            if (StringUtils.hasText(nameContains)) {
                uri.queryParam("prefix", nameContains);
            }
            if (StringUtils.hasText(namespace)) {
                uri.queryParam("namespace", namespace);
            }
            return uri.build();
        }).retrieve().body(TAG_PAGE);
    }

    public Tag createTag(String name, String namespace, String description) {
        return http.post().uri("/api/tags").contentType(MediaType.APPLICATION_JSON)
                .body(nonNull("name", name, "namespace", namespace, "description", description))
                .retrieve().body(Tag.class);
    }

    /** Tag names containing {@code fragment} anywhere (for search / autocomplete). */
    public List<String> suggestTagNames(String fragment) {
        return tags(fragment, null, 0, 20).contentOrEmpty().stream().map(Tag::name).toList();
    }

    /** The tag whose name equals {@code name} exactly (case-insensitive), or null. */
    public Tag findTagByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return tags(name.trim(), null, 0, 25).contentOrEmpty().stream()
                .filter(t -> t.name() != null && t.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    public void deleteTag(long id) {
        http.delete().uri("/api/tags/{id}", id).retrieve().toBodilessEntity();
    }

    public Tag mergeTags(List<Long> from, long into) {
        return http.post().uri("/api/tags/merge").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "into", into))
                .retrieve().body(Tag.class);
    }

    // ── nutrition reference ──────────────────────────────────────────────────

    /** POST a CSV file to /api/ingredients/import. Always 200 — per-row problems are in the result. */
    public ImportResult importCsv(byte[] bytes, String filename) {
        var body = new org.springframework.http.client.MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null ? "upload.csv" : filename;
            }
        }).contentType(MediaType.parseMediaType("text/csv"));
        return http.post().uri("/api/ingredients/import")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().body(ImportResult.class);
    }

    public List<NutritionRef> nutritionReference(String name) {
        return http.get().uri(uri -> {
            uri.path("/api/nutrition-reference");
            if (StringUtils.hasText(name)) {
                uri.queryParam("name", name);
            }
            return uri.build();
        }).retrieve().body(NUTRITION_REF_LIST);
    }

    /** POST a new row to the nutrition reference. Throws {@code HttpClientErrorException.Conflict} on a duplicate name. */
    public NutritionRef addNutritionReference(Map<String, Object> body) {
        return http.post().uri("/api/nutrition-reference").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(NutritionRef.class);
    }

    /** PUT — replace a reference row's figures. 404 (NotFound) if gone, 409 (Conflict) if the new name collides. */
    public NutritionRef updateNutritionReference(long id, Map<String, Object> body) {
        return http.put().uri("/api/nutrition-reference/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(NutritionRef.class);
    }

    private static Map<String, Object> nonNull(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null && !"".equals(kv[i + 1])) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    // ── shapes ───────────────────────────────────────────────────────────────

    public record Ingredient(Long id, String name, List<Tag> tags, Nutrition nutrition, Double densityGPerMl) {
        public List<Tag> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }
        public boolean hasNutrition() {
            return nutrition != null;
        }
        public boolean hasDensity() {
            return densityGPerMl != null && densityGPerMl > 0;
        }
    }

    public record Tag(Long id, String name, String namespace, String description) {
    }

    public record Nutrition(
            Double basisGrams, Double kcal, Double proteinG, Double carbsG,
            Double fatG, Double fiberG, Double sugarG, Double sodiumMg) {
    }

    public record NutritionRef(
            Long id, String name, Double basisGrams, Double kcal, Double proteinG,
            Double carbsG, Double fatG, Double fiberG, Double sugarG, Double sodiumMg) {
    }
}
