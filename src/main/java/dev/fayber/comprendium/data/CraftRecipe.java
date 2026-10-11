package dev.fayber.comprendium.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small, platform-neutral view of a data-driven shaped crafting recipe. */
public record CraftRecipe(String outputItem, List<String> grid, String recipeId) {
    public CraftRecipe {
        if (grid.size() != 9) throw new IllegalArgumentException("A crafting grid has nine slots");
        grid = List.copyOf(grid);
    }

    /**
     * Keep ingredient tags unresolved until presentation. Arbitrary tag
     * members are interchangeable, so their chosen preview item is not the
     * only valid ingredient.
     */
    public static Optional<CraftRecipe> parse(String recipeId, JsonObject json) {
        try {
            if (!json.has("type") ||
                    !json.get("type").getAsString().equals("minecraft:crafting_shaped")) return Optional.empty();
            if (!json.has("pattern") || !json.has("key") || !json.has("result")) return Optional.empty();
            JsonArray pattern = json.getAsJsonArray("pattern");
            JsonObject key = json.getAsJsonObject("key");
            if (pattern.isEmpty() || pattern.size() > 3) return Optional.empty();
            String output = ingredientId(json.get("result"));
            if (output == null || output.startsWith("#")) return Optional.empty();
            List<String> grid = new ArrayList<>();
            for (int y = 0; y < 3; y++) {
                String row = y < pattern.size() ? pattern.get(y).getAsString() : "";
                if (row.length() > 3) return Optional.empty();
                for (int x = 0; x < 3; x++) {
                    char symbol = x < row.length() ? row.charAt(x) : ' ';
                    if (symbol == ' ') {
                        grid.add("");
                        continue;
                    }
                    JsonElement choice = key.get(Character.toString(symbol));
                    String id = ingredientId(choice);
                    if (id == null) return Optional.empty();
                    grid.add(id);
                }
            }
            return Optional.of(new CraftRecipe(output, grid, recipeId));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static String ingredientId(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                String id = ingredientId(entry);
                if (id != null) return id;
            }
            return null;
        }
        if (value.isJsonPrimitive()) return normalize(value.getAsString());
        if (!value.isJsonObject()) return null;
        JsonObject obj = value.getAsJsonObject();
        if (obj.has("id")) return ingredientId(obj.get("id"));
        if (obj.has("item")) return ingredientId(obj.get("item"));
        if (obj.has("tag")) {
            String tag = ingredientId(obj.get("tag"));
            return tag == null ? null : "#" + tag;
        }
        return null;
    }

    public static String normalize(String id) {
        if (id == null || id.isBlank()) return null;
        if (id.startsWith("#")) {
            String name = normalize(id.substring(1));
            return name == null ? null : "#" + name;
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }
}
