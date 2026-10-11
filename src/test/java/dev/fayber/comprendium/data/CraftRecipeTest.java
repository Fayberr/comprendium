package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CraftRecipeTest {
    @Test
    void parsesShapedRecipeWithTagAndEmptySlots() {
        var json = JsonParser.parseString("""
                {
                  "type": "minecraft:crafting_shaped",
                  "pattern": ["PP", "WW"],
                  "key": {"P": "#minecraft:planks", "W": {"item":"minecraft:oak_log"}},
                  "result": {"id":"minecraft:cartography_table", "count":1}
                }
                """).getAsJsonObject();
        var recipe = CraftRecipe.parse("minecraft:cartography_table", json).orElseThrow();
        assertEquals("minecraft:cartography_table", recipe.outputItem());
        assertEquals(9, recipe.grid().size());
        assertEquals("#minecraft:planks", recipe.grid().get(0));
        assertEquals("minecraft:oak_log", recipe.grid().get(4));
        assertEquals("", recipe.grid().get(8));
    }

    @Test
    void supportsObjectTagsAndRejectsInvalidRecipePatterns() {
        var json = JsonParser.parseString("""
                {
                  "type":"minecraft:crafting_shaped",
                  "pattern":["III", " S ", " S "],
                  "key":{"I":{"tag":"minecraft:iron_ingots"},"S":"minecraft:stick"},
                  "result":{"id":"minecraft:some_workstation"}
                }
                """).getAsJsonObject();
        assertEquals("#minecraft:iron_ingots",
                CraftRecipe.parse("minecraft:test", json).orElseThrow().grid().get(0));
        json.add("pattern", JsonParser.parseString("[\"xxxx\"]"));
        assertTrue(CraftRecipe.parse("minecraft:test", json).isEmpty());
        json.add("pattern", JsonParser.parseString("[\"XYZ\"]"));
        assertTrue(CraftRecipe.parse("minecraft:test", json).isEmpty());
    }

    @Test
    void rejectsNonCraftingRecipesWithoutGuessingIngredients() {
        var json = JsonParser.parseString("""
                {"type":"minecraft:stonecutting", "ingredient":"minecraft:stone",
                  "result":{"id":"minecraft:stone_bricks"}}
                """).getAsJsonObject();
        assertTrue(CraftRecipe.parse("minecraft:stonecutting", json).isEmpty());
    }

    private static TradeAtlas.Trade variant(String id, String input, String output, boolean modified) {
        return new TradeAtlas.Trade(id,
                new TradeAtlas.Ingredient("minecraft:" + input, 1, "1", ""),
                null, new TradeAtlas.Ingredient("minecraft:" + output, 1, "1", ""),
                false, modified, false);
    }

    @Test
    void groupsCartographerBannersWithIdenticalPriceEvenIfModified() {
        var trades = List.of(
                variant("red", "emerald", "red_banner", true),
                variant("blue", "emerald", "blue_banner", true),
                variant("green", "emerald", "green_banner", true));
        var groups = TradeGroups.group(trades);
        assertEquals(1, groups.size());
        assertEquals(trades, groups.getFirst().variants());
    }

    @Test
    void compactsBoatTypesAndDyesPurchasedByVillagers() {
        var boats = List.of(
                variant("oak", "oak_boat", "emerald", false),
                variant("birch", "birch_boat", "emerald", false),
                variant("cherry", "cherry_boat", "emerald", false),
                variant("mangrove", "mangrove_boat", "emerald", false));
        assertEquals(1, TradeGroups.group(boats).size());
        assertEquals(4, TradeGroups.group(boats).getFirst().variants().size());
        assertTrue(TradeGroups.group(boats).getFirst().inputVariants());

        var dyes = List.of(
                variant("red", "red_dye", "emerald", false),
                variant("cyan", "cyan_dye", "emerald", false),
                variant("blue", "blue_dye", "emerald", false));
        assertEquals(1, TradeGroups.group(dyes).size());
    }

    @Test
    void refusesMismatchedPricesAndTradeModifiers() {
        var offers = List.of(
                variant("red", "emerald", "red_banner", true),
                variant("blue", "emerald", "blue_banner", false),
                variant("green", "diamond", "green_banner", true));
        assertEquals(3, TradeGroups.group(offers).size());
    }
}
