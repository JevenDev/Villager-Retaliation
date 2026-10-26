package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageRelationsRequestPayload(UUID perspective, UUID village, UUID target, UUID ruler,
                                             Action action, View view, int index, String query, long revision, int requestId)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 24;
    public static final Type<VillageRelationsRequestPayload> TYPE = VillagerPayloads.type("village_relations_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageRelationsRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageRelationsRequestPayload::encode, VillageRelationsRequestPayload::decode);

    public VillageRelationsRequestPayload {
        if (action == null || view == null || index < 0 || index > 1_000_000 || query == null || query.length() > 64
                || revision < 0 || requestId < 0 || action != Action.VIEW && target == null
                || action == Action.INTRODUCE && ruler == null
                || (action == Action.SHARE || action == Action.SEND_INTRODUCTION || action == Action.SEND_REPORT) && perspective == null
                || (action == Action.SEND_INTRODUCTION || action == Action.SEND_REPORT) && village == null) {
            throw new IllegalArgumentException("Invalid relations request");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillageRelationsRequestPayload value) {
        writeId(buffer, value.perspective()); writeId(buffer, value.village());
        writeId(buffer, value.target()); writeId(buffer, value.ruler());
        buffer.writeEnum(value.action()); buffer.writeEnum(value.view());
        buffer.writeVarInt(value.index()); buffer.writeUtf(value.query(), 64);
        buffer.writeVarLong(value.revision()); buffer.writeVarInt(value.requestId());
    }

    private static VillageRelationsRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillageRelationsRequestPayload(readId(buffer), readId(buffer), readId(buffer), readId(buffer),
                buffer.readEnum(Action.class), buffer.readEnum(View.class), buffer.readVarInt(), buffer.readUtf(64),
                buffer.readVarLong(), buffer.readVarInt());
    }

    static void writeId(RegistryFriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) buffer.writeUUID(id);
    }

    static UUID readId(RegistryFriendlyByteBuf buffer) { return buffer.readBoolean() ? buffer.readUUID() : null; }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action { VIEW, SHARE, INTRODUCE, SEND_INTRODUCTION, SEND_REPORT }
    public enum View { CONTACTS, KNOWLEDGE, REPORTS, OUTBOX, HISTORY, MAP, PERSPECTIVES }
}
