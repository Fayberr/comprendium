package dev.fayber.comprendium.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Presentation-only grouping. No offers are deleted or rewritten.
 * Only standard color variants with exactly identical costs and modifiers
 * are combined, so server-defined prices and datapack changes stay intact.
 */
public final class TradeGroups {
    private static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    );
    private static final List<String> FAMILIES = List.of(
            "wool", "carpet", "bed", "banner", "glazed_terracotta", "terracotta",
            "stained_glass", "stained_glass_pane", "concrete", "concrete_powder",
            "candle", "shulker_box"
    );

    private TradeGroups() {}

    public record DisplayTrade(List<TradeAtlas.Trade> variants, boolean inputVariants, String family) {
        public DisplayTrade {
            variants = List.copyOf(variants);
        }
        public TradeAtlas.Trade primary() { return variants.getFirst(); }
        public boolean grouped() { return variants.size() >= 3; }
        public int height() {
            return grouped() ? 8 + ((variants.size() + 7) / 8) * 18 : AtlasLayout.ROW_HEIGHT;
        }
    }

    private record GroupKey(boolean input, String family, TradeAtlas.Ingredient unchanged,
                            String varyingAmount, int varyingCount, String varyingDetails) {}

    public static List<DisplayTrade> group(List<TradeAtlas.Trade> trades) {
        Map<GroupKey, List<TradeAtlas.Trade>> candidates = new LinkedHashMap<>();
        for (TradeAtlas.Trade trade : trades) {
            GroupKey key = candidate(trade);
            if (key != null) candidates.computeIfAbsent(key, ignored -> new ArrayList<>()).add(trade);
        }

        List<DisplayTrade> result = new ArrayList<>();
        Set<GroupKey> emitted = new HashSet<>();
        for (TradeAtlas.Trade trade : trades) {
            GroupKey key = candidate(trade);
            if (key == null || candidates.get(key).size() < 3 || !differentColors(candidates.get(key), key.input())) {
                result.add(new DisplayTrade(List.of(trade), false, ""));
            } else if (emitted.add(key)) {
                // Preserve the game's original order, not a hardcoded color ordering.
                result.add(new DisplayTrade(candidates.get(key), key.input(), key.family()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean differentColors(List<TradeAtlas.Trade> trades, boolean input) {
        Set<String> unique = new HashSet<>();
        for (TradeAtlas.Trade trade : trades) {
            TradeAtlas.Ingredient ingredient = input ? trade.wants() : trade.gives();
            if (!unique.add(ingredient.id())) return false;
        }
        return true;
    }

    private static GroupKey candidate(TradeAtlas.Trade trade) {
        if (trade == null || trade.conditional() || trade.modified() || trade.glintPreview()
                || trade.additionalWants() != null || trade.wants() == null || trade.gives() == null)
            return null;
        String outputFamily = family(trade.gives().id());
        if (outputFamily != null) {
            return new GroupKey(false, outputFamily, trade.wants(),
                    trade.gives().amount(), trade.gives().count(), trade.gives().details());
        }
        String inputFamily = family(trade.wants().id());
        if (inputFamily != null) {
            return new GroupKey(true, inputFamily, trade.gives(),
                    trade.wants().amount(), trade.wants().count(), trade.wants().details());
        }
        return null;
    }

    private static String family(String id) {
        if (id == null || !id.startsWith("minecraft:")) return null;
        String path = id.substring("minecraft:".length());
        for (String family : FAMILIES) {
            String suffix = "_" + family;
            if (path.endsWith(suffix) && COLORS.contains(path.substring(0, path.length() - suffix.length()))) {
                return family;
            }
        }
        return null;
    }
}
