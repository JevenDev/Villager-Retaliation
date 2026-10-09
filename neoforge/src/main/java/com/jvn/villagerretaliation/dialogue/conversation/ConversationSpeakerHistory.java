package com.jvn.villagerretaliation.dialogue.conversation;

import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

final class ConversationSpeakerHistory {
    private ConversationSpeakerHistory() { }

    static ListTag save(ConversationSession session) {
        var values = new ListTag();
        for (var entry : session.speakerHistory) {
            var tag = new CompoundTag();
            tag.put("Before", speaker(entry.before()));
            tag.put("After", speaker(entry.after()));
            tag.putString("Authorization", entry.authorization());
            tag.putLong("Revision", entry.revision());
            tag.putLong("Time", entry.time());
            values.add(tag);
        }
        return values;
    }

    static void load(CompoundTag tag, ConversationSession session) {
        for (var raw : ConversationSessionCodec.list(tag, "SpeakerHistory")) {
            var value = (CompoundTag) raw;
            session.speakerHistory.add(new Entry(speaker(ConversationSessionCodec.compound(value, "Before")),
                    speaker(ConversationSessionCodec.compound(value, "After")), ConversationSessionCodec.text(value, "Authorization"),
                    ConversationSessionCodec.number(value, "Revision"), ConversationSessionCodec.number(value, "Time")));
        }
        var original = new LinkedHashMap<>(session.speakers);
        long revision = session.revision, time = Long.MAX_VALUE;
        for (var entry : session.speakerHistory.reversed()) {
            ConversationSessionCodec.require(entry.after().equals(original.get(entry.after().role())) && entry.revision() <= revision
                    && entry.time() <= time && entry.time() >= session.identity.createdAt(), "conversation speaker repair history is discontinuous");
            original.put(entry.before().role(), entry.before());
            revision = entry.revision();
            time = entry.time();
        }
        var character = original.get(session.identity.characterRole());
        ConversationSessionCodec.require(character != null && character.entity().equals(session.identity.character())
                && character.dimension().equals(session.identity.dimension()), "conversation character identity changed without repair history");
    }

    static boolean known(ConversationSession session, String role, UUID entity) {
        var current = session.speakers.get(role);
        return current != null && (current.entity().equals(entity) || session.speakerHistory.stream()
                .anyMatch(entry -> entry.before().role().equals(role) && entry.before().entity().equals(entity)));
    }

    private static CompoundTag speaker(ConversationSession.Speaker speaker) {
        var tag = new CompoundTag();
        tag.putString("Role", speaker.role());
        tag.putUUID("Entity", speaker.entity());
        tag.putString("Dimension", speaker.dimension().toString());
        tag.putString("Name", speaker.name());
        return tag;
    }

    private static ConversationSession.Speaker speaker(CompoundTag tag) {
        return new ConversationSession.Speaker(ConversationSessionCodec.text(tag, "Role"), ConversationSessionCodec.uuid(tag, "Entity"),
                ConversationSessionCodec.resource(tag, "Dimension"), ConversationSessionCodec.text(tag, "Name"));
    }

    record Entry(ConversationSession.Speaker before, ConversationSession.Speaker after, String authorization, long revision, long time) {
        Entry {
            ConversationSessionCodec.require(before != null && after != null && before.role().equals(after.role())
                    && !before.entity().equals(after.entity()) && revision > 1 && time >= 0, "invalid conversation speaker repair");
            ConversationSessionCodec.require(authorization != null
                    && (authorization.startsWith("campaign_repair:") || authorization.startsWith("campaign_replacement:")),
                    "conversation speaker repair has no campaign authorization");
            String operations = authorization.substring(authorization.indexOf(':') + 1);
            ConversationSessionCodec.require(!operations.isBlank(), "conversation speaker repair has no operation identity");
            for (String operation : operations.split("/", -1)) {
                var parsed = UUID.fromString(operation);
                ConversationSessionCodec.require(parsed.toString().equals(operation), "invalid conversation speaker repair operation");
            }
        }
    }
}
