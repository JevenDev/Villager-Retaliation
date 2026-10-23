package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record DialogueSpeech(UUID speaker, int entityId, String name, List<DialogueTextSegment> segments, com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera, UUID presentationId) {
    public static final int MAX_EXCHANGES = 256;
    private static final int MAX_CAMERA_SHOTS = 512;
    public DialogueSpeech {
        java.util.Objects.requireNonNull(presentationId);
        segments = List.copyOf(segments);
        if (speaker == null ? entityId != -1 : entityId < 0) throw new IllegalArgumentException("Invalid dialogue speaker");
        if (name.length() > 128) {
            int end = Character.isHighSurrogate(name.charAt(127)) && Character.isLowSurrogate(name.charAt(128)) ? 127 : 128;
            name = name.substring(0, end);
        }
    }

    public DialogueSpeech(UUID speaker, int entityId, String name, List<DialogueTextSegment> segments,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
        this(speaker, entityId, name, segments, camera, UUID.randomUUID());
    }

    public DialogueSpeech(UUID speaker, int entityId, String name, List<DialogueTextSegment> segments) {
        this(speaker, entityId, name, segments, null);
    }

    public static DialogueSpeech npc(net.minecraft.world.entity.Entity entity, List<DialogueTextSegment> segments,
            com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera camera) {
        return new DialogueSpeech(entity.getUUID(), entity.getId(),
                com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry.resolveDisplayName(entity).getString(), segments, camera);
    }

    public String text() { return DialogueTextSegment.plainText(segments); }

    public static List<DialogueSpeech> replaceSpeakerReply(List<DialogueSpeech> lines, DialogueSpeech reply) {
        var result = new ArrayList<DialogueSpeech>();
        boolean replaced = false;
        for (var line : lines) {
            if (java.util.Objects.equals(line.speaker(), reply.speaker())) {
                if (!replaced) result.add(reply.camera() != null || line.camera() == null ? reply
                        : new DialogueSpeech(reply.speaker(), reply.entityId(), reply.name(), reply.segments(), line.camera(), reply.presentationId()));
                replaced = true;
            } else result.add(line);
        }
        if (!replaced) result.add(reply);
        return List.copyOf(result);
    }

    public static void writeList(RegistryFriendlyByteBuf buffer, List<DialogueSpeech> lines) {
        if (lines.size() > MAX_EXCHANGES) throw new IllegalArgumentException("Too many dialogue exchanges");
        if (lines.stream().filter(line -> line.camera() != null).mapToInt(line -> line.camera().shots().size()).sum() > MAX_CAMERA_SHOTS)
            throw new IllegalArgumentException("Too many dialogue camera shots");
        buffer.writeVarInt(lines.size());
        for (var line : lines) {
            buffer.writeBoolean(line.speaker() != null);
            if (line.speaker() != null) buffer.writeUUID(line.speaker());
            buffer.writeVarInt(line.entityId());
            buffer.writeUtf(line.name(), 128);
            DialogueTextSegment.writeList(buffer, line.segments());
            DialogueCamera.write(buffer, line.camera());
            buffer.writeUUID(line.presentationId());
        }
    }

    public static List<DialogueSpeech> readList(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_EXCHANGES) throw new IllegalArgumentException("Invalid dialogue exchange count");
        var result = new ArrayList<DialogueSpeech>();
        for (int index = 0; index < count; index++) result.add(new DialogueSpeech(
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readVarInt(), buffer.readUtf(128), DialogueTextSegment.readList(buffer), DialogueCamera.read(buffer), buffer.readUUID()));
        if (result.stream().filter(line -> line.camera() != null).mapToInt(line -> line.camera().shots().size()).sum() > MAX_CAMERA_SHOTS)
            throw new IllegalArgumentException("Too many dialogue camera shots");
        return List.copyOf(result);
    }
}
