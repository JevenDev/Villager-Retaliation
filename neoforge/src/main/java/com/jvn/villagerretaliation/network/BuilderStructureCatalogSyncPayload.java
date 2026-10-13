package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
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
            buffer.writeUtf(entry.houseSize(), 16);
            buffer.writeEnum(entry.constructionCost().mode());
            buffer.writeVarInt(entry.constructionCost().ingredients().size());
            for (var ingredient : entry.constructionCost().ingredients()) {
                buffer.writeResourceLocation(ingredient.id());
                buffer.writeBoolean(ingredient.tag());
                buffer.writeVarInt(ingredient.count());
            }
        }
    }

    private static BuilderStructureCatalogSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "builder structure catalog entries");
        java.util.ArrayList<BuilderStructureCatalog.Entry> entries = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation id = buffer.readResourceLocation();
            String category = buffer.readUtf(96), label = buffer.readUtf(128);
            int baseCost = buffer.readVarInt();
            String houseSize = buffer.readUtf(16);
            var mode = buffer.readEnum(BuilderConstructionCost.Mode.class);
            int count = VillagerPayloads.readCollectionSize(buffer, BuilderConstructionCost.MAX_INGREDIENTS, "construction ingredients");
            var ingredients = new java.util.ArrayList<BuilderConstructionCost.Ingredient>(count);
            for (int material = 0; material < count; material++) {
                ingredients.add(new BuilderConstructionCost.Ingredient(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readVarInt()));
            }
            BuilderStructureCatalog.Entry entry = new BuilderStructureCatalog.Entry(id, category, label, baseCost,
                    new BuilderConstructionCost(mode, ingredients), houseSize);
            entries.add(entry);
        }
        return new BuilderStructureCatalogSyncPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
