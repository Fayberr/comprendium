package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeGroupsTest {
    private static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    static TradeAtlas.Ingredient item(String id, int count) {
        return new TradeAtlas.Ingredient("minecraft:" + id, count, Integer.toString(count), "");
    }

    private static TradeAtlas.Trade trade(String id, TradeAtlas.Ingredient wants,
                                           TradeAtlas.Ingredient gives) {
        return new TradeAtlas.Trade(id, wants, null, gives, false, false, false);
    }

    @Test
    void collapsesAllSixteenBedsWhileRetainingEachItem() {
        List<TradeAtlas.Trade> originals = new ArrayList<>();
        for (String color : COLORS) originals.add(
                trade("minecraft:test/" + color, item("emerald", 3), item(color + "_bed", 1)));
        var grouped = TradeGroups.group(originals);
        assertEquals(1, grouped.size());
        assertEquals(16, grouped.getFirst().variants().size());
        assertFalse(grouped.getFirst().inputVariants());
        assertEquals(44, grouped.getFirst().height());
        for (int i = 0; i < COLORS.length; i++)
            assertEquals(originals.get(i), grouped.getFirst().variants().get(i));
    }

    @Test
    void groupsColoredWoolBeingBoughtNotOnlyItemsBeingSold() {
        List<TradeAtlas.Trade> originals = new ArrayList<>();
        for (String color : COLORS)
            originals.add(trade(color, item(color + "_wool", 18), item("emerald", 1)));
        var rows = TradeGroups.group(originals);
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().inputVariants());
        assertEquals(16, rows.getFirst().variants().size());
    }

    @Test
    void pricesAndLevelsAreNotMixed() {
        var cheap = trade("a", item("emerald", 3), item("red_bed", 1));
        var expensive = trade("b", item("emerald", 4), item("blue_bed", 1));
        var cheapOther = trade("c", item("emerald", 3), item("green_bed", 1));
        assertEquals(3, TradeGroups.group(List.of(cheap, expensive, cheapOther)).size());
    }

    @Test
    void noCompactionOfEnchantedConditionalOrModdedTrades() {
        var original = trade("a", item("emerald", 1), item("red_bed", 1));
        var enchanted = new TradeAtlas.Trade("b", original.wants(), null,
                item("green_bed", 1), false, true, true);
        var conditional = new TradeAtlas.Trade("c", original.wants(), null,
                item("blue_bed", 1), true, false, false);
        var modded = trade("d", item("emerald", 1),
                new TradeAtlas.Ingredient("example:red_bed", 1, "1", ""));
        assertEquals(4, TradeGroups.group(List.of(original, enchanted, conditional, modded)).size());
    }

    @Test
    void layoutMeasuresCollapsedRowsAndNeverClipsThem() {
        List<TradeAtlas.Trade> trades = new ArrayList<>();
        for (String color : COLORS) trades.add(
                trade(color, item("emerald", 1), item(color + "_glazed_terracotta", 1)));
        var p = new TradeAtlas.Profession("minecraft:mason", "Mason", "minecraft:stonecutter",
                List.of(new TradeAtlas.Level(4, trades, "1")));
        assertEquals(AtlasLayout.CARD_HEADER + 16 + AtlasLayout.TIER_HEIGHT + 44,
                AtlasLayout.cardHeight(p));
        assertEquals(16, p.levels().getFirst().trades().size());
    }

    @Test
    void displaysSingletonUnchanged() {
        var only = trade("a", item("stone", 20), item("emerald", 1));
        var grouped = TradeGroups.group(List.of(only));
        assertEquals(1, grouped.size());
        assertFalse(grouped.getFirst().grouped());
        assertEquals(AtlasLayout.ROW_HEIGHT, grouped.getFirst().height());
    }
}
