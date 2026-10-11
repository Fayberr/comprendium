package dev.fayber.comprendium.client;

import dev.fayber.comprendium.Comprendium;
import dev.fayber.comprendium.data.TradeAtlas;
import dev.fayber.comprendium.net.AtlasPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ComprendiumClient implements ClientModInitializer {
    private static TradeAtlas current = TradeAtlas.empty("Waiting for synchronized trade definitions");
    private static KeyMapping openKey;

    public static TradeAtlas atlas() { return current; }

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.comprendium.open", GLFW.GLFW_KEY_J,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("comprendium", "general"))));
        ClientPlayNetworking.registerGlobalReceiver(AtlasPayload.TYPE, (payload, context) -> {
            try {
                TradeAtlas parsed = TradeAtlas.parse(payload.json());
                context.client().execute(() -> {
                    current = parsed;
                    Comprendium.LOG.info("Loaded {} professions from {}", parsed.professions().size(), parsed.source());
                });
            } catch (RuntimeException ex) {
                Comprendium.LOG.warn("Rejected malformed trade atlas packet", ex);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                current = TradeAtlas.empty("Server does not provide Comprendium trade data"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.level != null) client.setScreen(new AtlasScreen());
            }
        });
    }
}
