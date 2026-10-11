package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartographerGroupsTest {
    private static final List<String> COLORS = List.of(
            "white","orange","magenta","light_blue","yellow","lime","pink",
            "gray","light_gray","cyan","purple","blue","brown","green","red","black");
    private static TradeAtlas.Ingredient ingredient(String item, int count) {
        return new TradeAtlas.Ingredient("minecraft:" + item, count, Integer.toString(count), "");
    }
    @Test
    void groupsSixteenBiomeDependentBannersWithoutDroppingAnyOption() {
        List<TradeAtlas.Trade> source = new ArrayList<>();
        for (String color : COLORS) {
            source.add(new TradeAtlas.Trade("minecraft:cartographer/4/emerald_" + color + "_banner",
                    ingredient("emerald", 3), null, ingredient(color + "_banner", 1),
                    true, false, false));
        }
        var grouped = TradeGroups.group(source);
        assertEquals(1, grouped.size());
        assertTrue(grouped.getFirst().biomeDependent());
        assertEquals(16, grouped.getFirst().variants().size());
        assertEquals(95, grouped.getFirst().height());
        assertEquals(source, grouped.getFirst().variants());
    }

    @Test
    void groupsVillageMapsWithSharedCompassButNotDifferentPrice() {
        List<TradeAtlas.Trade> source = new ArrayList<>();
        for (String type : List.of("plains", "desert", "savanna", "taiga", "snowy")) {
            source.add(new TradeAtlas.Trade("minecraft:cartographer/2/emerald_and_compass_village_" + type + "_map",
                    ingredient("emerald", 8), ingredient("compass", 1),
                    ingredient(type + "_village_map", 1), true, true, false));
        }
        var grouped = TradeGroups.group(source);
        assertEquals(1, grouped.size());
        assertEquals("village_map", grouped.getFirst().family());
        assertEquals(5, grouped.getFirst().columns());
        assertEquals(5, grouped.getFirst().variants().size());
        assertEquals(47, grouped.getFirst().height());
        assertEquals(source, grouped.getFirst().variants());
        var different = new TradeAtlas.Trade("different",
                ingredient("emerald", 9), ingredient("compass", 1),
                ingredient("jungle_village_map", 1), true, true, false);
        assertEquals(2, TradeGroups.group(new ArrayList<TradeAtlas.Trade>() {{
            addAll(source); add(different);
        }}).size());
    }

    @Test
    void differentPredicateFlagsOrSecondIngredientsStayDistinct() {
        var normal = new TradeAtlas.Trade("a", ingredient("emerald", 8),
                ingredient("compass", 1), ingredient("plains_village_map", 1), true, true, false);
        var unconstrained = new TradeAtlas.Trade("b", ingredient("emerald", 8),
                ingredient("compass", 1), ingredient("desert_village_map", 1), false, true, false);
        var withoutCompass = new TradeAtlas.Trade("c", ingredient("emerald", 8),
                null, ingredient("taiga_village_map", 1), true, true, false);
        assertEquals(3, TradeGroups.group(List.of(normal, unconstrained, withoutCompass)).size());
    }
}
