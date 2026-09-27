package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record SettlementEvent(UUID id, long gameTime, Type type, String subject) {
    public SettlementEvent {
        if (id == null || gameTime < 0 || type == null || subject == null || subject.length() > 128) {
            throw new IllegalArgumentException("Invalid settlement history event");
        }
    }

    static SettlementEvent create(long gameTime, Type type, String subject) {
        int end = Math.min(128, subject.length());
        if (end < subject.length() && Character.isHighSurrogate(subject.charAt(end - 1))) end--;
        return new SettlementEvent(UUID.randomUUID(), gameTime, type, subject.substring(0, end));
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putLong("GameTime", this.gameTime);
        tag.putString("Type", this.type.name());
        tag.putString("Subject", this.subject);
        return tag;
    }

    static SettlementEvent load(CompoundTag tag) {
        return new SettlementEvent(tag.getUUID("Id"), tag.getLong("GameTime"),
                Type.valueOf(tag.getString("Type")), tag.getString("Subject"));
    }

    public enum Type {
        ESTABLISHED,
        STAGE_ADVANCED,
        RESIDENT_JOINED,
        RESIDENT_DEPARTED,
        PROJECT_STARTED,
        PROJECT_COMPLETED,
        PROJECT_CANCELLED,
        LEADERSHIP_TRANSFERRED
    }
}
