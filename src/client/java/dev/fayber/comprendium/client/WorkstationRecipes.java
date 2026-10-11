package dev.fayber.comprendium.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fayber.comprendium.Comprendium;
import dev.fayber.comprendium.data.CraftRecipe;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Client-side crafting recipe catalogue built from real, installed game/mod
 * data. Dedicated-server-only datapacks cannot be inferred without a separate
 * server recipe sync protocol, so unavailable recipes are not fabricated.
 */
public final class WorkstationRecipes {
    private final Map<String, CraftRecipe> byOutput;
    private final Map<String, List<String>> tags;

    private WorkstationRecipes(Map<String, CraftRecipe> recipes, Map<String, List<String>> tags) {
        this.byOutput = Map.copyOf(recipes);
        this.tags = Map.copyOf(tags);
    }

    public CraftRecipe find(String outputItem) {
        return byOutput.get(outputItem);
    }

    public String representative(String ingredient) {
        if (ingredient == null || ingredient.isBlank()) return "";
        if (!ingredient.startsWith("#")) return ingredient;
        return member(ingredient.substring(1), new java.util.HashSet<>(), 0);
    }

    private String member(String tag, java.util.Set<String> seen, int depth) {
        if (depth > 16 || !seen.add(tag)) return "";
        for (String candidate : tags.getOrDefault(tag, List.of())) {
            String id = candidate.startsWith("#")
                    ? member(candidate.substring(1), seen, depth + 1)
                    : candidate;
            if (!id.isEmpty()) {
                try {
                    var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
                    if (item != null && item != Items.AIR) return id;
                } catch (RuntimeException ignored) { }
            }
        }
        return "";
    }

    public static WorkstationRecipes load() {
        Map<String, CraftRecipe> recipes = new HashMap<>();
        Map<String, List<String>> tags = new HashMap<>();
        int scanned = 0;
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) continue;
                try (Stream<Path> files = Files.walk(data)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".json")).toList()) {
                        String path = root.relativize(file).toString().replace('\\', '/');
                        String[] parts = path.split("/");
                        if (parts.length < 4 || !parts[0].equals("data")) continue;
                        boolean recipe = parts[2].equals("recipe");
                        boolean itemTag = parts.length >= 5 && parts[2].equals("tags")
                                && parts[3].equals("item");
                        if (!recipe && !itemTag) continue;
                        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            JsonElement doc = JsonParser.parseReader(reader);
                            if (!doc.isJsonObject()) continue;
                            JsonObject json = doc.getAsJsonObject();
                            if (recipe) {
                                String name = path.substring(("data/" + parts[1] + "/recipe/").length(),
                                        path.length() - 5);
                                CraftRecipe.parse(parts[1] + ":" + name, json)
                                        .ifPresent(r -> recipes.put(r.outputItem(), r));
                            } else {
                                String name = path.substring(("data/" + parts[1] + "/tags/item/").length(),
                                        path.length() - 5);
                                String key = parts[1] + ":" + name;
                                if (json.has("values") && json.get("values").isJsonArray()) {
                                    List<String> values = tags.computeIfAbsent(key, ignored -> new ArrayList<>());
                                    if (json.has("replace") && json.get("replace").getAsBoolean()) values.clear();
                                    for (var value : json.getAsJsonArray("values")) {
                                        String entry = CraftRecipe.ingredientId(value);
                                        if (entry != null) values.add(entry);
                                    }
                                }
                            }
                            scanned++;
                        } catch (RuntimeException | java.io.IOException ignored) {
                            // A malformed recipe must never prevent the chart from opening.
                        }
                    }
                } catch (java.io.IOException | RuntimeException ex) {
                    Comprendium.LOG.debug("Couldn't scan crafting recipes in {}", root, ex);
                }
            }
        }
        Comprendium.LOG.info("Indexed {} crafting recipe/tag documents ({} craftable outputs)",
                scanned, recipes.size());
        return new WorkstationRecipes(recipes, tags);
    }
}
