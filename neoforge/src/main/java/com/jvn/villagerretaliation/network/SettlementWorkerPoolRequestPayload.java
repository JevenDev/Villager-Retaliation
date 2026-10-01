package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementWorkerPool;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementWorkerPoolRequestPayload(UUID town, UUID request, UUID pool, long revision, String query, int page, Command command)
        implements CustomPacketPayload {
    public static final Type<SettlementWorkerPoolRequestPayload> TYPE = VillagerPayloads.type("settlement_worker_pool_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementWorkerPoolRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementWorkerPoolRequestPayload::encode, SettlementWorkerPoolRequestPayload::decode);

    public SettlementWorkerPoolRequestPayload {
        Objects.requireNonNull(town);
        Objects.requireNonNull(request);
        Objects.requireNonNull(command);
        if (revision < -1 || query == null || query.length() > 96 || page < 0 || page > 32_767
                || pool == null && !(command instanceof View)) throw new IllegalArgumentException("Invalid worker pool request");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementWorkerPoolRequestPayload payload) {
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        SettlementPolicyRequestPayload.optionalId(buffer, payload.pool());
        buffer.writeLong(payload.revision());
        buffer.writeUtf(payload.query(), 96);
        buffer.writeVarInt(payload.page());
        switch (payload.command()) {
            case View ignored -> buffer.writeEnum(Kind.VIEW);
            case Preview preview -> {
                buffer.writeEnum(Kind.PREVIEW);
                buffer.writeNbt(preview.configuration().save());
            }
            case Save save -> {
                buffer.writeEnum(Kind.SAVE);
                buffer.writeNbt(save.configuration().save());
                buffer.writeBoolean(save.active());
            }
            case Change change -> {
                buffer.writeEnum(Kind.CHANGE);
                buffer.writeEnum(change.action());
            }
            case ReturnControl returned -> {
                buffer.writeEnum(Kind.RETURN);
                buffer.writeUUID(returned.resident());
                buffer.writeVarLong(returned.townRevision());
            }
        }
    }

    private static SettlementWorkerPoolRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        var town = buffer.readUUID();
        var request = buffer.readUUID();
        var pool = SettlementPolicyRequestPayload.optionalId(buffer);
        long revision = buffer.readLong();
        String query = buffer.readUtf(96);
        int page = buffer.readVarInt();
        Command command = switch (buffer.readEnum(Kind.class)) {
            case VIEW -> new View();
            case PREVIEW -> new Preview(configuration(buffer));
            case SAVE -> new Save(configuration(buffer), buffer.readBoolean());
            case CHANGE -> new Change(buffer.readEnum(Action.class));
            case RETURN -> new ReturnControl(buffer.readUUID(), buffer.readVarLong());
        };
        return new SettlementWorkerPoolRequestPayload(town, request, pool, revision, query, page, command);
    }

    private static SettlementWorkerPool.Configuration configuration(RegistryFriendlyByteBuf buffer) {
        var tag = buffer.readNbt();
        if (tag == null) throw new IllegalArgumentException("Missing worker pool configuration");
        return SettlementWorkerPool.Configuration.load(tag);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public sealed interface Command permits View, Preview, Save, Change, ReturnControl { }
    public record View() implements Command { }
    public record Preview(SettlementWorkerPool.Configuration configuration) implements Command {
        public Preview { Objects.requireNonNull(configuration); }
    }
    public record Save(SettlementWorkerPool.Configuration configuration, boolean active) implements Command {
        public Save { Objects.requireNonNull(configuration); }
    }
    public record Change(Action action) implements Command {
        public Change { Objects.requireNonNull(action); }
    }
    public record ReturnControl(UUID resident, long townRevision) implements Command {
        public ReturnControl {
            Objects.requireNonNull(resident);
            if (townRevision < 0) throw new IllegalArgumentException("Invalid worker pool return revision");
        }
    }
    public enum Action { ACTIVATE, PAUSE, REMOVE }
    private enum Kind { VIEW, PREVIEW, SAVE, CHANGE, RETURN }
}
