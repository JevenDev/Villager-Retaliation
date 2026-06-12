package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.BuilderStructureCatalog;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BuilderStructureCatalogSyncPayload(List<BuilderStructureCatalog.Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;
    public static final Type<BuilderStructureCatalogSyncPayload> TYPE = VillagerPayloads.type("builder_structure_catalog_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderStructureCatalogSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(BuilderStructureCatalogSyncPayload::encode, BuilderStructureCatalogSyncPayload::decode);

    public BuilderStructureCatalogSyncPayload {
        entries = entries == null
                ? List.of()
                : List.copyOf(entries.stream().filter(Objects::nonNull).limit(MAX_ENTRIES).toList());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BuilderStructureCatalogSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (BuilderStructureCatalog.Entry entry : payload.entries()) {
            buffer.writeResourceLocation(entry.id());
            buffer.writeUtf(entry.category(), 96);
            buffer.writeUtf(entry.label(), 128);
            buffer.writeVarInt(entry.baseCost());
        }
    }

    private static BuilderStructureCatalogSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        java.util.ArrayList<BuilderStructureCatalog.Entry> entries = new java.util.ArrayList<>(Math.min(MAX_ENTRIES, size));
        for (int index = 0; index < size; index++) {
            ResourceLocation id = buffer.readResourceLocation();
            BuilderStructureCatalog.Entry entry = new BuilderStructureCatalog.Entry(
                    id,
                    buffer.readUtf(96),
                    buffer.readUtf(128),
                    buffer.readVarInt());
            if (entries.size() < MAX_ENTRIES) {
                entries.add(entry);
            }
        }
        return new BuilderStructureCatalogSyncPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
