package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerAllegianceActionPayload(int entityId, Action action) implements CustomPacketPayload {
    public static final Type<VillagerAllegianceActionPayload> TYPE = VillagerPayloads.type("villager_allegiance_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerAllegianceActionPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerAllegianceActionPayload::encode, VillagerAllegianceActionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerAllegianceActionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
    }

    private static VillagerAllegianceActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerAllegianceActionPayload(buffer.readVarInt(), buffer.readEnum(Action.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        REASSIGN_TO_CURRENT_VILLAGE
    }
}
