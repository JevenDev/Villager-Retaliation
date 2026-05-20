package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueResponsePayload(
        int entityId,
        int reputation,
        VillagerReputationLevel reputationLevel,
        DialogueDisposition mood)
        implements CustomPacketPayload {
    public static final Type<VillagerDialogueResponsePayload> TYPE = VillagerPayloads.type("villager_dialogue_response");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueResponsePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDialogueResponsePayload::encode, VillagerDialogueResponsePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueResponsePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
        buffer.writeEnum(payload.mood());
    }

    private static VillagerDialogueResponsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueResponsePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
