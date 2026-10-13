package com.jvn.villagerretaliation.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreNodeCatalogPayload(List<ResourceLocation> blocks) implements CustomPacketPayload {
    public static final Type<OreNodeCatalogPayload> TYPE = VillagerPayloads.type("ore_node_catalog");
    public static final StreamCodec<RegistryFriendlyByteBuf, OreNodeCatalogPayload> STREAM_CODEC =
            VillagerPayloads.codec(OreNodeCatalogPayload::encode, OreNodeCatalogPayload::decode);

    public OreNodeCatalogPayload {
        blocks = List.copyOf(blocks);
        if (blocks.size() > 65536) throw new IllegalArgumentException("Too many ore blocks");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OreNodeCatalogPayload payload) {
        buffer.writeVarInt(payload.blocks().size());
        payload.blocks().forEach(buffer::writeResourceLocation);
    }

    private static OreNodeCatalogPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, 65536, "ore blocks");
        var blocks = new java.util.ArrayList<ResourceLocation>(size);
        for (int i = 0; i < size; i++) blocks.add(buffer.readResourceLocation());
        return new OreNodeCatalogPayload(blocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
