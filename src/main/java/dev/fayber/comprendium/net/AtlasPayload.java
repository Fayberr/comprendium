package dev.fayber.comprendium.net;

import dev.fayber.comprendium.data.TradeAtlas;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AtlasPayload(String json) implements CustomPacketPayload {
    public static final Type<AtlasPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("comprendium", "atlas_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasPayload> CODEC =
            StreamCodec.of((buf, value) -> buf.writeUtf(value.json(), TradeAtlas.MAX_JSON_CHARS),
                    buf -> new AtlasPayload(buf.readUtf(TradeAtlas.MAX_JSON_CHARS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
