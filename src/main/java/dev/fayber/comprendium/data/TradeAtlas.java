package dev.fayber.comprendium.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.List;

/**
 * Bounded, display-only snapshot. Never contains hidden per-villager offers.
 * Game item IDs are interpreted locally by the receiving client.
 */
public record TradeAtlas(int protocol, String source, List<Profession> professions) {
    public static final int VERSION = 1;
    public static final int MAX_JSON_CHARS = 1_000_000;
    private static final Gson GSON = new Gson();

    public record Profession(String id, String name, String workstation, List<Level> levels) {}
    public record Level(int level, List<Trade> trades, String selection) {}
    public record Trade(String id, Ingredient wants, Ingredient additionalWants, Ingredient gives,
                        boolean conditional, boolean modified) {}
    public record Ingredient(String id, int count, String amount, String details) {}

    public String toJson() {
        String json = GSON.toJson(this);
        if (json.length() > MAX_JSON_CHARS) throw new IllegalStateException("Trade atlas exceeds packet limit");
        return json;
    }

    public static TradeAtlas parse(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("Invalid atlas size");
        final TradeAtlas result;
        try {
            result = GSON.fromJson(json, TradeAtlas.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("Invalid atlas JSON", ex);
        }
        if (result == null || result.protocol != VERSION || result.source == null
                || result.professions == null || result.professions.size() > 256) {
            throw new IllegalArgumentException("Unsupported or invalid atlas");
        }
        for (Profession p : result.professions) {
            if (p == null || p.id == null || p.name == null || p.levels == null || p.levels.size() > 8) {
                throw new IllegalArgumentException("Invalid profession");
            }
            for (Level l : p.levels) {
                if (l == null || l.level < 1 || l.level > 8 || l.trades == null || l.trades.size() > 2048) {
                    throw new IllegalArgumentException("Invalid trade level");
                }
            }
        }
        return result;
    }

    public static TradeAtlas empty(String source) {
        return new TradeAtlas(VERSION, source, List.of());
    }
}
