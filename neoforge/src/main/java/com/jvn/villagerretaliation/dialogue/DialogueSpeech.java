package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueSpeech(UUID speaker, int entityId, String name, List<DialogueTextSegment> segments) {
    public static final int MAX_EXCHANGES = 256;
    public DialogueSpeech {
        segments = List.copyOf(segments);
        if (speaker == null ? entityId != -1 : entityId < 0) throw new IllegalArgumentException("Invalid dialogue speaker");
        if (name.length() > 128) {
            int end = Character.isHighSurrogate(name.charAt(127)) && Character.isLowSurrogate(name.charAt(128)) ? 127 : 128;
            name = name.substring(0, end);
        }
    }

    public String text() { return DialogueTextSegment.plainText(segments); }

    public static List<DialogueSpeech> replaceSpeakerReply(List<DialogueSpeech> lines, DialogueSpeech reply) {
        var result = new ArrayList<DialogueSpeech>();
        boolean replaced = false;
        for (var line : lines) {
            if (java.util.Objects.equals(line.speaker(), reply.speaker())) {
                if (!replaced) result.add(reply);
                replaced = true;
            } else result.add(line);
        }
        if (!replaced) result.add(reply);
        return List.copyOf(result);
    }

    public static void writeList(RegistryFriendlyByteBuf buffer, List<DialogueSpeech> lines) {
        if (lines.size() > MAX_EXCHANGES) throw new IllegalArgumentException("Too many dialogue exchanges");
        buffer.writeVarInt(lines.size());
        for (var line : lines) {
            buffer.writeBoolean(line.speaker() != null);
            if (line.speaker() != null) buffer.writeUUID(line.speaker());
            buffer.writeVarInt(line.entityId());
            buffer.writeUtf(line.name(), 128);
            DialogueTextSegment.writeList(buffer, line.segments());
        }
    }

    public static List<DialogueSpeech> readList(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_EXCHANGES) throw new IllegalArgumentException("Invalid dialogue exchange count");
        var result = new ArrayList<DialogueSpeech>();
        for (int index = 0; index < count; index++) result.add(new DialogueSpeech(
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readVarInt(), buffer.readUtf(128), DialogueTextSegment.readList(buffer)));
        return List.copyOf(result);
    }
}
