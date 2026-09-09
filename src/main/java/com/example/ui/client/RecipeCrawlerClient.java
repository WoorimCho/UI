package com.example.ui.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** recipe-crawler: turn a recipe URL into a draft, or import it outright. */
@Component
public class RecipeCrawlerClient {

    private final RestClient http;

    public RecipeCrawlerClient(RestClient recipeCrawlerRestClient) {
        this.http = recipeCrawlerRestClient;
    }

    public Draft preview(String url) {
        return http.post().uri("/api/import/preview").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", url))
                .retrieve().body(Draft.class);
    }

    public Imported commit(String url, String creator) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("url", url);
        if (creator != null && !creator.isBlank()) {
            body.put("creator", creator);
        }
        return http.post().uri("/api/import/commit").contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Imported.class);
    }

    // ── shapes (mirror recipe-crawler's DTOs) ────────────────────────────────

    public record Draft(String sourceUrl, String name, String yield,
                        List<DraftIngredient> ingredients, List<String> steps) {
        public List<DraftIngredient> ingredientsOrEmpty() {
            return ingredients == null ? List.of() : ingredients;
        }
        public List<String> stepsOrEmpty() {
            return steps == null ? List.of() : steps;
        }
    }

    public record DraftIngredient(String raw, Double amount, String unit, String name,
                                  String note, Long matchedId, boolean willCreate) {
    }

    public record Imported(long recipeId, String name, int ingredientCount,
                           int stepCount, int ingredientsCreated, String sourceUrl) {
    }
}
