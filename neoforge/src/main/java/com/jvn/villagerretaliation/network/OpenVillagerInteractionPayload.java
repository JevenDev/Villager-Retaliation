package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillagerInteractionPayload(
        int entityId,
        String villagerNameKey,
        String villagerNameFallback,
        String professionName,
        boolean baby,
        int reputation,
        VillagerReputationLevel reputationLevel,
        DialogueDisposition mood,
        boolean followingPlayer,
        List<DialogueOptionDefinition> dialogueOptions,
        List<String> knownLikedGiftNames,
        List<String> knownDislikedGiftNames)
        implements CustomPacketPayload {
    public static final Type<OpenVillagerInteractionPayload> TYPE = VillagerPayloads.type("open_villager_interaction");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerInteractionPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerInteractionPayload::encode, OpenVillagerInteractionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerInteractionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerNameKey());
        buffer.writeUtf(payload.villagerNameFallback());
        buffer.writeUtf(payload.professionName());
        buffer.writeBoolean(payload.baby());
        buffer.writeVarInt(payload.reputation());
        buffer.writeEnum(payload.reputationLevel());
        buffer.writeEnum(payload.mood());
        buffer.writeBoolean(payload.followingPlayer());
        writeDialogueOptions(buffer, payload.dialogueOptions());
        writeStringList(buffer, payload.knownLikedGiftNames());
        writeStringList(buffer, payload.knownDislikedGiftNames());
    }

    private static OpenVillagerInteractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerInteractionPayload(
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readEnum(VillagerReputationLevel.class),
                buffer.readEnum(DialogueDisposition.class),
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
