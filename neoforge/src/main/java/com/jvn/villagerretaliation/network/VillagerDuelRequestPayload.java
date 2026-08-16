package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.duel.DuelLoadout;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDuelRequestPayload(int entityId, Action action, ResourceLocation kitId, int stake)
        implements CustomPacketPayload {
    public static final Type<VillagerDuelRequestPayload> TYPE = VillagerPayloads.type("villager_duel_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDuelRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDuelRequestPayload::encode, VillagerDuelRequestPayload::decode);

    public VillagerDuelRequestPayload(int entityId, Action action) {
        this(entityId, action, DuelLoadout.BARE_HANDED.id(), 0);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDuelRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
        buffer.writeResourceLocation(payload.kitId());
        buffer.writeVarInt(payload.stake());
    }

    private static VillagerDuelRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDuelRequestPayload(buffer.readVarInt(), buffer.readEnum(Action.class),
                buffer.readResourceLocation(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        OPEN,
        START
    }
}
