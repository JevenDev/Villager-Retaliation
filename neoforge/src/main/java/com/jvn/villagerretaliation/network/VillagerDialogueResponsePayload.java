package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.interaction.GiftPreferenceView;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerDialogueResponsePayload(
        int entityId,
        int reputation,
        VillagerReputationLevel reputationLevel,
        DialogueDisposition mood,
        VillagerMood primaryMood,
        boolean forceCameraTowardsVillager,
        List<DialogueOptionDefinition> dialogueOptions,
        List<GiftPreferenceView> giftPreferences)
        implements CustomPacketPayload {
    public static final Type<VillagerDialogueResponsePayload> TYPE = VillagerPayloads.type("villager_dialogue_response");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerDialogueResponsePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerDialogueResponsePayload::encode, VillagerDialogueResponsePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerDialogueResponsePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
        buffer.writeEnum(payload.mood());
        buffer.writeEnum(payload.primaryMood());
        buffer.writeBoolean(payload.forceCameraTowardsVillager());
        DialogueOptionPayloadCodec.writeDialogueOptions(buffer, payload.dialogueOptions());
        DialogueOptionPayloadCodec.writeGiftPreferenceViews(buffer, payload.giftPreferences());
    }

    private static VillagerDialogueResponsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueResponsePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class),
                buffer.readEnum(VillagerMood.class),
                buffer.readBoolean(),
                DialogueOptionPayloadCodec.readDialogueOptions(buffer),
                DialogueOptionPayloadCodec.readGiftPreferenceViews(buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
