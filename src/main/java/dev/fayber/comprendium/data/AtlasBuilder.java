package dev.fayber.comprendium.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;

/**
 * Captures definitions from the active server-side registries. Never rolls offers
 * or probes villagers: this is a catalogue of possible trades, not discovered secrets.
 */
public final class AtlasBuilder {
    private AtlasBuilder() {}

    public static TradeAtlas build(MinecraftServer server) {
        var access = server.registryAccess();
        HolderLookup.RegistryLookup<TradeSet> sets = access.lookupOrThrow(Registries.TRADE_SET);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        List<TradeAtlas.Profession> professions = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<VillagerProfession>, VillagerProfession> entry :
                BuiltInRegistries.VILLAGER_PROFESSION.entrySet()) {
            VillagerProfession profession = entry.getValue();
            if (profession.tradeSetsByLevel().isEmpty()) continue;
            List<TradeAtlas.Level> levels = new ArrayList<>();
            for (int tier : profession.tradeSetsByLevel().keySet()) {
                var key = profession.getTrades(tier);
                if (key == null) continue;
                TradeSet set = sets.get(key).map(Holder::value).orElse(null);
                if (set == null) continue;
                List<TradeAtlas.Trade> trades = readTrades(set, ops);
                String selection = set.amount().unwrapKey().map(k -> k.identifier().toString())
                        .orElse("Random selection");
                levels.add(new TradeAtlas.Level(tier, trades, selection));
            }
            levels.sort(Comparator.comparingInt(TradeAtlas.Level::level));
            Identifier id = entry.getKey().identifier();
            professions.add(new TradeAtlas.Profession(id.toString(), profession.name().getString(),
                    findWorkstation(profession), levels));
        }
        // Wandering traders use three dedicated, non-level trade pools.
        String[] traderPools = {"buying", "common", "uncommon"};
        List<TradeAtlas.Level> traderLevels = new ArrayList<>();
        for (int i = 0; i < traderPools.length; i++) {
            var key = TradeSets.resourceKey("wandering_trader/" + traderPools[i]);
            TradeSet set = sets.get(key).map(Holder::value).orElse(null);
            if (set != null) {
                traderLevels.add(new TradeAtlas.Level(i + 1, readTrades(set, ops),
                        set.amount().unwrapKey().map(k -> k.identifier().toString()).orElse("Random selection")));
            }
        }
        if (!traderLevels.isEmpty()) {
            professions.add(new TradeAtlas.Profession("minecraft:wandering_trader",
                    "Wandering Trader", "", traderLevels));
        }
        professions.sort(Comparator.comparing(TradeAtlas.Profession::id));
        return new TradeAtlas(TradeAtlas.VERSION, "server", professions);
    }

    private static List<TradeAtlas.Trade> readTrades(TradeSet set, RegistryOps<JsonElement> ops) {
        List<TradeAtlas.Trade> trades = new ArrayList<>();
        for (Holder<VillagerTrade> trade : set.trades()) {
            try {
                JsonElement encoded = VillagerTrade.CODEC.encodeStart(ops, trade.value()).getOrThrow();
                String id = trade.unwrapKey().map(k -> k.identifier().toString()).orElse("inline");
                trades.add(readTrade(id, encoded.getAsJsonObject()));
            } catch (RuntimeException ex) {
                // A malformed custom trade should not blank the whole atlas.
            }
        }
        return trades;
    }

    /** Only preview enchantments when the definition actually enchants the output. */
    static boolean hasEnchantments(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (var entry : element.getAsJsonArray()) if (hasEnchantments(entry)) return true;
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()
                    && object.get("type").getAsString().contains("enchant")) return true;
            if (object.has("minecraft:enchantments") || object.has("minecraft:stored_enchantments")
                    || object.has("enchantments")) return true;
            for (var entry : object.entrySet()) if (hasEnchantments(entry.getValue())) return true;
        }
        return false;
    }

    private static String findWorkstation(VillagerProfession profession) {
        for (var poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.entrySet()) {
            Holder<PoiType> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE.wrapAsHolder(poi.getValue());
            if (profession.heldJobSite().test(holder)) {
                var state = poi.getValue().matchingStates().stream().findFirst();
                if (state.isPresent()) {
                    return BuiltInRegistries.ITEM.getKey(state.get().getBlock().asItem()).toString();
                }
            }
        }
        return "";
    }

    static TradeAtlas.Trade readTrade(String id, JsonObject obj) {
        boolean conditional = obj.has("merchant_predicate");
        boolean modified = obj.has("given_item_modifier") || obj.has("double_trade_price_enchantments");
        return new TradeAtlas.Trade(id, ingredient(obj.get("wants")),
                ingredient(obj.get("additional_wants")), ingredient(obj.get("gives")), conditional, modified,
                hasEnchantments(obj.get("given_item_modifier"))
                        || hasEnchantments(obj.get("gives")));
    }

    static TradeAtlas.Ingredient ingredient(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        String id = "";
        int count = 1;
        String amount = "1";
        String details = "";
        if (el.isJsonPrimitive()) {
            id = el.getAsString();
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("id")) id = o.get("id").getAsString();
            if (o.has("item")) id = o.get("item").getAsString();
            if (o.has("count")) {
                JsonElement n = o.get("count");
                if (n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber()) {
                    count = Math.max(1, n.getAsInt());
                    amount = Integer.toString(count);
                } else {
                    amount = "?";
                    details = n.toString();
                }
            }
            if (o.has("components")) details += " Components: " + o.get("components");
        }
        return new TradeAtlas.Ingredient(id, count, amount, details);
    }
}
