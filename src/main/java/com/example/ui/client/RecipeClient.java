package com.example.ui.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** RecipeCatalogue: recipes (the full model — steps + tools, structured ingredients, tags) and recipe tags. */
@Component
public class RecipeClient {

    private static final ParameterizedTypeReference<PageResult<Recipe>> RECIPE_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    public RecipeClient(RestClient recipeCatalogueRestClient) {
        this.http = recipeCatalogueRestClient;
    }

    public PageResult<Recipe> list(String name, List<String> tags, String match,
                                   List<Long> ingredientIds, int page, int size) {
        return http.get().uri(uri -> {
            uri.path("/api/recipes").queryParam("page", page).queryParam("size", size);
            if (StringUtils.hasText(name)) {
                uri.queryParam("name", name);
            }
            if (tags != null && !tags.isEmpty()) {
                uri.queryParam("tag", tags.toArray());
                uri.queryParam("match", StringUtils.hasText(match) ? match : "all");
            }
            if (ingredientIds != null && !ingredientIds.isEmpty()) {
                uri.queryParam("ingredientId", ingredientIds.toArray());
            }
            return uri.build();
        }).retrieve().body(RECIPE_PAGE);
    }

    /** GET /api/recipes/random — one random recipe from the filtered set. 404 if nothing matches. */
    public Recipe random(String name, List<String> tags, String match, List<Long> ingredientIds) {
        return http.get().uri(uri -> {
            uri.path("/api/recipes/random");
            if (StringUtils.hasText(name)) {
                uri.queryParam("name", name);
            }
            if (tags != null && !tags.isEmpty()) {
                uri.queryParam("tag", tags.toArray());
                uri.queryParam("match", StringUtils.hasText(match) ? match : "all");
            }
            if (ingredientIds != null && !ingredientIds.isEmpty()) {
                uri.queryParam("ingredientId", ingredientIds.toArray());
            }
            return uri.build();
        }).retrieve().body(Recipe.class);
    }

    public Recipe get(long id) {
        return http.get().uri("/api/recipes/{id}", id).retrieve().body(Recipe.class);
    }

    /** Recipe names containing {@code q} (for autocomplete). */
    public List<String> suggestNames(String q) {
        return list(q, null, null, null, 0, 20).contentOrEmpty().stream()
                .map(Recipe::name).distinct().toList();
    }

    /** First recipe whose name matches {@code name} exactly (case-insensitive), or null. */
    public Recipe findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return list(name.trim(), null, null, null, 0, 25).contentOrEmpty().stream()
                .filter(r -> r.name() != null && r.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    public Recipe create(Map<String, Object> body) {
        return http.post().uri("/api/recipes").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Recipe.class);
    }

    public Recipe update(long id, Map<String, Object> body) {
        return http.put().uri("/api/recipes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Recipe.class);
    }

    public void delete(long id) {
        http.delete().uri("/api/recipes/{id}", id).retrieve().toBodilessEntity();
    }

    /** POST a CSV file to /api/recipes/import (one row per recipe skeleton; steps are added later). */
    public ImportResult importCsv(byte[] bytes, String filename) {
        var body = new org.springframework.http.client.MultipartBodyBuilder();
        body.part("file", new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null ? "upload.csv" : filename;
            }
        }).contentType(MediaType.parseMediaType("text/csv"));
        return http.post().uri("/api/recipes/import")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().body(ImportResult.class);
    }

    // ── shapes ───────────────────────────────────────────────────────────────

    public record Recipe(
            Long id, String name, String creator, Integer version,
            List<Step> steps, List<Line> ingredients, List<Tag> tags) {
        public List<Step> stepsOrEmpty() {
            return steps == null ? List.of() : steps;
        }
        public List<Line> ingredientsOrEmpty() {
            return ingredients == null ? List.of() : ingredients;
        }
        public List<Tag> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }
    }

    public record Step(Integer position, String text, Set<String> tools) {
        public Set<String> toolsOrEmpty() {
            return tools == null ? Set.of() : tools;
        }
    }

    public record Line(
            Long id, Long ingredientId, String quantity, Double amount, String unit,
            boolean optional, boolean replaceable, List<Replacement> replacements) {
        public List<Replacement> replacementsOrEmpty() {
            return replacements == null ? List.of() : replacements;
        }
    }

    public record Replacement(Long ingredientId, Long recipeId) {
    }

    public record Tag(Long id, String name, String namespace, String description) {
    }
}
