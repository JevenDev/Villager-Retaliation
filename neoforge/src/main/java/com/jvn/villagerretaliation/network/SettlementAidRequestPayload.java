package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementAidRequestPayload(UUID village, UUID target, UUID request, Action action, int page, long revision,
                                           long nutrition, Policy policy, int requestId) implements CustomPacketPayload {
    public enum Action { VIEW, REQUEST, APPROVE, ACCEPT, DECLINE, RETRY, CANCEL, POLICY }
    public record Policy(boolean automatic, int reserveDays, int maximumItems, int minimumStanding, int intervalTicks) {
        public Policy {
            if (reserveDays < 1 || reserveDays > 30 || maximumItems < 1 || maximumItems > 1728 || minimumStanding < 0
                    || minimumStanding > 100 || intervalTicks < 24000 || intervalTicks > 2400000) throw new IllegalArgumentException("Invalid aid policy");
        }
        void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBoolean(automatic); buffer.writeVarInt(reserveDays); buffer.writeVarInt(maximumItems);
            buffer.writeVarInt(minimumStanding); buffer.writeVarInt(intervalTicks);
        }
        static Policy decode(RegistryFriendlyByteBuf buffer) {
            return new Policy(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
    }
    public static final Type<SettlementAidRequestPayload> TYPE = VillagerPayloads.type("settlement_aid_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementAidRequestPayload> STREAM_CODEC = VillagerPayloads.codec(SettlementAidRequestPayload::encode, SettlementAidRequestPayload::decode);
    public SettlementAidRequestPayload {
        if (village == null || action == null || page < 0 || page > 1000000 || revision < 0 || nutrition < 0 || nutrition > 10000000
                || policy == null || requestId < 0 || action == Action.REQUEST && target == null
                || (action == Action.APPROVE || action == Action.ACCEPT || action == Action.DECLINE || action == Action.RETRY || action == Action.CANCEL) && request == null)
            throw new IllegalArgumentException("Invalid aid action");
    }
    private static void encode(RegistryFriendlyByteBuf buffer, SettlementAidRequestPayload value) {
        buffer.writeUUID(value.village); VillageRelationsRequestPayload.writeId(buffer, value.target); VillageRelationsRequestPayload.writeId(buffer, value.request);
        buffer.writeEnum(value.action); buffer.writeVarInt(value.page); buffer.writeVarLong(value.revision); buffer.writeVarLong(value.nutrition);
        value.policy.encode(buffer); buffer.writeVarInt(value.requestId);
    }
    private static SettlementAidRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SettlementAidRequestPayload(buffer.readUUID(), VillageRelationsRequestPayload.readId(buffer), VillageRelationsRequestPayload.readId(buffer),
                buffer.readEnum(Action.class), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), Policy.decode(buffer), buffer.readVarInt());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
