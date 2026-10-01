package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProductionSnapshotPayload(ResourceLocation dimension, ProductionTarget target, UUID request, boolean applied,
        HiredVillagerRole role, String title, boolean editable, String blocker, String feedback, CompoundTag queue,
        int legacyCount, List<Legacy> legacy, List<Storage> storages, Map<UUID, Long> stock, List<com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement> requirements,
        List<SettlementPolicyOrderLink> managed) implements CustomPacketPayload {
    public static final Type<ProductionSnapshotPayload> TYPE = VillagerPayloads.type("production_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionSnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(ProductionSnapshotPayload::encode, ProductionSnapshotPayload::decode);

    public ProductionSnapshotPayload {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(target);
        Objects.requireNonNull(request);
        if (title == null || title.length() > 128 || blocker == null || blocker.length() > 256
                || feedback == null || feedback.length() > 256 || legacyCount < 0 || legacyCount > 4096) {
            throw new IllegalArgumentException("Invalid production snapshot");
        }
        queue = queue == null ? null : queue.copy();
        legacy = List.copyOf(legacy);
        storages = List.copyOf(storages);
        stock = Map.copyOf(stock);
        requirements = List.copyOf(requirements);
        managed = SettlementPolicyOrderLink.validate(target, managed);
        if (legacy.size() > 32 || legacyCount < legacy.size() || storages.size() > 128 || stock.size() > 32 || requirements.size() > 544
                || stock.values().stream().anyMatch(count -> count < -1L)) throw new IllegalArgumentException("Oversized production snapshot");
    }

    public ProductionSnapshotPayload(ResourceLocation dimension, ProductionTarget target, UUID request, boolean applied,
            HiredVillagerRole role, String title, boolean editable, String blocker, String feedback, CompoundTag queue,
            int legacyCount, List<Legacy> legacy, List<Storage> storages, Map<UUID, Long> stock) {
        this(dimension, target, request, applied, role, title, editable, blocker, feedback, queue, legacyCount, legacy, storages, stock, List.of());
    }

    public ProductionSnapshotPayload(ResourceLocation dimension, ProductionTarget target, UUID request, boolean applied,
            HiredVillagerRole role, String title, boolean editable, String blocker, String feedback, CompoundTag queue,
            int legacyCount, List<Legacy> legacy, List<Storage> storages, Map<UUID, Long> stock,
            List<com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement> requirements) {
        this(dimension, target, request, applied, role, title, editable, blocker, feedback, queue, legacyCount, legacy,
                storages, stock, requirements, List.of());
    }

    @Override
    public CompoundTag queue() {
        return this.queue == null ? null : this.queue.copy();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProductionSnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        payload.target().write(buffer);
        buffer.writeUUID(payload.request());
        buffer.writeBoolean(payload.applied());
        buffer.writeBoolean(payload.role() != null);
        if (payload.role() != null) buffer.writeEnum(payload.role());
        buffer.writeUtf(payload.title(), 128);
        buffer.writeBoolean(payload.editable());
        buffer.writeUtf(payload.blocker(), 256);
        buffer.writeUtf(payload.feedback(), 256);
        buffer.writeNbt(payload.queue());
        buffer.writeVarInt(payload.legacyCount());
        buffer.writeVarInt(payload.legacy().size());
        for (var legacy : payload.legacy()) {
            buffer.writeUUID(legacy.worker());
            ProductionRequestPayload.writeRecipe(buffer, legacy.recipe());
            buffer.writeVarInt(legacy.amount());
            buffer.writeBoolean(legacy.continuous());
            buffer.writeVarInt(legacy.activeBottles());
        }
        buffer.writeVarInt(payload.storages().size());
        for (var storage : payload.storages()) {
            buffer.writeBlockPos(storage.position());
            buffer.writeUtf(storage.name(), 128);
            buffer.writeBoolean(storage.available());
        }
        buffer.writeVarInt(payload.stock().size());
        payload.stock().forEach((id, count) -> {
            buffer.writeUUID(id);
            buffer.writeVarLong(count);
        });
        buffer.writeVarInt(payload.requirements().size());
        for (var need : payload.requirements()) {
            buffer.writeUUID(need.id());
            buffer.writeUUID(need.order());
            buffer.writeUtf(need.name(), 128);
            buffer.writeEnum(need.unit());
            buffer.writeLong(need.required());
            buffer.writeLong(need.supplied());
            buffer.writeLong(need.missing());
            buffer.writeLong(need.availableNow());
            buffer.writeLong(need.committed());
            buffer.writeLong(need.incoming());
            writeAllocation(buffer, need.allocation());
        }
        SettlementPolicyOrderLink.write(buffer, payload.managed());
    }

    private static ProductionSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var target = ProductionTarget.read(buffer);
        var request = buffer.readUUID();
        boolean applied = buffer.readBoolean();
        var role = buffer.readBoolean() ? buffer.readEnum(HiredVillagerRole.class) : null;
        String title = buffer.readUtf(128);
        boolean editable = buffer.readBoolean();
        String blocker = buffer.readUtf(256);
        String feedback = buffer.readUtf(256);
        var queue = buffer.readNbt();
        if (queue != null) {
            var parsed = HiredProductionQueue.load(queue, buffer.registryAccess(), ignored -> {});
            if (parsed.role() != role || parsed.scope() != (target.town() == null ? HiredProductionQueue.Scope.PERSONAL : HiredProductionQueue.Scope.WORKSITE)
                    || target.town() != null && !parsed.owner().equals(target.subject())) throw new IllegalArgumentException("Mismatched production snapshot");
        }
        int legacyCount = buffer.readVarInt();
        int legacySize = VillagerPayloads.readCollectionSize(buffer, 32, "Legacy orders");
        var legacy = new ArrayList<Legacy>();
        for (int index = 0; index < legacySize; index++) legacy.add(new Legacy(buffer.readUUID(), ProductionRequestPayload.readRecipe(buffer),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt()));
        int storageSize = VillagerPayloads.readCollectionSize(buffer, 128, "Production storage");
        var storages = new ArrayList<Storage>();
        for (int index = 0; index < storageSize; index++) storages.add(new Storage(buffer.readBlockPos(), buffer.readUtf(128), buffer.readBoolean()));
        int stockSize = VillagerPayloads.readCollectionSize(buffer, 32, "Production stock");
        var stock = new LinkedHashMap<UUID, Long>();
        for (int index = 0; index < stockSize; index++) {
            if (stock.putIfAbsent(buffer.readUUID(), buffer.readVarLong()) != null) throw new IllegalArgumentException("Duplicate production stock");
        }
        var requirements = new ArrayList<com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement>();
        int size = VillagerPayloads.readCollectionSize(buffer, 544, "Production requirements");
        for (int index = 0; index < size; index++) requirements.add(new com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand.Requirement(
                buffer.readUUID(), buffer.readUUID(), buffer.readUtf(128), buffer.readEnum(com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.Unit.class),
                buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), readAllocation(buffer)));
        return new ProductionSnapshotPayload(dimension, target, request, applied, role, title, editable, blocker, feedback, queue, legacyCount, legacy, storages, stock, requirements, SettlementPolicyOrderLink.read(buffer));
    }

    private static void writeAllocation(RegistryFriendlyByteBuf buffer, HiredProductionDemand.Allocation allocation) {
        buffer.writeLong(allocation.protectedMinimum());
        buffer.writeLong(allocation.targetReserve());
        buffer.writeVarInt(allocation.priority());
        buffer.writeUtf(allocation.blocker(), 128);
        buffer.writeBoolean(allocation.competitor() != null);
        if (allocation.competitor() != null) {
            var other = allocation.competitor();
            buffer.writeUUID(other.town());
            buffer.writeUUID(other.consumer());
            buffer.writeUtf(other.name(), 128);
            buffer.writeVarInt(other.priority());
            buffer.writeLong(other.protectedMinimum());
            buffer.writeEnum(other.unit());
        }
    }

    private static HiredProductionDemand.Allocation readAllocation(RegistryFriendlyByteBuf buffer) {
        long protectedMinimum = buffer.readLong();
        long targetReserve = buffer.readLong();
        int priority = buffer.readVarInt();
        String blocker = buffer.readUtf(128);
        var competitor = buffer.readBoolean() ? new HiredProductionDemand.Competitor(buffer.readUUID(), buffer.readUUID(),
                buffer.readUtf(128), buffer.readVarInt(), buffer.readLong(),
                buffer.readEnum(com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement.Unit.class)) : null;
        return new HiredProductionDemand.Allocation(protectedMinimum, targetReserve, priority, blocker, competitor);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Legacy(UUID worker, HiredProductionRecipe recipe, int amount, boolean continuous, int activeBottles) {
        public Legacy {
            Objects.requireNonNull(worker);
            Objects.requireNonNull(recipe);
            if (recipe.recipe() != null || amount < 1 || amount > 4096 || activeBottles < -1 || activeBottles > 96) {
                throw new IllegalArgumentException("Invalid legacy brewing preview");
            }
        }
    }

    public record Storage(BlockPos position, String name, boolean available) {
        public Storage {
            position = Objects.requireNonNull(position).immutable();
            if (name == null || name.length() > 128) throw new IllegalArgumentException("Invalid production storage");
        }
    }
}
