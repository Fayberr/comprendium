package dev.fayber.comprendium.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Resolves item tags into ordered, distinct, valid item choices for cycling recipe previews. */
public final class TagChoices {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_CANDIDATES = 1024;
    private final Map<String, List<String>> tags;
    private final Predicate<String> validItem;
    private final Map<String, List<String>> cache = new HashMap<>();

    public TagChoices(Map<String, List<String>> tags, Predicate<String> validItem) {
        this.tags = Map.copyOf(tags);
        this.validItem = validItem;
    }

    /** An untagged ingredient never changes. Tagged ingredients rotate once per elapsed second. */
    public String representative(String ingredient, long epochSeconds) {
        if (ingredient == null || ingredient.isBlank()) return "";
        if (!ingredient.startsWith("#")) return ingredient;
        List<String> choices = choices(ingredient);
        return choices.isEmpty() ? "" : choices.get((int) Math.floorMod(epochSeconds, choices.size()));
    }

    public List<String> choices(String ingredient) {
        if (ingredient == null || ingredient.isBlank()) return List.of();
        if (!ingredient.startsWith("#"))
            return validItem.test(ingredient) ? List.of(ingredient) : List.of();
        return cache.computeIfAbsent(ingredient, tag -> {
            LinkedHashSet<String> options = new LinkedHashSet<>();
            collect(tag.substring(1), new HashSet<>(), options, 0);
            return List.copyOf(options);
        });
    }

    private void collect(String tag, Set<String> visiting, LinkedHashSet<String> options, int depth) {
        if (depth >= MAX_DEPTH || options.size() >= MAX_CANDIDATES || !visiting.add(tag)) return;
        for (String id : tags.getOrDefault(tag, List.of())) {
            if (options.size() >= MAX_CANDIDATES) break;
            if (id.startsWith("#")) {
                collect(id.substring(1), visiting, options, depth + 1);
            } else if (validItem.test(id)) {
                options.add(id);
            }
        }
        visiting.remove(tag);
    }
}
