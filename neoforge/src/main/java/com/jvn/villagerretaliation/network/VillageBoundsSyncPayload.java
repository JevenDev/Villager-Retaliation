package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillageBoundsSyncPayload(
        boolean enabled,
        ResourceLocation dimension,
        List<VillageEntry> villages,
        int visibleTicks) implements CustomPacketPayload {
    public static final int MAX_VILLAGES = 64;
    public static final int MAX_SECTIONS_PER_VILLAGE = 512;
    public static final int MAX_TOTAL_SECTIONS = 4096;
    private static final int NAME_LIMIT = 64;
    public static final Type<VillageBoundsSyncPayload> TYPE = VillagerPayloads.type("village_bounds_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageBoundsSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageBoundsSyncPayload::encode, VillageBoundsSyncPayload::decode);

    public VillageBoundsSyncPayload {
        villages = villages == null ? List.of() : List.copyOf(villages.stream().limit(MAX_VILLAGES).toList());
        visibleTicks = Math.max(0, visibleTicks);
    }

    public static VillageBoundsSyncPayload disabled(ResourceLocation dimension) {
        return new VillageBoundsSyncPayload(false, dimension, List.of(), 0);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillageBoundsSyncPayload payload) {
        buffer.writeBoolean(payload.enabled());
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeVarInt(Math.min(MAX_VILLAGES, payload.villages().size()));
        int totalSections = 0;
        for (VillageEntry entry : payload.villages()) {
            if (totalSections + entry.sections().size() > MAX_TOTAL_SECTIONS) {
                throw new IllegalArgumentException("Village bounds payload exceeds total section limit");
            }
            buffer.writeUUID(entry.id().value());
            buffer.writeUtf(entry.name(), NAME_LIMIT);
            buffer.writeBlockPos(entry.center());
            buffer.writeEnum(entry.lifecycle());
            buffer.writeVarInt(entry.sections().size());
            for (long section : entry.sections()) {
                buffer.writeLong(section);
            }
            totalSections += entry.sections().size();
        }
        buffer.writeVarInt(payload.visibleTicks());
    }

    private static VillageBoundsSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = VillagerPayloads.readCollectionSize(buffer, MAX_VILLAGES, "village bounds entries");
        List<VillageEntry> villages = new ArrayList<>(count);
        int totalSections = 0;
        for (int index = 0; index < count; index++) {
            VillageAllegianceId id = new VillageAllegianceId(buffer.readUUID());
            String name = buffer.readUtf(NAME_LIMIT);
            BlockPos center = buffer.readBlockPos();
            VillageLifecycleState lifecycle = buffer.readEnum(VillageLifecycleState.class);
            int sectionCount = VillagerPayloads.readCollectionSize(
                    buffer, MAX_SECTIONS_PER_VILLAGE, "village footprint sections");
            totalSections += sectionCount;
            if (totalSections > MAX_TOTAL_SECTIONS) {
                throw new IllegalArgumentException("Village bounds payload exceeds total section limit");
            }
            List<Long> sections = new ArrayList<>(sectionCount);
            for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
                sections.add(buffer.readLong());
            }
            villages.add(new VillageEntry(id, name, center, lifecycle, sections));
        }
        return new VillageBoundsSyncPayload(enabled, dimension, villages, buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record VillageEntry(
            VillageAllegianceId id,
            String name,
            BlockPos center,
            VillageLifecycleState lifecycle,
            List<Long> sections) {
        public VillageEntry {
            name = name == null ? "" : name.strip();
            if (name.length() > NAME_LIMIT) {
                name = name.substring(0, NAME_LIMIT);
            }
            center = center == null ? BlockPos.ZERO : center.immutable();
            lifecycle = lifecycle == null ? VillageLifecycleState.ACTIVE : lifecycle;
            sections = sections == null ? List.of() : List.copyOf(sections.stream()
                    .distinct().limit(MAX_SECTIONS_PER_VILLAGE).toList());
        }
    }
}
