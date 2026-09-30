package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SettlementPolicySnapshotPayload(ResourceLocation dimension, UUID town, UUID request, boolean applied, boolean editable,
        String feedback, String blocker, List<Summary> policies, CompoundTag selected, List<Worksite> worksites,
        List<Storage> storages, List<Consumer> consumers, List<Need> needs, List<Production> production) implements CustomPacketPayload {
    public static final Type<SettlementPolicySnapshotPayload> TYPE = VillagerPayloads.type("settlement_policy_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPolicySnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementPolicySnapshotPayload::encode, SettlementPolicySnapshotPayload::decode);

    public SettlementPolicySnapshotPayload {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(town);
        Objects.requireNonNull(request);
        code(feedback);
        code(blocker);
        policies = List.copyOf(policies);
        selected = selected == null ? null : selected.copy();
        worksites = List.copyOf(worksites);
        storages = List.copyOf(storages);
        consumers = List.copyOf(consumers);
        needs = List.copyOf(needs);
        production = List.copyOf(production);
        if (policies.size() > SettlementSupplyPolicy.MAX_POLICIES || worksites.size() > SettlementRecord.MAX_AREAS
                || storages.size() > 512 || consumers.size() > 512 || needs.size() > 1024
                || production.size() > SettlementSupplyPolicy.MAX_STEPS) throw new IllegalArgumentException("Oversized policy snapshot");
    }

    @Override public CompoundTag selected() { return selected == null ? null : selected.copy(); }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementPolicySnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        buffer.writeBoolean(payload.applied());
        buffer.writeBoolean(payload.editable());
        buffer.writeUtf(payload.feedback(), 128);
        buffer.writeUtf(payload.blocker(), 128);
        buffer.writeVarInt(payload.policies().size());
        for (var policy : payload.policies()) {
            buffer.writeUUID(policy.id());
            buffer.writeUtf(policy.name(), 64);
            buffer.writeVarLong(policy.revision());
            buffer.writeEnum(policy.state());
            buffer.writeUtf(policy.blocker(), 128);
        }
        buffer.writeNbt(payload.selected());
        buffer.writeVarInt(payload.worksites().size());
        for (var area : payload.worksites()) {
            buffer.writeUUID(area.id());
            buffer.writeUtf(area.name(), 128);
            buffer.writeEnum(area.type());
            buffer.writeBlockPos(area.position());
            buffer.writeBoolean(area.enabled());
        }
        buffer.writeVarInt(payload.storages().size());
        for (var storage : payload.storages()) {
            buffer.writeBlockPos(storage.position());
            buffer.writeUtf(storage.name(), 128);
            buffer.writeBoolean(storage.source());
            buffer.writeBoolean(storage.destination());
            buffer.writeBoolean(storage.known());
        }
        buffer.writeVarInt(payload.consumers().size());
        for (var consumer : payload.consumers()) {
            buffer.writeUUID(consumer.id());
            buffer.writeUtf(consumer.name(), 128);
            buffer.writeEnum(consumer.kind());
        }
        buffer.writeVarInt(payload.needs().size());
        for (var need : payload.needs()) {
            SettlementPolicyRequestPayload.optionalId(buffer, need.step());
            buffer.writeUUID(need.id());
            buffer.writeUtf(need.name(), 128);
            buffer.writeEnum(need.unit());
            buffer.writeLong(need.required());
            buffer.writeLong(need.available());
            buffer.writeLong(need.committed());
            buffer.writeLong(need.incoming());
            buffer.writeLong(need.missing());
            buffer.writeBoolean(need.known());
            buffer.writeVarInt(need.samples().size());
            for (var sample : need.samples()) ItemStack.STREAM_CODEC.encode(buffer, sample);
            buffer.writeVarInt(need.sources().size());
            for (var source : need.sources()) buffer.writeBlockPos(source);
        }
        buffer.writeVarInt(payload.production().size());
        for (var step : payload.production()) {
            buffer.writeUUID(step.id());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, step.output());
            buffer.writeLong(step.proposed());
            buffer.writeLong(step.requested());
            buffer.writeLong(step.retained());
            buffer.writeUtf(step.blocker(), 128);
        }
    }

    private static SettlementPolicySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var town = buffer.readUUID();
        var request = buffer.readUUID();
        boolean applied = buffer.readBoolean();
        boolean editable = buffer.readBoolean();
        String feedback = buffer.readUtf(128);
        String blocker = buffer.readUtf(128);
        var policies = new ArrayList<Summary>();
        int size = VillagerPayloads.readCollectionSize(buffer, SettlementSupplyPolicy.MAX_POLICIES, "Policies");
        for (int index = 0; index < size; index++) policies.add(new Summary(buffer.readUUID(), buffer.readUtf(64), buffer.readVarLong(),
                buffer.readEnum(SettlementSupplyPolicy.State.class), buffer.readUtf(128)));
        var selected = buffer.readNbt();
        if (selected != null) SettlementSupplyPolicy.load(selected, buffer.registryAccess(), () -> {});
        var worksites = new ArrayList<Worksite>();
        size = VillagerPayloads.readCollectionSize(buffer, SettlementRecord.MAX_AREAS, "Policy worksites");
        for (int index = 0; index < size; index++) worksites.add(new Worksite(buffer.readUUID(), buffer.readUtf(128),
                buffer.readEnum(SettlementAreaType.class), buffer.readBlockPos(), buffer.readBoolean()));
        var storages = new ArrayList<Storage>();
        size = VillagerPayloads.readCollectionSize(buffer, 512, "Policy storage");
        for (int index = 0; index < size; index++) storages.add(new Storage(buffer.readBlockPos(), buffer.readUtf(128),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        var consumers = new ArrayList<Consumer>();
        size = VillagerPayloads.readCollectionSize(buffer, 512, "Policy consumers");
        for (int index = 0; index < size; index++) consumers.add(new Consumer(buffer.readUUID(), buffer.readUtf(128),
                buffer.readEnum(SettlementSupplyPolicy.TargetKind.class)));
        var needs = new ArrayList<Need>();
        size = VillagerPayloads.readCollectionSize(buffer, 1024, "Policy requirements");
        for (int index = 0; index < size; index++) {
            var step = SettlementPolicyRequestPayload.optionalId(buffer);
            var id = buffer.readUUID();
            String name = buffer.readUtf(128);
            var unit = buffer.readEnum(WorkSupplyRequirement.Unit.class);
            long required = buffer.readLong();
            long available = buffer.readLong();
            long committed = buffer.readLong();
            long incoming = buffer.readLong();
            long missing = buffer.readLong();
            boolean known = buffer.readBoolean();
            var samples = new ArrayList<ItemStack>();
            int count = VillagerPayloads.readCollectionSize(buffer, 16, "Policy alternatives");
            for (int entry = 0; entry < count; entry++) samples.add(ItemStack.STREAM_CODEC.decode(buffer));
            var sources = new ArrayList<BlockPos>();
            count = VillagerPayloads.readCollectionSize(buffer, 512, "Policy sources");
            for (int entry = 0; entry < count; entry++) sources.add(buffer.readBlockPos());
            needs.add(new Need(step, id, name, unit, required, available, committed, incoming, missing, known, samples, sources));
        }
        var production = new ArrayList<Production>();
        size = VillagerPayloads.readCollectionSize(buffer, SettlementSupplyPolicy.MAX_STEPS, "Policy steps");
        for (int index = 0; index < size; index++) production.add(new Production(buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readUtf(128)));
        return new SettlementPolicySnapshotPayload(dimension, town, request, applied, editable, feedback, blocker,
                policies, selected, worksites, storages, consumers, needs, production);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void code(String value) {
        if (value == null || value.length() > 128) throw new IllegalArgumentException("Invalid policy text");
    }

    public record Summary(UUID id, String name, long revision, SettlementSupplyPolicy.State state, String blocker) {
        public Summary {
            Objects.requireNonNull(id);
            Objects.requireNonNull(state);
            code(blocker);
            if (name == null || name.length() > 64 || revision < 0) throw new IllegalArgumentException("Invalid policy summary");
        }
    }
    public record Worksite(UUID id, String name, SettlementAreaType type, BlockPos position, boolean enabled) {
        public Worksite {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            position = Objects.requireNonNull(position).immutable();
            code(name);
        }
    }
    public record Storage(BlockPos position, String name, boolean source, boolean destination, boolean known) {
        public Storage {
            position = Objects.requireNonNull(position).immutable();
            code(name);
        }
    }
    public record Consumer(UUID id, String name, SettlementSupplyPolicy.TargetKind kind) {
        public Consumer {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            code(name);
            if (kind == SettlementSupplyPolicy.TargetKind.STOCK) throw new IllegalArgumentException("Stock targets need an endpoint");
        }
    }
    public record Need(UUID step, UUID id, String name, WorkSupplyRequirement.Unit unit, long required, long available,
                       long committed, long incoming, long missing, boolean known, List<ItemStack> samples, List<BlockPos> sources) {
        public Need {
            Objects.requireNonNull(id);
            Objects.requireNonNull(unit);
            code(name);
            samples = samples.stream().map(ItemStack::copy).toList();
            sources = sources.stream().map(BlockPos::immutable).toList();
            if (required < 0 || available < 0 || committed < 0 || incoming < 0 || missing < 0
                    || samples.size() > 16 || sources.size() > 512) throw new IllegalArgumentException("Invalid policy requirement");
        }
        @Override public List<ItemStack> samples() { return samples.stream().map(ItemStack::copy).toList(); }
    }
    public record Production(UUID id, ItemStack output, long proposed, long requested, long retained, String blocker) {
        public Production {
            Objects.requireNonNull(id);
            output = Objects.requireNonNull(output).copy();
            code(blocker);
            if (proposed < 0 || requested < 0 || retained < 0) throw new IllegalArgumentException("Invalid policy quantities");
        }
        @Override public ItemStack output() { return output.copy(); }
    }
}
