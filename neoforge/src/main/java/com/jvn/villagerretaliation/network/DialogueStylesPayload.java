package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.DialogueStyle;
import com.jvn.villagerretaliation.dialogue.DialogueCue;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DialogueStylesPayload(Map<String, DialogueStyle> styles) implements CustomPacketPayload {
    public static final Type<DialogueStylesPayload> TYPE = VillagerPayloads.type("dialogue_styles");
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueStylesPayload> STREAM_CODEC =
            VillagerPayloads.codec(DialogueStylesPayload::encode, DialogueStylesPayload::decode);

    public DialogueStylesPayload {
        styles = Map.copyOf(styles);
        DialogueStyleResources.validateCatalog(styles);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DialogueStylesPayload payload) {
        buffer.writeVarInt(payload.styles().size());
        payload.styles().forEach((tag, style) -> {
            buffer.writeUtf(tag, 64);
            DialogueTextEffects.write(buffer, style.effects());
            buffer.writeVarInt(style.overrides());
            buffer.writeBoolean(style.speed() != null);
            if (style.speed() != null) buffer.writeDouble(style.speed());
            buffer.writeBoolean(style.restorePose());
            writeCues(buffer, style.onEnter());
            writeCues(buffer, style.onExit());
        });
    }

    private static DialogueStylesPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, DialogueStyleResources.MAX_STYLES, "dialogue styles");
        var styles = new java.util.LinkedHashMap<String, DialogueStyle>();
        for (int index = 0; index < size; index++) {
            String tag = buffer.readUtf(64);
            var effects = DialogueTextEffects.read(buffer);
            int overrides = buffer.readVarInt();
            Double speed = buffer.readBoolean() ? buffer.readDouble() : null;
            boolean restorePose = buffer.readBoolean();
            var style = new DialogueStyle(effects, overrides, speed, readCues(buffer), readCues(buffer), restorePose);
            if (styles.putIfAbsent(tag, style) != null) throw new IllegalArgumentException("Duplicate dialogue style tag");
        }
        return new DialogueStylesPayload(styles);
    }

    private static void writeCues(RegistryFriendlyByteBuf buffer, java.util.List<DialogueCue> cues) {
        buffer.writeVarInt(cues.size());
        cues.forEach(cue -> cue.write(buffer));
    }

    private static java.util.List<DialogueCue> readCues(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, DialogueStyle.MAX_STYLE_CUES, "dialogue style cues");
        var cues = new java.util.ArrayList<DialogueCue>();
        for (int index = 0; index < size; index++) cues.add(DialogueCue.read(buffer));
        return java.util.List.copyOf(cues);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
