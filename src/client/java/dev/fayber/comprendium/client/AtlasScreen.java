package dev.fayber.comprendium.client;

import dev.fayber.comprendium.data.AtlasLayout;
import net.fayber.moderngui.render.Ui;
import net.fayber.moderngui.render.Theme;
import net.fayber.moderngui.widget.Tabs;
import dev.fayber.comprendium.data.CraftRecipe;
import dev.fayber.comprendium.data.TradeGroups;
import dev.fayber.comprendium.data.TradeAtlas;
import dev.fayber.comprendium.data.TradeAtlas.Ingredient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map-like trade reference. Native entity previews use screen coordinates and
 * MUST NOT inherit the 2D matrix used by text/items: Minecraft 26.3's entity
 * GUI extraction does not apply that matrix to extracted 3D render states.
 */
public final class AtlasScreen extends Screen {
    // Atlas dimensions are logical GUI units, independent from screen zoom.
    private static final int HEADER = 82;
    private static final float TRADE_ITEM_SCALE = 1.32f;
    private static final float RECIPE_ITEM_SCALE = 1.42f;
    private static final int RECIPE_CELL = 27;
    private static final int FOOTER = 34;
    private static final int BACKGROUND = 0xFF0C121B;
    private static final int SURFACE = 0xFF17212D;
    private static final int SURFACE_TOP = 0xFF202E3B;
    private static final int BORDER = 0xFF344454;
    private static final int TEXT = 0xFFF2F3EF;
    private static final int MUTED = 0xFF91A1AE;
    private static final int ACCENT = 0xFF9DCCB3;
    private static final int GOLD = 0xFFF1CD86;
    private static final int[] LEVEL_COLORS = {
        MUTED, 0xFFB8C2CC, 0xFFAED6B1, 0xFF7BC7CE, 0xFFE9BB84, 0xFFC6A7EA
    };
    private static final String[] LEVEL_NAMES = {
        "", "NOVICE", "APPRENTICE", "JOURNEYMAN", "EXPERT", "MASTER"
    };
    private static final String[] TRADER_POOL_NAMES = {
        "", "BUYING", "COMMON", "UNCOMMON"
    };

    private GuideSection section = GuideSection.TRADING;
    private Tabs guideTabs;
    private float zoom = 0.85f;
    private float panX, panY;
    private boolean positioned;
    private final Map<String, LivingEntity> models = new HashMap<>();
    private final Map<String, ItemStack> items = new HashMap<>();
    private int nextPreviewEntityId = -1;
    private WorkstationRecipes workstationRecipes;
    private TradeAtlas lastAtlas;
    private AtlasLayout.Layout layout;
    private record Hover(Ingredient ingredient, boolean glintPreview) {}
    private record TraderRow(int pool, TradeAtlas.Trade trade) {}

    public AtlasScreen() {
        super(Component.literal("Comprendium"));
    }

    @Override
    protected void init() {
        super.init();
        guideTabs = new Tabs(0, 24,
                java.util.Arrays.stream(GuideSection.values())
                        .<Component>map(tab -> Component.literal(tab.title())).toList(),
                () -> section.ordinal(),
                index -> section = GuideSection.values()[index])
                .theme(Theme.dark().withAccent(ACCENT));
        guideTabs.setX((width - guideTabs.getWidth()) / 2);
        addRenderableWidget(guideTabs);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        TradeAtlas atlas = ComprendiumClient.atlas();
        if (section == GuideSection.TRADING && workstationRecipes == null) {
            workstationRecipes = WorkstationRecipes.load();
        }
        if (layout == null || lastAtlas != atlas) {
            layout = AtlasLayout.arrange(atlas.professions());
            lastAtlas = atlas;
            models.clear();
            positioned = false;
        }
        if (!positioned) {
            zoom = Math.clamp((width - 70f) / Math.max(1, layout.width()), 0.38f, 0.9f);
            panX = (width - layout.width() * zoom) * 0.5f;
            panY = HEADER + 24;
            positioned = true;
        }
        graphics.fill(0, 0, width, height, BACKGROUND);
        if (section == GuideSection.TRADING) {
        graphics.enableScissor(0, HEADER, width, height - FOOTER);
        graphics.pose().pushMatrix();
        graphics.pose().translate(panX, panY);
        graphics.pose().scale(zoom, zoom);
        float worldMouseX = (mouseX - panX) / zoom;
        float worldMouseY = (mouseY - panY) / zoom;
        Hover hovered = null;
        if (layout.tiles().isEmpty()) {
            graphics.text(font, "NO TRADE DATA AVAILABLE", 22, 22, TEXT);
            graphics.text(font, atlas.source(), 22, 48, MUTED);
        }
        for (var tile : layout.tiles()) {
            if (!visible(tile)) continue;
            hovered = tile.trader()
                    ? drawTrader(graphics, tile, worldMouseX, worldMouseY, hovered)
                    : drawVillager(graphics, tile, worldMouseX, worldMouseY, hovered);
        }
        graphics.pose().popMatrix();

        // The native 3D renderer queues an independent render state, so project
        // tile coordinates into actual screen pixels instead of expecting its
        // bounding box to follow graphics.pose().
        for (var tile : layout.tiles()) {
            if (visible(tile)) drawPreview(graphics, tile);
        }
        graphics.disableScissor();
        if (hovered != null) {
            var stack = itemStack(hovered.ingredient(), hovered.glintPreview());
            if (!stack.isEmpty()) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
        } else {
            drawGuidePlaceholder(graphics);
        }

        // Persistent application chrome, independent from each guide canvas.
        Ui.roundRect(graphics, 7, 7, width - 14, HEADER - 13, 12, 0xFF151D26);
        Ui.text(graphics, Ui.uiBold("Comprendium"), 24, 22, TEXT);
        Ui.text(graphics, Ui.ui("In-game guidebook"), 24, 43, MUTED);
        String meta = "MC 26.3";
        Ui.textRight(graphics, Ui.ui(meta), width - 24, 28, MUTED);

        // The Modern GUI tab widget is rendered by Screen at the end of this method.
        // A subtle rounded surface gives it a clear, stable navigation home.
        if (guideTabs != null) {
            Ui.roundRect(graphics, guideTabs.getX() - 9, guideTabs.getY() - 5,
                    guideTabs.getWidth() + 18, Tabs.HEIGHT + 10, 10, 0xFF222E38);
        }

        Ui.roundRect(graphics, 7, height - FOOTER + 3, width - 14, FOOTER - 9, 9, 0xFF151D26);
        if (section == GuideSection.TRADING) {
            Ui.text(graphics, Ui.ui("Drag to pan   •   Scroll to zoom   •   Esc to close"),
                    22, height - FOOTER + 12, MUTED);
            String stats = atlas.professions().size() + " traders     " + Math.round(zoom * 100) + "% zoom";
            Ui.textRight(graphics, Ui.ui(stats), width - 23, height - FOOTER + 12, MUTED);
        } else {
            Ui.text(graphics, Ui.ui("Select Trading to return to your atlas   •   Esc to close"),
                    22, height - FOOTER + 12, MUTED);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** A genuinely empty future guide canvas; no placeholder trade data is invented. */
    private void drawGuidePlaceholder(GuiGraphicsExtractor graphics) {
        int w = Math.min(490, Math.max(200, width - 70));
        int h = Math.min(215, Math.max(115, height - HEADER - FOOTER - 45));
        int x = (width - w) / 2;
        int y = HEADER + (height - HEADER - FOOTER - h) / 2;
        Ui.roundRectBorder(graphics, x, y, w, h, 14,
                0xFF17222D, 0xFF2A3743, 1);
        Ui.roundRect(graphics, x + 21, y + 20, 95, 20, 10, 0xFF263B3A);
        Ui.text(graphics, Ui.uiBold("COMING NEXT"), x + 29, y + 26, ACCENT);
        Ui.textCentered(graphics, Ui.uiBold("A new guide starts here"),
                width / 2, y + h / 2 - 13, TEXT);
        Ui.textCentered(graphics, Ui.ui("This canvas is ready for future references."),
                width / 2, y + h / 2 + 12, MUTED);
    }

    private boolean visible(AtlasLayout.Tile tile) {
        float left = panX + tile.x() * zoom;
        float right = left + tile.width() * zoom;
        float top = panY + tile.y() * zoom;
        float bottom = top + tile.height() * zoom;
        return right > 0 && left < width && bottom > HEADER && top < height - FOOTER;
    }

    /**
     * Modern GUI's Inter glyph sheets have 1–6x raster variants.
     * Canvas text is additionally scaled by the atlas zoom (unlike the fixed UI
     * chrome). Select a glyph sheet for the resulting *physical* scale so large
     * zoom doesn't simply nearest-neighbor upscale low-resolution letters.
     */
    private Component canvasLabel(String label, boolean bold, float extraScale) {
        int raster = Math.clamp(Math.round(Ui.scale() * zoom * extraScale), 1, 6);
        String family = bold ? "ui_bold_x" : "ui_x";
        return Component.literal(label).setStyle(Style.EMPTY.withFont(
                new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("moderngui", family + raster))));
    }

    private void atlasText(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        graphics.text(font, canvasLabel(label, false, 1f), x, y, color, false);
    }

    private int atlasTextWidth(String label) {
        return font.width(canvasLabel(label, false, 1f));
    }

    private static final int[] TITLE_RASTERS = {1, 2, 3, 4, 6, 8, 10, 12};

    /**
     * Unlike the standard UI font (9 logical pixels), titles use native
     * 18-pixel Inter glyphs. We only scale DOWN to fit unusually long names,
     * never upscale a 9px glyph and magnify its edges.
     */
    private Component titleLabel(String title, int oversample) {
        return Component.literal(title).setStyle(Style.EMPTY.withFont(
                new FontDescription.Resource(Identifier.fromNamespaceAndPath(
                        "comprendium", "atlas_title_x" + oversample))));
    }

    private static int nearestTitleRaster(float physicalScale) {
        int nearest = TITLE_RASTERS[0];
        for (int step : TITLE_RASTERS) {
            if (Math.abs(step - physicalScale) < Math.abs(nearest - physicalScale)) nearest = step;
        }
        return nearest;
    }

    private void atlasTitle(GuiGraphicsExtractor graphics, String title, int x, int y, int maxWidth) {
        Component measurement = titleLabel(title, 2);
        float fit = Math.min(1f, maxWidth / (float) Math.max(1, font.width(measurement)));
        // The GUI scale and atlas zoom both affect how many display pixels a
        // single text glyph occupies. Choose a matching TTF atlas resolution.
        int oversample = nearestTitleRaster(Ui.scale() * zoom * fit);
        // GUI text coordinates describe its baseline region, not the visible
        // glyph's upper edge. Inter's 18px capitals extend about 7 units ABOVE
        // that anchor; previously the green accent started at y and appeared
        // roughly half a letter too low. Track the visible cap-height instead.
        float capHeight = 15.5f * fit;
        float capTop = y - 7f * fit;
        Ui.roundRect(graphics, x - 7f, capTop, 4f, capHeight,
                2f, ACCENT);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        if (fit < 1f) graphics.pose().scale(fit, fit);
        graphics.text(font, titleLabel(title, oversample), 0, 0, TEXT, false);
        graphics.pose().popMatrix();
    }

    private void panel(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile) {
        int x = tile.x(), y = tile.y(), w = tile.width(), h = tile.height();
        Ui.roundRect(graphics, x + 4, y + 6, w, h, 11, 0x66000000);
        Ui.roundRectBorder(graphics, x, y, w, h, 11, SURFACE, BORDER, 1f);
        Ui.roundRect(graphics, x + 1, y + 1, w - 2,
                AtlasLayout.CARD_HEADER - 8, 10, SURFACE_TOP);
    }

    private Hover drawVillager(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile,
                               float mx, float my, Hover hovered) {
        panel(graphics, tile);
        var p = tile.profession();
        int x = tile.x(), y = tile.y(), w = tile.width();
        atlasTitle(graphics, p.name(), x + 18, y + 15, w - 32);
        // Vanilla is the default; call out only external professions.
        if (!p.id().startsWith("minecraft:")) {
            atlasText(graphics, "MODDED  •  " + p.id(), x + 19, y + 42, ACCENT);
        }
        ItemStack workstation = itemStack(new Ingredient(p.workstation(), 1, "1", ""), false);
        if (!workstation.isEmpty()) {
            // The large job-site model sits above its actual crafting recipe.
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 152f, y + 43f);
            graphics.pose().scale(3.15f, 3.15f);
            graphics.fakeItem(workstation, 0, 0);
            graphics.pose().popMatrix();
            // The block is rendered through a scaled matrix, but hover testing
            // is done in unscaled atlas coordinates (same as all trade icons).
            if (mx >= x + 152 && mx < x + 152 + 51
                    && my >= y + 43 && my < y + 43 + 51) {
                hovered = new Hover(new Ingredient(p.workstation(), 1, "1", ""), false);
            }
            hovered = drawWorkstationRecipe(graphics, p.workstation(),
                    x + 134, y + 105, mx, my, hovered);
        }
        graphics.fill(x + 12, y + 217, x + w - 12, y + 218, BORDER);
        int cursorY = y + AtlasLayout.CARD_HEADER;
        for (var level : p.levels()) {
            int tier = Math.clamp(level.level(), 0, 5);
            Ui.roundRect(graphics, x + 9, cursorY, w - 18,
                    AtlasLayout.TIER_HEIGHT - 3, 4, 0xFF2B3948);
            String name = tier == 0 ? "LEVEL " + level.level() : LEVEL_NAMES[tier];
            atlasText(graphics, name, x + 16, cursorY + 9, LEVEL_COLORS[tier]);
            var grouped = TradeGroups.group(level.trades());
            String count = grouped.size() == level.trades().size()
                    ? Integer.toString(grouped.size()) : grouped.size() + " / " + level.trades().size();
            atlasText(graphics, count, x + w - atlasTextWidth(count) - 17, cursorY + 9, MUTED);
            cursorY += AtlasLayout.TIER_HEIGHT;
            for (var display : grouped) {
                hovered = display.grouped()
                        ? drawGroupedTrade(graphics, display, x + 9, cursorY, w - 18, mx, my, hovered)
                        : drawTrade(graphics, display.primary(), x + 9, cursorY, w - 18, mx, my, hovered);
                cursorY += display.height();
            }
        }
        return hovered;
    }

    /** Compact 3x3 native item grid, using the recipe currently packaged in the game/mod JARs. */
    private Hover drawWorkstationRecipe(GuiGraphicsExtractor graphics, String itemId,
                                        int x, int y, float mx, float my, Hover hovered) {
        CraftRecipe recipe = workstationRecipes.find(itemId);
        if (recipe == null) {
            atlasText(graphics, "RECIPE UNAVAILABLE", x - 23, y + 22, MUTED);
            return hovered;
        }
        // One shared second counter makes all slots of the same ingredient tag
        // show the same type of wood/planks on each recipe frame.
        long recipeSecond = System.currentTimeMillis() / 1_000L;
        for (int index = 0; index < 9; index++) {
            int ix = x + index % 3 * RECIPE_CELL;
            int iy = y + index / 3 * RECIPE_CELL;
            Ui.roundRect(graphics, ix, iy, RECIPE_CELL - 1, RECIPE_CELL - 1, 5, 0xFF30404D);
            Ui.roundRect(graphics, ix + 1, iy + 1, RECIPE_CELL - 3, RECIPE_CELL - 3, 4, 0xFF1B2935);
            String ingredient = recipe.grid().get(index);
            if (ingredient.isEmpty()) continue;
            String sample = workstationRecipes.representative(ingredient, recipeSecond);
            if (sample.isEmpty()) {
                atlasText(graphics, "?", ix + 10, iy + 9, GOLD);
            } else {
                // An item tag chooses a representative item, not an invented recipe.
                Ingredient entry = new Ingredient(sample, 1, "1",
                        ingredient.startsWith("#") ? ingredient : "");
                graphics.pose().pushMatrix();
                graphics.pose().translate(ix + 2f, iy + 2f);
                graphics.pose().scale(RECIPE_ITEM_SCALE, RECIPE_ITEM_SCALE);
                graphics.fakeItem(itemStack(entry, false), 0, 0);
                graphics.pose().popMatrix();
                if (mx >= ix && mx < ix + RECIPE_CELL - 1
                        && my >= iy && my < iy + RECIPE_CELL - 1) {
                    hovered = new Hover(entry, false);
                }
            }
        }
        return hovered;
    }

    private Hover drawTrader(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile,
                             float mx, float my, Hover hovered) {
        panel(graphics, tile);
        int x = tile.x(), y = tile.y(), w = tile.width();
        atlasTitle(graphics, "Wandering Trader", x + 19, y + 15, w - 36);
        atlasText(graphics, "SPECIAL TRADER  /  NO WORKSTATION", x + 15, y + 31, MUTED);
        atlasText(graphics, "BUYING  •  COMMON  •  UNCOMMON   /   RANDOM POSSIBLE OFFERS",
                x + 140, y + 95, MUTED);

        List<TraderRow> trades = new ArrayList<>();
        for (var level : tile.profession().levels()) {
            trades.add(new TraderRow(level.level(), null)); // Pool divider.
            for (var trade : level.trades()) trades.add(new TraderRow(level.level(), trade));
        }
        int perColumn = Math.max(1, (trades.size() + AtlasLayout.TRADER_COLUMNS - 1)
                / AtlasLayout.TRADER_COLUMNS);
        int padding = 12, gap = AtlasLayout.TRADER_COLUMN_GAP;
        int colWidth = (w - padding * 2 - (AtlasLayout.TRADER_COLUMNS - 1) * gap)
                / AtlasLayout.TRADER_COLUMNS;
        for (int col = 0; col < AtlasLayout.TRADER_COLUMNS; col++) {
            int from = col * perColumn;
            if (from >= trades.size()) break;
            int end = Math.min(trades.size(), from + perColumn);
            int cx = x + padding + col * (colWidth + gap);
            int pool = trades.get(from).pool();
            String name = pool >= 1 && pool < TRADER_POOL_NAMES.length
                    ? TRADER_POOL_NAMES[pool] : "POOL " + pool;
            graphics.fill(cx, y + AtlasLayout.CARD_HEADER, cx + colWidth,
                    y + AtlasLayout.CARD_HEADER + 20, 0xFF2B3948);
            atlasText(graphics, name, cx + 7, y + AtlasLayout.CARD_HEADER + 5, ACCENT);
            for (int index = from; index < end; index++) {
                TraderRow row = trades.get(index);
                int iy = y + AtlasLayout.CARD_HEADER + 24
                        + (index - from) * AtlasLayout.ROW_HEIGHT;
                if (row.trade() == null) {
                    graphics.fill(cx, iy, cx + colWidth, iy + AtlasLayout.ROW_HEIGHT - 1,
                            0xFF344659);
                    int group = row.pool();
                    String groupLabel = group >= 1 && group < TRADER_POOL_NAMES.length
                            ? TRADER_POOL_NAMES[group] : "POOL " + group;
                    atlasText(graphics, groupLabel, cx + 7, iy + 8, ACCENT);
                } else {
                    hovered = drawTrade(graphics, row.trade(), cx, iy, colWidth, mx, my, hovered);
                }
            }
        }
        return hovered;
    }

    /**
     * Compact presentation of all color variants at an identical price. The
     * palette uses the real item icons: eight variants per line, never a static
     * image and never a lossy representative item.
     */
    private Hover drawGroupedTrade(GuiGraphicsExtractor graphics, TradeGroups.DisplayTrade display,
                                   int x, int y, int width, float mx, float my, Hover hovered) {
        int h = display.height();
        boolean over = mx >= x && mx <= x + width && my >= y && my < y + h;
        Ui.roundRect(graphics, x, y, width, h - 1, 4, over ? 0xFF35465A : 0xFF222F3D);
        graphics.fill(x, y + h - 2, x + width, y + h - 1, 0xFF2D3B49);
        var base = display.primary();
        int paletteHeight = 10 + ((display.variants().size() + display.columns() - 1)
                / display.columns()) * 24;
        int fixedY = y + (paletteHeight - 16) / 2;
        if (display.inputVariants()) {
            atlasText(graphics, "→", x + 145, fixedY + 6, ACCENT);
            hovered = drawItem(graphics, base.gives(), x + 177, fixedY,
                    base.glintPreview(), mx, my, hovered);
        } else {
            hovered = drawItem(graphics, base.wants(), x + 8, fixedY, false, mx, my, hovered);
            if (base.additionalWants() != null) {
                atlasText(graphics, "+", x + 29, fixedY + 3, MUTED);
                hovered = drawItem(graphics, base.additionalWants(), x + 41, fixedY,
                        false, mx, my, hovered);
                atlasText(graphics, "→", x + 68, fixedY + 2, ACCENT);
            } else {
                atlasText(graphics, "→", x + 54, fixedY + 2, ACCENT);
            }
        }
        int paletteX = display.inputVariants() ? x + 8 :
                (base.additionalWants() != null ? x + 90 : x + 78);
        for (int i = 0; i < display.variants().size(); i++) {
            var variant = display.variants().get(i);
            Ingredient ingredient = display.inputVariants() ? variant.wants() : variant.gives();
            hovered = drawItem(graphics, ingredient,
                    paletteX + (i % display.columns()) * 23,
                    y + 5 + (i / display.columns()) * 24,
                    false, mx, my, hovered);
        }
        if (display.biomeDependent()) {
            atlasText(graphics, "BIOME-DEPENDENT OPTIONS", x + 10, y + h - 12, MUTED);
        }
        return hovered;
    }

    private Hover drawTrade(GuiGraphicsExtractor graphics, TradeAtlas.Trade trade,
                            int x, int y, int width, float mx, float my, Hover hovered) {
        boolean over = mx >= x && mx <= x + width
                && my >= y && my <= y + AtlasLayout.ROW_HEIGHT - 1;
        Ui.roundRect(graphics, x, y, width, AtlasLayout.ROW_HEIGHT - 1,
                4, over ? 0xFF35465A : 0xFF222F3D);
        graphics.fill(x, y + AtlasLayout.ROW_HEIGHT - 2, x + width,
                y + AtlasLayout.ROW_HEIGHT - 1, 0xFF2D3B49);
        boolean wide = width >= 245;
        int arrow = wide ? x + 132 : x + 112;
        int output = wide ? x + 171 : x + 145;
        hovered = drawItem(graphics, trade.wants(), x + 10, y + 6, false, mx, my, hovered);
        if (trade.additionalWants() != null) {
            atlasText(graphics, "+", x + 49, y + 11, MUTED);
            hovered = drawItem(graphics, trade.additionalWants(), x + 64, y + 6,
                    false, mx, my, hovered);
        }
        atlasText(graphics, "→", arrow, y + 11, ACCENT);
        hovered = drawItem(graphics, trade.gives(), output, y + 6,
                trade.glintPreview(), mx, my, hovered);
        if (trade.glintPreview()) {
            atlasText(graphics, "✦", x + width - 17, y + 11, GOLD);
        } else if (trade.conditional() || trade.modified()) {
            atlasText(graphics, "·", x + width - 15, y + 11, MUTED);
        }
        return hovered;
    }

    private Hover drawItem(GuiGraphicsExtractor graphics, Ingredient ingredient, int x, int y,
                           boolean glint, float mx, float my, Hover hovered) {
        if (ingredient == null) return hovered;
        ItemStack stack = itemStack(ingredient, glint);
        if (stack.isEmpty()) {
            atlasText(graphics, "?", x + 6, y + 7, GOLD);
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(TRADE_ITEM_SCALE, TRADE_ITEM_SCALE);
            graphics.fakeItem(stack, 0, 0);
            graphics.pose().popMatrix();
            String amount = ingredient.amount();
            if (amount != null && !amount.equals("1")) {
                // Vanilla Minecraft's regular item-stack count font and shadow.
                // UI labels still use Inter; item quantities intentionally do not.
                graphics.text(font, amount, x + 21 - font.width(amount), y + 15, TEXT, true);
            }
        }
        return mx >= x && mx < x + 22 && my >= y && my < y + 22
                ? new Hover(ingredient, glint) : hovered;
    }

    private ItemStack itemStack(Ingredient ingredient, boolean glint) {
        if (ingredient == null || ingredient.id() == null || ingredient.id().isBlank())
            return ItemStack.EMPTY;
        String key = ingredient.id() + (glint ? "#glint" : "");
        return items.computeIfAbsent(key, ignored -> {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(ingredient.id()));
                if (item == null || item == Items.AIR) return ItemStack.EMPTY;
                ItemStack stack = new ItemStack(item);
                if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                return stack;
            } catch (RuntimeException ex) {
                return ItemStack.EMPTY;
            }
        });
    }

    private void drawPreview(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile) {
        // The preview is culled using SCREEN coordinates, not local canvas coordinates.
        float sx = panX + (tile.x() + 9) * zoom;
        float sy = panY + (tile.y() + 62) * zoom;
        float ex = panX + (tile.x() + 136) * zoom;
        float ey = panY + (tile.y() + 209) * zoom;
        if (ex < 0 || sx > width || ey < HEADER || sy > height - FOOTER) return;
        LivingEntity entity = model(tile.profession().id());
        if (entity == null) return;
        int size = Math.max(1, Math.round(55 * zoom));
        // The preview helper uses *absolute pixel deltas* from the supplied
        // cursor position to calculate its angle. Scaling those deltas with zoom
        // caused the villagers to rotate whenever the user zoomed the atlas.
        // Always aim at the preview center for a stable straight-ahead view.
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                Math.round(sx), Math.round(sy), Math.round(ex), Math.round(ey),
                size, 0f, (sx + ex) * 0.5f, (sy + ey) * 0.5f, entity);
    }

    private LivingEntity model(String professionId) {
        if (minecraft.level == null) return null;
        return models.computeIfAbsent(professionId, id -> {
            try {
                LivingEntity entity;
                if (id.equals("minecraft:wandering_trader")) {
                    entity = EntityTypes.WANDERING_TRADER.create(minecraft.level, EntitySpawnReason.COMMAND);
                } else {
                    Villager villager = EntityTypes.VILLAGER.create(minecraft.level, EntitySpawnReason.COMMAND);
                    if (villager != null) {
                        villager.setVillagerData(villager.getVillagerData().withProfession(
                                minecraft.level.registryAccess(),
                                ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.parse(id))));
                    }
                    entity = villager;
                }
                if (entity != null) entity.setId(nextPreviewEntityId--);
                return entity;
            } catch (RuntimeException ex) {
                return null;
            }
        });
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (section != GuideSection.TRADING || event.y() < HEADER || event.y() >= height - FOOTER)
            return super.mouseDragged(event, dragX, dragY);
        panX += (float) dragX;
        panY += (float) dragY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (section != GuideSection.TRADING || mouseY < HEADER || mouseY >= height - FOOTER)
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        float next = Math.clamp(zoom * (float) Math.pow(1.12, deltaY), 0.22f, 2.5f);
        panX = (float) mouseX - ((float) mouseX - panX) * next / zoom;
        panY = (float) mouseY - ((float) mouseY - panY) * next / zoom;
        zoom = next;
        return true;
    }
}
