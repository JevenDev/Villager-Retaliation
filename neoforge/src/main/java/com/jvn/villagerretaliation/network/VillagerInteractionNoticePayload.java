package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.DialogueTextSegment;
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
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.text(), 512);
        buffer.writeUtf(payload.speakerLabel(), 128);
        DialogueTextSegment.writeList(buffer, payload.textSegments());
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
}
