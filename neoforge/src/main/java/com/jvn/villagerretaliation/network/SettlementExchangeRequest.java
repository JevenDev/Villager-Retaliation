package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementExchangeService.Action;
import com.jvn.villagerretaliation.settlement.SettlementExchangeTerms;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementExchangeRequest(UUID village, UUID target, UUID request, long revision, Action action,
                                        SettlementExchangeTerms terms, int page, int sequence) implements CustomPacketPayload {
    public static final Type<SettlementExchangeRequest> TYPE = VillagerPayloads.type("settlement_exchange_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementExchangeRequest> STREAM_CODEC = VillagerPayloads.codec(
            SettlementExchangeRequest::encode, SettlementExchangeRequest::decode);
    public SettlementExchangeRequest {
        if (village == null || action == null || revision < 0 || page < 0 || page > 1024 || sequence < 0
                || (action == Action.PROPOSE || action == Action.QUOTE) && (target == null || terms == null || request == null)
                || action != Action.VIEW && action != Action.PROPOSE && action != Action.QUOTE && request == null)
            throw new IllegalArgumentException("Invalid exchange action");
    }
    private static void encode(RegistryFriendlyByteBuf buffer, SettlementExchangeRequest value) {
        buffer.writeUUID(value.village); VillageRelationsRequestPayload.writeId(buffer, value.target);
        VillageRelationsRequestPayload.writeId(buffer, value.request); buffer.writeVarLong(value.revision); buffer.writeEnum(value.action);
        buffer.writeBoolean(value.terms != null);
        if (value.terms != null) buffer.writeNbt(value.terms.save(buffer.registryAccess()));
        buffer.writeVarInt(value.page); buffer.writeVarInt(value.sequence);
    }
    private static SettlementExchangeRequest decode(RegistryFriendlyByteBuf buffer) {
        var village = buffer.readUUID(); var target = VillageRelationsRequestPayload.readId(buffer); var id = VillageRelationsRequestPayload.readId(buffer);
        long revision = buffer.readVarLong(); var action = buffer.readEnum(Action.class);
        var terms = buffer.readBoolean() ? SettlementExchangeTerms.load(buffer.readNbt(), buffer.registryAccess()) : null;
        return new SettlementExchangeRequest(village, target, id, revision, action, terms, buffer.readVarInt(), buffer.readVarInt());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
