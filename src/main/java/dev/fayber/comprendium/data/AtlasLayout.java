package dev.fayber.comprendium.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure atlas geometry; it is independent from the Minecraft renderer and screen resolution. */
public final class AtlasLayout {
    public static final int CARD_WIDTH = 232;
    public static final int CARD_GAP = 20;
    public static final int CARD_HEADER = 132;
    public static final int ROW_HEIGHT = 27;
    public static final int TIER_HEIGHT = 24;
    public static final int ROW_GAP = 28;
    public static final int TRADER_COLUMNS = 6;
    public static final int TRADER_COLUMN_GAP = 10;

    private AtlasLayout() {}

    public record Tile(TradeAtlas.Profession profession, int x, int y, int width, int height, boolean trader) {}
    public record Layout(List<Tile> tiles, int width, int height) {}

    public static Layout arrange(List<TradeAtlas.Profession> input) {
        List<TradeAtlas.Profession> regular = new ArrayList<>();
        TradeAtlas.Profession trader = null;
        for (var profession : input) {
            if (profession.id().equals("minecraft:wandering_trader")) trader = profession;
            else regular.add(profession);
        }
        int columns = Math.max(1, Math.min(7, (regular.size() + 1) / 2));
        int rowCount = Math.max(1, (regular.size() + columns - 1) / columns);
        int width = columns * CARD_WIDTH + (columns - 1) * CARD_GAP;

        List<List<TradeAtlas.Profession>> rows = new ArrayList<>();
        int[] maxHeights = new int[rowCount];
        for (int i = 0; i < rowCount; i++) rows.add(new ArrayList<>());
        regular.sort(Comparator.comparingInt(AtlasLayout::cardHeight).reversed()
                .thenComparing(TradeAtlas.Profession::id));
        for (var profession : regular) {
            int best = -1;
            for (int row = 0; row < rowCount; row++) {
                if (rows.get(row).size() >= columns) continue;
                if (best < 0 || maxHeights[row] < maxHeights[best]
                        || (maxHeights[row] == maxHeights[best]
                        && rows.get(row).size() < rows.get(best).size())) best = row;
            }
            rows.get(best).add(profession);
            maxHeights[best] = Math.max(maxHeights[best], cardHeight(profession));
        }

        List<Tile> tiles = new ArrayList<>();
        int y = 0;
        for (int row = 0; row < rowCount; row++) {
            var professions = rows.get(row);
            professions.sort(Comparator.comparing(TradeAtlas.Profession::id));
            int rowWidth = professions.size() * CARD_WIDTH + (professions.size() - 1) * CARD_GAP;
            int startX = Math.max(0, (width - rowWidth) / 2);
            for (int i = 0; i < professions.size(); i++) {
                var p = professions.get(i);
                tiles.add(new Tile(p, startX + i * (CARD_WIDTH + CARD_GAP),
                        y, CARD_WIDTH, cardHeight(p), false));
            }
            y += maxHeights[row] + ROW_GAP;
        }

        if (trader != null) {
            tiles.add(new Tile(trader, 0, y, width, traderHeight(trader), true));
            y += traderHeight(trader) + ROW_GAP;
        }
        return new Layout(List.copyOf(tiles), width, Math.max(0, y - ROW_GAP));
    }

    public static int cardHeight(TradeAtlas.Profession p) {
        int result = CARD_HEADER + 16;
        for (var level : p.levels()) {
            result += TIER_HEIGHT;
            for (var display : TradeGroups.group(level.trades())) result += display.height();
        }
        return result;
    }

    public static int traderHeight(TradeAtlas.Profession trader) {
        int count = 0;
        for (var level : trader.levels()) count += level.trades().size();
        // Each column starts with a category label.
        int perColumn = (count + TRADER_COLUMNS - 1) / TRADER_COLUMNS;
        return CARD_HEADER + 22 + (perColumn + 1) * ROW_HEIGHT + 18;
    }
}
