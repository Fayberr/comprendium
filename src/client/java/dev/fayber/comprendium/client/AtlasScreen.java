package dev.fayber.comprendium.client;

import dev.fayber.comprendium.data.TradeAtlas;
import dev.fayber.comprendium.data.TradeAtlas.Ingredient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.Map;

/**
 * A spatial canvas, not a captured texture. Trade and entity data are separated
 * from presentation so additional charts can use the same viewport in future.
 */
public final class AtlasScreen extends Screen {
    private static final int CARD_WIDTH = 232;
    private static final int CARD_GAP = 20;
    private static final int HEADER_HEIGHT = 168;
    private static final int ROW_HEIGHT = 26;
    private static final int TITLE_HEIGHT = 43;
    private static final int FOOTER_HEIGHT = 21;
    private static final String[] LEVELS = {"", "NOVICE", "APPRENTICE", "JOURNEYMAN", "EXPERT", "MASTER"};
    private static final int BG = 0xFF101820;
    private static final int PANEL = 0xFF1C2935;
    private static final int ROW = 0xFF263643;
    private static final int TEXT = 0xFFF0F4F5;
    private static final int MUTED = 0xFFA1B3C2;
    private static final int ACCENT = 0xFF87CE9F;

    private float zoom = 0.9f;
    private float panX = 28;
    private float panY = TITLE_HEIGHT + 16;
    private final Map<String, Villager> models = new HashMap<>();
    private final Map<String, ItemStack> items = new HashMap<>();
    private TradeAtlas snapshot;

    public AtlasScreen() {
        super(Component.literal("Comprendium"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Draw our own background and overlays, without a vanilla inventory texture.
        graphics.fill(0, 0, width, height, BG);
        snapshot = ComprendiumClient.atlas();
        graphics.fill(0, 0, width, TITLE_HEIGHT, 0xFF192630);
        graphics.fill(0, TITLE_HEIGHT - 2, width, TITLE_HEIGHT, ACCENT);
        graphics.text(font, "COMPRENDIUM", 16, 10, TEXT);
        graphics.text(font, "VILLAGER TRADE ATLAS", 16, 26, MUTED);
        graphics.text(font, snapshot.professions().size() + " PROFESSIONS  •  " +
                Math.round(zoom * 100) + "%", Math.max(190, width - 155), 17, ACCENT);

        graphics.enableScissor(0, TITLE_HEIGHT, width, height - FOOTER_HEIGHT);
        graphics.pose().pushMatrix();
        graphics.pose().translate(panX, panY);
        graphics.pose().scale(zoom, zoom);

        float cursorX = (mouseX - panX) / zoom;
        float cursorY = (mouseY - panY) / zoom;
        Ingredient hovered = null;

        if (snapshot.professions().isEmpty()) {
            graphics.text(font, "No synchronized trade definitions available.", 24, 32, TEXT);
            graphics.text(font, snapshot.source(), 24, 54, MUTED);
            graphics.text(font, "Singleplayer or servers with Comprendium can provide live trades.", 24, 76, MUTED);
        } else {
            for (int i = 0; i < snapshot.professions().size(); i++) {
                TradeAtlas.Profession p = snapshot.professions().get(i);
                int x = i * (CARD_WIDTH + CARD_GAP);
                if ((x + CARD_WIDTH) * zoom + panX < 0 || x * zoom + panX > width) continue;
                hovered = drawProfession(graphics, p, x, cursorX, cursorY, hovered);
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0xFF192630);
        graphics.text(font, "DRAG  Move  |  SCROLL  Zoom  |  ESC  Close", 12, height - 16, MUTED);
        graphics.text(font, "26.3  •  Fabric", width - 94, height - 16, MUTED);

        if (hovered != null) {
            ItemStack hoveredStack = itemStack(hovered);
            if (!hoveredStack.isEmpty()) graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Ingredient drawProfession(GuiGraphicsExtractor graphics, TradeAtlas.Profession profession,
                                      int x, float mouseX, float mouseY, Ingredient hovered) {
        int totalRows = 0;
        for (TradeAtlas.Level level : profession.levels()) totalRows += 1 + level.trades().size();
        int height = HEADER_HEIGHT + totalRows * ROW_HEIGHT + 14;
        graphics.fill(x, 0, x + CARD_WIDTH, height, PANEL);
        graphics.fill(x, 0, x + CARD_WIDTH, 4, ACCENT);
        graphics.text(font, profession.name(), x + 12, 13, TEXT);
        graphics.text(font, profession.id(), x + 12, 28, MUTED);
        Villager villager = model(profession.id());
        if (villager != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + 8, 39, x + 116, 141,
                    35, 0f, mouseX, mouseY, villager);
        }
        graphics.text(font, "WORKSTATION", x + 126, 69, MUTED);
        ItemStack workstation = itemStack(new Ingredient(profession.workstation(), 1, "1", ""));
        if (!workstation.isEmpty()) graphics.fakeItem(workstation, x + 145, 88);
        else graphics.text(font, "—", x + 148, 94, MUTED);
        graphics.fill(x + 10, 148, x + CARD_WIDTH - 10, 149, 0xFF486171);

        int y = HEADER_HEIGHT;
        for (TradeAtlas.Level level : profession.levels()) {
            graphics.fill(x + 7, y - 3, x + CARD_WIDTH - 7, y + 18, 0xFF334754);
            String label = level.level() < LEVELS.length ? LEVELS[level.level()] : "LEVEL " + level.level();
            graphics.text(font, label + "  (" + level.trades().size() + ")", x + 14, y + 3, ACCENT);
            y += ROW_HEIGHT;
            for (TradeAtlas.Trade trade : level.trades()) {
                int rowTop = y - 3;
                boolean over = mouseX >= x + 8 && mouseX < x + CARD_WIDTH - 8
                        && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
                graphics.fill(x + 7, rowTop, x + CARD_WIDTH - 7, rowTop + ROW_HEIGHT - 1,
                        over ? 0xFF3D5260 : ROW);
                hovered = drawIngredient(graphics, trade.wants(), x + 17, rowTop + 4, mouseX, mouseY, hovered);
                if (trade.additionalWants() != null) {
                    graphics.text(font, "+", x + 50, rowTop + 8, MUTED);
                    hovered = drawIngredient(graphics, trade.additionalWants(), x + 62, rowTop + 4,
                            mouseX, mouseY, hovered);
                }
                graphics.text(font, "→", x + 115, rowTop + 7, ACCENT);
                hovered = drawIngredient(graphics, trade.gives(), x + 143, rowTop + 4, mouseX, mouseY, hovered);
                if (trade.conditional() || trade.modified()) {
                    graphics.text(font, "*", x + 205, rowTop + 6, 0xFFF2C879);
                }
                y += ROW_HEIGHT;
            }
        }
        return hovered;
    }

    private Ingredient drawIngredient(GuiGraphicsExtractor graphics, Ingredient ingredient, int x, int y,
                                      float mouseX, float mouseY, Ingredient hovered) {
        if (ingredient == null) return hovered;
        ItemStack stack = itemStack(ingredient);
        if (stack.isEmpty()) {
            graphics.text(font, "?", x + 4, y + 4, 0xFFE3A76B);
        } else {
            graphics.fakeItem(stack, x, y);
            String amount = ingredient.amount();
            if (amount != null && !amount.equals("1")) {
                graphics.text(font, amount, x + 16 - font.width(amount), y + 11, TEXT);
            }
        }
        if (mouseX >= x && mouseX < x + 19 && mouseY >= y && mouseY < y + 19) return ingredient;
        return hovered;
    }

    private ItemStack itemStack(Ingredient ingredient) {
        if (ingredient == null || ingredient.id() == null || ingredient.id().isBlank()) return ItemStack.EMPTY;
        return items.computeIfAbsent(ingredient.id(), key -> {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
                return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            } catch (RuntimeException ex) {
                return ItemStack.EMPTY;
            }
        });
    }

    private Villager model(String professionId) {
        if (minecraft.level == null) return null;
        return models.computeIfAbsent(professionId, id -> {
            try {
                Villager entity = EntityTypes.VILLAGER.create(minecraft.level, EntitySpawnReason.COMMAND);
                if (entity != null) entity.setVillagerData(entity.getVillagerData().withProfession(
                        minecraft.level.registryAccess(),
                        ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.parse(id))));
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
        float factor = (float) Math.pow(1.12, deltaY);
        float next = Math.clamp(zoom * factor, 0.35f, 2.3f);
        panX = (float) mouseX - ((float) mouseX - panX) * next / zoom;
        panY = (float) mouseY - ((float) mouseY - panY) * next / zoom;
        zoom = next;
        return true;
    }
}
