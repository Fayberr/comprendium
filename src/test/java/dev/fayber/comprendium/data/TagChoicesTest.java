package dev.fayber.comprendium.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TagChoicesTest {
    private static final Map<String, List<String>> TAGS = Map.of(
            "minecraft:logs", List.of("#minecraft:oak_logs", "minecraft:birch_log", "minecraft:dark_oak_log"),
            "minecraft:oak_logs", List.of("minecraft:oak_log", "minecraft:oak_wood"),
            "minecraft:planks", List.of("minecraft:oak_planks", "minecraft:birch_planks"),
            "minecraft:cycle_a", List.of("#minecraft:cycle_b"),
            "minecraft:cycle_b", List.of("#minecraft:cycle_a")
    );

    @Test
    void rotatesNestedTagOneChoicePerSecondAndSynchronizesSlots() {
        TagChoices choices = new TagChoices(TAGS, id -> !id.equals("minecraft:oak_wood"));
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:dark_oak_log"),
                choices.choices("#minecraft:logs"));
        assertEquals("minecraft:oak_log", choices.representative("#minecraft:logs", 0));
        assertEquals("minecraft:oak_log", choices.representative("#minecraft:logs", 0));
        assertEquals("minecraft:birch_log", choices.representative("#minecraft:logs", 1));
        assertEquals("minecraft:dark_oak_log", choices.representative("#minecraft:logs", 2));
        assertEquals("minecraft:oak_log", choices.representative("#minecraft:logs", 3));
        assertEquals("minecraft:oak_planks", choices.representative("#minecraft:planks", 0));
        assertEquals("minecraft:birch_planks", choices.representative("#minecraft:planks", 1));
    }

    @Test
    void handlesMissingOrRecursiveTagsAndFixedIngredients() {
        TagChoices choices = new TagChoices(TAGS, id -> true);
        assertEquals(List.of(), choices.choices("#minecraft:cycle_a"));
        assertEquals("", choices.representative("#minecraft:cycle_a", 10));
        assertEquals("", choices.representative("#minecraft:missing", 10));
        assertEquals("minecraft:furnace", choices.representative("minecraft:furnace", 999));
        assertEquals("minecraft:oak_log", choices.representative("#minecraft:logs", -3));
    }

    @Test
    void deduplicatesNestedTagsInSourceOrder() {
        TagChoices choices = new TagChoices(Map.of(
                "minecraft:one", List.of("#minecraft:two", "minecraft:a", "minecraft:b"),
                "minecraft:two", List.of("minecraft:a", "minecraft:c")), id -> true);
        assertEquals(List.of("minecraft:a", "minecraft:c", "minecraft:b"),
                choices.choices("#minecraft:one"));
    }
}
