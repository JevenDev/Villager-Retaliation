package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementWorkerPool;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SettlementWorkerPoolSnapshotPayload(ResourceLocation dimension, UUID town, UUID request, long revision, long gameTime,
        boolean applied, boolean editable, String feedback, String blocker, List<Summary> pools, SettlementWorkerPool selected,
        String query, int page, boolean more, List<Resident> residents, List<Worksite> worksites, List<History> history)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 32;
    public static final Type<SettlementWorkerPoolSnapshotPayload> TYPE = VillagerPayloads.type("settlement_worker_pool_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementWorkerPoolSnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementWorkerPoolSnapshotPayload::encode, SettlementWorkerPoolSnapshotPayload::decode);

    public SettlementWorkerPoolSnapshotPayload {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(town);
        Objects.requireNonNull(request);
        code(feedback);
        code(blocker);
        if (revision < 0 || gameTime < 0 || query == null || query.length() > 96 || page < 0 || page > 32_767)
            throw new IllegalArgumentException("Invalid worker pool snapshot");
        pools = List.copyOf(pools);
        residents = List.copyOf(residents);
        worksites = List.copyOf(worksites);
        history = List.copyOf(history);
        if (pools.size() > SettlementWorkerPool.MAX_POOLS || residents.size() > PAGE_SIZE
                || worksites.size() > SettlementRecord.MAX_AREAS || history.size() > SettlementWorkerPool.MAX_HISTORY)
            throw new IllegalArgumentException("Worker pool snapshot exceeds its limits");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementWorkerPoolSnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        buffer.writeVarLong(payload.revision());
        buffer.writeVarLong(payload.gameTime());
        buffer.writeBoolean(payload.applied());
        buffer.writeBoolean(payload.editable());
        buffer.writeUtf(payload.feedback(), 128);
        buffer.writeUtf(payload.blocker(), 128);
        buffer.writeVarInt(payload.pools().size());
        for (var value : payload.pools()) {
            buffer.writeUUID(value.id());
            buffer.writeUtf(value.name(), 64);
            buffer.writeVarLong(value.revision());
            buffer.writeEnum(value.state());
            buffer.writeUtf(value.blocker(), 128);
        }
        buffer.writeBoolean(payload.selected() != null);
        if (payload.selected() != null) buffer.writeNbt(payload.selected().save());
        buffer.writeUtf(payload.query(), 96);
        buffer.writeVarInt(payload.page());
        buffer.writeBoolean(payload.more());
        buffer.writeVarInt(payload.residents().size());
        for (var value : payload.residents()) {
            buffer.writeUUID(value.id());
            buffer.writeUtf(value.name(), 128);
            SettlementPolicyRequestPayload.optionalId(buffer, value.pool());
            SettlementPolicyRequestPayload.optionalId(buffer, value.duty());
            buffer.writeBoolean(value.pinned());
            buffer.writeVarLong(value.cooldown());
            buffer.writeUtf(value.blocker(), 128);
        }
        buffer.writeVarInt(payload.worksites().size());
        for (var value : payload.worksites()) {
            buffer.writeUUID(value.id());
            buffer.writeUtf(value.name(), 128);
            buffer.writeEnum(value.type());
            buffer.writeBlockPos(value.position());
            buffer.writeBoolean(value.available());
            buffer.writeVarInt(value.current());
            buffer.writeVarInt(value.desired());
            buffer.writeVarInt(value.minimum());
            buffer.writeVarInt(value.maximum());
            buffer.writeBoolean(value.active());
            buffer.writeBoolean(value.protectedShortage());
            buffer.writeVarInt(value.priority());
            SettlementPolicyRequestPayload.optionalId(buffer, value.demand());
            buffer.writeUtf(value.cause(), 128);
        }
        buffer.writeVarInt(payload.history().size());
        for (var value : payload.history()) {
            buffer.writeVarLong(value.at());
            buffer.writeUtf(value.resident(), 128);
            buffer.writeUtf(value.from(), 128);
            buffer.writeUtf(value.to(), 128);
            buffer.writeEnum(value.reason());
            SettlementPolicyRequestPayload.optionalId(buffer, value.demand());
            buffer.writeUtf(value.cause(), 128);
            buffer.writeVarInt(value.priority());
        }
    }

    private static SettlementWorkerPoolSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var town = buffer.readUUID();
        var request = buffer.readUUID();
        long revision = buffer.readVarLong();
        long time = buffer.readVarLong();
        boolean applied = buffer.readBoolean();
        boolean editable = buffer.readBoolean();
        String feedback = buffer.readUtf(128);
        String blocker = buffer.readUtf(128);
        var pools = list(buffer, SettlementWorkerPool.MAX_POOLS, input -> new Summary(input.readUUID(), input.readUtf(64),
                input.readVarLong(), input.readEnum(SettlementWorkerPool.State.class), input.readUtf(128)));
        SettlementWorkerPool selected = null;
        if (buffer.readBoolean()) {
            var tag = buffer.readNbt();
            if (tag == null) throw new IllegalArgumentException("Missing selected worker pool");
            selected = SettlementWorkerPool.load(tag);
        }
        String query = buffer.readUtf(96);
        int page = buffer.readVarInt();
        boolean more = buffer.readBoolean();
        var residents = list(buffer, PAGE_SIZE, input -> new Resident(input.readUUID(), input.readUtf(128),
                SettlementPolicyRequestPayload.optionalId(input), SettlementPolicyRequestPayload.optionalId(input),
                input.readBoolean(), input.readVarLong(), input.readUtf(128)));
        var worksites = list(buffer, SettlementRecord.MAX_AREAS, input -> new Worksite(input.readUUID(), input.readUtf(128),
                input.readEnum(SettlementAreaType.class), input.readBlockPos(), input.readBoolean(), input.readVarInt(), input.readVarInt(),
                input.readVarInt(), input.readVarInt(), input.readBoolean(), input.readBoolean(), input.readVarInt(),
                SettlementPolicyRequestPayload.optionalId(input), input.readUtf(128)));
        var history = list(buffer, SettlementWorkerPool.MAX_HISTORY, input -> new History(input.readVarLong(), input.readUtf(128),
                input.readUtf(128), input.readUtf(128), input.readEnum(SettlementWorkerPool.Reason.class),
                SettlementPolicyRequestPayload.optionalId(input), input.readUtf(128), input.readVarInt()));
        return new SettlementWorkerPoolSnapshotPayload(dimension, town, request, revision, time, applied, editable, feedback, blocker,
                pools, selected, query, page, more, residents, worksites, history);
    }

    private static <T> List<T> list(RegistryFriendlyByteBuf buffer, int maximum, Function<RegistryFriendlyByteBuf, T> read) {
        int size = VillagerPayloads.readCollectionSize(buffer, maximum, "Worker pool snapshot");
        var result = new ArrayList<T>(size);
        for (int index = 0; index < size; index++) result.add(read.apply(buffer));
        return result;
    }

    private static void code(String value) {
        if (value == null || value.length() > 128) throw new IllegalArgumentException("Invalid worker pool snapshot text");
    }

    private static void priority(int value) {
        if (value < 0 || value > 9) throw new IllegalArgumentException("Invalid worker pool priority");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Summary(UUID id, String name, long revision, SettlementWorkerPool.State state, String blocker) {
        public Summary {
            Objects.requireNonNull(id);
            Objects.requireNonNull(state);
            if (name == null || name.isBlank() || name.length() > 64 || revision < 0) throw new IllegalArgumentException("Invalid worker pool summary");
            code(blocker);
        }
    }

    public record Resident(UUID id, String name, UUID pool, UUID duty, boolean pinned, long cooldown, String blocker) {
        public Resident {
            Objects.requireNonNull(id);
            code(name);
            code(blocker);
            if (cooldown < 0 || cooldown > SettlementWorkerPool.COOLDOWN_TICKS) throw new IllegalArgumentException("Invalid worker pool cooldown");
        }
    }

    public record Worksite(UUID id, String name, SettlementAreaType type, BlockPos position, boolean available, int current,
            int desired, int minimum, int maximum, boolean active, boolean protectedShortage, int priority, UUID demand, String cause) {
        public Worksite {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            Objects.requireNonNull(position);
            code(name);
            code(cause);
            if (type.role() == null || current < 0 || desired < 0 || desired > SettlementWorksitePlan.MAX_STAFF
                    || minimum < 0 || maximum < minimum || maximum > SettlementWorksitePlan.MAX_STAFF)
                throw new IllegalArgumentException("Invalid worker pool worksite");
            SettlementWorkerPoolSnapshotPayload.priority(priority);
        }
    }

    public record History(long at, String resident, String from, String to, SettlementWorkerPool.Reason reason,
            UUID demand, String cause, int priority) {
        public History {
            Objects.requireNonNull(reason);
            if (at < 0) throw new IllegalArgumentException("Invalid worker pool history time");
            code(resident);
            code(from);
            code(to);
            code(cause);
            SettlementWorkerPoolSnapshotPayload.priority(priority);
        }
    }
}
