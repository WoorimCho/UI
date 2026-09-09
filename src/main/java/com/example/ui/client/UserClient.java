package com.example.ui.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** User service: accounts, credentials, dietary restrictions, favourites, and the restriction catalogue. */
@Component
public class UserClient {

    private static final ParameterizedTypeReference<List<String>> STRING_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Long>> LONG_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<FavoriteAlternative>> ALT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<Restriction>> RESTRICTION_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;

    public UserClient(RestClient userRestClient) {
        this.http = userRestClient;
    }

    /** Verify credentials. Throws 401 on bad ones. */
    public Identity authenticate(String identifier, String password) {
        return http.post().uri("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", identifier, "password", password))
                .retrieve().body(Identity.class);
    }

    public Account register(String username, String email, String displayName, String password) {
        return http.post().uri("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", email,
                        "displayName", displayName == null ? "" : displayName, "password", password))
                .retrieve().body(Account.class);
    }

    public Account account(long id) {
        return http.get().uri("/api/accounts/{id}", id).retrieve().body(Account.class);
    }

    public Account updateProfile(long id, String email, String displayName) {
        return http.put().uri("/api/accounts/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "displayName", displayName == null ? "" : displayName))
                .retrieve().body(Account.class);
    }

    public void changePassword(long id, String currentPassword, String newPassword) {
        http.put().uri("/api/accounts/{id}/password", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", currentPassword, "newPassword", newPassword))
                .retrieve().toBodilessEntity();
    }

    public List<String> restrictions(long id) {
        return http.get().uri("/api/accounts/{id}/restrictions", id).retrieve().body(STRING_LIST);
    }

    public Account replaceRestrictions(long id, List<String> codes) {
        return http.put().uri("/api/accounts/{id}/restrictions", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("restrictions", codes == null ? List.of() : codes))
                .retrieve().body(Account.class);
    }

    public List<Long> favoriteRecipeIds(long id) {
        return http.get().uri("/api/accounts/{id}/favorites/recipes", id).retrieve().body(LONG_LIST);
    }

    public void addFavoriteRecipe(long id, long recipeId) {
        http.post().uri("/api/accounts/{id}/favorites/recipes/{r}", id, recipeId).retrieve().toBodilessEntity();
    }

    public void removeFavoriteRecipe(long id, long recipeId) {
        http.delete().uri("/api/accounts/{id}/favorites/recipes/{r}", id, recipeId).retrieve().toBodilessEntity();
    }

    public List<FavoriteAlternative> favoriteAlternatives(long id) {
        return http.get().uri("/api/accounts/{id}/favorites/alternatives", id).retrieve().body(ALT_LIST);
    }

    public List<FavoriteAlternative> replaceFavoriteAlternatives(long id, List<Map<String, Object>> alternatives) {
        return http.put().uri("/api/accounts/{id}/favorites/alternatives", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("alternatives", alternatives == null ? List.of() : alternatives))
                .retrieve().body(ALT_LIST);
    }

    public List<Restriction> restrictionCatalogue(String kind) {
        return http.get().uri(uri -> {
            uri.path("/api/restrictions");
            if (StringUtils.hasText(kind)) {
                uri.queryParam("kind", kind);
            }
            return uri.build();
        }).retrieve().body(RESTRICTION_LIST);
    }

    // ── shapes ───────────────────────────────────────────────────────────────

    public record Identity(Long accountId, String username, String displayName) {
    }

    public record Account(
            Long id, String username, String email, String displayName,
            List<String> restrictions, String createdAt, String updatedAt) {
        public List<String> restrictionsOrEmpty() {
            return restrictions == null ? List.of() : restrictions;
        }
    }

    public record FavoriteAlternative(
            Long id, Long recipeId, Long ingredientId, Long replacementIngredientId) {
    }

    public record Restriction(Long id, String code, String label, String kind, String description) {
    }
}
