package com.example.ui.web;

import java.util.List;
import java.util.Set;

/**
 * The normalised recipe-detail view the {@code recipes/detail} template renders,
 * built either from the BFF's personalised composition (logged in) or a
 * lightweight UI-side stitch of RecipeCatalogue + IngredientCatalogue (anonymous).
 */
public record RecipeDetail(
        Long id, String name, String creator, Integer version,
        List<Step> steps, List<Line> lines,
        List<String> tags, List<String> inheritedTags,
        List<Conflict> conflicts,   // null when not personalised
        boolean personalised) {

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }

    public record Step(Integer position, String text, Set<String> tools) {
    }

    public record Line(
            Long ingredientId, String name, boolean resolved,
            String quantity, Double amount, String unit,
            boolean optional, boolean replaceable,
            List<String> tags, List<Sub> replacements,
            Long preferredReplacementId, String preferredReplacementName) {

        public boolean conflicting(List<Conflict> conflicts) {
            return conflicts != null && conflicts.stream()
                    .anyMatch(c -> c.ingredientIds() != null && c.ingredientIds().contains(ingredientId));
        }
        public String amountDisplay() {
            if (amount != null && unit != null) {
                String a = amount == Math.floor(amount) ? String.valueOf(amount.longValue()) : String.valueOf(amount);
                return a + " " + unit;
            }
            return quantity == null ? "" : quantity;
        }
    }

    public record Sub(Long ingredientId, String name, boolean resolved, Long recipeId) {
    }

    public record Conflict(String restriction, List<Long> ingredientIds, List<String> ingredientNames) {
    }
}
