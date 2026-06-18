package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueOptionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRequestType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class DialogueOptionPayloadCodec {
    private static final int MAX_DIALOGUE_OPTIONS = 128;
    private static final int MAX_STRING_LIST_VALUES = 128;
    private static final int OPTION_ID_LENGTH = 128;
    private static final int OPTION_LABEL_LENGTH = 128;
    private static final int STRING_VALUE_LENGTH = 128;

    private DialogueOptionPayloadCodec() {
    }

    static void writeDialogueOptions(RegistryFriendlyByteBuf buffer, List<DialogueOptionDefinition> options) {
        List<DialogueOptionDefinition> safeOptions = options == null ? List.of() : options;
        buffer.writeVarInt(Math.min(safeOptions.size(), MAX_DIALOGUE_OPTIONS));
        for (int index = 0; index < Math.min(safeOptions.size(), MAX_DIALOGUE_OPTIONS); index++) {
            DialogueOptionDefinition option = safeOptions.get(index);
            buffer.writeUtf(option.id(), OPTION_ID_LENGTH);
            buffer.writeUtf(option.label(), OPTION_LABEL_LENGTH);
            buffer.writeEnum(option.requestType());
            buffer.writeBoolean(option.forceCameraTowardsVillager());
            buffer.writeVarInt(option.order());
        }
    }

    static List<DialogueOptionDefinition> readDialogueOptions(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_DIALOGUE_OPTIONS, "dialogue options");
        List<DialogueOptionDefinition> options = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            DialogueOptionDefinition option = DialogueOptionDefinition.transmitted(
                    buffer.readUtf(OPTION_ID_LENGTH),
                    buffer.readUtf(OPTION_LABEL_LENGTH),
                    buffer.readEnum(DialogueRequestType.class),
                    buffer.readBoolean(),
                    buffer.readVarInt()
            );
            options.add(option);
        }
        return List.copyOf(options);
    }

    static void writeStringList(RegistryFriendlyByteBuf buffer, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        buffer.writeVarInt(Math.min(safeValues.size(), MAX_STRING_LIST_VALUES));
        for (int index = 0; index < Math.min(safeValues.size(), MAX_STRING_LIST_VALUES); index++) {
            buffer.writeUtf(safeValues.get(index), STRING_VALUE_LENGTH);
        }
    }

    static List<String> readStringList(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_STRING_LIST_VALUES, "string list values");
        List<String> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(buffer.readUtf(STRING_VALUE_LENGTH));
        }
        return List.copyOf(values);
    }
}
