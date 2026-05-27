package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        List<String> knownLikedGiftNames,
        List<String> knownDislikedGiftNames)
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
        writeDialogueOptions(buffer, payload.dialogueOptions());
        writeStringList(buffer, payload.knownLikedGiftNames());
        writeStringList(buffer, payload.knownDislikedGiftNames());
    }

    private static VillagerDialogueResponsePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerDialogueResponsePayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class),
                buffer.readEnum(VillagerMood.class),
                buffer.readBoolean(),
                readDialogueOptions(buffer),
                readStringList(buffer),
                readStringList(buffer)
        );
    }

    private static void writeDialogueOptions(RegistryFriendlyByteBuf buffer, List<DialogueOptionDefinition> options) {
        buffer.writeVarInt(options.size());
        for (DialogueOptionDefinition option : options) {
            buffer.writeUtf(option.id(), 128);
            buffer.writeUtf(option.label(), 128);
            buffer.writeEnum(option.requestType());
            buffer.writeBoolean(option.forceCameraTowardsVillager());
            buffer.writeVarInt(option.order());
        }
    }

    private static List<DialogueOptionDefinition> readDialogueOptions(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<DialogueOptionDefinition> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add(new DialogueOptionDefinition(
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readEnum(DialogueRequestType.class),
                    true,
                    true,
                    Set.of(),
                    Set.of(),
                    VillagerEquipmentCondition.empty(),
                    VillagerPlayerItemCondition.empty(),
                    VillagerReputationCondition.empty(),
                    com.jvn.villagerretaliation.dialogue.DialogueItemPayment.empty(),
                    buffer.readBoolean(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    List.of(),
                    false,
                    buffer.readVarInt()
            ));
        }
        return options;
    }

    private static void writeStringList(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value, 128);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readUtf(128));
        }
        return values;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
