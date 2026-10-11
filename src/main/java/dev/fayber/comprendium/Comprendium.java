package dev.fayber.comprendium;

import dev.fayber.comprendium.data.AtlasBuilder;
import dev.fayber.comprendium.net.AtlasPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Comprendium implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Comprendium");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(AtlasPayload.TYPE, AtlasPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> send(server, handler.getPlayer())));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) server.execute(() ->
                    server.getPlayerList().getPlayers().forEach(player -> send(server, player)));
        });
    }

    private static void send(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, AtlasPayload.TYPE)) return;
        try {
            ServerPlayNetworking.send(player, new AtlasPayload(AtlasBuilder.build(server).toJson()));
        } catch (Exception e) {
            LOG.warn("Couldn't send atlas to {}", player.getName().getString(), e);
        }
    }
}
