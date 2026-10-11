package dev.fayber.comprendium.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fayber.comprendium.Comprendium;
import dev.fayber.comprendium.data.TradeAtlas;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Client-only fallback: reads the original 26.3 trade JSONs from the actual
 * Minecraft game JAR, never from an out-of-date handwritten table.
 * This cannot see server datapacks; authoritative server sync overrides it.
 */
public final class VanillaAtlas {
    private VanillaAtlas() {}

    public static TradeAtlas load() {
        try {
            var source = Minecraft.class.getProtectionDomain().getCodeSource();
            if (source == null) return TradeAtlas.empty("Bundled Minecraft data not accessible");
            Path path = Path.of(source.getLocation().toURI());
            if (!path.toString().endsWith(".jar")) return TradeAtlas.empty("No game JAR found");
            try (JarFile jar = new JarFile(path.toFile())) {
                return parse(jar);
            }
        } catch (Exception ex) {
            Comprendium.LOG.warn("Can't load bundled vanilla trade definitions", ex);
            return TradeAtlas.empty("Could not load bundled game definitions");
        }
    }

    private static TradeAtlas parse(JarFile jar) {
        final String tradePrefix = "data/minecraft/villager_trade/";
        final String tagPrefix = "data/minecraft/tags/villager_trade/";
        final String setPrefix = "data/minecraft/trade_set/";
        Map<String, JsonObject> trades = new HashMap<>();
        Map<String, JsonObject> tags = new HashMap<>();
        Map<String, JsonObject> sets = new HashMap<>();
        var names = jar.entries();
        while (names.hasMoreElements()) {
            var file = names.nextElement();
            String path = file.getName();
            if (!path.endsWith(".json")) continue;
            String folder = path.startsWith(tradePrefix) ? tradePrefix
                    : path.startsWith(tagPrefix) ? tagPrefix
                    : path.startsWith(setPrefix) ? setPrefix : null;
            if (folder == null) continue;
            try (var reader = new InputStreamReader(jar.getInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String id = "minecraft:" + path.substring(folder.length(), path.length() - 5);
                if (folder.equals(tradePrefix)) trades.put(id, json);
                else if (folder.equals(tagPrefix)) tags.put(id, json);
                else sets.put(id, json);
            } catch (RuntimeException | java.io.IOException ignored) {
                // Malformed entries do not invalidate the rest of the reference chart.
            }
        }

        Map<String, List<TradeAtlas.Level>> levels = new HashMap<>();
        for (var entry : sets.entrySet()) {
            String path = entry.getKey().substring("minecraft:".length());
            String[] parts = path.split("/");
            if (parts.length != 2) continue;
            int tier;
            if (parts[0].equals("wandering_trader")) {
                tier = switch (parts[1]) {
                    case "buying" -> 1;
                    case "common" -> 2;
                    case "uncommon" -> 3;
                    default -> -1;
                };
                if (tier < 0) continue;
            } else {
                if (!parts[1].startsWith("level_")) continue;
                try { tier = Integer.parseInt(parts[1].substring(6)); }
                catch (NumberFormatException ex) { continue; }
                if (tier < 1 || tier > 5) continue;
            }
            JsonObject set = entry.getValue();
            List<String> refs = new ArrayList<>();
            if (set.has("trades")) expand(set.get("trades"), tags, refs, new HashSet<>());
            List<TradeAtlas.Trade> possibilities = new ArrayList<>();
            for (String id : refs) {
                JsonObject trade = trades.get(id);
                if (trade == null) continue;
                possibilities.add(new TradeAtlas.Trade(id, ingredient(trade.get("wants")),
                        ingredient(trade.get("additional_wants")), ingredient(trade.get("gives")),
                        trade.has("merchant_predicate"),
                        trade.has("given_item_modifier") || trade.has("double_trade_price_enchantments"),
                        hasEnchantments(trade.get("given_item_modifier")) || hasEnchantments(trade.get("gives"))));
            }
            String selection = set.has("amount") ? set.get("amount").toString() : "variable";
            levels.computeIfAbsent(parts[0], k -> new ArrayList<>())
                    .add(new TradeAtlas.Level(tier, possibilities, selection));
        }

        List<TradeAtlas.Profession> professions = new ArrayList<>();
        for (var entry : levels.entrySet()) {
            String id = "minecraft:" + entry.getKey();
            if (entry.getKey().equals("wandering_trader")) {
                entry.getValue().sort(Comparator.comparingInt(TradeAtlas.Level::level));
                professions.add(new TradeAtlas.Profession(id, "Wandering Trader", "", entry.getValue()));
                continue;
            }
            VillagerProfession profession;
            try { profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(Identifier.parse(id)); }
            catch (Exception ex) { continue; }
            if (profession == null) continue;
            String workstation = "";
            for (var poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.entrySet()) {
                Holder<PoiType> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE.wrapAsHolder(poi.getValue());
                if (profession.heldJobSite().test(holder)) {
                    var state = poi.getValue().matchingStates().stream().findFirst();
                    if (state.isPresent()) {
                        workstation = BuiltInRegistries.ITEM.getKey(state.get().getBlock().asItem()).toString();
                        break;
                    }
                }
            }
            entry.getValue().sort(Comparator.comparingInt(TradeAtlas.Level::level));
            professions.add(new TradeAtlas.Profession(id, profession.name().getString(), workstation, entry.getValue()));
        }
        professions.sort(Comparator.comparing(TradeAtlas.Profession::id));
        return new TradeAtlas(TradeAtlas.VERSION, "bundled vanilla Minecraft 26.3", professions);
    }

    private static void expand(JsonElement value, Map<String, JsonObject> tags,
                               List<String> resolved, Set<String> visiting) {
        if (resolved.size() > 4096) return;
        if (value.isJsonArray()) {
            for (JsonElement el : value.getAsJsonArray()) expand(el, tags, resolved, visiting);
            return;
        }
        String id;
        if (value.isJsonPrimitive()) id = value.getAsString();
        else if (value.isJsonObject() && value.getAsJsonObject().has("id"))
            id = value.getAsJsonObject().get("id").getAsString();
        else return;

        if (id.startsWith("#")) {
            String tagId = id.substring(1);
            if (!visiting.add(tagId)) return;
            JsonObject tag = tags.get(tagId);
            if (tag != null && tag.has("values")) {
                expand(tag.getAsJsonArray("values"), tags, resolved, visiting);
            }
            visiting.remove(tagId);
        } else {
            resolved.add(id.contains(":") ? id : "minecraft:" + id);
        }
    }

    private static boolean hasEnchantments(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (var e : element.getAsJsonArray()) if (hasEnchantments(e)) return true;
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            if (o.has("type") && o.get("type").isJsonPrimitive()
                    && o.get("type").getAsString().contains("enchant")) return true;
            if (o.has("enchantments") || o.has("minecraft:enchantments")
                    || o.has("minecraft:stored_enchantments")) return true;
            for (var e : o.entrySet()) if (hasEnchantments(e.getValue())) return true;
        }
        return false;
    }

    private static TradeAtlas.Ingredient ingredient(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) return new TradeAtlas.Ingredient(value.getAsString(), 1, "1", "");
        if (!value.isJsonObject()) return null;
        JsonObject o = value.getAsJsonObject();
        String id = o.has("id") ? o.get("id").getAsString() : o.has("item") ? o.get("item").getAsString() : "";
        int count = 1;
        String amount = "1", details = "";
        if (o.has("count")) {
            JsonElement n = o.get("count");
            if (n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber()) {
                count = Math.max(1, n.getAsInt());
                amount = Integer.toString(count);
            } else if (n.isJsonObject()) {
                JsonObject range = n.getAsJsonObject();
                if (range.has("min") && range.has("max")) {
                    amount = range.get("min").getAsString() + "–" + range.get("max").getAsString();
                } else amount = "?";
                details = n.toString();
            } else amount = "?";
        }
        if (o.has("components")) details += " " + o.get("components");
        return new TradeAtlas.Ingredient(id, count, amount, details);
    }
}
