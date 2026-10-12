package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.combat.ClericHealingService.Offer;
import com.jvn.villagerretaliation.combat.ClericHealingService.Reason;
import com.jvn.villagerretaliation.combat.ClericHealingService.Source;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerHealingStatePayload(int entityId, UUID villagerId, UUID quoteId, Offer offer,
                                         String dialogue, String healLabel) implements CustomPacketPayload {
    public static final Type<VillagerHealingStatePayload> TYPE = VillagerPayloads.type("villager_healing_state");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerHealingStatePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerHealingStatePayload::encode, VillagerHealingStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerHealingStatePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.villagerId());
        buffer.writeUUID(payload.quoteId());
        buffer.writeEnum(payload.offer().source());
        buffer.writeVarInt(payload.offer().price());
        buffer.writeVarLong(payload.offer().cooldownTicks());
        buffer.writeEnum(payload.offer().reason());
        buffer.writeUtf(payload.dialogue(), 512);
        buffer.writeUtf(payload.healLabel(), 128);
    }

    private static VillagerHealingStatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerHealingStatePayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(),
                new Offer(buffer.readEnum(Source.class), buffer.readVarInt(), buffer.readVarLong(), buffer.readEnum(Reason.class)),
                buffer.readUtf(512), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
