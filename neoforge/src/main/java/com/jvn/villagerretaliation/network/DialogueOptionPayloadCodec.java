package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.jvn.villagerretaliation.interaction.GiftCategoryName;
import com.jvn.villagerretaliation.interaction.GiftPreferenceDefinition;
import com.jvn.villagerretaliation.interaction.GiftPreferenceView;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
            writeStringList(buffer, option.metadata().tags().stream().toList());
        }
    }

    static List<DialogueOptionDefinition> readDialogueOptions(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_DIALOGUE_OPTIONS, "dialogue options");
        List<DialogueOptionDefinition> options = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String id = buffer.readUtf(OPTION_ID_LENGTH);
            String label = buffer.readUtf(OPTION_LABEL_LENGTH);
            DialogueRequestType requestType = buffer.readEnum(DialogueRequestType.class);
            boolean forceCameraTowardsVillager = buffer.readBoolean();
            int order = buffer.readVarInt();
            DialogueEntryMetadata metadata = new DialogueEntryMetadata(
                    "",
                    Set.copyOf(readStringList(buffer)),
                    "",
                    "",
                    "",
                    "");
            DialogueOptionDefinition option = DialogueOptionDefinition.transmitted(
                    id,
                    label,
                    requestType,
                    forceCameraTowardsVillager,
                    order,
                    metadata);
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

    static void writeGiftPreferenceViews(RegistryFriendlyByteBuf buffer, List<GiftPreferenceView> preferences) {
        List<GiftPreferenceView> safePreferences = preferences == null ? List.of() : preferences;
        int size = Math.min(safePreferences.size(), 256);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            GiftPreferenceView preference = safePreferences.get(index);
            buffer.writeResourceLocation(preference.categoryId());
            buffer.writeBoolean(preference.known());
            if (preference.known()) {
                buffer.writeByte(preference.rating());
            }
            buffer.writeVarInt(preference.priority());
            buffer.writeBoolean(preference.professionSpecific());
            buffer.writeUtf(preference.name().translationKey(), 256);
            buffer.writeUtf(preference.name().text(), 256);
            int matcherCount = Math.min(preference.matchers().size(), 512);
            buffer.writeVarInt(matcherCount);
            for (int matcherIndex = 0; matcherIndex < matcherCount; matcherIndex++) {
                GiftPreferenceView.Matcher matcher = preference.matchers().get(matcherIndex);
                buffer.writeEnum(matcher.source());
                buffer.writeResourceLocation(matcher.value());
            }
        }
    }

    static List<GiftPreferenceView> readGiftPreferenceViews(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, 256, "gift preferences");
        List<GiftPreferenceView> preferences = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            var categoryId = buffer.readResourceLocation();
            boolean known = buffer.readBoolean();
            int rating = known ? buffer.readByte() : 0;
            int priority = buffer.readVarInt();
            boolean professionSpecific = buffer.readBoolean();
            GiftCategoryName name = new GiftCategoryName(buffer.readUtf(256), buffer.readUtf(256));
            int matcherCount = VillagerPayloads.readCollectionSize(buffer, 512, "gift preference matchers");
            List<GiftPreferenceView.Matcher> matchers = new ArrayList<>(matcherCount);
            for (int matcherIndex = 0; matcherIndex < matcherCount; matcherIndex++) {
                matchers.add(new GiftPreferenceView.Matcher(
                        buffer.readEnum(GiftPreferenceDefinition.MatchSource.class),
                        buffer.readResourceLocation()));
            }
            preferences.add(new GiftPreferenceView(
                    categoryId,
                    rating,
                    known,
                    priority,
                    professionSpecific,
                    name,
                    matchers));
        }
        return List.copyOf(preferences);
    }
}
