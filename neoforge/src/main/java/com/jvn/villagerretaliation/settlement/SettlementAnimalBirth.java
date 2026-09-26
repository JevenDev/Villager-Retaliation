package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.AgeableMob;

final class SettlementAnimalBirth {
    private final UUID id;
    private final BlockPos position;
    private final CompoundTag entity;
    private final UUID experienceId;
    private final int experience;
    private boolean childApplied;

    private SettlementAnimalBirth(UUID id, BlockPos position, CompoundTag entity, UUID experienceId, int experience) {
        if (id == null || position == null || experienceId == null || id.equals(experienceId)
                || experience < 0 || experience > 7 || !entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(id)
                || !entity.contains("id", Tag.TAG_STRING) || net.minecraft.resources.ResourceLocation.tryParse(entity.getString("id")) == null) {
            throw new IllegalArgumentException("Animal birth identity, entity or experience is invalid");
        }
        this.id = id;
        this.position = position.immutable();
        this.entity = entity.copy();
        this.experienceId = experienceId;
        this.experience = experience;
    }

    static SettlementAnimalBirth create(AgeableMob child, int experience) {
        var entity = child.saveWithoutId(new CompoundTag());
        entity.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(child.getType()).toString());
        return new SettlementAnimalBirth(child.getUUID(), child.blockPosition(), entity, UUID.randomUUID(), experience);
    }

    UUID id() {
        return this.id;
    }

    BlockPos position() {
        return this.position;
    }

    CompoundTag entity() {
        return this.entity.copy();
    }

    UUID experienceId() {
        return this.experienceId;
    }

    int experience() {
        return this.experience;
    }

    boolean childApplied() {
        return this.childApplied;
    }

    void applyChild() {
        this.childApplied = true;
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putLong("Position", this.position.asLong());
        tag.put("Entity", this.entity.copy());
        tag.putUUID("ExperienceId", this.experienceId);
        tag.putInt("Experience", this.experience);
        tag.putBoolean("ChildApplied", this.childApplied);
        return tag;
    }

    static SettlementAnimalBirth load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("ExperienceId") || !tag.contains("Position", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Animal birth identity or position is missing");
        }
        var birth = new SettlementAnimalBirth(tag.getUUID("Id"), BlockPos.of(tag.getLong("Position")),
                tag.getCompound("Entity"), tag.getUUID("ExperienceId"), tag.getInt("Experience"));
        birth.childApplied = tag.getBoolean("ChildApplied");
        return birth;
    }
}
