package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerInteractionNoticePayload(
        int entityId,
        String text,
        String speakerLabel,
        List<DialogueTextSegment> textSegments) implements CustomPacketPayload {
    public static final Type<VillagerInteractionNoticePayload> TYPE = VillagerPayloads.type("villager_interaction_notice");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerInteractionNoticePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerInteractionNoticePayload::encode, VillagerInteractionNoticePayload::decode);

    public VillagerInteractionNoticePayload(int entityId, String text, String speakerLabel) {
        this(entityId,
                DialogueTextSegment.plainText(DialogueTextSegment.parse(text, DialogueTextEffects.NONE)),
                speakerLabel,
                DialogueTextSegment.parse(text, DialogueTextEffects.NONE));
    }

    public VillagerInteractionNoticePayload(int entityId, String text, String speakerLabel, DialogueTextEffects textEffects) {
        this(entityId,
                DialogueTextSegment.plainText(DialogueTextSegment.parse(text, textEffects)),
                speakerLabel,
                DialogueTextSegment.parse(text, textEffects));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerInteractionNoticePayload payload) {
        List<DialogueTextSegment> textSegments = DialogueTextSegment.forNetwork(payload.textSegments());
        String segmentedText = DialogueTextSegment.plainText(textSegments);
        String text = payload.text() == null ? "" : payload.text();
        if (textSegments.isEmpty() && !text.isEmpty()) {
            textSegments = DialogueTextSegment.forNetwork(
                    DialogueTextSegment.plain(text, DialogueTextEffects.NONE));
            segmentedText = DialogueTextSegment.plainText(textSegments);
        } else if (!segmentedText.equals(text)) {
            // A mismatched caller-provided style list must not make the client
            // render different dialogue from the packet's plain-text field.
            textSegments = DialogueTextSegment.forNetwork(
                    DialogueTextSegment.plain(text, DialogueTextEffects.NONE));
            segmentedText = DialogueTextSegment.plainText(textSegments);
        }
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(segmentedText, 512);
        buffer.writeUtf(truncate(payload.speakerLabel(), 128), 128);
        DialogueTextSegment.writeList(buffer, textSegments);
    }

    private static VillagerInteractionNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerInteractionNoticePayload(
                buffer.readVarInt(),
                buffer.readUtf(512),
                buffer.readUtf(128),
                DialogueTextSegment.readList(buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }
}
