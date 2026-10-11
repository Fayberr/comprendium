package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtlasLayoutTest {
    private static TradeAtlas.Profession profession(int i, int count) {
        List<TradeAtlas.Trade> offers = new ArrayList<>();
        for (int row = 0; row < count; row++) {
            offers.add(new TradeAtlas.Trade("minecraft:trade_" + row, null, null, null,
                    false, false, false));
        }
        return new TradeAtlas.Profession("minecraft:profession_" + i,
                "Profession " + i, "", List.of(new TradeAtlas.Level(1, offers, "1")));
    }

    @Test
    void thirteenProfessionsUseTwoRowsInsteadOfOneStrip() {
        List<TradeAtlas.Profession> input = new ArrayList<>();
        for (int i = 0; i < 13; i++) input.add(profession(i, i == 3 ? 18 : 3));
        var layout = AtlasLayout.arrange(input);
        assertEquals(13, layout.tiles().size());
        assertEquals(7 * AtlasLayout.CARD_WIDTH + 6 * AtlasLayout.CARD_GAP, layout.width());
        assertEquals(2, layout.tiles().stream().map(AtlasLayout.Tile::y).distinct().count());
        assertNoOverlaps(layout.tiles());
    }

    @Test
    void wanderingTraderHasDedicatedWidePanelAfterVillagers() {
        List<TradeAtlas.Profession> input = new ArrayList<>();
        for (int i = 0; i < 13; i++) input.add(profession(i, 2));
        var trader = new TradeAtlas.Profession("minecraft:wandering_trader",
                "Wandering Trader", "", List.of(new TradeAtlas.Level(2,
                profession(99, 80).levels().getFirst().trades(), "5")));
        input.add(trader);
        var layout = AtlasLayout.arrange(input);
        var panel = layout.tiles().stream().filter(AtlasLayout.Tile::trader).findFirst().orElseThrow();
        assertEquals(layout.width(), panel.width());
        assertTrue(layout.tiles().stream().filter(t -> !t.trader())
                .allMatch(t -> t.y() + t.height() < panel.y()));
        assertNoOverlaps(layout.tiles());
    }

    private static void assertNoOverlaps(List<AtlasLayout.Tile> tiles) {
        for (int i = 0; i < tiles.size(); i++) {
            var a = tiles.get(i);
            for (int j = i + 1; j < tiles.size(); j++) {
                var b = tiles.get(j);
                assertFalse(a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                        && a.y() < b.y() + b.height() && b.y() < a.y() + a.height(),
                        a.profession().id() + " overlaps " + b.profession().id());
            }
        }
    }
}
