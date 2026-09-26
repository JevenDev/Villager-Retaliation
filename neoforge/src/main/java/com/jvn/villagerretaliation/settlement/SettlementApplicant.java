package com.jvn.villagerretaliation.settlement;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class SettlementApplicant {
    private final UUID id;
    private final String name;
    private final long createdAt;
    private final CompoundTag entity;
    private final UUID approvedBy;

    SettlementApplicant(UUID id, String name, long createdAt, CompoundTag entity, UUID approvedBy) {
        if (id == null || name == null || name.isBlank() || name.length() > 128
                || entity == null || !entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(id)) {
            throw new IllegalArgumentException("Applicant identity or entity data is invalid");
        }
        this.id = id;
        this.name = name;
        this.createdAt = Math.max(0L, createdAt);
        this.entity = entity.copy();
        this.approvedBy = approvedBy;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public CompoundTag entity() {
        return this.entity.copy();
    }

    public Optional<UUID> approvedBy() {
        return Optional.ofNullable(this.approvedBy);
    }

    SettlementApplicant approve(UUID player) {
        return new SettlementApplicant(this.id, this.name, this.createdAt, this.entity, player);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Name", this.name);
        tag.putLong("CreatedAt", this.createdAt);
        tag.put("Entity", this.entity.copy());
        if (this.approvedBy != null) {
            tag.putUUID("ApprovedBy", this.approvedBy);
        }
        return tag;
    }

    static SettlementApplicant load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            throw new IllegalArgumentException("Applicant identity is missing");
        }
        return new SettlementApplicant(tag.getUUID("Id"), tag.getString("Name"), tag.getLong("CreatedAt"),
                tag.getCompound("Entity"), tag.hasUUID("ApprovedBy") ? tag.getUUID("ApprovedBy") : null);
    }
}
