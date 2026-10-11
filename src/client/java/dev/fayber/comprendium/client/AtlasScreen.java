package dev.fayber.comprendium.client;

import dev.fayber.comprendium.data.AtlasLayout;
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
    private static final int HEADER = 59;
    private static final int FOOTER = 27;
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

    private float zoom = 0.85f;
    private float panX, panY;
    private boolean positioned;
    private final Map<String, LivingEntity> models = new HashMap<>();
    private final Map<String, ItemStack> items = new HashMap<>();
    private int nextPreviewEntityId = -1;
    private TradeAtlas lastAtlas;
    private AtlasLayout.Layout layout;
    private record Hover(Ingredient ingredient, boolean glintPreview) {}
    private record TraderRow(int pool, TradeAtlas.Trade trade) {}

    public AtlasScreen() {
        super(Component.literal("Comprendium"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        TradeAtlas atlas = ComprendiumClient.atlas();
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

        // Fixed chrome is drawn after the transformed atlas.
        graphics.fill(0, 0, width, HEADER, 0xFF111A24);
        graphics.fill(0, HEADER - 1, width, HEADER, BORDER);
        graphics.fill(0, 0, 4, HEADER - 1, ACCENT);
        graphics.text(font, "COMPRENDIUM", 20, 12, TEXT);
        graphics.text(font, "REFERENCE  /  VILLAGER TRADES", 20, 32, MUTED);
        String stats = atlas.professions().size() + " TRADERS    " + Math.round(zoom * 100) + "%";
        graphics.text(font, stats, Math.max(220, width - font.width(stats) - 20), 23, ACCENT);

        graphics.fill(0, height - FOOTER, width, height, 0xFF111A24);
        graphics.fill(0, height - FOOTER, width, height - FOOTER + 1, BORDER);
        graphics.text(font, "DRAG TO PAN     •     SCROLL TO ZOOM     •     ESC TO CLOSE",
                18, height - FOOTER + 9, MUTED);
        String note = "✦  Enchanted output preview";
        graphics.text(font, note, Math.max(440, width - font.width(note) - 18),
                height - FOOTER + 9, GOLD);
        if (hovered != null) {
            var stack = itemStack(hovered.ingredient(), hovered.glintPreview());
            if (!stack.isEmpty()) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private boolean visible(AtlasLayout.Tile tile) {
        float left = panX + tile.x() * zoom;
        float right = left + tile.width() * zoom;
        float top = panY + tile.y() * zoom;
        float bottom = top + tile.height() * zoom;
        return right > 0 && left < width && bottom > HEADER && top < height - FOOTER;
    }

    private void panel(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile) {
        int x = tile.x(), y = tile.y(), w = tile.width(), h = tile.height();
        graphics.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x77000000);
        graphics.fill(x, y, x + w, y + h, BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, SURFACE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + AtlasLayout.CARD_HEADER - 6, SURFACE_TOP);
        graphics.fill(x + 1, y + 1, x + 49, y + 3, ACCENT);
    }

    private Hover drawVillager(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile,
                               float mx, float my, Hover hovered) {
        panel(graphics, tile);
        var p = tile.profession();
        int x = tile.x(), y = tile.y(), w = tile.width();
        graphics.text(font, p.name(), x + 13, y + 14, TEXT);
        String id = p.id().startsWith("minecraft:") ? "VILLAGER  •  VANILLA" : p.id();
        graphics.text(font, id, x + 13, y + 31, MUTED);
        graphics.fill(x + 133, y + 63, x + 220, y + 111, 0xFF182430);
        graphics.text(font, "JOB SITE", x + 146, y + 70, MUTED);
        ItemStack workstation = itemStack(new Ingredient(p.workstation(), 1, "1", ""), false);
        if (!workstation.isEmpty()) graphics.fakeItem(workstation, x + 164, y + 86);
        else graphics.text(font, "—", x + 172, y + 91, MUTED);
        graphics.fill(x + 12, y + 119, x + w - 12, y + 120, BORDER);
        int cursorY = y + AtlasLayout.CARD_HEADER;
        for (var level : p.levels()) {
            int tier = Math.clamp(level.level(), 0, 5);
            graphics.fill(x + 9, cursorY, x + w - 9, cursorY + AtlasLayout.TIER_HEIGHT - 3, 0xFF2B3948);
            String name = tier == 0 ? "LEVEL " + level.level() : LEVEL_NAMES[tier];
            graphics.text(font, name, x + 16, cursorY + 6, LEVEL_COLORS[tier]);
            String count = Integer.toString(level.trades().size());
            graphics.text(font, count, x + w - font.width(count) - 17, cursorY + 6, MUTED);
            cursorY += AtlasLayout.TIER_HEIGHT;
            for (var trade : level.trades()) {
                hovered = drawTrade(graphics, trade, x + 9, cursorY, w - 18, mx, my, hovered);
                cursorY += AtlasLayout.ROW_HEIGHT;
            }
        }
        return hovered;
    }

    private Hover drawTrader(GuiGraphicsExtractor graphics, AtlasLayout.Tile tile,
                             float mx, float my, Hover hovered) {
        panel(graphics, tile);
        int x = tile.x(), y = tile.y(), w = tile.width();
        graphics.text(font, "Wandering Trader", x + 15, y + 14, TEXT);
        graphics.text(font, "SPECIAL TRADER  /  NO WORKSTATION", x + 15, y + 31, MUTED);
        graphics.text(font, "BUYING  •  COMMON  •  UNCOMMON   /   RANDOM POSSIBLE OFFERS",
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
            graphics.text(font, name, cx + 7, y + AtlasLayout.CARD_HEADER + 5, ACCENT);
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
                    graphics.text(font, groupLabel, cx + 7, iy + 8, ACCENT);
                } else {
                    hovered = drawTrade(graphics, row.trade(), cx, iy, colWidth, mx, my, hovered);
                }
            }
        }
        return hovered;
    }

    private Hover drawTrade(GuiGraphicsExtractor graphics, TradeAtlas.Trade trade,
                            int x, int y, int width, float mx, float my, Hover hovered) {
        boolean over = mx >= x && mx <= x + width
                && my >= y && my <= y + AtlasLayout.ROW_HEIGHT - 1;
        graphics.fill(x, y, x + width, y + AtlasLayout.ROW_HEIGHT - 1,
                over ? 0xFF35465A : 0xFF222F3D);
        graphics.fill(x, y + AtlasLayout.ROW_HEIGHT - 2, x + width,
                y + AtlasLayout.ROW_HEIGHT - 1, 0xFF2D3B49);
        boolean wide = width >= 245;
        int arrow = wide ? x + 132 : x + 112;
        int output = wide ? x + 171 : x + 145;
        hovered = drawItem(graphics, trade.wants(), x + 10, y + 4, false, mx, my, hovered);
        if (trade.additionalWants() != null) {
            graphics.text(font, "+", x + 51, y + 9, MUTED);
            hovered = drawItem(graphics, trade.additionalWants(), x + 65, y + 4,
                    false, mx, my, hovered);
        }
        graphics.text(font, "→", arrow, y + 8, ACCENT);
        hovered = drawItem(graphics, trade.gives(), output, y + 4,
                trade.glintPreview(), mx, my, hovered);
        if (trade.glintPreview()) {
            graphics.text(font, "✦", x + width - 17, y + 8, GOLD);
        } else if (trade.conditional() || trade.modified()) {
            graphics.text(font, "·", x + width - 15, y + 8, MUTED);
        }
        return hovered;
    }

    private Hover drawItem(GuiGraphicsExtractor graphics, Ingredient ingredient, int x, int y,
                           boolean glint, float mx, float my, Hover hovered) {
        if (ingredient == null) return hovered;
        ItemStack stack = itemStack(ingredient, glint);
        if (stack.isEmpty()) {
            graphics.text(font, "?", x + 4, y + 5, GOLD);
        } else {
            graphics.fakeItem(stack, x, y);
            String amount = ingredient.amount();
            if (amount != null && !amount.equals("1")) {
                graphics.text(font, amount, x + 16 - font.width(amount), y + 12, TEXT);
            }
        }
        return mx >= x && mx < x + 18 && my >= y && my < y + 18
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
        float sx = panX + (tile.x() + 17) * zoom;
        float sy = panY + (tile.y() + 49) * zoom;
        float ex = panX + (tile.x() + 128) * zoom;
        float ey = panY + (tile.y() + 115) * zoom;
        if (ex < 0 || sx > width || ey < HEADER || sy > height - FOOTER) return;
        LivingEntity entity = model(tile.profession().id());
        if (entity == null) return;
        int size = Math.max(1, Math.round(31 * zoom));
        // Use the stable 26.3 API with a fixed synthetic mouse target, so
        // models neither rotate with the cursor nor drift relative to the card.
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics,
                Math.round(sx), Math.round(sy), Math.round(ex), Math.round(ey),
                size, 0f, (sx + ex) * 0.5f + 18f * zoom,
                (sy + ey) * 0.5f - 10f * zoom, entity);
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
        panX += (float) dragX;
        panY += (float) dragY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        float next = Math.clamp(zoom * (float) Math.pow(1.12, deltaY), 0.22f, 2.5f);
        panX = (float) mouseX - ((float) mouseX - panX) * next / zoom;
        panY = (float) mouseY - ((float) mouseY - panY) * next / zoom;
        zoom = next;
        return true;
    }
}
