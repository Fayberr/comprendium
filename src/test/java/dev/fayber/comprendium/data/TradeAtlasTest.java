package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeAtlasTest {
    @Test
    void roundTripPreservesCatalog() {
        TradeAtlas.Ingredient emeralds = new TradeAtlas.Ingredient("minecraft:emerald", 4, "4", "");
        TradeAtlas.Ingredient boots = new TradeAtlas.Ingredient("minecraft:iron_boots", 1, "1", "");
        TradeAtlas.Trade trade = new TradeAtlas.Trade("minecraft:armorer/1/emerald_iron_boots",
                emeralds, null, boots, false, false);
        TradeAtlas original = new TradeAtlas(TradeAtlas.VERSION, "test",
                List.of(new TradeAtlas.Profession("minecraft:armorer", "Armorer",
                        "minecraft:blast_furnace", List.of(new TradeAtlas.Level(1, List.of(trade), "2")))));
        assertEquals(original, TradeAtlas.parse(original.toJson()));
    }

    @Test
    void rejectsUnsupportedProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeAtlas.parse("{\"protocol\":2,\"source\":\"evil\",\"professions\":[]}"));
    }

    @Test
    void rejectsMissingCatalogAndOverlongPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> TradeAtlas.parse("{\"protocol\":1,\"source\":\"missing\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> TradeAtlas.parse("X".repeat(TradeAtlas.MAX_JSON_CHARS + 1)));
    }
}
