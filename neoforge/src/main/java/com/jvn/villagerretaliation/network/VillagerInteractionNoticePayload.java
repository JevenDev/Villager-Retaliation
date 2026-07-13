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
        this(entityId, speakerLabel, parseText(text, DialogueTextEffects.NONE));
    }

    public VillagerInteractionNoticePayload(int entityId, String text, String speakerLabel, DialogueTextEffects textEffects) {
        this(entityId, speakerLabel, parseText(text, textEffects));
    }

    private VillagerInteractionNoticePayload(int entityId, String speakerLabel, ParsedText parsedText) {
        this(entityId, parsedText.text(), speakerLabel, parsedText.segments());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerInteractionNoticePayload payload) {
        List<DialogueTextSegment> textSegments = DialogueTextSegment.forNetwork(payload.text(), payload.textSegments());
        String segmentedText = DialogueTextSegment.plainText(textSegments);
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

    private static ParsedText parseText(String text, DialogueTextEffects textEffects) {
        List<DialogueTextSegment> segments = DialogueTextSegment.parse(text, textEffects);
        return new ParsedText(DialogueTextSegment.plainText(segments), segments);
    }

    private record ParsedText(String text, List<DialogueTextSegment> segments) {
    }
}
